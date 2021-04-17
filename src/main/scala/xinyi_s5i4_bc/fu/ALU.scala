package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.parts.ControlConst._
import config.config._
import xinyi_s5i4_bc.fu._

/**
 * @module ALU
 *         ---
 * @param alu_ctrl_bits width of control
 *                      ---
 * @IO
 * @input in.a         value a
 * @input in.b         value b
 * @input in.fu_ctrl      control signal
 * @output out_res     result
 * @output exception   bool signal indicating an overflow
 *         ---
 * @Status can emit verilog code successfully.
 */

trait ALUConfig {
  final val ALU_XXX   = 0.U(5.W)
  final val FU_CTRL_W = ALU_XXX.getWidth // width of ALU control signal

  final val ALU_ADD  = 0.U(FU_CTRL_W.W)
  final val ALU_ADDU = 1.U(FU_CTRL_W.W)
  final val ALU_SUB  = 2.U(FU_CTRL_W.W)
  final val ALU_SLT  = 3.U(FU_CTRL_W.W)
  final val ALU_SLTU = 4.U(FU_CTRL_W.W)
  final val ALU_AND  = 5.U(FU_CTRL_W.W)
  final val ALU_LUI  = 6.U(FU_CTRL_W.W)
  final val ALU_NOR  = 7.U(FU_CTRL_W.W)
  final val ALU_OR   = 8.U(FU_CTRL_W.W)
  final val ALU_XOR  = 9.U(FU_CTRL_W.W)
  final val ALU_SLL  = 10.U(FU_CTRL_W.W)
  final val ALU_SRA  = 11.U(FU_CTRL_W.W)
  final val ALU_SRL  = 12.U(FU_CTRL_W.W)

  final val ALU_DIV  = 16.U(FU_CTRL_W.W)
  final val ALU_DIVU = 17.U(FU_CTRL_W.W)
  final val ALU_MUL  = 18.U(FU_CTRL_W.W)
  final val ALU_MULU = 19.U(FU_CTRL_W.W)
}

class ALU extends Module with ALUConfig with BALConfig {
  val io = IO(new FUIO)

  val a = Cat((io.in.fu_ctrl === ALU_DIV || io.in.fu_ctrl === ALU_MUL) && io.in.a(XLEN - 1), io.in.a)
  val b = Cat((io.in.fu_ctrl === ALU_DIV || io.in.fu_ctrl === ALU_MUL) && io.in.b(XLEN - 1), io.in.b)

  val mul_ab = a * b

  io.out.data := MuxLookup(
    io.in.fu_ctrl,
    "hcafebabe".U,
    Seq(
      ALU_ADD -> (io.in.a + io.in.b),
      ALU_ADDU -> (io.in.a + io.in.b),
      ALU_SUB -> (io.in.a - io.in.b),
      ALU_SLT -> (io.in.a.asSInt() < io.in.b.asSInt()),
      ALU_SLTU -> (io.in.a < io.in.b),
      ALU_AND -> (io.in.a & io.in.b),
      ALU_LUI -> Cat(io.in.b(XLEN - 1, XLEN / 2), 0.U((XLEN / 2).W)),
      ALU_NOR -> (~(io.in.a | io.in.b)),
      ALU_OR -> (io.in.a | io.in.b),
      ALU_XOR -> (io.in.a ^ io.in.b),
      ALU_SLL -> (io.in.a << io.in.b(4, 0)),
      ALU_SRA -> (io.in.a.asSInt() >> io.in.b(4, 0)).asUInt(),
      ALU_SRL -> (io.in.a >> io.in.b(4, 0)),
      ALU_DIV -> a % b,
      ALU_DIVU -> a % b,
      ALU_MUL -> mul_ab(2 * XLEN - 1, XLEN),
      ALU_MULU -> mul_ab(XLEN - 1, 0),
      JPC -> io.in.pc,
      BrGEPC -> io.in.pc,
      BrLTPC -> io.in.pc,
    )
  )

  io.out.hi := MuxLookup(
    io.in.fu_ctrl,
    "hcafebabe".U,
    Seq(
      ALU_DIV -> (a / b),
      ALU_DIVU -> (a / b),
      ALU_MUL -> mul_ab(XLEN - 1, 0),
      ALU_MULU -> mul_ab(XLEN - 1, 0),
    )
  )

  io.out.exception := ((io.in.fu_ctrl === ALU_ADD) &&
    (io.in.a(XLEN - 1) === io.in.b(XLEN - 1)) &&
    (io.in.a(XLEN - 1) =/= io.out.data(XLEN - 1))) ||
    ((io.in.fu_ctrl === ALU_SUB) &&
      (io.in.a(XLEN - 1) =/= io.in.b(XLEN - 1)) &&
      (io.in.a(XLEN - 1) =/= io.out.data(XLEN - 1)))
}
