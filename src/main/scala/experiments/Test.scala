package experiments

import chisel3._

trait TestConfig {
  val queue_size  = 4

  val fetch_num   = 2
  val issue_num   = 2
  val path_num    = 4
  val issue_cnt_w = 4
}

class ISIn extends Bundle {
  val pc   = Output(UInt(32.W))
  val inst = Output(UInt(32.W))
}

class Test extends Module with TestConfig {
  val io = IO(new Bundle{
    //val in  = Input(Vec(5, Bool()))
    val out = Output(Vec(3, UInt(32.W)))
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

  val used = Wire(UInt(3.W))
  used := 0.U(3.W)

  for (i <- 101 until 103) {
    for (j <- 0 until 3) {
      used(j) := 1.U(1.W)
      io.out(j) := i.U(32.W)
    }
  }
  // io.cnt := item
}