package xinyi_s5i4_bc.caches

import chisel3._
import wrap._

class ROMInterface(val addr_w: Int, val cache_w: Int) extends Bundle with XinYiConfig {
  val addr = Input (UInt(addr_w.W))
  val dout = Output(UInt(cache_w.W))
}

class RAMInterface(val addr_w: Int, val cache_w: Int) extends Bundle with XinYiConfig {
  val addr = Input (UInt(addr_w.W))
  val din  = Input (UInt(cache_w.W))
  val dout = Output(UInt(cache_w.W))
}