package unit_tests

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._
import chisel3.util._
import utils._

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class IssueQueueUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers  {
  behavior of "IssueQueue Unit Test"

  it should "Test Case 1: Input 2, Immediately Issue" in {
    test(new IssueQueue) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.in(0).poke(a)
      device.io.in(1).poke(b)
      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.overwrite.poke(false.B)
      device.io.actual_issue_cnt.poke(0.U)
      
      device.clock.step(1)

      device.io.issue_cnt.expect(2.U)
      device.io.inst(0).expect(a)
      device.io.inst(1).expect(b)
    }
  }

  it should "Test Case 2: Input 2, Immediately Issue 1, then Issue 1" in {
    test(new IssueQueue) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.in(0).poke(a)
      device.io.in(1).poke(b)
      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.overwrite.poke(false.B)
      device.io.actual_issue_cnt.poke(0.U)
      
      // Cycle 1

      device.clock.step(1)

      device.io.issue_cnt.expect(2.U)
      device.io.inst(0).expect(a)
      device.io.inst(1).expect(b)

      // Issue 1 instruction
      device.io.in(0).poke(z)
      device.io.in(1).poke(z)
      device.io.actual_issue_cnt.poke(1.U)

      // Cycle 2
      device.clock.step(1)

      device.io.issue_cnt.expect(3.U)
      device.io.inst(0).expect(b)
      device.io.inst(1).expect(z)
    }
  }

  it should "Test Case 3: Input 8, Issue 0, queue full" in {
    test(new IssueQueue) { device =>
      //val a = InstDecodedLitByPath(1, 1, 1, 1)
      //val b = InstDecodedLitByPath(1, 2, 2, 2)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.overwrite.poke(false.B)
      device.io.actual_issue_cnt.poke(0.U)
      
      for (i <- 0 until 4) {
        val j = i * 2
        device.io.in(0).poke(InstDecodedLitByPath(1, j, j, j))
        device.io.in(1).poke(InstDecodedLitByPath(1, j+1, j+1, j+1))
        device.clock.step(1)
      }
      device.io.full.expect(true.B)
      device.io.in(0).poke(z)
      device.io.in(1).poke(z)

      // Cycle 5

      for (i <- 0 until 3) {
        val j = i * 2

        device.io.issue_cnt.expect(6.U)
        device.io.actual_issue_cnt.poke(2.U)
        device.io.full.expect(false.B)
        device.io.inst(0).expect(InstDecodedLitByPath(1, j, j, j))
        device.io.inst(1).expect(InstDecodedLitByPath(1, j+1, j+1, j+1))

        device.clock.step(1)
      }
      device.io.inst(0).expect(z)
      device.io.inst(1).expect(z)
    }
  }

  it should "Test Case 4: Start from head = tail = 4" in {
    test(new IssueQueue) { device =>
      //val a = InstDecodedLitByPath(1, 1, 1, 1)
      //val b = InstDecodedLitByPath(1, 2, 2, 2)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.overwrite.poke(false.B)
      

      device.io.actual_issue_cnt.poke(0.U)
      for (i <- 0 until 2) {
        val j = i * 2
        device.io.in(0).poke(InstDecodedLitByPath(1, j, j, j))
        device.io.in(1).poke(InstDecodedLitByPath(1, j+1, j+1, j+1))
        device.clock.step(1)

        device.io.issue_cnt.expect(2.U)
        device.io.inst(0).expect(InstDecodedLitByPath(1, j, j, j))
        device.io.inst(1).expect(InstDecodedLitByPath(1, j+1, j+1, j+1))
        device.io.actual_issue_cnt.poke(2.U)
      }

      // Cycle 3

      for (i <- 0 until 4) {
        val j = i * 2
        device.io.in(0).poke(InstDecodedLitByPath(1, j, j, j))
        device.io.in(1).poke(InstDecodedLitByPath(1, j+1, j+1, j+1))
        device.clock.step(1)

        device.io.issue_cnt.expect((if (i < 3) 2 + j else 6).U)
        device.io.inst(0).expect(InstDecodedLitByPath(1, 0, 0, 0))
        device.io.inst(1).expect(InstDecodedLitByPath(1, 1, 1, 1))
        device.io.actual_issue_cnt.poke(0.U)
      }
      device.io.in(0).poke(z)
      device.io.in(1).poke(z)


      // Cycle 7

      for (i <- 0 until 3) {
        val j = i * 2

        device.io.actual_issue_cnt.poke(2.U)
        device.io.issue_cnt.expect(6.U)
        device.io.inst(0).expect(InstDecodedLitByPath(1, j, j, j))
        device.io.inst(1).expect(InstDecodedLitByPath(1, j+1, j+1, j+1))

        device.clock.step(1)
      }
      device.io.inst(0).expect(z)
      device.io.inst(1).expect(z)
    }
  }

  it should "Test Case 5: Branch Cache" in {
    test(new IssueQueue) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 1)
      val b = InstDecodedLitByPath(1, 2, 2, 2)
      val c = InstDecodedLitByPath(1, 3, 3, 3)
      val d = InstDecodedLitByPath(1, 4, 4, 4)
      val e = InstDecodedLitByPath(1, 5, 5, 5)
      val f = InstDecodedLitByPath(1, 6, 6, 6)
      val x = InstDecodedLitByPath(1, 10, 10, 10)
      val y = InstDecodedLitByPath(1, 11, 11, 11)
      val z = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.bc.inst(0).poke(a)
      device.io.bc.inst(1).poke(b)
      device.io.bc.overwrite.poke(true.B)
      
      device.io.in(0).poke(x)
      device.io.in(1).poke(y)
      device.io.actual_issue_cnt.poke(0.U)

      device.clock.step(1)
      
      device.io.inst(0).expect(a)
      device.io.inst(1).expect(b)

      // Cycle 2
      device.io.bc.inst(0).poke(c)
      device.io.bc.inst(1).poke(d)
      device.io.bc.overwrite.poke(true.B)
      
      device.io.in(0).poke(x)
      device.io.in(1).poke(y)
      device.io.actual_issue_cnt.poke(1.U)

      device.clock.step(1)
      
      device.io.inst(0).expect(b)
      device.io.inst(1).expect(c)

      // Cycle 3
      device.io.bc.inst(0).poke(z)
      device.io.bc.inst(1).poke(z)
      device.io.bc.overwrite.poke(false.B)
      
      device.io.in(0).poke(e)
      device.io.in(1).poke(f)
      device.io.actual_issue_cnt.poke(2.U)

      device.clock.step(1)
      
      device.io.inst(0).expect(d)
      device.io.inst(1).expect(e)

    }
  }
}