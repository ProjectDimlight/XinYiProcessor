package xinyi_s5i4_bc.fu

import chisel3._
import config.config._

import config.config._

class RegReadInterface extends Bundle  {
  val rs1   = Input(UInt(REG_ID_W.W))
  val rs2   = Input(UInt(REG_ID_W.W))
  val data1 = Output(UInt(DATA_W.W))
  val data2 = Output(UInt(DATA_W.W))
}

class RegWriteInterface extends Bundle  {
  val rd    = Input(UInt(REG_ID_W.W))
  val data  = Input(UInt(DATA_W.W))
}

/**
 * @Module Regs
 */
class Regs extends Module {
    val io = IO(new Bundle {
        val in_wen = Input(Bool())
        val in_rd = Input(UInt(REG_ID_W.W))
        val in_rs = Input(UInt(REG_ID_W.W))
        val in_rt = Input(UInt(REG_ID_W.W))
        val in_rd_val = Input(UInt(XLEN.W))
        val out_rs_val = Output(UInt(XLEN.W))
        val out_rt_val = Output(UInt(XLEN.W))
    })

    // register file
    val regfile = Reg(Vec(31, UInt(XLEN.W)))

    val rs_real_idx = Wire(UInt(REG_ID_W.W))
    val rt_real_idx = Wire(UInt(REG_ID_W.W))
    val rd_real_idx = Wire(UInt(REG_ID_W.W))
    rs_real_idx := io.in_rs + 1.U
    rt_real_idx := io.in_rt + 1.U
    rd_real_idx := io.in_rt + 1.U


    io.out_rs_val := Mux(io.in_rs === 0.U(REG_ID_W.W), 0.U(XLEN.W), regfile(rs_real_idx))
    io.out_rt_val := Mux(io.in_rt === 0.U(REG_ID_W.W), 0.U(XLEN.W), regfile(rt_real_idx))

    when(io.in_wen) {
        when(io.in_rd > 0.U(REG_ID_W.W)) {
            regfile(rd_real_idx) := io.in_rd_val
        }
    }
}
