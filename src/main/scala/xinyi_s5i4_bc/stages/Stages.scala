package xinyi_s5i4_bc.stages

import chisel3._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._

class IFIn extends Bundle with XinYiConfig {
  val pc = Input(UInt(lgc_addr_w.W))
}

class IFOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(l1_w.W))
}

// Load load_num instructions at a time
// Branch Cache
class IFStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IFIn
    val cache = Flipped(new RAMInterface(lgc_addr_w, l1_w))
    val out   = new IFOut
  })

  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  io.cache.din  := 0.U(32.W)
  
  io.out.pc := io.in.pc
  io.out.inst := io.cache.dout
}

class IDIn extends Bundle with XinYiConfig {
  val pc   = Input(UInt(lgc_addr_w.W))
  val inst = Input(UInt(data_w.W))
}

class IDOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(data_w.W))
  val dec  = Output(new ControlSet)
}

// Decode 2 instructions
// Branch Cache
class IDStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IDIn
    val out   = new IDOut
  })

  val decoder = Module(new MIPSDecoder)
  decoder.io.inst := io.in.inst

  io.out.pc   := io.in.pc
  io.out.inst := io.in.inst
  io.out.dec  := decoder.io.ctrl
}

// 