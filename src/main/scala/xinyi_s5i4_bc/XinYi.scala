package xinyi_s5i4_bc

import chisel3._
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
  val rdata        = IO(Input(UInt(L1_W.W)))
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
  val wdata        = IO(Output(UInt(L1_W.W)))
  val wstrb        = IO(Output(UInt((L1_W/8).W)))
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

  withClockAndReset(aclk, ~aresetn) {
    val axi3x1   = Module(new AXICrossbar)
    val datapath = Module(new DataPath)
    for (i <- 0 to 5) {
      datapath.io.interrupt(i) := ext_int(i)
    }
    debug_pc := datapath.io.debug_pc

    axi3x1.io.s_axi_awid      <> Cat(datapath.io.icache_axi.awid     , datapath.io.dcache_axi(0).awid     , datapath.io.dcache_axi(1).awid     )
    axi3x1.io.s_axi_awaddr    <> Cat(datapath.io.icache_axi.awaddr   , datapath.io.dcache_axi(0).awaddr   , datapath.io.dcache_axi(1).awaddr   )
    axi3x1.io.s_axi_awlen     <> Cat(datapath.io.icache_axi.awlen    , datapath.io.dcache_axi(0).awlen    , datapath.io.dcache_axi(1).awlen    )
    axi3x1.io.s_axi_awsize    <> Cat(datapath.io.icache_axi.awsize   , datapath.io.dcache_axi(0).awsize   , datapath.io.dcache_axi(1).awsize   )
    axi3x1.io.s_axi_awburst   <> Cat(datapath.io.icache_axi.awburst  , datapath.io.dcache_axi(0).awburst  , datapath.io.dcache_axi(1).awburst  )
    axi3x1.io.s_axi_awlock    <> Cat(datapath.io.icache_axi.awlock   , datapath.io.dcache_axi(0).awlock   , datapath.io.dcache_axi(1).awlock   )
    axi3x1.io.s_axi_awcache   <> Cat(datapath.io.icache_axi.awcache  , datapath.io.dcache_axi(0).awcache  , datapath.io.dcache_axi(1).awcache  )
    axi3x1.io.s_axi_awprot    <> Cat(datapath.io.icache_axi.awprot   , datapath.io.dcache_axi(0).awprot   , datapath.io.dcache_axi(1).awprot   )
    axi3x1.io.s_axi_awregion  <> Cat(datapath.io.icache_axi.awregion , datapath.io.dcache_axi(0).awregion , datapath.io.dcache_axi(1).awregion )
    axi3x1.io.s_axi_awqos     <> Cat(datapath.io.icache_axi.awqos    , datapath.io.dcache_axi(0).awqos    , datapath.io.dcache_axi(1).awqos    )
    axi3x1.io.s_axi_awvalid   <> Cat(datapath.io.icache_axi.awvalid  , datapath.io.dcache_axi(0).awvalid  , datapath.io.dcache_axi(1).awvalid  )
    axi3x1.io.s_axi_awready   <> Cat(datapath.io.icache_axi.awready  , datapath.io.dcache_axi(0).awready  , datapath.io.dcache_axi(1).awready  )
    axi3x1.io.s_axi_wdata     <> Cat(datapath.io.icache_axi.wdata    , datapath.io.dcache_axi(0).wdata    , datapath.io.dcache_axi(1).wdata    )
    axi3x1.io.s_axi_wstrb     <> Cat(datapath.io.icache_axi.wstrb    , datapath.io.dcache_axi(0).wstrb    , datapath.io.dcache_axi(1).wstrb    )
    axi3x1.io.s_axi_wlast     <> Cat(datapath.io.icache_axi.wlast    , datapath.io.dcache_axi(0).wlast    , datapath.io.dcache_axi(1).wlast    )
    axi3x1.io.s_axi_wvalid    <> Cat(datapath.io.icache_axi.wvalid   , datapath.io.dcache_axi(0).wvalid   , datapath.io.dcache_axi(1).wvalid   )
    axi3x1.io.s_axi_wready    <> Cat(datapath.io.icache_axi.wready   , datapath.io.dcache_axi(0).wready   , datapath.io.dcache_axi(1).wready   )
    axi3x1.io.s_axi_bid       <> Cat(datapath.io.icache_axi.bid      , datapath.io.dcache_axi(0).bid      , datapath.io.dcache_axi(1).bid      )
    axi3x1.io.s_axi_bresp     <> Cat(datapath.io.icache_axi.bresp    , datapath.io.dcache_axi(0).bresp    , datapath.io.dcache_axi(1).bresp    )
    axi3x1.io.s_axi_bvalid    <> Cat(datapath.io.icache_axi.bvalid   , datapath.io.dcache_axi(0).bvalid   , datapath.io.dcache_axi(1).bvalid   )
    axi3x1.io.s_axi_bready    <> Cat(datapath.io.icache_axi.bready   , datapath.io.dcache_axi(0).bready   , datapath.io.dcache_axi(1).bready   )
    axi3x1.io.s_axi_arid      <> Cat(datapath.io.icache_axi.arid     , datapath.io.dcache_axi(0).arid     , datapath.io.dcache_axi(1).arid     )
    axi3x1.io.s_axi_araddr    <> Cat(datapath.io.icache_axi.araddr   , datapath.io.dcache_axi(0).araddr   , datapath.io.dcache_axi(1).araddr   )
    axi3x1.io.s_axi_arlen     <> Cat(datapath.io.icache_axi.arlen    , datapath.io.dcache_axi(0).arlen    , datapath.io.dcache_axi(1).arlen    )
    axi3x1.io.s_axi_arsize    <> Cat(datapath.io.icache_axi.arsize   , datapath.io.dcache_axi(0).arsize   , datapath.io.dcache_axi(1).arsize   )
    axi3x1.io.s_axi_arburst   <> Cat(datapath.io.icache_axi.arburst  , datapath.io.dcache_axi(0).arburst  , datapath.io.dcache_axi(1).arburst  )
    axi3x1.io.s_axi_arlock    <> Cat(datapath.io.icache_axi.arlock   , datapath.io.dcache_axi(0).arlock   , datapath.io.dcache_axi(1).arlock   )
    axi3x1.io.s_axi_arcache   <> Cat(datapath.io.icache_axi.arcache  , datapath.io.dcache_axi(0).arcache  , datapath.io.dcache_axi(1).arcache  )
    axi3x1.io.s_axi_arprot    <> Cat(datapath.io.icache_axi.arprot   , datapath.io.dcache_axi(0).arprot   , datapath.io.dcache_axi(1).arprot   )
    axi3x1.io.s_axi_arregion  <> Cat(datapath.io.icache_axi.arregion , datapath.io.dcache_axi(0).arregion , datapath.io.dcache_axi(1).arregion )
    axi3x1.io.s_axi_arqos     <> Cat(datapath.io.icache_axi.arqos    , datapath.io.dcache_axi(0).arqos    , datapath.io.dcache_axi(1).arqos    )
    axi3x1.io.s_axi_arvalid   <> Cat(datapath.io.icache_axi.arvalid  , datapath.io.dcache_axi(0).arvalid  , datapath.io.dcache_axi(1).arvalid  )
    axi3x1.io.s_axi_arready   <> Cat(datapath.io.icache_axi.arready  , datapath.io.dcache_axi(0).arready  , datapath.io.dcache_axi(1).arready  )
    axi3x1.io.s_axi_rid       <> Cat(datapath.io.icache_axi.rid      , datapath.io.dcache_axi(0).rid      , datapath.io.dcache_axi(1).rid      )
    axi3x1.io.s_axi_rdata     <> Cat(datapath.io.icache_axi.rdata    , datapath.io.dcache_axi(0).rdata    , datapath.io.dcache_axi(1).rdata    )
    axi3x1.io.s_axi_rresp     <> Cat(datapath.io.icache_axi.rresp    , datapath.io.dcache_axi(0).rresp    , datapath.io.dcache_axi(1).rresp    )
    axi3x1.io.s_axi_rlast     <> Cat(datapath.io.icache_axi.rlast    , datapath.io.dcache_axi(0).rlast    , datapath.io.dcache_axi(1).rlast    )
    axi3x1.io.s_axi_rvalid    <> Cat(datapath.io.icache_axi.rvalid   , datapath.io.dcache_axi(0).rvalid   , datapath.io.dcache_axi(1).rvalid   )
    axi3x1.io.s_axi_rready    <> Cat(datapath.io.icache_axi.rready   , datapath.io.dcache_axi(0).rready   , datapath.io.dcache_axi(1).rready   )
    
    awid      <> axi3x1.io.m_axi_awid     
    awaddr    <> axi3x1.io.m_axi_awaddr   
    awlen     <> axi3x1.io.m_axi_awlen    
    awsize    <> axi3x1.io.m_axi_awsize   
    awburst   <> axi3x1.io.m_axi_awburst  
    awlock    <> axi3x1.io.m_axi_awlock   
    awcache   <> axi3x1.io.m_axi_awcache  
    awprot    <> axi3x1.io.m_axi_awprot   
    awregion  <> axi3x1.io.m_axi_awregion 
    awqos     <> axi3x1.io.m_axi_awqos    
    awvalid   <> axi3x1.io.m_axi_awvalid  
    awready   <> axi3x1.io.m_axi_awready  
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
    arregion  <> axi3x1.io.m_axi_arregion 
    arqos     <> axi3x1.io.m_axi_arqos    
    arvalid   <> axi3x1.io.m_axi_arvalid  
    arready   <> axi3x1.io.m_axi_arready  
    rid       <> axi3x1.io.m_axi_rid      
    rdata     <> axi3x1.io.m_axi_rdata    
    rresp     <> axi3x1.io.m_axi_rresp    
    rlast     <> axi3x1.io.m_axi_rlast    
    rvalid    <> axi3x1.io.m_axi_rvalid   
    rready    <> axi3x1.io.m_axi_rready   
  }

  /*
  val axi3x1   = Module(new CPUAXI3x1)
  withClockAndReset(aclk, ~aresetn) {
    val datapath = Module(new DataPath)
    for (i <- 0 to 5) {
      datapath.io.interrupt(i) := ext_int(i)
    }
    debug_pc := datapath.io.debug_pc

    axi3x1.io.i_addr_in       <> datapath.io.icache_axi.addr_in
    axi3x1.io.i_en            <> datapath.io.icache_axi.en
    axi3x1.io.i_addr_out      <> datapath.io.icache_axi.addr_out
    axi3x1.io.i_data          <> datapath.io.icache_axi.data
    axi3x1.io.i_stall         <> datapath.io.icache_axi.stall
    axi3x1.io.i_valid         <> datapath.io.icache_axi.valid

    axi3x1.io.d_0_addr_in     <> datapath.io.dcache_axi(()0).addr_in
    axi3x1.io.d_0_data_in     <> datapath.io.dcache_axi(()0).data_in
    axi3x1.io.d_0_wr          <> datapath.io.dcache_axi(()0).wr
    axi3x1.io.d_0_rd          <> datapath.io.dcache_axi(()0).rd
    axi3x1.io.d_0_size        <> datapath.io.dcache_axi(()0).size
    axi3x1.io.d_0_addr_out    <> datapath.io.dcache_axi(()0).addr_out
    axi3x1.io.d_0_data_out    <> datapath.io.dcache_axi(()0).data_out
    axi3x1.io.d_0_stall       <> datapath.io.dcache_axi(()0).stall
    axi3x1.io.d_0_valid       <> datapath.io.dcache_axi(()0).valid

    axi3x1.io.d_1_addr_in     <> datapath.io.dcache_axi(()1).addr_in
    axi3x1.io.d_1_data_in     <> datapath.io.dcache_axi(()1).data_in
    axi3x1.io.d_1_wr          <> datapath.io.dcache_axi(()1).wr
    axi3x1.io.d_1_rd          <> datapath.io.dcache_axi(()1).rd
    axi3x1.io.d_1_size        <> datapath.io.dcache_axi(()1).size
    axi3x1.io.d_1_addr_out    <> datapath.io.dcache_axi(()1).addr_out
    axi3x1.io.d_1_data_out    <> datapath.io.dcache_axi(()1).data_out
    axi3x1.io.d_1_stall       <> datapath.io.dcache_axi(()1).stall
    axi3x1.io.d_1_valid       <> datapath.io.dcache_axi(()1).valid
  }

  debug_wb_pc       := 0.U
  debug_wb_rf_wen   := 0.U
  debug_wb_rf_wnum  := 0.U
  debug_wb_rf_wdata := 0.U

  axi3x1.io.clk             <> aclk
  axi3x1.io.rst             <> ~aresetn

//////////////////////////////////////////////////////////////////////

  // ar
  arid         <> axi3x1.io.arid
  araddr       <> axi3x1.io.araddr
  arlen        <> axi3x1.io.arlen
  arsize       <> axi3x1.io.arsize
  arburst      <> axi3x1.io.arburst
  arlock       <> axi3x1.io.arlock
  arcache      <> axi3x1.io.arcache
  arprot       <> axi3x1.io.arprot
  arvalid      <> axi3x1.io.arvalid
  arready      <> axi3x1.io.arready
  //r
  rid          <> axi3x1.io.rid
  rdata        <> axi3x1.io.rdata
  rresp        <> axi3x1.io.rresp
  rlast        <> axi3x1.io.rlast
  rvalid       <> axi3x1.io.rvalid
  rready       <> axi3x1.io.rready
  //aw
  awid         <> axi3x1.io.awid
  awaddr       <> axi3x1.io.awaddr
  awlen        <> axi3x1.io.awlen
  awsize       <> axi3x1.io.awsize
  awburst      <> axi3x1.io.awburst
  awlock       <> axi3x1.io.awlock
  awcache      <> axi3x1.io.awcache
  awprot       <> axi3x1.io.awprot
  awvalid      <> axi3x1.io.awvalid
  awready      <> axi3x1.io.awready
  //w
  wid          <> axi3x1.io.wid
  wdata        <> axi3x1.io.wdata
  wstrb        <> axi3x1.io.wstrb
  wlast        <> axi3x1.io.wlast
  wvalid       <> axi3x1.io.wvalid
  wready       <> axi3x1.io.wready
  //b
  bid          <> axi3x1.io.bid
  bresp        <> axi3x1.io.bresp
  bvalid       <> axi3x1.io.bvalid
  bready       <> axi3x1.io.bready
  */
}
