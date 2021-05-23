package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.parts.ControlConst._

trait BALConfig {
  val JPC           = 24.U(FU_CTRL_W.W)
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
    val branch_next_pc = Input(UInt(NEXT_PC_W.W))

    val branch = Output(Bool())
    val target = Output(UInt(XLEN.W))
  })

  val a = io.path.a.asSInt()
  val b = io.path.b.asSInt()
  val branch = Wire(Bool())
  branch := io.branch_next_pc =/= PC4 &
    MuxLookupBi(
      io.path.fu_ctrl(2, 0),
      true.B,
      Array(
        BrEQ    -> (a === b),
        BrNE    -> (a =/= b),
        BrGE    -> (a >=  0.S),
        BrGT    -> (a >   0.S),
        BrLE    -> (a <=  0.S),
        BrLT    -> (a <   0.S)
      )
    )

  val pc4 = io.path.pc

  val target = Wire(UInt(LGC_ADDR_W.W))
  target := MuxLookupBi(
    io.branch_next_pc,
    0.U(LGC_ADDR_W.W),
    Array(
      // Note that syscall, trap, and all other exceptions will not be handled here
      // They will be triggered and managed in FU
      Branch -> (pc4 + io.path.imm),
      Jump   -> Cat(pc4(31, 28), io.path.imm(27, 0)),
      PCReg  -> io.path.a
    )
  )

  io.branch := branch
  io.target := target
}