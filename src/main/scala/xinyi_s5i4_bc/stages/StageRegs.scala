package xinyi_s5i4_bc.stages

import chisel3._

import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.fu._
import ControlConst._
import config.config._
import EXCCodeConfig._

class PCIFReg extends Module {
  val io = IO(new Bundle {
    val pc_out = Input(UInt(LGC_ADDR_W.W))
    val if_in  = Flipped(new IFIn)

    val stall = Input(Bool())
  })

  val pc = RegInit((if (DEBUG) DEBUG_BOOT_ADDR else BOOT_ADDR).U(LGC_ADDR_W.W))
  val stall = RegInit(false.B)

  when(!io.stall) {
    pc := io.pc_out
  }
  stall := io.stall

  io.if_in.pc := pc
}

class IFIDReg extends Module {
  val io = IO(new Bundle {
    val if_out = Flipped(new IFOut)
    val id_in  = Flipped(Vec(FETCH_NUM, new IDIn))

    val stall = Input(Bool())
    val flush = Input(Bool())
  })

  var init = Wire(new IFOut)
  init.pc := 0.U(LGC_ADDR_W.W)
  init.inst := 0.U(XLEN.W)
  
  val reg = RegInit(init)
  val flush_reg = RegInit(false.B)

  when (io.flush) {
    when (io.stall) {
      flush_reg := true.B
    }
    reg := init
  }
  .elsewhen (!io.stall) {
    reg := Mux(flush_reg, init, io.if_out)
    flush_reg := false.B
  }

  for (i <- 0 until FETCH_NUM) {
    io.id_in(i).pc := reg.pc + (i * 4).U(XLEN.W)
    io.id_in(i).inst := reg.inst((i + 1) * XLEN - 1, i * XLEN)
  }
}

// Issue Queue
class IssueQueue extends Module {
  val io = IO(new Bundle {
    val in               = Input(Vec(FETCH_NUM, Flipped(new Instruction)))
    val bc               = Flipped(new BranchCacheOut)
    val actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))
    val full             = Output(Bool())
    val issue_cnt        = Output(UInt(ISSUE_NUM_W.W))
    val inst             = Output(Vec(ISSUE_NUM, new Instruction))

    val stall = Input(Bool())
    val flush = Input(Bool())
  })

  def Step(base: UInt, offset: UInt) = {
    (base + offset)(QUEUE_LEN_W - 1, 0)
  }

  // Queue logic

  val queue    = RegInit(VecInit(Seq.fill(QUEUE_LEN)(0.U.asTypeOf(new Instruction))))
  val head     = RegInit(0.U(QUEUE_LEN_W.W))
  val tail     = RegInit(0.U(QUEUE_LEN_W.W))
  val head_n   = Wire(UInt(QUEUE_LEN_W.W)) // Next Head
  val tail_b   = Wire(UInt(QUEUE_LEN_W.W)) // Actual Tail Base
  val in_size  = Wire(UInt(QUEUE_LEN_W.W))
  val out_size = Wire(UInt(QUEUE_LEN_W.W))

  tail_b := Mux(
    io.bc.flush,
    head_n,
    tail
  )
  in_size := tail_b - head_n

  // Input
  when(io.flush) {
    tail := head_n
    io.full := false.B
  }
  .elsewhen (in_size < (QUEUE_LEN - FETCH_NUM).U) {
    when (!io.stall) {
      for (i <- 0 until FETCH_NUM) {
        queue(tail_b + i.U(QUEUE_LEN_W.W)) := Mux(io.bc.overwrite, io.bc.inst(i), io.in(i))
      }
      tail := Step(tail_b, FETCH_NUM.U(QUEUE_LEN_W.W))
    }
    .otherwise {
      tail := tail_b
    }
    io.full := false.B
  }
  .otherwise {
    tail := tail_b
    io.full := true.B
  }

  // Output 
  out_size := tail - head

  io.issue_cnt := out_size
  for (i <- 0 until ISSUE_NUM) {
    // If i > issue_cnt, the instruction path will be 0 (Stall)
    // So there is no need to clear the inst Vec here
    io.inst(i) := queue(head + i.U(QUEUE_LEN_W.W))

    // Issue until Delay Slot
    // If the Branch itself is not issued, it will be re-issued in the next cycle.
    // If the Branch is issued but the Delay Slot is not, 
    // The BJU would generate a branch_cache_overwrite signal along with a keep_delay_slot, 
    // Ensuring that the rest of the queue will be cleared while the Delay Slot works as is.
    when(io.inst(i).dec.next_pc =/= PC4) {
      // If the delay slot is already fetched (into the queue)
      when((i + 2).U <= out_size) {
        io.issue_cnt := (i + 2).U
      }
      // The delay slot is not in the queue yet
      // Stall
      .otherwise {
        io.issue_cnt := 0.U
      }
    }
  }

  head_n := Step(head, io.actual_issue_cnt)
  head := head_n
}

class ISBJUReg extends Module with ALUConfig {
  val io = IO(new Bundle{
    val is_path               = Input(new ISOut)
    val is_branch_next_pc     = Input(UInt(NEXT_PC_W.W))
    val is_delay_slot_pending = Input(Bool())
    val stall                 = Input(Bool())
    val flush                 = Input(Bool())

    val fu_path               = Output(new FUIn)
    val fu_branch_next_pc     = Output(UInt(NEXT_PC_W.W))
    val fu_delay_slot_pending = Output(Bool())
  })

