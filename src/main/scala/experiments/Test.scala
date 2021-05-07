package experiments

import utils._
import chisel3._
import chisel3.util._
import utils._
import chisel3.experimental._

trait TestConfig {
  val QUEUE_LEN  = 3

  val FETCH_NUM   = 2
  val ISSUE_NUM   = 2
  val issue_cnt_w = 4

  val ALU_PATH_NUM  = 4
  val MDU_PATH_NUM  = 4
  val LSU_PATH_NUM  = 4
  val QUEUE_LEN_W   = 4
}

class Test extends Module {
  val io = IO(new Bundle{
    val in1 = Input(UInt(8.W))
    val in2 = Input(UInt(8.W))
    val in3 = Input(UInt(8.W))
    val in4 = Input(UInt(8.W))
    val in5 = Input(UInt(8.W))
    val path = Input(UInt(5.W))
    val out = Output(UInt(8.W))
  })

  io.out := Mux1H(
    io.path,
    Seq(
      io.in1,
      io.in2,
      io.in3,
      io.in4,
      io.in5,
    )
  )
}

/*
class Test extends Module {
  val io = IO(new Bundle{
    val in1 = Input(UInt(8.W))
    val in2 = Input(UInt(8.W))
    val in3 = Input(UInt(8.W))
    val in4 = Input(UInt(8.W))
    val in5 = Input(UInt(8.W))
    val path = Input(UInt(3.W))
    val out = Output(UInt(8.W))
  })

  io.out := MuxLookupBi(
    io.path,
    0.U,
    Seq(
      0.U -> io.in1,
      1.U -> io.in2,
      2.U -> io.in3,
      3.U -> io.in4,
      4.U -> io.in5,
    )
  )
}

class Test extends MultiIOModule {
  val in = IO(Input(UInt(2.W)))
  val out = IO(Output(UInt()))

  val add = noPrefix { in + in + in }

  out := add
}

class Test extends Module {
  val io = IO(new Bundle{
    val in   = Input(Vec(4, UInt(3.W)))
    val out  = Output(UInt(3.W))
  })

  val o
  io.out := 7.U
  for (i <- 0 until 4) {
    when (io.in(i) < io.out) {
      io.out := io.in(i)
    }
  }
}

class X extends Bundle {
  val a = Bool()
}

class Test extends Module {
  val io = IO(new Bundle{
    val in = Input(Bool())
    val x = Output(new X)
    val x_a = Output(Bool())
  })

  io.x.a := !io.in
  io.x_a := io.in
}

class ISIn extends Bundle {
  val pc   = Output(UInt(32.W))
  val inst = Output(UInt(32.W))
}

class Test extends Module with TestConfig {
  val io = IO(new Bundle{
    val debug = Output(Vec(3, Vec(2, UInt(32.W))))
  })

  for (i <- 0 until 3)
    for (j <- 0 until 2)
      io.debug(i)(j) := (i * j).Uexception
}

class Test extends Module with TestConfig {
  val io = IO(new Bundle{
    val in  = Input(Vec(QUEUE_LEN, Bool()))
    val out = Output(Vec(ALU_PATH_NUM, UInt(32.W)))
    //val out = Output(Vec(ISSUE_NUM, new ISIn()))
    //val cnt = Output(UInt(32.W))
  })

  val queue = Reg(Vec(5, new ISIn))
  val head = RegInit(0.U(QUEUE_LEN_W.W))
  val tail = RegInit(0.U(QUEUE_LEN_W.W))

  for (i <- 0 until 5) {
    queue(i).pc := queue(i).pc + 1.U(32.W)
    queue(i).inst := queue(i).inst + 1.U(32.W)
  }
  head := head + 1.U(QUEUE_LEN_W.W)

  for (i <- 0 until ISSUE_NUM) {
    when (head + i.U(QUEUE_LEN_W.W) < queue_size.U(QUEUE_LEN_W.W)) {
      io.out(i) := queue(i.U(QUEUE_LEN_W.W) + head)
    }
    .otherwise {
      io.out(i) := queue(head + (16 + i - queue_size).U(QUEUE_LEN_W.W))
    }
  }

  // This queue simulates the instructions to be issued
  val queue = Wire(Vec(QUEUE_LEN, UInt(32.W)))
  // The path declares the set of FUs (ALU, MDU, LSU) it requires
  // In this simulation we may suppose there are only 2 type of FUs
  // And we only issue instructions with Type #1
  val path = Wire(Vec(QUEUE_LEN, Bool()))
  for (i <- 0 until QUEUE_LEN) {
    queue(i) := (i + 100).U(32.W)
    path(i) := ~(i & 1).B
  }

  val id = Wire(Vec(ALU_PATH_NUM, UInt(4.W)))
  // for each path
  for (j <- 0 until ALU_PATH_NUM) {
    id(j) := QUEUE_LEN.U(4.W)

    // For each instruciton in the queue
    // pre-decide whether it is available
    val available      = Wire(Vec(QUEUE_LEN, Bool()))
    val available_pass = Wire(Vec(QUEUE_LEN, Bool()))
    for (i <- 0 until QUEUE_LEN) {
      if (j != 0)
        // It must have the correct type
        // And it's id must be greater than the last issued instruction with the same type
        // That is, it must havn not yet been issued
        available(i)      := (path(i) & i.U(4.W) > id(j-1))
      else
        available(i)      :=  path(i)
    }

    available_pass(0) := false.B
    for (i <- 1 until QUEUE_LEN)
      available_pass(i) := available_pass(i-1) | available(i-1)

    // find the FIRST fitting instruction (which hasn't been issued yet)
    for (i <- 0 until QUEUE_LEN) {
      when (available(i) & ~available_pass(i)) {
        id(j) := i.U(4.W)
      }
    }

    when (id(j) < QUEUE_LEN.U(4.W)) {
      io.out(j) := queue(id(j))
    }
    .otherwise {
      io.out(j) := 0.U(32.W)
    }
  }

  // io.cnt := item
}
*/