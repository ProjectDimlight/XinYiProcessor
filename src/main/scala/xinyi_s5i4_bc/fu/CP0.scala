package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import chisel3.experimental.BundleLiterals._

trait CP0Config {
    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    // CP0 Register Configurations
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
    final val CP0_INDEX_WIDTH: Int = 5

    final val CP0_BADVADDR_INDEX = 8
    final val CP0_COUNT_INDEX = 9
    final val CP0_COMPARE_INDEX = 11
    final val CP0_STATUS_INDEX = 12
    final val CP0_CAUSE_INDEX = 13
    final val CP0_EPC_INDEX = 14

    // reference
    final val EXC_CODE_INT = 0
    final val EXC_CODE_ADEL = 4
    final val EXC_CODE_ADES = 5
    final val EXC_CODE_TR = 13
}


/**
 * @module CPO
 * @IO
 */
class CP0 extends Module with CP0Config {
    val io = IO(new Bundle {
        val in_cp0_wen = Input(Bool())
        val in_read_index = Input(UInt(CP0_INDEX_WIDTH.W))
        val in_write_index = Input(UInt(CP0_INDEX_WIDTH.W))
        val in_write_val = Input(UInt(XLEN.W))
        val in_eret_en = Input(Bool())
        val in_delay_slot_exception = Input(Bool())
        val in_exc_code = Input(UInt(5.W))
        val in_epc = Input(UInt(XLEN.W))
        val in_ls_badvaddr = Input(UInt(XLEN.W))
        val in_has_exception = Input(Bool())
        //        val in_exception_en = Input(Bool())
        //
        //        val out_epc = Output(UInt(CP0_REG_WIDTH.W))
        //        val out_cause = Output(UInt(CP0_REG_WIDTH.W))
        //        val out_status = Output(UInt(CP0_REG_WIDTH.W))
        val out_read_val = Output(UInt(XLEN.W))
    })

    //>>>>>>>>>>>>>>>>>>>>>>>>>>
    // cp0 registers declaration
    //<<<<<<<<<<<<<<<<<<<<<<<<<<
    val cp0_reg_count = RegInit(0.U(XLEN.W))

    val cp0_reg_cause = RegInit(WireInit(new Bundle {
        val BD = UInt(1.W)
        val IP7 = UInt(1.W)
        val EXC_CODE = UInt(5.W)
        0.U(2.W)
    }.Lit(_.IP7 -> 0.U, _.BD -> 0.U)))

    val cp0_reg_compare = RegInit(0.U(XLEN.W))

    val cp0_reg_status = RegInit(WireInit(new Bundle {
        val IM7 = UInt(1.W)
        val EXL = UInt(1.W)
        val IE = UInt(1.W)
    }.Lit(_.IE -> 1.U, _.IM7 -> 0.U, _.EXL -> 0.U)))

    val cp0_reg_badvaddr = RegInit(0.U(XLEN.W))

    val cp0_reg_epc = RegInit(0.U(XLEN.W))

    //>>>>>>>>>>>>>>>>>>>>>>>>>
    // local signal declaration
    //<<<<<<<<<<<<<<<<<<<<<<<<<
    val has_exception = Wire(Bool())

    has_exception := !cp0_reg_status.EXL && io.in_has_exception





    //>>>>>>>>>>>>>>>>>>>>
    // handle read & write
    //<<<<<<<<<<<<<<<<<<<<
    io.out_read_val := MuxLookup(
        io.in_read_index,
        "hcafebabe".U,
        Seq(
            CP0_BADVADDR_INDEX.U -> cp0_reg_badvaddr,
            CP0_COUNT_INDEX.U -> cp0_reg_count,
            CP0_COMPARE_INDEX.U -> cp0_reg_compare,
            CP0_STATUS_INDEX.U -> cp0_reg_status.asUInt(),
            CP0_CAUSE_INDEX.U -> cp0_reg_cause.asUInt(),
            CP0_EPC_INDEX.U -> cp0_reg_epc,
        )
    )


    // when mtc0 and not eret
    when(io.in_cp0_wen && !io.in_eret_en && !has_exception) {
        switch(io.in_write_index) {
            is(CP0_COUNT_INDEX.U) {
                cp0_reg_count := io.in_write_val
            }
            is(CP0_COMPARE_INDEX.U) {
                cp0_reg_compare := io.in_write_val
            }
            is(CP0_EPC_INDEX.U) {
                cp0_reg_epc := io.in_write_val
            }
        }
    }


    //>>>>>>>>>>>>>>>>>>>>>>>>>>>
    // handle cp0 register events
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<

    // counter register autoincrement 1 each cycle
    cp0_reg_count := Mux(cp0_reg_count === "hFFFFFFFF".U(XLEN.W), 0.U, cp0_reg_count + 1.U)

    when(cp0_reg_count === cp0_reg_compare) {
        cp0_reg_cause.IP7 := 1.U
    }.elsewhen(io.in_cp0_wen && io.in_write_index === CP0_COMPARE_INDEX.U) {
        cp0_reg_cause.IP7 := 0.U
    }


    when(has_exception) {
        cp0_reg_status.EXL := 1.U
        cp0_reg_cause.BD := io.in_delay_slot_exception
        cp0_reg_epc := Mux(io.in_delay_slot_exception, io.in_epc - 4.U, io.in_epc)
        cp0_reg_cause.EXC_CODE := io.in_exc_code
        when(io.in_exc_code === EXC_CODE_ADEL.U || io.in_exc_code === EXC_CODE_ADES.U) {
            cp0_reg_badvaddr := io.in_ls_badvaddr
        }
    }.elsewhen(io.in_eret_en) {
        cp0_reg_status.EXL := 0.U
    }
}