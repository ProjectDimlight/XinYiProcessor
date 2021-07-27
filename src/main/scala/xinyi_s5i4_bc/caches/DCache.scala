package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.AXIIO
import utils._
import chisel3.util.random.LFSR

trait DCacheConfig {
  // predefined parameters
  val CACHE_SIZE = 8 * 1024 * 8 // 8KB
  val LINE_NUM = 8
  val DATA_WIDTH = XLEN
  val WAY_NUM = 4
  val NAME = "DCache"

  // derived parameters
  val LINE_WIDTH = LINE_NUM * DATA_WIDTH
  val OFFSET_WIDTH = log2Ceil(LINE_WIDTH / 8)

  val SET_NUM = CACHE_SIZE / (WAY_NUM * LINE_WIDTH)
  val INDEX_WIDTH = log2Ceil(SET_NUM)
  val LINEOFFSET_WIDTH = log2Ceil(LINE_NUM)
  val WORDOFFSET_WIDTH = log2Ceil(DATA_WIDTH / 8)
  val TAG_WIDTH = PHY_ADDR_W - OFFSET_WIDTH - INDEX_WIDTH
  val META_WIDTH = TAG_WIDTH + 1

  // flags
  val hasDCache = true
  val hasDCacheOp = false
  val useBRAM = true
}

class DCacheAddr extends Bundle with DCacheConfig {
  val tag = UInt(TAG_WIDTH.W)
  val index = UInt(INDEX_WIDTH.W)
  val line_offset = UInt(LINEOFFSET_WIDTH.W)
  val word_offset = UInt(WORDOFFSET_WIDTH.W)
}

class DCacheMeta extends Bundle with DCacheConfig {
  val valid = Bool()
  val tag = UInt(TAG_WIDTH.W)
  override def toPrintable: Printable =
    p"DCacheMeta(valid = ${valid}, tag = 0x${Hexadecimal(tag)})"
}

class DCacheData extends Bundle with DCacheConfig {
  val data = Vec(LINE_NUM, UInt(DATA_WIDTH.W))
  override def toPrintable: Printable = p"DCacheData(data = ${data})"
}

class DCacheCPUIO extends Bundle with DCacheConfig {
  val rd = Input(Bool())
  val wr = Input(Bool())
  val uncache = Input(Bool())
  val size = Input(UInt(2.W))
  val strb = Input(UInt((DATA_WIDTH / 8).W))
  val addr = Input(UInt(PHY_ADDR_W.W))
  val din = Input(UInt(DATA_WIDTH.W))
  val dout = Output(UInt(DATA_WIDTH.W))
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

  val last_stall = Input(Bool()) // 上一拍stall，意味着这一拍没有新的请求，以免重复发送读写请求
  val stall = Input(Bool()) // 用来同步ytz的状态机，当前版本无效
  val flush = Input(Bool()) // 流水线要求刷洗；dummy cache无效
}

// define a base class to suppress deprecation warning
class DCachePathBase extends Module with DCacheConfig {
  val io = IO(new DCachePathIO)
  io <> DontCare
}

class DCachePath extends DCachePathBase {
  val s_idle :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_uncache_req :: s_uncache_resp :: s_invalidate :: Nil =
    Enum(8)
  val state = RegInit(s_idle)

  val upper = io.upper
  val uncached = upper.uncache

  class DCacheReq extends Bundle with DCacheConfig {
    val rd = Bool()
    val wr = Bool()
    val uncache = Bool()
    val size = UInt(2.W)
    val strb = UInt((DATA_WIDTH / 8).W)
    val addr = new DCacheAddr
    val din = UInt(DATA_WIDTH.W)
  }

  val new_request = !io.last_stall & (upper.rd | upper.wr)
  val upper_request = Wire(new DCacheReq)
  val current_request = RegEnable(upper_request, new_request)
  upper_request.rd := upper.rd
  upper_request.wr := upper.wr
  upper_request.uncache := upper.uncache
  upper_request.size := upper.size
  upper_request.strb := upper.strb
  upper_request.addr := upper.addr.asTypeOf(new DCacheAddr)
  upper_request.din := upper.din

  val read_index = Wire(UInt(INDEX_WIDTH.W))
  val read_meta = Wire(Vec(WAY_NUM, new DCacheMeta))
  val read_data = Wire(Vec(WAY_NUM, new DCacheData))

  read_index := upper_request.addr.index

  val invalid_vec = VecInit(read_meta.map(m => !m.valid)).asUInt
  val tag_vec = VecInit(
    read_meta.map(m => m.tag === upper_request.addr.tag)
  ).asUInt
  val hit_vec = invalid_vec & tag_vec
  // val hit_index = PriorityEncoder(hit_vec)
  val hit = hit_vec.orR && !uncached

  // random replacement
  // val victim_index = Mux(
  //   invalid_vec.orR,
  //   PriorityEncoder(invalid_vec),
  //   LFSR(XLEN)(log2Ceil(WAY_NUM) - 1, 0)
  // )
  val victim_vec = Mux(
    invalid_vec.orR,
    invalid_vec,
    UIntToOH(LFSR(XLEN)(log2Ceil(WAY_NUM) - 1, 0))
  )

  val access_vec = Mux(hit, hit_vec, victim_vec)

