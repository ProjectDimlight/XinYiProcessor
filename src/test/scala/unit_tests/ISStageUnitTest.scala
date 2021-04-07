package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chiseltest._

import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class ISStageTestCaseBundle extends Bundle with XinYiConfig {
  val in        = Wire(Vec(fetch_num, Flipped(new Instruction)))
  val bc        = Wire(new BranchCacheOut)
  
  val alu_paths = Wire(Vec(alu_path_num, new PathInterface))
  val mdu_paths = Wire(Vec(mdu_path_num, new PathInterface))
  val lsu_paths = Wire(Vec(lsu_path_num, new PathInterface))
}

class ISStageUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with XinYiConfig {
  behavior of "ISStageUnitTest"

  it should "fail" in {
    test(new ISStage()) { c =>
      var in = Vec(fetch_num, new Instruction)
      var bc = new BranchCacheOut()
      var alu_paths = Vec(alu_path_num, new PathInterface)
      var mdu_paths = Vec(mdu_path_num, new PathInterface)
      var lsu_paths = Vec(lsu_path_num, new PathInterface)
      
      def exec() {
        c.io.in.poke(in)
        c.io.bc.poke(bc)
        for (i <- 0 until alu_path_num) {
          c.io.alu_paths(i).wt.     poke(alu_paths(i).wt)
          c.io.alu_paths(i).rd.     poke(alu_paths(i).rd)
          c.io.alu_paths(i).ready.  poke(alu_paths(i).ready)
        }
        for (i <- 0 until mdu_path_num) {
          c.io.mdu_paths(i).wt.     poke(mdu_paths(i).wt)
          c.io.mdu_paths(i).rd.     poke(mdu_paths(i).rd)
          c.io.mdu_paths(i).ready.  poke(mdu_paths(i).ready)
        }
        for (i <- 0 until lsu_path_num) {
          c.io.lsu_paths(i).wt.     poke(lsu_paths(i).wt)
          c.io.lsu_paths(i).rd.     poke(lsu_paths(i).rd)
          c.io.lsu_paths(i).ready.  poke(lsu_paths(i).ready)
        }
        
        for (i <- 0 until alu_path_num) {
          c.io.alu_paths(i).inst.expect(alu_paths(i).inst)
        }
        for (i <- 0 until mdu_path_num) {
          c.io.mdu_paths(i).inst.expect(mdu_paths(i).inst)
        }
        for (i <- 0 until lsu_path_num) {
          c.io.lsu_paths(i).inst.expect(lsu_paths(i).inst)
        }
      }

      // Test case 1: 2 ALU to empty paths
      in(0) := InstDecodedByPath(1, 1, 1, 2)
      in(1) := InstDecodedByPath(1, 3, 3, 4)

      bc.inst(0) := 0.U(32.W)
      bc.inst(1) := 0.U(32.W)
      bc.branch_cache_overwrite := false.B

      for (i <- 0 until alu_path_num) {
        alu_paths(i).wt    := DReg
        alu_paths(i).rd    := 0.U(5.W)
        alu_paths(i).ready := true.B
        alu_paths(i).inst  := NOPBubble()
      }

      for (i <- 0 until mdu_path_num) {
        mdu_paths(i).wt    := DReg
        mdu_paths(i).rd    := 0.U(5.W)
        mdu_paths(i).ready := true.B
        mdu_paths(i).inst  := NOPBubble()
      }

      for (i <- 0 until lsu_path_num) {
        lsu_paths(i).wt    := DReg
        lsu_paths(i).rd    := 0.U(5.W)
        lsu_paths(i).ready := true.B
        lsu_paths(i).inst  := NOPBubble()
      }

      alu_paths(0).inst := in(0)
      alu_paths(1).inst := in(1)

      exec()
    }
  }
}