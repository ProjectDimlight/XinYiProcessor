package xinyi_s5i4_bc.caches

import chisel3._
import wrap._

class CacheInterface extends Bundle with XinYiConfig {
  val addr = Input (UInt(addrw.W))
  val din  = Input (UInt(dataw.W))
  val dout = Output(UInt(dataw.W))
}

class ICache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val cpu = new CacheInterface
  })

  io.cpu.dout := 0.U(dataw.W)
}