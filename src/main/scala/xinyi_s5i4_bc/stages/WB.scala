package xinyi_s5i4_bc.stages


import chisel3._
import chisel3.util._
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

  val write_cp0_en        = Bool()
  val write_cp0_rd        = UInt(XLEN.W)
  val write_cp0_exception = UInt(EXC_CODE_W.W)
  val write_cp0_data      = UInt(XLEN.W)
  val write_cp0_pc        = UInt(XLEN.W)
}

// write back IO
class WBIO extends Bundle {
  val fu_res_vec        = Input(Vec(TOT_PATH_NUM, new FUOut)) // fu result vector
  val actual_issue_cnt  = Input(UInt(ISSUE_NUM_W.W)) // issue param
  val write_channel_vec = Output(Vec(ISSUE_NUM, new WBOut))

  val incoming_epc       = Input(UInt(LGC_ADDR_W.W))
  val incoming_interrupt = Input(Vec(8, Bool()))
  val exception_handled  = Output(Bool())
}

class WBStage extends Module with CP0Config {
  val io = IO(new WBIO)

  // check if exception handled
  //    if any exception found in WB, forall will be False
  // and the whole predicate will be True
  io.exception_handled := !io.fu_res_vec.forall((p: FUOut) => {
    p.exc_code === NO_EXCEPTION
  })



  // handle interrupt
  when(io.incoming_interrupt.asUInt.orR()) {
    io.exception_handled := 1.U
    io.write_channel_vec(0).write_cp0_en := 1.U
    io.write_channel_vec(0).write_cp0_rd := CP0_CAUSE_INDEX
    io.write_channel_vec(0).write_cp0_pc := io.incoming_epc
    io.write_channel_vec(0).write_cp0_exception := EXC_CODE_INT

    io.write_channel_vec(0).write_cp0_data := Cat(Seq(0.U(16.W), Reverse(io.incoming_interrupt.asUInt), 0.U(8.W)))

  }.otherwise {
    for (i <- 0 until ISSUE_NUM) {

      val previous_exception = Wire(Vec(i, Bool()))
      for (j <- 0 until i) {
        previous_exception(j) := io.fu_res_vec(i).exc_code =/= NO_EXCEPTION
      }

      when(io.fu_res_vec(i).ready && i.U < io.actual_issue_cnt && !previous_exception.asUInt.orR) {
        // params from input
        val fu_tmp_res = io.fu_res_vec(i)
        val order      = fu_tmp_res.order

        switch(fu_tmp_res.write_target) {
          is(DReg) {
            when(fu_tmp_res.rd =/= 0.U) {
              io.write_channel_vec(order).write_regs_en := 1.U
              io.write_channel_vec(order).write_regs_data := fu_tmp_res.data
              io.write_channel_vec(order).write_regs_rd := fu_tmp_res.rd
            }
          }
          is(DCP0) {
            io.write_channel_vec(order).write_cp0_en := 1.U
            io.write_channel_vec(order).write_cp0_exception := fu_tmp_res.exc_code
            io.write_channel_vec(order).write_cp0_data := fu_tmp_res.data
            io.write_channel_vec(order).write_cp0_pc := fu_tmp_res.pc
          }
          is(DHi) {
            io.write_channel_vec(order).write_hi_en := 1.U
            io.write_channel_vec(order).write_hi_data := fu_tmp_res.hi
          }
          is(DLo) {
            io.write_channel_vec(order).write_lo_en := 1.U
            io.write_channel_vec(order).write_lo_data := fu_tmp_res.data
          }
          is(DHiLo) {
            io.write_channel_vec(order).write_hi_en := 1.U
            io.write_channel_vec(order).write_lo_en := 1.U
            io.write_channel_vec(order).write_hi_data := fu_tmp_res.hi
            io.write_channel_vec(order).write_lo_data := fu_tmp_res.data
          }
        }
      }
    }
  }

}
