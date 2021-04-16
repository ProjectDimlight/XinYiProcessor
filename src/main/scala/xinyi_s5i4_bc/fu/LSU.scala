package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._


/**
 * @module LSU
 * @param XLEN width of data
 * @param lsu_ctrl_bits
 */

class LSUIO extends Bundle {
    val in_data = Input(UInt(XLEN.W))
    val in_addr = Input(UInt(XLEN.W))
    val in_ctrl = Input(UInt(XLEN.W))
    val out_data = Output(UInt(XLEN.W))
    val out_addr = Output(UInt(XLEN.W))
    val ready = Output(Bool())
}


class LSU extends Module {
    val io = IO(new LSUIO)

    // TODO LSU by ziyue
}

