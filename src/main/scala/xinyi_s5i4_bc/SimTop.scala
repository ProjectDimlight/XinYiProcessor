package xinyi_s5i4_bc

import chisel3._
import chisel3.util._
import config.config._

class SimTop extends RawModule with PortConfig {
  override val desiredName = TOP_NAME

  val aclk         = IO(Input(Clock()))
  val aresetn      = IO(Input(Bool()))

  val ext_int      = IO(Input(UInt(6.W)))   //high active
  // axi
  // ar
  val arid         = IO(Output(UInt(AXI_R_ID_WIDTH.W)))
  val araddr       = IO(Output(UInt(PHY_ADDR_W.W)))
  val arlen        = IO(Output(UInt(8.W)))
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
  val awlen        = IO(Output(UInt(8.W)))
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

  withClockAndReset(aclk, ~aresetn) {
    val axi3x1 = Module(new CrossbarNto1(3))
    val datapath = Module(new DataPath)
    for (i <- 0 to 5) {
      datapath.io.interrupt(i) := ext_int(i)
    }
    debug_pc := datapath.io.debug_pc

    for (i <- 0 to 1) {
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

    datapath.io.icache_axi.awid      <> axi3x1.io.in(2).aw.bits.id     
    datapath.io.icache_axi.awaddr    <> axi3x1.io.in(2).aw.bits.addr   
    datapath.io.icache_axi.awlen     <> axi3x1.io.in(2).aw.bits.len    
    datapath.io.icache_axi.awsize    <> axi3x1.io.in(2).aw.bits.size   
    datapath.io.icache_axi.awburst   <> axi3x1.io.in(2).aw.bits.burst  
    datapath.io.icache_axi.awlock    <> axi3x1.io.in(2).aw.bits.lock   
    datapath.io.icache_axi.awcache   <> axi3x1.io.in(2).aw.bits.cache  
    datapath.io.icache_axi.awprot    <> axi3x1.io.in(2).aw.bits.prot
    datapath.io.icache_axi.awvalid   <> axi3x1.io.in(2).aw.valid  
    datapath.io.icache_axi.awready   <> axi3x1.io.in(2).aw.ready  
    datapath.io.icache_axi.wid       <> axi3x1.io.in(2).w.bits.id
    datapath.io.icache_axi.wdata     <> axi3x1.io.in(2).w.bits.data    
    datapath.io.icache_axi.wstrb     <> axi3x1.io.in(2).w.bits.strb    
    datapath.io.icache_axi.wlast     <> axi3x1.io.in(2).w.bits.last    
    datapath.io.icache_axi.wvalid    <> axi3x1.io.in(2).w.valid   
    datapath.io.icache_axi.wready    <> axi3x1.io.in(2).w.ready   
    datapath.io.icache_axi.bid       <> axi3x1.io.in(2).b.bits.id      
    datapath.io.icache_axi.bresp     <> axi3x1.io.in(2).b.bits.resp    
    datapath.io.icache_axi.bvalid    <> axi3x1.io.in(2).b.valid   
    datapath.io.icache_axi.bready    <> axi3x1.io.in(2).b.ready   
    datapath.io.icache_axi.arid      <> axi3x1.io.in(2).ar.bits.id     
    datapath.io.icache_axi.araddr    <> axi3x1.io.in(2).ar.bits.addr   
    datapath.io.icache_axi.arlen     <> axi3x1.io.in(2).ar.bits.len    
    datapath.io.icache_axi.arsize    <> axi3x1.io.in(2).ar.bits.size   
    datapath.io.icache_axi.arburst   <> axi3x1.io.in(2).ar.bits.burst  
    datapath.io.icache_axi.arlock    <> axi3x1.io.in(2).ar.bits.lock   
    datapath.io.icache_axi.arcache   <> axi3x1.io.in(2).ar.bits.cache  
    datapath.io.icache_axi.arprot    <> axi3x1.io.in(2).ar.bits.prot   
    datapath.io.icache_axi.arvalid   <> axi3x1.io.in(2).ar.valid  
    datapath.io.icache_axi.arready   <> axi3x1.io.in(2).ar.ready  
    datapath.io.icache_axi.rid       <> axi3x1.io.in(2).r.bits.id      
    datapath.io.icache_axi.rdata     <> axi3x1.io.in(2).r.bits.data    
    datapath.io.icache_axi.rresp     <> axi3x1.io.in(2).r.bits.resp    
    datapath.io.icache_axi.rlast     <> axi3x1.io.in(2).r.bits.last    
    datapath.io.icache_axi.rvalid    <> axi3x1.io.in(2).r.valid   
    datapath.io.icache_axi.rready    <> axi3x1.io.in(2).r.ready
    
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
}
