
package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import ControlConst._
import ISAPatterns._
import config.config._
import xinyi_s5i4_bc.fu.ALUConfig

class Instruction extends Bundle {
  val pc = UInt(LGC_ADDR_W.W)
  val inst = UInt(DATA_W.W)
  val dec = new ControlSet
}

object ControlConst {
  val PATH_ALU      = 1.U(2.W)
  val PATH_MDU      = 2.U(2.W)
  val PATH_LSU      = 3.U(2.W)

  val InstXXX       = 0.U(4.W)
  val RType         = 1.U(4.W)
  val RSType        = 2.U(4.W)    // RType with shamt
  val RTType        = 3.U(4.W)    // RType with Trap
  val RMDType       = 4.U(4.W)    // RType with MDU
  val IType         = 5.U(4.W)
  val IBType        = 6.U(4.W)    // IType with branch / jump
  val IMType        = 7.U(4.W)    // IType with memory
  val JType         = 8.U(4.W)
  val JRType        = 9.U(4.W)
  val SType         = 10.U(4.W)   // Supervisor
  val Illegal       = 15.U(4.W)
  val INST_TYPE_W   = InstXXX.getWidth

  val PC4           = 0.U(3.W)
  val PCReg         = 1.U(3.W)
  val Branch        = 2.U(3.W)
  val Jump          = 3.U(3.W)
  val Trap          = 4.U(3.W)
  val Ret           = 5.U(3.W)
  val NEXT_PC_W     = PC4.getWidth

  val AXXX          = 0.U(3.W)
  val AReg          = 0.U(3.W)
  val ACP0          = 1.U(3.W)
  val AHi           = 2.U(3.W)
  val ALo           = 3.U(3.W)
  val AShamt        = 4.U(3.W)
  val PARAM_A_W     = AXXX.getWidth

  val BXXX          = 0.U(1.W)
  val BReg          = 0.U(1.W)
  val BImm          = 1.U(1.W)
  val PARAM_B_W     = BXXX.getWidth

  val DXXX          = 0.U(3.W)
  val DReg          = 0.U(3.W)
  val DCP0          = 1.U(3.W)
  val DHi           = 2.U(3.W)
  val DLo           = 3.U(3.W)
  val DMem          = 4.U(3.W)
  val DHiLo         = 5.U(3.W)
  val WRITE_TARGET_W= DXXX.getWidth

  val MemXXX        = 0.U(3.W)
  val MemWord       = 1.U(3.W)
  val MemByte       = 2.U(3.W)
  val MemByteU      = 3.U(3.W)
  val MemHalf       = 4.U(3.W)
  val MemHalfU      = 5.U(3.W)
  val MEM_WIDTH_W   = MemXXX.getWidth

  val PathXXX       = N_A_PATH_TYPE.U(2.W)
  val PathALU       = ALU_PATH_TYPE.U(2.W)
  val PathBJU       = BJU_PATH_TYPE.U(2.W)
  val PathLSU       = LSU_PATH_TYPE.U(2.W)

  val FU_CTRL_W     = 5
}

class ControlSet extends Bundle {
  val next_pc       = UInt(NEXT_PC_W.W)
  val param_a       = UInt(PARAM_A_W.W)
  val param_b       = UInt(PARAM_B_W.W)
  val write_target  = UInt(WRITE_TARGET_W.W)
  val fu_ctrl       = UInt(FU_CTRL_W.W)
  val path          = UInt(PATH_W.W)
  val rs1           = UInt(REG_ID_W.W)
  val rs2           = UInt(REG_ID_W.W)
  val rd            = UInt(REG_ID_W.W)
}

class MIPSDecoder extends Module with ALUConfig with BJUConfig {
  val io = IO(new Bundle{
    val inst = Input(UInt(DATA_W.W))
    val dec = Output(new ControlSet)
  })

  val SHAMT = io.inst(10,  6)
  val IMM   = io.inst(15,  0)
  val IRS   = io.inst(25, 21)
  val IRT   = io.inst(20, 16)
  val IRD   = io.inst(15, 11)
  val IRA   = 31.U(REG_ID_W.W)
  val IXX   = 0.U(REG_ID_W.W)

