package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import chisel3.experimental.BundleLiterals._
import EXCCodeConfig._

trait CP0Config {
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // CP0 Register Configurations
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  val CP0_INDEX_WIDTH: Int = 5

  val CP0_BADVADDR_INDEX =  8.U(CP0_INDEX_WIDTH.W)
  val CP0_COUNT_INDEX    =  9.U(CP0_INDEX_WIDTH.W)
  val CP0_COMPARE_INDEX  = 11.U(CP0_INDEX_WIDTH.W)
  val CP0_STATUS_INDEX   = 12.U(CP0_INDEX_WIDTH.W)
  val CP0_CAUSE_INDEX    = 13.U(CP0_INDEX_WIDTH.W)
  val CP0_EPC_INDEX      = 14.U(CP0_INDEX_WIDTH.W)
}

object EXCCodeConfig {

  // reference
  val EXC_CODE_W: Int = 5 // width of EXC field
  
  val EXC_CODE_INT  =  0.U(EXC_CODE_W.W) //
  val EXC_CODE_ADEL =  4.U(EXC_CODE_W.W) // load or an instruction fetch exception
  val EXC_CODE_ADES =  5.U(EXC_CODE_W.W) // store exception
  val EXC_CODE_SYS  =  8.U(EXC_CODE_W.W) // Syscall
  val EXC_CODE_BP   =  9.U(EXC_CODE_W.W) // Break
  val EXC_CODE_RI   = 10.U(EXC_CODE_W.W) // Instruction
  val EXC_CODE_OV   = 12.U(EXC_CODE_W.W) // Overflow
  val EXC_CODE_TR   = 13.U(EXC_CODE_W.W) // trap exception
  val NO_EXCEPTION  = 31.U(EXC_CODE_W.W)
}


class ExceptionInfo extends Bundle {
  val exc_code             = UInt(EXC_CODE_W.W)
  val pc                   = UInt(XLEN.W)
  val data                 = UInt(XLEN.W)
  val in_branch_delay_slot = Bool() // exception happened in branch delay slot
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

  val cp0_reg_cause = RegInit(WireInit(new Bundle {
    val BD       = UInt(1.W)
    val IP7      = UInt(1.W)
    val EXC_CODE = UInt(5.W)
    0.U(2.W)
  }.Lit(_.IP7 -> 0.U, _.BD -> 0.U)))

  val cp0_reg_compare = RegInit(0.U(XLEN.W))

  val cp0_reg_status = RegInit(WireInit(new Bundle {
    val IM7 = UInt(1.W)
    val EXL = UInt(1.W)
    val IE  = UInt(1.W)
  }.Lit(_.IE -> 1.U, _.IM7 -> 0.U, _.EXL -> 0.U)))

  val cp0_reg_badvaddr = RegInit(0.U(XLEN.W))

  val cp0_reg_epc = RegInit(0.U(XLEN.W))

  //>>>>>>>>>>>>>>>>>>>>>>>>>
  // local signal declaration
  //<<<<<<<<<<<<<<<<<<<<<<<<<


  val has_exception_vec = Wire(Vec(ISSUE_NUM, Bool()))

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
        }
        is(CP0_STATUS_INDEX) {
          cp0_reg_status := io.write(i).data
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
      cp0_reg_cause.IP7 := 0.U
    }
  }

  when(cp0_reg_count === cp0_reg_compare) {
    cp0_reg_cause.IP7 := 1.U
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