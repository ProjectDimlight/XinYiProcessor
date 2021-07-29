package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import utils._

import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.fu._
import ControlConst._
import config.config._
import EXCCodeConfig._

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
    when (io.exception.enable) {
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
    (io.pc & 0xFFFFFFFCL.U) + Mux(io.pc(2), 4.U, 8.U),
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
class IFStage extends Module with TLBConfig {
  val io = IO(new Bundle {
    val in       = new IFIn
    val out      = new IFOut

    val tlb      = Flipped(new TLBLookupInterface)
    val cache    = Flipped(new ICacheCPUIO)
    
    val full     = Input(Bool())

    val tlb_miss = Output(Bool())
    val tlb_addr = Output(UInt(LGC_ADDR_W.W))
  })

  // TLB
  val lgc_addr = io.in.pc
  io.tlb.vpn2 := lgc_addr(LGC_ADDR_W-1, PAGE_SIZE_W)
  
  val item = Mux(lgc_addr(PAGE_SIZE_W), io.tlb.entry.i1, io.tlb.entry.i0)
  
  val addr = Mux(
    lgc_addr(31, 30) === 2.U,
    lgc_addr & 0x1FFFFFFF.U,
    Cat(item.pfn, lgc_addr(PAGE_SIZE_W-1, 0))
  )

  io.tlb_miss := (lgc_addr(31, 30) =/= 2.U) & io.tlb.miss
  io.tlb_addr := lgc_addr

  // ICache
  io.cache.rd := !io.full
  io.cache.addr := addr
  // TODO connect to real flush signal
  io.cache.flush := false.B

  // Output to IF-ID Regs
  io.out.pc := io.in.pc
  io.out.inst := io.cache.data
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
    val pc4 = io.in(i).pc + 4.U
    io.out(i).imm := MuxCase(
      io.in(i).inst(15, 0),
      Array(
         (decoder.io.dec.next_pc === Branch)  -> (signed_x4.asUInt() + pc4),
         (decoder.io.dec.next_pc === Jump)    -> Cat(pc4(31, 28), io.in(i).inst(25, 0), 0.U(2.W)),
        ((decoder.io.dec.fu_ctrl === ALU_SLL  |
          decoder.io.dec.fu_ctrl === ALU_SRL  |
          decoder.io.dec.fu_ctrl === ALU_SRA
        ) & decoder.io.dec.path === PathALU) -> io.in(i).inst(10, 6),
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
    val issue_cnt = Input(UInt(QUEUE_LEN_W.W))
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

  val issue_cnt = Wire(UInt(QUEUE_LEN_W.W))

  // Begin

  issue_cnt := Mux(
    io.branch_cache_out.flush,
    io.branch_cache_out.keep_delay_slot,
    io.issue_cnt
  )
  io.actual_issue_cnt := ISSUE_NUM.U(ISSUE_NUM_W.W)

  def RAWPath(i: Int, j: Int) {

    when (
      io.forwarding(j).write_target === DHiLo & io.inst(i).dec.param_a === BitPat("b01?") | // HiLo
      io.forwarding(j).write_target === DReg  & io.inst(i).dec.param_a === AReg &           // Regs
      io.forwarding(j).rd === io.inst(i).dec.rs1 & io.inst(i).dec.rs1 =/= 0.U               // Same ID, Not 0
    ) {
      io.forwarding_path_id(i).rs1 := j.U
    }
    when (
      io.forwarding(j).write_target === io.inst(i).dec.param_a &                            // Same source (implicit not HiLo)
      io.forwarding(j).write_target =/= DReg                                                // Not Regs
    ) {
      raw(i) := true.B
    }

    when (
      io.forwarding(j).write_target === DReg &     // rs2 ONLY relies on regs
      io.forwarding(j).rd === io.inst(i).dec.rs2 & // Same ID
      io.inst(i).dec.rs2 =/= 0.U                   // Not 0
    ) {
        io.forwarding_path_id(i).rs2 := j.U
    }
  }

  def RAWInst(i: Int, k: Int) {
    when (
      io.inst(k).dec.write_target === 5.U & io.inst(i).dec.param_a === BitPat("b01?") | // HiLo
      io.inst(k).dec.write_target === io.inst(i).dec.param_a &                          // Same source
     (io.inst(k).dec.rd === io.inst(i).dec.rs1 & io.inst(i).dec.rs1 =/= 0.U |           // Same id, Not 0
      io.inst(i).dec.param_a =/= AReg)                                                  // Not Regs
    ) {
      raw(i) := true.B
    }

    when (
      io.inst(k).dec.write_target === 0.U &       // rs2 ONLY relies on regs
      io.inst(k).dec.rd === io.inst(i).dec.rs2 &  // Same ID
      io.inst(i).dec.rs2 =/= 0.U                  // Not 0
    ) {
      raw(i) := true.B
    }

    // In fact this is WAR
    when (
      io.inst(k).dec.path === PathLSU & 
      io.inst(i).dec.path === PathLSU &
      io.inst(i).dec.write_target === DMem
    ) {
      raw(i) := true.B
    }
  }

  def WAWInst(i: Int, k: Int) {
    when (
      io.inst(k).dec.write_target === DHiLo & io.inst(i).dec.write_target === BitPat("b01?") |  // HiLo
      io.inst(i).dec.write_target === DHiLo & io.inst(k).dec.write_target === BitPat("b01?") |  // HiLo
      io.inst(k).dec.write_target === io.inst(i).dec.write_target &                             // Same source
     (io.inst(i).dec.write_target =/= DReg | io.inst(k).dec.rd === io.inst(i).dec.rd & io.inst(i).dec.rd =/= 0.U)  // Not Reg 0 
    ) { 
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

// FU sorting stage
class FUStage extends Module with CP0Config {
  val io = IO(new Bundle {
    val fu_out               = Input(Vec(TOT_PATH_NUM, new FUOut))
    val fu_actual_issue_cnt  = Input(UInt(ISSUE_NUM_W.W))
    val incoming_epc         = Input(UInt(LGC_ADDR_W.W))
    val incoming_interrupt   = Input(Vec(8, Bool()))
  
    val if_tlb_miss          = Input(Bool())
    val if_tlb_addr          = Input(UInt(LGC_ADDR_W.W))

    val sorted_fu_out        = Output(Vec(ISSUE_NUM, new FUOut))
    val fu_exception_order   = Output(UInt(ISSUE_NUM_W.W))
    val fu_exception_handled = Output(Bool())
    val fu_exception_target  = Output(UInt(LGC_ADDR_W.W))

    val exc_info             = Output(new ExceptionInfo)
  })

  // check if exception handled
  //    if any exception found in WB, forall will be False
  // and the whole predicate will be True
  
  io.fu_exception_handled := false.B
  io.fu_exception_target  := 0.U

  // Mux: from Paths to Issues
  val issue_vec = Wire(Vec(ISSUE_NUM, new FUOut))
  for (i <- 0 until ISSUE_NUM) {
    issue_vec(i) := FUOutBubble()

    when (i.U < io.fu_actual_issue_cnt) {
      for (j <- 0 until TOT_PATH_NUM) {
        when (io.fu_out(j).order === i.U) {
          issue_vec(i) := io.fu_out(j)
        }
      }
    }
  }

  io.sorted_fu_out := issue_vec

  val exception_pc = Mux(io.fu_actual_issue_cnt === 0.U, io.incoming_epc, issue_vec(0).pc)
  
  // generate exception order
  io.fu_exception_order := io.fu_actual_issue_cnt
  
  io.exc_info.pc := 0.U
  io.exc_info.exc_code := NO_EXCEPTION
  io.exc_info.data := 0.U
  io.exc_info.in_branch_delay_slot := false.B
  io.exc_info.eret := false.B

  // IF stage tlb miss exception
  when (io.if_tlb_miss) {
    io.exc_info.pc := exception_pc
    io.exc_info.exc_code := EXC_CODE_TLBL
    io.exc_info.data := io.if_tlb_addr
    io.exc_info.in_branch_delay_slot := false.B
    
    io.fu_exception_order := 0.U
    io.fu_exception_handled := true.B
    io.fu_exception_target  := EXCEPTION_ADDR.U
  }

  // normal exception handling
  for (i <- ISSUE_NUM - 1 to 0 by -1) {
    when (issue_vec(i).exception) {
      
      when (issue_vec(i).exc_code =/= EXC_CODE_ERET) {
        io.exc_info.pc := issue_vec(i).pc
        io.exc_info.exc_code := issue_vec(i).exc_code
        io.exc_info.data := issue_vec(i).exc_meta
        // some of the exception info should be passed by HI
        // for example: badvaddr in LSU
        io.exc_info.in_branch_delay_slot := issue_vec(i).is_delay_slot

        io.fu_exception_order := i.U
        io.fu_exception_handled := true.B
        io.fu_exception_target  := EXCEPTION_ADDR.U
      }
      .otherwise {
        io.fu_exception_order := (i+1).U

        io.fu_exception_handled := true.B
        io.fu_exception_target  := issue_vec(i).exc_meta
        io.exc_info.eret := true.B
      }
    }
    .elsewhen ((issue_vec(i).write_target === DCP0) &
               (issue_vec(i).rd === CP0_CAUSE_INDEX) &
               (issue_vec(i).data(9, 8) =/= 0.U)) {
       
      io.exc_info.pc := issue_vec(i).pc + 4.U
      io.exc_info.exc_code := EXC_CODE_INT
      io.exc_info.data := Cat(Seq(0.U(16.W), 0.U(6.W), issue_vec(i).data(9, 8), 0.U(8.W)))
      io.exc_info.in_branch_delay_slot := issue_vec(i).is_delay_slot

      io.fu_exception_order := i.U
      io.fu_exception_handled := true.B
      io.fu_exception_target  := EXCEPTION_ADDR.U
    }
  }
  
  // handle interrupt
  when (io.incoming_interrupt.asUInt().orR) {
    io.exc_info.pc := exception_pc
    io.exc_info.exc_code := EXC_CODE_INT
    io.exc_info.data := Cat(Seq(0.U(16.W), io.incoming_interrupt.asUInt, 0.U(8.W)))
    io.exc_info.in_branch_delay_slot := 0.U
    
    io.fu_exception_order := 0.U
    io.fu_exception_handled := true.B
    io.fu_exception_target  := EXCEPTION_ADDR.U
  }
}