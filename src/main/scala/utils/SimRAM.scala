package utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import config.config._
import xinyi_s5i4_bc.caches.{DCacheCPUIO, ICacheCPUIO}

class SimRAM extends Module {
  val io = IO(new Bundle {
    val icache_io = new ICacheCPUIO
    val dcache_io = new DCacheCPUIO
  })

  val write_ram = WireDefault(true.B)

  val ram_mask    = "h3ffffff".U
  val icandidates = Wire(Vec(32, UInt(8.W)))
  val dcandidates = Wire(Vec(32, UInt(8.W)))

  when(io.dcache_io.rd || io.dcache_io.wr) {
    when(io.dcache_io.addr(29, 0) >= "h4000000".U) {
      write_ram := false.B
      printf("dcache is accessing %x, might be mmio\n", io.dcache_io.addr)
    }
  }

  when(io.icache_io.rd) {
    when(io.icache_io.addr >= "h84000000".U) {
      printf("icache is accessing %x, ram overflow\n", io.icache_io.addr)
    }
  }

  val memory = Mem(0x4000000L, UInt(8.W))
  loadMemoryFromFileInline(memory, "../elf/ucore-kernel-initrd.hex")

  for (i <- 0 until 32) {
    icandidates(i) := memory.read(ram_mask & (io.icache_io.addr + i.U))
    dcandidates(i) := memory.read(ram_mask & (io.dcache_io.addr + i.U))
  }


  // read is simple
  io.icache_io.rd := RegNext(io.icache_io.rd)
  io.icache_io.data := RegNext(icandidates.asUInt)
  io.dcache_io.rd := RegNext(io.dcache_io.rd)
  io.dcache_io.dout := RegNext(dcandidates.asUInt)

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