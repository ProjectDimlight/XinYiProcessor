package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._
import config.config._

// wrapper class for dual_port_ram
class DualPortBRAM(DATA_WIDTH: Int = XLEN, DEPTH: Int = 1024, LATENCY: Int = 1)
    extends BlackBox(
      Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH, "LATENCY" -> LATENCY)
    )
    with HasBlackBoxInline {

  override val desiredName = "dual_port_bram"

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Reset())
    val wea = Input(Bool())
    val web = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina = Input(UInt(DATA_WIDTH.W))
    val dinb = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))
  })

  setInline(
    "dual_port_ram.v",
    s"""
               |module dual_port_ram #(
               |	parameter DATA_WIDTH = 32,
               |	parameter DEPTH      = 1024,
               |	parameter LATENCY    = 1,
               |	parameter LATENCY_A  = LATENCY,
               |	parameter LATENCY_B  = LATENCY
               |) (
               |	input  clk,
               |	input  rst,
               |	input  wea,
               |	input  web,
               |	input  [$$clog2(DEPTH)-1:0] addra,
               |	input  [$$clog2(DEPTH)-1:0] addrb,
               |	input  [DATA_WIDTH-1:0] dina,
               |	input  [DATA_WIDTH-1:0] dinb,
               |	output [DATA_WIDTH-1:0] douta,
               |	output [DATA_WIDTH-1:0] doutb
               |);
               |
               |// xpm_memory_tdpram: True Dual Port RAM
               |// Xilinx Parameterized Macro, Version 2016.2
               |xpm_memory_tdpram #(
               |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
               |	.MEMORY_PRIMITIVE("auto"),
               |	.CLOCKING_MODE("common_clock"),
               |	.USE_MEM_INIT(0),
               |	.WAKEUP_TIME("disable_sleep"),
               |	.MESSAGE_CONTROL(0),
               |
               |	// Port A module parameters
               |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
               |	.READ_DATA_WIDTH_A(DATA_WIDTH),
               |	.READ_RESET_VALUE_A("0"),
               |	.READ_LATENCY_A(LATENCY_A),
               |	.WRITE_MODE_A("read_first"),
               |
               |	// Port B module parameters
               |	.WRITE_DATA_WIDTH_B(DATA_WIDTH),
               |	.READ_DATA_WIDTH_B(DATA_WIDTH),
               |	.READ_RESET_VALUE_B("0"),
               |	.READ_LATENCY_B(LATENCY_B),
               |	.WRITE_MODE_B("read_first")
               |) xpm_mem (
               |	.sleep          (1'b0 ),
               |	// Port A module ports
               |	.clka           (clk  ),
               |	.rsta           (rst  ),
               |	.ena            (1'b1 ),
               |	.regcea         (1'b0 ),
               |	.wea            (wea  ),
               |	.addra          (addra),
               |	.dina           (dina ),
               |	.injectsbiterra (1'b0 ),
               |	.injectdbiterra (1'b0 ),
               |	.douta          (douta),
               |	.sbiterra       (     ),
               |	.dbiterra       (     ),
               |
               |	// Port B module ports
               |	.clkb           (clk  ),
               |	.rstb           (rst  ),
               |	.enb            (1'b1 ),
               |	.regceb         (1'b0 ),
               |	.web            (web  ),
               |	.addrb          (addrb),
               |	.dinb           (dinb ),
               |	.injectsbiterrb (1'b0 ),
               |	.injectdbiterrb (1'b0 ),
               |	.doutb          (doutb),
               |	.sbiterrb       (     ),
               |	.dbiterrb       (     )
               |);
               |
               |endmodule
               |""".stripMargin
  )
}

