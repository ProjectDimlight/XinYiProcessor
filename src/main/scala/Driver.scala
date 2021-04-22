import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import xinyi_s5i4_bc._

//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
//      this is a demo for SOL dalao
//
// "-td" is short for "--target-directory",
// and the sequenced target dir.
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
object Driver extends App {
  (new ChiselStage).execute(
    Array("-X", "verilog", "-td", "verilog"),
    Seq(ChiselGeneratorAnnotation(() => new S5I4))
  )
}

