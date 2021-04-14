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

  val icache = Module(new DummyCache(lgc_addr_w, l1_w))

  // Stages
  val pc_stage      = Module(new PCStage)
  val if_stage      = Module(new IFStage)
  val id_stage      = VecInit(Seq.fill(fetch_num)(Module(new IDStage).io))
  val issue_queue   = Module(new IssueQueue)
  val is_stage      = Module(new ISStage)

  // FUs
  val bju           = Module(new BJU)

  // PC Stage
  pc_stage.io.pc <> pc_if_reg.io.if_in.pc
  pc_stage.io.branch <> bju.io.pc_interface
  pc_stage.io.next_pc <> pc_if_reg.io.pc_out

  // IF Stage
  if_stage.io.in <> pc_if_reg.io.if_in
  if_stage.io.cache <> icache.io.upper
  if_stage.io.out <> if_id_reg.io.if_out

  icache.io.lower <> io.icache_l2

  // ID Stage instances
  for (i <- 0 until fetch_num) {
    id_stage(i).io.in <> if_id_reg.io.id_in(i)
  }

  // ID -> Issue Queue -> IS -> BJU -> IS

  // Issue Queue
  for (i <- 0 until fetch_num) {
    issue_queue.io.in(i) <> id_stage(i).io.out
  }
  issue_queue.io.bc <> bju.io.branch_cache_out
  issue_queue.io.actual_issue_cnt <> is_stage.io.actual_issue_cnt

  // ISStage
  is_stage.io.issue_cnt <> issue_queue.io.issue_cnt
  is_stage.io.inst <> issue_queue.io.inst
  
  // 

  // BJU
  // bju.io.path <> is_stage.io.bju_interface
  bju.io.delay_slot_pending <> is_stage.io.delay_slot_pending

  // TODO: add FUs
}
