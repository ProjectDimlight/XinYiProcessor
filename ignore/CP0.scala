//package xinyi_s5i4_bc.fu
//
//import chisel3._
//import chisel3.util._
//import config.config._
//import xinyi_s5i4_bc.parts.ControlConst._
//import chisel3.experimental.BundleLiterals._
//
//object ExcCode {
//  val EX_Int  = 0x0
//  val EX_AdEL = 0x4
//  val EX_AdES = 0x6
//  val EX_OV   = 0xD
//  val EX_Sys  = 0x8
//  val EX_Bp   = 0x9
//  val EX_RI   = 0xa
//}
//
//trait CP0Config {
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  // CP0 Register Configurations
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
//  val CP0_INDEX_WIDTH: Int = 5
//
//  val CP0_BADVADDR_INDEX = 8.U
//  val CP0_COUNT_INDEX    = 9.U
//  val CP0_COMPARE_INDEX  = 11.U
//  val CP0_STATUS_INDEX   = 12.U
//  val CP0_CAUSE_INDEX    = 13.U
//  val CP0_EPC_INDEX      = 14.U
//
//  // reference
//  val EXC_CODE_W    = 5 // width of EXC field
//  val EXC_CODE_INT  = 0.U //
//  val EXC_CODE_ADEL = 4.U
//  val EXC_CODE_ADES = 5.U
//  val EXC_CODE_TR   = 13.U
//  val NO_EXCEPTION  = 31.U
//}
//
//
//class ExceptionInfo extends Bundle with CP0Config {
//  val exc_code             = UInt(EXC_CODE_W.W)
//  val pc                   = UInt(XLEN.W)
//  val data                 = UInt(XLEN.W)
//  val in_branch_delay_slot = Bool() // exception happened in branch delay slot
//}
//
//
//// CP0 modules
//class CP0 extends Module with CP0Config {
//  val io = IO(new Bundle {
//    val rs1_vec     = Input(Vec(ISSUE_NUM, UInt(CP0_INDEX_WIDTH.W)))
//    val rs2_vec     = Input(Vec(ISSUE_NUM, UInt(CP0_INDEX_WIDTH.W)))
//    val rd_vec      = Input(Vec(ISSUE_NUM, UInt(CP0_INDEX_WIDTH.W)))
//    val rs1_val_rec = Output(Vec(ISSUE_NUM, UInt(XLEN.W)))
//    val rs2_val_rec = Output(Vec(ISSUE_NUM, UInt(XLEN.W)))
//
//    val wen_vec       = Input(Vec(ISSUE_NUM, Bool()))
//    val write_val_vec = Input(Vec(ISSUE_NUM, UInt(CP0_INDEX_WIDTH.W)))
//
//    val exc_info_vec = Input(Vec(ISSUE_NUM, new ExceptionInfo))
//  })
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>
//  // cp0 registers declaration
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<
//  val cp0_reg_count = RegInit(0.U(XLEN.W))
//
//  val cp0_reg_cause = RegInit(WireInit(new Bundle {
//    val BD       = UInt(1.W)
//    val IP7      = UInt(1.W)
//    val EXC_CODE = UInt(5.W)
//    0.U(2.W)
//  }.Lit(_.IP7 -> 0.U, _.BD -> 0.U)))
//
//  val cp0_reg_compare = RegInit(0.U(XLEN.W))
//
//  val cp0_reg_status = RegInit(WireInit(new Bundle {
//    val IM7 = UInt(1.W)
//    val EXL = UInt(1.W)
//    val IE  = UInt(1.W)
//  }.Lit(_.IE -> 1.U, _.IM7 -> 0.U, _.EXL -> 0.U)))
//
//  val cp0_reg_badvaddr = RegInit(0.U(XLEN.W))
//
//  val cp0_reg_epc = RegInit(0.U(XLEN.W))
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>
//  // local signal declaration
//  //<<<<<<<<<<<<<<<<<<<<<<<<<
//
//
//  val has_exception_vec = Vec(ISSUE_NUM, Wire(Bool()))
//
//  for (i <- 0 until ISSUE_NUM) {
//    has_exception_vec(i) := !cp0_reg_status.EXL && io.exc_info_vec(i).exc_code =/= NO_EXCEPTION
//  }
//
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  //     handle read & write
//  //
//  // fully parameterized read and
//  // write.
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
//  for (i <- 0 until ISSUE_NUM) {
//    io.rs1_val_rec(i) := MuxLookup(
//      io.rs1_vec(i),
//      "hcafebabe".U,
//      Seq(
//        CP0_BADVADDR_INDEX -> cp0_reg_badvaddr,
//        CP0_COUNT_INDEX -> cp0_reg_count,
//        CP0_COMPARE_INDEX -> cp0_reg_compare,
//        CP0_STATUS_INDEX -> cp0_reg_status.asUInt(),
//        CP0_CAUSE_INDEX -> cp0_reg_cause.asUInt(),
//        CP0_EPC_INDEX -> cp0_reg_epc,
//      )
//    )
//    io.rs2_val_rec(i) := MuxLookup(
//      io.rs2_vec(i),
//      "hcafebabe".U,
//      Seq(
//        CP0_BADVADDR_INDEX -> cp0_reg_badvaddr,
//        CP0_COUNT_INDEX -> cp0_reg_count,
//        CP0_COMPARE_INDEX -> cp0_reg_compare,
//        CP0_STATUS_INDEX -> cp0_reg_status.asUInt(),
//        CP0_CAUSE_INDEX -> cp0_reg_cause.asUInt(),
//        CP0_EPC_INDEX -> cp0_reg_epc,
//      )
//    )
//  }
//
//  val waw_vec = Vec(ISSUE_NUM, WireDefault(0.U))
//
//
//  def waw_detect(exc: UInt, rd: UInt) = UInt(1.W) {
//    when((rd === CP0_EPC_INDEX || rd === CP0_STATUS_INDEX || rd === CP0_CAUSE_INDEX) && exc =/= NO_EXCEPTION) {
//      1.U
//    } .elsewhen((exc === EXC_CODE_ADES || exc === EXC_CODE_ADEL) && rd === CP0_BADVADDR_INDEX) {
//      1.U
//    }
//
//
//    }.elsewhen(exc === EXC_CODE_TR) {
//      rd === CP0_EPC_INDEX ||
//    }.otherwise {
//      0.U
//    }
//    0.U
//  }
//
//  for (i <- 0 until ISSUE_NUM) {
//    if (i + 1 < ISSUE_NUM) {
//      when(io.exc_info_vec(i + 1).exc_code === EXC_CODE_INT && io.rd_vec(i) ===) {
//
//      }
//      waw_vec(i) := io.exc_info_vec(i).exc_code =/= NO_EXCEPTION && io.rd_vec(i) ===
//    }
//  }
//
//  // when mtc0
//  for (i <- 0 until ISSUE_NUM) {
//    when(io.wen_vec(i) && io.exc_info_vec(i).exc_code === NO_EXCEPTION.U) {
//    }
//  }
//
//
//  when(io.in_cp0_wen && !io.in_eret_en && !has_exception) {
//    switch(io.in_write_index) {
//      is(CP0_COUNT_INDEX.U) {
//        cp0_reg_count := io.in_write_val
//      }
//      is(CP0_COMPARE_INDEX.U) {
//        cp0_reg_compare := io.in_write_val
//      }
//      is(CP0_EPC_INDEX.U) {
//        cp0_reg_epc := io.in_write_val
//      }
//    }
//  }
//
//
//  //>>>>>>>>>>>>>>>>>>>>>>>>>>>
//  // handle cp0 register events
//  //<<<<<<<<<<<<<<<<<<<<<<<<<<<
//
//  // counter register autoincrement 1 each cycle
//  cp0_reg_count := Mux(cp0_reg_count === "hFFFFFFFF".U(XLEN.W), 0.U, cp0_reg_count + 1.U)
//
//  when(cp0_reg_count === cp0_reg_compare) {
//    cp0_reg_cause.IP7 := 1.U
//  }.elsewhen(io.in_cp0_wen && io.in_write_index === CP0_COMPARE_INDEX.U) {
//    cp0_reg_cause.IP7 := 0.U
//  }
//
//  for (i <- 0 until ISSUE_NUM) {
//    when(has_exception_vec(i)) {
//      cp0_reg_status.EXL := 1.U
//      cp0_reg_cause.BD := io.exc_info_vec(i).in_branch_delay_slot
//      cp0_reg_epc := Mux(io.exc_info_vec(i).in_branch_delay_slot, io.exc_info_vec(i).pc - 4.U, io.exc_info_vec(i).pc)
//      cp0_reg_cause.EXC_CODE := io.exc_info_vec(i).exc_code
//
//      when(io.exc_info_vec(i).exc_code === EXC_CODE_ADEL.U || io.exc_info_vec(i).exc_code === EXC_CODE_ADES.U) {
//        cp0_reg_badvaddr := io.exc_info_vec(i).data
//      }
//    }
//  }
//
//}