package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import utils._
import xinyi_s5i4_bc.fu.EXCCodeConfig._
import xinyi_s5i4_bc.parts.ControlConst._


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
  val ALU_XXX = 0.U(FU_CTRL_W.W)

  val ALU_SLL  = 0.U(FU_CTRL_W.W)
  val ALU_SRA  = 1.U(FU_CTRL_W.W)
  val ALU_SRL  = 2.U(FU_CTRL_W.W)
  val ALU_SLT  = 3.U(FU_CTRL_W.W)
  val ALU_SLTU = 4.U(FU_CTRL_W.W)
  val ALU_AND  = 5.U(FU_CTRL_W.W)
  val ALU_LUI  = 6.U(FU_CTRL_W.W)
  val ALU_NOR  = 7.U(FU_CTRL_W.W)
  val ALU_OR   = 8.U(FU_CTRL_W.W)
  val ALU_XOR  = 9.U(FU_CTRL_W.W)

  val ALU_ADD  = 10.U(FU_CTRL_W.W)
  val ALU_ADDU = 11.U(FU_CTRL_W.W)
  val ALU_SUB  = 12.U(FU_CTRL_W.W)
  val ALU_SUBU = 13.U(FU_CTRL_W.W)

  val ALU_DIV  = 16.U(FU_CTRL_W.W)
  val ALU_DIVU = 17.U(FU_CTRL_W.W)
  val ALU_MUL  = 18.U(FU_CTRL_W.W)
  val ALU_MULU = 19.U(FU_CTRL_W.W)

  val ALU_ERET = 24.U(FU_CTRL_W.W)
}

class ALU extends Module with ALUConfig with BALConfig {
  val io = IO(new FUIO)

  io.out.is_delay_slot := io.in.is_delay_slot

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // connect some unchanged wires
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  io.out.write_target := io.in.write_target
  io.out.rd := io.in.rd
  io.out.order := io.in.order
  io.out.pc := io.in.pc

  //>>>>>>>>>>>>>>>>
  // MDU operations
  //<<<<<<<<<<<<<<<<

  val a      = Cat((io.in.fu_ctrl === ALU_DIV || io.in.fu_ctrl === ALU_MUL) && io.in.a(XLEN - 1), io.in.a).asSInt()
  val b      = Cat((io.in.fu_ctrl === ALU_DIV || io.in.fu_ctrl === ALU_MUL) && io.in.b(XLEN - 1), io.in.b).asSInt()
  val mul_ab = (a * b).asUInt()

  // instantiate div
  val div     = Module(new DIV)
  val is_div  = io.in.fu_ctrl === ALU_DIV
  val div_res = Wire(UInt((2 * XLEN).W))

  div.io.aclk := clock
  div.io.s_axis_dividend_tvalid := is_div
  div.io.s_axis_divisor_tvalid := is_div
  div.io.s_axis_dividend_tdata := io.in.a
  div.io.s_axis_divisor_tdata := io.in.b
  div_res := div.io.m_axis_dout_tdata

  // instantiate divu
  val divu     = Module(new DIVU)
  val is_divu  = io.in.fu_ctrl === ALU_DIVU
  val divu_res = Wire(UInt((2 * XLEN).W))

  divu.io.aclk := clock
  divu.io.s_axis_dividend_tvalid := is_divu
  divu.io.s_axis_divisor_tvalid := is_divu
  divu.io.s_axis_dividend_tdata := io.in.a
  divu.io.s_axis_divisor_tdata := io.in.b
  divu_res := divu.io.m_axis_dout_tdata


  // ready
  io.out.ready := !(is_divu || is_div) || (is_divu && divu.io.m_axis_dout_tvalid) || (is_div && div.io.m_axis_dout_tvalid)

  //>>>>>>>>>>>>>>>>
  // ALU operations
  //<<<<<<<<<<<<<<<<

  io.out.data := MuxLookupBi(
    io.in.fu_ctrl,
    "hcafebabe".U,
    Seq(
      ALU_ADD -> (io.in.a + io.in.b),
      ALU_ADDU -> (io.in.a + io.in.b),
      ALU_SUB -> (io.in.a - io.in.b),
      ALU_SUBU -> (io.in.a - io.in.b),
      ALU_SLT -> (io.in.a.asSInt() < io.in.b.asSInt()),
      ALU_SLTU -> (io.in.a < io.in.b),
      ALU_AND -> (io.in.a & io.in.b),
      ALU_LUI -> Cat(io.in.b(XLEN / 2 - 1, 0), 0.U((XLEN / 2).W)),
      ALU_NOR -> (~(io.in.a | io.in.b)),
      ALU_OR -> (io.in.a | io.in.b),
      ALU_XOR -> (io.in.a ^ io.in.b),
      ALU_SLL -> (io.in.b << io.in.a(4, 0)),
      ALU_SRA -> (io.in.b.asSInt() >> io.in.a(4, 0)).asUInt(),
      ALU_SRL -> (io.in.b >> io.in.a(4, 0)),
      ALU_DIV -> div_res(2 * XLEN - 1, XLEN),
      ALU_DIVU -> divu_res(2 * XLEN - 1, XLEN),
      ALU_MUL -> mul_ab(XLEN - 1, 0),
      ALU_MULU -> mul_ab(XLEN - 1, 0),
      JPC -> (io.in.pc + 8.U),
      BrGEPC -> (io.in.pc + 8.U),
      BrLTPC -> (io.in.pc + 8.U),
      ALU_ERET -> 0.U
    )
  )

