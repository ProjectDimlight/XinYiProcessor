package xinyi_s5i4_bc.caches

import chisel3._
import config.config._

class ICacheAXI extends Bundle {
  val addr_in     = Output(UInt(PHY_ADDR_W.W))
  val en          = Output(Bool())
  val addr_out    = Input(UInt(PHY_ADDR_W.W))
  val data        = Input(UInt(DATA_W.W))
  val stall       = Input(Bool())
  val valid       = Input(Bool())
}

class DummyICache extends Module {
  val io = IO(new Bundle{
    val upper = new RAMInterface(LGC_ADDR_W, L1_W)
    val lower = new ICacheAXI
  
    val stall     = Input(Bool())
    val stall_req = Output(Bool())
  })

  io.lower.addr_in  := io.upper.addr
  io.lower.en       := !io.stall
  
  when (io.lower.valid & !io.lower.stall)
  {
    io.upper.dout   := io.lower.data
    io.stall_req    := false.B
  }
  .otherwise {
    io.upper.dout   := 0.U
    io.stall_req    := true.B
  }
}