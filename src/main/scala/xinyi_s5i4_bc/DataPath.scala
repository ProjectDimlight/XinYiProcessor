package xinyi_s5i4_bc

import chisel3._
import chisel3.experimental.BundleLiterals._

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
//  val debug_out   = Vec(2, new IDOut)
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
  
  val forwarding    = Wire(Vec(TOT_PATH_NUM, new Forwarding))

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
  if_stage.io.stall <> stall_frontend
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
  val inst_params = Wire(Vec(FETCH_NUM , Vec(2, UInt(XLEN.W))))
  for (i <- 0 until FETCH_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)
    // when (inst(i).param_a)
    inst.dec.rs1 <> regs.io.read(i).rs1
    inst_params(i)(0) <> regs.io.read(i).data1

    inst.dec.rs2 <> regs.io.read(i).rs2
    inst_params(i)(1) <> regs.io.read(i).data2
  }
  for (i <- 0 until FETCH_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)

    when (is_stage.io.forwarding_path_id(i).rs1 =/= TOT_PATH_NUM.U) {
      val j = is_stage.io.forwarding_path_id(i).rs1
      val path = is_fu_reg.io.is_out(j)
      inst_params(i)(0) := Mux(inst.dec.param_a === AHi, forwarding(j).hi, forwarding(j).data)
    }

    when (is_stage.io.forwarding_path_id(i).rs2 =/= TOT_PATH_NUM.U) {
      val j = is_stage.io.forwarding_path_id(i).rs2
      val path = is_fu_reg.io.is_out(j)
      inst_params(i)(1) := forwarding(j).data
    }
  }

  // IS-FU regs
  for (j <- 0 until TOT_PATH_NUM) {
    is_fu_reg.io.is_out(j).Lit(
      _.write_target -> is_stage.io.path(j).write_target,
      _.rd           -> is_stage.io.path(j).rd,
      _.fu_ctrl      -> is_stage.io.path(j).fu_ctrl,
      _.pc           -> is_stage.io.path(j).pc,
      _.order        -> is_stage.io.path(j).order,
      _.a            -> inst_params(is_stage.io.path(j).order)(0),
      _.b            -> inst_params(is_stage.io.path(j).order)(1),
      _.imm          -> issue_queue.io.inst(is_stage.io.path(j).order).imm
    )
  }

  // BJU
  bju.io.path <> is_fu_reg.io.is_out(is_stage.io.branch_jump_id)
  bju.io.delay_slot_pending <> is_stage.io.delay_slot_pending

  def PathType(path_type: Int) = {
    if (path_type == 1) {
      Module(new ALU)
    }
    else if (path_type == 3) {
      Module(new LSU)
    }
    else {
      Module(new ALU)
    }
  }

  // FUs
  var base = 0
  for (path_type <- 1 until PATH_TYPE_NUM) {
    for (j <- base until base + PATH_NUM(path_type)) {
      val fu = PathType(path_type)
      fu.io.in := is_fu_reg.io.fu_in(j)
      forwarding(j).Lit(
        _.write_target  -> fu.io.out.write_target,
        _.rd            -> fu.io.out.rd,
        _.data          -> fu.io.out.data,
        _.hi            -> fu.io.out.hi,
        _.ready         -> fu.io.out.ready,
        _.order         -> fu.io.out.order
      )
    }
    
    base += PATH_NUM(path_type)
  }
}
