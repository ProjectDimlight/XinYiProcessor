package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.AXIIO
import config.config._


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//          ICache
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

trait ICacheConfig {
  // basic attributes
  val SET_ASSOCIATIVE = 4
  val BLOCK_INST_NUM  = 8

  // width
  val BLOCK_WIDTH  = BLOCK_INST_NUM * XLEN // each cache block has 8 instructions
  val TAG_WIDTH    = 20
  val OFFSET_WIDTH = log2Ceil(BLOCK_WIDTH >> 3)
  val INDEX_WIDTH  = XLEN - TAG_WIDTH - OFFSET_WIDTH

  // derived number
  val GROUP_NUM = 1 << (INDEX_WIDTH - log2Ceil(SET_ASSOCIATIVE))
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

class ICacheAddr extends Bundle with ICacheConfig {
  val tag    = UInt(TAG_WIDTH.W)
  val index  = UInt(INDEX_WIDTH.W)
  val offset = UInt(OFFSET_WIDTH.W)
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


  // write-read addr
  val wr_addr = RegInit(0.U(INDEX_WIDTH.W))
  val rd_addr = RegInit(0.U(INDEX_WIDTH.W))

  // write-read data
  val wr_block     = Wire(UInt(BLOCK_WIDTH.W)) // single write data
  val rd_block_vec = Wire(Vec(SET_ASSOCIATIVE, UInt(BLOCK_WIDTH.W))) // read data blocks
  val rd_block     = Wire(UInt(BLOCK_WIDTH.W)) // single read block

  val tag_valid_wr_data = Wire(Vec(SET_ASSOCIATIVE, new ICacheTagValid))
  val tag_valid_rd_data = Wire(Vec(SET_ASSOCIATIVE, new ICacheTagValid))

  // TODO to combinational logic
  val plru_update_reg = RegInit(false.B)

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

  // PLRU records
  for (i <- 0 until GROUP_NUM) {
    val plru_record = CreatePLRU(i.asUInt())
  }

  // select replace from selection vector
  replace := replace_vec(io.cpu_io.addr.index).asBools()

  // data and tag-valid brams
  for (i <- 0 until SET_ASSOCIATIVE) {
    val data_bram      = CreateDataBRAM(i)
    val tag_width_bram = CreateTagValidBRAM(i)
  }



  // get data from the hit set and from the offset
  rd_block := rd_block_vec(hit_access)

  val inst_offset_index = io.cpu_io.addr.offset(4, 2)

  // select data
  io.cpu_io.data := Mux(inst_offset_index(0),
                        Cat((rd_block >> (inst_offset_index << 2)) (31, 0), 0.U(XLEN.W)), // read single instruction
                        (rd_block >> (inst_offset_index(2, 1) << 3)) (63, 0)) // read two instructions

  //>>>>>>>>>>>>>
  //  ICache FSM
  //<<<<<<<<<<<<<

  // ICache FSM state
  val s_idle :: s_axi_pending :: s_axi_wait :: Nil = Enum(3)

  val state = RegInit(s_idle)




  //>>>>>>>>>>>>>>>
  //  INNER LOGIC
  //<<<<<<<<<<<<<<<

  // burst_count: calculate how many bytes has been burst from the AXI
  val burst_count = RegInit(0.U(OFFSET_WIDTH.W))


  // not valid
  io.cpu_io.stall_req := state =/= s_idle



  //>>>>>>>>>>>>>>>>>
  // STATE TRANSFER
  //<<<<<<<<<<<<<<<<<
  when(state === s_idle) {
    when(io.cpu_io.rd && miss) { // read request and cache miss
      state := s_axi_pending // wait for the AXI ready
    }
  }

  when(state === s_axi_pending) {
    when(io.axi_io.arready) {
      state := s_axi_wait

      burst_count := 0.U
    }
  }

  when(state === s_axi_wait) {
    when(io.axi_io.rvalid && io.axi_io.rlast) {
      // no further data transfer, back to IDLE
      state := s_refill

      burst_count := burst_count + 1.U // increment burst count by 1
    }
  }


  //>>>>>>>>>>>>>
  //  AXI LOGIC
  //<<<<<<<<<<<<<
  io.axi_io.arvalid := state === s_axi_pending


  // calculated
  // TODO io.axi_io.arlen := (LINE_WIDTH / 32 - 1).U
  io.axi_io.arsize := 4.U // 4 bytes per burst
  io.axi_io.arburst := 1.U // INCR mode


  // zeros
  io.axi_io.arid := 0.U
  io.axi_io.awid := 0.U
  io.axi_io.wid := 0.U

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //  FUNCTIONAL CREATING MODULES
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  def CreatePLRU(i: UInt) = {
    var plru = Module(new PLRU())

    plru.io.flush := io.cpu_io.flush
    plru.io.update := hit && i === io.cpu_io.addr.index // hit and match index
    plru.io.update_index := hit_access // update when hit
    replace_vec(i) := plru.io.replace_vec.asUInt()

    plru
  }


  // generate data bram
  def CreateDataBRAM(i: Int) = {
    var data_bram = Module(new DualPortRAM(DATA_WIDTH = BLOCK_WIDTH))

    data_bram.io.clk := clock
    data_bram.io.rst := reset

    // port 1: write
    data_bram.io.wea := replace(i)
    data_bram.io.addra := wr_addr
    data_bram.io.dina := wr_block

    // port 2: read
    data_bram.io.web := false.B
    data_bram.io.addra := rd_addr
    rd_block_vec(i) := data_bram.io.doutb

    data_bram
  }


  // generate tag bram
  def CreateTagValidBRAM(i: Int) = {
    var tag_valid_bram = Module(new DualPortLUTRAM(DATA_WIDTH = (new ICacheTagValid).getWidth))

    tag_valid_bram.io.clk := clock
    tag_valid_bram.io.rst := reset

    // port 1: write
    tag_valid_bram.io.wea := replace(i)
    tag_valid_bram.io.addra := wr_addr
    tag_valid_bram.io.dina := tag_valid_wr_data(i).asUInt()

    // port 2: read
    tag_valid_bram.io.addrb := rd_addr
    tag_valid_rd_data(i) := tag_valid_bram.io.doutb.asTypeOf(new ICacheTagValid)

    tag_valid_bram
  }

}
