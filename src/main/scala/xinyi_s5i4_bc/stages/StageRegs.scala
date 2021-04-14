package xinyi_s5i4_bc.stages

import chisel3._
import wrap._

class PCIFReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val pc_out    = Input(UInt(lgc_addr_w.W))
    val if_in     = Flipped(new IFIn)
  })

  val pc = RegInit(start_addr.U(lgc_addr_w.W))
  pc := io.pc_out
  
  io.if_in.pc := pc
}

class IFIDReg extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val if_out = Flipped(new IFOut)
    val id_in = Flipped(Vec(fetch_num, new IDIn))
  })

  val reg = RegNext(io.if_out)

  for (i <- 0 until fetch_num) {
    io.id_in(i).pc := reg.pc + (i * 4).U(data_w.W)
    io.id_in(i).inst := reg.inst((i + 1) * data_w - 1, i * data_w)
  }
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