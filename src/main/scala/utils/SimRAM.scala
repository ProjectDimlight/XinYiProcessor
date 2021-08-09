package utils

import chisel3._
import chisel3.util._

class SimDualPortBRAM(DATA_WIDTH: Int, DEPTH: Int, LATENCY: Int = 1) extends Module {
  val io = IO(new Bundle {
    val clk   = Input(Clock())
    val rst   = Input(Reset())
    val wea   = Input(Bool())
    val web   = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina  = Input(UInt(DATA_WIDTH.W))
    val dinb  = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))
  })

  val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

  io.douta := RegNext(mem(io.addra))
  io.doutb := RegNext(mem(io.addrb))

  when(io.wea) {
    mem(io.addra) := io.dina
  }
  when(io.web) {
    mem(io.addrb) := io.dinb
  }
}


class SimDualPortLUTRAM(DATA_WIDTH: Int, DEPTH: Int, LATENCY: Int = 1) extends Module {
  val io  = IO(new Bundle {
    val clk   = Input(Clock())
    val rst   = Input(Reset())
    val wea   = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina  = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))
  })
  val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

  io.douta := (mem(io.addra))
  io.doutb := RegNext(mem(io.addrb))

  when(io.wea) {
    mem(io.addra) := io.dina
  }
}


class SimSinglePortBRAM(DATA_WIDTH: Int, DEPTH: Int, LATENCY: Int = 1) extends Module {
  val io = IO(new Bundle {
    val clk  = Input(Clock())
    val rst  = Input(Reset())
    val we   = Input(Bool())
    val addr = Input(UInt(log2Ceil(DEPTH).W))
    val din  = Input(UInt(DATA_WIDTH.W))
    val dout = Output(UInt(DATA_WIDTH.W))
  })

  val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

  io.dout := RegNext(mem(io.addr))

  when(io.we) {
    mem(io.addr) := io.din
  }
}