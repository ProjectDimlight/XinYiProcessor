package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._

class HiLo extends Module {
    val io = IO(new Bundle {
        val in_hi = Input(UInt(XLEN.W))
        val in_lo = Input(UInt(XLEN.W))
        val in_hilo_wen = Input(Bool())
        val out_hi = Output(UInt(XLEN.W))
        val out_lo = Output(UInt(XLEN.W))
    })

    val hi = RegInit(0.U(XLEN.W))
    val lo = RegInit(0.U(XLEN.W))

    when(io.in_hilo_wen) {
        hi := io.in_hi
        lo := io.in_lo
    }

    io.out_hi := hi
    io.out_lo := lo
}
