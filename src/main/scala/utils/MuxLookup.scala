package utils

import chisel3._
import chisel3.util._
import utils._

// ref: https://cnrv.io/challenge/ch001-mux-n
object MuxN {
  private def iter[S <: UInt, T <: Data](d: Vec[T], s: S, dw: Int): T =
    dw match {
      case 1 => d(0)
      case 2 => Mux(s(0), d(1), d(0))
      case _ =>
        val sw = s.getWidth
        val half = 1 << (sw - 1)
        val sd = d.grouped(half).toSeq
        Mux(
          s(sw - 1),
          iter(VecInit(sd(1)), s(sw - 2, 0), sd(1).size),
          iter(VecInit(sd(0)), s(sw - 2, 0), sd(0).size)
        )
    }

  def apply[S <: UInt, T <: Data](d: Vec[T], s: S): T = iter(d, s, d.size)
}

object MuxLookupBi {
  def apply[S <: UInt, T <: Data](
      idx: S,
      default: T,
      mapping: Seq[(S, T)]
  ): T = {
    val sw = idx.getWidth
    val dw = (1 << sw)

    val d = VecInit(Seq.fill(dw)(default))
    for ((k, v) <- mapping) {
      d(k) := v
    }

    MuxN(d, idx)
  }
}

object VecIndex {
  def apply[S <: UInt, T <: Data](idx: S, default: T, len: Int, array: Vec[T]): T = {
    MuxLookupBi(idx, default, Seq.tabulate(len)(i => (i.U, array(i))))
  }
}

object ListLookup1H {

  def apply[T <: Data](addr: UInt, default: List[T], mapping: Array[(BitPat, List[T])]): List[T] = {
    val hit  = mapping.map(m => (m._1 === addr))
    val data = mapping.map(m => m._2)
    val miss = !hit.foldLeft(false.B)((a, b) => (a | b))

    val hit_d = miss +: hit
    val data_d = default +: data

    List(
      Mux1H(hit_d, data_d.map(d => d(0))),
      Mux1H(hit_d, data_d.map(d => d(1))),
      Mux1H(hit_d, data_d.map(d => d(2))),
      Mux1H(hit_d, data_d.map(d => d(3))),
      Mux1H(hit_d, data_d.map(d => d(4))),
      Mux1H(hit_d, data_d.map(d => d(5))),
      Mux1H(hit_d, data_d.map(d => d(6))),
      Mux1H(hit_d, data_d.map(d => d(7))),
      Mux1H(hit_d, data_d.map(d => d(8))),
      Mux1H(hit_d, data_d.map(d => d(9)))
    )
  }
}