//package xinyi_s5i4_bc.stages
//
//
//import chisel3._
//import chisel3.util._
import utils._
//import config.config._
//import xinyi_s5i4_bc.parts.ControlConst._
//import xinyi_s5i4_bc.fu._
//
//import scala.collection.immutable.Nil
//
//class WBOut extends Bundle with CP0Config {
//  val write_hi_en   = Bool()
//  val write_lo_en   = Bool()
//  val write_hi_data = UInt(XLEN.W)
//  val write_lo_data = UInt(XLEN.W)
//
//  val write_regs_en   = Bool()
//  val write_regs_data = UInt(XLEN.W)
//
//  val write_cp0_en        = Bool()
//  val write_cp0_exception = UInt(EXC_CODE_INT.W)
//  val write_cp0_data      = UInt(XLEN.W)
//  val write_cp0_pc        = UInt(XLEN.W)
//}
//
//// write back IO
//class WBIO extends Bundle {
//  val fu_res_vec        = Input(Vec(TOT_PATH_NUM, new FUOut)) // fu result vector
//  val actual_issue_cnt  = Input(UInt(ISSUE_NUM_W.W)) // issue param
//  val write_channel_vec = Output(Vec(ISSUE_NUM, new WBOut))
//}
//
//
//class WB extends Module {
//  val io = IO(new WBIO)
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  // filter the write back results
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
//
//  // a vector with ISSUE_NUM elements.
//  val wb_vec = Wire(Vec(ISSUE_NUM, Module(new FUOut)))
//
//  for (j <- 0 until ISSUE_NUM) {
//    for (i <- 0 until TOT_PATH_NUM) {
//      when(io.fu_res_vec(i).ready && io.fu_res_vec(i).order === j.asUInt()) {
//        wb_vec(j) := io.fu_res_vec(i)
//      }.otherwise {
//        wb_vec(j) := 0.U
//      }
//    }
//  }
//
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  //  filter the write back vector
//  // according to other write back channel
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
//
//
//  // define the conflict type
//  val full_conflict :: reduce_to_hi :: reduce_to_lo :: no_conflict :: Nil = Enum(4)
//
//  // calculate the conflict type
//  def detect_conflict(a: FUOut, b: FUOut): UInt = {
//    when(a.write_target === DReg && b.write_target === DReg && a.rd === b.rd) {
//      return full_conflict
//    }.elsewhen(a.write_target === DCP0 && b.write_target === DCP0 && a.rd === b.rd) {
//      return full_conflict
//    }.elsewhen(a.write_target === DHi && (b.write_target === DHi || b.write_target === DHiLo)) {
//      return full_conflict
//    }.elsewhen(a.write_target === DLo && (b.write_target === DLo || b.write_target === DHiLo)) {
//      return full_conflict
//    }.elsewhen(a.write_target === DHiLo && b.write_target === DHi) {
//      return reduce_to_lo
//    }.elsewhen(a.write_target === DHiLo && b.write_target === DLo) {
//      return reduce_to_hi
//    }.otherwise {
//      return no_conflict
//    }
//    no_conflict
//  }
//
//  // the final possible write back queue
//  val true_wb_target = Wire(Vec(ISSUE_NUM, UInt(WRITE_TARGET_W.W)))
//
//  // filter logic
//  for (i <- 0 until ISSUE_NUM) {
//    true_wb_target(i) := wb_vec(i).write_target
//
//    for (j <- i + 1 until ISSUE_NUM) {
//      switch(detect_conflict(wb_vec(i), wb_vec(j))) {
//        is(full_conflict) {
//          true_wb_target(i) := D_NONE
//        }
//        is(reduce_to_hi) {
//          true_wb_target(i) := DHi
//        }
//        is(reduce_to_lo) {
//          true_wb_target(i) := DLo
//        }
//      }
//    }
//  }
//
//
//
//  //>>>>>>>>>>>>>>>>>>
//  // write back logic
//  //<<<<<<<<<<<<<<<<<<
//  for (i <- 0 until ISSUE_NUM) {
//    switch(true_wb_vec(i).write_target) {
//      is(DReg) {
//        when(true_wb_vec(i).rd =/= 0.U) {
//          io.write_channel_vec(i).write_regs_en := 1.U
//          io.write_channel_vec(i).write_regs_data := true_wb_vec(i).
//        }
//      }
//      is(DCP0) {
//
//      }
//      is(DHi) {
//
//      }
//      is(DLo) {
//
//      }
//      is(DHiLo) {
//
//      }
//    }
//  }
//
//}
