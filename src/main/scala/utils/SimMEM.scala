package utils


import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import xinyi_s5i4_bc.caches.{DCacheCPUIO, ICacheCPUIO}


class SimMEMIO extends Bundle {
  val icache_io  = new ICacheCPUIO
  val dcache_io  = new DCacheCPUIO
  val last_stall = Input(Bool())
}


class SimMEM extends Module {
  val io = IO(new SimMEMIO)

  val write_ram = WireDefault(true.B)

  val ram_mask    = "h3ffffff".U
  val icandidates = Wire(Vec(8, UInt(8.W)))
  val dcandidates = Wire(Vec(4, UInt(8.W)))

  when(io.dcache_io.rd || io.dcache_io.wr) {
    when(io.dcache_io.addr(29, 0) >= "h4000000".U) {
      write_ram := false.B
      //       printf("dcache is accessing %x, might be mmio\n", io.dcache_io.req.addr)
    }
  }

  when(io.icache_io.rd) {
    when(io.icache_io.addr >= "h84000000".U) {
      // printf("icache is accessing %x, ram overflow\n", io.icache_io.req.addr)
    }
    // printf("icache reading %x, inst is %x\n", io.icache_io.req.addr, icandidates(0))
  }

  // For simplicity, fixed at 0x8000_0000 to 0x8400_0000 RAM, 64MB
  // async read, sync write
  val memory = Mem(0x4000000L, UInt(8.W))
  loadMemoryFromFileInline(memory, "./ucore-kernel-initrd.hex")

  for (i <- 0 until 8) {
    icandidates(i) := memory.read(ram_mask & (io.icache_io.addr + i.U))
  }
  for (i <- 0 until 4) {
    dcandidates(i) := memory.read(ram_mask & (io.dcache_io.addr + i.U))
  }

  //   printf("mem 0x80000000 is %x\n", Cat(memory.read(3.U), memory.read(2.U), memory.read(1.U), memory.read(0.U)))

  // read is simple
  io.icache_io.stall_req := false.B
  io.icache_io.data := RegNext(icandidates.asUInt())
  io.dcache_io.stall_req := false.B
  io.dcache_io.dout := dcandidates.asUInt()

  // write is complex
  val uart_print_addr = 0
  when(io.dcache_io.wr && write_ram) {
    switch(io.dcache_io.size) {
      is(1.U) {
        // 1 byte
        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
      }
      is(2.U) {
        // 2 byte
        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
        memory.write((io.dcache_io.addr + 1.U) & ram_mask, io.dcache_io.din(15, 8))
      }
      is(4.U) {
        // 4 byte
        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
        memory.write((io.dcache_io.addr + 1.U) & ram_mask, io.dcache_io.din(15, 8))
        memory.write((io.dcache_io.addr + 2.U) & ram_mask, io.dcache_io.din(23, 16))
        memory.write((io.dcache_io.addr + 3.U) & ram_mask, io.dcache_io.din(31, 24))
      }
    }
  }
}
