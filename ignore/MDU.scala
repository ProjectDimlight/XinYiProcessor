//import chisel3._
//import chisel3.util._
import utils._
//import config.config._
//
//
///**
// *
// * @param XLEN
// * @IO
// *      @in_a
// *      @in_b
// *      @in_ctrl
// *      @out_hi
// *      @out_lo
// */
//class MDU extends Module {
//    val io = IO(new Bundle {
//        val in_a = Input(UInt(XLEN.W))
//        val in_b = Input(UInt(XLEN.W))
//        val in_ctrl = Input(UInt(alu_op_w.W))
//        val out_hi = Output(UInt(XLEN.W))
//        val out_lo = Output(UInt(XLEN.W))
//    })
//
//
//    io.out_hi := Mux(io.in_ctrl === MDUDIV || io.in_ctrl === MDUDIVU, a % b, mul_ab(2 * XLEN - 1, XLEN))
//    io.out_lo := Mux(io.in_ctrl === MDUDIV || io.in_ctrl === MDUDIVU, a / b, mul_ab(XLEN - 1, 0))
//}