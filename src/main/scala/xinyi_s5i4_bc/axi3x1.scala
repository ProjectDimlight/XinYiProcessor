package xinyi_s5i4_bc

import chisel3._
import config.config._

trait PortConfig {
  val L1_W = 64
  val L2_W = 32
  val AXI_R_ID_WIDTH  = 4
  val AXI_W_ID_WIDTH  = 4
  val AXI_RQUEUE_SIZE = 4
}

class CPUAXI3x1 extends BlackBox with PortConfig {
  override val desiredName = s"AXI_complex"

  val io = IO(new Bundle{
    //i
    val i_addr_in    = Input(UInt(PHY_ADDR_W.W))
    val i_en         = Input(Bool())
    val i_addr_out   = Output(UInt(PHY_ADDR_W.W))
    val i_data       = Output(UInt(L1_W.W))
    val i_stall      = Output(Bool())
    val i_valid      = Output(Bool())
    // d_0
    val d_0_addr_in  = Input(UInt(PHY_ADDR_W.W))
    val d_0_data_in  = Input(UInt(L1_W.W))
    val d_0_wr       = Input(Bool())
    val d_0_rd       = Input(Bool())
    val d_0_size     = Input(UInt(3.W))
    val d_0_addr_out = Output(UInt(PHY_ADDR_W.W))
    val d_0_data_out = Output(UInt(L1_W.W))
    val d_0_stall    = Output(Bool())
    val d_0_valid    = Output(Bool())
    // d_1
    val d_1_addr_in  = Input(UInt(PHY_ADDR_W.W))
    val d_1_data_in  = Input(UInt(L1_W.W))
    val d_1_wr       = Input(Bool())
    val d_1_rd       = Input(Bool())
    val d_1_size     = Input(UInt(3.W))
    val d_1_addr_out = Output(UInt(PHY_ADDR_W.W))
    val d_1_data_out = Output(UInt(L1_W.W))
    val d_1_stall    = Output(Bool())
    val d_1_valid    = Output(Bool())
    // axi
    // ar
    val arid         = Output(UInt(AXI_R_ID_WIDTH.W))
    val araddr       = Output(UInt(PHY_ADDR_W.W))
    val arlen        = Output(UInt(8.W))
    val arsize       = Output(UInt(3.W))
    val arburst      = Output(UInt(2.W))
    val arlock       = Output(UInt(2.W))
    val arcache      = Output(UInt(4.W))
    val arprot       = Output(UInt(3.W))
    val arvalid      = Output(Bool())
    val arready      = Input(Bool())
    //r           
    val rid          = Input(UInt(AXI_R_ID_WIDTH.W))
    val rdata        = Input(UInt(L2_W.W))
    val rresp        = Input(UInt(2.W))
    val rlast        = Input(Bool())
    val rvalid       = Input(Bool())
    val rready       = Output(Bool())
    //aw          
    val awid         = Output(UInt(AXI_W_ID_WIDTH.W))
    val awaddr       = Output(UInt(PHY_ADDR_W.W))
    val awlen        = Output(UInt(8.W))
    val awsize       = Output(UInt(3.W))
    val awburst      = Output(UInt(2.W))
    val awlock       = Output(UInt(2.W))
    val awcache      = Output(UInt(4.W))
    val awprot       = Output(UInt(3.W))
    val awvalid      = Output(Bool())
    val awready      = Input(Bool())
    //w          
    val wid          = Output(UInt(AXI_W_ID_WIDTH.W))
    val wdata        = Output(UInt(L2_W.W))
    val wstrb        = Output(UInt((L2_W / 8).W))
    val wlast        = Output(Bool())
    val wvalid       = Output(Bool())
    val wready       = Input(Bool())
    //b           
    val bid          = Input(UInt(AXI_W_ID_WIDTH.W))
    val bresp        = Input(UInt(2.W))
    val bvalid       = Input(Bool())
    val bready       = Output(Bool())
  })
}