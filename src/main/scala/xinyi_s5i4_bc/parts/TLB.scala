package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import config.config._

trait TLBConfig {
  val TLB_ENTRY_NUM = 64
  val TLB_INDEX_W   = 6
  val PAGE_SIZE_W   = 12
  val VPN_W         = LGC_ADDR_W - PAGE_SIZE_W - 1
  val PFN_W         = PHY_ADDR_W - PAGE_SIZE_W
}

class TLBItem extends Bundle with TLBConfig {
	val pfn = UInt(PFN_W.W)
  val c   = UInt(3.W)
  val d   = Bool()
  val v   = Bool()
}

class TLBEntry extends Bundle with TLBConfig {
	val vpn2 = UInt(VPN_W.W)
  val g    = Bool()
  val asid = UInt(8.W)

  val i0   = new TLBItem
  val i1   = new TLBItem
}

class TLBLookupInterface extends Bundle with TLBConfig {
  val vpn2  = Input(UInt(VPN_W.W))
  val index = Output(UInt(XLEN.W))
  val entry = Output(new TLBEntry)
  val miss  = Output(Bool())
}

class TLBRInterface extends Bundle {
  val index     = Input(UInt(XLEN.W))
  val entry_hi  = Output(UInt(XLEN.W))
  val entry_lo0 = Output(UInt(XLEN.W))
  val entry_lo1 = Output(UInt(XLEN.W))
}

class TLBWInterface extends Bundle {
  val index     = Input(UInt(32.W))
  val entry_hi  = Input(UInt(XLEN.W))
  val entry_lo0 = Input(UInt(XLEN.W))
  val entry_lo1 = Input(UInt(XLEN.W))
}

class TLBInterface extends Bundle {
  val asid  = Input(UInt(8.W))
  val path  = Vec(LSU_PATH_NUM + 1, new TLBLookupInterface)  // LSU_NUM * D + 1 * I
  val read  = new TLBRInterface
  val ren   = Input(Bool())
  val write = new TLBWInterface 
  val wen   = Input(Bool())
}

class TLB extends Module with TLBConfig {
  val io = IO(new TLBInterface)

  val entry = RegInit(VecInit(Seq.fill(TLB_ENTRY_NUM)(0.U.asTypeOf(new TLBEntry))))

  // probe (by axid & vpn)
  for (j <- 0 to LSU_PATH_NUM) {
    val hit_one_hot = Wire(Vec(TLB_ENTRY_NUM, Bool()))
    for (i <- 0 until TLB_ENTRY_NUM) {
      hit_one_hot(i) := (entry(i).g | (entry(i).asid === io.asid)) & (entry(i).vpn2 === io.path(j).vpn2)
    }
    
    io.path(j).miss  := !(hit_one_hot.asUInt().orR())
    io.path(j).entry := Mux1H(hit_one_hot, entry)
    io.path(j).index := OHToUInt(hit_one_hot)
  }
  
  // read (by index)
  when (io.ren) {
    val hit_one_hot = Wire(Vec(TLB_ENTRY_NUM, Bool()))
    for (i <- 0 until TLB_ENTRY_NUM) {
      hit_one_hot(i) := i.U === io.path(j).addr
    }
    
    val rd_entry = Mux1H(hit_one_hot, entry)
    io.read.hi  := Cat(rd_entry.vpn2, 0.U((XLEN - 8 - VPN_W).W), rd_entry.asid)
    io.read.lo0 := Cat(0.U((XLEN - 6 - PFN_W).W), rd_entry.i0.pfn, rd_entry.i0.c, rd_entry.i0.d, rd_entry.i0.v, rd_entry.g)
    io.read.lo1 := Cat(0.U((XLEN - 6 - PFN_W).W), rd_entry.i1.pfn, rd_entry.i1.c, rd_entry.i1.d, rd_entry.i1.v, rd_entry.g)
  }

  // write (by index)
  when (io.wen) {
    entry(io.write.index).vpn2   := io.write.entry_hi(31, 13)
    entry(io.write.index).g      := io.write.entry_lo1(0)
    entry(io.write.index).asid   := io.write.entry_hi(7, 0)
  
    entry(io.write.index).i0.pfn := io.write.entry_lo0(29, 6)
    entry(io.write.index).i0.c   := io.write.entry_lo0(5, 3)
    entry(io.write.index).i0.d   := io.write.entry_lo0(2)
    entry(io.write.index).i0.v   := io.write.entry_lo0(1)

    entry(io.write.index).i1.pfn := io.write.entry_lo1(29, 6)
    entry(io.write.index).i1.c   := io.write.entry_lo1(5, 3)
    entry(io.write.index).i1.d   := io.write.entry_lo1(2)
    entry(io.write.index).i1.v   := io.write.entry_lo1(1)
  }
}
