package utils

import chisel3._
import chisel3.util._
import chisel3.util.experimental._
import config.config._
import xinyi_s5i4_bc.AXIIO

class SimAXI extends Module {
  val io = IO(new Bundle {
    val icache_axi = Flipped(new AXIIO)
    val dcache_axi = Flipped(new AXIIO)
  })

  // memory
  val memory = Mem(0x4000000L, UInt(8.W))
  loadMemoryFromFileInline(memory, "../elf/ucore-kernel-initrd.hex")

  val write_ram = WireDefault(true.B)
  val ram_mask  = "h3ffffff".U


  // AXI FSM for read channel
  val r_idle :: r_ready :: r_process :: Nil = Enum(3)

  // AXI FSM for write channel
  val w_idle :: w_aready :: w_ready :: w_process :: Nil = Enum(4)

  // icache read FSM
  val ir_state = RegInit(r_idle)

  val icache_araddr = Reg(UInt(XLEN.W))
  val icache_arlen  = Reg(UInt(4.W))

  io.icache_axi <> DontCare
  io.icache_axi.arready := ir_state === r_ready
  io.icache_axi.rlast := icache_arlen === 0.U

  switch(ir_state) {
    is(r_idle) {
      when(io.icache_axi.arvalid) {
        ir_state := r_ready
      }
    }
    is(r_ready) {
      ir_state := r_process
      icache_arlen := io.icache_axi.arlen + 1.U // counter
      icache_araddr := io.icache_axi.araddr // addr
    }
    is(r_process) {
      when(icache_arlen > 0.U) {
        val icandidates = Wire(Vec(4, UInt(8.W)))
        for (i <- 0 until 4) {
          icandidates(i) := memory.read(ram_mask & (icache_araddr + i.U))
        }
        io.icache_axi.rdata := icandidates.asUInt()
        icache_araddr := icache_araddr + 4.U
        icache_arlen := icache_arlen - 1.U
      }.otherwise {
        ir_state := r_idle
      }
    }
  }



  // DCache AXI Request

  // dcache read FSM
  val dr_state = RegInit(r_idle)

  val dcache_araddr = Reg(UInt(XLEN.W))
  val dcache_arlen  = Reg(UInt(4.W))

  io.dcache_axi <> DontCare
  io.dcache_axi.arready := dr_state === r_ready
  io.dcache_axi.rlast := dcache_arlen === 0.U

  switch(dr_state) {
    is(r_idle) {
      when(io.dcache_axi.arvalid) {
        dr_state := r_ready
      }
    }
    is(r_ready) {
      dr_state := r_process
      dcache_arlen := io.dcache_axi.arlen + 1.U // counter
      dcache_araddr := io.dcache_axi.araddr // addr
    }
    is(r_process) {
      when(dcache_arlen > 0.U) {
        val dcandidates = Wire(Vec(4, UInt(8.W)))
        for (i <- 0 until 4) {
          dcandidates(i) := memory.read(ram_mask & (dcache_araddr + i.U))
        }

        val data = dcandidates.asUInt()
        io.dcache_axi.rdata := MuxLookup(
          io.dcache_axi.arsize, data,
          Seq(
            0.U -> data(7, 0),
            1.U -> data(15, 0),
          )
        )
        dcache_araddr := dcache_araddr + 4.U
        dcache_arlen := dcache_arlen - 1.U
      }.otherwise {
        dr_state := r_idle
      }
    }
  }

  // dcache write FSM
  val dw_state = RegInit(w_idle)

  val dcache_awlen = Reg(UInt(4.W))

  io.dcache_axi.awready := dw_state === w_aready
  io.dcache_axi.wready := dw_state === w_ready
  io.dcache_axi.bready := dw_state === w_process


  switch(dw_state) {
    is(w_idle) {
      when(io.dcache_axi.awvalid) {
        dw_state := w_aready
      }
    }

    is(w_aready) {
      dw_state := w_ready
      dcache_awlen := io.dcache_axi.awlen + 1.U
    }

    is(w_ready) {
      dw_state := w_process
    }

    is(w_process) {

    }
  }



  // Ignore

  //
  //  val icandidates = Wire(Vec(32, UInt(8.W)))
  //  val dcandidates = Wire(Vec(32, UInt(8.W)))
  //
  //  when(io.dcache_io.rd || io.dcache_io.wr) {
  //    when(io.dcache_io.addr(29, 0) >= "h4000000".U) {
  //      write_ram := false.B
  //      printf("dcache is accessing %x, might be mmio\n", io.dcache_io.addr)
  //    }
  //  }
  //
  //  when(io.icache_io.rd) {
  //    when(io.icache_io.addr >= "h84000000".U) {
  //      printf("icache is accessing %x, ram overflow\n", io.icache_io.addr)
  //    }
  //  }
  //
  //
  //  for (i <- 0 until 32) {
  //    icandidates(i) := memory.read(ram_mask & (io.icache_io.addr + i.U))
  //    dcandidates(i) := memory.read(ram_mask & (io.dcache_io.addr + i.U))
  //  }
  //
  //
  //  // read is simple
  //  io.icache_io.stall_req := RegNext(!io.icache_io.rd)
  //  io.icache_io.data := RegNext(icandidates.asUInt)
  //  io.dcache_io.stall_req := RegNext(!io.dcache_io.rd)
  //  io.dcache_io.dout := RegNext(dcandidates.asUInt)
  //
  //  // write is complex
  //  val uart_print_addr = 0
  //  when(io.dcache_io.wr && write_ram) {
  //    switch(io.dcache_io.size) {
  //      is(1.U) {
  //        // 1 byte
  //        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
  //      }
  //      is(2.U) {
  //        // 2 byte
  //        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
  //        memory.write((io.dcache_io.addr + 1.U) & ram_mask, io.dcache_io.din(15, 8))
  //      }
  //      is(4.U) {
  //        // 4 byte
  //        memory.write(io.dcache_io.addr & ram_mask, io.dcache_io.din(7, 0))
  //        memory.write((io.dcache_io.addr + 1.U) & ram_mask, io.dcache_io.din(15, 8))
  //        memory.write((io.dcache_io.addr + 2.U) & ram_mask, io.dcache_io.din(23, 16))
  //        memory.write((io.dcache_io.addr + 3.U) & ram_mask, io.dcache_io.din(31, 24))
  //      }
  //    }
  //  }
}