package xinyi_s5i4_bc.parts

import chisel3._
import wrap._

class BranchCacheOut extends Bundle with XinYiConfig {
  val inst = Output(Vec(fetch_num, new Instruction))
  val branch_cache_overwrite = Output(new Bool)
}

class BranchCache extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val out = new BranchCacheOut
  })

  for (i <- 0 until fetch_num) {
    io.out.inst(i).pc := 0.U(32.W)
    io.out.inst(i).inst := 0.U(32.W)
  }
  io.out.branch_cache_overwrite := false.B
}
