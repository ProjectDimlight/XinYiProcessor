package wrap

import chisel3._
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.caches._

trait XinYiConfig {
  val phy_addr_w  = 32
  val lgc_addr_w  = 32
  val data_w      = 32
  
  val start_addr  = 0x80000000L

  val fetch_num   = 2
  val issue_num   = 2
  
  val l1_w        = 64
  val l2_w        = 64
}

class CoreIO extends Bundle with XinYiConfig {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(Array("--target-dir", "verilog"), () => new DataPath())
  }
}