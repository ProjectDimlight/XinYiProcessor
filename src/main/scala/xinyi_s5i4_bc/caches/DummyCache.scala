package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._
import utils._

trait CacheState {
  val s_idle     = 0.U(3.W)
  val s_pending  = 1.U(3.W)
  val s_busy     = 2.U(3.W)
  val s_valid    = 3.U(3.W)
  val s_pending2 = 5.U(3.W)
  val s_busy2    = 6.U(3.W)
}

class ICacheCPU extends Bundle {
  val rd   = Input (Bool())
  val addr = Input (UInt(LGC_ADDR_W.W))
  val dout = Output(UInt((XLEN * ISSUE_NUM).W))
}

class ICacheAXI extends Bundle {
  val addr_in     = Output(UInt(PHY_ADDR_W.W))
  val en          = Output(Bool())
  val addr_out    = Input(UInt(PHY_ADDR_W.W))
  val data        = Input(UInt(L1_W.W))
  val stall       = Input(Bool())
  val valid       = Input(Bool())
}

class DummyICache extends Module with CacheState {
  val io = IO(new Bundle{
    val upper = new ICacheCPU
    val lower = new ICacheAXI
  
    val stall_req = Output(Bool())
  })
  
  val state_reg = RegInit(s_idle)
  val state     = Wire(UInt(2.W))
  
  state := MuxLookupBi(
    state_reg,
    s_idle,
    Array(
      s_idle -> Mux(io.upper.rd, 
        Mux(io.lower.stall, s_pending, s_busy),
        s_idle
      ),
      s_pending -> Mux(io.lower.stall, s_pending, s_busy),
      s_busy -> Mux(io.lower.valid, s_valid, s_busy),
      s_valid -> Mux(io.upper.rd,
        Mux(io.lower.stall, s_pending, s_busy),
        s_idle
      )
    )
  )
  state_reg := state

  io.lower.addr_in  := Mux(
    io.upper.addr(31, 30) === 2.U,
    io.upper.addr & 0x1FFFFFFF.U,
    io.upper.addr
  )
  io.lower.en       := ((state === s_pending) | (state === s_busy) & (state_reg =/= s_busy))
  
  io.upper.dout     := io.lower.data
  io.stall_req      := (state =/= s_valid) & (state =/= s_idle)
}

class DCacheCPU extends Bundle {
  val rd   = Input (Bool())
  val wr   = Input (Bool())
  val size = Input (UInt(2.W))
  val addr = Input (UInt(PHY_ADDR_W.W))
  val din  = Input (UInt(XLEN.W))
  val dout = Output(UInt(XLEN.W))
}

class DCacheAXI extends Bundle {
  val addr_in     = Output(UInt(PHY_ADDR_W.W))
  val data_in     = Output(UInt(L1_W.W))
  val wr          = Output(Bool())
  val rd          = Output(Bool())
  val size        = Output(UInt(2.W))
  val addr_out    = Input(UInt(PHY_ADDR_W.W))
  val data_out    = Input(UInt(L1_W.W))
  val stall       = Input(Bool())
  val valid       = Input(Bool())
}

class DummyDCache extends Module with CacheState {
  val io = IO(new Bundle{
    val upper = Vec(LSU_PATH_NUM, new DCacheCPU)
    val lower = Vec(LSU_PATH_NUM, new DCacheAXI)

    val last_stall = Input(Bool())
    val stall = Input(Bool())
    val flush = Input(Bool())
    val stall_req = Output(Vec(LSU_PATH_NUM, Bool()))
  })

  val wr_data_buffer = RegInit(VecInit(Seq.fill(LSU_PATH_NUM)(0.U(LGC_ADDR_W.W))))
  val wr_addr_buffer = RegInit(VecInit(Seq.fill(LSU_PATH_NUM)(0.U(XLEN.W))))
  val wr_size_buffer = RegInit(VecInit(Seq.fill(LSU_PATH_NUM)(0.U(2.W))))
  val wr_ctrl_buffer = RegInit(VecInit(Seq.fill(LSU_PATH_NUM)(false.B)))

  for (j <- 0 until LSU_PATH_NUM) {
    val addr = Mux(
      io.upper(j).addr(31, 30) === 2.U,
      io.upper(j).addr & 0x1FFFFFFF.U,
      io.upper(j).addr
    )
    when (io.stall & wr_ctrl_buffer(j) & io.lower(j).valid) {
      wr_ctrl_buffer(j) := false.B
    }
    .elsewhen (!io.stall) {
      wr_addr_buffer(j) := addr
      wr_data_buffer(j) := io.upper(j).din
      wr_size_buffer(j) := io.upper(j).size
      wr_ctrl_buffer(j) := io.upper(j).wr
    }

    val state_reg = RegInit(s_idle)
    val state     = Wire(UInt(3.W))
    state := MuxLookupBi(
      state_reg,
      s_idle,
      Array(
        s_idle -> Mux(!io.last_stall & (io.upper(j).rd | wr_ctrl_buffer(j)), 
          Mux(io.lower(j).stall, s_pending, s_busy),
          s_idle
        ),
        s_pending -> Mux(io.lower(j).stall, s_pending, s_busy),
        s_busy -> Mux(io.lower(j).valid, 
          Mux(io.upper(j).rd & wr_ctrl_buffer(j), 
            Mux(io.lower(j).stall, s_pending2, s_busy2), 
            s_valid
          ),
          s_busy
        ),
        s_pending2 -> Mux(io.lower(j).stall, s_pending2, s_busy2),
        s_busy2 -> Mux(io.lower(j).valid, 
          s_valid,
          s_busy2
        ),
        s_valid -> Mux(!io.last_stall & (io.upper(j).rd | wr_ctrl_buffer(j)),
          Mux(io.lower(j).stall, s_pending, s_busy),
          s_idle
        )
      )
    )
    state_reg := state

    io.lower(j).size     := Mux(wr_ctrl_buffer(j), wr_size_buffer(j), io.upper(j).size)
    io.lower(j).addr_in  := Mux(wr_ctrl_buffer(j), wr_addr_buffer(j), addr)
    io.lower(j).data_in  := Cat(0.U(32.W), wr_data_buffer(j))
    io.lower(j).rd       := !wr_ctrl_buffer(j) & io.upper(j).rd & ((state(1, 0) === s_pending) | (state(1, 0) === s_busy) & (state_reg =/= state))
    io.lower(j).wr       :=  wr_ctrl_buffer(j) & ((state(1, 0) === s_pending) | (state(1, 0) === s_busy) & (state_reg =/= state))

    val valid = (state === s_valid) & (state_reg =/= s_valid)
    val data_reg = RegInit(0.U(XLEN.W))
    val data = io.lower(j).data_out(L1_W - 1, L1_W - XLEN)
    when (valid) {
      data_reg := data
    }
    io.upper(j).dout     := Mux(valid, data, data_reg)
    io.stall_req(j)      := (state =/= s_valid) & (state =/= s_idle)
  }
}