package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import utils._

import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.fu._
import ControlConst._
import config.config._

class PCInterface extends Bundle {
  val enable = Bool()
  val target = UInt(LGC_ADDR_W.W)
}

class PCStage extends Module {
  val io = IO(new Bundle {
    val pc = Input(UInt(LGC_ADDR_W.W))
    val branch = Input(new PCInterface)
    val exception = Input(new PCInterface)
    val stall = Input(Bool())
    val next_pc = Output(UInt(LGC_ADDR_W.W))
  })

  val ex_reg = RegInit(0.U.asTypeOf(new PCInterface))
  val br_reg = RegInit(0.U.asTypeOf(new PCInterface))

  val ex = Wire(new PCInterface)
  val br = Wire(new PCInterface)

  ex.enable := ex_reg.enable | io.exception.enable
  ex.target := Mux(ex_reg.enable, ex_reg.target, io.exception.target)

  br.enable := br_reg.enable | io.branch.enable
  br.target := Mux(br_reg.enable, br_reg.target, io.branch.target)

  when (io.stall) {
    when (!ex_reg.enable) {
      ex_reg := io.exception
    }
    when (!br_reg.enable) {
      br_reg := io.branch
    }
  }
  .otherwise {
    ex_reg := 0.U.asTypeOf(new PCInterface)
    br_reg := 0.U.asTypeOf(new PCInterface)
  }

  io.next_pc := MuxCase(
    (io.pc & 0xFFFFFFFCL.U) + (4 * FETCH_NUM).U(LGC_ADDR_W.W),
    Array(
      ex.enable -> ex.target,
      br.enable -> br.target
    )
  )
}

class IFIn extends Bundle {
  val pc    = Input(UInt(LGC_ADDR_W.W))
}

class IFOut extends Bundle {
  val pc = Output(UInt(LGC_ADDR_W.W))
  val inst = Output(UInt(L1_W.W))
}

// Load load_num instructions at a time
// Branch Cache
class IFStage extends Module {
  val io = IO(new Bundle {
    val in = new IFIn
    val cache = Flipped(new ICacheCPU)
    val full = Input(Bool())
    val out = new IFOut
  })

  // ICache
  io.cache.rd := !io.full
  // io.cache.wr := false.B
  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  // io.cache.din := 0.U(32.W)

  // Output to IF-ID Regs
  io.out.pc := io.in.pc
  io.out.inst := io.cache.dout
}

class IDIn extends Bundle {
  val pc = Input(UInt(LGC_ADDR_W.W))
  val inst = Input(UInt(XLEN.W))
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module with ALUConfig{
  val io = IO(new Bundle {
    val in = Vec(FETCH_NUM, new IDIn)
    val out = Output(Vec(FETCH_NUM, new Instruction))
  })

  for (i <- 0 until FETCH_NUM) {
    val decoder = Module(new MIPSDecoder)
    decoder.io.inst := io.in(i).inst

    val signed    = Wire(SInt(32.W))
    val signed_x4 = Wire(SInt(32.W))

    signed    := io.in(i).inst(15, 0).asSInt()
    signed_x4 := Cat(io.in(i).inst(15, 0), 0.U(2.W)).asSInt()

    io.out(i).pc := io.in(i).pc
    io.out(i).imm := MuxCase(
      io.in(i).inst(15, 0),
      Array(
         (decoder.io.dec.next_pc === Branch)  -> signed_x4.asUInt(),
         (decoder.io.dec.next_pc === Jump)    -> Cat(io.in(i).inst(25, 0), 0.U(2.W)),
        ((decoder.io.dec.fu_ctrl === ALU_ADD  | 
          decoder.io.dec.fu_ctrl === ALU_ADDU | 
          decoder.io.dec.fu_ctrl === ALU_SUB  | 
          decoder.io.dec.fu_ctrl === ALU_SLT  | 
          decoder.io.dec.fu_ctrl === ALU_SLTU 
         ) & decoder.io.dec.path === PathALU | 
          decoder.io.dec.path    === PathLSU) -> signed.asUInt()
      )
    )
    io.out(i).dec := decoder.io.dec
  }
}

class ForwardingPathId extends Bundle {
  val rs1  = UInt(TOT_PATH_NUM_W.W)
  val rs2  = UInt(TOT_PATH_NUM_W.W)
}

class Path extends Bundle {
  // target
  val write_target  = Output(UInt(WRITE_TARGET_W.W))
  val rd            = Output(UInt(REG_ID_W.W))

