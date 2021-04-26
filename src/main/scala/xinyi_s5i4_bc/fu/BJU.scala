package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
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
    val path = Input(new FUIn)
    val branch_next_pc = Input(UInt(NEXT_PC_W.W))
    val delay_slot_pending = Input(Bool())
    val stall_frontend = Input(Bool())

    val branch_cache_out = new BranchCacheOut
    val pc_interface = Output(new PCInterface)
  })

  val a = io.path.a.asSInt()
  val b = io.path.b.asSInt()
  val branch = Wire(Bool())
  branch := io.branch_next_pc =/= PC4 &
    MuxLookupBi(
      io.path.fu_ctrl,
      true.B,
      Array(
        BrEQ    -> (a === b),
        BrNE    -> (a =/= b),
        BrGE    -> (a >=  b),
        BrGT    -> (a >   b),
        BrLE    -> (a <=  b),
        BrLT    -> (a <   b),
        BrGEPC  -> (a >=  b),
        BrLTPC  -> (a <   b)
      )
    )

  val pc4 = io.path.pc + 4.U(LGC_ADDR_W.W)

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

  val bc = Module(new BranchCache)
  bc.io.in.branch := branch
  bc.io.in.delay_slot_pending := io.delay_slot_pending
  bc.io.in.target := target
  bc.io.stall_frontend := io.stall_frontend

  io.branch_cache_out := bc.io.out
  io.pc_interface.enable := branch
  io.pc_interface.target := bc.io.branch_cached_pc
}