package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._

import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._
import config.config._

class PCInterface extends Bundle {
  val enable = Input(Bool())
  val target = Input(UInt(LGC_ADDR_W.W))
}

class PCStage extends Module {
  val io = IO(new Bundle {
    val pc = Input(UInt(LGC_ADDR_W.W))
    val branch = new PCInterface
    val exception = new PCInterface
    val next_pc = Output(UInt(LGC_ADDR_W.W))
  })

  io.next_pc := MuxCase(
    (io.pc & 0xFFFFFFFC.U) + 4.U(LGC_ADDR_W.W),
    Array(
      io.exception.enable -> io.exception.target,
      io.branch.enable -> io.branch.target
    )
  )
}

class IFIn extends Bundle {
  val pc = Input(UInt(LGC_ADDR_W.W))
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
    val cache = Flipped(new RAMInterface(LGC_ADDR_W, L1_W))
    val out = new IFOut
  })

  // ICache
  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  io.cache.din := 0.U(32.W)

  // Output to IF-ID Regs
  io.out.pc := io.in.pc
  io.out.inst := io.cache.dout
}

class IDIn extends Bundle {
  val pc = Input(UInt(LGC_ADDR_W.W))
  val inst = Input(UInt(DATA_W.W))
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module {
  val io = IO(new Bundle {
    val in = Vec(FETCH_NUM, new IDIn)
    val out = Output(Vec(FETCH_NUM, new Instruction))
  })

  for (i <- 0 until FETCH_NUM) {
    val decoder = Module(new MIPSDecoder)
    decoder.io.inst := io.in(i).inst
    io.out(i).pc := io.in(i).pc
    io.out(i).inst := io.in(i).inst
    io.out(i).dec := decoder.io.dec
  }
}

class PathIn extends Bundle {
  val inst = Input(new Instruction)
  val id = Input(UInt(ISSUE_NUM_W.W))
}

class PathOut extends Bundle {
  val wt = Output(UInt(WRITE_TARGET_W.W))
  val rd = Output(UInt(REG_ID_W.W))
  val data = Output(UInt(DATA_W.W))
  val hi = Output(UInt(DATA_W.W))
  val ready = Output(Bool())
}

class PathData extends Bundle {
  val rs1 = Input(UInt(DATA_W.W))
  val rs2 = Input(UInt(DATA_W.W))
}

class PathInterface extends Bundle {
  val in = new PathIn
  val out = new PathOut
}

class ISInterface extends PathInterface {
  val data = new PathData
}

class WBInterface extends PathInterface {
}

class BJUPathInterface extends Bundle {
  val in = new PathIn
  val data = new PathData
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
  val order         = Output(UInt(ISSUE_NUM_W))
}

class ISOut extends Path {
  // params
  val a             = Output(UInt(XLEN.W))
  val b             = Output(UInt(XLEN.W))
  val imm           = Output(UInt(XLEN.W))
}

// Issue Stage
class ISStage extends Module {
  val io = IO(new Bundle {
    val issue_cnt = Input(UInt(QUEUE_LEN_w.W))
    val inst = Input(Vec(ISSUE_NUM, new Instruction))

    // To Param Fetcher 
    val forwarding_path_id  = Output(Vec(ISSUE_NUM, new ForwardingPathId))

    // To common FUs
    val path                = Flipped(Vec(TOT_PATH_NUM, new Path))
    val forwarding          = Flipped(Vec(TOT_PATH_NUM, new Forwarding))
    val actual_issue_cnt    = Output(UInt(ISSUE_NUM_W.W))

    // To BJU
    val branch_jump_id = Output(UInt(ALU_PATH_NUM_W.W))
    val delay_slot_pending = Output(Bool())
  })

  // Hazard Detect Logic  
  val filtered_inst = Wire(Vec(ISSUE_NUM, new Instruction))
  val inst = Wire(Vec(ISSUE_NUM, new Instruction))

  // For each instruction, decide which path it should go
  val target = Wire(Vec(ISSUE_NUM, UInt(PATH_W.W)))

  val issued = Wire(Vec(PATH_TYPE_NUM, Vec(ISSUE_NUM, Bool())))
  val raw = Wire(Vec(ISSUE_NUM, Bool()))
  val structural_hazard = Wire(Bool())

