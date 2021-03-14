;buildInfoPackage: chisel3, version: 3.3.1, scalaVersion: 2.12.11, sbtVersion: 1.3.10
circuit IFStage : 
  module IFStage : 
    input clock : Clock
    input reset : UInt<1>
    output io : {in : {flip pc : UInt<32>}, flip bc : {pc : UInt<32>, inst : UInt<32>, branch_cache_overwrite : UInt<1>}, flip cache : {flip addr : UInt<32>, flip din : UInt<32>, dout : UInt<32>}, out : {pc : UInt<32>, inst : UInt<32>}}
    
    io.cache.din <= UInt<32>("h00") @[Stages.scala 35:17]
    io.cache.addr <= io.in.pc @[Stages.scala 36:17]
    when io.bc.branch_cache_overwrite : @[Stages.scala 38:39]
      io.out.pc <= io.bc.pc @[Stages.scala 39:15]
      io.out.inst <= io.bc.inst @[Stages.scala 40:17]
      skip @[Stages.scala 38:39]
    else : @[Stages.scala 41:16]
      io.out.pc <= io.in.pc @[Stages.scala 42:15]
      io.out.inst <= io.cache.dout @[Stages.scala 43:17]
      skip @[Stages.scala 41:16]
    
