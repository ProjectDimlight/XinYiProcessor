package wrap

import chisel3._
import chisel3.stage.ChiselStage

import xinyi_s5i4_bc._
import xinyi_s5i4_bc.stages._
import xinyi_s5i4_bc.caches._
import experiments._

trait XinYiConfig {
  
}

class CoreIO extends Bundle {
  
}

object Generate {
  def main(args: Array[String]): Unit = {
    (new ChiselStage).emitVerilog(new DataPath)
  }
}