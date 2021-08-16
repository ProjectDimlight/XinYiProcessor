import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import config.config._
import utils.{SimAXI, SimDataPath}
import xinyi_s5i4_bc._
import xinyi_s5i4_bc.caches._

object Verilator extends App {
  VERILATOR = true
  Main.prompt("SimDataPath (for verilator test)")

  def Gen(): Unit = {
    val default_args = Array("-X", "verilog", "-td", "src/main/verilog/sim")

    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new SimDataPath))
    )
  }

  Gen()
}

object SimAXITest extends App {
  VERILATOR = true
  Main.prompt("SimAXI")


  def Gen(): Unit = {
    val default_args = Array("-X", "verilog", "-td", "src/main/verilog/simaxi")

    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new SimAXI))
    )
  }

  Gen()
}


object Main extends App {
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //      some prettified prompt
  //
  //    basically it is out of
  //  ziyue's personal taste.
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  def prompt(module_name: String) = {
    val XINYI = Console.BOLD + Console.YELLOW + "\n[XinYiProcessor] " + Console.RESET
    println(XINYI + "generating verilog code for " + Console.CYAN + module_name + Console.RESET)
  }


  def Gen(): Unit = {

    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //    start generating verilog code
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

    val default_args = Array("-X", "verilog", "-td", "src/main/verilog/mycpu")

    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new S5I4))
    )
  }

  prompt("mycpu_top")
  Gen()
}

object CacheTB extends App {
  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //      some prettified prompt
  //
  //    basically it is out of
  //  ziyue's personal taste.
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  def prompt(module_name: String) = {
    val XINYI = Console.BOLD + Console.YELLOW + "\n[XinYiProcessor] " + Console.RESET
    println(XINYI + "generating verilog code for " + Console.CYAN + module_name + Console.RESET)
  }


  def Gen(): Unit = {

    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    //    start generating verilog code
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

    val default_args = Array("-X", "verilog", "-td", "src/main/verilog/mycpu")

    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new CacheTB))
    )
  }

  prompt("CacheTB")
  Gen()
}