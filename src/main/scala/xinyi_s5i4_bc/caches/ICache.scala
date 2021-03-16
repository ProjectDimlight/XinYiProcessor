package xinyi_s5i4_bc.caches

import chisel3._
import wrap._

class ROMInterface extends Bundle with XinYiConfig {
  val addr = Input (UInt(addrw.W))
  val dout = Output(UInt(dataw.W))
}

class RAMInterface extends Bundle with XinYiConfig {
  val addr = Input (UInt(addrw.W))
  val din  = Input (UInt(dataw.W))
  val dout = Output(UInt(dataw.W))
}

class ICache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val cpu = new RAMInterface
    val l2 = Flipped(new RAMInterface)
  })

  // TODO: Debug
  // This is to prevent Chisel from
  // optimizing the entire decoder
  // Remove this when Cache is ready
  io.l2.addr  := io.cpu.addr
  io.l2.din   := 0.U(32.W)
  io.cpu.dout := io.l2.dout
}