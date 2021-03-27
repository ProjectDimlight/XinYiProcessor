package wrap

import chisel3._
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.stages._
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
  val issue_num_w   = 3
  
  val alu_path_num  = 2
  val alu_path_id   = 1
  val mdu_path_num  = 1
  val mdu_path_id   = 2
  val lsu_path_num  = 1
  val lsu_path_id   = 3

  val queue_len_w   = 4
  val path_w        = 2
  
  val l1_w          = 64
  val l2_w          = 64
}

class CoreIO extends Bundle with XinYiConfig {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(Array("--target-dir", "verilog"), () => new ISStage())
  }
}