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
    val input   = new PathInterface 
    val output  = Flipped(new WBInterface)

    val exception_id  = Input(UInt(ISSUE_NUM.W))
    val exception     = Output(UInt(ISSUE_NUM.W))
  })

}

