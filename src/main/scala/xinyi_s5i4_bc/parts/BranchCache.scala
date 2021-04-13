package xinyi_s5i4_bc.parts

import chisel3._
import wrap._

class BranchCacheRecord extends Bundle with XinYiConfig {
  
}

class BranchCacheIn extends Bundle with XinYiConfig {
  val branch = Input(Bool())
  val delay_slot_pending = Input(Bool())
  val target = Input(UInt(lgc_addr_w.W))
}

class BranchCacheOut extends Bundle with XinYiConfig {
  val inst = Output(Vec(fetch_num, new Instruction))
  val overwrite = Output(new Bool)
  val flush = Output(new Bool)
  val keep_delay_slot = Output(new Bool)
}

class BranchCache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in  = new BranchCacheIn
    val out = new BranchCacheOut
    val branch_cached_pc = Output(UInt(lgc_addr_w.W))
  })

  // Default
  for (i <- 0 until fetch_num) {
    io.out.inst(i) := NOPBubble()
  }
  io.out.overwrite := false.B
  io.out.flush := false.B
  io.out.keep_delay_slot := false.B

  val state_reg = RegInit(0.U(bc_line_size_w.W))
  val state = Wire(UInt(bc_line_size_w.W))
  val hit = false.B

  // As the dummy BC always misses, next PC of PC stage should be target
  // If BC hits, next PC should be target + bc_line_size * fetch_num
  when (io.in.branch) {
    io.out.flush := true.B
    io.out.keep_delay_slot := io.in.delay_slot_pending
    state := bc_line_size.U
  }
  .otherwise {
    state := state_reg
  }
  state_reg := Mux(state =/= 0.U, state - 1.U, state)
  io.branch_cached_pc := Mux(hit, io.in.target + (bc_line_size * fetch_num).U, io.in.target)

  // TODO: Dummy branch cache, do nothing
  when (state =/= 0.U) {
    // If hit, the queue will be overwritten with the contents of the BC
    // If miss, it will be filled with NOPBubbles, by default
    io.out.overwrite := true.B
  }
}
