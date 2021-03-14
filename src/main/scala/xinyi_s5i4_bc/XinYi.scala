package xinyi_s5i4_bc

import chisel3._
import wrap._

class S5I4 extends Module with XinYiConfig {
  //val io = IO(new CoreIO)
  val io = IO(new Bundle{})

  val dataPath = Module(new DataPath)
  //val controlPath = Module(new ControlPath)
}
