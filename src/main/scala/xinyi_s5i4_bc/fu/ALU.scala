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

  val ALU_DIV  = 24.U(FU_CTRL_W.W)
  val ALU_DIVU = 25.U(FU_CTRL_W.W)
  val ALU_MUL  = 26.U(FU_CTRL_W.W)
  val ALU_MULU = 27.U(FU_CTRL_W.W)

  val ALU_ERET = 28.U(FU_CTRL_W.W)
}

class ALUIO extends FUIO {
}

class ALU extends Module with ALUConfig with BALConfig with CP0Config {
  val io = IO(new ALUIO)

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
  val mulu_ab  = io.in.a * io.in.b
  val mul_ab   = io.in.a.asSInt() * io.in.b.asSInt()
  val is_mul   = (io.in.fu_ctrl === ALU_MUL) || (io.in.fu_ctrl === ALU_MULU)

  val mulu_ab_reg = RegNext(mulu_ab)
  val mul_ab_reg  = RegNext(mul_ab)
  val mul_valid_reg = RegInit(false.B)
  
  mul_valid_reg := Mux(mul_valid_reg, false.B, is_mul)

  val div_res  = Wire(UInt((2 * XLEN).W))
  val divu_res = Wire(UInt((2 * XLEN).W))

  if (!VERILATOR) {
    // instantiate div
    val div    = Module(new DIV)
    val is_div = io.in.fu_ctrl === ALU_DIV
    val is_div_last = RegInit(false.B)

    when (is_div & div.io.s_axis_dividend_tready) {
      is_div_last := true.B
    }
    when (div.io.m_axis_dout_tvalid) {
      is_div_last := false.B
    }

    div.io.aclk := clock
    div.io.s_axis_dividend_tvalid := is_div & !is_div_last
    div.io.s_axis_divisor_tvalid := is_div & !is_div_last
    div.io.s_axis_dividend_tdata := io.in.a
    div.io.s_axis_divisor_tdata := io.in.b
    div_res := div.io.m_axis_dout_tdata

    // instantiate divu
    val divu    = Module(new DIVU)
    val is_divu = io.in.fu_ctrl === ALU_DIVU
    val is_divu_last = RegInit(false.B)
    
    when (is_divu & divu.io.s_axis_dividend_tready) {
      is_divu_last := true.B
    }
    when (divu.io.m_axis_dout_tvalid) {
      is_divu_last := false.B
    }
    

    divu.io.aclk := clock
    divu.io.s_axis_dividend_tvalid := is_divu & !is_divu_last
    divu.io.s_axis_divisor_tvalid := is_divu & !is_divu_last
    divu.io.s_axis_dividend_tdata := io.in.a
    divu.io.s_axis_divisor_tdata := io.in.b
    divu_res := divu.io.m_axis_dout_tdata

    // ready
    io.out.ready := 
      (!(is_divu || is_div) || (divu.io.m_axis_dout_tvalid) || (div.io.m_axis_dout_tvalid)) &&
      (!is_mul || mul_valid_reg)
  } else {
    val sign_a = Wire(SInt(XLEN.W))
    val sign_b = Wire(SInt(XLEN.W))

    sign_a := io.in.a.asSInt()
    sign_b := io.in.b.asSInt()

    div_res := Cat(sign_a / sign_b, sign_a % sign_b)
    divu_res := Cat(io.in.a / io.in.b, io.in.a % io.in.b)
    io.out.ready := true.B
  }

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
      ALU_SLL -> (io.in.a << io.in.b(4, 0)),
      ALU_SRA -> (io.in.a.asSInt() >> io.in.b(4, 0)).asUInt(),
      ALU_SRL -> (io.in.a >> io.in.b(4, 0)),
      ALU_DIV -> div_res(2 * XLEN - 1, XLEN),
      ALU_DIVU -> divu_res(2 * XLEN - 1, XLEN),
      ALU_MUL -> mul_ab_reg(XLEN - 1, 0),
      ALU_MULU -> mulu_ab_reg(XLEN - 1, 0),
      JPC -> (io.in.pc + 8.U),
      BrGEPC -> (io.in.pc + 8.U),
      BrLTPC -> (io.in.pc + 8.U),
      ALU_ERET -> 0.U
    )
  )

  val hi =
  MuxLookupBi(
    io.in.fu_ctrl(1, 0),
    "hcafebabe".U,
    Seq(
      0.U -> div_res(XLEN - 1, 0),
      1.U -> divu_res(XLEN - 1, 0),
      2.U -> mul_ab_reg(2 * XLEN - 1, XLEN),
      3.U -> mulu_ab_reg(2 * XLEN - 1, XLEN)
    )
  )
  
  io.out.hi := hi

  val ov =
    ((io.in.fu_ctrl === ALU_ADD) &&
      (io.in.a(XLEN - 1) === io.in.b(XLEN - 1)) &&
      (io.in.a(XLEN - 1) =/= (io.in.a + io.in.b)(XLEN - 1))) ||
    ((io.in.fu_ctrl === ALU_SUB) &&
      (io.in.a(XLEN - 1) =/= io.in.b(XLEN - 1)) &&
      (io.in.a(XLEN - 1) =/= (io.in.a - io.in.b)(XLEN - 1)))
  /*
  val ov = ((io.in.fu_ctrl === ALU_ADD) &&
            (io.in.a(XLEN - 1) === io.in.b(XLEN - 1)) ||
            (io.in.fu_ctrl === ALU_SUB) &&
            (io.in.a(XLEN - 1) =/= io.in.b(XLEN - 1))) &&
          io.in.a(XLEN - 1) ^ io.in.ov
  */
  //val ov = io.in.ov

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
  io.out.exception := 
    (io.in.pc(1, 0) =/= 0.U) |
    (io.in.fu_ctrl === FU_XXX) |
    (io.in.fu_ctrl === FU_SYSCALL) |
    (io.in.fu_ctrl === FU_BREAK) |
    (io.in.fu_ctrl === ALU_ERET) |
    ov
  
  io.out.exc_meta := Mux(
    io.in.write_target === DCP0 &
    io.in.rd === CP0_CAUSE_INDEX,
    io.out.data,
    Mux(io.in.fu_ctrl === ALU_ERET , io.in.a, io.in.pc)
  )
}


class DIV extends BlackBox {
  val io = IO(new Bundle {
    val aclk                   = Input(Clock())
    val s_axis_divisor_tvalid  = Input(Bool())
    val s_axis_divisor_tready  = Output(Bool())
    val s_axis_divisor_tdata   = Input(UInt(XLEN.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tready = Output(Bool())
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
    val s_axis_divisor_tready  = Output(Bool())
    val s_axis_divisor_tdata   = Input(UInt(XLEN.W))
    val s_axis_dividend_tvalid = Input(Bool())
    val s_axis_dividend_tready = Output(Bool())
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
