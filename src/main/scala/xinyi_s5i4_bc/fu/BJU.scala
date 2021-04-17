package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.parts.ControlConst._

trait BALConfig {
  val BrGEPC        = 14.U(FU_CTRL_W.W)
  val BrLTPC        = 15.U(FU_CTRL_W.W)
}

trait BJUConfig extends BALConfig {
  val BrXXX         = 0.U(FU_CTRL_W.W)
  val BrEQ          = 1.U(FU_CTRL_W.W)
  val BrNE          = 2.U(FU_CTRL_W.W)
  val BrGE          = 3.U(FU_CTRL_W.W)
  val BrGT          = 4.U(FU_CTRL_W.W)
  val BrLE          = 5.U(FU_CTRL_W.W)
  val BrLT          = 6.U(FU_CTRL_W.W)
}

class BJU extends Module {
  val io = IO(new Bundle {
    val path = new FUIn
    val delay_slot_pending = Input(Bool())

    val branch_cache_out = new BranchCacheOut
    val pc_interface = Flipped(new PCInterface)
  })

  val branch = Wire(Bool())
  branch := io.path.in.inst.dec.next_pc =/= PC4 &
    MuxLookup(
      io..branch_type,
      true.B,
      Array(
        BrEQ -> (io.path.data.rs1 === io.path.data.rs2),
        BrNE -> (io.path.data.rs1 =/= io.path.data.rs2),
        BrGE -> (io.path.data.rs1 >= io.path.data.rs2),
        BrGT -> (io.path.data.rs1 > io.path.data.rs2),
        BrLE -> (io.path.data.rs1 <= io.path.data.rs2),
        BrLT -> (io.path.data.rs1 < io.path.data.rs2)
      )
    )

  val target = Wire(UInt(LGC_ADDR_W.W))
  target := MuxLookup(
    io.path.in.inst.dec.next_pc,
    0.U(LGC_ADDR_W.W),
    Array(
      // Note that syscall, trap, and all other exceptions will not be handled here
      // They will be triggered and managed in FU
      Branch -> ((io.path.in.inst.pc + 4.U(LGC_ADDR_W.W)).asSInt() + Cat(io.path.in.inst.inst(15, 0), 0.U(2.W)).asSInt()).asUInt(),
      Jump -> Cat(io.path.in.inst.pc(31, 28), io.path.in.inst.inst(25, 0), 0.U(2.W))
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