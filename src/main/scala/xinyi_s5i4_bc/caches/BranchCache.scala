package xinyi_s5i4_bc.parts

import chisel3._
import wrap._

class BranchCacheOut extends Bundle with XinYiConfig {
  val pc   = Output(UInt(addrw.W))
  val inst = Output(UInt(instw.W))
  val branch_cache_overwrite = Output(Bool())
}

class BranchCache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val inst_if = new BranchCacheOut
    val inst_id = new BranchCacheOut
  })

  io.inst_if.pc := 0.U(addrw.W)
  io.inst_if.inst := 0.U(instw.W)
  io.inst_if.branch_cache_overwrite := 0.B


  io.inst_id.pc := 0.U(addrw.W)
  io.inst_id.inst := 0.U(instw.W)
  io.inst_id.branch_cache_overwrite := 0.B
}
