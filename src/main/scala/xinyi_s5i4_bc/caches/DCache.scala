package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.AXIIO

trait DCacheConfig {
  val TAG_WIDTH       = 22
  val INDEX_WIDTH     = XLEN - TAG_WIDTH
  val ENTRY_NUM       = 1 << INDEX_WIDTH
  val SET_ASSOCIATIVE = 4
}

class DCacheCPUIO extends Bundle {
  val rdata     = Output(UInt(XLEN.W))
  val wdata     = Input(UInt(XLEN.W))
  val addr      = Input(UInt(XLEN.W))
  val rd        = Input(Bool())
  val wr        = Input(Bool())
  val stall_req = Output(Bool())
  val flush     = Input(Bool())
}


class DCache extends Module {
  val io = IO(new Bundle {
    val cpu_io         = new DCacheCPUIO
    val dcache_axi_io  = new AXIIO
    val uncache_axi_io = new AXIIO
  })



}