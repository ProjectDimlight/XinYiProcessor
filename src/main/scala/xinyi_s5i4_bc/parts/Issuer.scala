package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._

import xinyi_s5i4_bc.stages._
import ControlConst._
import config.config._

class Issuer(path_id: Int, path_num: Int) extends Module  {
  val io = IO(new Bundle{
    val inst      = Input(Vec(ISSUE_NUM, new Instruction))
    val target    = Input(Vec(ISSUE_NUM, UInt(PATH_W.W)))
    val ready     = Input(Vec(path_num, Bool()))
    val issue     = Output(Vec(ISSUE_NUM, Bool()))
    val path      = Output(Vec(path_num, new Instruction))
    val id        = Output(Vec(path_num, UInt(ISSUE_NUM_W.W)))
  })

  val id = Wire(Vec(path_num, UInt(ISSUE_NUM_W.W)))
  io.id := id
  
  for (i <- 0 until ISSUE_NUM)
    io.issue(i) := false.B

  // For each path
  val issued = Wire(Vec(path_num, Vec(ISSUE_NUM, Bool())))

  for (j <- 0 until path_num) {
    io.path(j) := NOPBubble()
    id(j) := ISSUE_NUM.U(ISSUE_NUM_W.W)
    
    if (j != 0)
      issued(j) := issued(j-1)
    else
      issued(j) := Seq.fill(ISSUE_NUM)(false.B)

    // If the j-th path is ready for a new instruction
    when (io.ready(j)) {
      // For each instruciton in the queue
      // pre-decide whether it is available
      val available      = Wire(Vec(ISSUE_NUM, Bool()))
      val available_pass = Wire(Vec(ISSUE_NUM, Bool()))
      for (i <- 0 until ISSUE_NUM) {
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
      for (i <- 1 until ISSUE_NUM)
        available_pass(i) := available_pass(i-1) & !available(i-1)

      // find the FIRST fitting instruction (which hasn't been issued yet)
      for (i <- 0 until ISSUE_NUM) {
        when (available(i) & available_pass(i)) {
          id(j) := i.U(4.W)
        }
      }

      when (id(j) < ISSUE_NUM.U(ISSUE_NUM_W.W)) {
        io.path(j) := io.inst(id(j))
        io.issue(id(j)) := true.B
        issued(j)(id(j)) := true.B
      }
    } // End when (ready(j))
  } // End for
}

object Issuer {
  def apply(
    path_id  : Int,
    path_num : Int,
    inst     : Vec[Instruction],
    target   : Vec[UInt],
    issue    : Vec[Bool],
    path     : Vec[PathInterface]
  ) = {
    val issuer = Module(new Issuer(path_id, path_num))
    issuer.io.inst   <> inst
    issuer.io.target <> target
    issuer.io.issue  <> issue
    for (i <- 0 until path_num) {
      issuer.io.ready(i)  <> path(i).out.ready
      issuer.io.path(i)   <> path(i).in.inst
      issuer.io.id(i)     <> path(i).in.id
    }
  }
}