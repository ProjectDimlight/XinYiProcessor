package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import config.config._

trait TLBConfig {
  val TLB_ENTRY_NUM = 64
  val TLB_INDEX_W   = 6
  val PAGE_SIZE_W   = 12
  val VPN_W         = LGC_ADDR_W - PAGE_SIZE_W
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
  val index = Output(UInt(32.W))
  val entry = Output(new TLBEntry)
  val miss  = Output(Bool())
}

class TLBWInterface extends Bundle {
  val wen       = Input(Bool())
  val index     = Input(UInt(32.W))
  val entry_hi  = Input(UInt(XLEN.W))
  val entry_lo0 = Input(UInt(XLEN.W))
  val entry_lo1 = Input(UInt(XLEN.W))
}

class TLBInterface extends Bundle {
  val asid  = Input(UInt(8.W))
  val path  = Vec(LSU_PATH_NUM + 1, new TLBLookupInterface)  // LSU_NUM * D + 1 * I
  val write = new TLBWInterface 
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

  // write (by index)
  when (io.write.wen) {
    entry(io.write.index).vpn2 := io.write.entry_hi(31, 13)
    entry(io.write.index).g    := io.write.entry_lo1(0)
    entry(io.write.index).asid := io.write.entry_hi(7, 0)
  
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