  // Decode

val control_signal = ListLookup(io.inst,
                    List(  PC4     ,  AXXX   ,  BXXX   ,  DXXX   , ALU_ADD   ,  PathALU   , IXX , IXX , IXX),
    Array(         //   |   PC     |   A     |   B     |  D      | Operation |  Path id   | rs1 | rs2 | rd |
                   //   | Select   | use rs1 | use rs2 | write   |   Type    |   Select   |     |     |    |
                   //   | next_pc  | param_a | param_b | wrt_tgt | fu_ctrl   | MultiIssue |     |     |    |
      NOP        -> List(  PC4     ,  AReg   ,  BReg   ,  DXXX   , ALU_ADD   ,  PathALU   , IRS , IRT , IXX),
      ADD        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_ADD   ,  PathALU   , IRS , IRT , IRD),
      ADDI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_ADD   ,  PathALU   , IRS , IXX , IRT),
      ADDU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_ADDU  ,  PathALU   , IRS , IRT , IRD),
      ADDIU      -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_ADDU  ,  PathALU   , IRS , IXX , IRT),
      SUB        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SUB   ,  PathALU   , IRS , IRT , IRD),
      SUBU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SUB   ,  PathALU   , IRS , IRT , IRD),
      SLT        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLT   ,  PathALU   , IRS , IRT , IRD),
      SLTI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SLT   ,  PathALU   , IRS , IXX , IRT),
      SLTU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLTU  ,  PathALU   , IRS , IRT , IRD),
      SLTIU      -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SLTU  ,  PathALU   , IRS , IXX , IRT),
      DIV        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_DIV   ,  PathALU   , IRS , IRT , IRD),
      DIVU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_DIVU  ,  PathALU   , IRS , IRT , IRD),
      MULT       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_MUL   ,  PathALU   , IRS , IRT , IRD),
      MULTU      -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_MULU  ,  PathALU   , IRS , IRT , IRD),
           
      AND        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_AND   ,  PathALU   , IRS , IRT , IRD),
      ANDI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_AND   ,  PathALU   , IRS , IXX , IRT),
      LUI        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_LUI   ,  PathALU   , IXX , IXX , IRT),
      NOR        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_NOR   ,  PathALU   , IRS , IRT , IRD),
      OR         -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_OR    ,  PathALU   , IRS , IRT , IRD),
      ORI        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_OR    ,  PathALU   , IRS , IXX , IRT),
      XOR        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_XOR   ,  PathALU   , IRS , IRT , IRD),
      XORI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_XOR   ,  PathALU   , IRS , IXX , IRT),
           
      SLLV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLL   ,  PathALU   , IRS , IRT , IRD),
      SLL        -> List(  PC4     ,  AShamt ,  BReg   ,  DReg   , ALU_SLL   ,  PathALU   , IXX , IRT , IRD),
      SRAV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SRA   ,  PathALU   , IRS , IRT , IRD),
      SRA        -> List(  PC4     ,  AShamt ,  BReg   ,  DReg   , ALU_SRA   ,  PathALU   , IXX , IXX , IRD),
      SRLV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SRL   ,  PathALU   , IRS , IRT , IRD),
      SRL        -> List(  PC4     ,  AShamt ,  BReg   ,  DReg   , ALU_SRL   ,  PathALU   , IXX , IXX , IRD),
           
      BEQ        -> List(  Branch  ,  AReg   ,  BReg   ,  DXXX   , BrEQ      ,  PathALU   , IRS , IRT , IXX),
      BNE        -> List(  Branch  ,  AReg   ,  BReg   ,  DXXX   , BrNE      ,  PathALU   , IRS , IRT , IXX),
      BGEZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrGE      ,  PathALU   , IRS , IRT , IXX),
      BGTZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrGT      ,  PathALU   , IRS , IRT , IXX),
      BLEZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrLE      ,  PathALU   , IRS , IRT , IXX),
      BLTZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrLT      ,  PathALU   , IRS , IRT , IXX),
      BGEZAL     -> List(  Branch  ,  AReg   ,  BXXX   ,  DReg   , BrGEPC    ,  PathALU   , IRS , IRT , IRA),
      BLTZAL     -> List(  Branch  ,  AReg   ,  BXXX   ,  DReg   , BrLTPC    ,  PathALU   , IRS , IRT , IRA),
           
      J          -> List(  Jump    ,  AXXX   ,  BXXX   ,  DXXX   , ALU_ADD   ,  PathALU   , IXX , IXX , IXX),
      JAL        -> List(  Jump    ,  AXXX   ,  BXXX   ,  DReg   , ALU_PC    ,  PathALU   , IXX , IXX , IRA),
      JR         -> List(  PCReg   ,  AReg   ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IRS , IXX , IRD),
      JALR       -> List(  PCReg   ,  AReg   ,  BXXX   ,  DReg   , ALU_PC    ,  PathALU   , IRS , IXX , IRA),
           
      MFHI       -> List(  PC4     ,  AHi    ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IXX , IXX , IRD),
      MFLO       -> List(  PC4     ,  ALo    ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IXX , IXX , IRD),
      MTHI       -> List(  PC4     ,  AReg   ,  BXXX   ,  DHi    , ALU_ADD   ,  PathALU   , IRS , IXX , IXX),
      MTLO       -> List(  PC4     ,  AReg   ,  BXXX   ,  DLo    , ALU_ADD   ,  PathALU   , IRS , IXX , IXX),
           
      BREAK      -> List(  PC4     ,  AXXX   ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IXX , IXX , IXX),
      SYSCALL    -> List(  PC4     ,  AXXX   ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IXX , IXX , IXX),
           
      LB         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemByte   ,  PathLSU   , IRS , IXX , IRT),
      LBU        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemByteU  ,  PathLSU   , IRS , IXX , IRT),
      LH         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemHalf   ,  PathLSU   , IRS , IXX , IRT),
      LHU        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemHalfU  ,  PathLSU   , IRS , IXX , IRT),
      LW         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemWord   ,  PathLSU   , IRS , IXX , IRT),
      SB         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemByte   ,  PathLSU   , IRS , IRT , IXX),
      SH         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemHalf   ,  PathLSU   , IRS , IRT , IXX),
      SW         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemWord   ,  PathLSU   , IRS , IRT , IXX),
           
      ERET       -> List(  PC4     ,  AXXX   ,  BXXX   ,  DXXX   , ALU_ADD   ,  PathALU   , IXX , IXX , IXX),
      MFC0       -> List(  PC4     ,  ACP0   ,  BXXX   ,  DReg   , ALU_ADD   ,  PathALU   , IRD , IXX , IRT),
      MTC0       -> List(  PC4     ,  AReg   ,  BXXX   ,  DCP0   , ALU_ADD   ,  PathALU   , IRT , IXX , IRD)
  ))

  io.dec.next_pc       := control_signal(0)
  io.dec.param_a       := control_signal(1)
  io.dec.param_b       := control_signal(2)
  io.dec.write_target  := control_signal(3)
  io.dec.fu_ctrl       := control_signal(4)
  io.dec.path          := control_signal(5)
  io.dec.rs1           := control_signal(6)
  io.dec.rs2           := control_signal(7)
  io.dec.rd            := control_signal(8)
}

