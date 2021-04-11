package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._


/**
 * @module  CPO
 * @IO
 */
class CP0 extends Module {
    val io = IO(new Bundle {
        val in_cp0_wen = Input(Bool())
        val in_read_index = Input(UInt(CP0_INDEX_WIDTH.W))
        val in_write_index = Input(UInt(CP0_INDEX_WIDTH.W))
        val in_eret_en = Input(Bool())
        val in_exception_en = Input(Bool())

        val out_epc = Output(UInt(CP0_REG_WIDTH.W))
        val out_cause = Output(UInt(CP0_REG_WIDTH.W))
        val out_status = Output(UInt(CP0_REG_WIDTH.W))
        val out_cp0_reg = Output(UInt(CP0_REG_WIDTH.W))
    })

    // cp0 register file
    val cp0_regfile = Reg(Vec(32, UInt(XLEN.W)))


    cp0_regfile(CP0_COUNT_INDEX) := cp0_regfile(CP0_COUNT_INDEX) + 1.U  // counter register autoincrement 1 each cycle



    // handle counter
    val timer_interrupt = Wire(Bool())
    timer_interrupt :=

    when(cp0_regfile(CP0_COUNT_INDEX) === cp0_regfile(CP0_COMPARE_INDEX)) {
        cp0_regfile(CP0_CAUSE_INDEX)(15) := 1.U
    }



//
//    when(io.in_cp0_wen) {
//        // TODO update CP0 register
//    }
//
//    when(io.in_exception_en) {
//        // TODO handle exception
//    }
//
//    when(io.in_eret_en) {
//        // TODO handle eret
//    }

    io.out_cp0_reg := cp0_regfile(io.in_read_index)
    io.out_status := cp0_regfile(CP0_STATUS_INDEX)
    io.out_cause := cp0_regfile(CP0_CAUSE_INDEX)
    io.out_epc := cp0_regfile(CP0_EPC_INDEX)
}
