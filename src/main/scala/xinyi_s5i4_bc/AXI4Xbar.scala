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

  val s_idle :: s_readResp :: s_writeResp :: Nil = Enum(3)
  val r_state = RegInit(s_idle)
  val inputArb_r = Module(new Arbiter(new AXI4BundleAR, n))
  (inputArb_r.io.in zip io.in.map(_.ar)).map { case (arb, in) => arb <> in }
  val thisReq_r = inputArb_r.io.out
  val inflightSrc_r = Reg(UInt(log2Ceil(n).W))

  io.out.ar.bits := Mux(
    r_state === s_idle,
    thisReq_r.bits,
    io.in(inflightSrc_r).ar.bits
  )
  // bind correct valid and ready signals
  io.out.ar.valid := thisReq_r.valid && (r_state === s_idle)
  io.in.map(_.ar.ready := false.B)
  thisReq_r.ready := io.out.ar.ready && (r_state === s_idle)

  io.in.map(_.r.bits := io.out.r.bits)
  io.in.map(_.r.valid := false.B)
  (io.in(inflightSrc_r).r, io.out.r) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(r_state) {
    is(s_idle) {
      when(thisReq_r.fire()) {
        inflightSrc_r := inputArb_r.io.chosen
        io.in(inputArb_r.io.chosen).ar.ready := true.B
        when(thisReq_r.valid) { r_state := s_readResp }
      }
    }
    is(s_readResp) {
      when(io.out.r.fire() && io.out.r.bits.last) { r_state := s_idle }
    }
  }

  val w_state = RegInit(s_idle)
  val inputArb_w = Module(new Arbiter(new AXI4BundleAW, n))
  (inputArb_w.io.in zip io.in.map(_.aw)).map { case (arb, in) => arb <> in }
  val thisReq_w = inputArb_w.io.out
  val inflightSrc_w = Reg(UInt(log2Ceil(n).W))

  io.out.aw.bits := Mux(
    w_state === s_idle,
    thisReq_w.bits,
    io.in(inflightSrc_w).aw.bits
  )
  // bind correct valid and ready signals
  io.out.aw.valid := thisReq_w.valid && (w_state === s_idle)
  io.in.map(_.aw.ready := false.B)
  thisReq_w.ready := io.out.aw.ready && (w_state === s_idle)

  io.out.w.valid := io.in(inflightSrc_w).w.valid
  io.out.w.bits := io.in(inflightSrc_w).w.bits
  io.in.map(_.w.ready := false.B)
  io.in(inflightSrc_w).w.ready := io.out.w.ready

  io.in.map(_.b.bits := io.out.b.bits)
  io.in.map(_.b.valid := false.B)
  (io.in(inflightSrc_w).b, io.out.b) match {
    case (l, r) => {
      l.valid := r.valid
      r.ready := l.ready
    }
  }

  switch(w_state) {
    is(s_idle) {
      when(thisReq_w.fire()) {
        inflightSrc_w := inputArb_w.io.chosen
        io.in(inputArb_w.io.chosen).aw.ready := true.B
        when(thisReq_w.valid) { w_state := s_writeResp }
      }
    }
    is(s_writeResp) {
      when(io.out.b.fire()) { w_state := s_idle }
    }
  }

  // printf(p"[${GTimer()}]: XbarNto1 Debug Start-----------\n")
  // printf(
  //   p"r_state=${r_state},inflightSrc_r=${inflightSrc_r},w_state=${w_state},inflightSrc_w=${inflightSrc_w}\n"
  // )
  // printf(p"inputArb_r.io.chosen=${inputArb_r.io.chosen}, inputArb_w.io.chosen=${inputArb_w.io.chosen}\n")
  // printf(p"thisReq_r=${thisReq_r}, thisReq_w=${thisReq_w}\n")
  // for (i <- 0 until n) {
  //   printf(p"io.in(${i}): \n${io.in(i)}\n")
  // }
  // printf(p"io.out: \n${io.out}\n")
  // printf("--------------------------------\n")
}
