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
  val WAY_NUM = 2
  val NAME = "DCache"

  // derived parameters
  val LINE_WIDTH = LINE_NUM * DATA_WIDTH
  val OFFSET_WIDTH = log2Ceil(LINE_WIDTH / 8)

  val SET_NUM = CACHE_SIZE / (WAY_NUM * LINE_WIDTH)
  val INDEX_WIDTH = log2Ceil(SET_NUM)
  val LINEOFFSET_WIDTH = log2Ceil(LINE_NUM)
  val WORDOFFSET_WIDTH = log2Ceil(DATA_WIDTH / 8)
  val TAG_WIDTH = PHY_ADDR_W - OFFSET_WIDTH - INDEX_WIDTH
  val META_WIDTH = TAG_WIDTH + 2

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
  val dirty = Bool()
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
  val uncached = Input(Bool())
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
  val data_din = Input(UInt(LINE_WIDTH.W))
  val data_dout = Output(UInt(LINE_WIDTH.W))
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

  class DCacheReq extends Bundle with DCacheConfig {
    val rd = Bool()
    val wr = Bool()
    val uncached = Bool()
    val size = UInt(2.W)
    val strb = UInt((DATA_WIDTH / 8).W)
    val addr = new DCacheAddr
    val din = UInt(DATA_WIDTH.W)
  }

  val upper = io.upper
  val lower = io.lower
  val bram = io.bram

  val s_idle :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_uncache_req :: s_uncache_resp :: s_invalidate :: Nil =
    Enum(8)
  val state = RegInit(s_idle)

  val new_request = !io.last_stall & (upper.rd | upper.wr)
  val upper_request = Wire(new DCacheReq)
  val current_request = RegEnable(upper_request, new_request)
  val inflight_request = Mux(state === s_idle, upper_request, current_request)
  upper_request.rd := upper.rd
  upper_request.wr := upper.wr
  upper_request.uncached := upper.uncached
  upper_request.size := upper.size
  upper_request.strb := upper.strb
  upper_request.addr := upper.addr.asTypeOf(new DCacheAddr)
  upper_request.din := upper.din

  val read_index = inflight_request.addr.index
  val read_meta = Wire(Vec(WAY_NUM, new DCacheMeta))
  val read_data = Wire(Vec(WAY_NUM, new DCacheData))

  val invalid_vec = VecInit(read_meta.map(m => !m.valid)).asUInt
  val tag_vec = VecInit(
    read_meta.map(m => m.tag === inflight_request.addr.tag)
  ).asUInt
  val hit_vec = (~invalid_vec) & tag_vec
  // val hit_index = PriorityEncoder(hit_vec)
  val hit = hit_vec.orR && !inflight_request.uncached

  // random replacement
  val victim_index = if (WAY_NUM == 1) { 0.U }
  else {
    Mux(
      invalid_vec.orR,
      PriorityEncoder(invalid_vec),
      LFSR(XLEN)(log2Ceil(WAY_NUM) - 1, 0)
    )
  }
  val victim_vec = UIntToOH(victim_index)
  val access_vec = Mux(hit, hit_vec, victim_vec)
  val access_index = PriorityEncoder(access_vec)
  val cacheline_meta = read_meta(access_index)
  val cacheline_data = read_data(access_index)

  val awvalid_enable = Reg(Bool())
  val wvalid_enable = Reg(Bool())
  val read_counter = Counter(LINE_NUM)
  val write_counter = Counter(LINE_NUM)

  val read_satisfy = state === s_read_resp && lower.rvalid && lower.rlast
  val write_satisfy = state === s_write_resp && lower.bvalid

  // state machine
  switch(state) {
    is(s_idle) {
      when(new_request && !hit) {
        state := Mux(
          inflight_request.uncached,
          Mux(inflight_request.wr, s_write_req, s_read_req),
          Mux(
            cacheline_meta.valid && cacheline_meta.dirty,
            s_write_req,
            s_read_req
          )
        )
      }
      awvalid_enable := true.B
      wvalid_enable := true.B
      read_counter.reset()
      write_counter.reset()
    }
    is(s_read_req) {
      when(lower.arready && lower.arvalid) {
        state := s_read_resp
      }
    }
    is(s_read_resp) {
      when(lower.rvalid && lower.rlast) {
        state := s_idle
      }
    }
    is(s_write_req) {
      when(lower.awready) {
        awvalid_enable := false.B
      }
      when(lower.wready) {
        when(!inflight_request.uncached) {
          write_counter.inc()
        }
        when(lower.wlast) {
          state := s_write_resp
          wvalid_enable := false.B
        }
      }
    }
    is(s_write_resp) {
      when(lower.bvalid) {
        state := Mux(inflight_request.uncached, s_idle, s_read_req)
      }
    }
    is(s_invalidate) { // TODO
      state := s_idle
    }
  }

  val write_meta = Wire(Vec(WAY_NUM, new DCacheMeta))
  val write_data = Wire(Vec(WAY_NUM, new DCacheData))
  val need_bram_write = Wire(Bool())
  val last_index = RegInit(0.U.asTypeOf(inflight_request.addr.index))
  val last_tag = RegInit(0.U.asTypeOf(inflight_request.addr.tag))
  val last_access_index = RegInit(0.U.asTypeOf(access_index))
  val last_write_meta = RegInit(0.U.asTypeOf(write_meta(access_index)))
  val last_write_data = RegInit(0.U.asTypeOf(write_data(access_index)))
  when(need_bram_write) {
    last_index := inflight_request.addr.index
    last_tag := inflight_request.addr.tag
    last_access_index := access_index
    last_write_meta := write_meta(access_index)
    last_write_data := write_data(access_index)
  }
  write_meta := DontCare
  write_data := DontCare
  need_bram_write := false.B

  val result = Wire(UInt(DATA_WIDTH.W))
  val fetched_vec = Wire(new DCacheData)
  val new_meta = Wire(new DCacheMeta)
  result := DontCare
  fetched_vec := DontCare
  new_meta := DontCare

  val target_data = Mux(hit, cacheline_data, fetched_vec)

  when(new_request || state =/= s_idle) {
    when(hit || read_satisfy) {
      when(inflight_request.rd) {
        result := Mux(
          inflight_request.uncached,
          lower.rdata,
          target_data.data(inflight_request.addr.line_offset)
        )
        when(!inflight_request.uncached && !hit) {
          need_bram_write := true.B
          new_meta := cacheline_meta
          new_meta.valid := true.B
          new_meta.tag := inflight_request.addr.tag
          for (i <- 0 until WAY_NUM) {
            write_meta(i) := Mux(access_vec(i), new_meta, read_meta(i))
            write_data(i) := Mux(access_vec(i), target_data, read_data(i))
          }
        }
      }.elsewhen(inflight_request.wr && !inflight_request.uncached) {
        val new_data = Wire(new DCacheData)
        val offset = inflight_request.addr.word_offset << 3
        val mask = WireDefault(UInt(DATA_WIDTH.W), 0.U(DATA_WIDTH.W))
        switch(inflight_request.size) {
          is(0.U) {
            mask := Fill(8, 1.U(1.W)) << offset
          }
          is(1.U) {
            mask := Fill(16, 1.U(1.W)) << offset
          }
          is(2.U) {
            mask := Fill(32, 1.U(1.W))
          }
        }
        new_data := target_data
        new_data.data(
          inflight_request.addr.line_offset
        ) := (inflight_request.din & mask) | (target_data.data(
          inflight_request.addr.line_offset
        ) & ~mask)
        need_bram_write := true.B
        new_meta.valid := true.B
        new_meta.dirty := true.B
        new_meta.tag := inflight_request.addr.tag
        for (i <- 0 until WAY_NUM) {
          write_meta(i) := Mux(access_vec(i), new_meta, read_meta(i))
          write_data(i) := Mux(access_vec(i), new_data, read_data(i))
        }
      }
    }
  }

  // upper IO
  upper.stall_req := (new_request && !hit) || (state =/= s_idle && !read_satisfy)
  upper.dout := result

  // lower IO
  lower <> DontCare
  lower.arvalid := (state === s_read_req)
  lower.araddr := Mux(
    inflight_request.uncached,
    inflight_request.addr.asTypeOf(UInt(PHY_ADDR_W.W)),
    Cat(
      inflight_request.addr.tag,
      inflight_request.addr.index,
      0.U(OFFSET_WIDTH.W)
    )
  )
  lower.arsize := inflight_request.size
  lower.arburst := Mux(inflight_request.uncached, 0.U, 1.U)
  lower.arlen := Mux(inflight_request.uncached, 0.U, (LINE_NUM - 1).U)
  lower.rready := (state === s_read_resp)
  when(lower.rvalid && !inflight_request.uncached) {
    fetched_vec.data(read_counter.value) := lower.rdata
    read_counter.inc()
  }

  lower.awvalid := (state === s_write_req) && awvalid_enable
  lower.awaddr := Mux(
    inflight_request.uncached,
    inflight_request.addr.asTypeOf(UInt(PHY_ADDR_W.W)),
    Cat(cacheline_meta.tag, inflight_request.addr.index, 0.U(OFFSET_WIDTH.W))
  )
  lower.awsize := inflight_request.size
  lower.awburst := Mux(inflight_request.uncached, 0.U, 1.U)
  lower.awlen := Mux(inflight_request.uncached, 0.U, (LINE_NUM - 1).U)
  lower.wvalid := (state === s_write_req) && wvalid_enable
  lower.wdata := Mux(
    inflight_request.uncached,
    inflight_request.din,
    cacheline_data.data(write_counter.value)
  )
  lower.wstrb := inflight_request.strb
  lower.wlast := inflight_request.uncached || (write_counter.value === (LINE_NUM - 1).U)
  lower.bready := (state === s_write_resp)

  // BRAM IO
  for (i <- 0 until WAY_NUM) {
    bram(i).meta_we := need_bram_write && i.U === access_index
    bram(i).meta_addr := Mux(
      need_bram_write,
      inflight_request.addr.index,
      read_index
    )
    bram(i).meta_din := write_meta(i).asTypeOf(UInt(META_WIDTH.W))
    read_meta(i) := Mux(
      i.U === last_access_index && read_index === last_index && inflight_request.addr.tag === last_tag,
      last_write_meta,
      bram(i).meta_dout.asTypeOf(new DCacheMeta)
    )
    bram(i).data_we := need_bram_write && i.U === access_index
    bram(i).data_addr := Mux(
      need_bram_write,
      inflight_request.addr.index,
      read_index
    )
    bram(i).data_din := write_data(i).asTypeOf(UInt(LINE_WIDTH.W))
    read_data(i) := Mux(
      i.U === last_access_index && read_index === last_index && inflight_request.addr.tag === last_tag,
      last_write_data,
      bram(i).data_dout.asTypeOf(new DCacheData)
    )
  }
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
    path(i).io.last_stall := io.last_stall
    path(i).io.stall := io.stall
    path(i).io.flush := io.flush
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
        Module(
          new DualPortRAM(DATA_WIDTH = LINE_WIDTH, DEPTH = SET_NUM, LATENCY = 0)
        )
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
    } else { // TODO fix CombLoop here
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
