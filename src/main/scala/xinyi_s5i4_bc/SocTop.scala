// package xinyi_s5i4_bc

// import chisel3._
// import chisel3.util._
// import config.config._

// class SimMemIO extends Bundle with Port {
//   val clk = Input(Clock())
//   val raddr = Input(UInt(xlen.W))
//   val rdata = Output(UInt(xlen.W))
//   val waddr = Input(UInt(xlen.W))
//   val wdata = Input(UInt(xlen.W))
//   val wmask = Input(UInt(xlen.W))
//   val wen = Input(Bool())
// }

// class SimMem extends BlackBox {
//   val io = IO(new SimMemIO)
// }

// class DiffTestIO extends Bundle with PortConfig {
//   val regs     = Output(Vec(32, UInt(XLEN.W)))
//   val pc       = Output(UInt(XLEN.W))
//   val inst     = Output(UInt(XLEN.W))
//   val valid    = Output(Bool())
//   // val csr_cmd  = Output(UInt(ControlConst.wenBits.W))
//   // val tick     = Output(Bool())
//   // val int      = Output(Bool())
//   // val mcycle   = Output(UInt(xlen.W))
//   // val mstatus  = Output(UInt(xlen.W))
//   // val priv     = Output(UInt(2.W))
//   // val mepc     = Output(UInt(xlen.W))
//   // val mtval    = Output(UInt(xlen.W))
//   // val mcause   = Output(UInt(xlen.W))
//   // val sstatus  = Output(UInt(xlen.W))
//   // val sepc     = Output(UInt(xlen.W))
//   // val stval    = Output(UInt(xlen.W))
//   // val scause   = Output(UInt(xlen.W))
//   // val stvec    = Output(UInt(xlen.W))
//   // val mtvec    = Output(UInt(xlen.W))
//   // val mideleg  = Output(UInt(xlen.W))
//   // val medeleg  = Output(UInt(xlen.W))
//   // val mip      = Output(UInt(xlen.W))
//   // val mie      = Output(UInt(xlen.W))
//   // val sip      = Output(UInt(xlen.W))
//   // val sie      = Output(UInt(xlen.W))
//   // val uartirq  = Output(Bool())
//   // val plicmeip = Output(Bool())
//   // val plicseip = Output(Bool())
//   // val plicip   = Output(Vec(32, Bool()))
//   // val plicie   = Output(UInt(32.W))
//   // val plicprio = Output(UInt(32.W))
//   // val plicthrs = Output(UInt(32.W))
//   // val plicclaim = Output(UInt(32.W))
//   // val alu_val  = Output(UInt(xlen.W))
//   // val is_mem   = Output(Bool())
//   // val icache_read_misses   = Output(UInt(64.W))
//   // val icache_read_count   = Output(UInt(64.W))
//   // val icache_write_misses   = Output(UInt(64.W))
//   // val icache_write_count   = Output(UInt(64.W))
//   // val dcache_read_misses   = Output(UInt(64.W))
//   // val dcache_read_count   = Output(UInt(64.W))
//   // val dcache_write_misses   = Output(UInt(64.W))
//   // val dcache_write_count   = Output(UInt(64.W))
//   // val l2cache_read_misses   = Output(UInt(64.W))
//   // val l2cache_read_count   = Output(UInt(64.W))
//   // val l2cache_write_misses   = Output(UInt(64.W))
//   // val l2cache_write_count   = Output(UInt(64.W))
// }

// class TopIO extends Bundle with phvntomParams {
//   // Difftest
//   val difftest = new DiffTestIO
// }

// class Top extends Module with phvntomParams {
//   override val desiredName = "soc_top"
  
//   val io = IO(new DiffTestIO)

//   val datapath = Module(new DataPath)
  

//   val difftest = WireInit(0.U.asTypeOf(new DiffTestIO))
//   // BoringUtils.addSink(difftest.streqs,  "difftestStreqs")
//   BoringUtils.addSink(difftest.regs,    "difftestRegs")
//   // BoringUtils.addSink(difftest.pc,      "difftestPC")
//   // BoringUtils.addSink(difftest.inst,    "difftestInst")
//   // BoringUtils.addSink(difftest.valid,   "difftestValid")
//   // BoringUtils.addSink(difftest.csr_cmd, "difftestCSRCmd")
//   // BoringUtils.addSink(difftest.int,     "difftestInt")
//   // BoringUtils.addSink(difftest.mcycle,  "difftestmcycler")
//   // BoringUtils.addSink(difftest.mstatus, "difftestmstatusr")
//   // BoringUtils.addSink(difftest.priv,    "difftestprivilege")

