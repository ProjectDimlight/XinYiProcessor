package xinyi_s5i4_bc.fu

import chisel3._
import config.config._

class RegReadInterface extends Bundle {
  val rs1   = Input(UInt(REG_ID_W.W))
  val rs2   = Input(UInt(REG_ID_W.W))
  val data1 = Output(UInt(DATA_W.W))
  val data2 = Output(UInt(DATA_W.W))
}

class RegWriteInterface extends Bundle {
  val we    = Input(Bool())
  val rd    = Input(UInt(REG_ID_W.W))
  val data  = Input(UInt(DATA_W.W))
}

/**
 * @Module Regs
 */
class Regs extends Module {
  val io = IO(new Bundle {
    val read    = Vec(ISSUE_NUM, new RegReadInterface)
    val write   = Vec(ISSUE_NUM, new RegWriteInterface)
  })

  // register file
  val regfile = Reg(Vec(32, UInt(DATA_W.W)))

  def WriteForward(rs: UInt, data: UInt) {
    data := regfile(rs)
    for (i <- 0 until ISSUE_NUM) {
      when (io.write(i).we & (io.write(i).rd === rs)) {
        data := io.write(i).data
      }
    }
  }

  for (i <- 0 until ISSUE_NUM) {
    WriteForward(io.read(i).rs1, io.read(i).data1)
    WriteForward(io.read(i).rs2, io.read(i).data2)
  }


  for (i <- 0 until ISSUE_NUM) {
    when (io.write(i).we) {
      regfile(io.write(i).rd) := io.write(i).data
    }
  }
}
