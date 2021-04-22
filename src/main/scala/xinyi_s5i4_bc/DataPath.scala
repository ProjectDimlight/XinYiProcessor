package xinyi_s5i4_bc

import chisel3._
import chisel3.experimental.BundleLiterals._

import config.config._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.fu._
import ControlConst._
import EXCCodeConfig._

class DataPath extends Module {
  //val io = IO(new DataPathIO)
  val io = IO(new Bundle{
    val interrupt   = Input(Vec(6, Bool()))
    val icache_axi  = new ICacheAXI
    val dcache_axi  = Vec(LSU_PATH_NUM, new DCacheAXI)
  })

  val pc_if_reg     = Module(new PCIFReg)
  val if_id_reg     = Module(new IFIDReg)
  val issue_queue   = Module(new IssueQueue)
  val is_fu_reg     = Module(new ISFUReg)
  val fu_wb_reg     = Module(new FUWBReg)
  val interrupt_reg = Module(new InterruptReg)

  val icache = Module(new DummyICache)
  val dcache = Module(new DummyDCache)

  val stall_frontend = Wire(Bool())
  val stall_backend  = Wire(Bool())

  // Stages
  val pc_stage      = Module(new PCStage)
  val if_stage      = Module(new IFStage)
  val id_stage      = Module(new IDStage)
  val is_stage      = Module(new ISStage)
  val wb_stage      = Module(new WBStage)
  val flush         = Wire(Bool())

  // FUs
  val bju           = Module(new BJU)

  // Regs
  val regs          = Module(new Regs)
  val cp0           = Module(new CP0)
  val hilo          = Module(new HiLo)
  
  val forwarding    = Wire(Vec(TOT_PATH_NUM, new Forwarding))

  // Flush
  if_id_reg.  io.flush := flush
  issue_queue.io.flush := flush
  is_fu_reg.  io.flush := flush
  fu_wb_reg.  io.flush := flush

  // PC Stage
  pc_stage.io.pc      <> pc_if_reg.io.if_in.pc
  pc_stage.io.branch  <> bju.io.pc_interface
  pc_stage.io.next_pc <> pc_if_reg.io.pc_out

  // IF Stage
  if_stage.io.in      <> pc_if_reg.io.if_in
  if_stage.io.cache   <> icache.io.upper
  if_stage.io.out     <> if_id_reg.io.if_out

  icache.io.lower     <> io.icache_axi
  icache.io.stall_req  := stall_frontend
  pc_if_reg.io.stall   := stall_frontend
  if_stage.io.stall    := stall_frontend
  if_id_reg.io.stall   := stall_frontend
  issue_queue.io.stall := stall_frontend

  // ID Stage instances
  id_stage.io.in <> if_id_reg.io.id_in

  // ID -> Issue Queue -> IS -> BJU -> IS

  // Issue Queue
  issue_queue.io.in := id_stage.io.out
  issue_queue.io.bc := bju.io.branch_cache_out
  issue_queue.io.actual_issue_cnt := is_stage.io.actual_issue_cnt

  // ISStage
  is_stage.io.issue_cnt   := issue_queue.io.issue_cnt
  is_stage.io.inst        := issue_queue.io.inst
  is_stage.io.forwarding  := forwarding 
  is_stage.io.stall       := stall_backend

  stall_backend := false.B
  for (j <- 0 until TOT_PATH_NUM)
    when (!forwarding(j).ready) {
      stall_backend := true.B
    }
  
  // Fetch instruction params
  val inst_params = Wire(Vec(FETCH_NUM , Vec(2, UInt(XLEN.W))))
  for (i <- 0 until FETCH_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)
    // when (inst(i).param_a)
    inst.dec.rs1      := regs.io.read(i).rs1
    inst_params(i)(0) := regs.io.read(i).data1

    inst.dec.rs2      := regs.io.read(i).rs2
    inst_params(i)(1) := regs.io.read(i).data2
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
  val is_out = Wire(Vec(TOT_PATH_NUM, new ISOut))
  for (j <- 0 until TOT_PATH_NUM) {
    is_out(j).write_target  := is_stage.io.path(j).write_target
    is_out(j).rd            := is_stage.io.path(j).rd
    is_out(j).fu_ctrl       := is_stage.io.path(j).fu_ctrl
    is_out(j).pc            := is_stage.io.path(j).pc
    is_out(j).order         := is_stage.io.path(j).order
    is_out(j).a             := inst_params(is_stage.io.path(j).order)(0)
    is_out(j).b             := inst_params(is_stage.io.path(j).order)(1)
    is_out(j).imm           := issue_queue.io.inst(is_stage.io.path(j).order).imm
    is_out(j).is_delay_slot := is_stage.io.is_delay_slot(is_stage.io.path(j).order)
  }
  is_fu_reg.io.is_out := is_out
  is_fu_reg.io.is_actual_issue_cnt := is_stage.io.actual_issue_cnt
  is_fu_reg.io.stall := stall_backend

  // BJU
  bju.io.path := is_out(is_stage.io.branch_jump_id)
  bju.io.branch_next_pc := is_stage.io.branch_next_pc
  bju.io.delay_slot_pending := is_stage.io.delay_slot_pending

  // FUs
  dcache.io.lower <> io.dcache_axi

