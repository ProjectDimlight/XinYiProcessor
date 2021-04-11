package xinyi_s5i4_bc.fu

import chisel3._
import config.config._


/**
 * @Module Regs
 */
class Regs extends Module {
    val io = IO(new Bundle {
        val in_wen = Input(Bool())
        val in_rd = Input(UInt(REG_INDEX_WIDTH.W))
        val in_rs = Input(UInt(REG_INDEX_WIDTH.W))
        val in_rt = Input(UInt(REG_INDEX_WIDTH.W))
        val in_rd_val = Input(UInt(XLEN.W))
        val out_rs_val = Output(UInt(XLEN.W))
        val out_rt_val = Output(UInt(XLEN.W))
    })

    // register file
    val regfile = Reg(Vec(31, UInt(XLEN.W)))

    val rs_real_idx = Wire(UInt(REG_INDEX_WIDTH.W))
    val rt_real_idx = Wire(UInt(REG_INDEX_WIDTH.W))
    val rd_real_idx = Wire(UInt(REG_INDEX_WIDTH.W))
    rs_real_idx := io.in_rs + 1
    rt_real_idx := io.in_rt + 1
    rd_real_idx := io.in_rt + 1


    io.out_rs_val := Mux(io.in_rs === 0.U(REG_INDEX_WIDTH.W), 0.U(XLEN.W), regfile(rs_real_idx))
    io.out_rt_val := Mux(io.in_rt === 0.U(REG_INDEX_WIDTH.W), 0.U(XLEN.W), regfile(rt_real_idx))

    when(io.in_wen) {
        when(io.in_rd > 0.U(REG_INDEX_WIDTH.W)) {
            regfile(rd_real_idx) := io.in_rd_val
        }
    }
}
