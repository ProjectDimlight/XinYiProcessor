package cache

import chisel3._
import chisel3.util._

class AXI4RAM(memByte: Int, name: String = "ram") extends AXI4Slave(name = name){
  // val offsetBits = log2Up(memByte)
  // val offsetMask = (1 << offsetBits) - 1
  // def index(addr: UInt) = (addr & offsetMask.U) >> log2Ceil(xlen / 8)
  // def inRange(idx: UInt) = idx < (memByte / 8).U

  val offset = log2Ceil(bitWidth)
  val wIdx = waddr + (writeBeatCnt << offset)
  val rIdx = raddr + (readBeatCnt << offset)
  val wen = io.in.w.fire() // && inRange(wIdx)

  val mem = Mem(memByte / 4, UInt(32.W))

  //  IPORT
  val rdata = mem(rIdx)
  io.in.r.bits.data := RegEnable(rdata, ren)

  //  DPORT
  when (wen) {
    val data = mem(wIdx)
    mem(wIdx) := (io.in.w.bits.data & fullMask) | (data & ~fullMask)
  }

  // printf(p"[${GTimer()}]: AXI4RAM Debug Start----------\n")
  // printf(
  //   "waddr = %x, wIdx = %x, wdata = %x, wmask = %x, wen = %d, writeBeatCnt = %d\n",
  //   waddr,
  //   wIdx,
  //   io.in.w.bits.data,
  //   fullMask,
  //   wen,
  //   writeBeatCnt
  // )
  // printf(
  //   "raddr = %x, rIdx = %x, readBeatCnt = %d\n",
  //   raddr,
  //   rIdx,
  //   readBeatCnt
  // )
  // printf("----------AXI4RAM Debug Done----------\n")
}
