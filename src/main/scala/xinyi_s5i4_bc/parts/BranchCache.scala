package xinyi_s5i4_bc.parts

import chisel3._
import config.config._

class BranchCacheRecord extends Bundle {
  
}

class BranchCacheIn extends Bundle {
  val branch = Input(Bool())
  val delay_slot_pending = Input(Bool())
  val target = Input(UInt(LGC_ADDR_W.W))
}

class BranchCacheOut extends Bundle {
  val inst = Output(Vec(FETCH_NUM, new Instruction))
  val overwrite = Output(new Bool)
  val flush = Output(new Bool)
  val keep_delay_slot = Output(new Bool)
}

class BranchCache extends Module {
  val io = IO(new Bundle{
    val in  = new BranchCacheIn
    val out = new BranchCacheOut
    val branch_cached_pc = Output(UInt(LGC_ADDR_W.W))
  })

  // Default
  for (i <- 0 until FETCH_NUM) {
    io.out.inst(i) := NOPBubble()
  }
  io.out.overwrite := false.B
  io.out.flush := false.B
  io.out.keep_delay_slot := false.B

  val state_reg = RegInit(0.U(BC_LINE_SIZE_W.W))
  val state = Wire(UInt(BC_LINE_SIZE_W.W))
  val hit = false.B

  // As the dummy BC always misses, next PC of PC stage should be target
  // If BC hits, next PC should be target + BC_LINE_SIZE * FETCH_NUM
  when (io.in.branch) {
    io.out.flush := true.B
    io.out.keep_delay_slot := io.in.delay_slot_pending
    state := BC_LINE_SIZE.U
  }
  .otherwise {
    state := state_reg
  }
  state_reg := Mux(state =/= 0.U, state - 1.U, state)
  io.branch_cached_pc := Mux(hit, io.in.target + (BC_LINE_SIZE * FETCH_NUM).U, io.in.target)

  // TODO: Dummy branch cache, do nothing
  when (state =/= 0.U) {
    // If hit, the queue will be overwritten with the contents of the BC
    // If miss, it will be filled with NOPBubbles, by default
    io.out.overwrite := true.B
  }
}
