package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.parts.ControlConst._

trait BALConfig {
  val JPC           = 13.U(FU_CTRL_W.W)
  val BrGEPC        = 14.U(FU_CTRL_W.W)
  val BrLTPC        = 15.U(FU_CTRL_W.W)
}

trait BJUConfig extends BALConfig {
  // 0 for unconditioned jump
  val BrEQ          = 1.U(FU_CTRL_W.W)
  val BrNE          = 2.U(FU_CTRL_W.W)
  val BrGE          = 3.U(FU_CTRL_W.W)
  val BrGT          = 4.U(FU_CTRL_W.W)
  val BrLE          = 5.U(FU_CTRL_W.W)
  val BrLT          = 6.U(FU_CTRL_W.W)
  val BrERET        = 28.U(FU_CTRL_W.W)
}

class BJU extends Module with BJUConfig {
  val io = IO(new Bundle {
    val path = new FUIn
    val branch_next_pc = Input(UInt(NEXT_PC_W.W))
    val delay_slot_pending = Input(Bool())

    val branch_cache_out = new BranchCacheOut
    val pc_interface = Flipped(new PCInterface)
  })

  val branch = Wire(Bool())
  branch := io.branch_next_pc =/= PC4 &
    MuxLookup(
      io.path.fu_ctrl,
      true.B,
      Array(
        BrEQ    -> (io.path.a === io.path.b),
        BrNE    -> (io.path.a =/= io.path.b),
        BrGE    -> (io.path.a >=  io.path.b),
        BrGT    -> (io.path.a >   io.path.b),
        BrLE    -> (io.path.a <=  io.path.b),
        BrLT    -> (io.path.a <   io.path.b),
        BrGEPC  -> (io.path.a >=  io.path.b),
        BrLTPC  -> (io.path.a <   io.path.b)
      )
    )

  val pc4 = io.path.pc + 4.U(LGC_ADDR_W.W)

  val target = Wire(UInt(LGC_ADDR_W.W))
  target := MuxLookup(
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

  val bc = Module(new BranchCache)
  bc.io.in.branch := branch
  bc.io.in.delay_slot_pending := io.delay_slot_pending
  bc.io.in.target := target

  io.branch_cache_out := bc.io.out
  io.pc_interface.enable := branch
  io.pc_interface.target := bc.io.branch_cached_pc
}