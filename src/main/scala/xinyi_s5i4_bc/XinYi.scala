package xinyi_s5i4_bc

import chisel3._
import chisel3.util._
import config.config._

class S5I4 extends RawModule with PortConfig {
  override val desiredName = TOP_NAME

  val aclk         = IO(Input(Clock()))
  val aresetn      = IO(Input(Bool()))

  val ext_int      = IO(Input(UInt(6.W)))   //high active
  // axi
  // ar
  val arid         = IO(Output(UInt(AXI_R_ID_WIDTH.W)))
  val araddr       = IO(Output(UInt(PHY_ADDR_W.W)))
  val arlen        = IO(Output(UInt(4.W)))
  val arsize       = IO(Output(UInt(3.W)))
  val arburst      = IO(Output(UInt(2.W)))
  val arlock       = IO(Output(UInt(2.W)))
  val arcache      = IO(Output(UInt(4.W)))
  val arprot       = IO(Output(UInt(3.W)))
  val arvalid      = IO(Output(Bool()))
  val arready      = IO(Input(Bool()))
  //r
  val rid          = IO(Input(UInt(AXI_R_ID_WIDTH.W)))
  val rdata        = IO(Input(UInt(L2_W.W)))
  val rresp        = IO(Input(UInt(2.W)))
  val rlast        = IO(Input(Bool()))
  val rvalid       = IO(Input(Bool()))
  val rready       = IO(Output(Bool()))
  //aw
  val awid         = IO(Output(UInt(AXI_W_ID_WIDTH.W)))
  val awaddr       = IO(Output(UInt(PHY_ADDR_W.W)))
  val awlen        = IO(Output(UInt(4.W)))
  val awsize       = IO(Output(UInt(3.W)))
  val awburst      = IO(Output(UInt(2.W)))
  val awlock       = IO(Output(UInt(2.W)))
  val awcache      = IO(Output(UInt(4.W)))
  val awprot       = IO(Output(UInt(3.W)))
  val awvalid      = IO(Output(Bool()))
  val awready      = IO(Input(Bool()))
  //w
  val wid          = IO(Output(UInt(AXI_W_ID_WIDTH.W)))
  val wdata        = IO(Output(UInt(L2_W.W)))
  val wstrb        = IO(Output(UInt((L2_W/8).W)))
  val wlast        = IO(Output(Bool()))
  val wvalid       = IO(Output(Bool()))
  val wready       = IO(Input(Bool()))
  //b
  val bid          = IO(Input(UInt(AXI_W_ID_WIDTH.W)))
  val bresp        = IO(Input(UInt(2.W)))
  val bvalid       = IO(Input(Bool()))
  val bready       = IO(Output(Bool()))

  //debug interface
  val debug_wb_pc       = IO(Output(UInt(32.W)))
  val debug_wb_rf_wen   = IO(Output(UInt(4.W)))
  val debug_wb_rf_wnum  = IO(Output(UInt(5.W)))
  val debug_wb_rf_wdata = IO(Output(UInt(32.W)))

  val debug_pc = IO(Output(Vec(ISSUE_NUM, UInt(XLEN.W))))

  debug_wb_pc       := 0.U
  debug_wb_rf_wen   := 0.U
  debug_wb_rf_wnum  := 0.U
  debug_wb_rf_wdata := 0.U

  def Connect[S <: UInt](out: S, in0: Bits, in2: Bits, in1: Bits, input: Boolean = false) : S = {
    val w = in0.getWidth
    if (input) {
      in0 := out(w-1, 0)
      in1 := out(2*w-1, w)
      in2 := out(3*w-1, 2*w)
    }
    else {
      out := Cat(in2, in1, in0)
    }
    out
  }

  def Connect2[S <: UInt](out: S, in0: Bits, in1: Bits, input: Boolean = false) : S = {
    val w = in0.getWidth
    if (input) {
      in0 := out(w-1, 0)
      in1 := out(2*w-1, w)
    }
    else {
      out := Cat(in1, in0)
    }
    out
  }

