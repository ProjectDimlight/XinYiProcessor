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

class BJUUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers  {
  behavior of "BJU Unit Test"

  it should "Test Case 1: Branch" in {
    test(new BJU()) { device =>
      val a = InstDecodedLitByPath(5, 1, 1, -4)    // Branch
      val z = InstDecodedLitByPath(0, 0, 0, 0)    // Zero

      val path = new PathIn
      val data = new PathData
      val delay_slot_pending = Input(Bool())

      val branch_cache_out = new BranchCacheOut
      val pc_interface = new PCInterface

      device.io.path.in.inst.poke(a)
      device.io.path.data.poke((new PathData).Lit(_.rs1 -> 0.U, _.rs2 -> 0.U))
      device.io.delay_slot_pending.poke(false.B)

      // Replace 1
      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(true.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(true.B)
      device.io.pc_interface.target.expect(20.U)

      device.clock.step(1)

      // Replace 2
      device.io.path.in.inst.poke(z)

      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(false.B)

      device.clock.step(1)

      // End
      device.io.path.in.inst.poke(z)

      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(false.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)
    }
  }


  it should "Test Case 2: Branch with Delay Slot" in {
    test(new BJU()) { device =>
      val a = InstDecodedLitByPath(5, 1, 1, 3)    // Branch
      val z = InstDecodedLitByPath(0, 0, 0, 0)    // Zero

      val path = new PathIn
      val data = new PathData
      val delay_slot_pending = Input(Bool())

      val branch_cache_out = new BranchCacheOut
      val pc_interface = new PCInterface

      device.io.path.in.inst.poke(a)
      device.io.path.data.poke((new PathData).Lit(_.rs1 -> 0.U, _.rs2 -> 0.U))
      device.io.delay_slot_pending.poke(true.B)

      // Replace 1
      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(true.B)
      device.io.branch_cache_out.keep_delay_slot.expect(true.B)

      device.io.pc_interface.enable.expect(true.B)
      device.io.pc_interface.target.expect(48.U)

      device.clock.step(1)

      // Replace 2
      device.io.path.in.inst.poke(z)

      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(true.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)

      device.io.pc_interface.enable.expect(false.B)

      device.clock.step(1)

      // End
      device.io.path.in.inst.poke(z)

      device.io.branch_cache_out.inst(0).expect(z)
      device.io.branch_cache_out.overwrite.expect(false.B)
      device.io.branch_cache_out.flush.expect(false.B)
      device.io.branch_cache_out.keep_delay_slot.expect(false.B)
    }
  }
}