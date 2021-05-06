package xinyi_s5i4_bc.fu

import chisel3._
import config.config._
import xinyi_s5i4_bc.parts.ControlConst._
import EXCCodeConfig._

class FUIn extends Bundle {
  val write_target  = UInt(WRITE_TARGET_W.W) // write target
  val rd            = UInt(REG_ID_W.W) // destination register
  val fu_ctrl       = UInt(FU_CTRL_W.W) // control param
  // operation params
  val a             = UInt(XLEN.W)
  val b             = UInt(XLEN.W)
  val imm           = UInt(XLEN.W)
  // meta
  val pc            = UInt(LGC_ADDR_W.W)
  val order         = UInt(ISSUE_NUM_W.W)
  // delay slot
  val is_delay_slot = Bool() 
}

class Forwarding extends Bundle {
  // target
  val write_target  = UInt(WRITE_TARGET_W.W)
  val rd            = UInt(REG_ID_W.W)
  // data
  val data          = UInt(XLEN.W)
  val hi            = UInt(XLEN.W)
  // ready
  val ready         = Bool()
  val order         = UInt(ISSUE_NUM_W.W)
}

class FUOut extends Forwarding {
  val pc            = UInt(XLEN.W)
  val exc_code      = UInt(EXC_CODE_W.W)
  val exception     = Bool()
  // delay slot
  val is_delay_slot = Bool()
}

class FUIO extends Bundle {
  val in  = Input(new FUIn)
  val out = Output(new FUOut)
}

object FUOutBubble {
  def apply() = {
    val init = Wire(new FUOut)
    init.write_target  := DXXX
    init.rd            := 0.U
    init.data          := 0.U
    init.hi            := 0.U
    init.ready         := true.B
    init.order         := ISSUE_NUM.U
    init.pc            := 0.U
    init.exc_code      := NO_EXCEPTION
    init.exception     := false.B
    init.is_delay_slot := false.B
    init
  }
}