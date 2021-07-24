package xinyi_s5i4_bc

import chisel3._
import chisel3.util._

abstract class AXI4BundleBase() extends Bundle

trait AXI4Parameters {
  // These are all fixed by the AXI4 standard:
  val lenBits = 4
  val sizeBits = 3
  val burstBits = 2
  val lockBits = 2
  val cacheBits = 4
  val protBits = 3
  val qosBits = 0
  val respBits = 2

  // These are not fixed:
  val idBits = 4
  val addrBits = 32
  val dataBits = 32
  val userBits = 0
}

class AXI4BundleA extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val addr = Output(UInt(addrBits.W))
  val len = Output(UInt(lenBits.W)) // number of beats - 1
  val size = Output(UInt(sizeBits.W)) // bytes in beat = 2^size
  val burst = Output(UInt(burstBits.W)) // burst type
  val lock = Output(UInt(lockBits.W)) // lock type
  val cache = Output(UInt(cacheBits.W)) // memory type
  val prot = Output(UInt(protBits.W)) // protection type
  // val qos = Output(UInt(qosBits.W)) // 0=no QoS, bigger = higher priority
  //   val region = UInt(width = 4) // optional
  // val user = Output(UInt(userBits.W))

  override def toPrintable: Printable =
    p"addr = 0x${Hexadecimal(addr)}, len = ${len}, size = ${size}"
}

class AXI4BundleAW extends AXI4BundleA
class AXI4BundleAR extends AXI4BundleA

class AXI4BundleW extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val data = Output(UInt(dataBits.W))
  val strb = Output(UInt((dataBits / 8).W))
  val last = Output(Bool())
  // val user = Output(UInt(userBits.W))

  override def toPrintable: Printable =
    p"data = 0x${Hexadecimal(data)}, strb = 0x${Hexadecimal(strb)}, last = ${last}"
}

class AXI4BundleR extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val data = Output(UInt(dataBits.W))
  val resp = Output(UInt(respBits.W))
  val last = Output(Bool())
  // val user = Output(UInt(userBits.W))

  override def toPrintable: Printable =
    p"data = 0x${Hexadecimal(data)}, resp = ${resp}, last = ${last}"
}

class AXI4BundleB extends AXI4BundleBase with AXI4Parameters {
  val id = Output(UInt(idBits.W))
  val resp = Output(UInt(respBits.W))
  // val user = Output(UInt(userBits.W))

  override def toPrintable: Printable = p"resp = ${resp}"
}

class AXI4Bundle extends AXI4BundleBase with AXI4Parameters {
  // Decoupled provides ready&valid bit
  val aw = Decoupled(new AXI4BundleAW) // address write
  val w = Decoupled(new AXI4BundleW) // data write
  val b = Flipped(Decoupled(new AXI4BundleB)) // write response
  val ar = Decoupled(new AXI4BundleAR) // address read
  val r = Flipped(Decoupled(new AXI4BundleR)) // data read

  override def toPrintable: Printable =
    p"aw: valid=${aw.valid}, ready=${aw.ready}, ${aw.bits}\nw: valid=${w.valid}, ready=${w.ready}, ${w.bits}\nb: valid=${b.valid}, ready=${b.ready}, ${b.bits}\nar: valid=${ar.valid}, ready=${ar.ready}, ${ar.bits}\nr: valid=${r.valid}, ready=${r.ready}, ${r.bits}\n"
}

class CrossbarNto1(n: Int) extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Vec(n, new AXI4Bundle))
    val out = new AXI4Bundle
  })

  val s_idle :: s_readReq :: s_readResp :: s_writeReq :: s_writeResp :: Nil =
    Enum(5)
  val r_state = RegInit(s_idle)
  val read_arbiter = Module(new Arbiter(new AXI4BundleAR, n))
  (read_arbiter.io.in zip io.in.map(_.ar)).map { case (arb, in) => arb <> in }
  val inflight_read_index = Reg(UInt(log2Ceil(n).W))
  val inflight_read_request = Reg(chiselTypeOf(read_arbiter.io.out.bits))

  io.out.ar.bits := inflight_read_request
  // bind correct valid and ready signals
  io.out.ar.valid := r_state === s_readReq
  io.in.map(_.ar.ready := false.B)
  read_arbiter.io.out.ready := (r_state === s_idle)

  io.in.map(_.r.bits := io.out.r.bits)
  io.in.map(_.r.valid := false.B)
  (io.in(inflight_read_index).r, io.out.r) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(read_arbiter.io.out.valid) {
        inflight_read_index := read_arbiter.io.chosen
        inflight_read_request := read_arbiter.io.out.bits
        r_state := s_readReq
      }
    }
    is(s_readReq) {
      when(io.out.ar.fire()) {
        io.in(inflight_read_index).ar.ready := true.B
        r_state := s_readResp
      }
    }
    is(s_readResp) {
      when(io.out.r.fire() && io.out.r.bits.last) { r_state := s_idle }
    }
  }

  val w_state = RegInit(s_idle)
  val write_arbiter = Module(new Arbiter(new AXI4BundleAW, n))
  (write_arbiter.io.in zip io.in.map(_.aw)).map { case (arb, in) => arb <> in }
  val inflight_write_index = Reg(UInt(log2Ceil(n).W))
  val inflight_write_request = Reg(chiselTypeOf(write_arbiter.io.out.bits))

  io.out.aw.bits := inflight_write_request
  // bind correct valid and ready signals
  io.out.aw.valid := w_state === s_writeReq
  io.in.map(_.aw.ready := false.B)
  write_arbiter.io.out.ready := w_state === s_idle

  io.out.w.valid := io.in(inflight_write_index).w.valid
  io.out.w.bits := io.in(inflight_write_index).w.bits
  io.in.map(_.w.ready := false.B)
  io.in(inflight_write_index).w.ready := io.out.w.ready

  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)
  (io.in(inflight_write_index).b, io.out.b) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(write_arbiter.io.out.valid) {
        inflight_write_index := write_arbiter.io.chosen
        inflight_write_request := write_arbiter.io.out.bits
        w_state := s_writeReq
        // io.in(write_arbiter.io.chosen).aw.ready := true.B
        // when(inflight_write_request.valid) { w_state := s_writeResp }
      }
    }
    is(s_writeReq) {
      when(io.out.aw.fire()) {
        io.in(inflight_write_index).aw.ready := true.B
        w_state := s_writeResp
      }
    }
    is(s_writeResp) {
      when(io.out.b.fire()) { w_state := s_idle }
    }
  }

  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)
  (io.in(inflight_write_index).b, io.out.b) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  // printf("-----------XbarNto1 Debug Start-----------\n")
  // printf(
  //   p"r_state=${r_state},inflight_read_index=${inflight_read_index},read_arbiter.io.chosen=${read_arbiter.io.chosen}\n"
  // )
  // printf(
  //   p"w_state=${w_state},inflight_write_index=${inflight_write_index}, write_arbiter.io.chosen=${write_arbiter.io.chosen}\n"
  // )
  // printf(p"inflight_write_request=${inflight_write_request}\n")
  // for (i <- 0 until n) {
  //   printf(p"io.in(${i}): \n${io.in(i)}\n")
  // }
  // printf(p"io.out: \n${io.out}\n")
  // printf("--------------------------------\n")
}
