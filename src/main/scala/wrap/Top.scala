package wrap

import chisel3._
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.caches._
import experiments._

trait XinYiConfig {
  val phy_addr_w    = 32
  val lgc_addr_w    = 32
  val data_w        = 32
  val queue_len     = 8
  
  val start_addr    = 0x80000000L

  val fetch_num     = 2
  val issue_num     = 2
  val alu_path_num  = 4
  val mdu_path_num  = 4
  val lsu_path_num  = 4
  val queue_len_w   = 4
  
  val l1_w          = 64
  val l2_w          = 64
}

class CoreIO extends Bundle with XinYiConfig {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(Array("--target-dir", "verilog"), () => new Test())
  }
}