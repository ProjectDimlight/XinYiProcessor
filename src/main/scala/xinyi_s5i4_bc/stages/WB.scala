package xinyi_s5i4_bc.stages


import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import xinyi_s5i4_bc.fu._

class WBIO extends Bundle {
  // fu result vector
  val fu_res_vec = Vec(TOT_PATH_NUM, Flipped(new FUOut))

  // issue param
  val actual_issue_cnt = Input(UInt(ISSUE_NUM_W.W))


  val write_hi_en   = Output(Bool())
  val write_lo_en   = Output(Bool())
  val write_hi_data = Output(UInt(XLEN.W))
  val write_lo_data = Output(UInt(XLEN.W))

  val write_regs_en   = Output(Bool())
  val write_regs_data = Output(UInt(XLEN.W))

  // TODO specify CP0 data
  val write_cp0_en        = Output(Bool())
  val write_cp0_exception = Output(Bool())
  val write_cp0_data      = Output(UInt(XLEN.W))
  val write_cp0_pc        = Output(UInt(XLEN.W))
}


class WB extends Module {
  val io = IO(new WBIO)

  for (i <- 0 until TOT_PATH_NUM) {
    when(io.fu_res_vec(i).order < io.actual_issue_cnt) {
      // TODO handle the write back

    }
  }
}
