package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._

import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._
import config.config._

class PCInterface extends Bundle  {
  val enable = Input(UInt(LGC_ADDR_W.W))
  val target = Input(UInt(LGC_ADDR_W.W))
}

class PCStage extends Module  {
  val io = IO(new Bundle{
    val pc        = Input(UInt(LGC_ADDR_W.W))
    val branch    = new PCInterface
    val exception = new PCInterface
    val next_pc   = Output(UInt(LGC_ADDR_W.W))
  })

  //when (exception)
  io.next_pc := io.pc + 4.U(LGC_ADDR_W.W)
}

class IFIn extends Bundle  {
  val pc = Input(UInt(LGC_ADDR_W.W))
}

class IFOut extends Bundle  {
  val pc   = Output(UInt(LGC_ADDR_W.W))
  val inst = Output(UInt(L1_W.W))
}

// Load load_num instructions at a time
// Branch Cache
class IFStage extends Module  {
  val io = IO(new Bundle{
    val in    = new IFIn
    val cache = Flipped(new RAMInterface(LGC_ADDR_W, L1_W))
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

class IDIn extends Bundle  {
  val pc   = Input(UInt(LGC_ADDR_W.W))
  val inst = Input(UInt(DATA_W.W))
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module  {
  val io = IO(new Bundle{
    val in    = Vec(FETCH_NUM, new IDIn)
    val out   = Output(Vec(FETCH_NUM, new Instruction))
  })

  for (i <- 0 until FETCH_NUM) {
    val decoder = Module(new MIPSDecoder)
    decoder.io.inst := io.in(i).inst
    io.out(i).pc   := io.in(i).pc
    io.out(i).inst := io.in(i).inst
    io.out(i).dec  := decoder.io.dec
  }
}

class PathIn extends Bundle  {
  val inst        = Input(new Instruction)
  val id          = Input(UInt(ISSUE_NUM_W.W))
}

class PathOut extends Bundle  {
  val wt          = Output(UInt(write_target_w.W))
  val rd          = Output(UInt(REG_ID_W.W))
  val ready       = Output(Bool())
}

class PathData extends Bundle  {
  val rs1         = Input(UInt(DATA_W.W))
  val rs2         = Input(UInt(DATA_W.W))
}

class PathInterface extends Bundle  {
  val in   = new PathIn
  val out  = new PathOut
}

class PathInterfaceWithData extends PathInterface {
  val data = new PathData
}

class BJUPathInterface extends Bundle  {
  val in   = new PathIn
  val data = new PathData
}

// Issue Stage
class ISStage extends Module  {
  val io = IO(new Bundle{
    val issue_cnt = Input(UInt(QUEUE_LEN_w.W))
    val inst      = Input(Vec(ISSUE_NUM, new Instruction))

    val alu_paths = Flipped(Vec(ALU_PATH_NUM, new PathInterface))
    val bju_paths = Flipped(Vec(BJU_PATH_NUM, new PathInterface))   // bju path num = 0
    val lsu_paths = Flipped(Vec(LSU_PATH_NUM, new PathInterface))
    val actual_issue_cnt = Output(UInt(ISSUE_NUM_W.W))

    // To BJU
    val branch_jump_id = Output(UInt(ALU_PATH_NUM_W.W))
    val delay_slot_pending = Output(Bool())
  })

  // Hazard Detect Logic  

  val inst = Wire(Vec(ISSUE_NUM, new Instruction))
  val filtered_inst = Wire(Vec(ISSUE_NUM, new Instruction))
  inst := io.inst

  // For each instruction, decide which path it should go
  val target = Wire(Vec(ISSUE_NUM, UInt(PATH_W.W)))

  val issued_by_alu = Wire(Vec(ISSUE_NUM, Bool()))
  val issued_by_bju = Wire(Vec(ISSUE_NUM, Bool()))
  val issued_by_lsu = Wire(Vec(ISSUE_NUM, Bool()))
  val issued        = Wire(Vec(ISSUE_NUM, Bool()))
  val no_raw        = Wire(Vec(ISSUE_NUM, Bool()))

  // Begin
  io.actual_issue_cnt := ISSUE_NUM.U(ISSUE_NUM_W.W)

  def RAWPath(i: Instruction, j: PathInterface) = {
    !j.out.ready & (j.out.rd === i.dec.rs1 | j.out.rd === i.dec.rs2)
  }

  def RAWInst(i: Instruction, k: Instruction) = {
    (k.dec.rd === i.dec.rs1 | k.dec.rd === i.dec.rs2)
  }

  // i is the id of the currect instruction to be detected
  for (i <- 0 until ISSUE_NUM) {
    // Detect hazards

    // RAW Data hazard

    // From path (issued)
    no_raw(i) := true.B
    for (j <- 0 until ALU_PATH_NUM) {
      when (RAWPath(inst(i), io.alu_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until BJU_PATH_NUM) {
      when (RAWPath(inst(i), io.bju_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until LSU_PATH_NUM) {
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
      io.actual_issue_cnt := i.U(ISSUE_NUM_W.W)
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    when (i.U(ISSUE_NUM_W.W) >= io.actual_issue_cnt) {
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
  Issuer(ALU_PATH_ID, ALU_PATH_NUM, filtered_inst, target, issued_by_alu, io.alu_paths)
  Issuer(BJU_PATH_ID, BJU_PATH_NUM, filtered_inst, target, issued_by_bju, io.bju_paths)
  Issuer(LSU_PATH_ID, LSU_PATH_NUM, filtered_inst, target, issued_by_lsu, io.lsu_paths)
  
  io.branch_jump_id := ALU_PATH_NUM.U(ALU_PATH_NUM_W.W)
  io.delay_slot_pending := false.B
  for (j <- 0 until ALU_PATH_NUM) {
    // Branch
    when (io.alu_paths(j).in.inst.dec.next_pc =/= PC4) {
      io.branch_jump_id := j.U(ALU_PATH_NUM_W.W)
      io.delay_slot_pending := (io.alu_paths(j).in.id + 1.U) === io.actual_issue_cnt
    }
  }
}