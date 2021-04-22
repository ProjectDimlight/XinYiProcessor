package xinyi_s5i4_bc

import chisel3._
import config.config._

class S5I4 extends MultiIOModule with PortConfig {
  override val desiredName = s"mycpu_top"

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

  //debug interface
  //val debug_wb_pc       = IO(Output(UInt(32.W)))
  //val debug_wb_rf_wen   = IO(Output(UInt(4.W)))
  //val debug_wb_rf_wnum  = IO(Output(UInt(5.W)))
  //val debug_wb_rf_wdata = IO(Output(UInt(32.W)))

  val datapath = Module(new DataPath)
  val axi3x1   = Module(new CPUAXI3x1)
  
  datapath.io.interrupt := ext_int
  
  axi3x1.io.i_addr_in  :=datapath.io.icache_axi.addr_in
  axi3x1.io.i_en       :=datapath.io.icache_axi.en
  axi3x1.io.i_addr_out :=datapath.io.icache_axi.addr_out
  axi3x1.io.i_data     :=datapath.io.icache_axi.data
  axi3x1.io.i_stall    :=datapath.io.icache_axi.stall
  axi3x1.io.i_valid    :=datapath.io.icache_axi.valid

  axi3x1.io.d_0_addr_in     :=datapath.io.dcache_axi(0).addr_in    
  axi3x1.io.d_0_data_in     :=datapath.io.dcache_axi(0).data_in    
  axi3x1.io.d_0_wr          :=datapath.io.dcache_axi(0).wr         
  axi3x1.io.d_0_rd          :=datapath.io.dcache_axi(0).rd         
  axi3x1.io.d_0_size        :=datapath.io.dcache_axi(0).size       
  axi3x1.io.d_0_addr_out    :=datapath.io.dcache_axi(0).addr_out   
  axi3x1.io.d_0_data_out    :=datapath.io.dcache_axi(0).data_out   
  axi3x1.io.d_0_stall       :=datapath.io.dcache_axi(0).stall      
  axi3x1.io.d_0_valid       :=datapath.io.dcache_axi(0).valid      

  axi3x1.io.d_1_addr_in     :=datapath.io.dcache_axi(1).addr_in    
  axi3x1.io.d_1_data_in     :=datapath.io.dcache_axi(1).data_in    
  axi3x1.io.d_1_wr          :=datapath.io.dcache_axi(1).wr         
  axi3x1.io.d_1_rd          :=datapath.io.dcache_axi(1).rd         
  axi3x1.io.d_1_size        :=datapath.io.dcache_axi(1).size       
  axi3x1.io.d_1_addr_out    :=datapath.io.dcache_axi(1).addr_out   
  axi3x1.io.d_1_data_out    :=datapath.io.dcache_axi(1).data_out   
  axi3x1.io.d_1_stall       :=datapath.io.dcache_axi(1).stall      
  axi3x1.io.d_1_valid       :=datapath.io.dcache_axi(1).valid      

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
}
