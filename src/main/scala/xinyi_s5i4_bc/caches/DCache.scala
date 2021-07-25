package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.AXIIO
import utils._

trait DCacheConfig {
  // predefined parameters
  val CACHE_SIZE = 8 * 1024 * 8 // 8KB
  val LINE_NUM = 8
  val DATA_WIDTH = XLEN
  val WAY_NUM = 4
  val NAME = "DCache"

  // derived parameters
  val LINE_WIDTH = LINE_NUM * DATA_WIDTH
  val OFFSET_WIDTH = log2Ceil(LINE_NUM)

  val SET_NUM = CACHE_SIZE / (WAY_NUM * OFFSET_WIDTH)
  val INDEX_WIDTH = log2Ceil(SET_NUM)
  val TAG_WIDTH = PHY_ADDR_W - OFFSET_WIDTH - INDEX_WIDTH
  val META_WIDTH = TAG_WIDTH + 1
}

class DCacheAddr extends Bundle with DCacheConfig {
  val tag = UInt(TAG_WIDTH.W)
  val index = UInt(INDEX_WIDTH.W)
  val offset = UInt(OFFSET_WIDTH.W)
}

class DCacheMeta extends Bundle with ICacheConfig {
  val valid = Bool()
  val tag = UInt(TAG_WIDTH.W)
}

class DCacheCPUIO extends Bundle with DCacheConfig {
  val rd = Input(Bool())
  val wr = Input(Bool())
  val size = Input(UInt(2.W))
  val strb = Input(UInt((XLEN / 8).W))
  val addr = Input(UInt(PHY_ADDR_W.W))
  val din = Input(UInt(XLEN.W))
  val dout = Output(UInt(XLEN.W))
  val stall_req = Output(Bool())
  val invalidate = if (hasDCacheOp) { Input(Bool()) }
  else { null } // TODO support ops
}

class DCacheIO extends Bundle with DCacheConfig {
  val upper = Vec(LSU_PATH_NUM, new DCacheCPUIO)
  val lower = Vec(LSU_PATH_NUM, new AXIIO)

  val last_stall = Input(Bool())
  val stall = Input(Bool())
  val flush = Input(Bool())
  // val stall_req = Output(Vec(LSU_PATH_NUM, Bool()))
}

class PathBRAMIO extends Bundle with DCacheConfig {
  val meta_we = Input(Bool())
  val meta_addr = Input(UInt(log2Ceil(SET_NUM).W))
  val meta_din = Input(UInt(META_WIDTH.W))
  val meta_dout = Output(UInt(META_WIDTH.W))
  val data_we = Input(Bool())
  val data_addr = Input(UInt(log2Ceil(SET_NUM).W))
  val data_din = Input(UInt(DATA_WIDTH.W))
  val data_dout = Output(UInt(DATA_WIDTH.W))
}

class DCachePathIO extends Bundle with DCacheConfig {
  val upper = new DCacheCPUIO
  val lower = new AXIIO
  val bram = Flipped(Vec(WAY_NUM, new PathBRAMIO))

  val last_stall = Input(Bool())
  val stall = Input(Bool())
  val flush = Input(Bool())
}

// define a base class to suppress deprecation warning
class DCachePathBase extends Module with DCacheConfig {
  val io = IO(new DCachePathIO)
  io <> DontCare
}

class DCachePath extends DCachePathBase {
  io <> DontCare
}

class DCachePathFake extends DCachePathBase {
  val s_idle :: s_pending :: s_busy :: s_valid :: Nil = Enum(4)
  val state_reg = RegInit(s_idle)
  val state = Wire(UInt(3.W))  

  state := MuxLookupBi(
    state_reg,
    s_idle,
    Array(
      s_idle -> Mux(
        !io.last_stall & (io.upper.rd | io.upper.wr),
        s_pending,
        s_idle
      ),
      s_pending -> Mux(
        io.lower.arready | io.lower.awready,
        s_busy,
        s_pending
      ),
      s_busy -> Mux(
        io.lower.bvalid | io.lower.rvalid & io.lower.rlast,
        s_valid,
        s_busy
      ),
      s_valid -> Mux(
        !io.last_stall & (io.upper.rd | io.upper.wr),
        s_pending,
        s_idle
      )
    )
  )
  state_reg := state

