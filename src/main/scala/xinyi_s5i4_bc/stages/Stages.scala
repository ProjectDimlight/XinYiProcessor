package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class PCInterface extends Bundle with XinYiConfig {
  val enable = Input(UInt(lgc_addr_w.W))
  val target = Input(UInt(lgc_addr_w.W))
}

class PCStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val pc        = Input(UInt(lgc_addr_w.W))
    val branch    = new PCInterface
    val exception = new PCInterface
    val next_pc   = Output(UInt(lgc_addr_w.W))
  })

  io.next_pc := io.pc + 4.U(lgc_addr_w.W)
}

class IFIn extends Bundle with XinYiConfig {
  val pc = Input(UInt(lgc_addr_w.W))
}

class IFOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(l1_w.W))
}

// Load load_num instructions at a time
// Branch Cache
class IFStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IFIn
    val cache = Flipped(new RAMInterface(lgc_addr_w, l1_w))
    val out   = new IFOut
  })

  // ICache
  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  io.cache.din  := 0.U(32.W)
  
  // Output to IF-ID Regs
  io.out.pc := io.in.pc
  io.out.inst := io.cache.dout
}

class IDIn extends Bundle with XinYiConfig {
  val pc   = Input(UInt(lgc_addr_w.W))
  val inst = Input(UInt(data_w.W))
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = Vec(fetch_num, new IDIn)
    val out   = Output(Vec(fetch_num, new Instruction))
  })

  for (i <- 0 until fetch_num) {
    val decoder = Module(new MIPSDecoder)
    decoder.io.inst := io.in(i).inst
    io.out(i).pc   := io.in(i).pc
    io.out(i).inst := io.in(i).inst
    io.out(i).dec  := decoder.io.dec
  }
}

class PathIn extends Bundle with XinYiConfig {
  val inst        = Input(new Instruction)
  val id          = Input(UInt(issue_num_w.W))
}

class PathOut extends Bundle with XinYiConfig {
  val wt          = Output(UInt(write_target_w.W))
  val rd          = Output(UInt(reg_id_w.W))
  val ready       = Output(Bool())
}

class PathData extends Bundle with XinYiConfig {
  val rs1         = Input(UInt(data_w.W))
  val rs2         = Input(UInt(data_w.W))
}

class PathInterface extends Bundle with XinYiConfig {
  val in   = new PathIn
  val out  = new PathOut
}

class PathInterfaceWithData extends PathInterface {
  val data = new PathData
}

class BJUPathInterface extends Bundle with XinYiConfig {
  val in   = new PathIn
  val data = new PathData
}

// Issue Stage
class ISStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val issue_cnt = Input(UInt(queue_len_w.W))
    val inst      = Input(Vec(issue_num, new Instruction))

    val alu_paths = Flipped(Vec(alu_path_num, new PathInterface))
    val bju_paths = Flipped(Vec(bju_path_num, new PathInterface))   // bju path num = 0
    val lsu_paths = Flipped(Vec(lsu_path_num, new PathInterface))
    val actual_issue_cnt = Output(UInt(issue_num_w.W))

    // To BJU
    val branch_jump_id = Output(UInt(alu_path_num_w.W))
    val delay_slot_pending = Output(Bool())
  })

  // Hazard Detect Logic  

  val inst = Wire(Vec(issue_num, new Instruction))
  val filtered_inst = Wire(Vec(issue_num, new Instruction))
  inst := io.inst

  // For each instruction, decide which path it should go
  val target = Wire(Vec(issue_num, UInt(path_w.W)))

  val issued_by_alu = Wire(Vec(issue_num, Bool()))
  val issued_by_bju = Wire(Vec(issue_num, Bool()))
  val issued_by_lsu = Wire(Vec(issue_num, Bool()))
  val issued        = Wire(Vec(issue_num, Bool()))
  val no_raw        = Wire(Vec(issue_num, Bool()))

  // Begin
  io.actual_issue_cnt := issue_num.U(issue_num_w.W)

  def RAWPath(i: Instruction, j: PathInterface) = {
    !j.out.ready & (j.out.rd === i.dec.rs1 | j.out.rd === i.dec.rs2)
  }

  def RAWInst(i: Instruction, k: Instruction) = {
    (k.dec.rd === i.dec.rs1 | k.dec.rd === i.dec.rs2)
  }

  // i is the id of the currect instruction to be detected
  for (i <- 0 until issue_num) {
    // Detect hazards

    // RAW Data hazard

    // From path (issued)
    no_raw(i) := true.B
    for (j <- 0 until alu_path_num) {
      when (RAWPath(inst(i), io.alu_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until bju_path_num) {
      when (RAWPath(inst(i), io.bju_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until lsu_path_num) {
      when (RAWPath(inst(i), io.lsu_paths(j))) {
        no_raw(i) := false.B
      }
    }

    // From queue (going to issue)
    for (k <- 0 until i) {
      when (RAWInst(inst(i), inst(k))) {
        no_raw(i) := false.B
      }
    }

    // Target filter
    target(i) := Mux(
      (!no_raw(i) | (i.U >= io.issue_cnt)),
      0.U,
      inst(i).dec.path
    )

    // Structural hazard
    issued(i) := issued_by_alu(i) | issued_by_bju(i) | issued_by_lsu(i)

    // If an instruction cannot be issued
    // Mark its ID
    // Every following instruction (with a greater ID) will be replaced by an NOP Bubble
    when (issued(i) === false.B) {
      io.actual_issue_cnt := i.U(issue_num_w.W)
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    when (i.U(issue_num_w.W) >= io.actual_issue_cnt) {
      filtered_inst(i) := NOPBubble()
    }
    .otherwise {
      filtered_inst(i) := inst(i)
    }
  }

  /////////////////////////////////////////////////////////////////

  // Issue Logic

  // Put instructions into paths
  // Parameterized issuing
  Issuer(alu_path_id, alu_path_num, filtered_inst, target, issued_by_alu, io.alu_paths)
  Issuer(bju_path_id, bju_path_num, filtered_inst, target, issued_by_bju, io.bju_paths)
  Issuer(lsu_path_id, lsu_path_num, filtered_inst, target, issued_by_lsu, io.lsu_paths)
  
  io.branch_jump_id := alu_path_num.U(alu_path_num_w.W)
  io.delay_slot_pending := false.B
  for (j <- 0 until alu_path_num) {
    // Branch
    when (io.alu_paths(j).in.inst.dec.next_pc =/= PC4) {
      io.branch_jump_id := j.U(alu_path_num_w.W)
      io.delay_slot_pending := (io.alu_paths(j).in.id + 1.U) === io.actual_issue_cnt
    }
  }
}