import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import xinyi_s5i4_bc._
import config.config._
import xinyi_s5i4_bc.fu._
import xinyi_s5i4_bc.caches.ICache

object Verilator extends App {
  DIV_IP_CORE = false
  Main.prompt("mycpu_top (for verilator test)")
  Main.Gen()
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

    val default_args = Array("-X", "verilog", "-td", "verilog_modules/mycpu_top")

    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new S5I4))
    )
  }

  prompt("mycpu_top")
  Gen()
}


object ICache extends App {
  def Gen(): Unit = {
    val default_args = Array("-X", "verilog", "-td", "verilog_modules/test")
    (new ChiselStage).execute(
      default_args,
      Seq(ChiselGeneratorAnnotation(() => new ICache))
    )
  }

  Main.prompt("ICache")
  Gen()
}
