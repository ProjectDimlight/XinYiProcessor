package xinyi_s5i4_bc.caches

import chisel3._
import chisel3.util._


class PLRU(SET_ASSOCIATIVE: Int = 4) extends Module {
  val io = IO(new Bundle {
    val update_index = Input(UInt(log2Ceil(SET_ASSOCIATIVE).W))
    val update       = Input(Bool())
    val flush        = Input(Bool()) // flush request
    val replace_vec  = Output(Vec(SET_ASSOCIATIVE, Bool()))
  })

  // the plru records
  val plru_nodes = Reg(Vec(SET_ASSOCIATIVE - 1, Bool()))

  // reset PLRU nodes
  when(reset.asBool() || io.flush) {
    for (i <- 0 until SET_ASSOCIATIVE - 1) {
      plru_nodes(i) := false.B
    }
  }

  // support limited PLRU associative
  if (SET_ASSOCIATIVE == 4) {

    // calculate which way should be replaced
    when(plru_nodes(0)) {
      io.replace_vec := VecInit(false.B, false.B, ~plru_nodes(2), plru_nodes(2))
    }.otherwise {
      io.replace_vec := VecInit(~plru_nodes(1), plru_nodes(1), false.B, false.B)
    }

    // update PLRU records
    when(io.update) {
      switch(io.update_index) {
        is(0.U) {
          plru_nodes(0) := true.B
          plru_nodes(1) := true.B
        }
        is(1.U) {
          plru_nodes(0) := true.B
          plru_nodes(1) := false.B
        }
        is(2.U) {
          plru_nodes(0) := false.B
          plru_nodes(2) := true.B
        }
        is(3.U) {
          plru_nodes(0) := false.B
          plru_nodes(1) := false.B
        }
      }
    }
  } else {
    println("SET_ASSOCIATIVE of", SET_ASSOCIATIVE, "is not supported !!!")
    sys.exit(-1)
  }

}
