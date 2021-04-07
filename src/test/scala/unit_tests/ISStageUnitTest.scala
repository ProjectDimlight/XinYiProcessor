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

class ISStageUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with XinYiConfig {
  behavior of "ISStage Unit Test"

  it should "Test Case 1" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)

      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until alu_path_num) {
        device.io.alu_paths(i).wt.     poke(DReg)
        device.io.alu_paths(i).rd.     poke(0.U(5.W))
        device.io.alu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until mdu_path_num) {
        device.io.mdu_paths(i).wt.     poke(DReg)
        device.io.mdu_paths(i).rd.     poke(0.U(5.W))
        device.io.mdu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until lsu_path_num) {
        device.io.lsu_paths(i).wt.     poke(DReg)
        device.io.lsu_paths(i).rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).ready.  poke(true.B)
      }

      device.io.alu_paths(0).inst.expect(a)
      device.io.alu_paths(1).inst.expect(b)
      device.io.actual_issue_cnt.expect(2.U)
    }
  }

  it should "Test Case 2" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(2, 1, 1, 2)
      val b = InstDecodedLitByPath(2, 3, 3, 4)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until alu_path_num) {
        device.io.alu_paths(i).wt.     poke(DReg)
        device.io.alu_paths(i).rd.     poke(0.U(5.W))
        device.io.alu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until mdu_path_num) {
        device.io.mdu_paths(i).wt.     poke(DReg)
        device.io.mdu_paths(i).rd.     poke(0.U(5.W))
        device.io.mdu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until lsu_path_num) {
        device.io.lsu_paths(i).wt.     poke(DReg)
        device.io.lsu_paths(i).rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).ready.  poke(true.B)
      }

      device.io.alu_paths(0).inst.expect(c)
      device.io.alu_paths(1).inst.expect(c)
      device.io.mdu_paths(0).inst.expect(a)
      device.io.lsu_paths(0).inst.expect(c)
      device.io.actual_issue_cnt.expect(1.U)
    }
  }

  it should "Test Case 3" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(2, 3, 3, 4)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until alu_path_num) {
        device.io.alu_paths(i).wt.     poke(DReg)
        device.io.alu_paths(i).rd.     poke(0.U(5.W))
        device.io.alu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until mdu_path_num) {
        device.io.mdu_paths(i).wt.     poke(DReg)
        device.io.mdu_paths(i).rd.     poke(0.U(5.W))
        device.io.mdu_paths(i).ready.  poke(true.B)
      }
      for (i <- 0 until lsu_path_num) {
        device.io.lsu_paths(i).wt.     poke(DReg)
        device.io.lsu_paths(i).rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).ready.  poke(true.B)
      }

      device.io.alu_paths(0).inst.expect(a)
      device.io.alu_paths(1).inst.expect(c)
      device.io.mdu_paths(0).inst.expect(b)
      device.io.lsu_paths(0).inst.expect(c)
      device.io.actual_issue_cnt.expect(2.U)
    }
  }
}