package xinyi_s5i4_bc.parts

import chisel3._
import config.config._
import utils._

class BranchCacheRecord extends Bundle {
  val tag   = UInt((XLEN - BC_INDEX_W - 2).W)
  val valid = Bool()
  val inst  = Vec(2, Vec(BC_LINE_SIZE, new Instruction))
}

class BranchCacheWriteIn extends Bundle {
  val flush = Input(Bool())
  val stall = Input(Bool())
  val inst  = Input(Vec(FETCH_NUM, new Instruction))
}

class BranchCacheIn extends Bundle {
  val branch = Input(Bool())
  val delay_slot_pending = Input(Bool())
  val target = Input(UInt(LGC_ADDR_W.W))
}

class BranchCacheOut extends Bundle {
  val inst = Output(Vec(FETCH_NUM, new Instruction))
  val overwrite = Output(new Bool)
  val flush = Output(new Bool)
  val keep_delay_slot = Output(new Bool)
}

class BranchCache extends Module {
  val io = IO(new Bundle{
    val in  = new BranchCacheIn
    val out = new BranchCacheOut
    val wr  = new BranchCacheWriteIn
    val stall_frontend = Input(Bool())
    val stall_backend = Input(Bool())
    val branch_cached_pc = Output(UInt(LGC_ADDR_W.W))
  })

  val record    = RegInit(VecInit(Seq.fill(BC_INDEX)(0.U.asTypeOf(new BranchCacheRecord))))

  val state_reg = RegInit(2.U(BC_LINE_SIZE_W.W))
  val state     = Wire(UInt(BC_LINE_SIZE_W.W))
  val index_reg = RegInit(0.U(BC_INDEX_W.W))
  val index     = Wire(UInt(BC_INDEX_W.W))
  val hit_reg   = RegInit(false.B)
  val hit       = Wire(Bool())

  val write_pos = RegInit(2.U(BC_LINE_SIZE_W.W))
  val write_pc  = RegInit(0.U(XLEN.W))

  // Out
  // Default
  io.out.overwrite := false.B
  io.out.flush := false.B
  io.out.keep_delay_slot := false.B

  val id  = io.in.target(1 + BC_INDEX_W, 2)
  val row = MuxLookupBi(
    index,
    record(0),
    Array(
       1.U -> record(1),
       2.U -> record(2),
       3.U -> record(3),
       4.U -> record(4),
       5.U -> record(5),
       6.U -> record(6),
       7.U -> record(7),
       8.U -> record(8),
       9.U -> record(9),
      10.U -> record(10),
      11.U -> record(11),
      12.U -> record(12),
      13.U -> record(13),
      14.U -> record(14),
      15.U -> record(15)
    )
  )
  val ht  = row.valid & (io.in.target(XLEN - 1, 2 + BC_INDEX_W) === row.tag)

  // As the dummy BC always misses, next PC of PC stage should be target
  // If BC hits, next PC should be target + BC_LINE_SIZE * FETCH_NUM
  when (io.in.branch & !io.stall_backend) {
    io.out.flush := true.B
    io.out.keep_delay_slot := io.in.delay_slot_pending
    index     := id
    index_reg := id
    hit       := ht
    hit_reg   := ht
    state := 0.U

    when (!ht) {
      write_pos := 0.U;
      write_pc  := io.in.target;
    }
  }
  .otherwise {
    index := index_reg
    hit   := hit_reg
    state := state_reg
  }
  state_reg := Mux((state =/= 2.U) & !io.stall_frontend, state + 1.U, state)
  io.branch_cached_pc := Mux(ht, io.in.target + (BC_LINE_SIZE * FETCH_NUM * 4).U, io.in.target)

  when (state =/= 2.U) {
    // If hit, the queue will be overwritten with the contents of the BC
    // If miss, it will be filled with NOPBubbles, by default
    io.out.overwrite := true.B
  }
  io.out.inst      := Mux(hit, row.inst(state), VecInit(Seq.fill(BC_LINE_SIZE)(NOPBubble())))

  // In
  when (io.wr.flush) {
    record := VecInit(Seq.fill(16)(0.U.asTypeOf(new BranchCacheRecord)))
  }
  .elsewhen (!io.out.overwrite & !io.wr.stall) {
    val index = write_pc(1 + BC_INDEX_W, 2)
    val tag   = write_pc(XLEN - 1, 2 + BC_INDEX_W)

    when (write_pos === 0.U) {
      record(index).valid   := true.B
      record(index).tag     := tag
      record(index).inst(0) := io.wr.inst
      write_pos := 1.U
    }
    when (write_pos === 1.U) {
      record(index).inst(1) := io.wr.inst
      write_pos := 2.U
    }
  }

}
