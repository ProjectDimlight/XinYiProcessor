package xinyi_s5i4_bc

import chisel3._
import config.config._

class S5I4 extends Module {
  //val io = IO(new CoreIO)
  val io = IO(new Bundle{})

  val dataPath = Module(new DataPath)
  //val controlPath = Module(new ControlPath)
}
