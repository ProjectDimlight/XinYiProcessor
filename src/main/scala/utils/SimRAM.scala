package utils

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.caches._

class SimDualPortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
  val io = IO(new DualPortBRAMIO(DATA_WIDTH, DEPTH))

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


class SimDualPortLUTRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
  val io  = IO(new DualPortLUTRAMIO(DATA_WIDTH, DEPTH))
  val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

  io.douta := mem(io.addra)
  io.doutb := mem(io.addrb)

  when(io.wea) {
    mem(io.addra) := io.dina
  }
}


class SimSinglePortBRAM(DATA_WIDTH: Int, DEPTH: Int) extends Module {
  val io = IO(new SinglePortBRAMIO(DATA_WIDTH, DEPTH))

  val mem = RegInit(VecInit(Seq.fill(DEPTH)(0.U(DATA_WIDTH.W))))

  io.dout := RegNext(mem(io.addr))

  when(io.we) {
    mem(io.addr) := io.din
  }
}