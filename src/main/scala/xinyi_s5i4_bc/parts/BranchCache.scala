package xinyi_s5i4_bc.parts

import chisel3._
import wrap._

/*
class BranchCache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val target = new Vec(2, new ISIn)
    val branch_cache_overwrite = new Bool
  })

  for (i <- 0 until issue_num) {
    io.target(i).pc := 0.U(32.W)
    io.target(i).inst := 0.U(32.W)
  }
  io.branch_cache_overwrite := false.B
}
*/