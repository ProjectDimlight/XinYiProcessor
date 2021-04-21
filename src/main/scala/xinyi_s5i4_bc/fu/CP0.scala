package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import chisel3.experimental.BundleLiterals._

object ExcCode {
  val EX_Int  = 0x0
  val EX_AdEL = 0x4
  val EX_AdES = 0x5
  val EX_OV   = 0xC
  val EX_Sys  = 0x8
  val EX_Bp   = 0x9
  val EX_RI   = 0xa
}

trait CP0Config {
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // CP0 Register Configurations
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  val CP0_INDEX_WIDTH: Int = 5

  val CP0_BADVADDR_INDEX = 8.U
  val CP0_COUNT_INDEX    = 9.U
  val CP0_COMPARE_INDEX  = 11.U
  val CP0_STATUS_INDEX   = 12.U
  val CP0_CAUSE_INDEX    = 13.U
  val CP0_EPC_INDEX      = 14.U

  // reference
  val EXC_CODE_W    = 5 // width of EXC field
  val EXC_CODE_INT  = 0.U //
  val EXC_CODE_ADEL = 4.U // load or an instruction fetch exception
  val EXC_CODE_ADES = 5.U // store exception
  val EXC_CODE_TR   = 13.U // trap exception
  val NO_EXCEPTION  = 31.U
}


class ExceptionInfo extends Bundle with CP0Config {
  val exc_code             = UInt(EXC_CODE_W.W)
  val pc                   = UInt(XLEN.W)
  val data                 = UInt(XLEN.W)
  val in_branch_delay_slot = Bool() // exception happened in branch delay slot
}


class CP0StatusBundle extends Bundle {
  val CU      = Vec(4, Bool())
  val RP      = Bool()
  val FR      = Bool()
  val RE      = Bool()
  val MX      = Bool()
  val IGNORE1 = Bool()
  val BEV     = Bool()
  val TS      = Bool()
  val SR      = Bool()
  val NMI     = Bool()
  val ASE     = Bool()
  val Impl    = UInt(2.W)
  val IM      = Vec(8, Bool())
  val IGNORE3 = UInt(3.W)
  val UM      = Bool()
  val R0      = Bool()
  val ERL     = Bool()
  val EXL     = UInt(1.W)
  val IE      = UInt(1.W)
}


class CP0CauseBundle extends Bundle {
  val BD       = Bool()
  val TI       = Bool()
  val CE       = UInt(2.W)
  val DC       = Bool()
  val PCI      = Bool()
  val ASE1     = UInt(2.W)
  val IV       = Bool()
  val WP       = Bool()
  val FDCI     = Bool()
  val IGNORE3  = UInt(3.W)
  val ASE2     = UInt(2.W)
  val IP       = Vec(10, Bool())
  val IGNORE1  = 0.U(1.W)
  val EXC_CODE = UInt(5.W)
  val IGNORE2  = UInt(2.W)
}


// CP0 modules
class CP0 extends Module with CP0Config {
  val io = IO(new Bundle {
    val read         = Vec(ISSUE_NUM, new RegReadInterface)
    val write        = Vec(ISSUE_NUM, new RegWriteInterface)
    val exc_info_vec = Input(Vec(ISSUE_NUM, new ExceptionInfo))
  })

  //>>>>>>>>>>>>>>>>>>>>>>>>>>
  // cp0 registers declaration
  //<<<<<<<<<<<<<<<<<<<<<<<<<<
  val cp0_reg_count = RegInit(0.U(XLEN.W))


  def CP0CauseInit: CP0CauseBundle = {
    val initial_value = Wire(new CP0CauseBundle)
    initial_value.IGNORE1 := 0.U(1.W)
    initial_value.IGNORE2 := 0.U(2.W)
    initial_value.IGNORE3 := 0.U(3.W)
    initial_value
  }

  val cp0_reg_cause = RegInit(CP0CauseInit)

  val cp0_reg_compare = RegInit(0.U(XLEN.W))


  def CP0StatusInit: CP0StatusBundle = {
    val initial_value = Wire(new CP0StatusBundle)
    initial_value.IGNORE1 := 0.U(1.W)
    initial_value.IGNORE3 := 0.U(3.W)
    initial_value
  }


  val cp0_reg_status = RegInit(CP0StatusInit)

  val cp0_reg_badvaddr = RegInit(0.U(XLEN.W))