  val init  = Wire(new ISOut)
  init.write_target := DXXX
  init.rd := 0.U
  init.fu_ctrl := ALU_ADD
  init.pc := 0.U
  init.order := ISSUE_NUM.U
  init.a := 0.U
  init.b := 0.U
  init.imm := 0.U
  init.is_delay_slot := false.B

  val reg_path                = RegInit(init)
  val reg_branch_next_pc      = RegInit(0.U(NEXT_PC_W.W))
  val reg_delay_slot_pending  = RegInit(false.B)
  
  when(io.flush) {
    reg_path                  := init
    reg_branch_next_pc        := 0.U(NEXT_PC_W.W)
    reg_delay_slot_pending    := false.B
  }
  .otherwise {
    when(!io.stall) {
      reg_path                := io.is_path
      reg_branch_next_pc      := io.is_branch_next_pc
      reg_delay_slot_pending  := io.is_delay_slot_pending
    }
  }

  io.fu_path                  := reg_path
  io.fu_branch_next_pc        := reg_branch_next_pc
  io.fu_delay_slot_pending    := reg_delay_slot_pending
}

class ISFUReg extends Module with ALUConfig {
  val io = IO(new Bundle {
    val is_out              = Flipped(Vec(TOT_PATH_NUM, new ISOut))
    val is_actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))
    val stall               = Input(Bool())
    val flush               = Input(Bool())

    val fu_in               = Output(Vec(TOT_PATH_NUM, new FUIn))
    val fu_actual_issue_cnt = Output(UInt(ISSUE_NUM_W.W))
    val stalled             = Output(Bool())
  })

  val init = Wire(Vec(TOT_PATH_NUM, new ISOut))
  for (i <- 0 until TOT_PATH_NUM) {
    init(i).write_target := DXXX
    init(i).rd := 0.U
    init(i).fu_ctrl := ALU_ADD
    init(i).pc := 0.U
    init(i).order := ISSUE_NUM.U
    init(i).a := 0.U
    init(i).b := 0.U
    init(i).imm := 0.U
    init(i).is_delay_slot := false.B
  }

  val reg_out              = RegInit(init)
  val reg_actual_issue_cnt = RegInit(ISSUE_NUM.U)
  val reg_stall            = RegInit(false.B)

  when(io.flush) {
    reg_out := init
    reg_actual_issue_cnt := 0.U
    reg_stall := false.B
  }
  .otherwise {
    reg_stall := io.stall
    when(!io.stall) {
      reg_out := io.is_out
      reg_actual_issue_cnt := io.is_actual_issue_cnt
    }
  }

  io.fu_in := reg_out
  io.fu_actual_issue_cnt := reg_actual_issue_cnt
  io.stalled := reg_stall
}

class FUWBReg extends Module {
  val io = IO(new Bundle {
    val sorted_fu_out        = Input(Vec(ISSUE_NUM, new FUOut))
    val fu_exception_order   = Input(UInt(ISSUE_NUM_W.W))
    val fu_exception_handled = Input(Bool())
    val fu_exception_target  = Input(UInt(LGC_ADDR_W.W))

    val fu_exc_info          = Input(new ExceptionInfo)

    val stall                = Input(Bool())
    val flush                = Input(Bool())

    val wb_in                = Output(Vec(ISSUE_NUM, new FUOut))
    val wb_exception_order   = Output(UInt(ISSUE_NUM_W.W))
    val wb_exception_handled = Output(Bool())
    val wb_exception_target  = Output(UInt(LGC_ADDR_W.W))
    val wb_exc_info          = Output(new ExceptionInfo)
  })

  val exc_info_init = Wire(new ExceptionInfo)
  exc_info_init.pc := 0.U
  exc_info_init.exc_code := NO_EXCEPTION
  exc_info_init.data := 0.U
  exc_info_init.in_branch_delay_slot := false.B

  val reg_out                = RegInit(VecInit(Seq.fill(ISSUE_NUM)(FUOutBubble())))
  val reg_exception_order    = RegInit(0.U(ISSUE_NUM_W.W))
  val reg_exception_handled  = RegInit(false.B)
  val reg_exception_target   = RegInit(0.U(LGC_ADDR_W.W))
  val reg_exc_info           = RegInit(exc_info_init)

  reg_out               := io.sorted_fu_out
  reg_exception_order   := Mux(io.flush | io.stall, 0.U, io.fu_exception_order)
  reg_exception_handled := Mux(io.flush | io.stall, 0.U, io.fu_exception_handled)
  reg_exception_target  := io.fu_exception_target
  reg_exc_info          := io.fu_exc_info


  io.wb_in := reg_out
  io.wb_exception_order   := reg_exception_order
  io.wb_exception_handled := reg_exception_handled
  io.wb_exception_target  := reg_exception_target
  io.wb_exc_info          := reg_exc_info
}

class InterruptReg extends Module {
  val io = IO(new Bundle {
    val fu_pc               = Input(UInt(LGC_ADDR_W.W))
    val fu_is_delay_slot    = Input(Bool())
    val fu_actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))

    val wb_epc       = Output(UInt(LGC_ADDR_W.W))
  })

  val pc_reg = RegInit(0.U(LGC_ADDR_W.W))
  when (io.fu_actual_issue_cnt =/= 0.U) {
    pc_reg := Mux(io.fu_is_delay_slot, io.fu_pc - 4.U, io.fu_pc)
  }
  io.wb_epc := pc_reg
}