  if(VERILATOR) {
    withClockAndReset(aclk, ~aresetn) {
      val axi3x1 = Module(new CrossbarNto1(LSU_PATH_NUM + 1))
      val datapath = Module(new DataPath)
      for (i <- 0 to 5) {
        datapath.io.interrupt(i) := ext_int(i)
      }
      debug_pc := datapath.io.debug_pc

      for (i <- 0 until LSU_PATH_NUM) {
        datapath.io.dcache_axi(i).awid      <> axi3x1.io.in(i).aw.bits.id
        datapath.io.dcache_axi(i).awaddr    <> axi3x1.io.in(i).aw.bits.addr
        datapath.io.dcache_axi(i).awlen     <> axi3x1.io.in(i).aw.bits.len
        datapath.io.dcache_axi(i).awsize    <> axi3x1.io.in(i).aw.bits.size
        datapath.io.dcache_axi(i).awburst   <> axi3x1.io.in(i).aw.bits.burst
        datapath.io.dcache_axi(i).awlock    <> axi3x1.io.in(i).aw.bits.lock
        datapath.io.dcache_axi(i).awcache   <> axi3x1.io.in(i).aw.bits.cache
        datapath.io.dcache_axi(i).awprot    <> axi3x1.io.in(i).aw.bits.prot
        datapath.io.dcache_axi(i).awvalid   <> axi3x1.io.in(i).aw.valid
        datapath.io.dcache_axi(i).awready   <> axi3x1.io.in(i).aw.ready
        datapath.io.dcache_axi(i).wid       <> axi3x1.io.in(i).w.bits.id
        datapath.io.dcache_axi(i).wdata     <> axi3x1.io.in(i).w.bits.data
        datapath.io.dcache_axi(i).wstrb     <> axi3x1.io.in(i).w.bits.strb
        datapath.io.dcache_axi(i).wlast     <> axi3x1.io.in(i).w.bits.last
        datapath.io.dcache_axi(i).wvalid    <> axi3x1.io.in(i).w.valid
        datapath.io.dcache_axi(i).wready    <> axi3x1.io.in(i).w.ready
        datapath.io.dcache_axi(i).bid       <> axi3x1.io.in(i).b.bits.id
        datapath.io.dcache_axi(i).bresp     <> axi3x1.io.in(i).b.bits.resp
        datapath.io.dcache_axi(i).bvalid    <> axi3x1.io.in(i).b.valid
        datapath.io.dcache_axi(i).bready    <> axi3x1.io.in(i).b.ready
        datapath.io.dcache_axi(i).arid      <> axi3x1.io.in(i).ar.bits.id
        datapath.io.dcache_axi(i).araddr    <> axi3x1.io.in(i).ar.bits.addr
        datapath.io.dcache_axi(i).arlen     <> axi3x1.io.in(i).ar.bits.len
        datapath.io.dcache_axi(i).arsize    <> axi3x1.io.in(i).ar.bits.size
        datapath.io.dcache_axi(i).arburst   <> axi3x1.io.in(i).ar.bits.burst
        datapath.io.dcache_axi(i).arlock    <> axi3x1.io.in(i).ar.bits.lock
        datapath.io.dcache_axi(i).arcache   <> axi3x1.io.in(i).ar.bits.cache
        datapath.io.dcache_axi(i).arprot    <> axi3x1.io.in(i).ar.bits.prot
        datapath.io.dcache_axi(i).arvalid   <> axi3x1.io.in(i).ar.valid
        datapath.io.dcache_axi(i).arready   <> axi3x1.io.in(i).ar.ready
        datapath.io.dcache_axi(i).rid       <> axi3x1.io.in(i).r.bits.id
        datapath.io.dcache_axi(i).rdata     <> axi3x1.io.in(i).r.bits.data
        datapath.io.dcache_axi(i).rresp     <> axi3x1.io.in(i).r.bits.resp
        datapath.io.dcache_axi(i).rlast     <> axi3x1.io.in(i).r.bits.last
        datapath.io.dcache_axi(i).rvalid    <> axi3x1.io.in(i).r.valid
        datapath.io.dcache_axi(i).rready    <> axi3x1.io.in(i).r.ready
      }

      datapath.io.icache_axi.awid      <> axi3x1.io.in(1).aw.bits.id
      datapath.io.icache_axi.awaddr    <> axi3x1.io.in(1).aw.bits.addr
      datapath.io.icache_axi.awlen     <> axi3x1.io.in(1).aw.bits.len
      datapath.io.icache_axi.awsize    <> axi3x1.io.in(1).aw.bits.size
      datapath.io.icache_axi.awburst   <> axi3x1.io.in(1).aw.bits.burst
      datapath.io.icache_axi.awlock    <> axi3x1.io.in(1).aw.bits.lock
      datapath.io.icache_axi.awcache   <> axi3x1.io.in(1).aw.bits.cache
      datapath.io.icache_axi.awprot    <> axi3x1.io.in(1).aw.bits.prot
      datapath.io.icache_axi.awvalid   <> axi3x1.io.in(1).aw.valid
      datapath.io.icache_axi.awready   <> axi3x1.io.in(1).aw.ready
      datapath.io.icache_axi.wid       <> axi3x1.io.in(1).w.bits.id
      datapath.io.icache_axi.wdata     <> axi3x1.io.in(1).w.bits.data
      datapath.io.icache_axi.wstrb     <> axi3x1.io.in(1).w.bits.strb
      datapath.io.icache_axi.wlast     <> axi3x1.io.in(1).w.bits.last
      datapath.io.icache_axi.wvalid    <> axi3x1.io.in(1).w.valid
      datapath.io.icache_axi.wready    <> axi3x1.io.in(1).w.ready
      datapath.io.icache_axi.bid       <> axi3x1.io.in(1).b.bits.id
      datapath.io.icache_axi.bresp     <> axi3x1.io.in(1).b.bits.resp
      datapath.io.icache_axi.bvalid    <> axi3x1.io.in(1).b.valid
      datapath.io.icache_axi.bready    <> axi3x1.io.in(1).b.ready
      datapath.io.icache_axi.arid      <> axi3x1.io.in(1).ar.bits.id
      datapath.io.icache_axi.araddr    <> axi3x1.io.in(1).ar.bits.addr
      datapath.io.icache_axi.arlen     <> axi3x1.io.in(1).ar.bits.len
      datapath.io.icache_axi.arsize    <> axi3x1.io.in(1).ar.bits.size
      datapath.io.icache_axi.arburst   <> axi3x1.io.in(1).ar.bits.burst
      datapath.io.icache_axi.arlock    <> axi3x1.io.in(1).ar.bits.lock
      datapath.io.icache_axi.arcache   <> axi3x1.io.in(1).ar.bits.cache
      datapath.io.icache_axi.arprot    <> axi3x1.io.in(1).ar.bits.prot
      datapath.io.icache_axi.arvalid   <> axi3x1.io.in(1).ar.valid
      datapath.io.icache_axi.arready   <> axi3x1.io.in(1).ar.ready
      datapath.io.icache_axi.rid       <> axi3x1.io.in(1).r.bits.id
      datapath.io.icache_axi.rdata     <> axi3x1.io.in(1).r.bits.data
      datapath.io.icache_axi.rresp     <> axi3x1.io.in(1).r.bits.resp
      datapath.io.icache_axi.rlast     <> axi3x1.io.in(1).r.bits.last
      datapath.io.icache_axi.rvalid    <> axi3x1.io.in(1).r.valid
      datapath.io.icache_axi.rready    <> axi3x1.io.in(1).r.ready

      awid      <> axi3x1.io.out.aw.bits.id
      awaddr    <> axi3x1.io.out.aw.bits.addr
      awlen     <> axi3x1.io.out.aw.bits.len
      awsize    <> axi3x1.io.out.aw.bits.size
      awburst   <> axi3x1.io.out.aw.bits.burst
      awlock    <> axi3x1.io.out.aw.bits.lock
      awcache   <> axi3x1.io.out.aw.bits.cache
      awprot    <> axi3x1.io.out.aw.bits.prot
      awvalid   <> axi3x1.io.out.aw.valid
      awready   <> axi3x1.io.out.aw.ready
      wid       <> axi3x1.io.out.w.bits.id
      wdata     <> axi3x1.io.out.w.bits.data
      wstrb     <> axi3x1.io.out.w.bits.strb
      wlast     <> axi3x1.io.out.w.bits.last
      wvalid    <> axi3x1.io.out.w.valid
      wready    <> axi3x1.io.out.w.ready
      bid       <> axi3x1.io.out.b.bits.id
      bresp     <> axi3x1.io.out.b.bits.resp
      bvalid    <> axi3x1.io.out.b.valid
      bready    <> axi3x1.io.out.b.ready
      arid      <> axi3x1.io.out.ar.bits.id
      araddr    <> axi3x1.io.out.ar.bits.addr
      arlen     <> axi3x1.io.out.ar.bits.len
      arsize    <> axi3x1.io.out.ar.bits.size
      arburst   <> axi3x1.io.out.ar.bits.burst
      arlock    <> axi3x1.io.out.ar.bits.lock
      arcache   <> axi3x1.io.out.ar.bits.cache
      arprot    <> axi3x1.io.out.ar.bits.prot
      arvalid   <> axi3x1.io.out.ar.valid
      arready   <> axi3x1.io.out.ar.ready
      rid       <> axi3x1.io.out.r.bits.id
      rdata     <> axi3x1.io.out.r.bits.data
      rresp     <> axi3x1.io.out.r.bits.resp
      rlast     <> axi3x1.io.out.r.bits.last
      rvalid    <> axi3x1.io.out.r.valid
      rready    <> axi3x1.io.out.r.ready
    }
  } else {
    val axi3x1         = Module(new AXICrossbar)
    axi3x1.io.aclk    := aclk
    axi3x1.io.aresetn := aresetn

    withClockAndReset(aclk, ~aresetn) {
      val datapath = Module(new DataPath)
      for (i <- 0 to 5) {
        datapath.io.interrupt(i) := ext_int(i)
      }
      debug_pc := datapath.io.debug_pc

      if (LSU_PATH_NUM == 2) {
        Connect(axi3x1.io.s_axi_awid     , datapath.io.icache_axi.awid     , datapath.io.dcache_axi(0).awid     , datapath.io.dcache_axi(1).awid     )
        Connect(axi3x1.io.s_axi_awaddr   , datapath.io.icache_axi.awaddr   , datapath.io.dcache_axi(0).awaddr   , datapath.io.dcache_axi(1).awaddr   )
        Connect(axi3x1.io.s_axi_awlen    , datapath.io.icache_axi.awlen    , datapath.io.dcache_axi(0).awlen    , datapath.io.dcache_axi(1).awlen    )
        Connect(axi3x1.io.s_axi_awsize   , datapath.io.icache_axi.awsize   , datapath.io.dcache_axi(0).awsize   , datapath.io.dcache_axi(1).awsize   )
        Connect(axi3x1.io.s_axi_awburst  , datapath.io.icache_axi.awburst  , datapath.io.dcache_axi(0).awburst  , datapath.io.dcache_axi(1).awburst  )
        Connect(axi3x1.io.s_axi_awlock   , datapath.io.icache_axi.awlock   , datapath.io.dcache_axi(0).awlock   , datapath.io.dcache_axi(1).awlock   )
        Connect(axi3x1.io.s_axi_awcache  , datapath.io.icache_axi.awcache  , datapath.io.dcache_axi(0).awcache  , datapath.io.dcache_axi(1).awcache  )
        Connect(axi3x1.io.s_axi_awprot   , datapath.io.icache_axi.awprot   , datapath.io.dcache_axi(0).awprot   , datapath.io.dcache_axi(1).awprot   )
        Connect(axi3x1.io.s_axi_awvalid  , datapath.io.icache_axi.awvalid  , datapath.io.dcache_axi(0).awvalid  , datapath.io.dcache_axi(1).awvalid  )
        Connect(axi3x1.io.s_axi_awready  , datapath.io.icache_axi.awready  , datapath.io.dcache_axi(0).awready  , datapath.io.dcache_axi(1).awready  , true)
        Connect(axi3x1.io.s_axi_wdata    , datapath.io.icache_axi.wdata    , datapath.io.dcache_axi(0).wdata    , datapath.io.dcache_axi(1).wdata    )
        Connect(axi3x1.io.s_axi_wstrb    , datapath.io.icache_axi.wstrb    , datapath.io.dcache_axi(0).wstrb    , datapath.io.dcache_axi(1).wstrb    )
        Connect(axi3x1.io.s_axi_wlast    , datapath.io.icache_axi.wlast    , datapath.io.dcache_axi(0).wlast    , datapath.io.dcache_axi(1).wlast    )
        Connect(axi3x1.io.s_axi_wvalid   , datapath.io.icache_axi.wvalid   , datapath.io.dcache_axi(0).wvalid   , datapath.io.dcache_axi(1).wvalid   )
        Connect(axi3x1.io.s_axi_wready   , datapath.io.icache_axi.wready   , datapath.io.dcache_axi(0).wready   , datapath.io.dcache_axi(1).wready   , true)
        Connect(axi3x1.io.s_axi_bid      , datapath.io.icache_axi.bid      , datapath.io.dcache_axi(0).bid      , datapath.io.dcache_axi(1).bid      , true)
        Connect(axi3x1.io.s_axi_bresp    , datapath.io.icache_axi.bresp    , datapath.io.dcache_axi(0).bresp    , datapath.io.dcache_axi(1).bresp    , true)
        Connect(axi3x1.io.s_axi_bvalid   , datapath.io.icache_axi.bvalid   , datapath.io.dcache_axi(0).bvalid   , datapath.io.dcache_axi(1).bvalid   , true)
        Connect(axi3x1.io.s_axi_bready   , datapath.io.icache_axi.bready   , datapath.io.dcache_axi(0).bready   , datapath.io.dcache_axi(1).bready   )
        Connect(axi3x1.io.s_axi_arid     , datapath.io.icache_axi.arid     , datapath.io.dcache_axi(0).arid     , datapath.io.dcache_axi(1).arid     )
        Connect(axi3x1.io.s_axi_araddr   , datapath.io.icache_axi.araddr   , datapath.io.dcache_axi(0).araddr   , datapath.io.dcache_axi(1).araddr   )
        Connect(axi3x1.io.s_axi_arlen    , datapath.io.icache_axi.arlen    , datapath.io.dcache_axi(0).arlen    , datapath.io.dcache_axi(1).arlen    )
        Connect(axi3x1.io.s_axi_arsize   , datapath.io.icache_axi.arsize   , datapath.io.dcache_axi(0).arsize   , datapath.io.dcache_axi(1).arsize   )
        Connect(axi3x1.io.s_axi_arburst  , datapath.io.icache_axi.arburst  , datapath.io.dcache_axi(0).arburst  , datapath.io.dcache_axi(1).arburst  )
        Connect(axi3x1.io.s_axi_arlock   , datapath.io.icache_axi.arlock   , datapath.io.dcache_axi(0).arlock   , datapath.io.dcache_axi(1).arlock   )
        Connect(axi3x1.io.s_axi_arcache  , datapath.io.icache_axi.arcache  , datapath.io.dcache_axi(0).arcache  , datapath.io.dcache_axi(1).arcache  )
        Connect(axi3x1.io.s_axi_arprot   , datapath.io.icache_axi.arprot   , datapath.io.dcache_axi(0).arprot   , datapath.io.dcache_axi(1).arprot   )
        Connect(axi3x1.io.s_axi_arvalid  , datapath.io.icache_axi.arvalid  , datapath.io.dcache_axi(0).arvalid  , datapath.io.dcache_axi(1).arvalid  )
        Connect(axi3x1.io.s_axi_arready  , datapath.io.icache_axi.arready  , datapath.io.dcache_axi(0).arready  , datapath.io.dcache_axi(1).arready  , true)
        Connect(axi3x1.io.s_axi_rid      , datapath.io.icache_axi.rid      , datapath.io.dcache_axi(0).rid      , datapath.io.dcache_axi(1).rid      , true)
        Connect(axi3x1.io.s_axi_rdata    , datapath.io.icache_axi.rdata    , datapath.io.dcache_axi(0).rdata    , datapath.io.dcache_axi(1).rdata    , true)
        Connect(axi3x1.io.s_axi_rresp    , datapath.io.icache_axi.rresp    , datapath.io.dcache_axi(0).rresp    , datapath.io.dcache_axi(1).rresp    , true)
        Connect(axi3x1.io.s_axi_rlast    , datapath.io.icache_axi.rlast    , datapath.io.dcache_axi(0).rlast    , datapath.io.dcache_axi(1).rlast    , true)
        Connect(axi3x1.io.s_axi_rvalid   , datapath.io.icache_axi.rvalid   , datapath.io.dcache_axi(0).rvalid   , datapath.io.dcache_axi(1).rvalid   , true)
        Connect(axi3x1.io.s_axi_rready   , datapath.io.icache_axi.rready   , datapath.io.dcache_axi(0).rready   , datapath.io.dcache_axi(1).rready   )
      }
      else {
        Connect2(axi3x1.io.s_axi_awid     , datapath.io.icache_axi.awid     , datapath.io.dcache_axi(0).awid     )
        Connect2(axi3x1.io.s_axi_awaddr   , datapath.io.icache_axi.awaddr   , datapath.io.dcache_axi(0).awaddr   )
        Connect2(axi3x1.io.s_axi_awlen    , datapath.io.icache_axi.awlen    , datapath.io.dcache_axi(0).awlen    )
        Connect2(axi3x1.io.s_axi_awsize   , datapath.io.icache_axi.awsize   , datapath.io.dcache_axi(0).awsize   )
        Connect2(axi3x1.io.s_axi_awburst  , datapath.io.icache_axi.awburst  , datapath.io.dcache_axi(0).awburst  )
        Connect2(axi3x1.io.s_axi_awlock   , datapath.io.icache_axi.awlock   , datapath.io.dcache_axi(0).awlock   )
        Connect2(axi3x1.io.s_axi_awcache  , datapath.io.icache_axi.awcache  , datapath.io.dcache_axi(0).awcache  )
        Connect2(axi3x1.io.s_axi_awprot   , datapath.io.icache_axi.awprot   , datapath.io.dcache_axi(0).awprot   )
        Connect2(axi3x1.io.s_axi_awvalid  , datapath.io.icache_axi.awvalid  , datapath.io.dcache_axi(0).awvalid  )
        Connect2(axi3x1.io.s_axi_awready  , datapath.io.icache_axi.awready  , datapath.io.dcache_axi(0).awready  , true)
        Connect2(axi3x1.io.s_axi_wdata    , datapath.io.icache_axi.wdata    , datapath.io.dcache_axi(0).wdata    )
        Connect2(axi3x1.io.s_axi_wstrb    , datapath.io.icache_axi.wstrb    , datapath.io.dcache_axi(0).wstrb    )
        Connect2(axi3x1.io.s_axi_wlast    , datapath.io.icache_axi.wlast    , datapath.io.dcache_axi(0).wlast    )
        Connect2(axi3x1.io.s_axi_wvalid   , datapath.io.icache_axi.wvalid   , datapath.io.dcache_axi(0).wvalid   )
        Connect2(axi3x1.io.s_axi_wready   , datapath.io.icache_axi.wready   , datapath.io.dcache_axi(0).wready   , true)
        Connect2(axi3x1.io.s_axi_bid      , datapath.io.icache_axi.bid      , datapath.io.dcache_axi(0).bid      , true)
        Connect2(axi3x1.io.s_axi_bresp    , datapath.io.icache_axi.bresp    , datapath.io.dcache_axi(0).bresp    , true)
        Connect2(axi3x1.io.s_axi_bvalid   , datapath.io.icache_axi.bvalid   , datapath.io.dcache_axi(0).bvalid   , true)
        Connect2(axi3x1.io.s_axi_bready   , datapath.io.icache_axi.bready   , datapath.io.dcache_axi(0).bready   )
        Connect2(axi3x1.io.s_axi_arid     , datapath.io.icache_axi.arid     , datapath.io.dcache_axi(0).arid     )
        Connect2(axi3x1.io.s_axi_araddr   , datapath.io.icache_axi.araddr   , datapath.io.dcache_axi(0).araddr   )
        Connect2(axi3x1.io.s_axi_arlen    , datapath.io.icache_axi.arlen    , datapath.io.dcache_axi(0).arlen    )
        Connect2(axi3x1.io.s_axi_arsize   , datapath.io.icache_axi.arsize   , datapath.io.dcache_axi(0).arsize   )
        Connect2(axi3x1.io.s_axi_arburst  , datapath.io.icache_axi.arburst  , datapath.io.dcache_axi(0).arburst  )
        Connect2(axi3x1.io.s_axi_arlock   , datapath.io.icache_axi.arlock   , datapath.io.dcache_axi(0).arlock   )
        Connect2(axi3x1.io.s_axi_arcache  , datapath.io.icache_axi.arcache  , datapath.io.dcache_axi(0).arcache  )
        Connect2(axi3x1.io.s_axi_arprot   , datapath.io.icache_axi.arprot   , datapath.io.dcache_axi(0).arprot   )
        Connect2(axi3x1.io.s_axi_arvalid  , datapath.io.icache_axi.arvalid  , datapath.io.dcache_axi(0).arvalid  )
        Connect2(axi3x1.io.s_axi_arready  , datapath.io.icache_axi.arready  , datapath.io.dcache_axi(0).arready  , true)
        Connect2(axi3x1.io.s_axi_rid      , datapath.io.icache_axi.rid      , datapath.io.dcache_axi(0).rid      , true)
        Connect2(axi3x1.io.s_axi_rdata    , datapath.io.icache_axi.rdata    , datapath.io.dcache_axi(0).rdata    , true)
        Connect2(axi3x1.io.s_axi_rresp    , datapath.io.icache_axi.rresp    , datapath.io.dcache_axi(0).rresp    , true)
        Connect2(axi3x1.io.s_axi_rlast    , datapath.io.icache_axi.rlast    , datapath.io.dcache_axi(0).rlast    , true)
        Connect2(axi3x1.io.s_axi_rvalid   , datapath.io.icache_axi.rvalid   , datapath.io.dcache_axi(0).rvalid   , true)
        Connect2(axi3x1.io.s_axi_rready   , datapath.io.icache_axi.rready   , datapath.io.dcache_axi(0).rready   )
      }
      axi3x1.io.s_axi_awqos := 0.U
      axi3x1.io.s_axi_arqos := 0.U

      awid      <> axi3x1.io.m_axi_awid
      awaddr    <> axi3x1.io.m_axi_awaddr
      awlen     <> axi3x1.io.m_axi_awlen
      awsize    <> axi3x1.io.m_axi_awsize
      awburst   <> axi3x1.io.m_axi_awburst
      awlock    <> axi3x1.io.m_axi_awlock
      awcache   <> axi3x1.io.m_axi_awcache
      awprot    <> axi3x1.io.m_axi_awprot
      awvalid   <> axi3x1.io.m_axi_awvalid
      awready   <> axi3x1.io.m_axi_awready
      wid       <> axi3x1.io.m_axi_wid
      wdata     <> axi3x1.io.m_axi_wdata
      wstrb     <> axi3x1.io.m_axi_wstrb
      wlast     <> axi3x1.io.m_axi_wlast
      wvalid    <> axi3x1.io.m_axi_wvalid
      wready    <> axi3x1.io.m_axi_wready
      bid       <> axi3x1.io.m_axi_bid
      bresp     <> axi3x1.io.m_axi_bresp
      bvalid    <> axi3x1.io.m_axi_bvalid
      bready    <> axi3x1.io.m_axi_bready
      arid      <> axi3x1.io.m_axi_arid
      araddr    <> axi3x1.io.m_axi_araddr
      arlen     <> axi3x1.io.m_axi_arlen
      arsize    <> axi3x1.io.m_axi_arsize
      arburst   <> axi3x1.io.m_axi_arburst
      arlock    <> axi3x1.io.m_axi_arlock
      arcache   <> axi3x1.io.m_axi_arcache
      arprot    <> axi3x1.io.m_axi_arprot
      arvalid   <> axi3x1.io.m_axi_arvalid
      arready   <> axi3x1.io.m_axi_arready
      rid       <> axi3x1.io.m_axi_rid
      rdata     <> axi3x1.io.m_axi_rdata
      rresp     <> axi3x1.io.m_axi_rresp
      rlast     <> axi3x1.io.m_axi_rlast
      rvalid    <> axi3x1.io.m_axi_rvalid
      rready    <> axi3x1.io.m_axi_rready
    }
  }
}
