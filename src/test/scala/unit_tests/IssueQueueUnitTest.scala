package unit_tests

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._
import chisel3.util._

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import wrap._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class IssueQueueUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with XinYiConfig {
  behavior of "IssueQueue Unit Test"

  it should "Test Case 1: Input 2, Immediately Issue" in {
    test(new IssueQueue()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.in(0).poke(a)
      device.io.in(1).poke(b)
      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.branch_cache_overwrite.poke(false.B)
      device.io.actual_issue_cnt.poke(0.U)
      
      device.clock.step(1)

      device.io.size.expect(2.U)

      device.io.issue_cnt.expect(2.U)
      device.io.inst(0).expect(a)
      device.io.inst(1).expect(b)
    }
  }
}