package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._


/**
 * @module LSU
 * @param XLEN width of data
 * @param lsu_ctrl_bits
 */
class LSU(addr_bits: Int, lsu_ctrl_bits: Int) extends Module {
    val io = IO(new Bundle {
        val in_data = Input(UInt(XLEN.W))
        val in_addr = Input(UInt(addr_bits.W))
        val in_ctrl = Input(UInt(lsu_ctrl_bits.W))
        val out_data = Output(UInt(XLEN.W))
        val out_addr = Output(UInt(addr_bits.W))
        val ready = Output(Bool)
    })

    // TODO LSU by ziyue
}

