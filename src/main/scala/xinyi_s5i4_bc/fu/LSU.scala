package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._


/**
 * @module LSU
 * @param data_bits width of data
 * @param lsu_ctrl_bits
 */
class LSU(data_bits: Int, addr_bits: Int, lsu_ctrl_bits: Int) extends Module {
    val io = IO(new Bundle {
        val in_data = Input(UInt(data_bits.W))
        val in_addr = Input(UInt(addr_bits.W))
        val in_ctrl = Input(UInt(lsu_ctrl_bits.W))
        val out_data = Output(UInt(data_bits.W))
        val out_addr = Output(UInt(addr_bits.W))
        val ready = Output(Bool)
    })

    // store buffer and load buffer
    val store_buffer = Reg(Vec(STORE_BUFFER_DEPTH, UInt(data_bits.W)))
    val load_buffer = Reg(Vec(LOAD_BUFFER_DEPTH, UInt(data_bits.W)))

    // buffer signals
    val store_buffer_empty = RegInit(true.B)
    val store_buffer_full = RegInit(true.B)
    val load_buffer_empty = RegInit(true.B)
    val load_buffer_full = RegInit(true.B)

    

}