  // control
  val fu_ctrl       = Output(UInt(FU_CTRL_W.W))
  
  // meta
  val pc            = Output(UInt(LGC_ADDR_W.W))
  val order         = Output(UInt(ISSUE_NUM_W.W))
}

class ISOut extends Path {
  // params
  val a             = Output(UInt(XLEN.W))
  val b             = Output(UInt(XLEN.W))
  val imm           = Output(UInt(XLEN.W))
  
  // Delay Slot Mark
  val is_delay_slot = Output(Bool())
}

// Issue Stage
class ISStage extends Module {
  val io = IO(new Bundle {
    val issue_cnt = Input(UInt(QUEUE_LEN_w.W))
    val inst = Input(Vec(ISSUE_NUM, new Instruction))

    // To Param Fetcher 
    val forwarding_path_id  = Output(Vec(ISSUE_NUM, new ForwardingPathId))
    val is_delay_slot       = Output(Vec(ISSUE_NUM + 1, Bool()))

    // To common FUs
    val actual_issue_cnt    = Output(UInt(ISSUE_NUM_W.W))
    val path                = Vec(TOT_PATH_NUM, new Path)
    val forwarding          = Input(Flipped(Vec(TOT_PATH_NUM, new Forwarding)))
    val branch_cache_out    = Flipped(new BranchCacheOut)
    val stall               = Input(Bool())

    // To BJU
    val branch_jump_id      = Output(UInt(ALU_PATH_NUM_W.W))
    val branch_next_pc      = Output(UInt(NEXT_PC_W.W))
    val delay_slot_pending  = Output(Bool())
  })

  // Hazard Detect Logic  
  val filtered_inst = Wire(Vec(ISSUE_NUM, new Instruction))

  // For each instruction, decide which path it should go
  val target = Wire(Vec(ISSUE_NUM, UInt(PATH_W.W)))

  val issued = Wire(Vec(PATH_TYPE_NUM, Vec(ISSUE_NUM, Bool())))
  val raw = Wire(Vec(ISSUE_NUM, Bool()))
  val waw = Wire(Vec(ISSUE_NUM, Bool()))

  val issue_cnt = Wire(UInt(QUEUE_LEN_w.W))

  // Begin
  

  issue_cnt := Mux(
    io.branch_cache_out.flush,
    io.branch_cache_out.keep_delay_slot,
    io.issue_cnt
  )
  io.actual_issue_cnt := ISSUE_NUM.U(ISSUE_NUM_W.W)

  def RAWPath(i: Int, j: Int) {

    when ((io.forwarding(j).write_target === 5.U & io.inst(i).dec.param_a === BitPat("b01?") |  // HiLo
           io.forwarding(j).write_target === io.inst(i).dec.param_a) &       // Same source
           io.forwarding(j).rd === io.inst(i).dec.rs1 &                      // Same ID
          (io.inst(i).dec.param_a =/= AReg | io.inst(i).dec.rs1 =/= 0.U)) {  // Not Reg 0
//    when (io.forwarding(j).ready) {
        io.forwarding_path_id(i).rs1 := j.U
//    }
//    .otherwise {
//      raw(i) := true.B
//    }
    }
    when ( io.forwarding(j).write_target === 0.U &       // rs2 ONLY relies on regs
           io.forwarding(j).rd === io.inst(i).dec.rs2 &  // Same ID
           io.inst(i).dec.rs2 =/= 0.U) {                 // Not Reg 0
//    when (io.forwarding(j).ready) {
        io.forwarding_path_id(i).rs2 := j.U
//    }
//    .otherwise {
//      raw(i) := true.B
//    }
    }
  }

  def RAWInst(i: Int, k: Int) {
    when ((io.inst(k).dec.write_target === 5.U & io.inst(i).dec.param_a === BitPat("b01?") |  // HiLo
           io.inst(k).dec.write_target === io.inst(i).dec.param_a) &         // Same source
           io.inst(k).dec.rd === io.inst(i).dec.rs1 &                        // Same id
          (io.inst(i).dec.param_a =/= AReg | io.inst(i).dec.rs1 =/= 0.U)) {  // Not Reg 0
      raw(i) := true.B
    }

    when ( io.inst(k).dec.write_target === 0.U &       // rs2 ONLY relies on regs
           io.inst(k).dec.rd === io.inst(i).dec.rs2 &  // Same id
           io.inst(i).dec.rs2 =/= 0.U) {
      raw(i) := true.B
    }
  }

