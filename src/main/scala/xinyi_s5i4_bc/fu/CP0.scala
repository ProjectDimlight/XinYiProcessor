package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import chisel3.experimental.BundleLiterals._
import EXCCodeConfig._

trait CP0Config {
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // CP0 Register Configurations
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  val CP0_INDEX_WIDTH: Int = 5

  val CP0_BADVADDR_INDEX = 8.U(CP0_INDEX_WIDTH.W)
  val CP0_COUNT_INDEX    = 9.U(CP0_INDEX_WIDTH.W)
  val CP0_COMPARE_INDEX  = 11.U(CP0_INDEX_WIDTH.W)
  val CP0_STATUS_INDEX   = 12.U(CP0_INDEX_WIDTH.W)
  val CP0_CAUSE_INDEX    = 13.U(CP0_INDEX_WIDTH.W)
  val CP0_EPC_INDEX      = 14.U(CP0_INDEX_WIDTH.W)
}

object EXCCodeConfig {

  // reference
  val EXC_CODE_W: Int = 5 // width of EXC field

  val EXC_CODE_INT  = 0.U(EXC_CODE_W.W) // interrupt
  val EXC_CODE_ADEL = 4.U(EXC_CODE_W.W) // load or an instruction fetch exception
  val EXC_CODE_ADES = 5.U(EXC_CODE_W.W) // store exception
  val EXC_CODE_SYS  = 8.U(EXC_CODE_W.W) // syscall
  val EXC_CODE_BP   = 9.U(EXC_CODE_W.W) // Break
  val EXC_CODE_RI   = 10.U(EXC_CODE_W.W) // Instruction
  val EXC_CODE_OV   = 12.U(EXC_CODE_W.W) // Overflow
  val EXC_CODE_TR   = 13.U(EXC_CODE_W.W) // trap exception
  val EXC_CODE_ERET = 30.U(EXC_CODE_W.W)
  val NO_EXCEPTION  = 31.U(EXC_CODE_W.W)
}


class ExceptionInfo extends Bundle {
  val exc_code             = UInt(EXC_CODE_W.W)
  val pc                   = UInt(XLEN.W)
  val data                 = UInt(XLEN.W)
  val in_branch_delay_slot = Bool() // exception happened in branch delay slot
}


class CP0StatusBundle extends Bundle {
  val CU      = UInt(4.W)
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
  val IM      = UInt(8.W)
  val IGNORE3 = UInt(3.W)
  val UM      = Bool()
  val R0      = Bool()
  val ERL     = Bool()
  val EXL     = Bool()
  val IE      = Bool()
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
  val IP       = UInt(8.W)
  val IGNORE1  = UInt(1.W)
  val EXC_CODE = UInt(5.W)
  val IGNORE2  = UInt(2.W)
}

class CP0ReadInterface extends Bundle {
  val rs   = Input(UInt(REG_ID_W.W))
  val data = Output(UInt(XLEN.W))
}

// CP0 modules
class CP0 extends Module with CP0Config {
  val io = IO(new Bundle {
    val read     = Vec(ISSUE_NUM, new CP0ReadInterface)
    val write    = Vec(ISSUE_NUM, new RegWriteInterface)
    val exc_info = Input(new ExceptionInfo)

    // interrupt support
    val soft_int_pending_vec = Output(Vec(2, Bool())) // software interrupt
    val time_int             = Output(Bool()) // time interruption
    val int_mask_vec         = Output(Vec(8, Bool())) // interrupt mask
  })

  //>>>>>>>>>>>>>>>>>>>>>>>>>>
  // cp0 registers declaration
  //<<<<<<<<<<<<<<<<<<<<<<<<<<
  val cp0_reg_count = RegInit(0.U(XLEN.W))


  def CP0CauseInit: CP0CauseBundle = {
    val initial_value = WireDefault(0.U.asTypeOf(new CP0CauseBundle))
    initial_value.IGNORE1 := 0.U(1.W)
    initial_value.IGNORE2 := 0.U(2.W)
    initial_value.IGNORE3 := 0.U(3.W)
    initial_value
  }

  val cp0_reg_cause = RegInit(CP0CauseInit)

  val cp0_reg_compare = RegInit(0.U(XLEN.W))


