package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.AXIIO
import utils._
import chisel3.util.random.LFSR

object GTimer {
  def apply() = {
    val c = RegInit(0.U(64.W))
    c := c + 1.U
    c
  }
}

trait DCacheConfig {
  // predefined parameters
  val CACHE_SIZE = 16 * 1024 * 8 // 8KB
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

  override def toPrintable: Printable =
    p"addr(tag = 0x${Hexadecimal(tag)}, index = 0x${Hexadecimal(index)}, line_offset = 0x${Hexadecimal(line_offset)}, word_offset = 0x${Hexadecimal(word_offset)})"
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

object DCachePLRUPolicy extends DCacheConfig {
  def choose_victim(array: UInt): UInt = {
    if (WAY_NUM == 1) {
      1.U
    } else if (WAY_NUM == 2) {
      Cat(array(0), ~array(0))
    } else if (WAY_NUM == 4) {
      val result = Wire(UInt(WAY_NUM.W))
      when(array(0)) {
        result := Cat(array(2), ~array(2), false.B, false.B)
      }.otherwise {
        result := Cat(false.B, false.B, array(1), ~array(1))
      }
      result
    } else {
      println("WAY_NUM of", WAY_NUM, "is not supported !!!")
      1.U
    }
  }
  def update_meta(
      array: UInt,
      access_index: UInt
  ): UInt = {
    if (WAY_NUM == 1) {
      array
    } else if (WAY_NUM == 2) {
      !access_index(0)
    } else if (WAY_NUM == 4) {
      val result = WireDefault(UInt((WAY_NUM - 1).W), 0.U)
      switch(access_index) {
        is(0.U) {
          result := Cat(array(2), true.B, true.B)
        }
        is(1.U) {
          result := Cat(array(2), false.B, true.B)
        }
        is(2.U) {
          result := Cat(true.B, array(1), false.B)
        }
        is(3.U) {
          result := Cat(false.B, array(1), false.B)
        }
      }
      result
    } else {
      println("WAY_NUM of", WAY_NUM, "is not supported !!!")
      array
    }
  }
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

