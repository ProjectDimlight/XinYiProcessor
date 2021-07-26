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
  val SET_ASSOCIATIVE = 4
  val BLOCK_INST_NUM  = 8

  // width
  val BLOCK_WIDTH       = BLOCK_INST_NUM * XLEN // each cache block has 8 instructions
  val INDEX_WIDTH       = 3
  val SET_OFFSET_WIDTH  = log2Ceil(SET_ASSOCIATIVE)
  val GROUP_WIDTH       = INDEX_WIDTH - SET_OFFSET_WIDTH
  val INST_OFFSET_WIDTH = log2Ceil(BLOCK_WIDTH >> 3)
  val TAG_WIDTH         = XLEN - INDEX_WIDTH - INST_OFFSET_WIDTH

  // derived number
  val GROUP_NUM = 1 << GROUP_WIDTH
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
  val group       = UInt(GROUP_WIDTH.W)
  val set_offset  = UInt(SET_OFFSET_WIDTH.W)
  val inst_offset = UInt(INST_OFFSET_WIDTH.W)
}


class ICacheCPUIO extends Bundle {
  val data      = Output(UInt((2 * XLEN).W)) // double width
  val addr      = Input(new ICacheAddr) // address
  val flush     = Input(Bool())
  val rd        = Input(Bool()) // read request
  val stall_req = Output(Bool()) // stall
}


// ICache module
class ICache extends Module with ICacheConfig {
  val io = IO(new Bundle {
    val cpu_io = new ICacheCPUIO
    val axi_io = new AXIIO
  })


  //>>>>>>>>>>>>>>>>>>>
  //  ICache Metadata
  //<<<<<<<<<<<<<<<<<<<


  // write-enable
  val replace     = Wire(Vec(SET_ASSOCIATIVE, Bool())) // indicate replace
  val replace_vec = Wire(Vec(GROUP_NUM, UInt(SET_ASSOCIATIVE.W)))

  // BRAM and LUTRAM write-enable
  val ram_we = Wire(Vec(SET_ASSOCIATIVE, Bool())) // replace and fetch the replaced data from AXI

  // write-read addr
  var tmp_group = RegInit(0.U(INDEX_WIDTH.W)) // tmp reg for address
  val wr_index  = Wire(UInt(INDEX_WIDTH.W)) // store write index if cache miss
  val rd_index  = Wire(UInt(INDEX_WIDTH.W)) // store read index if cache miss


  // write-read data
  val rd_block_vec = Wire(Vec(SET_ASSOCIATIVE, UInt(BLOCK_WIDTH.W))) // read data blocks
  val rd_block     = Wire(UInt(BLOCK_WIDTH.W)) // single read block

  val tag_valid_rd_data = Wire(Vec(SET_ASSOCIATIVE, new ICacheTagValid))


  // hit_vec: indicate the vector of hit in 4-way ICache
  val hit_vec = Wire(Vec(SET_ASSOCIATIVE, Bool()))
  for (i <- 0 until SET_ASSOCIATIVE) {
    hit_vec(i) := tag_valid_rd_data(i).valid && tag_valid_rd_data(i).tag === io.cpu_io.addr.tag
  }

  // miss
  val hit  = Wire(Bool())
  val miss = Wire(Bool())
  hit := hit_vec.exists((x: Bool) => x === true.B)
  miss := ~hit

  // hit access index
  val hit_access = Wire(UInt(log2Ceil(SET_ASSOCIATIVE).W))
  hit_access := hit_vec.indexWhere((x: Bool) => x === true.B)


  //>>>>>>>>>>>>>>>
  //  INNER LOGIC
  //<<<<<<<<<<<<<<<

  // ICache FSM state
  val s_idle :: s_axi_pending :: s_axi_wait :: Nil = Enum(3)

  val state = RegInit(s_idle)

  // stash index
  rd_index := Mux(state === s_idle, io.cpu_io.addr.group, tmp_group)
  wr_index := tmp_group


  // select replace from selection vector
  replace := replace_vec(io.cpu_io.addr.group).asBools()
  for (i <- 0 until SET_ASSOCIATIVE) {
    ram_we(i) := replace(i) && state === s_axi_wait && io.axi_io.rvalid && io.axi_io.rlast
  }


