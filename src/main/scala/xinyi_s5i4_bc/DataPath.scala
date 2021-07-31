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

class DataPath extends Module with ALUConfig {
  //val io = IO(new DataPathIO)
  val io = IO(new Bundle{
    val interrupt   = Input(Vec(6, Bool()))
    //val icache_axi  = new ICacheAXI
    //val dcache_axi  = Vec(LSU_PATH_NUM, new DCacheAXI)
    val icache_axi  = new AXIIO
    val dcache_axi  = Vec(LSU_PATH_NUM, new AXIIO)

    val debug_pc    = Output(Vec(ISSUE_NUM, UInt(XLEN.W)))
  })

  val pc_if_reg     = Module(new PCIFReg)
  val if_id_reg     = Module(new IFIDReg)
  val issue_queue   = Module(new IssueQueue)
  val is_bju_reg    = Module(new ISBJUReg)
  val is_fu_reg     = Module(new ISFUReg)
  val fu_wb_reg     = Module(new FUWBReg)
  val interrupt_reg = Module(new InterruptReg)
  val tlb_read_reg  = Module(new TLBReadReg)

  val icache = Module(new ICache)
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
  val tlb           = Module(new TLB)

  // Regs
  val regs          = Module(new Regs)
  val cp0           = Module(new CP0)
  val hilo          = Module(new HiLo)
  
  val forwarding    = Wire(Vec(TOT_PATH_NUM, new Forwarding))

  // Flush
  pc_if_reg.  io.flush := flush
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
  if_stage.io.tlb     <> tlb.io.path(LSU_PATH_NUM)
  if_stage.io.cache   <> icache.io.cpu_io
  if_stage.io.full    <> issue_queue.io.full
  if_stage.io.out     <> if_id_reg.io.if_out

  icache.io.axi_io      <> io.icache_axi
  
