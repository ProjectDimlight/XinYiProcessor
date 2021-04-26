package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
import config.config._

class HiLo extends Module {
  val io = IO(new Bundle {
    val in_hi = Input(UInt(XLEN.W))
    val in_lo = Input(UInt(XLEN.W))
    val in_hi_wen = Input(Bool())
    val in_lo_wen = Input(Bool())
    val out_hi = Output(UInt(XLEN.W))
    val out_lo = Output(UInt(XLEN.W))
  })

  val hi = RegInit(0.U(XLEN.W))
  val lo = RegInit(0.U(XLEN.W))

  io.out_hi := hi
  io.out_lo := lo

  when (io.in_hi_wen) {
    hi := io.in_hi
    io.out_hi := io.in_hi
  }
  when (io.in_lo_wen) {
    lo := io.in_lo
    io.out_lo := io.in_lo
  }

}
