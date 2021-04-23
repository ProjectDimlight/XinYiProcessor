import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import xinyi_s5i4_bc.fu.CP0
import xinyi_s5i4_bc.stages.WBStage

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

  prompt("WBStage")
  (new ChiselStage).execute(
    Array("-X", "verilog", "-td", "verilog"),
    Seq(ChiselGeneratorAnnotation(() => new WBStage))
  )


  prompt("CP0")
  (new ChiselStage).execute(
    Array("-X", "verilog", "-td", "verilog"),
    Seq(ChiselGeneratorAnnotation(() => new CP0))
  )
}

