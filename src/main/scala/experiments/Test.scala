package experiments

import chisel3._

trait TestConfig {
  val queue_len  = 3

  val fetch_num   = 2
  val issue_num   = 2
  val issue_cnt_w = 4

  val alu_path_num  = 4
  val mdu_path_num  = 4
  val lsu_path_num  = 4
  val queue_len_w   = 4
}

class ISIn extends Bundle {
  val pc   = Output(UInt(32.W))
  val inst = Output(UInt(32.W))
}

class Test extends Module with TestConfig {
  val io = IO(new Bundle{
    val in  = Input(Vec(queue_len, Bool()))
    val out = Output(Vec(alu_path_num, UInt(32.W)))
    //val out = Output(Vec(issue_num, new ISIn()))
    //val cnt = Output(UInt(32.W))
  })

  /*
  val queue = Reg(Vec(5, new ISIn))
  val head = RegInit(0.U(queue_len_w.W))
  val tail = RegInit(0.U(queue_len_w.W))

  for (i <- 0 until 5) {
    queue(i).pc := queue(i).pc + 1.U(32.W)
    queue(i).inst := queue(i).inst + 1.U(32.W)
  }
  head := head + 1.U(queue_len_w.W)

  for (i <- 0 until issue_num) {
    when (head + i.U(queue_len_w.W) < queue_size.U(queue_len_w.W)) {
      io.out(i) := queue(i.U(queue_len_w.W) + head)
    }
    .otherwise {
      io.out(i) := queue(head + (16 + i - queue_size).U(queue_len_w.W))
    }
  }
  */

  val queue = Wire(Vec(queue_len, UInt(32.W)))
  val path = Wire(Vec(queue_len, Bool()))
  for (i <- 0 until queue_len) {
    queue(i) := (i + 100).U(32.W)
    path(i) := ~(i & 1).B
  }

  val id = Wire(Vec(alu_path_num, UInt(4.W)))
  // for each path
  for (j <- 0 until alu_path_num) {
    id(j) := queue_len.U(4.W)

    // for each instruciton in the queue
    // pre-decide whether it is available
    val available      = Wire(Vec(queue_len, Bool()))
    val available_pass = Wire(Vec(queue_len, Bool()))
    for (i <- 0 until queue_len) {
      if (j != 0)
        // It's id must greater than the last issued instruction
        // That is, it must havn not yet been issued
        available(i)      := (path(i) & i.U(4.W) > id(j-1))
      else
        available(i)      :=  path(i)
    }

    available_pass(0) := false.B
    for (i <- 1 until queue_len)
      available_pass(i) := available_pass(i-1) | available(i-1)

    // find the FIRST available instruction (which hasn't been issued yet)
    for (i <- 0 until queue_len) {
      when (available(i) & ~available_pass(i)) {
        id(j) := i.U(4.W)
      }
    }

    when (id(j) < queue_len.U(4.W)) {
      io.out(j) := queue(id(j))
    }
    .otherwise {
      io.out(j) := 0.U(32.W)
    }
  }

  // io.cnt := item
}