  val stall = Wire(Bool())
  val write_index = Wire(UInt(INDEX_WIDTH.W))
  val write_meta = Wire(Vec(WAY_NUM, new DCacheMeta))
  val write_data = Reg(Vec(WAY_NUM, new DCacheData))

  val need_meta_write = Wire(Bool())
  val need_data_write = Wire(Bool())
  val access_index = Wire(UInt(INDEX_WIDTH.W))

  stall := DontCare
  write_index := DontCare
  write_meta := DontCare
  write_data := DontCare

  need_meta_write := false.B
  need_data_write := false.B
  access_index := DontCare

  // state machine
  switch(state) {
    is(s_idle) {
      when(!hit) {}
    }
  }

  // BRAM IO
  for (i <- 0 until WAY_NUM) {
    io.bram(i).meta_we := need_meta_write
    io.bram(i).meta_addr := access_index(i)
    io.bram(i).meta_din := write_meta(i).asTypeOf(UInt(META_WIDTH.W))
    read_meta(i) := io.bram(i).meta_dout.asTypeOf(new DCacheMeta)
    io.bram(i).data_we := need_data_write
    io.bram(i).data_addr := access_index(i)
    io.bram(i).data_din := write_data(i).asTypeOf(UInt(LINE_WIDTH.W))
    read_data(i) := io.bram(i).data_dout.asTypeOf(new DCacheData)
  }

  // AXI IO
  io.lower <> DontCare

}

class DCachePathFake extends DCachePathBase {
  val s_idle :: s_pending :: s_busy :: s_valid :: Nil = Enum(4)
  val state_reg = RegInit(s_idle)
  val state = Wire(chiselTypeOf(s_idle))

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
  val data_reg = RegInit(0.U(DATA_WIDTH.W))
  val data = io.lower.rdata
  when(valid) {
    data_reg := data
  }
  io.upper.dout := Mux(valid, data, data_reg)
  io.upper.stall_req := (state =/= s_valid) & (state =/= s_idle)

}
class DCache extends Module with DCacheConfig {
  val io = IO(new DCacheIO)

  // connect to path
  val path = List.fill(LSU_PATH_NUM)(if (hasDCache) { Module(new DCachePath) }
  else { Module(new DCachePathFake) })
  for (i <- 0 until LSU_PATH_NUM) {
    path(i).io.upper <> io.upper(i)
    path(i).io.lower <> io.lower(i)
    path(i).io.last_stall <> io.last_stall
    path(i).io.stall <> io.stall
    path(i).io.flush <> io.flush
  }

  // connect to RAM
  for (i <- 0 until WAY_NUM) {
    path(0).io.bram(i) <> DontCare
    if (LSU_PATH_NUM == 2) {
      path(1).io.bram(i) <> DontCare
    }
  }
  if (hasDCache) {
    if (useBRAM) {
      val meta = List.fill(WAY_NUM)(
        Module(
          new DualPortLUTRAM(
            DATA_WIDTH = META_WIDTH,
            DEPTH = SET_NUM,
            LATENCY = 0
          )
        )
      )
      val data = List.fill(WAY_NUM)(
        Module(new DualPortRAM(DATA_WIDTH = LINE_WIDTH, DEPTH = SET_NUM))
      )
      for (i <- 0 until WAY_NUM) {
        meta(i).io.clk := clock
        meta(i).io.rst := reset
        meta(i).io.wea := path(0).io.bram(i).meta_we
        meta(i).io.addra := path(0).io.bram(i).meta_addr
        meta(i).io.dina := path(0).io.bram(i).meta_din
        path(0).io.bram(i).meta_dout := meta(i).io.douta

        data(i).io.clk := clock
        data(i).io.rst := reset
        data(i).io.wea := path(0).io.bram(i).data_we
        data(i).io.addra := path(0).io.bram(i).data_addr
        data(i).io.dina := path(0).io.bram(i).data_din
        path(0).io.bram(i).data_dout := data(i).io.douta
        if (LSU_PATH_NUM == 2) {
          // TODO avoid write conflict
          // meta(i).io.web := path(1).io.bram(i).meta_we
          meta(i).io.addrb := path(1).io.bram(i).meta_addr
          // meta(i).io.dinb := path(1).io.bram(i).meta_din
          path(1).io.bram(i).meta_dout := meta(i).io.doutb
          data(i).io.web := path(1).io.bram(i).data_we
          data(i).io.addrb := path(1).io.bram(i).data_addr
          data(i).io.dinb := path(1).io.bram(i).data_din
          path(1).io.bram(i).data_dout := data(i).io.doutb
        }
      }
    } else {
      val meta = List.fill(WAY_NUM)(Mem(SET_NUM, UInt(META_WIDTH.W)))
      val data = List.fill(WAY_NUM)(SyncReadMem(SET_NUM, UInt(LINE_WIDTH.W)))
      for (i <- 0 until WAY_NUM) {
        for (j <- 0 until LSU_PATH_NUM) {
          val bram = path(j).io.bram(i)
          bram.meta_dout := meta(i).read(bram.meta_addr)
          when(bram.meta_we) {
            meta(i).write(bram.meta_addr, bram.meta_din)
          }
          bram.data_dout := data(i).read(bram.data_addr, !bram.data_we)
          when(bram.data_we) {
            data(i).write(bram.data_addr, bram.data_din)
          }
        }
      }
    }
  }
}
