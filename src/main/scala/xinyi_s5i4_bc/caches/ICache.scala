package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import xinyi_s5i4_bc.AXIIO
import config.config._


//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//          ICache
//    It takes two instructions
//  at each fetch.
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

trait ICacheConfig {
  val TAG_WIDTH       = 22
  val INDEX_WIDTH     = XLEN - TAG_WIDTH
  val ENTRY_NUM       = 1 << INDEX_WIDTH
  val SET_ASSOCIATIVE = 4
}


// ICache line
// +---------+---------+-------+
// |   data  |   tag   |   V   |
// +---------+---------+-------+
class ICacheLine extends Bundle with ICacheConfig {
  val data = UInt(XLEN.W)
  val tag  = UInt(TAG_WIDTH.W)
}

class ICacheAddr extends Bundle {
  val index = UInt()
  val tag   = UInt()
}


class ICacheCPUIO extends Bundle {
  val data      = Output(UInt((2 * XLEN).W)) // double width
  val addr      = Input(UInt(XLEN.W)) // address
  val flush     = Input(Bool())
  val rd        = Input(Bool()) // read request
  val stall_req = Output(Bool()) // stall
}


class ICache extends Module with ICacheConfig {
  val io = IO(new Bundle {
    val cpu_io = new ICacheCPUIO
    val axi_io = new AXIIO
  })

  //>>>>>>>>>>>>>>>>>>>
  //  ICache Metadata
  //<<<<<<<<<<<<<<<<<<<
  val cache_entries = Mem(ENTRY_NUM, new ICacheLine)
  val valid_entries = Reg(Vec(ENTRY_NUM, Bool()))

  when(reset.asBool() || io.cpu_io.flush) {
    for (i <- 0 until ENTRY_NUM) {
      valid_entries(i) := false.B
    }
  }


  //>>>>>>>>
  //  PLRU
  //<<<<<<<<


  //>>>>>>>>>>>>>
  //  ICache FSM
  //<<<<<<<<<<<<<

  // ICache FSM state
  val s_idle :: s_fill :: s_fill_wait :: Nil = Enum(3)

  val state = RegInit(s_idle)


  // not valid
  // io.cpu_io.valid := state =/= s_idle




  //>>>>>>>>>>>>>>>>
  // STATE TRANSFER
  //<<<<<<<<<<<<<<<<


  when(state === s_idle) {

  }

  when(state === s_fill) {

  }

  when(state === s_fill_wait) {

  }


}
