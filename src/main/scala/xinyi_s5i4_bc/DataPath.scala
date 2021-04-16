package xinyi_s5i4_bc

import chisel3._
import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.fu._
import ControlConst._

class DataPath extends Module {
  //val io = IO(new DataPathIO)
  val io = IO(new Bundle{
    val icache_axi  = new ICacheAXI
//    val debug_out   = Vec(2, new IDOut)
  })

  val pc_if_reg     = Module(new PCIFReg)
  val if_id_reg     = Module(new IFIDReg)
  val issue_queue   = Module(new IssueQueue)
  val is_fu_reg     = Module(new ISFUReg)

  val icache = Module(new DummyICache)

  val stall_frontend = Wire(Bool())

  // Stages
  val pc_stage      = Module(new PCStage)
  val if_stage      = Module(new IFStage)
  val id_stage      = Module(new IDStage)
  val is_stage      = Module(new ISStage)

  // FUs
  val bju           = Module(new BJU)

  // Other modules
  val regs          = Module(new Regs)

  // PC Stage
  pc_stage.io.pc <> pc_if_reg.io.if_in.pc
  pc_stage.io.branch <> bju.io.pc_interface
  pc_stage.io.next_pc <> pc_if_reg.io.pc_out

  // IF Stage
  if_stage.io.in <> pc_if_reg.io.if_in
  if_stage.io.cache <> icache.io.upper
  if_stage.io.out <> if_id_reg.io.if_out

  icache.io.lower <> io.icache_axi
  icache.io.stall_req <> stall_frontend
  pc_if_reg.io.stall <> stall_frontend
  if_id_reg.io.stall <> stall_frontend
  issue_queue.io.stall <> stall_frontend

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
  val inst_params = Wire(Vec(FETCH_NUM , new PathData))
  for (i <- 0 until FETCH_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)
    // when (inst(i).param_a)
    inst.dec.rs1 <> regs.io.read(i).rs1
    inst_params(i).rs1 <> regs.io.read(i).data1

    inst.dec.rs2 <> regs.io.read(i).rs2
    inst_params(i).rs2 <> regs.io.read(i).data2
  }
  for (i <- 0 until FETCH_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)

    when (is_stage.io.forwarding_path(i).rs1 =/= TOT_PATH_NUM.U) {
      val j = is_stage.io.forwarding_path(i).rs1
      val path = is_fu_reg.io.is_out(j)
      inst_params(i).rs1 <> Mux(inst.dec.param_a === AHi, path.out.hi, path.out.data)
    }

    when (is_stage.io.forwarding_path(i).rs2 =/= TOT_PATH_NUM.U) {
      val j = is_stage.io.forwarding_path(i).rs2
      val path = is_fu_reg.io.is_out(j)
      inst_params(i).rs2 <> path.out.data
    }
  }

  // FUs
  for (j <- 0 until TOT_PATH_NUM) {
    is_fu_reg.io.is_out(j).out  <> is_stage.io.paths(j).out

    is_fu_reg.io.is_out(j).in   <> is_stage.io.paths(j).in
    is_fu_reg.io.is_out(j).data <> inst_params(is_stage.io.paths(j).in.id)
  }

  // BJU
  bju.io.path.in   <> is_fu_reg.io.is_out(is_stage.io.branch_jump_id).in
  bju.io.path.data <> is_fu_reg.io.is_out(is_stage.io.branch_jump_id).data
  bju.io.delay_slot_pending <> is_stage.io.delay_slot_pending

  // TODO: add FUs
}
