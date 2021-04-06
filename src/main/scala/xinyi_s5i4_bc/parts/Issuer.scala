package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import wrap._
import ControlConst._

class Issuer(path_id: Int, path_num: Int) extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val inst      = Input(Vec(issue_num, new Instruction))
    val target    = Input(Vec(issue_num, UInt(path_w.W)))
    val ready     = Input(Vec(path_num, Bool()))
    val issue     = Output(Vec(issue_num, Bool()))
    val path      = Output(Vec(path_num, new Instruction))
  })

  val id = Wire(Vec(path_num, UInt(issue_num_w.W)))
  
  for (i <- 0 until issue_num)
    io.issue(i) := false.B

  // For each path
  val issued = Wire(Vec(path_num, Vec(issue_num, Bool())))

  for (j <- 0 until path_num) {
    io.path(j) := NOPBubble()
    id(j) := issue_num.U(issue_num_w.W)
    
    if (j != 0)
      issued(j) := issued(j-1)
    else
      issued(j) := Seq.fill(issue_num)(false.B)

    // If the j-th path is ready for a new instruction
    when (io.ready(j)) {
      // For each instruciton in the queue
      // pre-decide whether it is available
      val available      = Wire(Vec(issue_num, Bool()))
      val available_pass = Wire(Vec(issue_num, Bool()))
      for (i <- 0 until issue_num) {
        if (j != 0)
        {
          // It must have the correct type
          // And it's id must be greater than the last issued instruction with the same type
          // That is, it must havn not yet been issued
          available(i)      := (io.target(i) === path_id.U) & !issued(j-1)(i)
        }
        else
          available(i)      := (io.target(i) === path_id.U)
      }

      available_pass(0) := true.B
      for (i <- 1 until issue_num)
        available_pass(i) := available_pass(i-1) & !available(i-1)

      // find the FIRST fitting instruction (which hasn't been issued yet)
      for (i <- 0 until issue_num) {
        when (available(i) & available_pass(i)) {
          id(j) := i.U(4.W)
        }
      }

      when (id(j) < issue_num.U(issue_num_w.W)) {
        io.path(j) := io.inst(id(j))
        io.issue(id(j)) := true.B
        issued(j)(id(j)) := true.B
      }
    } // End when (ready(j))
  } // End for
}

object Issuer extends XinYiConfig {
  def apply(
    path_id  : Int,
    path_num : Int,
    inst     : Vec[Instruction],
    target   : Vec[UInt],
    ready    : Vec[Bool],
    issue    : Vec[Bool],
    path     : Vec[Instruction]
  ) = {
    val issuer = Module(new Issuer(path_id, path_num))
    issuer.io.inst   <> inst
    issuer.io.target <> target
    issuer.io.ready  <> ready
    issuer.io.issue  <> issue
    issuer.io.path   <> path
  }
}