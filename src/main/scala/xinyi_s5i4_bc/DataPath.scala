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
  val id_stage      = Module(new IDStage)
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
  id_stage.io.in <> if_id_reg.io.id_in

  // ID -> Issue Queue -> IS -> BJU -> IS

  // Issue Queue
  issue_queue.io.in <> id_stage.io.out
  issue_queue.io.bc <> bju.io.branch_cache_out
  issue_queue.io.actual_issue_cnt <> is_stage.io.actual_issue_cnt

  // ISStage
  is_stage.io.issue_cnt <> issue_queue.io.issue_cnt
  is_stage.io.inst <> issue_queue.io.inst
  
  // Fetch instruction params
  val inst_params = Wire(Vec(fetch_num , new PathData)
  for (i <- 0 until fetch_num) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)
    // when (inst(i).param_a)
    inst.dec.rs1 <> regs.io.inst(i).reg_id1
    inst_params(i).rs1 <> regs.io.inst(i).data1

    inst.dec.rs2 <> regs.io.inst(i).reg_id2
    inst_params(i).rs2 <> regs.io.inst(i).data2
  }

  // FUs
  for (j <- 0 until alu_path_num) {
    alu_paths(j).io.in   <> is_stage.io.alu_paths(j).in
    alu_paths(j).io.out  <> is_stage.io.alu_paths(j).out
    alu_paths(j).io.data <> inst_params(is_stage.io.alu_paths(j).in.id)
  }
  for (j <- 0 until lsu_path_num) {
    lsu_paths(j).io.in   <> is_stage.io.lsu_paths(j).in
    lsu_paths(j).io.out  <> is_stage.io.lsu_paths(j).out
    lsu_paths(j).io.data <> inst_params(is_stage.io.lsu_paths(j).in.id)
  }

  // BJU
  bju.io.path.in   <> alu_paths(is_stage.io.branch_jump_id).id
  bju.io.path.data <> alu_paths(is_stage.io.branch_jump_id).data
  bju.io.delay_slot_pending <> is_stage.io.delay_slot_pending

  // TODO: add FUs
}
