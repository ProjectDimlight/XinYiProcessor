package xinyi_s5i4_bc.stages

import chisel3._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._

class IFIn extends Bundle with XinYiConfig {
  val pc = Input(UInt(addrw.W))
}

class IFOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(addrw.W))
  val inst = Output(UInt(instw.W))
}

// Load 2 instructions
// Branch Cache
class IFStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IFIn
    val bc    = Flipped(new BranchCacheOut)
    val cache = Flipped(new RAMInterface)
    val out   = new IFOut
  })

  io.cache.addr := io.in.pc
  io.cache.din  := 0.U(32.W)

  when (io.bc.branch_cache_overwrite) {
    io.out.pc := io.bc.pc
    io.out.inst := io.bc.inst
  } .otherwise {
    io.out.pc := io.in.pc
    io.out.inst := io.cache.dout
  }
}

class IDIn extends Bundle with XinYiConfig {
  val pc   = Input(UInt(addrw.W))
  val inst = Input(UInt(instw.W))
}

class IDOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(addrw.W))
  val inst = Output(UInt(instw.W))
  val dec  = Output(new ControlSet)
}

// Decode 2 instructions
// Branch Cache
class IDStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IDIn
    val bc    = Flipped(new BranchCacheOut)
    val out   = new IDOut
  })

  val decoder = Module(new MIPSDecoder)
  decoder.io.inst := io.in.inst

  io.out.pc   := io.in.pc
  io.out.inst := io.in.inst
  io.out.dec  := decoder.io.ctrl
}

// 