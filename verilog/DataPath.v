module DataPath(
  input         clock,
  input         reset,
  output [31:0] io_debug_pc,
  output [31:0] io_debug_inst
);
  assign io_debug_pc = 32'h80000000; // @[DataPath.scala 26:12]
  assign io_debug_inst = 32'h0; // @[DataPath.scala 26:12]
endmodule
