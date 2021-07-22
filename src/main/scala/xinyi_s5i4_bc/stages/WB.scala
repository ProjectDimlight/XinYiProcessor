package xinyi_s5i4_bc.stages


import chisel3._
import chisel3.util._
import utils._
import config.config._
import xinyi_s5i4_bc.fu._
import xinyi_s5i4_bc.parts.ControlConst._
import EXCCodeConfig._

class WBOut extends Bundle {
  val write_hi_en   = Bool()
  val write_lo_en   = Bool()
  val write_hi_data = UInt(XLEN.W)
  val write_lo_data = UInt(XLEN.W)

  val write_regs_en   = Bool()
  val write_regs_data = UInt(XLEN.W)
  val write_regs_rd   = UInt(XLEN.W)

  val write_cp0_en   = Bool()
  val write_cp0_rd   = UInt(XLEN.W)
  val write_cp0_data = UInt(XLEN.W)
}

// write back IO
class WBIO extends Bundle {
  val wb_in              = Input(Vec(ISSUE_NUM, new FUOut)) // fu result vector
  val wb_exception_order = Input(UInt(ISSUE_NUM_W.W)) // issue param

  val write_channel_vec  = Output(Vec(ISSUE_NUM, new WBOut)) // write back channels (withoud exception)
}

class WBStage extends Module {
  val io = IO(new WBIO)
  val exception_order = io.wb_exception_order

  // normal WB
  for (i <- 0 until ISSUE_NUM) {
    io.write_channel_vec(i) := 0.U.asTypeOf(new WBOut)
    val fu_tmp_res = io.wb_in(i)
    val order      = i.U

    when (i.U < exception_order) {
      // params from input
      switch(fu_tmp_res.write_target) {
        is(DReg) {
          io.write_channel_vec(order).write_regs_en := (fu_tmp_res.rd =/= 0.U)
        }
        is(DCP0) {
          io.write_channel_vec(order).write_cp0_en := 1.U
        }
        is(DHi) {
          io.write_channel_vec(order).write_hi_en := 1.U
        }
        is(DLo) {
          io.write_channel_vec(order).write_lo_en := 1.U
        }
        is(DHiLo) {
          io.write_channel_vec(order).write_hi_en := 1.U
          io.write_channel_vec(order).write_lo_en := 1.U
        }
      }
    }
    
    io.write_channel_vec(order).write_regs_data := fu_tmp_res.data
    io.write_channel_vec(order).write_regs_rd := fu_tmp_res.rd
    io.write_channel_vec(order).write_cp0_data := fu_tmp_res.data
    io.write_channel_vec(order).write_cp0_rd := fu_tmp_res.rd
    io.write_channel_vec(order).write_hi_data := Mux(fu_tmp_res.write_target === DHi, fu_tmp_res.data, fu_tmp_res.hi)
    io.write_channel_vec(order).write_lo_data := fu_tmp_res.data
  }

}