  def WAWInst(i: Int, k: Int) {
    when ((io.inst(k).dec.write_target === DHiLo & io.inst(i).dec.write_target === BitPat("b01?") |  // HiLo
           io.inst(i).dec.write_target === DHiLo & io.inst(k).dec.write_target === BitPat("b01?") |  // HiLo
           io.inst(k).dec.write_target === io.inst(i).dec.write_target) &        // Same source
           io.inst(k).dec.rd === io.inst(i).dec.rd &                             // Same id
          (io.inst(i).dec.write_target =/= DReg | io.inst(i).dec.rd =/= 0.U) |   // Not Reg 0
          // (io.inst(k).dec.write_target === DCP0 & io.inst(i).dec.write_target === DCP0))
          (io.inst(k).dec.write_target === DCP0))
    { 
      waw(i) := true.B
    }
  }

  val delay_slot_reg = RegInit(false.B)
  val is_delay_slot = Wire(Vec(ISSUE_NUM + 1, Bool()))
  for (i <- 0 until ISSUE_NUM) {
    io.is_delay_slot(i) := is_delay_slot(i)
  }
  io.is_delay_slot(ISSUE_NUM) := false.B

  is_delay_slot(0) := delay_slot_reg
  for (i <- 1 to ISSUE_NUM) {
    is_delay_slot(i) := false.B
  }

  delay_slot_reg := is_delay_slot(io.actual_issue_cnt)
  // i is the id of the currect instruction to be detected
  for (i <- 0 until ISSUE_NUM) {
    // Detect Delay Slot
    when (io.inst(i).dec.next_pc =/= PC4){
      is_delay_slot(i+1) := true.B
    }

    // Detect hazards

    // RAW Data hazard
    io.forwarding_path_id(i).rs1 := TOT_PATH_NUM.U(TOT_PATH_NUM_W.W)
    io.forwarding_path_id(i).rs2 := TOT_PATH_NUM.U(TOT_PATH_NUM_W.W)
    // From path (issued)
    raw(i) := false.B
    waw(i) := false.B
    for (j <- 0 until TOT_PATH_NUM) {
      RAWPath(i, j)
    }
    // From queue (going to issue)
    for (k <- 0 until i) {
      RAWInst(i, k)
    }
    // From queue (going to issue)
    for (k <- 0 until i) {
      WAWInst(i, k)
    }

    // Structural hazard
    // Given by input

    // Target filter
    target(i) := io.inst(i).dec.path

    issued(0)(i) := false.B
    when (!(raw(i) | waw(i) | (i.U >= issue_cnt))) {
      for (path_type <- 1 until PATH_TYPE_NUM)
        when(issued(path_type)(i)) {
          issued(0)(i) := true.B
        }
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    filtered_inst(i) := Mux(
      i.U(ISSUE_NUM_W.W) >= io.actual_issue_cnt,
      NOPBubble(),
      io.inst(i)
    )
  }

  for (i <- ISSUE_NUM - 1 to 0 by -1) {
    // If an instruction cannot be issued
    // Mark its ID
    // Every following instruction (with a greater ID) will be replaced by an NOP Bubble
    when(issued(0)(i) === false.B) {
      io.actual_issue_cnt := i.U(ISSUE_NUM_W.W)
    }
  }

  when (io.stall) {
    io.actual_issue_cnt := 0.U(ISSUE_NUM_W.W)
  }

  /////////////////////////////////////////////////////////////////

  // Issue Logic

  // Put instructions into paths
  // Parameterized issuing
  var base = 0
  for (path_type <- 1 until PATH_TYPE_NUM) {
    Issuer(path_type, base, PATH_NUM(path_type), filtered_inst, target, issued(path_type), io.path /*, io.forwarding*/)
    base += PATH_NUM(path_type)
  }

  io.branch_jump_id := ALU_PATH_NUM.U(TOT_PATH_NUM_W.W)
  io.branch_next_pc := PC4
  io.delay_slot_pending := false.B
  for (j <- 0 until ALU_PATH_NUM) {
    // Branch
    when ((io.path(j).order =/= ISSUE_NUM.U) & (filtered_inst(io.path(j).order).dec.next_pc =/= PC4)) {
      io.branch_jump_id := j.U(ALU_PATH_NUM_W.W)
      io.branch_next_pc := filtered_inst(io.path(j).order).dec.next_pc
      io.delay_slot_pending := (io.path(j).order + 1.U) === io.actual_issue_cnt
    }
  }
}

