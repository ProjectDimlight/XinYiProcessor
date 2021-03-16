package xinyi_s5i4_bc

import chisel3._
import wrap._
import xinyi_s5i4_bc.stages._

class DataPath extends Module with XinYiConfig {
  //val io = IO(new DataPathIO)
  val io = IO(new Bundle{
    val debug = new IDOut
  })

  val pc_if_reg = Module(new PCIFReg)
  val if_id_reg = Module(new IFIDReg)

  val branch_cache = Module(new BranchCache)
  val icache = Module(new ICache)

  val if_stage = Module(new IFStage)
  val id_stage = Module(new IDStage)

  if_stage.io.in <> pc_if_reg.io.if_in
  if_stage.io.bc <> branch_cache.io.inst_if
  if_stage.io.cache <> icache.io.cpu
  if_stage.io.out <> if_id_reg.io.if_out

  id_stage.io.in <> if_id_reg.io.id_in
  id_stage.io.bc <> branch_cache.io.inst_if
  id_stage.io.out <> io.debug
}
