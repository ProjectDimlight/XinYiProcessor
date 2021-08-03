package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.AXIIO
import config.config._

// TODO flush cache

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//          ICache
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

trait ICacheConfig {
  // basic attributes
  val SET_ASSOCIATIVE = 2
  val BLOCK_INST_NUM  = 8

  // width
  val BLOCK_WIDTH  = BLOCK_INST_NUM * XLEN // each cache block has 8 instructions
  val INDEX_WIDTH  = 5
  val OFFSET_WIDTH = log2Ceil(BLOCK_WIDTH >> 3)
  val TAG_WIDTH    = XLEN - INDEX_WIDTH - OFFSET_WIDTH

  // derived number
  val GROUP_NUM = 1 << INDEX_WIDTH
}


// ICache line
// +----------+---------+-------+
// |   block  |   tag   |   V   |
// +----------+---------+-------+

// tag and valid:
//    stored in LUTRAM
class ICacheTagValid extends Bundle with ICacheConfig {
  val tag   = UInt(TAG_WIDTH.W)
  val valid = Bool()
}

// ICache address
// +----------+---------+----------------+----------------+
// |   tag    |  group  |   set_offset   |  inst_offset   |
// +----------+---------+----------------+----------------+
class ICacheAddr extends Bundle with ICacheConfig {
  val tag         = UInt(TAG_WIDTH.W)
  val index       = UInt(INDEX_WIDTH.W)
  val inst_offset = UInt(OFFSET_WIDTH.W)
}


class ICacheCPUIO extends Bundle {
  val data      = Output(UInt((2 * XLEN).W)) // double width
  val addr      = Input(UInt(XLEN.W)) // address
  val flush     = Input(Bool())
  val rd        = Input(Bool()) // read request
  val stall_req = Output(Bool()) // stall
  val uncached  = Input(Bool()) // uncacheable
}


// ICache module
class ICache extends Module with ICacheConfig {
  val io = IO(new Bundle {
    val cpu_io = new ICacheCPUIO
    val axi_io = new AXIIO
  })

  //>>>>>>>>>>>>>>>>>>>>>>>
  //  ICache Cacheability
  //<<<<<<<<<<<<<<<<<<<<<<<
  val uncached = RegInit(false.B)


  //>>>>>>>>>>>>>>>>>>>
  //  ICache Metadata
  //<<<<<<<<<<<<<<<<<<<
  //  val io_group_index = io.cpu_io.addr.index(INDEX_WIDTH - log2Ceil(SET_ASSOCIATIVE) - 1, 0) // get the res group index from cpu_io
  val io_addr    = io.cpu_io.addr.asTypeOf(new ICacheAddr)
  val last_index = RegInit(0.U(INDEX_WIDTH.W)) // record the last req group index
  val last_tag   = RegInit(0.U(TAG_WIDTH.W)) // record the last req tag
  val last_hit   = RegInit(false.B)

  // write-enable
  val replace     = Wire(Vec(SET_ASSOCIATIVE, Bool())) // indicate replace
  val replace_vec = Wire(Vec(GROUP_NUM, UInt(SET_ASSOCIATIVE.W)))

  // BRAM and LUTRAM write-enable
  val ram_we = Wire(Vec(SET_ASSOCIATIVE, Bool())) // replace and fetch the replaced data from AXI

  // write-read data
  val rd_block_vec     = Wire(Vec(SET_ASSOCIATIVE, UInt(BLOCK_WIDTH.W))) // read data blocks
  val rd_tag_valid_vec = Wire(Vec(SET_ASSOCIATIVE, new ICacheTagValid))


  // hit_vec: indicate the vector of hit in 4-way ICache
  val hit_vec = Wire(Vec(SET_ASSOCIATIVE, Bool()))
  for (i <- 0 until SET_ASSOCIATIVE) {
    hit_vec(i) := rd_tag_valid_vec(i).valid && rd_tag_valid_vec(i).tag === io_addr.tag
  }


