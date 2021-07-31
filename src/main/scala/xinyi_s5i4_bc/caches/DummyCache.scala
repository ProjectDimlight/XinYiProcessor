package xinyi_s5i4_bc.caches

import xinyi_s5i4_bc._
import chisel3._
import chisel3.util._
import config.config._
import utils._

trait CacheState {
  val s_idle = 0.U(3.W)
  val s_pending = 1.U(3.W)
  val s_busy = 2.U(3.W)
  val s_valid = 3.U(3.W)
  val s_valid2 = 4.U(3.W)
  val s_pending2 = 5.U(3.W)
  val s_busy2 = 6.U(3.W)
}

// class DummyICache extends Module with CacheState {
//   val io = IO(new Bundle {
//     val cpu_io = new ICacheCPUIO
//     val axi_io = new AXIIO

//     // val stall_req = Output(Bool())
//   })

//   val state_reg = RegInit(s_idle)
//   val state = Wire(UInt(2.W))

//   val cnt = RegInit(0.U(2.W))
//   val data = RegInit(VecInit(Seq.fill(FETCH_NUM)(0.U(XLEN.W))))

//   state := MuxLookupBi(
//     state_reg,
//     s_idle,
//     Array(
//       s_idle -> Mux(io.cpu_io.rd, s_pending, s_idle),
//       s_pending -> Mux(io.axi_io.arready, s_busy, s_pending),
//       s_busy -> Mux(cnt === 2.U, s_valid, s_busy),
//       s_valid -> Mux(io.cpu_io.rd, s_pending, s_idle)
//     )
//   )
//   state_reg := state

//   when(state === s_pending) {
//     cnt := 0.U
//   }
//     .elsewhen(io.axi_io.rvalid) {
//       data(cnt) := io.axi_io.rdata
//       cnt := cnt + 1.U
//     }

//   val addr_in  = io.upper.addr
//   val rd       = state(1, 0) === s_pending
  
//   io.lower.arid    <> 0.U
//   io.lower.araddr  <> addr_in
//   io.lower.arlen   <> 1.U
//   io.lower.arsize  <> 2.U
//   io.lower.arburst <> 1.U
//   io.lower.arlock  <> 0.U
//   io.lower.arcache <> 0.U
//   io.lower.arprot  <> 0.U
//   io.lower.arvalid <> rd
//   io.lower.rready  <> 1.U
//   io.lower.awid    <> 0.U
//   io.lower.awaddr  <> 0.U
//   io.lower.awlen   <> 0.U
//   io.lower.awsize  <> 0.U
//   io.lower.awburst <> 1.U
//   io.lower.awlock  <> 0.U
//   io.lower.awcache <> 0.U
//   io.lower.awprot  <> 0.U
//   io.lower.awvalid <> 0.U
//   io.lower.wid     <> 0.U
//   io.lower.wdata   <> 0.U
//   io.lower.wstrb   <> 0.U
//   io.lower.wlast   <> 0.U
//   io.lower.wvalid  <> 0.U
//   io.lower.bready  <> 1.U

//   io.upper.data     := data.asUInt()
//   io.stall_req      := (state =/= s_valid) & (state =/= s_idle)
// }

// class DCacheCPU extends Bundle {
//   val rd   = Input (Bool())
//   val wr   = Input (Bool())
//   val size = Input (UInt(2.W))
//   val strb = Input (UInt(4.W))
//   val addr = Input (UInt(PHY_ADDR_W.W))
//   val din  = Input (UInt(XLEN.W))
//   val dout = Output(UInt(XLEN.W))
// }

class WriteBufferRecord extends Bundle {
  val addr = UInt(LGC_ADDR_W.W)
  val data = UInt(XLEN.W)
  val size = UInt(2.W)
  val ctrl = Bool()
}

class DummyDCache extends Module with CacheState {
  val io = IO(new DCacheIO)

  for (j <- 0 until LSU_PATH_NUM) {
    val state_reg = RegInit(s_idle)
    val state = Wire(UInt(3.W))

    state := MuxLookupBi(
      state_reg,
      s_idle,
      Array(
        s_idle -> Mux(
          !io.last_stall & (io.upper(j).rd | io.upper(j).wr),
          s_pending,
          s_idle
        ),
        s_pending -> Mux(
          io.lower(j).arready | io.lower(j).awready,
          s_busy,
          s_pending
        ),
        s_busy -> Mux(
          io.lower(j).bvalid | io.lower(j).rvalid & io.lower(j).rlast,
          s_valid,
          s_busy
        ),
        s_valid -> Mux(
          !io.last_stall & (io.upper(j).rd | io.upper(j).wr),
          s_pending,
          s_idle
        )
      )
    )
    state_reg := state

    val size = io.upper(j).size
    //val strb     = MuxLookupBi(size, 15.U, Array(0.U -> 1.U, 1.U -> 3.U))
    val strb = io.upper(j).strb
    val addr_in = io.upper(j).addr
    val data_in = io.upper(j).din

    val rd = (state === s_pending) & io.upper(j).rd
    val wr = (state === s_pending) & io.upper(j).wr
    val wrd = RegInit(false.B)
    wrd := Mux(wr, true.B, Mux(io.lower(j).wready, false.B, wrd))

    io.lower(j).arid <> 0.U
    io.lower(j).araddr <> addr_in
    io.lower(j).arlen <> 0.U
    io.lower(j).arsize <> size
    io.lower(j).arburst <> 1.U
    io.lower(j).arlock <> 0.U
    io.lower(j).arcache <> 0.U
    io.lower(j).arprot <> 0.U
    io.lower(j).arvalid <> rd
    io.lower(j).rready <> 1.U
    io.lower(j).awid <> 0.U
    io.lower(j).awaddr <> addr_in
    io.lower(j).awlen <> 0.U
    io.lower(j).awsize <> size
    io.lower(j).awburst <> 1.U
    io.lower(j).awlock <> 0.U
    io.lower(j).awcache <> 0.U
    io.lower(j).awprot <> 0.U
    io.lower(j).awvalid <> wr
    io.lower(j).wid <> 0.U
    io.lower(j).wdata <> data_in
    io.lower(j).wstrb <> strb
    io.lower(j).wlast <> 1.U
    io.lower(j).wvalid <> wrd
    io.lower(j).bready <> 1.U

    val valid = (state === s_valid) & (state_reg =/= s_valid)
    val data_reg = RegInit(0.U(XLEN.W))
    val data = io.lower(j).rdata
    when(valid) {
      data_reg := data
    }
    io.upper(j).dout := Mux(valid, data, data_reg)
    io.upper(j).stall_req := (state =/= s_valid) & (state =/= s_idle)
  }
}
