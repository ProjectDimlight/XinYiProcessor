package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class PCStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val pc      = Input(UInt(lgc_addr_w.W))
    //val bpu_out = Flipped(new BPUOut)
    //val bju_out = Flipped(new BJUOut)
    val next_pc = Output(UInt(lgc_addr_w.W))
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

class IDOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(data_w.W))
  val dec  = Output(new ControlSet)
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IDIn
    val out   = new IDOut
  })

  val decoder = Module(new MIPSDecoder)
  decoder.io.inst := io.in.inst

  io.out.pc   := io.in.pc
  io.out.inst := io.in.inst
  io.out.dec  := decoder.io.ctrl
}

class PathInterface extends Bundle with XinYiConfig {
  val wt          = Output(UInt(write_target_w.W))
  val rd          = Output(UInt(reg_id_w.W))
  val ready       = Output(Bool())
  val inst        = Input(new Instruction)
}

// Issue Queue
class ISStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in        = Input(Vec(fetch_num, Flipped(new Instruction)))
    val bc        = Flipped(new BranchCacheOut)
    
    val alu_paths = Flipped(Vec(alu_path_num, new PathInterface))
    val mdu_paths = Flipped(Vec(mdu_path_num, new PathInterface))
    val lsu_paths = Flipped(Vec(lsu_path_num, new PathInterface))
  })

  // Queue logic

  val queue = Reg(Vec(queue_len, new Instruction))
  val head  = RegInit(0.U(queue_len_w.W))
  val tail  = RegInit(0.U(queue_len_w.W))
  val size  = RegInit(0.U(queue_len_w.W))
  
  for (i <- 0 until fetch_num) {
    when (tail + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
      queue(head + i.U(queue_len_w.W)) := io.in(i)
    } 
    .otherwise {
      queue(head + ((1 << queue_len_w) + i - queue_len).U(queue_len_w.W)) := io.in(i)
    }
  }
  tail := tail + fetch_num.U(queue_len_w.W)

  val inst = Wire(Vec(issue_num, new Instruction))

  // The next instruction comes from branch cache
  when (io.bc.branch_cache_overwrite) {
    inst := io.bc.inst
  }
  // The next instruction is from the queue
  .otherwise {
    for (i <- 0 until issue_num) {
      when (head + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
        inst(i) := queue(head + i.U(queue_len_w.W))
      }
      .otherwise {
        inst(i) := queue(head + ((1 << queue_len_w) + i - queue_len).U(queue_len_w.W))
      }
    }
  }

  /////////////////////////////////////////////////////////////////

  // Hazard Detect Logic  

  val actual_issue_cnt = Wire(UInt(issue_num_w.W))
  
  val filtered_inst = Wire(Vec(issue_num, new Instruction))

  // For each instruction, decide which path it should go
  val target = Wire(Vec(issue_num, UInt(path_w.W)))

  val issued_by_alu = Wire(Vec(issue_num, Bool()))
  val issued_by_mdu = Wire(Vec(issue_num, Bool()))
  val issued_by_lsu = Wire(Vec(issue_num, Bool()))
  val issued        = Wire(Vec(issue_num, Bool()))
  val no_raw        = Wire(Vec(issue_num, Bool()))

  // Begin
  actual_issue_cnt := 0.U(issue_num_w.W)

  // i is the id of the currect instruction to be detected
  for (i <- 0 until issue_num) {
    // Detect hazards

    // RAW Data hazard
    no_raw(i) := true.B
    for (j <- 0 until alu_path_num) {
      when (!io.alu_paths(j).ready & (io.alu_paths(j).rd === inst(i).dec.rs1 | io.alu_paths(j).rd === inst(i).dec.rs2)) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until mdu_path_num) {
      when (!io.mdu_paths(j).ready & (io.mdu_paths(j).rd === inst(i).dec.rs1 | io.mdu_paths(j).rd === inst(i).dec.rs2)) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until lsu_path_num) {
      when (!io.lsu_paths(j).ready & (io.lsu_paths(j).rd === inst(i).dec.rs1 | io.lsu_paths(j).rd === inst(i).dec.rs2)) {
        no_raw(i) := false.B
      }
    }

    // Target filter
    target(i) := MuxCase(
      alu_path_id.U(path_w.W),                                          // ALU
      Array(
        (inst(i).dec.mem_width =/= MemXXX) -> lsu_path_id.U(path_w.W),  // LSU
        (inst(i).dec.mdu)                  -> mdu_path_id.U(path_w.W)   // MDU
      )
    )
    when (!no_raw(i)) {
      target(i) := 0.U(path_w.W)
    }

    // Structural hazard
    issued(i) := issued_by_alu(i) | issued_by_mdu(i) | issued_by_lsu(i)

    // If an instruction cannot be issued
    // Mark its ID
    // Every following instruction (with a greater ID) will be replaced by an NOP Bubble
    when (issued(i) === false.B) {
      actual_issue_cnt := (i + 1).U(issue_num_w.W)
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    when (i.U(issue_num_w.W) >= actual_issue_cnt) {
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
  Issuer(alu_path_id, alu_path_num, inst, target, issued_by_alu, io.alu_paths)
  Issuer(mdu_path_id, mdu_path_num, inst, target, issued_by_mdu, io.mdu_paths)
  Issuer(lsu_path_id, lsu_path_num, inst, target, issued_by_lsu, io.lsu_paths)
  
  head := head + actual_issue_cnt
}