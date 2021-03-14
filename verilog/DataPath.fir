;buildInfoPackage: chisel3, version: 3.3.1, scalaVersion: 2.12.11, sbtVersion: 1.3.10
circuit DataPath : 
  module PCIFReg : 
    input clock : Clock
    input reset : Reset
    output io : {flip if_in : {flip pc : UInt<32>}}
    
    reg pc : UInt<32>, clock with : (reset => (reset, UInt<32>("h080000000"))) @[StageRegs.scala 11:19]
    io.if_in.pc <= pc @[StageRegs.scala 13:15]
    
  module IFIDReg : 
    input clock : Clock
    input reset : Reset
    output io : {flip if_out : {pc : UInt<32>, inst : UInt<32>}}
    
    reg reg : {pc : UInt<32>, inst : UInt<32>}, clock @[StageRegs.scala 21:20]
    reg.inst <= io.if_out.inst @[StageRegs.scala 21:20]
    reg.pc <= io.if_out.pc @[StageRegs.scala 21:20]
    
  module BranchCache : 
    input clock : Clock
    input reset : Reset
    output io : {inst_if : {pc : UInt<32>, inst : UInt<32>, branch_cache_overwrite : UInt<1>}}
    
    io.inst_if.pc <= UInt<32>("h00") @[BranchCache.scala 11:17]
    io.inst_if.inst <= UInt<32>("h00") @[BranchCache.scala 12:19]
    io.inst_if.branch_cache_overwrite <= UInt<1>("h00") @[BranchCache.scala 13:37]
    
  module ICache : 
    input clock : Clock
    input reset : Reset
    output io : {cpu : {flip addr : UInt<32>, flip din : UInt<32>, dout : UInt<32>}}
    
    io.cpu.dout <= UInt<32>("h00") @[ICache.scala 11:15]
    
  module IFStage : 
    input clock : Clock
    input reset : Reset
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
    
  module DataPath : 
    input clock : Clock
    input reset : UInt<1>
    output io : {debug : {pc : UInt<32>, inst : UInt<32>}}
    
    inst pc_if_reg of PCIFReg @[DataPath.scala 13:25]
    pc_if_reg.clock <= clock
    pc_if_reg.reset <= reset
    inst if_id_reg of IFIDReg @[DataPath.scala 14:25]
    if_id_reg.clock <= clock
    if_id_reg.reset <= reset
    inst branch_cache of BranchCache @[DataPath.scala 16:28]
    branch_cache.clock <= clock
    branch_cache.reset <= reset
    inst icache of ICache @[DataPath.scala 17:22]
    icache.clock <= clock
    icache.reset <= reset
    inst if_stage of IFStage @[DataPath.scala 19:24]
    if_stage.clock <= clock
    if_stage.reset <= reset
    if_stage.io.in.pc <= pc_if_reg.io.if_in.pc @[DataPath.scala 21:18]
    if_stage.io.bc.branch_cache_overwrite <= branch_cache.io.inst_if.branch_cache_overwrite @[DataPath.scala 22:18]
    if_stage.io.bc.inst <= branch_cache.io.inst_if.inst @[DataPath.scala 22:18]
    if_stage.io.bc.pc <= branch_cache.io.inst_if.pc @[DataPath.scala 22:18]
    if_stage.io.cache.dout <= icache.io.cpu.dout @[DataPath.scala 23:21]
    icache.io.cpu.din <= if_stage.io.cache.din @[DataPath.scala 23:21]
    icache.io.cpu.addr <= if_stage.io.cache.addr @[DataPath.scala 23:21]
    if_id_reg.io.if_out.inst <= if_stage.io.out.inst @[DataPath.scala 24:19]
    if_id_reg.io.if_out.pc <= if_stage.io.out.pc @[DataPath.scala 24:19]
    io.debug.inst <= if_stage.io.out.inst @[DataPath.scala 26:12]
    io.debug.pc <= if_stage.io.out.pc @[DataPath.scala 26:12]
    
