package xinyi_s5i4_bc.caches

import chisel3._
import config.config._

class ICacheAXI extends Bundle {
  val addr_in     = Output(UInt(PHY_ADDR_W.W))
  val en          = Output(Bool())
  val addr_out    = Input(UInt(PHY_ADDR_W.W))
  val data        = Input(UInt(XLEN.W))
  val stall       = Input(Bool())
  val valid       = Input(Bool())
}

class DummyICache extends Module {
  val io = IO(new Bundle{
    val upper = new RAMInterface(LGC_ADDR_W, L1_W)
    val lower = new ICacheAXI
  
    val stall_req = Output(Bool())
  })

  io.lower.addr_in  := io.upper.addr
  io.lower.en       := io.upper.rd
  
  when (io.lower.valid)
  {
    io.upper.dout   := io.lower.data
    io.stall_req    := false.B
  }
  .otherwise {
    io.upper.dout   := 0.U
    io.stall_req    := true.B
  }
}

class DCacheAXI extends Bundle {
  val addr_in     = Output(UInt(PHY_ADDR_W.W))
  val data_in     = Output(UInt(XLEN.W))
  val wr          = Output(Bool())
  val rd          = Output(Bool())
  val size        = Output(UInt(3.W))
  val addr_out    = Input(UInt(PHY_ADDR_W.W))
  val data_out    = Input(UInt(XLEN.W))
  val stall       = Input(Bool())
  val valid       = Input(Bool())
}

class DummyDCache extends Module {
  val io = IO(new Bundle{
    val upper = Vec(LSU_PATH_NUM, new RAMInterface(LGC_ADDR_W, L1_W))
    val lower = Vec(LSU_PATH_NUM, new DCacheAXI)

    val stall_req = Output(Vec(LSU_PATH_NUM, Bool()))
  })

  for (j <- 0 until LSU_PATH_NUM) {
    io.lower(j).size     := 0.U
    io.lower(j).addr_in  := io.upper(j).addr
    io.lower(j).data_in  := io.upper(j).din
    io.lower(j).rd       := io.upper(j).rd
    io.lower(j).wr       := io.upper(j).wr

    when (io.lower(j).valid) {
      io.upper(j).dout   := io.lower(j).data_out
      io.stall_req(j)    := false.B
    }
    .otherwise {
      io.upper(j).dout   := 0.U
      io.stall_req(j)    := true.B
    }
  }
}