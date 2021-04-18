package xinyi_s5i4_bc.stages


import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import xinyi_s5i4_bc.fu._

// write back channel
class WBOut extends Bundle with CP0Config {
  val write_hi_en   = Output(Bool())
  val write_lo_en   = Output(Bool())
  val write_hi_data = Output(UInt(XLEN.W))
  val write_lo_data = Output(UInt(XLEN.W))

  val write_regs_en   = Output(Bool())
  val write_regs_data = Output(UInt(XLEN.W))

  val write_cp0_en        = Output(Bool())
  val write_cp0_exception = Output(UInt(EXC_CODE_INT.W))
  val write_cp0_data      = Output(UInt(XLEN.W))
  val write_cp0_pc        = Output(UInt(XLEN.W))
}

class WBIO extends Bundle {
  // fu result vector
  val fu_res_vec        = Input(Vec(TOT_PATH_NUM, new FUOut))
  // issue param
  val actual_issue_cnt  = Input(UInt(ISSUE_NUM_W.W))
  val write_channel_vec = Output(Vec(ISSUE_NUM, new WBOut))
}


class WB extends Module {
  val io = IO(new WBIO)


  // filter the write back results
  val wb_vec = Wire(Vec(ISSUE_NUM, new FUOut))

  var count: Int = 0
  for (i <- 0 until TOT_PATH_NUM) {
    for (j <- 0 until ISSUE_NUM) {
      when(io.fu_res_vec(i).ready && io.fu_res_vec(i).order === j.asUInt()) {
        wb_vec(j) := io.fu_res_vec(i)
      }.otherwise {
        wb_vec(j) := 0.U
      }
    }
  }

  // write back logic
  val regs_busy = WireInit(0.U(1.W))
  val cp0_busy  = WireInit(0.U(1.W))
  val hi_busy   = WireInit(0.U(1.W))
  val lo_busy   = WireInit(0.U(1.W))

  for (i <- 0 until ISSUE_NUM) {
    io.write_channel_vec(i).write_regs_en := wb_vec(i).write_target === DReg && wb_vec(i).rd =/= 0.U
    io.write_channel_vec(i).write_hi_en := wb_vec(i).write_target === DHi || wb_vec(i).write_target === DHiLo
    io.write_channel_vec(i).write_lo_en := wb_vec(i).write_target === DLo || wb_vec(i).write_target === DHiLo
    io.write_channel_vec(i).write_cp0_en := wb_vec(i).write_target === DCP0


    io.write_channel_vec(i).write_regs_data := wb_vec(i).data
    io.write_channel_vec(i).write_hi_data := wb_vec(i).data
    io.write_channel_vec(i).write_lo_data := wb_vec(i).data
    io.write_channel_vec(i).write_cp0_data := wb_vec(i).data
  }

}
