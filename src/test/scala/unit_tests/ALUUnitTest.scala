//package unit_tests
//
//import chisel3._
//import chiseltest._
//import chisel3.experimental.BundleLiterals._
//import chisel3.util._
//import utils._
//
//import org.scalatest._
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers
//
//import config.config._
//import xinyi_s5i4_bc.stages._
//import xinyi_s5i4_bc.parts._
//import xinyi_s5i4_bc.fu._
//import xinyi_s5i4_bc.caches._
//
//import ControlConst._
//
//class ALUUnitTest extends AnyFlatSpec with ChiselScalatestTester with Matchers with ALUConfig with BJUConfig {
//  behavior of "ALU Unit Test"
//
//  def ToUInt(x: Int) = {
//    (x.asInstanceOf[Long] & 0x00000000ffffffffL).U
//  }
//
//  def ConstructInput(op: UInt, a: Int, b: Int, imm: Int, pc: Int, order: Int): FUIn = {
//    val fu = new FUIn
//    fu.Lit(
//      _.write_target -> DXXX,
//      _.rd           -> 0.U,
//      _.fu_ctrl      -> op,
//      _.a            -> (a.asInstanceOf[Long] & 0x00000000ffffffffL).U,
//      _.b            -> (b.asInstanceOf[Long] & 0x00000000ffffffffL).U,
//      _.imm          -> (imm.asInstanceOf[Long] & 0x00000000ffffffffL).U,
//      _.pc           -> pc.U,
//      _.order        -> order.U,
//      _.is_delay_slot-> false.B
//    )
//  }
//
//  it should "Test Case 1: Div" in {
//    test(new ALU()) { device =>
//      val div = ConstructInput(ALU_DIV, 0xfda5ea8a, 0xfac1873c, 0, 0, 0)
//
//      device.io.in.poke(div)
//
//      device.io.out.data.expect(0.U)
//      device.io.out.hi.expect(ToUInt(0xfda5ea8a))
//
//      val div1 = ConstructInput(ALU_DIV, -10,  3, 0, 0, 0)  // -3 -1
//      val div2 = ConstructInput(ALU_DIV,  10, -3, 0, 0, 0)  // -3  1
//      val div3 = ConstructInput(ALU_DIV, -10, -3, 0, 0, 0)  //  3 -1
//
//      device.io.in.poke(div1)
//      device.io.out.data.expect(ToUInt(-3))
//      device.io.out.hi.expect(ToUInt(-1))
//
//      device.io.in.poke(div2)
//      device.io.out.data.expect(ToUInt(-3))
//      device.io.out.hi.expect(ToUInt(1))
//
//      device.io.in.poke(div3)
//      device.io.out.data.expect(ToUInt(3))
//      device.io.out.hi.expect(ToUInt(-1))
//    }
//  }
//
//  it should "Test Case 2: Mult" in {
//    test(new ALU()) { device =>
//      val mul1 = ConstructInput(ALU_MUL, -2,  3, 0, 0, 0)
//      val mul2 = ConstructInput(ALU_MUL, -2, -3, 0, 0, 0)
//      val mul3 = ConstructInput(ALU_MUL, -1, -1, 0, 0, 0)
//      val mul4 = ConstructInput(ALU_MULU, -1, -1, 0, 0, 0)
//
//      device.io.in.poke(mul1)
//      device.io.out.data.expect(ToUInt(-6))
//      device.io.out.hi.expect(ToUInt(-1))
//
//      device.io.in.poke(mul2)
//      device.io.out.data.expect(ToUInt(6))
//      device.io.out.hi.expect(ToUInt(0))
//
//      device.io.in.poke(mul3)
//      device.io.out.data.expect(ToUInt(1))
//      device.io.out.hi.expect(ToUInt(0))
//
//      device.io.in.poke(mul4)
//      device.io.out.data.expect(ToUInt(1))
//      device.io.out.hi.expect(ToUInt(-2))
//    }
//  }
//}