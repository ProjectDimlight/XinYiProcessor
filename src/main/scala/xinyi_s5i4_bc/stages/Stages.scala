package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

class PCStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val pc      = Input(UInt(lgc_addr_w.W))
    //val bpu_out = Flipped(new BPUOut)
    //val bju_out = Flipped(new BJUOut)
    val next_pc = Output(UInt(lgc_addr_w.W))
  })

  io.next_pc := io.pc + 4.U(lgc_addr_w.W)
}

class IFIn extends Bundle with XinYiConfig {
  val pc = Input(UInt(lgc_addr_w.W))
}

class IFOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(l1_w.W))
}

// Load load_num instructions at a time
// Branch Cache
class IFStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IFIn
    val cache = Flipped(new RAMInterface(lgc_addr_w, l1_w))
    val out   = new IFOut
  })

  // ICache
  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  io.cache.din  := 0.U(32.W)
  
  // Output to IF-ID Regs
  io.out.pc := io.in.pc
  io.out.inst := io.cache.dout
}

class IDIn extends Bundle with XinYiConfig {
  val pc   = Input(UInt(lgc_addr_w.W))
  val inst = Input(UInt(data_w.W))
}

class IDOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(lgc_addr_w.W))
  val inst = Output(UInt(data_w.W))
  val dec  = Output(new ControlSet)
}

// Decode 1 instruction
// Generate multiple instances to support multi-issuing
class IDStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in    = new IDIn
    val out   = new IDOut
  })

  val decoder = Module(new MIPSDecoder)
  decoder.io.inst := io.in.inst

  io.out.pc   := io.in.pc
  io.out.inst := io.in.inst
  io.out.dec  := decoder.io.ctrl
}

class PathInterface extends Bundle with XinYiConfig {
  val wt          = Output(UInt(write_target_w.W))
  val rd          = Output(UInt(reg_id_w.W))
  val ready       = Output(Bool())
  val inst        = Input(new Instruction)
  val id          = Input(UInt(issue_num_w.W))
}

// Issue Queue
class IssueQueue extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in                = Input(Vec(fetch_num, Flipped(new Instruction)))
    val bc                = Flipped(new BranchCacheOut)
    val actual_issue_cnt  = Input(UInt(issue_num_w.W))
    val full              = Output(Bool())
    val issue_cnt         = Output(UInt(issue_num_w.W))
    val inst              = Output(Vec(issue_num, new Instruction))
  })

  def Step(base: UInt, offset: UInt) = {
    Mux(
      base +& offset >= queue_len.U,
      base + offset - queue_len.U,
      base + offset
    )
  }

  // Queue logic

  val queue   = Reg(Vec(queue_len, new Instruction))
  val head    = RegInit(0.U(queue_len_w.W))
  val tail    = RegInit(0.U(queue_len_w.W))
  val head_n  = Wire(UInt(queue_len_w.W))   // Next Head
  val tail_b  = Wire(UInt(queue_len_w.W))   // Actual Tail Base
  val size    = Wire(UInt(queue_len_w.W))

  tail_b := Mux(
    io.bc.flush,
    Mux(io.bc.keep_delay_slot, Step(head_n, 1.U(queue_len_w.W)), head_n),
    tail
  )
  size := Mux(
    tail_b >= head,
    tail_b - head,
    tail_b + queue_len.U - head
  )

  // Input
  when (size < (queue_len - fetch_num).U +& io.actual_issue_cnt) {
    for (i <- 0 until fetch_num) {
      when (tail_b + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
        queue(tail_b + i.U(queue_len_w.W)) := Mux(io.bc.overwrite, io.bc.inst(i), io.in(i))
      } 
      .otherwise {
        queue(tail_b + ((1 << queue_len_w) + i - queue_len).U(queue_len_w.W)) := Mux(io.bc.overwrite, io.bc.inst(i), io.in(i))
      }
    }

    tail := Step(tail_b, fetch_num.U(queue_len_w.W))
    io.full := false.B
  }
  .otherwise {
    io.full := true.B
  }

  // Output 
  io.issue_cnt := size
  for (i <- 0 until issue_num) {
    // If i > issue_cnt, the instruction path will be 0 (Stall)
    // So there is no need to clear the inst Vec here
    when (head + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
      io.inst(i) := queue(head + i.U(queue_len_w.W))
    }
    .otherwise {
      io.inst(i) := queue(head + ((1 << queue_len_w) + i - queue_len).U(queue_len_w.W))
    }

    // Issue until Delay Slot
    // If the Branch itself is not issued, it will be re-issued in the next cycle.
    // If the Branch is issued but the Delay Slot is not, 
    // The BJU would generate a branch_cache_overwrite signal along with a keep_delay_slot, 
    // Ensuring that the rest of the queue will be cleared while the Delay Slot works as is.
    when (io.inst(i).dec.next_pc =/= PC4) {
      // If the delay slot is already fetched (into the queue)
      when ((i + 2).U <= size) {
        io.issue_cnt := (i + 2).U
      }
      // The delay slot is not in the queue yet
      // Stall
      .otherwise {
        io.issue_cnt := 0.U
      }
    }
  }
  
  head_n := Step(head, io.actual_issue_cnt)
  head   := head_n
}