  override def toPrintable: Printable =
    p"rd=${rd}, wr=${wr}, uncached=${uncached}, size=${size}, strb=${strb}, addr=0x${Hexadecimal(
      addr
    )}, din=0x${Hexadecimal(din)}\ndout=0x${Hexadecimal(dout)},stall_req=${stall_req}"
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

  override def toPrintable: Printable =
    p"meta_we=${meta_we}, meta_addr=0x${Hexadecimal(meta_addr)}, meta_din=0x${Hexadecimal(
      meta_din
    )}, meta_dout=0x${Hexadecimal(meta_dout)}\ndata_we=${data_we}, data_addr=0x${Hexadecimal(
      data_addr
    )}, data_din=0x${Hexadecimal(data_din)}, data_dout=0x${Hexadecimal(data_dout)}"
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
    override def toPrintable: Printable =
      p"rd=${rd}, wr=${wr}, uncached=${uncached}, size=${size}, strb=${strb}, addr=${addr}, din=0x${Hexadecimal(din)}"
  }

  // PLRU
  val plru_records = RegInit(
    VecInit(Seq.fill(SET_NUM)(0.U(((WAY_NUM - 1).max(1)).W)))
  )

  // set alias
  val upper = io.upper
  val lower = io.lower
  val bram = io.bram

  // cache state definition
  val s_idle :: s_fetch :: s_read_req :: s_read_resp :: s_write_req :: s_write_resp :: s_invalidate :: Nil =
    Enum(7)
  val state = RegInit(s_idle)

  val new_request = !io.last_stall && (upper.rd | upper.wr) && state === s_idle
  val upper_request = Wire(new DCacheReq)
  val current_request = RegInit(0.U.asTypeOf(new DCacheReq))
  when(new_request) {
    current_request := upper_request
  }
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
    read_meta.map(m => m.tag === current_request.addr.tag)
  ).asUInt
  val hit_vec = (~invalid_vec) & tag_vec
  // val hit_index = PriorityEncoder(hit_vec)
  val hit = hit_vec.orR && !current_request.uncached

  // random replacement
  // val victim_index = if (WAY_NUM == 1) { 0.U }
  // else {
  //   Mux(
  //     invalid_vec.orR,
  //     PriorityEncoder(invalid_vec),
  //     LFSR(XLEN)(log2Ceil(WAY_NUM) - 1, 0)
  //   )
  // }
  // val victim_vec = UIntToOH(victim_index)
  val victim_vec =
    DCachePLRUPolicy.choose_victim(plru_records(current_request.addr.index))
  val access_vec = Mux(hit, hit_vec, victim_vec)
  val access_index = PriorityEncoder(access_vec)
  val cacheline_meta = read_meta(access_index)
  val cacheline_data = read_data(access_index)

  val awvalid_enable = RegInit(true.B)
  val read_counter = Counter(LINE_NUM)
  val write_counter = Counter(LINE_NUM)

  val read_satisfy = state === s_read_resp && lower.rvalid && lower.rlast
  val write_satisfy = state === s_write_resp && lower.bvalid
  val cached_satisfy = (!current_request.uncached) && read_satisfy
  val uncached_satisfy =
    current_request.uncached && (read_satisfy || write_satisfy)

  // state machine
  switch(state) {
    is(s_idle) {
      when(new_request) {
        state := Mux(
          upper_request.uncached,
          Mux(upper_request.wr, s_write_req, s_read_req),
          s_fetch
        )
      }
      awvalid_enable := true.B
      read_counter.reset()
      write_counter.reset()
    }
    is(s_fetch) {
      state := Mux(
        hit,
        s_idle,
        Mux(
          cacheline_meta.valid && cacheline_meta.dirty,
          s_write_req,
          s_read_req
        )
      )
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
        when(!current_request.uncached) {
          write_counter.inc()
        }
        when(lower.wlast) {
          state := s_write_resp
        }
      }
    }
    is(s_write_resp) {
      when(lower.bvalid) {
        state := Mux(current_request.uncached, s_idle, s_read_req)
      }
    }
    is(s_invalidate) { // TODO
      state := s_idle
    }
  }

  val write_meta = Wire(Vec(WAY_NUM, new DCacheMeta))
  val write_data = Wire(Vec(WAY_NUM, new DCacheData))
  val need_bram_write = Wire(Bool())
  val last_index = RegInit(0.U.asTypeOf(current_request.addr.index))
  val last_tag = RegInit(0.U.asTypeOf(current_request.addr.tag))
  val last_access_index = RegInit(0.U.asTypeOf(access_index))
  val last_write_meta = RegInit(0.U.asTypeOf(write_meta(access_index)))
  val last_write_data = RegInit(0.U.asTypeOf(write_data(access_index)))
  when(need_bram_write) {
    last_index := current_request.addr.index
    last_tag := current_request.addr.tag
    last_access_index := access_index
    last_write_meta := write_meta(access_index)
    last_write_data := write_data(access_index)
  }
  write_meta := DontCare
  write_data := DontCare
  need_bram_write := false.B

  val result = Wire(UInt(DATA_WIDTH.W))
  val fetched_vec_reg = RegInit(0.U.asTypeOf(new DCacheData))
  val fetched_vec = Wire(new DCacheData)
  val new_meta = Wire(new DCacheMeta)
  result := DontCare
  new_meta := DontCare
  for (i <- 0 until (LINE_NUM - 1)) {
    fetched_vec.data(i) := fetched_vec_reg.data(i)
  }
  fetched_vec.data(LINE_NUM - 1) := lower.rdata

  val target_data = Mux(hit, cacheline_data, fetched_vec)

  when(state =/= s_idle) {
    when(hit || read_satisfy) {
      when(current_request.rd) {
        result := Mux(
          current_request.uncached,
          lower.rdata,
          target_data.data(current_request.addr.line_offset)
        )
        when(!current_request.uncached) {
          // update PLRU
          plru_records(current_request.addr.index) := DCachePLRUPolicy
            .update_meta(plru_records(current_request.addr.index), access_index)
          when(!hit) {
            need_bram_write := true.B
            new_meta := cacheline_meta
            new_meta.valid := true.B
            new_meta.tag := current_request.addr.tag
            for (i <- 0 until WAY_NUM) {
              write_meta(i) := Mux(access_vec(i), new_meta, read_meta(i))
              write_data(i) := Mux(access_vec(i), target_data, read_data(i))
            }
          }
        }
      }.elsewhen(current_request.wr && !current_request.uncached) {
        val new_data = Wire(new DCacheData)
        val offset = current_request.addr.word_offset << 3
        val mask = WireDefault(UInt(DATA_WIDTH.W), 0.U(DATA_WIDTH.W))
        switch(current_request.size) {
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
          current_request.addr.line_offset
        ) := (current_request.din & mask) | (target_data.data(
          current_request.addr.line_offset
        ) & ~mask)
        need_bram_write := true.B
        new_meta.valid := true.B
        new_meta.dirty := true.B
        new_meta.tag := current_request.addr.tag
        for (i <- 0 until WAY_NUM) {
          write_meta(i) := Mux(access_vec(i), new_meta, read_meta(i))
          write_data(i) := Mux(access_vec(i), new_data, read_data(i))
        }

        // update PLRU
        plru_records(current_request.addr.index) := DCachePLRUPolicy
          .update_meta(plru_records(current_request.addr.index), access_index)
      }
    }
  }

  // upper IO
  upper.stall_req := new_request || state =/= s_idle
  upper.dout := RegNext(result)

  // lower IO
  lower <> DontCare
  lower.arvalid := (state === s_read_req)
  lower.araddr := Mux(
    current_request.uncached,
    current_request.addr.asTypeOf(UInt(PHY_ADDR_W.W)),
    Cat(
      current_request.addr.tag,
      current_request.addr.index,
      0.U(OFFSET_WIDTH.W)
    )
  )
  lower.arsize := Mux(current_request.uncached, current_request.size, 2.U)
  lower.arburst := Mux(current_request.uncached, 0.U, 1.U)
  lower.arlen := Mux(current_request.uncached, 0.U, (LINE_NUM - 1).U)
  lower.rready := (state === s_read_resp)
  when(lower.rvalid && !current_request.uncached) {
    fetched_vec_reg.data(read_counter.value) := lower.rdata
    read_counter.inc()
  }

  lower.awvalid := (state === s_write_req) && awvalid_enable
  lower.awaddr := Mux(
    current_request.uncached,
    current_request.addr.asTypeOf(UInt(PHY_ADDR_W.W)),
    Cat(cacheline_meta.tag, current_request.addr.index, 0.U(OFFSET_WIDTH.W))
  )
  lower.awsize := Mux(current_request.uncached, current_request.size, 2.U)
  lower.awburst := Mux(current_request.uncached, 0.U, 1.U)
  lower.awlen := Mux(current_request.uncached, 0.U, (LINE_NUM - 1).U)
  lower.wvalid := (state === s_write_req)
  lower.wdata := Mux(
    current_request.uncached,
    current_request.din,
    cacheline_data.data(write_counter.value)
  )
  lower.wstrb := Mux(current_request.uncached, current_request.strb, 0xf.U)
  lower.wlast := current_request.uncached || (write_counter.value === (LINE_NUM - 1).U)
  lower.bready := (state === s_write_resp)

  // BRAM IO
  for (i <- 0 until WAY_NUM) {
    bram(i).meta_we := need_bram_write && i.U === access_index
    bram(i).meta_addr := inflight_request.addr.index
    bram(i).meta_din := write_meta(i).asTypeOf(UInt(META_WIDTH.W))
    read_meta(i) := bram(i).meta_dout.asTypeOf(new DCacheMeta)
    // read_meta(i) := Mux(
    //   i.U === last_access_index && read_index === last_index && inflight_request.addr.tag === last_tag,
    //   last_write_meta,
    //   bram(i).meta_dout.asTypeOf(new DCacheMeta)
    // )
    bram(i).data_we := need_bram_write && i.U === access_index
    bram(i).data_addr := inflight_request.addr.index
    bram(i).data_din := write_data(i).asTypeOf(UInt(LINE_WIDTH.W))
    read_data(i) := bram(i).data_dout.asTypeOf(new DCacheData)
    // read_data(i) := Mux(
    //   i.U === last_access_index && read_index === last_index && inflight_request.addr.tag === last_tag,
    //   last_write_data,
    //   bram(i).data_dout.asTypeOf(new DCacheData)
    // )
  }

  // printf(p"[${GTimer()}]: ${NAME} Debug Info----------\n")
  // printf(p"----------${NAME} inflight_request----------\n")
  // printf(p"${inflight_request}\n")
  // printf(p"state=${state}, new_request=${new_request}\n")
  // printf(
  //   p"read_index=${read_index}, read_meta=${read_meta}, read_data=${read_data}\n"
  // )
  // printf(
  //   p"invalid_vec=${invalid_vec}, tag_vec=${tag_vec}, hit_vec=${hit_vec}, hit=${hit}\n"
  // )
  // printf(
  //   p"victim_vec=${victim_vec}, access_vec=${access_vec}, access_index=${access_index}\n"
  // )
  // printf(
  //   p"cacheline_meta=${cacheline_meta}, cacheline_data=${cacheline_data}\n"
  // )
  // printf(
  //   p"awvalid_enable=${awvalid_enable}, read_counter=${read_counter.value}, write_counter=${write_counter.value}\n"
  // )
  // printf(p"read_satisfy=${read_satisfy}, write_satisfy=${write_satisfy}\n")
  // printf(p"write_meta=${write_meta}, write_data=${write_data}\n")
  // printf(p"fetched_vec=${fetched_vec}, target_data=${target_data}\n")
  // printf(p"need_bram_write=${need_bram_write}\n")
  // printf(p"----------${NAME} io.upper----------\n")
  // printf(p"${io.upper}\n")
  // printf(p"----------${NAME} io.lower----------\n")
  // printf(p"aw: valid=${io.lower.awvalid}, ready=${io.lower.awready}, addr=0x${Hexadecimal(io.lower.awaddr)}, len=${io.lower.awlen}, size=${io.lower.awsize}, burst=${io.lower.awburst}\n")
  // printf(p"w: valid=${io.lower.wvalid}, ready=${io.lower.wready}, data=0x${Hexadecimal(io.lower.wdata)}, strb=${io.lower.wstrb}, last=${io.lower.wlast}\n")
  // printf(p"b: valid=${io.lower.bvalid}, ready=${io.lower.bready}, resp=${io.lower.bresp}\n")
  // printf(p"ar: valid=${io.lower.arvalid}, ready=${io.lower.arready}, addr=0x${Hexadecimal(io.lower.araddr)}, len=${io.lower.arlen}, size=${io.lower.arsize}, burst=${io.lower.arburst}\n")
  // printf(p"r: valid=${io.lower.rvalid}, ready=${io.lower.rready}, resp=${io.lower.rresp}, last=${io.lower.rlast}\n")
  // printf(p"----------${NAME} io.bram----------\n")
  // printf(p"${io.bram}\n")
  // printf(p"------------------------------\n")
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

