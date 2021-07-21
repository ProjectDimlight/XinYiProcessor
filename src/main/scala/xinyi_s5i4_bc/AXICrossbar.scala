package xinyi_s5i4_bc

import chisel3._
import config.config._


// This bundle is for exposing IO interface to AXI Crossbar IP
class AXIIO extends Bundle {
  // ar
  val arid    = Output(UInt(4.W))
  val araddr  = Output(UInt(XLEN.W))
  val arlen   = Output(UInt(4.W))
  val arsize  = Output(UInt(3.W))
  val arburst = Output(UInt(2.W))
  val arlock  = Output(UInt(2.W))
  val arcache = Output(UInt(4.W))
  val arprot  = Output(UInt(3.W))
  val arvalid = Output(Bool())
  val arready = Input(Bool())
  //r
  val rid     = Input(UInt(4.W))
  val rdata   = Input(UInt(XLEN.W))
  val rresp   = Input(UInt(2.W))
  val rlast   = Input(Bool())
  val rvalid  = Input(Bool())
  val rready  = Output(Bool())
  //aw
  val awid    = Output(UInt(4.W))
  val awaddr  = Output(UInt(XLEN.W))
  val awlen   = Output(UInt(4.W))
  val awsize  = Output(UInt(3.W))
  val awburst = Output(UInt(2.W))
  val awlock  = Output(UInt(2.W))
  val awcache = Output(UInt(4.W))
  val awprot  = Output(UInt(3.W))
  val awvalid = Output(Bool())
  val awready = Input(Bool())
  //w
  val wid     = Output(UInt(4.W))
  val wdata   = Output(UInt(XLEN.W))
  val wstrb   = Output(UInt(4.W))
  val wlast   = Output(Bool())
  val wvalid  = Output(Bool())
  val wready  = Input(Bool())
  //b
  val bid     = Input(UInt(4.W))
  val bresp   = Input(UInt(2.W))
  val bvalid  = Input(Bool())
  val bready  = Output(Bool())
}


// This is the wrapper module for AXICrossbar IP core.
class AXICrossbar extends BlackBox {
  val io = IO(new Bundle {
    val aclk           = Input(Clock())
    val aresetn        = Input(Bool())

    // s
    val s_axi_awid     = Input(UInt(12.W))
    val s_axi_awaddr   = Input(UInt((3 * XLEN).W))
    val s_axi_awlen    = Input(UInt(24.W))
    val s_axi_awsize   = Input(UInt(9.W))
    val s_axi_awburst  = Input(UInt(6.W))
    val s_axi_awlock   = Input(UInt(3.W))
    val s_axi_awcache  = Input(UInt(12.W))
    val s_axi_awprot   = Input(UInt(9.W))
    val s_axi_awvalid  = Input(UInt(9.W))
    val s_axi_awready  = Output(UInt(3.W))
    val s_axi_wid      = Input(UInt(12.W))
    val s_axi_wdata    = Input(UInt((3 * XLEN).W))
    val s_axi_wstrb    = Input(UInt(12.W))
    val s_axi_wlast    = Input(UInt(3.W))
    val s_axi_wvalid   = Input(UInt(3.W))
    val s_axi_wready   = Output(UInt(3.W))
    val s_axi_bid      = Output(UInt(12.W))
    val s_axi_bresp    = Output(UInt(6.W))
    val s_axi_bvalid   = Output(UInt(3.W))
    val s_axi_bready   = Input(UInt(3.W))
    val s_axi_arid     = Input(UInt(12.W))
    val s_axi_araddr   = Input(UInt((3 * XLEN).W))
    val s_axi_arlen    = Input(UInt(24.W))
    val s_axi_arsize   = Input(UInt(9.W))
    val s_axi_arburst  = Input(UInt(6.W))
    val s_axi_arlock   = Input(UInt(3.W))
    val s_axi_arcache  = Input(UInt(12.W))
    val s_axi_arprot   = Input(UInt(9.W))
    val s_axi_arqos    = Input(UInt(12.W))
    val s_axi_arvalid  = Input(UInt(3.W))
    val s_axi_arready  = Output(UInt(3.W))
    val s_axi_rid      = Output(UInt(12.W))
    val s_axi_rdata    = Output(UInt((3 * XLEN).W))
    val s_axi_rresp    = Output(UInt(6.W))
    val s_axi_rlast    = Output(UInt(3.W))
    val s_axi_rvalid   = Output(UInt(3.W))
    val s_axi_rready   = Input(UInt(3.W))


    // m
    val m_axi_awid     = Output(UInt(4.W))
    val m_axi_awaddr   = Output(UInt(XLEN.W))
    val m_axi_awlen    = Output(UInt(4.W))
    val m_axi_awsize   = Output(UInt(3.W))
    val m_axi_awburst  = Output(UInt(2.W))
    val m_axi_awlock   = Output(Bool())
    val m_axi_awcache  = Output(UInt(4.W))
    val m_axi_awprot   = Output(UInt(3.W))
    val m_axi_awvalid  = Output(Bool())
    val m_axi_awready  = Input(Bool())
    val m_axi_wid      = Output(UInt(4.W))
    val m_axi_wdata    = Output(UInt(XLEN.W))
    val m_axi_wstrb    = Output(UInt(4.W))
    val m_axi_wlast    = Output(Bool())
    val m_axi_wvalid   = Output(Bool())
    val m_axi_wready   = Input(Bool())
    val m_axi_bid      = Input(UInt(4.W))
    val m_axi_bresp    = Input(UInt(2.W))
    val m_axi_bvalid   = Input(Bool())
    val m_axi_bready   = Output(Bool())
    val m_axi_arid     = Output(UInt(4.W))
    val m_axi_araddr   = Output(UInt(XLEN.W))
    val m_axi_arlen    = Output(UInt(8.W))
    val m_axi_arsize   = Output(UInt(3.W))
    val m_axi_arburst  = Output(UInt(2.W))
    val m_axi_arlock   = Output(Bool())
    val m_axi_arcache  = Output(UInt(4.W))
    val m_axi_arprot   = Output(UInt(3.W))
    val m_axi_arvalid  = Output(Bool())
    val m_axi_arready  = Input(Bool())
    val m_axi_rid      = Input(UInt(4.W))
    val m_axi_rdata    = Input(UInt(XLEN.W))
    val m_axi_rresp    = Input(UInt(2.W))
    val m_axi_rlast    = Input(Bool())
    val m_axi_rvalid   = Input(Bool())
    val m_axi_rready   = Output(Bool())
  })
}
