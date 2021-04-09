package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.parts.ControlConst._


/**
 * @module ALU
 * ---
 * @param data_bits     width of data
 * @param alu_ctrl_bits width of control
 * ---
 * @IO
 * @input in_a         value a
 * @input in_b         value b
 * @input in_ctrl      control signal
 * @output out_res     result
 * @output err_overflow    bool signal indicating an overflow
 * ---
 * @Status can emit verilog code successfully.
 */
class ALU(data_bits: Int, alu_ctrl_bits: Int) extends Module {
    val io = IO(new Bundle {
        val in_a = Input(UInt(data_bits.W))
        val in_b = Input(UInt(data_bits.W))
        val in_ctrl = Input(UInt(alu_ctrl_bits.W))

        val out_res = Output(UInt(data_bits.W))
        val err_overflow = Output(Bool())
    })


    io.out_res := MuxLookup(
        io.in_ctrl,
        "hcafebabe".U,
        Seq(
            ALUADD -> (io.in_a + io.in_b),
            ALUADDU -> (io.in_a + io.in_b),
            ALUSUB -> (io.in_a - io.in_b),
            ALUSLT -> (io.in_a.asSInt() < io.in_b.asSInt()),
            ALUSLTU -> (io.in_a < io.in_b),
            ALUAND -> (io.in_a & io.in_b),
            ALULUI -> Cat(io.in_b(data_bits - 1, data_bits / 2), 0.U((data_bits / 2).W)),
            ALUNOR -> (~(io.in_a | io.in_b)),
            ALUOR -> (io.in_a | io.in_b),
            ALUXOR -> (io.in_a ^ io.in_b),
            ALUSLL -> (io.in_a << io.in_b(4, 0)),
            ALUSRA -> (io.in_a.asSInt() >> io.in_b(4, 0)).asUInt(),
            ALUSRL -> (io.in_a >> io.in_b(4, 0))
        )
    )

    io.err_overflow := ((io.in_ctrl === ALUADD) &&
        (io.in_a(data_bits - 1) === io.in_b(data_bits - 1)) &&
        (io.in_a(data_bits - 1) =/= io.out_res(data_bits - 1))) ||
        ((io.in_ctrl === ALUSUB) &&
            (io.in_a(data_bits - 1) =/= io.in_b(data_bits - 1)) &&
            (io.in_a(data_bits - 1) =/= io.out_res(data_bits - 1)))
}
