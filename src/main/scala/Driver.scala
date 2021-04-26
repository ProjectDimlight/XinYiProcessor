import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import xinyi_s5i4_bc._
import experiments._

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//      this is a demo for SOL dalao
//
// "-td" is short for "--target-directory",
// and the sequenced target dir.
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
object Driver extends App {


  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //      some prettified prompt
  //
  //    basically it is out of
  //  ziyue's personal taste.
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
  val XINYI = Console.BOLD + Console.YELLOW + "\n[XinYiProcessor] " + Console.RESET

  def prompt(module_name: String) = {
    println(XINYI + "generating verilog code for " + Console.CYAN + module_name + Console.RESET)
  }

  //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
  //    start generating verilog code
  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

  val default_args = Array("-X", "verilog", "-td", "verilog")

  prompt("mycpu_top")
  (new ChiselStage).execute(
    default_args,
    Seq(ChiselGeneratorAnnotation(() => new S5I4))
  )

  /*
  prompt("CP0")
  (new ChiselStage).execute(
    default_args,
    Seq(ChiselGeneratorAnnotation(() => new CP0))
  )
  */
}

