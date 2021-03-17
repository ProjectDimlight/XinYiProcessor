package xinyi_s5i4_bc.stages

import chisel3._
import wrap._

class PCIFReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val if_in = Flipped(new IFIn)
  })

  val pc = RegInit(start_addr.U(lgc_addr_w.W))
  
  io.if_in.pc := pc
}

class IFIDReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val if_out = Flipped(new IFOut)
    val id_in = Flipped(Vec(fetch_num, new IDIn))
  })

  val reg = RegNext(io.if_out)

  for (i <- 0 until fetch_num) {
    io.id_in(i).pc := reg.pc + (i * 4).U(data_w.W)
    io.id_in(i).inst := reg.inst((i + 1) * data_w - 1, i * data_w)
  }
}