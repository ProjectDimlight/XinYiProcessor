package xinyi_s5i4_bc.fu

import chisel3._
import chisel3.util._
import config.config._
import xinyi_s5i4_bc.stages._

/**
 * @module LSU
 * @param XLEN width of data
 * @param lsu_ctrl_bits
 */
class LSU(addr_bits: Int, lsu_ctrl_bits: Int) extends Module {
  val io = IO(new Bundle {
    val in   = new FUIn 
    val out  = new FUOut

    // To DCache
    val cache           = Flipped(new RAMInterface(LGC_ADDR_W, L1_W))
    val stall_req       = Input(Bool())
    
    val exception_order = Input(UInt(ISSUE_NUM.W))
  })

  val addr = Wire(UInt(LGC_ADDR_W.W))
  addr := in.a + in.imm

  val normal = exception_order > in.order

  io.cache.wt   := normal & (in.write_target === DMem)
  io.cache.rd   := normal & (in.rd =/= 0.U)  
  io.cache.addr := addr
  io.cache.din  := rt
  
  out.hi        := addr
  out.data      := io.cache.dout
  out.ready     := !cache.stall_req

  out.write_target := in.write_target
  out.rd           := in.rd
  out.order        := in.order
  out.pc           := in.pc
  out.exception    := MuxLookup(
    in.fu_ctrl,
    false.B,
    Array(
      MemHalf  -> addr(0) === 0.U(1.W),
      MemHalfH -> addr(0) === 0.U(1.W),
      MemWord  -> addr(1, 0) === 0.U(2.W)
    )
  )
  
}