  // hit access index
  val hit_access = hit_vec.indexWhere((x: Bool) => x === true.B)


  // get data from the hit set and from the offset
  val l0_block = RegInit(0.U(BLOCK_WIDTH.W)) // block data
  val rd_block = Wire(UInt(BLOCK_WIDTH.W))

  val rd_tag_valid = rd_tag_valid_vec(hit_access)

  val cached_miss = io_addr.index =/= last_index || io_addr.tag =/= last_tag

  // miss
  val hit  = Wire(Bool())
  val miss = Wire(Bool())
  hit := hit_vec.exists((x: Bool) => x === true.B)
  miss := !hit

  //>>>>>>>>>>>>>>>
  //  INNER LOGIC
  //<<<<<<<<<<<<<<<

  // ICache FSM state
  val s_idle :: s_fetch :: s_axi_pending :: s_axi_wait :: s_fill :: s_valid :: Nil = Enum(6)

  // state reg
  val state = RegInit(s_idle)

  // select replace from selection vector
  replace := replace_vec(io_addr.index).asBools()
  for (i <- 0 until SET_ASSOCIATIVE) {
    ram_we(i) := replace(i) && state === s_fill
  }

  // burst_count: calculate how many bytes has been burst from the AXI
  val burst_count = RegInit(0.U(OFFSET_WIDTH.W))

  // buffer for received data from AXI
  val receive_buffer = RegInit(VecInit(Seq.fill(BLOCK_INST_NUM)(0.U(XLEN.W))))


  val uncache_valid = Wire(Bool())
  uncache_valid := false.B

  // not valid
  io.cpu_io.stall_req := !uncache_valid && (state =/= s_idle || io.cpu_io.uncached || cached_miss)


  //>>>>>>>>>>>>
  //  RAM DATA
  //<<<<<<<<<<<<

  // PLRU records
  for (i <- 0 until GROUP_NUM) {
    val plru_record = CreatePLRU(i.asUInt())
  }


  // data and tag-valid brams
  for (i <- 0 until SET_ASSOCIATIVE) {
    val data_bram        = CreateDataBRAM(i)
    val tag_valid_lutram = CreateTagValidLUTRAM(i)
  }


  val inst_offset_index = io_addr.inst_offset(4, 2)

  // select data
  val uncached_data = Cat(0.U(XLEN.W), io.axi_io.rdata)

  val cached_data = Mux(inst_offset_index(0),
    Cat(0.U(XLEN.W), (rd_block >> (inst_offset_index << 5)) (31, 0)), // read single instruction
    (rd_block >> (inst_offset_index(2, 1) << 6)) (63, 0)) // read two instructions

  io.cpu_io.data := Mux(uncached, uncached_data, cached_data)

  // forward bram block to reduce hit latency
  rd_block := Mux(last_hit, rd_block_vec(hit_access), l0_block)


  //>>>>>>>>>>>>>>>>>
  // STATE TRANSFER
  //<<<<<<<<<<<<<<<<<

  switch(state) {
    // when FSM is idle
    is(s_idle) {
      // new request arrives
      when(io.cpu_io.rd && io.cpu_io.uncached) {
        uncached := true.B
        state := s_axi_pending
      }
      .elsewhen(io.cpu_io.rd && cached_miss) { // read request and cache miss
        state := s_fetch
        last_index := io_addr.index
        last_tag := io_addr.tag
        last_hit := false.B
      }
    }

    // when FSM fetches metadata from BRAM and LUTRAM
    is(s_fetch) {
      when(hit) { // when hit, back to idle
        state := s_idle
        l0_block := rd_block_vec(hit_access)
        last_hit := true.B
      }.otherwise { // when miss, fetch data from AXI
        state := s_axi_pending
      }
    }

    // when FSM is pending, waiting for AXI ready
    is(s_axi_pending) {
      when(io.axi_io.arready) {
        state := s_axi_wait
        burst_count := 0.U
      }
    }

    // AXI working, wait
    is(s_axi_wait) {
      // still receiving
      when(io.axi_io.rvalid) {
        when(uncached) {
          state := s_idle
          uncache_valid := true.B
          uncached := false.B
        }.otherwise {
          receive_buffer(burst_count) := io.axi_io.rdata
          burst_count := burst_count + 1.U // increment burst count by 1

          // the last received data
          when(io.axi_io.rlast) {
            // no further data transfer, back to IDLE
            state := s_fill
            l0_block := Cat(io.axi_io.rdata, receive_buffer.asUInt()(BLOCK_WIDTH - XLEN - 1, 0))
          }
        }
      }
    }

    is(s_fill) {
      state := s_idle
    }

    is (s_valid) {
      state := s_idle
    }
  }


