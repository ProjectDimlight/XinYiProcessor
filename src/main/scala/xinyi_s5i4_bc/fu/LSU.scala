package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import ControlConst._
import EXCCodeConfig._

trait LSUConfig {
  val MemByte       = 0.U(FU_CTRL_W.W)
  val MemByteU      = 1.U(FU_CTRL_W.W)
  val MemHalf       = 2.U(FU_CTRL_W.W)
  val MemHalfU      = 3.U(FU_CTRL_W.W)
  val MemWord       = 4.U(FU_CTRL_W.W)
}

class LSUIO extends FUIO {
  // Stall 
  val stall           = Input(Bool())

  // To DCache
  val cache           = Flipped(new RAMInterface(LGC_ADDR_W, L1_W))
  val stall_req       = Input(Bool())

  // Exception
  val exception_order = Input(UInt(ISSUE_NUM.W))
}

class LSU extends Module with LSUConfig {
  val io = IO(new LSUIO)

  io.out.is_delay_slot := io.in.is_delay_slot

  val addr = Wire(UInt(LGC_ADDR_W.W))
  addr := io.in.a + io.in.imm

  val exception = MuxLookup(
    io.in.fu_ctrl,
    false.B,
    Array(
      MemHalf  -> (addr(0) === 0.U(1.W)),
      MemHalfU -> (addr(0) === 0.U(1.W)),
      MemWord  -> (addr(1, 0) === 0.U(2.W))
    )
  )
  val normal = (io.exception_order > io.in.order) & !exception & !io.stall

  val wr = (io.in.write_target === DMem)
  val rd = (io.in.rd =/= 0.U)

  io.cache.wr   := normal & wr
  io.cache.rd   := normal & rd
  io.cache.size := io.in.fu_ctrl(2, 1)
  io.cache.addr := addr
  io.cache.din  := io.in.b

  io.out.hi        := addr
  io.out.data      := io.cache.dout
  io.out.ready     := !io.stall_req

  io.out.write_target := io.in.write_target
  io.out.rd           := io.in.rd
  io.out.order        := io.in.order
  io.out.pc           := io.in.pc
  io.out.exc_code     := MuxCase(
    NO_EXCEPTION,
    Array(
      (io.in.pc(1, 0) =/= 0.U) -> EXC_CODE_ADEL,
      (io.in.fu_ctrl === FU_XXX) -> EXC_CODE_RI,
      (rd & exception) -> EXC_CODE_ADEL,
      (wr & exception) -> EXC_CODE_ADES
    )
  )

}

