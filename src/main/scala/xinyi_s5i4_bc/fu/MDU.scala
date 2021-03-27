
package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.parts.ControlConst._

class MDU(data_bits: Int, mdu_ctrl_bits: Int) extends Module {
    val io = IO(new Bundle {
        val in_a = Input(UInt(data_bits.W))
        val in_b = Input(UInt(data_bits.W))
        val in_ctrl = Input(UInt(mdu_ctrl_bits.W))
        val out_hi = Output(UInt(data_bits.W))
        val out_lo = Output(UInt(data_bits.W))
    })


    val a = Cat((io.in_ctrl === MDUDIV || io.in_ctrl === MDUMUL) && io.in_a(data_bits - 1), io.in_a)
    val b = Cat((io.in_ctrl === MDUDIV || io.in_ctrl === MDUMUL) && io.in_b(data_bits - 1), io.in_b)

    val mul_ab = a * b

    io.out_hi := Mux(io.in_ctrl === MDUDIV || io.in_ctrl === MDUDIVU, a % b, mul_ab(2 * data_bits - 1, data_bits))
    io.out_lo := Mux(io.in_ctrl === MDUDIV || io.in_ctrl === MDUDIVU, a / b, mul_ab(data_bits - 1, 0))
}