  def CP0StatusInit: CP0StatusBundle = {
    val initial_value = WireDefault(0.U.asTypeOf(new CP0StatusBundle))
    initial_value.BEV     := 1.U(1.W)
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


  val has_exception_vec = !cp0_reg_status.EXL && io.exc_info.exc_code =/= NO_EXCEPTION


  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //    handle software interrupt
  // output Cause.IP0 ~ Cause.IP7 as
  // software interrupt source
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  io.soft_int_pending_vec(1) := cp0_reg_cause.IP(1)
  io.soft_int_pending_vec(0) := cp0_reg_cause.IP(0)

  io.int_mask_vec := Reverse(cp0_reg_status.IM).asBools


  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //     handle read & write
  //
  // fully parameterized read and
  // write.
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  for (i <- 0 until ISSUE_NUM) {
    io.read(i).data := MuxLookupBi(
      io.read(i).rs,
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
    io.read(i).data := MuxLookupBi(
      io.read(i).rs,
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

  val write_cause_ip = Wire(Bool())
  write_cause_ip := false.B

  val next_cause_ip = Wire(UInt(2.W))
  next_cause_ip := cp0_reg_cause.IP(1, 0)

  // when mtc0
  for (i <- 0 until ISSUE_NUM) {
    when(io.write(i).we && io.exc_info.exc_code === NO_EXCEPTION) {
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
          /*
          cp0_reg_cause := io.write(i).data.asTypeOf(new CP0CauseBundle)
          cp0_reg_cause.IGNORE1 := 0.U(1.W)
          cp0_reg_cause.IGNORE2 := 0.U(2.W)
          cp0_reg_cause.IGNORE3 := 0.U(3.W)
          */
          write_cause_ip := true.B
          next_cause_ip  := io.write(i).data(9, 8)
        }
        is(CP0_STATUS_INDEX) {
          /*
          cp0_reg_status := io.write(i).data.asTypeOf(new CP0StatusBundle)
          cp0_reg_status.BEV     := 1.U(1.W)
          cp0_reg_status.IGNORE1 := 0.U(1.W)
          cp0_reg_status.IGNORE3 := 0.U(3.W)
          */
          cp0_reg_status.IM      := io.write(i).data(15, 8)
          cp0_reg_status.EXL     := io.write(i).data(1)
          cp0_reg_status.IE      := io.write(i).data(0)
        }
      }
    }
  }

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //    handle interruption
  //    if interrupt -> update IP
  //  otherwise update
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  when(io.exc_info.exc_code === EXC_CODE_INT) {
    cp0_reg_cause.IP := io.exc_info.data(15, 8)
  }
  .otherwise {
    cp0_reg_cause.IP := Cat(0.U(6.W), next_cause_ip)
  }

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // handle cp0 register events
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<

  // counter register autoincrement 1 each cycle
  cp0_reg_count := Mux(cp0_reg_count === "hFFFFFFFF".U(XLEN.W), 0.U, cp0_reg_count + 1.U)

  for (i <- 0 until ISSUE_NUM) {
    when(io.write(i).we && io.write(i).rd === CP0_COMPARE_INDEX) {
      cp0_reg_cause.IP := Cat(0.U(1.W), cp0_reg_cause.IP(6, 0))
    }
  }

  when(cp0_reg_count === cp0_reg_compare) {
    cp0_reg_cause.IP := Cat(1.U(1.W), cp0_reg_cause.IP(6, 0))
    cp0_reg_cause.TI := 1.U
    io.time_int := 1.U
  }.otherwise {
    cp0_reg_cause.TI := 0.U
    io.time_int := 0.U
  }

  when(has_exception_vec) {
    when (!cp0_reg_status.EXL) {
      cp0_reg_cause.BD := io.exc_info.in_branch_delay_slot
      cp0_reg_epc := Mux(io.exc_info.in_branch_delay_slot, io.exc_info.pc - 4.U, io.exc_info.pc)
    }
    cp0_reg_status.EXL := 1.U
    cp0_reg_cause.EXC_CODE := io.exc_info.exc_code

    when(io.exc_info.exc_code === EXC_CODE_ADEL || io.exc_info.exc_code === EXC_CODE_ADES) {
      cp0_reg_badvaddr := io.exc_info.data
    }
  }
}
