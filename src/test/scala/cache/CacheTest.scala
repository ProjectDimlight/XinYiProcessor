package cache

import org.scalatest._

import chisel3._
import chiseltest._
import chisel3.experimental.BundleLiterals._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import xinyi_s5i4_bc.caches._

class CacheTB extends Module {
    val io = IO(new Bundle{
        val rd = Bool()
    })
    val cache = Module(new DCache)

    val mem = Module(new AXI4RAM(memByte = 128 * 1024 * 1024)) // 0x8000000

}