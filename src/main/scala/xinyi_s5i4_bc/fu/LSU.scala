package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.caches._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.parts._
import ControlConst._

trait LSUConfig {
  val MemByte       = 0.U(FU_CTRL_W.W)
  val MemByteU      = 1.U(FU_CTRL_W.W)
  val MemHalf       = 2.U(FU_CTRL_W.W)
  val MemHalfU      = 3.U(FU_CTRL_W.W)
  val MemWord       = 4.U(FU_CTRL_W.W)
}

class LSUIO extends FUIO {
  // To DCache
  val cache           = Flipped(new RAMInterface(LGC_ADDR_W, L1_W))
  val stall_req       = Input(Bool())
  
  // Exception
  val exception_order = Input(UInt(ISSUE_NUM.W))
}

class LSU extends Module with LSUConfig {
  val io = IO(new LSUIO)

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
  val normal = (io.exception_order > io.in.order) & !exception

  io.cache.wr   := normal & (io.in.write_target === DMem)
  io.cache.rd   := normal & (io.in.rd =/= 0.U)  
  io.cache.addr := addr
  io.cache.din  := io.in.b
  
  io.out.hi        := addr
  io.out.data      := io.cache.dout
  io.out.ready     := !io.stall_req

  io.out.write_target := io.in.write_target
  io.out.rd           := io.in.rd
  io.out.order        := io.in.order
  io.out.pc           := io.in.pc
  io.out.exception    := exception

}

