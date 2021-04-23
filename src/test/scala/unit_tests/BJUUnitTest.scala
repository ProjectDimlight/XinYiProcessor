package unit_tests

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._
import chisel3.util._

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.fu._
import xinyi_s5i4_bc.caches._

import ControlConst._

class BJUUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with ALUConfig with BJUConfig {
  behavior of "BJU Unit Test"

  def ConstructInput(op: UInt, a: Int, b: Int, imm: Int, pc: Int, order: Int): FUIn = {
    val fu = new FUIn
    fu.Lit(
      _.write_target -> DXXX, 
      _.rd           -> 0.U,
      _.fu_ctrl      -> op,
      _.a            -> a.U,
      _.b            -> b.U,
      _.imm          -> (imm.asInstanceOf[Long] & 0x00000000ffffffffL).U,
      _.pc           -> pc.U,
      _.order        -> order.U,
      _.is_delay_slot-> false.B
    )
  }

  it should "Test Case 1: Branch" in {
    test(new BJU()) { device =>
      //val a = InstDecodedLitByPath(5, 1, 1, -4)   // Branch
      val nop = InstDecodedLitByPath(0, 0, 0, 0)    // Zero

      val a = ConstructInput(BrEQ, 1, 1, -16, 0x20, 0)
      val z = ConstructInput(ALU_ADD, 0, 0,  0, 0x24, 0)

      device.io.path.poke(a)
      device.io.branch_next_pc.poke(Branch)
      device.io.delay_slot_pending.poke(false.B)

      // Replace 1
      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(true.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(true.B)
      device.io.pc_interface.target.expect(20.U)

      device.clock.step(1)

      // Replace 2
      device.io.path.poke(z)
      device.io.branch_next_pc.poke(PC4)

      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(false.B)

      device.clock.step(1)

      // End
      device.io.path.poke(z)

      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(false.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)
    }
  }

  it should "Test Case 2: Branch with Delay Slot" in {
    test(new BJU()) { device =>
      //val a = InstDecodedLitByPath(5, 1, 1, 3)    // Branch
      val nop = InstDecodedLitByPath(0, 0, 0, 0)    // Zero

      val a = ConstructInput(BrEQ, 1, 1, 12, 0x20, 0)
      val z = ConstructInput(ALU_ADD, 0, 0,  0, 0x24, 0)

      device.io.path.poke(a)
      device.io.branch_next_pc.poke(Branch)
      device.io.delay_slot_pending.poke(true.B)

      // Replace 1
      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(true.B)
      device.io.branch_cache_out.keep_delay_slot.expect(true.B)

      device.io.pc_interface.enable.expect(true.B)
      device.io.pc_interface.target.expect(48.U)

      device.clock.step(1)

      // Replace 2
      device.io.path.poke(z)
      device.io.branch_next_pc.poke(PC4)

      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(false.B)

      device.clock.step(1)

      // End
      device.io.path.poke(z)

      device.io.branch_cache_out.inst(0).expect(nop)
      device.io.branch_cache_out.overwrite.expect(false.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)
    }
  }
}