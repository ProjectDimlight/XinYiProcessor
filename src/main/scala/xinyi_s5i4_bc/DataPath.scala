package xinyi_s5i4_bc

import chisel3._
import chisel3.util._
import utils._
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

    val debug_pc    = Output(Vec(ISSUE_NUM, UInt(XLEN.W)))
  })

  val pc_if_reg     = Module(new PCIFReg)
  val if_id_reg     = Module(new IFIDReg)
  val issue_queue   = Module(new IssueQueue)
  val is_bju_reg    = Module(new ISBJUReg)
  val is_fu_reg     = Module(new ISFUReg)
  val fu_wb_reg     = Module(new FUWBReg)
  val interrupt_reg = Module(new InterruptReg)

  val icache = Module(new DummyICache)
  val dcache = Module(new DummyDCache)

  val stall_frontend = Wire(Bool())
  val stall_backend  = Wire(Bool())

  val interrupt = Wire(Vec(8, Bool()))
  val has_interrupt = Wire(Bool())

  // Stages
  val pc_stage      = Module(new PCStage)
  val if_stage      = Module(new IFStage)
  val id_stage      = Module(new IDStage)
  val is_stage      = Module(new ISStage)
  val fu_stage      = Module(new FUStage)
  val wb_stage      = Module(new WBStage)
  val flush         = Wire(Bool())

  // FUs
  val bju           = Module(new BJU)
  val bc            = Module(new BranchCache)

  // Regs
  val regs          = Module(new Regs)
  val cp0           = Module(new CP0)
  val hilo          = Module(new HiLo)
  
  val forwarding    = Wire(Vec(TOT_PATH_NUM, new Forwarding))

  // Flush
  if_id_reg.  io.flush := flush
  issue_queue.io.flush := flush
  is_bju_reg. io.flush := flush
  is_fu_reg.  io.flush := flush
  fu_wb_reg.  io.flush := flush

  // PC Stage
  pc_stage.io.pc      <> pc_if_reg.io.if_in.pc
  pc_stage.io.next_pc <> pc_if_reg.io.pc_out

  pc_stage.io.branch.enable    := bc.io.branch_cached_en
  pc_stage.io.branch.target    := bc.io.branch_cached_pc
  pc_stage.io.exception.target := fu_wb_reg.io.wb_exception_target
  pc_stage.io.exception.enable := fu_wb_reg.io.wb_exception_handled
  

  // IF Stage
  if_stage.io.in      <> pc_if_reg.io.if_in
  if_stage.io.cache   <> icache.io.upper
  if_stage.io.full    <> issue_queue.io.full
  if_stage.io.out     <> if_id_reg.io.if_out

  icache.io.lower      <> io.icache_axi
  
  stall_frontend := icache.io.stall_req | issue_queue.io.full
  
  pc_stage.io.stall    := stall_frontend
  pc_if_reg.io.stall   := stall_frontend
  if_id_reg.io.stall   := stall_frontend
  issue_queue.io.stall := stall_frontend

  // ID Stage instances
  id_stage.io.in <> if_id_reg.io.id_in

  // ID -> Issue Queue -> IS -> BJU -> IS

  // Issue Queue
  issue_queue.io.in := id_stage.io.out
  issue_queue.io.bc := bc.io.out
  issue_queue.io.actual_issue_cnt := is_stage.io.actual_issue_cnt

  // ISStage
  is_stage.io.issue_cnt        := issue_queue.io.issue_cnt
  is_stage.io.inst             := issue_queue.io.inst
  is_stage.io.forwarding       := forwarding
  is_stage.io.branch_cache_out := bc.io.out
  is_stage.io.stall            := stall_backend

  stall_backend := false.B
  for (j <- 0 until TOT_PATH_NUM)
    when (!forwarding(j).ready) {
      stall_backend := true.B
    }
  
  // Fetch instruction params
  val inst_params = Wire(Vec(ISSUE_NUM , Vec(2, UInt(XLEN.W))))
  for (i <- 0 until ISSUE_NUM) {
    val inst = Wire(new Instruction)
    inst := issue_queue.io.inst(i)
    inst.dec.rs1      <> regs.io.read(i).rs1
    inst.dec.rs1      <> cp0 .io.read(i).rs

    inst_params(i)(0) := MuxLookupBi(
      inst.dec.param_a,
      regs.io.read(i).data1,
      Array(
        ACP0   -> cp0 .io.read(i).data,
        AHi    -> hilo.io.out_hi,
        ALo    -> hilo.io.out_lo,
        AShamt -> inst.imm(10, 6)
      )
    )

    inst.dec.rs2      <> regs.io.read(i).rs2
    inst_params(i)(1) := Mux((inst.dec.param_b === BImm) & (inst.dec.path === PathALU), inst.imm, regs.io.read(i).data2)
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
  is_fu_reg.io.is_out                 := is_out
  is_fu_reg.io.is_actual_issue_cnt    := is_stage.io.actual_issue_cnt
  is_fu_reg.io.stall                  := stall_backend

  // IS-BJU regs
  is_bju_reg.io.is_path               := is_out(is_stage.io.branch_jump_id)
  is_bju_reg.io.is_branch_next_pc     := is_stage.io.branch_next_pc
  is_bju_reg.io.is_delay_slot_pending := is_stage.io.delay_slot_pending
  is_bju_reg.io.stall                 := stall_backend

  // BJU
  bju.io.path                         := is_bju_reg.io.fu_path
  bju.io.branch_next_pc               := is_bju_reg.io.fu_branch_next_pc

  // BC
  bc.io.in.branch := bju.io.branch
  bc.io.in.target := bju.io.target
  bc.io.in.delay_slot_pending := is_bju_reg.io.fu_delay_slot_pending
  bc.io.stall_frontend := stall_frontend
  bc.io.stall_backend  := stall_backend

  bc.io.wr.flush := flush
  bc.io.wr.stall := stall_frontend
  bc.io.wr.inst  := id_stage.io.out

  // FUs
  dcache.io.lower <> io.dcache_axi
  dcache.io.flush <> flush
  dcache.io.last_stall <> is_fu_reg.io.stalled
  dcache.io.stall <> stall_backend

  val exception_by_path = Wire(Vec(ISSUE_NUM, Vec(TOT_PATH_NUM, Bool())))
  val exception_by_order = Wire(Vec(ISSUE_NUM, Bool()))
  for (i <- 0 until ISSUE_NUM) {
    exception_by_path(i) := 0.U.asTypeOf(Vec(TOT_PATH_NUM, Bool()))
    exception_by_order(i) := exception_by_path(i).asUInt().orR()
  }
  
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

      fu.io.in := is_fu_reg.io.fu_in(j)
      forwarding(j).write_target := fu.io.out.write_target
      forwarding(j).rd           := fu.io.out.rd
      forwarding(j).data         := fu.io.out.data
      forwarding(j).hi           := fu.io.out.hi
      forwarding(j).ready        := fu.io.out.ready
      forwarding(j).order        := fu.io.out.order

      fu.io.exception_order := min_exception_order
      fu.io.interrupt       := has_interrupt
      fu.io.flush           := flush

      fu_stage.io.fu_out(j) := fu.io.out
      exception_by_path(fu.io.out.order)(j) := fu.io.out.exception

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
      
      fu_stage.io.fu_out(j) := fu.io.out

      exception_by_path(fu.io.out.order)(j) := fu.io.out.exception

      fu
    }
  }

  fu_stage.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt

  fu_wb_reg.io.sorted_fu_out        := fu_stage.io.sorted_fu_out
  fu_wb_reg.io.fu_exception_order   := fu_stage.io.fu_exception_order
  fu_wb_reg.io.fu_exception_handled := fu_stage.io.fu_exception_handled
  fu_wb_reg.io.fu_exception_target  := fu_stage.io.fu_exception_target
  fu_wb_reg.io.fu_exc_info          := fu_stage.io.exc_info
  fu_wb_reg.io.stall := stall_backend

  var base = 0
  for (path_type <- 1 until PATH_TYPE_NUM) {
    for (j <- base until base + PATH_NUM(path_type)) {
      val fu = CreatePath(path_type, j, base)
    }
    base += PATH_NUM(path_type)
  }
  
  // Interrupt
  for (i <- 0 until 2) {
    interrupt(i) := cp0.io.soft_int_pending_vec(i)
  }
  for (i <- 2 until 7) {
    interrupt(i) := io.interrupt(i - 2)
  }
  interrupt(7) := io.interrupt(5) | cp0.io.time_int // TODO: Clock interrupt

  has_interrupt := false.B
  val masked_interrupt = Wire(Vec(8, Bool()))
  for (i <- 0 until 8) {
    masked_interrupt(i) := interrupt(i) & cp0.io.int_mask_vec(i)
    when (interrupt(i)) {
      has_interrupt := true.B
    }
  }
  fu_stage.io.incoming_interrupt := masked_interrupt

  // FU Interrupt Reg
  interrupt_reg.io.fu_pc := BOOT_ADDR.U
  interrupt_reg.io.fu_is_delay_slot := false.B
  
  when (is_fu_reg.io.fu_actual_issue_cnt =/= 0.U) {
    interrupt_reg.io.fu_pc := fu_stage.io.sorted_fu_out(0).pc
    interrupt_reg.io.fu_is_delay_slot :=  fu_stage.io.sorted_fu_out(0).is_delay_slot
  }
  interrupt_reg.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt
  fu_stage.io.incoming_epc             := interrupt_reg.io.wb_epc

  // WB Stage
  wb_stage.io.wb_in := fu_wb_reg.io.wb_in
  wb_stage.io.wb_exception_order := fu_wb_reg.io.wb_exception_order
  flush := fu_wb_reg.io.wb_exception_handled

  // Write Back data path
  hilo.io.in_hi_wen := false.B
  hilo.io.in_hi     := 0.U
  hilo.io.in_lo_wen := false.B
  hilo.io.in_lo     := 0.U
  for (i <- 0 until ISSUE_NUM) {
    io.debug_pc(i)        := fu_wb_reg.io.wb_in(i).pc

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

  // CP0 exceptional write back
  cp0.io.exc_info := fu_wb_reg.io.wb_exc_info
}
