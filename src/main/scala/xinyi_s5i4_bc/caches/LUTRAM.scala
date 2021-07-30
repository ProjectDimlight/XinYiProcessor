// package xinyi_s5i4_bc.caches

// import chisel3._
// import chisel3.util._
// import config.config._


// // wrapper class for dual_port_lutram
// class DualPortLUTRAM(DATA_WIDTH: Int = XLEN, DEPTH: Int = 1024, LATENCY: Int = 1) extends BlackBox(Map(
//   "DATA_WIDTH" -> DATA_WIDTH,
//   "DEPTH" -> DEPTH,
//   "LATENCY" -> LATENCY)) with HasBlackBoxInline {

//   override val desiredName = "dual_port_lutram"

//   val io = IO(new Bundle {
//     val clk   = Input(Clock())
//     val rst   = Input(Reset())
//     val wea   = Input(Bool())
//     val addra = Input(UInt(log2Ceil(DEPTH).W))
//     val addrb = Input(UInt(log2Ceil(DEPTH).W))
//     val dina  = Input(UInt(DATA_WIDTH.W))
//     val douta = Output(UInt(DATA_WIDTH.W))
//     val doutb = Output(UInt(DATA_WIDTH.W))
//   })


//   setInline("dual_port_lutram.v",
//     s"""
//        |module dual_port_lutram #(
//        |	parameter DATA_WIDTH = 32,
//        |	parameter DEPTH      = 1024,
//        |	parameter LATENCY    = 1,
//        |	parameter LATENCY_A  = LATENCY,
//        |	parameter LATENCY_B  = LATENCY
//        |) (
//        |	input  clk,
//        |	input  rst,
//        |	input  wea,
//        |	input  [$$clog2(DEPTH)-1:0] addra,
//        |	input  [$$clog2(DEPTH)-1:0] addrb,
//        |	input  [DATA_WIDTH-1:0] dina,
//        |	output [DATA_WIDTH-1:0] douta,
//        |	output [DATA_WIDTH-1:0] doutb
//        |);
//        |
//        |xpm_memory_dpdistram #(
//        |	// Common module parameters
//        |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
//        |	.CLOCKING_MODE("common_clock"),
//        |	.USE_MEM_INIT(0),
//        |	.MESSAGE_CONTROL(0),
//        |
//        |	// Port A module parameters
//        |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
//        |	.READ_DATA_WIDTH_A(DATA_WIDTH),
//        |	.READ_RESET_VALUE_A("0"),
//        |	.READ_LATENCY_A(LATENCY_A),
//        |
//        |	// Port B module parameters
//        |	.READ_DATA_WIDTH_B(DATA_WIDTH),
//        |	.READ_RESET_VALUE_B("0"),
//        |	.READ_LATENCY_B(LATENCY_B)
//        |) xpm_mem (
//        |	// Port A module ports
//        |	.clka           (clk  ),
//        |	.rsta           (rst  ),
//        |	.ena            (1'b1 ),
//        |	.regcea         (1'b0 ),
//        |	.wea            (wea  ),
//        |	.addra          (addra),
//        |	.dina           (dina ),
//        |	.douta          (douta),
//        |
//        |	// Port B module ports
//        |	.clkb           (clk  ),
//        |	.rstb           (rst  ),
//        |	.enb            (1'b1 ),
//        |	.regceb         (1'b0 ),
//        |	.addrb          (addrb),
//        |	.doutb          (doutb)
//        |);
//        |
//        |endmodule
//        |""".stripMargin)
// }
