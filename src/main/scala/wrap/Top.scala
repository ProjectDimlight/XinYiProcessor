package wrap

import chisel3._
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.stages._
//import xinyi_s5i4_bc.parts._

trait XinYiConfig {
  val addrw      = 32
  val instw      = 32
  val dataw      = 32
  val start_addr = 0x80000000L
}

class CoreIO extends Bundle with XinYiConfig {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    chisel3.Driver.execute(Array("--target-dir", "verilog"), () => new DataPath())
  }
}