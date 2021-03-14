package xinyi_s5i4_bc.stages

import chisel3._
import wrap._

class PCIFReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val if_in = Flipped(new IFIn)
  })

  val pc = RegInit(start_addr.U(addrw.W))
  
  io.if_in.pc := pc
}

class IFIDReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val if_out = Flipped(new IFOut)
    val id_in = Flipped(new IDIn)
  })

  val reg = RegNext(io.if_out)
  
  id_in := reg
}