// Issue Stage
class ISStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val issue_cnt = Input(UInt(queue_len_w.W))
    val inst      = Input(Vec(issue_num, new Instruction))
    val alu_paths = Flipped(Vec(alu_path_num, new PathInterface))
    val bju_paths = Flipped(Vec(bju_path_num, new PathInterface))
    val lsu_paths = Flipped(Vec(lsu_path_num, new PathInterface))
    val actual_issue_cnt = Output(UInt(issue_num_w.W))
  })

  // Hazard Detect Logic  

  val inst = Wire(Vec(issue_num, new Instruction))
  val filtered_inst = Wire(Vec(issue_num, new Instruction))
  inst := io.inst

  // For each instruction, decide which path it should go
  val target = Wire(Vec(issue_num, UInt(path_w.W)))

  val issued_by_alu = Wire(Vec(issue_num, Bool()))
  val issued_by_bju = Wire(Vec(issue_num, Bool()))
  val issued_by_lsu = Wire(Vec(issue_num, Bool()))
  val issued        = Wire(Vec(issue_num, Bool()))
  val no_raw        = Wire(Vec(issue_num, Bool()))

  // Begin
  io.actual_issue_cnt := issue_num.U(issue_num_w.W)

  def RAWPath(i: Instruction, j: PathInterface) = {
    !j.ready & (j.rd === i.dec.rs1 | j.rd === i.dec.rs2)
  }

  def RAWInst(i: Instruction, k: Instruction) = {
    (k.dec.rd === i.dec.rs1 | k.dec.rd === i.dec.rs2)
  }

  // i is the id of the currect instruction to be detected
  for (i <- 0 until issue_num) {
    // Detect hazards

    // RAW Data hazard

    // From path (issued)
    no_raw(i) := true.B
    for (j <- 0 until alu_path_num) {
      when (RAWPath(inst(i), io.alu_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until bju_path_num) {
      when (RAWPath(inst(i), io.bju_paths(j))) {
        no_raw(i) := false.B
      }
    }
    for (j <- 0 until lsu_path_num) {
      when (RAWPath(inst(i), io.lsu_paths(j))) {
        no_raw(i) := false.B
      }
    }

    // From queue (going to issue)
    for (k <- 0 until i) {
      when (RAWInst(inst(i), inst(k))) {
        no_raw(i) := false.B
      }
    }

    // Target filter
    target(i) := Mux(
      (!no_raw(i) | (i.U >= io.issue_cnt)),
      0.U,
      inst(i).dec.path
    )

    // Structural hazard
    issued(i) := issued_by_alu(i) | issued_by_bju(i) | issued_by_lsu(i)

    // If an instruction cannot be issued
    // Mark its ID
    // Every following instruction (with a greater ID) will be replaced by an NOP Bubble
    when (issued(i) === false.B) {
      io.actual_issue_cnt := i.U(issue_num_w.W)
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    when (i.U(issue_num_w.W) >= io.actual_issue_cnt) {
      filtered_inst(i) := NOPBubble()
    }
    .otherwise {
      filtered_inst(i) := inst(i)
    }
  }

  /////////////////////////////////////////////////////////////////

  // Issue Logic

  // Put instructions into paths
  // Parameterized issuing
  Issuer(alu_path_id, alu_path_num, filtered_inst, target, issued_by_alu, io.alu_paths)
  Issuer(bju_path_id, bju_path_num, filtered_inst, target, issued_by_bju, io.bju_paths)
  Issuer(lsu_path_id, lsu_path_num, filtered_inst, target, issued_by_lsu, io.lsu_paths)
}