  val cp0_reg_epc = RegInit(0.U(XLEN.W))

  //>>>>>>>>>>>>>>>>>>>>>>>>>
  // local signal declaration
  //<<<<<<<<<<<<<<<<<<<<<<<<<


  val has_exception_vec = Vec(ISSUE_NUM, Wire(Bool()))

  for (i <- 0 until ISSUE_NUM) {
    has_exception_vec(i) := !cp0_reg_status.EXL && io.exc_info_vec(i).exc_code =/= NO_EXCEPTION
  }


  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //     handle read & write
  //
  // fully parameterized read and
  // write.
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  for (i <- 0 until ISSUE_NUM) {
    io.read(i).data1 := MuxLookup(
      io.read(i).rs1(i),
      "hcafebabe".U,
      Seq(
        CP0_BADVADDR_INDEX -> cp0_reg_badvaddr,
        CP0_COUNT_INDEX -> cp0_reg_count,
        CP0_COMPARE_INDEX -> cp0_reg_compare,
        CP0_STATUS_INDEX -> cp0_reg_status.asUInt(),
        CP0_CAUSE_INDEX -> cp0_reg_cause.asUInt(),
        CP0_EPC_INDEX -> cp0_reg_epc,
      )
    )
    io.read(i).data2 := MuxLookup(
      io.read(i).rs2(i),
      "hcafebabe".U,
      Seq(
        CP0_BADVADDR_INDEX -> cp0_reg_badvaddr,
        CP0_COUNT_INDEX -> cp0_reg_count,
        CP0_COMPARE_INDEX -> cp0_reg_compare,
        CP0_STATUS_INDEX -> cp0_reg_status.asUInt(),
        CP0_CAUSE_INDEX -> cp0_reg_cause.asUInt(),
        CP0_EPC_INDEX -> cp0_reg_epc,
      )
    )
  }




  // when mtc0
  for (i <- 0 until ISSUE_NUM) {
    when(io.write(i).we && io.exc_info_vec(i).exc_code === NO_EXCEPTION) {
      switch(io.write(i).rd) {
        is(CP0_COUNT_INDEX) {
          cp0_reg_count := io.write(i).data
        }
        is(CP0_COMPARE_INDEX) {
          cp0_reg_compare := io.write(i).data
        }
        is(CP0_EPC_INDEX) {
          cp0_reg_epc := io.write(i).data
        }
        is(CP0_BADVADDR_INDEX) {
          cp0_reg_badvaddr := io.write(i).data
        }
        is(CP0_CAUSE_INDEX) {
          cp0_reg_cause := io.write(i).data
          cp0_reg_cause.IGNORE1 := 0.U(1.W)
          cp0_reg_cause.IGNORE2 := 0.U(2.W)
          cp0_reg_cause.IGNORE3 := 0.U(3.W)
        }
        is(CP0_STATUS_INDEX) {
          cp0_reg_status := io.write(i).data
          cp0_reg_status.IGNORE1 := 0.U(1.W)
          cp0_reg_status.IGNORE3 := 0.U(3.W)
        }
      }
    }
  }


  //>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // handle cp0 register events
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<

  // counter register autoincrement 1 each cycle
  cp0_reg_count := Mux(cp0_reg_count === "hFFFFFFFF".U(XLEN.W), 0.U, cp0_reg_count + 1.U)

  for (i <- 0 until ISSUE_NUM) {
    when(io.write(i).we && io.write(i).rd === CP0_COMPARE_INDEX) {
      cp0_reg_cause.IP(7) := 0.U
    }
  }

  when(cp0_reg_count === cp0_reg_compare) {
    cp0_reg_cause.IP(7) := 1.U
  }

  for (i <- 0 until ISSUE_NUM) {
    when(has_exception_vec(i)) {
      cp0_reg_status.EXL := 1.U
      cp0_reg_cause.BD := io.exc_info_vec(i).in_branch_delay_slot
      cp0_reg_epc := Mux(io.exc_info_vec(i).in_branch_delay_slot, io.exc_info_vec(i).pc - 4.U, io.exc_info_vec(i).pc)
      cp0_reg_cause.EXC_CODE := io.exc_info_vec(i).exc_code

      when(io.exc_info_vec(i).exc_code === EXC_CODE_ADEL || io.exc_info_vec(i).exc_code === EXC_CODE_ADES) {
        cp0_reg_badvaddr := io.exc_info_vec(i).data
      }
    }
  }

}