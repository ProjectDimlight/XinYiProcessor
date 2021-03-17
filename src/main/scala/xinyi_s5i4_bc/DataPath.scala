package xinyi_s5i4_bc

import chisel3._
import wrap._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._

class DataPath extends Module with XinYiConfig {
  //val io = IO(new DataPathIO)
  val io = IO(new Bundle{
    val icache_l2 = Flipped(new RAMInterface(lgc_addr_w, l1_w))
    val debug_out = Vec(2, new IDOut)
  })

  val pc_if_reg = Module(new PCIFReg)
  val if_id_reg = Module(new IFIDReg)

  // val branch_cache = Module(new BranchCache)
  val icache = Module(new DummyCache(lgc_addr_w, l1_w))

  // Stages
  val if_stage = Module(new IFStage)
  val id_stage = VecInit(Seq.fill(fetch_num)(Module(new IDStage).io))

  // IF Stage
  if_stage.io.in <> pc_if_reg.io.if_in
  if_stage.io.cache <> icache.io.upper
  if_stage.io.out <> if_id_reg.io.if_out

  icache.io.lower <> io.icache_l2

  // ID Stage instances
  for (i <- 0 until fetch_num) {
    id_stage(i).in <> if_id_reg.io.id_in(i)
    id_stage(i).out <> io.debug_out(i)
  }

  // TODO: Add these decoded results to the issue queue
}
