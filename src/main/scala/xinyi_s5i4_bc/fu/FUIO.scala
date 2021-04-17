package xinyi_s5i4_bc.fu

import chisel3._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._

class FUIn extends Bundle {
  // target
  val write_target = Input(Input(UInt(WRITE_TARGET_W.W)))
  val rd = Input(UInt(REG_ID_W.W))

  // control param
  val fu_ctrl = Input(UInt(FU_CTRL_W.W))

  // operation params
  val a = Input(UInt(XLEN.W))
  val b = Input(UInt(XLEN.W))
  val imm = Input(UInt(XLEN.W))

  // meta
  val pc = Input(UInt(LGC_ADDR_W.W))
  val order = Input(UInt(ISSUE_NUM_W.W))
}


class Forwarding extends Bundle {
  // target
  val write_target = Output(UInt(WRITE_TARGET_W.W))
  val rd = Output(UInt(REG_ID_W.W))

  // data
  val data = Output(UInt(XLEN.W))
  val hi = Output(UInt(XLEN.W))

  // ready
  val ready = Output(Bool())

  val order = Output(UInt(ISSUE_NUM_W.W))
}

class FUOut extends Forwarding {
  val pc = Output(UInt(XLEN.W))
  val exception = Output(Bool())
}