class newBRAMBundle extends Bundle with DCacheConfig {
  val meta = UInt(META_WIDTH.W)
  val data = UInt(LINE_WIDTH.W)
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
      if (LSU_PATH_NUM == 1) {
        val storage_width = (new newBRAMBundle).getWidth
        val storage = List.fill(WAY_NUM)(
          Module(
            new SinglePortRAM(
              DATA_WIDTH = storage_width,
              DEPTH = SET_NUM,
              LATENCY = 1
            )
          )
        )
        for (i <- 0 until WAY_NUM) {
          val bundle_in = Wire(new newBRAMBundle)
          val bundle_out = storage(i).io.dout.asTypeOf(new newBRAMBundle)
          bundle_in.meta := path(0).io.bram(i).meta_din
          bundle_in.data := path(0).io.bram(i).data_din
          storage(i).io.clk := clock
          storage(i).io.rst := reset
          storage(i).io.we := path(0).io.bram(i).meta_we
          storage(i).io.addr := path(0).io.bram(i).meta_addr
          storage(i).io.din := bundle_in.asTypeOf(UInt(storage_width.W))
          path(0).io.bram(i).meta_dout := bundle_out.meta
          path(0).io.bram(i).data_dout := bundle_out.data
      }
        // val meta = List.fill(WAY_NUM)(
        //   Module(
        //     new SinglePortRAM(
        //       DATA_WIDTH = META_WIDTH,
        //       DEPTH = SET_NUM,
        //       LATENCY = 1
        //     )
        //   )
        // )
        // val data = List.fill(WAY_NUM)(
        //   Module(
        //     new SinglePortRAM(
        //       DATA_WIDTH = LINE_WIDTH,
        //       DEPTH = SET_NUM,
        //       LATENCY = 1
        //     )
        //   )
        // )
        // for (i <- 0 until WAY_NUM) {
        //   meta(i).io <> DontCare
        //   meta(i).io.clk := clock
        //   meta(i).io.rst := reset
        //   meta(i).io.we := path(0).io.bram(i).meta_we
        //   meta(i).io.addr := path(0).io.bram(i).meta_addr
        //   meta(i).io.din := path(0).io.bram(i).meta_din
        //   path(0).io.bram(i).meta_dout := meta(i).io.dout

        //   data(i).io <> DontCare
        //   data(i).io.clk := clock
        //   data(i).io.rst := reset
        //   data(i).io.we := path(0).io.bram(i).data_we
        //   data(i).io.addr := path(0).io.bram(i).data_addr
        //   data(i).io.din := path(0).io.bram(i).data_din
        //   path(0).io.bram(i).data_dout := data(i).io.dout
        // }
      } else {
        val meta = List.fill(WAY_NUM)(
          Module(
            new DualPortLUTRAM(
              DATA_WIDTH = META_WIDTH,
              DEPTH = SET_NUM,
              LATENCY = 1
            )
          )
        )
        val data = List.fill(WAY_NUM)(
          Module(
            new DualPortBRAM(
              DATA_WIDTH = LINE_WIDTH,
              DEPTH = SET_NUM,
              LATENCY = 1
            )
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
      }
    } else {
      val meta = List.fill(WAY_NUM)(SyncReadMem(SET_NUM, UInt(META_WIDTH.W)))
      val data = List.fill(WAY_NUM)(SyncReadMem(SET_NUM, UInt(LINE_WIDTH.W)))
      for (i <- 0 until WAY_NUM) {
        for (j <- 0 until LSU_PATH_NUM) {
          val bram = path(j).io.bram(i)
          bram.meta_dout := meta(i).read(bram.meta_addr, !bram.meta_we)
          when(bram.meta_we) {
            meta(i).write(bram.meta_addr, bram.meta_din)
          }
          bram.data_dout := data(i).read(bram.data_addr, !bram.data_we)
          when(bram.data_we) {
            data(i).write(bram.data_addr, bram.data_din)
          }
        }
      }
      when(reset.asBool()) {
        for (i <- 0 until WAY_NUM) {
          for (j <- 0 until SET_NUM) {
            meta(i).write(j.U, 0.U)
            data(i).write(j.U, 0.U)
          }
        }
      }
    }
  }
}