  val exception_by_order = Wire(Vec(ISSUE_NUM, Bool()))
  for (i <- 0 until ISSUE_NUM)
    exception_by_order(i) := false.B
  
  val min_exception_order = Wire(UInt(ISSUE_NUM_W.W))
  min_exception_order := ISSUE_NUM.U
  for (i <- ISSUE_NUM - 1 to 0 by -1) {
    when (exception_by_order(i)) {
      min_exception_order := i.U
    }
  }

  def CreatePath(path_type: Int, j: Int, base: Int) = {
    if (path_type == 3) {
      var fu = Module(new LSU)
      fu.io.cache     <> dcache.io.upper(j - base)
      fu.io.stall_req <> dcache.io.stall_req(j - base)
      fu.io.stall     <> is_fu_reg.io.stalled

      fu.io.in := is_fu_reg.io.fu_in(j)
      forwarding(j).write_target := fu.io.out.write_target
      forwarding(j).rd           := fu.io.out.rd
      forwarding(j).data         := fu.io.out.data
      forwarding(j).hi           := fu.io.out.hi
      forwarding(j).ready        := fu.io.out.ready
      forwarding(j).order        := fu.io.out.order

      fu.io.exception_order := min_exception_order
      fu_wb_reg.io.fu_out(j) := fu.io.out

      when (fu.io.out.exc_code =/= NO_EXCEPTION) {
        exception_by_order(fu.io.out.order) := true.B
      }

      fu
    }
    else {
      var fu = Module(new ALU)

      fu.io.in := is_fu_reg.io.fu_in(j)
      forwarding(j).write_target := fu.io.out.write_target
      forwarding(j).rd           := fu.io.out.rd
      forwarding(j).data         := fu.io.out.data
      forwarding(j).hi           := fu.io.out.hi
      forwarding(j).ready        := fu.io.out.ready
      forwarding(j).order        := fu.io.out.order
      
      fu_wb_reg.io.fu_out(j) := fu.io.out

      when (fu.io.out.exc_code =/= NO_EXCEPTION) {
        exception_by_order(fu.io.out.order) := true.B
      }

      fu
    }
  }

  fu_wb_reg.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt
  fu_wb_reg.io.stall := stall_backend

  var base = 0
  for (path_type <- 1 until PATH_TYPE_NUM) {
    for (j <- base until base + PATH_NUM(path_type)) {
      val fu = CreatePath(path_type, j, base)
    }
    base += PATH_NUM(path_type)
  }

  // FU Interrupt Reg
  val interrupt = Wire(Vec(8, Bool()))
  for (i <- 0 until 2) {
    interrupt(i) := cp0.io.soft_int_pending_vec(i)
  }
  for (i <- 2 until 7) {
    interrupt(i) := interrupt(i)
  }
  interrupt(7) := io.interrupt(5) | false.B  // TODO: Clock interrupt

  val masked_interrupt = Wire(Vec(8, Bool()))
  for (i <- 0 until 7)
    masked_interrupt(i) := interrupt(i) & cp0.io.int_mask_vec(i)

  interrupt_reg.io.fu_pc := BOOT_ADDR.U
  for (j <- 0 until TOT_PATH_NUM) {
    when (is_fu_reg.io.fu_in(j).order === 0.U) {
      interrupt_reg.io.fu_pc := is_fu_reg.io.fu_in(j).pc
    }
  }
  interrupt_reg.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt
  interrupt_reg.io.fu_interrupt := masked_interrupt

  // WB Stage
  for (j <- 0 until TOT_PATH_NUM) {
    wb_stage.io.fu_res_vec(j)   := fu_wb_reg.io.wb_in(j)
  }
  wb_stage.io.actual_issue_cnt  := fu_wb_reg.io.wb_actual_issue_cnt

  wb_stage.io.incoming_epc       := interrupt_reg.io.wb_epc
  wb_stage.io.incoming_interrupt := interrupt_reg.io.wb_interrupt
  flush := wb_stage.io.exception_handled

  // Write Back data path
  hilo.io.in_hi_wen := false.B
  hilo.io.in_hi     := 0.U
  hilo.io.in_lo_wen := false.B
  hilo.io.in_lo     := 0.U
  for (i <- 0 until ISSUE_NUM) {
    regs.io.write(i).we   := wb_stage.io.write_channel_vec(i).write_regs_en
    regs.io.write(i).rd   := wb_stage.io.write_channel_vec(i).write_regs_rd
    regs.io.write(i).data := wb_stage.io.write_channel_vec(i).write_regs_data

    cp0 .io.write(i).we   := wb_stage.io.write_channel_vec(i).write_cp0_en
    cp0 .io.write(i).rd   := wb_stage.io.write_channel_vec(i).write_cp0_rd
    cp0 .io.write(i).data := wb_stage.io.write_channel_vec(i).write_cp0_data

    when (wb_stage.io.write_channel_vec(i).write_hi_en) {
      hilo.io.in_hi_wen   := true.B
      hilo.io.in_hi       := wb_stage.io.write_channel_vec(i).write_hi_data
    }
    when (wb_stage.io.write_channel_vec(i).write_lo_en) {
      hilo.io.in_lo_wen   := true.B
      hilo.io.in_lo       := wb_stage.io.write_channel_vec(i).write_lo_data
    }
  }
}