  val size = io.upper.size
  //val strb     = MuxLookupBi(size, 15.U, Array(0.U -> 1.U, 1.U -> 3.U))
  val strb = io.upper.strb
  val addr_in = io.upper.addr
  val data_in = io.upper.din

  val rd = (state === s_pending) & io.upper.rd
  val wr = (state === s_pending) & io.upper.wr
  val wrd = RegInit(false.B)
  wrd := Mux(wr, true.B, Mux(io.lower.wready, false.B, wrd))

  io.lower.arid <> 0.U
  io.lower.araddr <> addr_in
  io.lower.arlen <> 0.U
  io.lower.arsize <> size
  io.lower.arburst <> 1.U
  io.lower.arlock <> 0.U
  io.lower.arcache <> 0.U
  io.lower.arprot <> 0.U
  io.lower.arvalid <> rd
  io.lower.rready <> 1.U
  io.lower.awid <> 0.U
  io.lower.awaddr <> addr_in
  io.lower.awlen <> 0.U
  io.lower.awsize <> size
  io.lower.awburst <> 1.U
  io.lower.awlock <> 0.U
  io.lower.awcache <> 0.U
  io.lower.awprot <> 0.U
  io.lower.awvalid <> wr
  io.lower.wid <> 0.U
  io.lower.wdata <> data_in
  io.lower.wstrb <> strb
  io.lower.wlast <> 1.U
  io.lower.wvalid <> wrd
  io.lower.bready <> 1.U

  val valid = (state === s_valid) & (state_reg =/= s_valid)
  val data_reg = RegInit(0.U(XLEN.W))
  val data = io.lower.rdata
  when(valid) {
    data_reg := data
  }
  io.upper.dout := Mux(valid, data, data_reg)
  io.upper.stall_req := (state =/= s_valid) & (state =/= s_idle)
}

class DCache extends Module with DCacheConfig {
  val io = IO(new DCacheIO)

  val path = List.fill(LSU_PATH_NUM)(if (hasDCache) { Module(new DCachePath) }
  else { Module(new DCachePathFake) })
  val data = if (hasDCache) {
    List.fill(WAY_NUM)(
      Module(new DualPortRAM(DATA_WIDTH = LINE_WIDTH, DEPTH = SET_NUM))
    )
  } else { null }
  val meta = if (hasDCache) {
    List.fill(WAY_NUM)(
      Module(new DualPortLUTRAM(DATA_WIDTH = LINE_WIDTH, DEPTH = SET_NUM))
    )
  } else { null }

  for (i <- 0 until LSU_PATH_NUM) {
    path(i).io.upper <> io.upper(i)
    path(i).io.lower <> io.lower(i)
    path(i).io.last_stall <> io.last_stall
    path(i).io.stall <> io.stall
    path(i).io.flush <> io.flush
  }

  for (j <- 0 until WAY_NUM) {
    if (hasDCache) {
      meta(j).io.wea := path(0).io.bram(j).meta_we
      meta(j).io.addra := path(0).io.bram(j).meta_addr
      meta(j).io.dina := path(0).io.bram(j).meta_din
      path(0).io.bram(j).meta_dout := meta(j).io.douta
      data(j).io.wea := path(0).io.bram(j).data_we
      data(j).io.addra := path(0).io.bram(j).data_addr
      data(j).io.dina := path(0).io.bram(j).data_din
      path(0).io.bram(j).data_dout := data(j).io.douta
      if (LSU_PATH_NUM == 2) {
        // TODO avoid write conflict
        // meta(j).io.web := path(1).io.bram(j).meta_we
        meta(j).io.addrb := path(1).io.bram(j).meta_addr
        // meta(j).io.dinb := path(1).io.bram(j).meta_din
        path(1).io.bram(j).meta_dout := meta(j).io.doutb
        data(j).io.web := path(1).io.bram(j).data_we
        data(j).io.addrb := path(1).io.bram(j).data_addr
        data(j).io.dinb := path(1).io.bram(j).data_din
        path(1).io.bram(j).data_dout := data(j).io.doutb
      }
    } else {
      path(0).io.bram(j) <> DontCare
      if (LSU_PATH_NUM == 2) {
        path(1).io.bram(j) <> DontCare
      }
    }
  }

}
