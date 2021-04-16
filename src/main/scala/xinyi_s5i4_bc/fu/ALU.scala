package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.parts.ControlConst._
import config.config._

/**
 * @module ALU
 *         ---
 * @param alu_ctrl_bits width of control
 *                      ---
 * @IO
 * @input in_a         value a
 * @input in_b         value b
 * @input in_ctrl      control signal
 * @output out_res     result
 * @output exception   bool signal indicating an overflow
 *         ---
 * @Status can emit verilog code successfully.
 */

trait ALUConfig {
    final val ALUXXX = 0.U(5.W)
    final val ALU_CTRL_WIDTH = ALUXXX.getWidth // width of ALU control signal

    final val ALU_ADD = 0.U(ALU_CTRL_WIDTH.W)
    final val ALU_ADDU = 1.U(ALU_CTRL_WIDTH.W)
    final val ALU_SUB = 2.U(ALU_CTRL_WIDTH.W)
    final val ALU_SLT = 3.U(ALU_CTRL_WIDTH.W)
    final val ALU_SLTU = 4.U(ALU_CTRL_WIDTH.W)
    final val ALU_AND = 5.U(ALU_CTRL_WIDTH.W)
    final val ALU_LUI = 6.U(ALU_CTRL_WIDTH.W)
    final val ALU_NOR = 7.U(ALU_CTRL_WIDTH.W)
    final val ALU_OR = 8.U(ALU_CTRL_WIDTH.W)
    final val ALU_XOR = 9.U(ALU_CTRL_WIDTH.W)
    final val ALU_SLL = 10.U(ALU_CTRL_WIDTH.W)
    final val ALU_SRA = 11.U(ALU_CTRL_WIDTH.W)
    final val ALU_SRL = 12.U(ALU_CTRL_WIDTH.W)
    final val ALU_PC = 15.U(ALU_CTRL_WIDTH.W)
    final val ALU_DIV = 16.U(ALU_CTRL_WIDTH.W)
    final val ALU_DIVU = 17.U(ALU_CTRL_WIDTH.W)
    final val ALU_MUL = 18.U(ALU_CTRL_WIDTH.W)
    final val ALU_MULU = 19.U(ALU_CTRL_WIDTH.W)
}




class ALU extends Module with ALUConfig {
    val io = IO(new Bundle {
        val in_a = Input(UInt(XLEN.W))
        val in_b = Input(UInt(XLEN.W))
        val in_ctrl = Input(UInt(ALU_CTRL_WIDTH.W))
        val in_pc = Input(UInt(XLEN.W))
        val out_lo = Output(UInt(XLEN.W))
        val out_hi = Output(UInt(XLEN.W))
        val exception = Output(Bool())
    })

    val a = Cat((io.in_ctrl === ALU_DIV || io.in_ctrl === ALU_MUL) && io.in_a(XLEN - 1), io.in_a)
    val b = Cat((io.in_ctrl === ALU_DIV || io.in_ctrl === ALU_MUL) && io.in_b(XLEN - 1), io.in_b)

    val mul_ab = a * b

    io.out_lo := MuxLookup(
        io.in_ctrl,
        "hcafebabe".U,
        Seq(
            ALU_ADD -> (io.in_a + io.in_b),
            ALU_ADDU -> (io.in_a + io.in_b),
            ALU_SUB -> (io.in_a - io.in_b),
            ALU_SLT -> (io.in_a.asSInt() < io.in_b.asSInt()),
            ALU_SLTU -> (io.in_a < io.in_b),
            ALU_AND -> (io.in_a & io.in_b),
            ALU_LUI -> Cat(io.in_b(XLEN - 1, XLEN / 2), 0.U((XLEN / 2).W)),
            ALU_NOR -> (~(io.in_a | io.in_b)),
            ALU_OR -> (io.in_a | io.in_b),
            ALU_XOR -> (io.in_a ^ io.in_b),
            ALU_SLL -> (io.in_a << io.in_b(4, 0)),
            ALU_SRA -> (io.in_a.asSInt() >> io.in_b(4, 0)).asUInt(),
            ALU_SRL -> (io.in_a >> io.in_b(4, 0)),
            ALU_DIV -> a % b,
            ALU_DIVU -> a % b,
            ALU_MUL -> mul_ab(2 * XLEN - 1, XLEN),
            ALU_MULU -> mul_ab(XLEN - 1, 0),
            ALU_PC -> io.in_pc,
        )
    )

    io.out_hi := MuxLookup(
        io.in_ctrl,
        "hcafebabe".U,
        Seq(
            ALU_DIV -> (a / b),
            ALU_DIVU -> (a / b),
            ALU_MUL -> mul_ab(XLEN - 1, 0),
            ALU_MULU -> mul_ab(XLEN - 1, 0),
        )
    )

    io.exception := ((io.in_ctrl === ALU_ADD) &&
        (io.in_a(XLEN - 1) === io.in_b(XLEN - 1)) &&
        (io.in_a(XLEN - 1) =/= io.out_lo(XLEN - 1))) ||
        ((io.in_ctrl === ALU_SUB) &&
            (io.in_a(XLEN - 1) =/= io.in_b(XLEN - 1)) &&
            (io.in_a(XLEN - 1) =/= io.out_lo(XLEN - 1)))
}
