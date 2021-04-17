package xinyi_s5i4_bc.caches

import chisel3._
import config.config._

class ROMInterface(val addr_w: Int, val cache_w: Int) extends Bundle {
  val rd   = Input (Bool())
  val addr = Input (UInt(addr_w.W))
  val dout = Output(UInt(cache_w.W))
}

class RAMInterface(val addr_w: Int, val cache_w: Int) extends Bundle {
  val rd   = Input (Bool())
  val wr   = Input (Bool())
  val addr = Input (UInt(addr_w.W))
  val din  = Input (UInt(cache_w.W))
  val dout = Output(UInt(cache_w.W))
}