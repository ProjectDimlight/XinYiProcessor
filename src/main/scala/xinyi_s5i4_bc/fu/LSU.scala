package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import utils._
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
  // To DCache
  val cache           = Flipped(new DCacheCPU)
  val stall_req       = Input(Bool())

  // Exception
  val exception_order = Input(UInt(ISSUE_NUM.W))
  val interrupt       = Input(Bool())
  val flush           = Input(Bool())
}

class LSU extends Module with LSUConfig {
  val io = IO(new LSUIO)

  io.out.is_delay_slot := io.in.is_delay_slot

  val addr = Wire(UInt(LGC_ADDR_W.W))
  addr := io.in.imm

  val exception = 
    io.in.fu_ctrl(1) & addr(0) | 
    io.in.fu_ctrl(2) & (addr(1) | addr(0))
  val rd_normal = !exception & !io.flush
  val wr_normal = (io.exception_order > io.in.order) & !exception & !io.interrupt & !io.flush

  val wr = (io.in.write_target === DMem)
  val rd = (io.in.rd =/= 0.U)

  val i_byte = io.in.a(7, 0)
  val i_half = io.in.a(15, 0)

  io.cache.wr   := wr_normal & wr
  io.cache.rd   := rd_normal & rd
  io.cache.size := io.in.fu_ctrl(2, 1)
  io.cache.addr := addr
  io.cache.din  := MuxLookupBi(
    io.in.fu_ctrl(2, 1),
    io.in.a,
    Array(
      0.U -> Cat(i_byte, i_byte, i_byte, i_byte),
      1.U -> Cat(i_half, i_half)
    )
  )

  val o_byte = MuxLookupBi(
    addr(1, 0),
    io.cache.dout(7, 0),
    Array(
      1.U -> io.cache.dout(15,  8),
      2.U -> io.cache.dout(23, 16),
      3.U -> io.cache.dout(31, 24)
    )
  )
  val o_half = Mux(
    addr(1),
    io.cache.dout(31, 16),
    io.cache.dout(15,  0)
  )

  io.out.hi        := addr
  io.out.data      := MuxLookupBi(
    io.in.fu_ctrl(2, 1),
    io.cache.dout,
    Array(
      0.U -> Cat(Mux(!io.in.fu_ctrl(0) & o_byte( 7), 0xffffff.U(24.W) , 0.U(24.W)), o_byte),
      1.U -> Cat(Mux(!io.in.fu_ctrl(0) & o_half(15),   0xffff.U(16.W) , 0.U(16.W)), o_half)
    )
  )
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
  io.out.exception := 
    (io.in.pc(1, 0) =/= 0.U) |
    (io.in.fu_ctrl === FU_XXX) |
    (rd & exception) |
    (wr & exception)
}

