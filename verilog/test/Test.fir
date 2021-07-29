;buildInfoPackage: chisel3, version: 3.4.3, scalaVersion: 2.12.12, sbtVersion: 1.3.10
circuit Test : 
  module Test : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in1 : UInt<8>, flip in2 : UInt<8>, flip in3 : UInt<8>, flip in4 : UInt<8>, flip in5 : UInt<8>, flip path : UInt<5>, out : UInt<8>}
    
    node _io_out_T = bits(io.path, 0, 0) @[Mux.scala 29:36]
    node _io_out_T_1 = bits(io.path, 1, 1) @[Mux.scala 29:36]
    node _io_out_T_2 = bits(io.path, 2, 2) @[Mux.scala 29:36]
    node _io_out_T_3 = bits(io.path, 3, 3) @[Mux.scala 29:36]
    node _io_out_T_4 = bits(io.path, 4, 4) @[Mux.scala 29:36]
    node _io_out_T_5 = mux(_io_out_T, io.in1, UInt<1>("h00")) @[Mux.scala 27:72]
    node _io_out_T_6 = mux(_io_out_T_1, io.in2, UInt<1>("h00")) @[Mux.scala 27:72]
    node _io_out_T_7 = mux(_io_out_T_2, io.in3, UInt<1>("h00")) @[Mux.scala 27:72]
    node _io_out_T_8 = mux(_io_out_T_3, io.in4, UInt<1>("h00")) @[Mux.scala 27:72]
    node _io_out_T_9 = mux(_io_out_T_4, io.in5, UInt<1>("h00")) @[Mux.scala 27:72]
    node _io_out_T_10 = or(_io_out_T_5, _io_out_T_6) @[Mux.scala 27:72]
    node _io_out_T_11 = or(_io_out_T_10, _io_out_T_7) @[Mux.scala 27:72]
    node _io_out_T_12 = or(_io_out_T_11, _io_out_T_8) @[Mux.scala 27:72]
    node _io_out_T_13 = or(_io_out_T_12, _io_out_T_9) @[Mux.scala 27:72]
    wire _io_out_WIRE : UInt<8> @[Mux.scala 27:72]
    _io_out_WIRE <= _io_out_T_13 @[Mux.scala 27:72]
    io.out <= _io_out_WIRE @[Test.scala 33:10]
    
