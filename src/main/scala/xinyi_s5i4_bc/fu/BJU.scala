package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.parts.ControlConst._

trait BALConfig {
  val JPC           = 16.U(FU_CTRL_W.W)
  val BrGEPC        = 19.U(FU_CTRL_W.W)
  val BrLTPC        = 22.U(FU_CTRL_W.W)
}

trait BJUConfig extends BALConfig {
  // 0 for unconditioned jump
  val BrEQ          = 1.U(FU_CTRL_W.W)
  val BrNE          = 2.U(FU_CTRL_W.W)
  val BrGE          = 3.U(FU_CTRL_W.W)
  val BrGT          = 4.U(FU_CTRL_W.W)
  val BrLE          = 5.U(FU_CTRL_W.W)
  val BrLT          = 6.U(FU_CTRL_W.W)
}

class BJU extends Module with BJUConfig {
  val io = IO(new Bundle {
    val path = Input(new FUIn)
    val b_bc   = Input(UInt(LGC_ADDR_W.W))
    val imm_bc = Input(UInt(LGC_ADDR_W.W))
    val branch_next_pc = Input(UInt(NEXT_PC_W.W))

    val branch = Output(Bool())
    val target = Output(UInt(XLEN.W))
    val target_bc = Output(UInt(XLEN.W))
  })

  val a = io.path.a
  val b = io.path.b
  val branch = Wire(Bool())
  branch := io.branch_next_pc =/= PC4 &
    MuxLookupBi(
      io.path.fu_ctrl(2, 0),
      true.B,
      Array(
        BrEQ    -> !((a ^ b).orR()),
        BrNE    -> (a ^ b).orR(),
        BrGE    -> (!a(31)),
        BrGT    -> (!a(31) & a.orR()),
        BrLE    -> (a(31) | !a.orR()),
        BrLT    -> (a(31))
      )
    )

  val pc4 = io.path.pc

  val target    = Wire(UInt(LGC_ADDR_W.W))
  target       := Mux(io.branch_next_pc === PCReg, io.path.b, io.path.imm)
  val target_bc = Wire(UInt(LGC_ADDR_W.W))
  target_bc    := Mux(io.branch_next_pc === PCReg, io.b_bc, io.imm_bc)

  io.branch    := branch
  io.target    := target
  io.target_bc := target_bc
}