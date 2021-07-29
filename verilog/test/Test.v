module Test(
  input        clock,
  input        reset,
  input  [7:0] io_in1,
  input  [7:0] io_in2,
  input  [7:0] io_in3,
  input  [7:0] io_in4,
  input  [7:0] io_in5,
  input  [4:0] io_path,
  output [7:0] io_out
);
  wire [7:0] _io_out_T_5 = io_path[0] ? io_in1 : 8'h0; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_6 = io_path[1] ? io_in2 : 8'h0; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_7 = io_path[2] ? io_in3 : 8'h0; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_8 = io_path[3] ? io_in4 : 8'h0; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_9 = io_path[4] ? io_in5 : 8'h0; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_10 = _io_out_T_5 | _io_out_T_6; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_11 = _io_out_T_10 | _io_out_T_7; // @[Mux.scala 27:72]
  wire [7:0] _io_out_T_12 = _io_out_T_11 | _io_out_T_8; // @[Mux.scala 27:72]
  assign io_out = _io_out_T_12 | _io_out_T_9; // @[Mux.scala 27:72]
endmodule
