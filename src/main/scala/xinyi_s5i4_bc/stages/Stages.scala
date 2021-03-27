package xinyi_s5i4_bc.stages

import chisel3._
import chisel3.util._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._
import ControlConst._

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

  io.cache.addr := io.in.pc
  // If Cache instructions are supported, we might have to write into ICache
  // I don't know
  io.cache.din  := 0.U(32.W)
  
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

// Decode 2 instructions
// Branch Cache
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

class Issuer(path_id: Int, path_num: Int) extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val inst      = Input(Vec(issue_num, new Instruction))
    val target    = Input(Vec(issue_num, UInt(path_w.W)))
    val path      = Output(Vec(path_num, new Instruction))
    val issue_cnt = Output(UInt(issue_num_w.W))
  })

  io.issue_cnt := 0.U(issue_num_w.W)
  val id = Wire(Vec(path_num, UInt(4.W)))

  // for each path
  for (j <- 0 until path_num) {
    id(j) := issue_num.U(4.W)

    // For each instruciton in the queue
    // pre-decide whether it is available
    val available      = Wire(Vec(issue_num, Bool()))
    val available_pass = Wire(Vec(issue_num, Bool()))
    for (i <- 0 until issue_num) {
      if (j != 0)
        // It must have the correct type
        // And it's id must be greater than the last issued instruction with the same type
        // That is, it must havn not yet been issued
        available(i)      := ((io.target(i) === path_id.U) & (i.U > id(j-1)))
      else
        available(i)      :=  (io.target(i) === path_id.U)
    }

    available_pass(0) := false.B
    for (i <- 1 until issue_num)
      available_pass(i) := available_pass(i-1) | available(i-1)

    // find the FIRST fitting instruction (which hasn't been issued yet)
    for (i <- 0 until issue_num) {
      when (available(i) & ~available_pass(i)) {
        id(j) := i.U(4.W)
      }
    }

    when (id(j) < issue_num.U(4.W)) {
      io.path(j) := io.inst(id(j))
      io.issue_cnt := j.U(issue_num_w.W)
    }
    .otherwise {
      io.path(j) := NOPBubble()
    }
  }
}

object Issuer extends XinYiConfig {
  def apply(
    path_id  : Int,
    path_num : Int,
    inst     : Vec[Instruction],
    target   : Vec[UInt],
    path     : Vec[Instruction]
  ) = {
    val issuer = Module(new Issuer(path_id, path_num))
    issuer.io.inst   <> inst
    issuer.io.target <> target
    issuer.io.path   <> path
    issuer.io.issue_cnt
  }
}

// Issue Queue
class ISStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in        = Input(Vec(fetch_num, Flipped(new Instruction)))
    val bc        = Flipped(new BranchCacheOut)
    //val stall     = Output(Bool())
    val alu_path  = Output(Vec(alu_path_num, new Instruction))
    val mdu_path  = Output(Vec(mdu_path_num, new Instruction))
    val lsu_path  = Output(Vec(lsu_path_num, new Instruction))
  })

  val queue = Reg(Vec(queue_len, new Instruction))
  val head  = RegInit(0.U(queue_len_w.W))
  val tail  = RegInit(0.U(queue_len_w.W))
  val size  = RegInit(0.U(queue_len_w.W))
  
  val inst = Wire(Vec(issue_num, new Instruction))

  // The next instruction comes from branch cache
  when (io.bc.branch_cache_overwrite) {
    inst := io.bc.inst
  }
  // The next instruction is from the queue
  .otherwise {    
    for (i <- 0 until issue_num) {
      when (head + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
        inst(i) := queue(i.U(queue_len_w.W) + head)
      }
      .otherwise {
        inst(i) := queue(head + (16 + i - queue_len).U(queue_len_w.W))
      }
    }
  }

  // For each instruction, decide which path it should go
  val actual_issue_cnt = Wire(UInt(issue_num_w.W))
  val target = Wire(Vec(issue_num, UInt(path_w.W)))
  val available = Wire(Vec(issue_num, Bool()))

  actual_issue_cnt := 0.U(issue_num_w.W)

  for (i <- 0 until issue_num) {
    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    if (i == 0)
      available(i) := true.B
    else
      available(i) := available(i-1)

    // TODO: Detect hazards
    for (j <- 0 until issue_num) {
      
    }
    
    when (available(i)) {
      target(i) := MuxCase(
        alu_path_id.U(path_w.W),                                          // ALU
        Array(
          (inst(i).dec.mem_width =/= MemXXX) -> lsu_path_id.U(path_w.W),  // LSU
          (inst(i).dec.mdu)                  -> mdu_path_id.U(path_w.W)   // MDU
        )
      )
    } .otherwise {
      target(i) := 0.U(path_w.W)
    }
  }

  // Put instructions into paths
  // Parameterized issuing
  actual_issue_cnt :=
    Issuer(alu_path_id, alu_path_num, inst, target, io.alu_path) + 
    Issuer(mdu_path_id, mdu_path_num, inst, target, io.mdu_path) + 
    Issuer(lsu_path_id, lsu_path_num, inst, target, io.lsu_path)
  
  head := head + actual_issue_cnt
}
