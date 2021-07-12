package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._


// L0 Config
trait L0DcacheConfig {
  val TAG_WIDTH  : Int = 22 // tag field width
  val INDEX_WIDTH: Int = XLEN - TAG_WIDTH // index field width
  val ENTRY_NUM  : Int = 1 << INDEX_WIDTH // entry num
}

// L0 cache line
// +---------+---------+-------+-------+
// |   data  |   tag   |   V   |   D   |
// +---------+---------+-------+-------+
class L0DCacheLine extends Bundle with L0DcacheConfig {
  val data : UInt = UInt(XLEN.W)
  val tag  : UInt = UInt(TAG_WIDTH.W)
  val dirty: Bool = Bool()
  val valid: Bool = Bool()
}

// separate address to tag and index
class L0DCacheAddr extends Bundle with L0DcacheConfig {
  val tag  : UInt = UInt(TAG_WIDTH.W)
  val index: UInt = UInt(INDEX_WIDTH.W)
}


// L0 cache IO
class L0DCacheCPU extends Bundle {
  val rd  : Bool         = Input(Bool())
  val wr  : Bool         = Input(Bool())
  val addr: L0DCacheAddr = Input(new L0DCacheAddr)
  val din : UInt         = Input(UInt(XLEN.W))
  val dout: UInt         = Output(UInt(XLEN.W))
}

class L0DCacheAXI extends Bundle {
  val dout  = Output(UInt(XLEN.W))
  val wr    = Output(Bool())
  val rd    = Output(Bool())
  val addr  = Output(UInt(PHY_ADDR_W.W))
  val din   = Input(UInt(XLEN.W))
  val stall = Input(Bool())
  val valid = Input(Bool())
}

class L0DCache extends Module with L0DcacheConfig {
  val io = IO(new Bundle {
    val upper = new L0DCacheCPU
    val lower = new L0DCacheAXI
    val ready = Output(Bool())
//    val flush = Input(Bool())
  })


  //<<<<<<<<<<<<
  // Cache Data
  //>>>>>>>>>>>>
  val cache_entries = Mem(ENTRY_NUM, new L0DCacheLine)

  // get the indexed entry


  //>>>>>>>>>>>>>>>>>>>>>>>>>
  //      Controller
  //<<<<<<<<<<<<<<<<<<<<<<<<<

  // L0 finite states
  val s_idle :: s_back :: s_back_wait :: s_fill :: s_fill_wait :: Nil = Enum(5)

  val state = RegInit(s_idle)

  io.ready := state =/= s_idle

  // request type (only valid when miss)
  val idle :: read :: write :: Nil = Enum(3)
  val req_type                     = RegInit(idle.asUInt())

  // tmp data during state transmission
  io.lower.rd := state === s_back && state === s_back_wait
  io.lower.wr := state === s_fill && state === s_fill_wait

  val req_addr = RegInit(0.U.asTypeOf(new L0DCacheAddr))
  io.lower.addr := Mux(state === s_back && state === s_back_wait,
    Cat(cache_entries(req_addr.index).tag, req_addr.index),
    req_addr.asUInt())
  io.upper.dout := cache_entries(req_addr.index).data

  val req_data = RegInit(0.U(XLEN.W))
  io.lower.dout := req_data



  //>>>>>>>>>>>>>>>>>>>>>>>>>>
  //      Meta Data
  //<<<<<<<<<<<<<<<<<<<<<<<<<<

  // get comparison signals
  val is_valid = cache_entries(io.upper.addr.index).valid
  val is_dirty = cache_entries(io.upper.addr.index).dirty

  val is_hit       = Wire(Bool())
  val is_tag_match = Wire(Bool())
  is_hit := is_tag_match && is_valid
  is_tag_match := io.upper.addr.tag === cache_entries(io.upper.addr.index).tag


  //>>>>>>>>>>>>>>>>>>>
  // cache controller
  //<<<<<<<<<<<<<<<<<<<


  // idle state
  when(state === s_idle) {
    when(io.upper.rd) { // read
      when(!is_hit) {
        state := Mux(is_dirty, s_back, s_fill)
        req_type := read
        req_addr := io.upper.addr
      }
    }.elsewhen(io.upper.wr) { // write
      when(is_hit) {
        when(is_dirty) {
          state := s_back
          req_type := write
          req_addr := io.upper.addr
        }
      }.otherwise {
        when(is_dirty) {
          state := s_back
          req_type := write
          req_addr := io.upper.addr
        }
      }
    }
  }

  when(state === s_back) {
    when(io.lower.valid) { // can write to AXI
      // state transfer
      state := s_back_wait
    }
  }

  when(state === s_back_wait) {
    when(io.lower.valid) { // write has done
      when(io.upper.rd) {
        // state transfer
        state := s_fill
      }.elsewhen(io.upper.wr) {
        // state transfer
        state := s_idle
        // write cache metadata
        cache_entries(req_addr.index) := Cat(req_data,
          req_addr.tag, true.B, true.B).asTypeOf(new L0DCacheLine)
      }
    }
  }

  when(state === s_fill) {
    when(io.lower.valid) {
      // state transfer
      state := s_fill_wait
    }
  }

  when(state === s_fill_wait) {
    when(io.lower.valid) {
      // state transfer
      state := s_idle
      // write cache metadata
      cache_entries(req_addr.index) := Cat(io.lower.din,
        req_addr.tag, false.B, true.B).asTypeOf(new L0DCacheLine)
    }
  }
}
