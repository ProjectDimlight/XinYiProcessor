module IFStage(
  input         clock,
  input         reset,
  input  [31:0] io_in_pc,
  input  [31:0] io_bc_pc,
  input  [31:0] io_bc_inst,
  input         io_bc_branch_cache_overwrite,
  output [31:0] io_cache_addr,
  output [31:0] io_cache_din,
  input  [31:0] io_cache_dout,
  output [31:0] io_out_pc,
  output [31:0] io_out_inst
);
  assign io_cache_addr = io_in_pc; // @[Stages.scala 36:17]
  assign io_cache_din = 32'h0; // @[Stages.scala 35:17]
  assign io_out_pc = io_bc_branch_cache_overwrite ? io_bc_pc : io_in_pc; // @[Stages.scala 39:15 Stages.scala 42:15]
  assign io_out_inst = io_bc_branch_cache_overwrite ? io_bc_inst : io_cache_dout; // @[Stages.scala 40:17 Stages.scala 43:17]
endmodule
