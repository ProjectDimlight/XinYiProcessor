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
  val BLOCK_INST_NUM  = 16

  // width
  val BLOCK_WIDTH  = BLOCK_INST_NUM * XLEN // each cache block has 8 instructions
  val INDEX_WIDTH  = 6
  val OFFSET_WIDTH = log2Ceil(BLOCK_INST_NUM) + 2
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
  val last_offset= RegInit(0.U(OFFSET_WIDTH.W))

  // write-enable
  val replace     = Wire(Vec(SET_ASSOCIATIVE, Bool())) // indicate replace
  val replace_vec = Wire(Vec(GROUP_NUM, UInt(SET_ASSOCIATIVE.W)))

  // BRAM and LUTRAM write-enable
  val ram_we = Wire(Vec(SET_ASSOCIATIVE, Bool())) // replace and fetch the replaced data from AXI

  // write-read data
  val rd_block_vec     = Wire(Vec(SET_ASSOCIATIVE, UInt(BLOCK_WIDTH.W))) // read data blocks
  val rd_tag_valid_vec = Wire(Vec(SET_ASSOCIATIVE, new ICacheTagValid))


  // ICache FSM state
  val s_idle :: s_axi_pending :: s_axi_wait :: s_fill :: s_valid :: Nil = Enum(5)
  val state = RegInit(s_idle)

  // hit_vec: indicate the vector of hit in 4-way ICache
  val hit_vec = Wire(Vec(SET_ASSOCIATIVE, Bool()))
  for (i <- 0 until SET_ASSOCIATIVE) {
    hit_vec(i) := rd_tag_valid_vec(i).valid && 
                  rd_tag_valid_vec(i).tag === Mux(state === s_idle, io_addr.tag, last_tag)
  }


  // hit access index
  val hit_access = hit_vec.indexWhere((x: Bool) => x === true.B)
  val hit_access_reg = RegNext(hit_access)

  // get data from the hit set and from the offset
  val rd_block = Wire(UInt(BLOCK_WIDTH.W))

  // miss
  val hit  = Wire(Bool())
  val miss = Wire(Bool())
  hit := hit_vec.exists((x: Bool) => x === true.B)
  miss := !hit

  //>>>>>>>>>>>>>>>
  //  INNER LOGIC
  //<<<<<<<<<<<<<<<

  // state reg
  val next_state = Wire(UInt(3.W))
  next_state := state

  // select replace from selection vector
  replace := replace_vec(last_index).asBools()
  for (i <- 0 until SET_ASSOCIATIVE) {
    ram_we(i) := replace(i) && state === s_fill
  }

  // burst_count: calculate how many bytes has been burst from the AXI
  val burst_count = RegInit(0.U(OFFSET_WIDTH.W))

  // buffer for received data from AXI
  val receive_buffer = RegInit(VecInit(Seq.fill(BLOCK_INST_NUM)(0.U(XLEN.W))))

  // not valid
  io.cpu_io.stall_req := next_state =/= s_idle


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


  val inst_offset_index = last_offset(OFFSET_WIDTH - 1, 3)
  // select data
  val uncached_inst = RegInit(0.U(XLEN.W))
  val uncached_data = Cat(uncached_inst, 0.U(XLEN.W))

  val cached_data = (rd_block >> (inst_offset_index << 6)) (63, 0) // always read two instructions

  io.cpu_io.data := Mux(uncached, uncached_data, cached_data)

  rd_block := rd_block_vec(hit_access_reg)

  //>>>>>>>>>>>>>>>>>
  // STATE TRANSFER
  //<<<<<<<<<<<<<<<<<

  switch(state) {
    // when FSM is idle
    is(s_idle) {
      // new request arrives
      when (io.cpu_io.rd) {
        last_index := io_addr.index
        last_tag := io_addr.tag
        last_offset := io_addr.inst_offset
        uncached := io.cpu_io.uncached
        when (io.cpu_io.uncached | miss) {
          next_state := s_axi_pending
        }
      }
    }

    // when FSM is pending, waiting for AXI ready
    is(s_axi_pending) {
      when(io.axi_io.arready) {
        next_state := s_axi_wait
        burst_count := 0.U
      }
    }

    // AXI working, wait
    is(s_axi_wait) {
      // still receiving
      when(io.axi_io.rvalid) {
        when(uncached) {
          next_state := s_valid
          uncached_inst := io.axi_io.rdata
        }.otherwise {
          receive_buffer(burst_count) := io.axi_io.rdata
          burst_count := burst_count + 1.U // increment burst count by 1

          // the last received data
          when(io.axi_io.rlast) {
            // no further data transfer, back to IDLE
            next_state := s_fill
          }
        }
      }
    }

    is (s_fill) {
      next_state := s_valid
    }

    is (s_valid) {
      next_state := s_idle
    }
  }
  state := next_state


  //>>>>>>>>>>>>>
  //  AXI LOGIC
  //<<<<<<<<<<<<<
  
  // don't care
  io.axi_io := DontCare

  io.axi_io.arvalid := state === s_axi_pending

  // calculated
  io.axi_io.arlen := Mux(io.cpu_io.uncached, 0.U, (BLOCK_INST_NUM - 1).U)
  io.axi_io.arsize := 2.U // 4 bytes per burst
  io.axi_io.arburst := Mux(io.cpu_io.uncached, 0.U, 1.U) // INCR mode
  io.axi_io.rready := state === s_axi_wait
  io.axi_io.araddr := Mux(io.cpu_io.uncached, io_addr.asUInt(),
    Cat(io_addr.tag, io_addr.index, 0.U(OFFSET_WIDTH.W))
  )



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
    data_bram.io.addra := io_addr.index
    data_bram.io.dina := receive_buffer.asUInt()

    // port 2: read
    data_bram.io.web := false.B
    data_bram.io.addrb := io_addr.index
    rd_block_vec(i) := data_bram.io.doutb

    data_bram
  }


  // generate tag bram
  def CreateTagValidLUTRAM(i: Int) = {
    var tag_valid_bram = Module(new DualPortLUTRAM(DATA_WIDTH = (new ICacheTagValid).getWidth, DEPTH = GROUP_NUM))

    tag_valid_bram.io.clk := clock
    tag_valid_bram.io.rst := reset

    // port 1: write
    tag_valid_bram.io.wea := ram_we(i)
    tag_valid_bram.io.addra := io_addr.index
    tag_valid_bram.io.dina := Cat(io_addr.tag, true.B)

    // port 2: read
    tag_valid_bram.io.addrb := io_addr.index
    rd_tag_valid_vec(i) := tag_valid_bram.io.doutb.asTypeOf(new ICacheTagValid)

    tag_valid_bram
  }
}