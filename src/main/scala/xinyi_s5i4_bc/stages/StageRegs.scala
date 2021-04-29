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

  val pc = RegInit(BOOT_ADDR.U(LGC_ADDR_W.W))
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
    Mux(
      base +& offset >= QUEUE_LEN.U,
      base + offset - QUEUE_LEN.U,
      base + offset
    )
  }

  // Queue logic

  val queue    = RegInit(VecInit(Seq.fill(QUEUE_LEN)(0.U.asTypeOf(new Instruction))))
  val head     = RegInit(0.U(QUEUE_LEN_w.W))
  val tail     = RegInit(0.U(QUEUE_LEN_w.W))
  val head_n   = Wire(UInt(QUEUE_LEN_w.W)) // Next Head
  val tail_b   = Wire(UInt(QUEUE_LEN_w.W)) // Actual Tail Base
  val in_size  = Wire(UInt(QUEUE_LEN_w.W))
  val out_size = Wire(UInt(QUEUE_LEN_w.W))

  tail_b := Mux(
    io.bc.flush,
    Mux(io.bc.keep_delay_slot, Step(head_n, 1.U(QUEUE_LEN_w.W)), head_n),
    tail
  )
  in_size := Mux(
    tail_b >= head_n,
    tail_b - head_n,
    tail_b + QUEUE_LEN.U - head_n
  )

  // Input
  when(io.flush) {
    tail := head_n
    io.full := false.B
  }
  .elsewhen (in_size < (QUEUE_LEN - FETCH_NUM).U) {
    when (!io.stall) {
      for (i <- 0 until FETCH_NUM) {
        when(tail_b + i.U(QUEUE_LEN_w.W) < QUEUE_LEN.U(QUEUE_LEN_w.W)) {
          queue(tail_b + i.U(QUEUE_LEN_w.W)) := Mux(io.bc.overwrite, io.bc.inst(i), io.in(i))
        }
        .otherwise {
          queue(tail_b + ((1 << QUEUE_LEN_w) + i - QUEUE_LEN).U(QUEUE_LEN_w.W)) := Mux(io.bc.overwrite, io.bc.inst(i), io.in(i))
        }
      }
      tail := Step(tail_b, FETCH_NUM.U(QUEUE_LEN_w.W))
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
  out_size := Mux(
    tail >= head,
    tail - head,
    tail + QUEUE_LEN.U - head
  )


  io.issue_cnt := out_size
  for (i <- 0 until ISSUE_NUM) {
    // If i > issue_cnt, the instruction path will be 0 (Stall)
    // So there is no need to clear the inst Vec here
    when(head + i.U(QUEUE_LEN_w.W) < QUEUE_LEN.U(QUEUE_LEN_w.W)) {
      io.inst(i) := queue(head + i.U(QUEUE_LEN_w.W))
    }
    .otherwise {
      io.inst(i) := queue(head + ((1 << QUEUE_LEN_w) + i - QUEUE_LEN).U(QUEUE_LEN_w.W))
    }

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
    val fu_out              = Input(Vec(TOT_PATH_NUM, new FUOut))
    val fu_actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))
    val stall               = Input(Bool())
    val flush               = Input(Bool())

    val wb_in               = Output(Vec(TOT_PATH_NUM, new FUOut))
    val wb_actual_issue_cnt = Output(UInt(ISSUE_NUM_W.W))
  })

  val reg_out              = RegInit(VecInit(Seq.fill(TOT_PATH_NUM)(FUOutBubble())))
  val reg_actual_issue_cnt = RegInit(0.U(ISSUE_NUM_W.W))

  reg_out              := io.fu_out
  reg_actual_issue_cnt := Mux(io.flush | io.stall, 0.U, io.fu_actual_issue_cnt)

  io.wb_in := reg_out
  io.wb_actual_issue_cnt := reg_actual_issue_cnt
}

class InterruptReg extends Module {
  val io = IO(new Bundle {
    val fu_pc               = Input(UInt(LGC_ADDR_W.W))
    val fu_is_delay_slot    = Input(Bool())
    val fu_actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))
    val fu_interrupt        = Input(Vec(8, Bool()))

    val wb_epc       = Output(UInt(LGC_ADDR_W.W))
    val wb_interrupt = Output(Vec(8, Bool()))
  })

  val pc_reg = RegInit(0.U(LGC_ADDR_W.W))
  when (io.fu_actual_issue_cnt =/= 0.U) {
    pc_reg := Mux(io.fu_is_delay_slot, io.fu_pc - 4.U, io.fu_pc)
  }

  val interrupt_reg = RegInit(VecInit(Seq.fill(8)(false.B)))
  interrupt_reg := io.fu_interrupt

  io.wb_epc := pc_reg
  io.wb_interrupt := interrupt_reg
}