// wrapper class for dual_port_lutram
class DualPortLUTRAM(
    DATA_WIDTH: Int = XLEN,
    DEPTH: Int = 1024,
    LATENCY: Int = 1
) extends BlackBox(
      Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH, "LATENCY" -> LATENCY)
    )
    with HasBlackBoxInline {

  override val desiredName = "dual_port_lutram"

  val io = IO(new Bundle {
    val clk = Input(Clock())
    val rst = Input(Reset())
    val wea = Input(Bool())
    val addra = Input(UInt(log2Ceil(DEPTH).W))
    val addrb = Input(UInt(log2Ceil(DEPTH).W))
    val dina = Input(UInt(DATA_WIDTH.W))
    val douta = Output(UInt(DATA_WIDTH.W))
    val doutb = Output(UInt(DATA_WIDTH.W))
  })

  setInline(
    "dual_port_lutram.v",
    s"""
               |module dual_port_lutram #(
               |	parameter DATA_WIDTH = 32,
               |	parameter DEPTH      = 1024,
               |	parameter LATENCY    = 1,
               |	parameter LATENCY_A  = LATENCY,
               |	parameter LATENCY_B  = LATENCY
               |) (
               |	input  clk,
               |	input  rst,
               |	input  wea,
               |	input  [$$clog2(DEPTH)-1:0] addra,
               |	input  [$$clog2(DEPTH)-1:0] addrb,
               |	input  [DATA_WIDTH-1:0] dina,
               |	output [DATA_WIDTH-1:0] douta,
               |	output [DATA_WIDTH-1:0] doutb
               |);
               |
               |xpm_memory_dpdistram #(
               |	// Common module parameters
               |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
               |	.CLOCKING_MODE("common_clock"),
               |	.USE_MEM_INIT(0),
               |	.MESSAGE_CONTROL(0),
               |
               |	// Port A module parameters
               |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
               |	.READ_DATA_WIDTH_A(DATA_WIDTH),
               |	.READ_RESET_VALUE_A("0"),
               |	.READ_LATENCY_A(LATENCY_A),
               |
               |	// Port B module parameters
               |	.READ_DATA_WIDTH_B(DATA_WIDTH),
               |	.READ_RESET_VALUE_B("0"),
               |	.READ_LATENCY_B(LATENCY_B)
               |) xpm_mem (
               |	// Port A module ports
               |	.clka           (clk  ),
               |	.rsta           (rst  ),
               |	.ena            (1'b1 ),
               |	.regcea         (1'b0 ),
               |	.wea            (wea  ),
               |	.addra          (addra),
               |	.dina           (dina ),
               |	.douta          (douta),
               |
               |	// Port B module ports
               |	.clkb           (clk  ),
               |	.rstb           (rst  ),
               |	.enb            (1'b1 ),
               |	.regceb         (1'b0 ),
               |	.addrb          (addrb),
               |	.doutb          (doutb)
               |);
               |
               |endmodule
               |""".stripMargin
  )
}

// wrapper class for single_port_ram
class SinglePortRAM(DATA_WIDTH: Int, DEPTH: Int, LATENCY: Int = 1)
    extends BlackBox(
      Map("DATA_WIDTH" -> DATA_WIDTH, "DEPTH" -> DEPTH, "LATENCY" -> LATENCY)
    )
    with HasBlackBoxInline {

  override val desiredName = "single_port_ram"

  val io = IO(new Bundle{
    val clk = Input(Clock())
    val rst = Input(Reset())
    val we  = Input(Bool())
    val addr = Input(UInt(log2Ceil(DEPTH).W))
    val din = Input(UInt(DATA_WIDTH.W))
    val dout = Output(UInt(DATA_WIDTH.W))
  })

  setInline(
    "single_port_ram.v",
    s"""
              |module single_port_ram # (
              | parameter DATA_WIDTH = 32,
              |	parameter DEPTH      = 1024,
              |	parameter LATENCY    = 1
              |)(
              |	input  clk,
              |	input  rst,
              |	input  we,
              |	input  [$$clog2(DEPTH)-1:0] addr,
              |	input  [DATA_WIDTH-1:0]  din,
              |	output [DATA_WIDTH-1:0]  dout
              |);
              |
              |// xpm_memory_spram: Single Port RAM
              |// Xilinx Parameterized Macro, Version 2016.2
              |xpm_memory_spram #(
              |	// Common module parameters
              |	.MEMORY_SIZE(DATA_WIDTH * DEPTH),
              |	.MEMORY_PRIMITIVE("auto"),
              |	.USE_MEM_INIT(0),
              |	.WAKEUP_TIME("disable_sleep"),
              |	.MESSAGE_CONTROL(0),
              |	// Port A module parameters
              |	.WRITE_DATA_WIDTH_A(DATA_WIDTH),
              |	.READ_DATA_WIDTH_A(DATA_WIDTH),
              |	.READ_RESET_VALUE_A("0"),
              |	.READ_LATENCY_A(LATENCY),
              |	.WRITE_MODE_A("write_first")
              |) xpm_mem (
              |	// Common module ports
              |	.sleep          ( 1'b0  ),
              |	// Port A module ports
              |	.clka           ( clk   ),
              |	.rsta           ( rst   ),
              |	.ena            ( 1'b1  ),
              |	.regcea         ( 1'b0  ),
              |	.wea            ( we    ),
              |	.addra          ( addr  ),
              |	.dina           ( din   ),
              |	.injectsbiterra ( 1'b0  ), // do not change
              |	.injectdbiterra ( 1'b0  ), // do not change
              |	.douta          ( dout  ),
              |	.sbiterra       (       ), // do not change
              |	.dbiterra       (       )  // do not change
              |);
              |
              |endmodule
              |""".stripMargin
  )
}