  //>>>>>>>>>>>>>
  //  AXI LOGIC
  //<<<<<<<<<<<<<
  io.axi_io.arvalid := state === s_axi_pending

  // calculated
  io.axi_io.arlen := (BLOCK_INST_NUM - 1).U
  io.axi_io.arsize := 2.U // 4 bytes per burst
  io.axi_io.arburst := 1.U // INCR mode
  io.axi_io.rready := state === s_axi_wait
  io.axi_io.araddr := Cat(io_addr.tag, io_addr.index, 0.U(OFFSET_WIDTH.W))

  // zeros
  io.axi_io.arid := 0.U
  io.axi_io.arprot := 0.U
  io.axi_io.arcache := 0.U
  io.axi_io.arlock := 0.U

  io.axi_io.awid := 0.U
  io.axi_io.awlen := 0.U
  io.axi_io.awaddr := 0.U
  io.axi_io.awlock := 0.U
  io.axi_io.awvalid := 0.U
  io.axi_io.awsize := 0.U
  io.axi_io.awcache := 0.U
  io.axi_io.awburst := 0.U
  io.axi_io.awprot := 0.U

  io.axi_io.wid := 0.U
  io.axi_io.wvalid := 0.U
  io.axi_io.wstrb := 0.U
  io.axi_io.wlast := 0.U
  io.axi_io.wdata := 0.U

  io.axi_io.bready := 0.U



  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //  FUNCTIONAL CREATING MODULES
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  def CreatePLRU(i: UInt) = {
    var plru = Module(new PLRU(SET_ASSOCIATIVE))

    plru.io.flush := io.cpu_io.flush
    plru.io.update := hit && i === io_addr.index // hit and match index, update when hit
    plru.io.update_index := hit_access
    replace_vec(i) := plru.io.replace_vec.asUInt()

    plru
  }


  // generate data bram
  def CreateDataBRAM(i: Int) = {
    var data_bram = Module(new DualPortBRAM(DATA_WIDTH = BLOCK_WIDTH, DEPTH = GROUP_NUM))

    data_bram.io.clk := clock
    data_bram.io.rst := reset

    // port 1: write
    data_bram.io.wea := ram_we(i)
    data_bram.io.addra := last_index
    data_bram.io.dina := receive_buffer.asUInt()

    // port 2: read
    data_bram.io.web := false.B
    data_bram.io.addrb := last_index
    rd_block_vec(i) := data_bram.io.doutb

    data_bram
  }


  // generate tag bram
  def CreateTagValidLUTRAM(i: Int) = {
    var tag_valid_bram = Module(new DualPortLUTRAM(DATA_WIDTH = (new ICacheTagValid).getWidth,
      LATENCY = 0, DEPTH = GROUP_NUM))

    tag_valid_bram.io.clk := clock
    tag_valid_bram.io.rst := reset

    // port 1: write
    tag_valid_bram.io.wea := ram_we(i)
    tag_valid_bram.io.addra := last_index
    tag_valid_bram.io.dina := Cat(io_addr.tag, true.B)

    // port 2: read
    tag_valid_bram.io.addrb := last_index
    rd_tag_valid_vec(i) := tag_valid_bram.io.doutb.asTypeOf(new ICacheTagValid)

    tag_valid_bram
  }
}