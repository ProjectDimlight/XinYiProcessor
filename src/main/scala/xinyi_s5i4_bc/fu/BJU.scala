package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import wrap._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.parts.ControlConst._

class BJU extends Module with XinYiConfig {
  val io = IO(new Bundle {
    val path = new BJUPathInterface
    val delay_slot_pending = Input(Bool())

    val branch_cache_out = new BranchCacheOut
    val pc_interface = Flipped(new PCInterface)
  })

  val branch = Wire(Bool())
  branch := io.path.in.inst.dec.next_pc =/= PC4 & 
    MuxLookup(
      io.path.inst.dec.branch_type,
        true.B,
      Array(
        BrEQ -> (io.path.data.rs1 === io.path.data.rs2),
        BrNE -> (io.path.data.rs1 =/= io.path.data.rs2),
        BrGE -> (io.path.data.rs1 >=  io.path.data.rs2),
        BrGT -> (io.path.data.rs1 >   io.path.data.rs2),
        BrLE -> (io.path.data.rs1 <=  io.path.data.rs2),
        BrLT -> (io.path.data.rs1 <   io.path.data.rs2)
      )
    )

  val target = Wire(UInt(lgc_addr_w.W))
  target := MuxLookup(
    io.path.inst.dec.next_pc,
      0.U(lgc_addr_w.W),
    Array(
      // Note that syscall, trap, and all other exceptions will not be handled here
      // They will be triggered and managed in FU
      Branch  -> ((io.path.inst.pc + 4.U(lgc_addr_w.W)).asSInt() + Cat(io.path.inst.inst(15, 0), 0.U(2.W)).asSInt()).asUInt(),
      Jump    -> Cat(io.path.inst.pc(31, 28), io.path.inst.inst(25, 0), 0.U(2.W))
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