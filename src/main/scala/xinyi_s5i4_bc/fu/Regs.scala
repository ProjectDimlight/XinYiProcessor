package xinyi_s5i4_bc.fu

import chisel3._
import config.config._
import utils._

class RegReadInterface extends Bundle {
  val rs1   = Input(UInt(REG_ID_W.W))
  val rs2   = Input(UInt(REG_ID_W.W))
  val data1 = Output(UInt(XLEN.W))
  val data2 = Output(UInt(XLEN.W))
}

class RegWriteInterface extends Bundle {
  val we    = Input(Bool())
  val rd    = Input(UInt(REG_ID_W.W))
  val data  = Input(UInt(XLEN.W))
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
  val reginit = VecInit(Seq.fill(32)(0.U(XLEN.W)))
  if (DEBUG) {
    reginit(16) := (DEBUG_TEST_CASE - 1).U
    reginit(19) := (DEBUG_TEST_CASE - 1).U
  }

  val regfile = RegInit(reginit)


  def WriteForward(rs: UInt, data: UInt) {
    // data := regfile(rs)
    data := MuxLookupBi(
      rs,
      0.U(32.W),
      Array(
        1.U -> regfile(1),
        2.U -> regfile(2),
        3.U -> regfile(3),
        4.U -> regfile(4),
        5.U -> regfile(5),
        6.U -> regfile(6),
        7.U -> regfile(7),
        8.U -> regfile(8),
        9.U -> regfile(9),

        10.U -> regfile(10),
        11.U -> regfile(11),
        12.U -> regfile(12),
        13.U -> regfile(13),
        14.U -> regfile(14),
        15.U -> regfile(15),
        16.U -> regfile(16),
        17.U -> regfile(17),
        18.U -> regfile(18),
        19.U -> regfile(19),
        
        20.U -> regfile(20),
        21.U -> regfile(21),
        22.U -> regfile(22),
        23.U -> regfile(23),
        24.U -> regfile(24),
        25.U -> regfile(25),
        26.U -> regfile(26),
        27.U -> regfile(27),
        28.U -> regfile(28),
        29.U -> regfile(29),
        
        30.U -> regfile(30),
        31.U -> regfile(31),
      )
    )

    for (i <- 0 until ISSUE_NUM) {
      when (io.write(i).we & (io.write(i).rd === rs) & (rs =/= 0.U)) {
        data := io.write(i).data
      }
    }
  }

  for (i <- 0 until ISSUE_NUM) {
    WriteForward(io.read(i).rs1, io.read(i).data1)
    WriteForward(io.read(i).rs2, io.read(i).data2)
  }


  for (i <- 0 until ISSUE_NUM) {
    when (io.write(i).we & (io.write(i).rd =/= 0.U)) {
      regfile(io.write(i).rd) := io.write(i).data
    }
  }
}
