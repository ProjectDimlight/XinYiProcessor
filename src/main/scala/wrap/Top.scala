package wrap

import chisel3._
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.caches._
import experiments._

trait XinYiConfig {
  val phy_addr_w      = 32
  val lgc_addr_w      = 32
  val data_w          = 32
  val queue_len       = 8
  
  val start_addr      = 0x80000000L

  val bc_num          = 4
  val fetch_num       = 2
  val issue_num       = 2
  val issue_num_w     = 3
  
  val n_a_path_id     = 0

  val alu_path_num    = 2
  val alu_path_id     = 1

  val bju_path_num    = 0
  val bju_path_id     = 2
  
  val lsu_path_num    = 2
  val lsu_path_id     = 3

  val queue_len_w     = 4
  val path_w          = 2
  
  val l1_w            = 64
  val l2_w            = 64

  val bc_line_size    = 2
  val bc_line_size_w  = 2
}

class CoreIO extends Bundle with XinYiConfig {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(Array("--target-dir", "verilog"), () => new IssueQueue())
  }
}