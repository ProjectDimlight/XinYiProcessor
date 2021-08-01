package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._

class CacheTB extends Module {
  val io = IO(new Bundle {
    val cpu = new DCacheCPUIO
    val last_stall = Input(Bool())
  })

  val cache = Module(new DCache)
  val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000

  mem.io.in <> DontCare
  cache.io.lower(0).awid <> mem.io.in.aw.bits.id
  cache.io.lower(0).awaddr <> mem.io.in.aw.bits.addr
  cache.io.lower(0).awlen <> mem.io.in.aw.bits.len
  cache.io.lower(0).awsize <> mem.io.in.aw.bits.size
  cache.io.lower(0).awburst <> mem.io.in.aw.bits.burst
  cache.io.lower(0).awlock <> mem.io.in.aw.bits.lock
  cache.io.lower(0).awcache <> mem.io.in.aw.bits.cache
  cache.io.lower(0).awprot <> mem.io.in.aw.bits.prot
  cache.io.lower(0).awvalid <> mem.io.in.aw.valid
  cache.io.lower(0).awready <> mem.io.in.aw.ready
  // cache.io.lower(0).wid <> mem.io.in.w.bits.id
  cache.io.lower(0).wdata <> mem.io.in.w.bits.data
  cache.io.lower(0).wstrb <> mem.io.in.w.bits.strb
  cache.io.lower(0).wlast <> mem.io.in.w.bits.last
  cache.io.lower(0).wvalid <> mem.io.in.w.valid
  cache.io.lower(0).wready <> mem.io.in.w.ready
  cache.io.lower(0).bid <> mem.io.in.b.bits.id
  cache.io.lower(0).bresp <> mem.io.in.b.bits.resp
  cache.io.lower(0).bvalid <> mem.io.in.b.valid
  cache.io.lower(0).bready <> mem.io.in.b.ready
  cache.io.lower(0).arid <> mem.io.in.ar.bits.id
  cache.io.lower(0).araddr <> mem.io.in.ar.bits.addr
  cache.io.lower(0).arlen <> mem.io.in.ar.bits.len
  cache.io.lower(0).arsize <> mem.io.in.ar.bits.size
  cache.io.lower(0).arburst <> mem.io.in.ar.bits.burst
  cache.io.lower(0).arlock <> mem.io.in.ar.bits.lock
  cache.io.lower(0).arcache <> mem.io.in.ar.bits.cache
  cache.io.lower(0).arprot <> mem.io.in.ar.bits.prot
  cache.io.lower(0).arvalid <> mem.io.in.ar.valid
  cache.io.lower(0).arready <> mem.io.in.ar.ready
  cache.io.lower(0).rid <> mem.io.in.r.bits.id
  cache.io.lower(0).rdata <> mem.io.in.r.bits.data
  cache.io.lower(0).rresp <> mem.io.in.r.bits.resp
  cache.io.lower(0).rlast <> mem.io.in.r.bits.last
  cache.io.lower(0).rvalid <> mem.io.in.r.valid
  cache.io.lower(0).rready <> mem.io.in.r.ready

  io.cpu <> cache.io.upper(0)
  io.last_stall <> cache.io.last_stall
  cache.io.stall := false.B
  cache.io.flush := false.B  
}
