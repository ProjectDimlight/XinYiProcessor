package xinyi_s5i4_bc.stages

import chisel3._
import wrap._
import xinyi_s5i4_bc.parts._
import xinyi_s5i4_bc.caches._

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

class Instruction extends Bundle with XinYiConfig {
  val pc   = UInt(lgc_addr_w.W)
  val inst = UInt(data_w.W)
  val dec  = new ControlSet
}

// Issue Queue
class ISStage extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val in        = Input(Vec(fetch_num, Flipped(new Instruction)))
    val bc        = Flipped(new BranchCacheOut)
    val stall     = Output(Bool())
    val alu_path  = Output(Vec(alu_path_num, new Instruction))
    val mdu_path  = Output(Vec(mdu_path_num, new Instruction))
    val lsu_path  = Output(Vec(lsu_path_num, new Instruction))
  })

  val queue = Reg(Vec(queue_len, new Instruction))
  val head  = RegInit(0.U(queue_len_w.W))
  val tail  = RegInit(0.U(queue_len_w.W))
  val size  = RegInit(0.U(queue_len_w.W))
  
  val inst = Wire(Vec(issue_num, new Instruction))
  val issue = Wire(Vec(issue_num, new Bool))

  // The next instruction comes from branch cache
  when (bc.branch_cache_overwrite) {
    inst := bc.inst
  }
  // The next instruction is from the queue
  .otherwise {    
    for (i <- 0 until issue_num) {
      when (head + i.U(queue_len_w.W) < queue_len.U(queue_len_w.W)) {
        io.out(i) := queue(i.U(queue_len_w.W) + head)
      }
      .otherwise {
        io.out(i) := queue(head + (16 + i - queue_len).U(queue_len_w.W))
      }
    }
  }

  // For each instruction, decide which path it should go
  val issue_cnt = Wire(UInt(queue_len_w.W))
  val alu_used  = Wire(UInt(alu_path_num.W))
  val mdu_used  = Wire(UInt(mdu_path_num.W))
  val lsu_used  = Wire(UInt(lsu_path_num.W))
  
  issue_cnt := 0.U(32.W)
  alu_used  := 0.U(alu_paths.W)
  mdu_used  := 0.U(mdu_paths.W)
  lsu_used  := 0.U(lsu_paths.W)

  for (i <- 0 until issue_num) {
    // TODO: Detect conflicts
    for (j <- 0 until issue_num) {
      
    }

    // Ordered issuing
    // If an instruction fails to issue
    // Then all instructions afterwards will also be stalled
    if (i != 0)
      io.out(i) := io.out(i-1) & available(i)
    else
      io.out(i) := true
    
    when (io.available(i)) {
      issue_cnt := i.U(queue_len.W)
      
      // Instruction type
      // Mem
      when (inst(i).dec.mem_width != MemXXX) {
        for (j <- 0 until lsu_paths) {
          when (!lsu_used(i)) {
            lsu_used(i) 
          }
        }
      }
      // Mult / Div
      .elsewhen (inst(i).dec.mdu) {
        io.out(2) := inst(i)
        used(3) := 1.U(1.W)
      }
      // ALU1
      .elsewhen () {
        io.out(1) := inst(i)
        used(3) := 1.U(1.W)
      }
      // ALU0
      .otherwise {
        io.out(0) := inst(i)
        used(3) := 1.U(1.W)
      }
    }
  }

  // TODO: Put instructions into paths
   
  
}