  val hi = Mux(
    io.in.write_target === DHi,
    io.out.data,
    MuxLookupBi(
      io.in.fu_ctrl,
      "hcafebabe".U,
      Seq(
        ALU_DIV -> div_res(XLEN - 1, 0),
        ALU_DIVU -> divu_res(XLEN - 1, 0),
        ALU_MUL -> mul_ab(2 * XLEN - 1, XLEN),
        ALU_MULU -> mul_ab(2 * XLEN - 1, XLEN),
        ALU_ERET -> io.in.a
      )
    )
  )

  // TODO update exception in ALU
  val ov = ((io.in.fu_ctrl === ALU_ADD) &&
    (io.in.a(XLEN - 1) === io.in.b(XLEN - 1)) &&
    (io.in.a(XLEN - 1) =/= io.out.data(XLEN - 1))) ||
    ((io.in.fu_ctrl === ALU_SUB) &&
      (io.in.a(XLEN - 1) =/= io.in.b(XLEN - 1)) &&
      (io.in.a(XLEN - 1) =/= io.out.data(XLEN - 1)))

  io.out.exc_code := MuxCase(
    NO_EXCEPTION,
    Array(
      (io.in.pc(1, 0) =/= 0.U) -> EXC_CODE_ADEL,
      (io.in.fu_ctrl === FU_XXX) -> EXC_CODE_RI,
      (io.in.fu_ctrl === FU_SYSCALL) -> EXC_CODE_SYS,
      (io.in.fu_ctrl === FU_BREAK) -> EXC_CODE_BP,
      (io.in.fu_ctrl === ALU_ERET) -> EXC_CODE_ERET,
      ov -> EXC_CODE_OV
    )
  )
  io.out.hi := Mux(
    io.out.exc_code === EXC_CODE_ADEL,
    io.in.pc,
    hi
  )
}


class DIV extends BlackBox {
  val io = IO(new Bundle {
    val aclk                   = Input(Clock())
    val s_axis_divisor_tvalid  = Input(Bool())
    val s_axis_divisor_tdata   = Input(UInt(XLEN.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tdata  = Input(UInt(XLEN.W))
    val m_axis_dout_tvalid     = Output(Bool())
    val m_axis_dout_tdata      = Output(UInt((2 * XLEN).W))
  })

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // this is the divider IP core instantiate template
  //------------------------------------------------------------------------------------------------
  //  DIV your_instance_name (
  //  .aclk(aclk),                                      // input wire aclk
  //  .s_axis_divisor_tvalid(s_axis_divisor_tvalid),    // input wire s_axis_divisor_tvalid
  //  .s_axis_divisor_tdata(s_axis_divisor_tdata),      // input wire [39 : 0] s_axis_divisor_tdata
  //  .s_axis_dividend_tvalid(s_axis_dividend_tvalid),  // input wire s_axis_dividend_tvalid
  //  .s_axis_dividend_tdata(s_axis_dividend_tdata),    // input wire [39 : 0] s_axis_dividend_tdata
  //  .m_axis_dout_tvalid(m_axis_dout_tvalid),          // output wire m_axis_dout_tvalid
  //  .m_axis_dout_tdata(m_axis_dout_tdata)            // output wire [79 : 0] m_axis_dout_tdata
  //  );
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

}

class DIVU extends BlackBox {
  val io = IO(new Bundle {
    val aclk                   = Input(Clock())
    val s_axis_divisor_tvalid  = Input(Bool())
    val s_axis_divisor_tdata   = Input(UInt(XLEN.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tdata  = Input(UInt(XLEN.W))
    val m_axis_dout_tvalid     = Output(Bool())
    val m_axis_dout_tdata      = Output(UInt((2 * XLEN).W))
  })

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  // this is the divider IP core instantiate template
  //------------------------------------------------------------------------------------------------
  //  DIVU your_instance_name (
  //  .aclk(aclk),                                      // input wire aclk
  //  .s_axis_divisor_tvalid(s_axis_divisor_tvalid),    // input wire s_axis_divisor_tvalid
  //  .s_axis_divisor_tdata(s_axis_divisor_tdata),      // input wire [39 : 0] s_axis_divisor_tdata
  //  .s_axis_dividend_tvalid(s_axis_dividend_tvalid),  // input wire s_axis_dividend_tvalid
  //  .s_axis_dividend_tdata(s_axis_dividend_tdata),    // input wire [39 : 0] s_axis_dividend_tdata
  //  .m_axis_dout_tvalid(m_axis_dout_tvalid),          // output wire m_axis_dout_tvalid
  //  .m_axis_dout_tdata(m_axis_dout_tdata)            // output wire [79 : 0] m_axis_dout_tdata
  //  );
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

}