  // Begin
  io.actual_issue_cnt := ISSUE_NUM.U(ISSUE_NUM_W.W)

  def RAWPath(i: Int, j: Int) {
    io.forwarding_path(i).rs1 := TOT_PATH_NUM.U(TOT_PATH_NUM_W.W)
    io.forwarding_path(i).rs2 := TOT_PATH_NUM.U(TOT_PATH_NUM_W.W)

    when ((io.forwarding(j).write_target === 5.U & io.inst(i).dec.param_a === BitPat("b01?") |  // HiLo
           io.forwarding(j).write_target === io.inst(i).dec.param_a) &      // Same source
           io.forwarding(j).rd === io.inst(i).dec.rs1 &                     // Same ID
          (io.inst(i).dec.param_a =/= 0.U | io.inst(i).dec.rs1 =/= 0.U)) {  // Not Reg 0
//    when (io.forwarding(j).ready) {
        io.forwarding_path(i).rs1 := j.U
//    }
//    .otherwise {
//      raw(i) := true.B
//    }
    }
    when ( io.forwarding(j).write_target === 0.U &    // rs2 ONLY relies on regs
           io.forwarding(j).rd === inst(i).dec.rs2 &  // Same ID
           io.inst(i).dec.rs2 =/= 0.U) {              // Not Reg 0
//    when (io.forwarding(j).ready) {
        io.forwarding_path(i).rs2 := j.U
//    }
//    .otherwise {
//      raw(i) := true.B
//    }
    }
  }

  def RAWInst(i: Int, k: Int) {
    when ((io.inst(k).write_target === 5.U & io.inst(i).dec.param_a === BitPat("b01?") |  // HiLo
           io.inst(k).write_target === io.inst(i).dec.param_a) &            // Same source
           io.inst(k).dec.rd === io.inst(i).dec.rs1 &                       // Same id
          (io.inst(i).dec.param_a =/= 0.U | io.inst(i).dec.rs1 =/= 0.U)) {  // Not Reg 0
      raw(i) := true.B
    }

    when ( io.inst(k).dec.write_target === 0.U &    // rs2 ONLY relies on regs
           io.inst(k).dec.rd === inst(i).dec.rs2 &  // Same id
           io.inst(i).dec.rs2 =/= 0.U) {
      raw(i) := true.B
    }
  }

  structural_hazard := false.B
  for (j <- 0 until TOT_PATH_NUM)
    when (!io.forwarding(j).ready) {
      structural_hazard := true.B
    }

  // i is the id of the currect instruction to be detected
  for (i <- 0 until ISSUE_NUM) {
    // Detect hazards

    // RAW Data hazard
    // From path (issued)
    raw(i) := false.B
    for (j <- 0 until TOT_PATH_NUM) {
      RAWPath(i, j)
    }
    // From queue (going to issue)
    for (k <- 0 until i) {
      RAWInst(i, k)
    }

    // Structural hazard
    // Target filter
    target(i) := Mux(
      (raw(i) | structural_hazard | (i.U >= io.issue_cnt)),
      0.U,
      io.inst(i).dec.path
    )

    issued(0)(i) := false.B
    for (path_type <- 1 until PATH_TYPE_NUM)
      when(issued(path_type)(i)) {
        issued(0)(i) := true.B
      }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    when(i.U(ISSUE_NUM_W.W) >= io.actual_issue_cnt) {
      filtered_inst(i) := NOPBubble()
    }
      .otherwise {
        filtered_inst(i) := io.inst(i)
      }
  }

  for (i <- ISSUE_NUM - 1 to 0 by -1) {
    // If an instruction cannot be issued
    // Mark its ID
    // Every following instruction (with a greater ID) will be replaced by an NOP Bubble
    when(issued(0)(i) === false.B) {
      io.actual_issue_cnt := i.U(ISSUE_NUM_W.W)
    }
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
  io.delay_slot_pending := false.B
  for (j <- 0 until ALU_PATH_NUM) {
    // Branch
    when (io.path(j).in.inst.dec.next_pc =/= PC4) {
      io.branch_jump_id := j.U(ALU_PATH_NUM_W.W)
      io.delay_slot_pending := (io.path(j).order + 1.U) === io.actual_issue_cnt
    }
  }
}