// Construct an NOP instruction
// To issue a bubble, or as debug input
object NOPBubble extends ALUConfig {
  def apply() = {
    val item = Wire(new Instruction)
    item.pc               := 0.U(LGC_ADDR_W.W)
    item.inst             := 0.U(DATA_W.W)
    item.dec.next_pc      := 0.U(NEXT_PC_W.W)
    item.dec.param_a      := 0.U(PARAM_A_W.W)
    item.dec.param_b      := 0.U(PARAM_B_W.W)
    item.dec.write_target := 0.U(WRITE_TARGET_W.W)
    item.dec.fu_ctrl      := 0.U(FU_CTRL_W.W)
    item.dec.path         := 0.U(PATH_W.W)
    item.dec.rs1          := 0.U(REG_ID_W.W)
    item.dec.rs2          := 0.U(REG_ID_W.W)
    item.dec.rd           := 0.U(REG_ID_W.W)
    item
  }
}

// Construct a decoded Instruction with given functions
object InstDecodedLitByPath extends ALUConfig {
  // Construction By path
  def apply(path_type: Int, rs1: Int, rs2: Int, rd: Int): Instruction = {
    val inst = new Instruction
    // ALU
    if (path_type == 1) {
      inst.Lit(
        _.pc               -> 0.U(LGC_ADDR_W.W),
        _.inst             -> 0.U(DATA_W.W),
        _.dec.next_pc      -> PC4,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DReg,
        _.dec.alu_op       -> ALU_ADD,
        _.dec.path         -> PathALU,
        _.dec.rs1          -> rs1.U(REG_ID_W.W),
        _.dec.rs2          -> rs2.U(REG_ID_W.W),
        _.dec.rd           -> rd.U(REG_ID_W.W)
      )
    }
    // BJU
    else if (path_type == 5) {
      inst.Lit(
        _.pc               -> 0x20.U(LGC_ADDR_W.W),
        _.inst             -> (rd & 0x0000FFFF).U(DATA_W.W),
        _.dec.next_pc      -> Branch,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> BrEQ,
        _.dec.path         -> PathALU,
        _.dec.rs1          -> rs1.U(REG_ID_W.W),
        _.dec.rs2          -> rs2.U(REG_ID_W.W),
        _.dec.rd           -> 0.U(REG_ID_W.W)
      )
    }
    // N/A
    else if (path_type == 2) {
      inst.Lit(
        _.pc               -> 0.U(LGC_ADDR_W.W),
        _.inst             -> 0.U(DATA_W.W),
        _.dec.next_pc      -> Branch,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> ALU_ADD,
        _.dec.path         -> PathBJU,
        _.dec.rs1          -> rs1.U(REG_ID_W.W),
        _.dec.rs2          -> rs2.U(REG_ID_W.W),
        _.dec.rd           -> rd.U(REG_ID_W.W)
      )
    }
    /*
    // LSU
    else if (path_type == 3) {
      // TODO
    }
    */
    else {
      inst.Lit(
        _.pc               -> 0.U(LGC_ADDR_W.W),
        _.inst             -> 0.U(DATA_W.W),
        _.dec.next_pc      -> PC4,
        _.dec.param_a      -> AXXX,
        _.dec.param_b      -> BXXX,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> ALU_XXX,
        _.dec.path         -> PathXXX,
        _.dec.rs1          -> rs1.U(5.W),
        _.dec.rs2          -> rs2.U(5.W),
        _.dec.rd           -> rd.U(5.W)
      )
    }
  }
}