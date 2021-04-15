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
import xinyi_s5i4_bc.caches._
import ControlConst._

class ISStageUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers  {
  behavior of "ISStage Unit Test"

  it should "Test Case 1: 2 ALU" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)

      device.io.issue_cnt.poke(2.U)
      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until ALU_PATH_NUM) {
        device.io.alu_paths(i).out.wt.     poke(DReg)
        device.io.alu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.alu_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until BJU_PATH_NUM) {
        device.io.bju_paths(i).out.wt.     poke(DReg)
        device.io.bju_paths(i).out.rd.     poke(0.U(5.W))
        device.io.bju_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until LSU_PATH_NUM) {
        device.io.lsu_paths(i).out.wt.     poke(DReg)
        device.io.lsu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).out.ready.  poke(true.B)
      }

      device.io.alu_paths(0).in.inst.expect(a)
      device.io.alu_paths(0).in.id.expect(0.U)
      device.io.alu_paths(1).in.inst.expect(b)
      device.io.alu_paths(1).in.id.expect(1.U)
      device.io.lsu_paths(0).in.id.expect(2.U)
      device.io.actual_issue_cnt.expect(2.U)
    }
  }

  it should "Test Case 2: 2 BJU" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(2, 1, 1, 2)
      val b = InstDecodedLitByPath(2, 3, 3, 4)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.issue_cnt.poke(2.U)
      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until ALU_PATH_NUM) {
        device.io.alu_paths(i).out.wt.     poke(DReg)
        device.io.alu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.alu_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until BJU_PATH_NUM) {
        device.io.bju_paths(i).out.wt.     poke(DReg)
        device.io.bju_paths(i).out.rd.     poke(0.U(5.W))
        device.io.bju_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until LSU_PATH_NUM) {
        device.io.lsu_paths(i).out.wt.     poke(DReg)
        device.io.lsu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).out.ready.  poke(true.B)
      }

      device.io.alu_paths(0).in.inst.expect(c)
      device.io.alu_paths(0).in.id.expect(2.U)
      device.io.alu_paths(1).in.inst.expect(c)
      device.io.alu_paths(0).in.id.expect(2.U)
      device.io.lsu_paths(0).in.inst.expect(c)
      device.io.actual_issue_cnt.expect(1.U)
    }
  }

  it should "Test Case 3: 1ALU 1BJU" in {
    // As there are no BJU paths, these instructions cannot issue at all
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(2, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.issue_cnt.poke(2.U)
      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until ALU_PATH_NUM) {
        device.io.alu_paths(i).out.wt.     poke(DReg)
        device.io.alu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.alu_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until BJU_PATH_NUM) {
        device.io.bju_paths(i).out.wt.     poke(DReg)
        device.io.bju_paths(i).out.rd.     poke(0.U(5.W))
        device.io.bju_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until LSU_PATH_NUM) {
        device.io.lsu_paths(i).out.wt.     poke(DReg)
        device.io.lsu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).out.ready.  poke(true.B)
      }

      device.io.alu_paths(0).in.inst.expect(c)
      device.io.alu_paths(0).in.id.expect(1.U)
      device.io.alu_paths(1).in.inst.expect(c)
      device.io.alu_paths(1).in.id.expect(2.U)
      device.io.lsu_paths(0).in.inst.expect(c)
      device.io.actual_issue_cnt.expect(0.U)
    }
  }

  it should "Test Case 4: 2ALU with RAW" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(2, 2, 2, 3)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.issue_cnt.poke(2.U)
      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until ALU_PATH_NUM) {
        device.io.alu_paths(i).out.wt.     poke(DReg)
        device.io.alu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.alu_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until BJU_PATH_NUM) {
        device.io.bju_paths(i).out.wt.     poke(DReg)
        device.io.bju_paths(i).out.rd.     poke(0.U(5.W))
        device.io.bju_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until LSU_PATH_NUM) {
        device.io.lsu_paths(i).out.wt.     poke(DReg)
        device.io.lsu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).out.ready.  poke(true.B)
      }

      device.io.alu_paths(0).in.inst.expect(a)
      device.io.alu_paths(1).in.inst.expect(c)
      device.io.lsu_paths(0).in.inst.expect(c)
      device.io.actual_issue_cnt.expect(1.U)
    }
  }

  it should "Test Case 5: 2 ALU, but only issue 1" in {
    test(new ISStage()) { device =>
      val a = InstDecodedLitByPath(1, 1, 1, 2)
      val b = InstDecodedLitByPath(1, 3, 3, 4)
      val c = InstDecodedLitByPath(0, 0, 0, 0)

      device.io.issue_cnt.poke(1.U)
      device.io.inst(0).poke(a)
      device.io.inst(1).poke(b)

      for (i <- 0 until ALU_PATH_NUM) {
        device.io.alu_paths(i).out.wt.     poke(DReg)
        device.io.alu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.alu_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until BJU_PATH_NUM) {
        device.io.bju_paths(i).out.wt.     poke(DReg)
        device.io.bju_paths(i).out.rd.     poke(0.U(5.W))
        device.io.bju_paths(i).out.ready.  poke(true.B)
      }
      for (i <- 0 until LSU_PATH_NUM) {
        device.io.lsu_paths(i).out.wt.     poke(DReg)
        device.io.lsu_paths(i).out.rd.     poke(0.U(5.W))
        device.io.lsu_paths(i).out.ready.  poke(true.B)
      }

      device.io.alu_paths(0).in.inst.expect(a)
      device.io.alu_paths(1).in.inst.expect(c)
      device.io.actual_issue_cnt.expect(1.U)
    }
  }
}