  stall_frontend := icache.io.cpu_io.stall_req | issue_queue.io.full
  
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
  issue_queue.io.stall_backend := stall_backend

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
        ALo    -> hilo.io.out_lo
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
      val fwd = MuxLookupBi(j(1, 0),
        if (LSU_PATH_NUM == 1) forwarding(0) else forwarding(3),
        Array(
          0.U -> forwarding(0),
          1.U -> forwarding(1),
          2.U -> forwarding(2)
        )
      )
      inst_params(i)(0) := Mux(inst.dec.param_a === AHi, fwd.hi, fwd.data)
    }

    when (is_stage.io.forwarding_path_id(i).rs2 =/= TOT_PATH_NUM.U & 
      !((inst.dec.param_b === BImm) & (inst.dec.path === PathALU))
    ) {
      val j = is_stage.io.forwarding_path_id(i).rs2
      val fwd = MuxLookupBi(j(1, 0),
        if (LSU_PATH_NUM == 1) forwarding(0) else forwarding(3),
        Array(
          0.U -> forwarding(0),
          1.U -> forwarding(1),
          2.U -> forwarding(2)
        )
      )
      inst_params(i)(1) := fwd.data
    }
  }

  // IS-FU regs
  val is_out = Wire(Vec(TOT_PATH_NUM, new FUIn))
  for (j <- 0 until TOT_PATH_NUM) {
    is_out(j).write_target  := is_stage.io.path(j).write_target
    is_out(j).rd            := is_stage.io.path(j).rd
    is_out(j).fu_ctrl       := is_stage.io.path(j).fu_ctrl
    is_out(j).pc            := is_stage.io.path(j).pc
    is_out(j).order         := is_stage.io.path(j).order
    is_out(j).a             := inst_params(is_stage.io.path(j).order)(0)
    is_out(j).b             := inst_params(is_stage.io.path(j).order)(1)
    if (j >= ALU_PATH_NUM)
      is_out(j).imm         := issue_queue.io.inst(is_stage.io.path(j).order).imm + is_out(j).b
    else
      is_out(j).imm         := issue_queue.io.inst(is_stage.io.path(j).order).imm
      // Precalculate add or sub
    is_out(j).ov          := Mux(
      is_out(j).fu_ctrl(1),
      (is_out(j).a + is_out(j).b)(XLEN - 1),
      (is_out(j).a - is_out(j).b)(XLEN - 1)
    )
    is_out(j).is_delay_slot := is_stage.io.is_delay_slot(is_stage.io.path(j).order)
  }
  is_fu_reg.io.is_out                 := is_out
  is_fu_reg.io.is_actual_issue_cnt    := is_stage.io.actual_issue_cnt
  is_fu_reg.io.stall                  := stall_backend

  // IS-BJU regs
  is_bju_reg.io.is_path               := is_out(is_stage.io.branch_jump_id(0))
  is_bju_reg.io.is_branch_next_pc     := is_stage.io.branch_next_pc
  is_bju_reg.io.is_delay_slot_pending := is_stage.io.delay_slot_pending
  is_bju_reg.io.stall                 := stall_backend

  // BJU
  bju.io.path                         := is_bju_reg.io.fu_path
  bju.io.b_bc                         := is_bju_reg.io.fu_b_bc
  bju.io.imm_bc                       := is_bju_reg.io.fu_imm_bc
  bju.io.branch_next_pc               := is_bju_reg.io.fu_branch_next_pc

  // BC
  bc.io.in.branch := bju.io.branch
  bc.io.in.target := bju.io.target
  bc.io.in.target_bc := bju.io.target_bc
  bc.io.in.delay_slot_pending := is_bju_reg.io.fu_delay_slot_pending
  bc.io.stall_frontend := stall_frontend
  bc.io.stall_backend  := stall_backend
  bc.io.exception := fu_wb_reg.io.wb_exception_handled

  bc.io.wr.flush := flush
  bc.io.wr.stall := stall_frontend
  bc.io.wr.inst  := id_stage.io.out

  // FUs
  dcache.io.lower <> io.dcache_axi
  dcache.io.flush <> flush
  dcache.io.last_stall <> is_fu_reg.io.stalled
  dcache.io.stall <> stall_backend

  val exception_by_path = Wire(Vec(TOT_PATH_NUM, Bool()))

  val tlbw_path = Wire(Vec(LSU_PATH_NUM, Bool()))
  val tlbr_path = Wire(Vec(LSU_PATH_NUM, Bool()))
  val tlbp_path = Wire(Vec(LSU_PATH_NUM, Bool()))
  val tlbw = tlbw_path.asUInt().orR()
  val tlbr = tlbr_path.asUInt().orR()
  val tlbp = tlbp_path.asUInt().orR()
  

  def CreatePath(path_type: Int, j: Int, base: Int) = {
    if (path_type == 3) {
      var fu = Module(new LSU)
      fu.io.tlb       <> tlb.io.path(j - base)
      fu.io.cache     <> dcache.io.upper(j - base)
      fu.io.stall_req <> dcache.io.stall_req(j - base)

      tlbr_path(j - base) := fu.io.tlbr
      tlbw_path(j - base) := fu.io.tlbw
      tlbp_path(j - base) := fu.io.tlbp

      fu.io.in := is_fu_reg.io.fu_in(j)
      forwarding(j).write_target := fu.io.out.write_target
      forwarding(j).rd           := fu.io.out.rd
      forwarding(j).data         := fu.io.out.data
      forwarding(j).hi           := fu.io.out.hi
      forwarding(j).ready        := fu.io.out.ready
      forwarding(j).order        := fu.io.out.order

      if (j - base == 0) {
        fu.io.exception_in       := (fu.io.in.order =/= 0.U) & exception_by_path(0) 
      }
      else {
        fu.io.exception_in       := exception_by_path(base)
      }
      fu.io.interrupt       := has_interrupt
      fu.io.flush           := flush

      fu_stage.io.fu_out(j) := fu.io.out
      exception_by_path(j) := fu.io.out.exception

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

      exception_by_path(j) := fu.io.out.exception

      fu
    }
  }

  // FU TLBW
  tlb.io.asid            := cp0.io.write_tlb.entry_hi(7, 0)
  tlb.io.wen             := tlbw
  tlb.io.read .index     := cp0.io.write_tlb.index
  tlb.io.write.index     := cp0.io.write_tlb.index
  tlb.io.write.entry_hi  := cp0.io.write_tlb.entry_hi
  tlb.io.write.entry_lo0 := cp0.io.write_tlb.entry_lo0
  tlb.io.write.entry_lo1 := cp0.io.write_tlb.entry_lo1

  fu_stage.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt
  fu_stage.io.if_tlb_miss         := if_stage.io.tlb_miss
  fu_stage.io.if_tlb_addr         := if_stage.io.tlb_addr

  // FU WB Reg
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
    val int = interrupt(i)
    masked_interrupt(i) := int & cp0.io.int_mask_vec(i) & !cp0.io.exl
    when (masked_interrupt(i)) {
      has_interrupt := true.B
    }
  }
  fu_stage.io.incoming_interrupt := masked_interrupt

  // FU Interrupt Reg
  interrupt_reg.io.fu_pc := fu_stage.io.sorted_fu_out(0).pc
  interrupt_reg.io.fu_is_delay_slot :=  fu_stage.io.sorted_fu_out(0).is_delay_slot
  interrupt_reg.io.fu_actual_issue_cnt := is_fu_reg.io.fu_actual_issue_cnt
  interrupt_reg.io.eret := fu_stage.io.exc_info.eret
  interrupt_reg.io.fu_epc := fu_stage.io.fu_exception_target
  fu_stage.io.incoming_epc := interrupt_reg.io.wb_epc

  // FU TLBR Reg
  tlb_read_reg.io.fu_tlbp      := tlbp
  tlb_read_reg.io.fu_wen       := tlbr
  tlb_read_reg.io.fu_entry_hi  := tlb.io.read.entry_hi
  tlb_read_reg.io.fu_entry_lo0 := tlb.io.read.entry_lo0
  tlb_read_reg.io.fu_entry_lo1 := tlb.io.read.entry_lo1

  cp0.io.read_tlb_en        := tlb_read_reg.io.wb_wen
  cp0.io.tlb_probe_en       := tlb_read_reg.io.wb_tlbp
  cp0.io.read_tlb.entry_hi  := tlb_read_reg.io.wb_entry_hi
  cp0.io.read_tlb.entry_lo0 := tlb_read_reg.io.wb_entry_lo0
  cp0.io.read_tlb.entry_lo1 := tlb_read_reg.io.wb_entry_lo1
  

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
