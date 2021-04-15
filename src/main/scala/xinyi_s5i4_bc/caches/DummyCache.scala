package xinyi_s5i4_bc.caches

import chisel3._
import config.config._

class DummyCache(val addr_w: Int, val cache_w: Int) extends Module {
  val io = IO(new Bundle{
    val upper = new RAMInterface(addr_w, cache_w)
    val lower = Flipped(new RAMInterface(addr_w, cache_w))
  })

  // TODO: Debug
  // This is to prevent Chisel from
  // optimizing the entire decoder
  // Remove this when Cache is ready
  io.lower.addr  := io.upper.addr
  io.lower.din   := 0.U(cache_w.W)
  io.upper.dout := io.lower.dout
}