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
  val fu_res_vec         = Input(Vec(TOT_PATH_NUM, new FUOut)) // fu result vector
  val actual_issue_cnt   = Input(UInt(ISSUE_NUM_W.W)) // issue param
  val write_channel_vec  = Output(Vec(ISSUE_NUM, new WBOut)) // write back channels (withoud exception)
  val exc_info           = Output(new ExceptionInfo) // exception information (exception happened)
  val incoming_epc       = Input(UInt(LGC_ADDR_W.W)) // the incoming epc for interruption
  val incoming_interrupt = Input(Vec(8, Bool())) // the incoming interrupt happened
  val exception_handled  = Output(Bool()) // exception or interrupt found
}

class WBStage extends Module with CP0Config {
  val io = IO(new WBIO)

  // check if exception handled
  //    if any exception found in WB, forall will be False
  // and the whole predicate will be True


  val exception_found = Wire(Bool())
  exception_found := false.B

  val interrupt_found = Wire(Bool())
  interrupt_found := io.incoming_interrupt.asUInt().orR()

  io.exception_handled := exception_found || interrupt_found

  // Mux: from Paths to Issues
  val issue_vec = Wire(Vec(ISSUE_NUM, new FUOut))
  for (i <- 0 until ISSUE_NUM) {
    issue_vec(i) := FUOutBubble()

    for (j <- 0 until TOT_PATH_NUM) {
      when ((i.U < io.actual_issue_cnt) & (io.fu_res_vec(j).order === i.U)) {
        issue_vec(i) := io.fu_res_vec(j)
      }
    }
  }

  // generate exception order
  val exception_order = Wire(UInt(ISSUE_NUM_W.W))
  exception_order := io.actual_issue_cnt
  
  io.exc_info.pc := 0.U
  io.exc_info.exc_code := NO_EXCEPTION
  io.exc_info.data := 0.U
  io.exc_info.in_branch_delay_slot := false.B
  for (i <- ISSUE_NUM - 1 to 0 by -1) {
    when (issue_vec(i).exc_code =/= NO_EXCEPTION) {
      exception_found := true.B

      io.exc_info.pc := Mux(issue_vec(i).is_delay_slot, issue_vec(i).pc - 4.U, issue_vec(i).pc)
      io.exc_info.exc_code := issue_vec(i).exc_code
      io.exc_info.data := issue_vec(i).data // some of the exception info should be passed by normal data
      // for example: badvaddr
      io.exc_info.in_branch_delay_slot := issue_vec(i).is_delay_slot
      
      exception_order := i.U
    }
  }

  // handle interrupt
  when (interrupt_found) {
    for (i <- 0 until ISSUE_NUM) {
      io.write_channel_vec(i) := 0.U.asTypeOf(new WBOut)
    }
    io.exc_info.pc := io.incoming_epc
    io.exc_info.exc_code := EXC_CODE_INT
    io.exc_info.data := Cat(Seq(0.U(16.W), Reverse(io.incoming_interrupt.asUInt), 0.U(8.W)))
    io.exc_info.in_branch_delay_slot := 0.U
  }
  .otherwise {
    for (i <- 0 until ISSUE_NUM) {
      io.write_channel_vec(i) := 0.U.asTypeOf(new WBOut)

      when (i.U < exception_order) {
        // params from input
        val fu_tmp_res = issue_vec(i)
        val order      = i.U

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
            io.write_channel_vec(order).write_cp0_data := fu_tmp_res.data
            io.write_channel_vec(order).write_cp0_rd := fu_tmp_res.rd
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