//   // BoringUtils.addSink(difftest.mepc,    "difftestmepcr")
//   // BoringUtils.addSink(difftest.mtval,   "difftestmtvalr")
//   // BoringUtils.addSink(difftest.mcause,  "difftestmcauser")
//   // BoringUtils.addSink(difftest.sstatus, "difftestsstatusr")
//   // BoringUtils.addSink(difftest.sepc,    "difftestsepcr")
//   // BoringUtils.addSink(difftest.stval,   "diffteststvalr")
//   // BoringUtils.addSink(difftest.scause,  "difftestscauser")
//   // BoringUtils.addSink(difftest.stvec,   "diffteststvecr")
//   // BoringUtils.addSink(difftest.mtvec,   "difftestmtvecr")
//   // BoringUtils.addSink(difftest.mideleg, "difftestmidelegr")
//   // BoringUtils.addSink(difftest.medeleg, "difftestmedelegr")
//   // BoringUtils.addSink(difftest.mip,     "difftestmipr")
//   // BoringUtils.addSink(difftest.mie,     "difftestmier")
//   // BoringUtils.addSink(difftest.sip,     "difftestsipr")
//   // BoringUtils.addSink(difftest.sie,     "difftestsier")

//   // val poweroff = WireInit(0.U(xlen.W))
//   // BoringUtils.addSink(poweroff, "difftestpoweroff")

//   // BoringUtils.addSink(difftest.uartirq,  "difftestuartirq")
//   // BoringUtils.addSink(difftest.plicmeip, "difftestplicmeip")
//   // BoringUtils.addSink(difftest.plicseip, "difftestplicseip")

//   // BoringUtils.addSink(difftest.plicip,      "difftestplicpend")
//   // BoringUtils.addSink(difftest.plicie,      "difftestplicenable")
//   // BoringUtils.addSink(difftest.plicprio,    "difftestplicpriority")
//   // BoringUtils.addSink(difftest.plicthrs,    "difftestplicthreshold")
//   // BoringUtils.addSink(difftest.plicclaim,   "difftestplicclaimed")

//   // BoringUtils.addSink(difftest.alu_val, "difftestALU")
//   // BoringUtils.addSink(difftest.is_mem,  "difftestMem")
//   // BoringUtils.addSink(difftest.icache_read_misses, "icache_read_misses")
//   // BoringUtils.addSink(difftest.icache_read_count, "icache_read_count")
//   // BoringUtils.addSink(difftest.dcache_read_misses, "dcache_read_misses")
//   // BoringUtils.addSink(difftest.dcache_read_count, "dcache_read_count")
//   // BoringUtils.addSink(difftest.dcache_write_misses, "dcache_write_misses")
//   // BoringUtils.addSink(difftest.dcache_write_count, "dcache_write_count")
//   // BoringUtils.addSink(difftest.l2cache_read_misses, "l2cache_read_misses")
//   // BoringUtils.addSink(difftest.l2cache_read_count, "l2cache_read_count")
//   // BoringUtils.addSink(difftest.l2cache_write_misses, "l2cache_write_misses")
//   // BoringUtils.addSink(difftest.l2cache_write_count, "l2cache_write_count")

//   io.difftest := difftest
//   // io.poweroff := poweroff
// }

// object elaborate {
//   def main(args: Array[String]): Unit = {
//     val packageName = this.getClass.getPackage.getName

//     if (args.isEmpty)
//       (new chisel3.stage.ChiselStage).execute(
//         Array("-td", "build/verilog/"+packageName, "-X", "verilog"),
//         Seq(ChiselGeneratorAnnotation(() => new Top)))
//     else
//       (new chisel3.stage.ChiselStage).execute(args,
//         Seq(ChiselGeneratorAnnotation(() => new Top)))
//   }
// }