  // burst_count: calculate how many bytes has been burst from the AXI
  val burst_count = RegInit(0.U(INST_OFFSET_WIDTH.W))

  // buffer for received data from AXI
  val receive_buffer = RegInit(VecInit(Seq.fill(BLOCK_INST_NUM)(0.U(XLEN.W))))


  // not valid
  io.cpu_io.stall_req := state =/= s_idle


  //>>>>>>>>>>>>
  //  RAM DATA
  //<<<<<<<<<<<<

  // PLRU records
  for (i <- 0 until GROUP_NUM) {
    val plru_record = CreatePLRU(i.asUInt())
  }




  // data and tag-valid brams
  for (i <- 0 until SET_ASSOCIATIVE) {
    val data_bram      = CreateDataBRAM(i)
    val tag_width_bram = CreateTagValidBRAM(i)
  }



  // get data from the hit set and from the offset
  rd_block := rd_block_vec(hit_access)

  val inst_offset_index = io.cpu_io.addr.inst_offset(4, 2)

  // select data
  io.cpu_io.data := Mux(inst_offset_index(0),
    Cat((rd_block >> (inst_offset_index << 2)) (31, 0), 0.U(XLEN.W)), // read single instruction
    (rd_block >> (inst_offset_index(2, 1) << 3)) (63, 0)) // read two instructions




  //>>>>>>>>>>>>>>>>>
  // STATE TRANSFER
  //<<<<<<<<<<<<<<<<<

  switch(state) {
    // when FSM is idle
    is(s_idle) {
      when(io.cpu_io.rd && miss) { // read request and cache miss
        state := s_axi_pending // wait for the AXI ready
        tmp_group := io.cpu_io.addr.group // stash the access index
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
        receive_buffer(burst_count) := io.axi_io.rdata

        burst_count := burst_count + 1.U // increment burst count by 1

        // the last received data
        when(io.axi_io.rlast) {
          // no further data transfer, back to IDLE
          state := s_idle
        }
      }
    }
  }


  //>>>>>>>>>>>>>
  //  AXI LOGIC
  //<<<<<<<<<<<<<
  io.axi_io.arvalid := state === s_axi_pending


  // calculated
  io.axi_io.arlen := (BLOCK_INST_NUM - 1).U
  io.axi_io.arsize := 4.U // 4 bytes per burst
  io.axi_io.arburst := 1.U // INCR mode
  io.axi_io.rready := state === s_axi_wait
  io.axi_io.araddr := Cat(io.cpu_io.addr.tag, io.cpu_io.addr.group, io.cpu_io.addr.set_offset, 0.U(INST_OFFSET_WIDTH.W))

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
    var plru = Module(new PLRU())

    plru.io.flush := io.cpu_io.flush
    plru.io.update := hit && i === io.cpu_io.addr.group // hit and match index, update when hit
    plru.io.update_index := hit_access
    replace_vec(i) := plru.io.replace_vec.asUInt()

    plru
  }


  // generate data bram
  def CreateDataBRAM(i: Int) = {
    var data_bram = Module(new DualPortRAM(DATA_WIDTH = BLOCK_WIDTH))

    data_bram.io.clk := clock
    data_bram.io.rst := reset

    // port 1: write
    data_bram.io.wea := ram_we(i)
    data_bram.io.addra := wr_index
    data_bram.io.dina := receive_buffer.asUInt()

    // port 2: read
    data_bram.io.web := false.B
    data_bram.io.addra := rd_index
    rd_block_vec(i) := data_bram.io.doutb

    data_bram
  }


  // generate tag bram
  def CreateTagValidBRAM(i: Int) = {
    var tag_valid_bram = Module(new DualPortLUTRAM(DATA_WIDTH = (new ICacheTagValid).getWidth))

    tag_valid_bram.io.clk := clock
    tag_valid_bram.io.rst := reset

    // port 1: write
    tag_valid_bram.io.wea := ram_we(i)
    tag_valid_bram.io.addra := wr_index
    tag_valid_bram.io.dina := Cat(io.cpu_io.addr.tag, true.B)

    // port 2: read
    tag_valid_bram.io.addrb := rd_index
    tag_valid_rd_data(i) := tag_valid_bram.io.doutb.asTypeOf(new ICacheTagValid)

    tag_valid_bram
  }
}