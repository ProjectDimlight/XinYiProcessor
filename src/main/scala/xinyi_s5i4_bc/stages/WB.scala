package xinyi_s5i4_bc.stages


import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._


class WBIO extends Bundle {
    val write_target = Input(UInt(WRITE_TARGET_W.W))
    val data = Input(UInt(XLEN.W))
    val rd = Input(UInt(XLEN.W))
    val id = Input(UInt(ISSUE_NUM_W.W))
    val ready = Input(Bool())
    //>>>>>
    val pc = Input(UInt(XLEN.W))
}


class WB {

}
