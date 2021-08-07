
package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import utils._
import chisel3.experimental.BundleLiterals._
import ISAPatterns._
import config.config._
import xinyi_s5i4_bc.fu._

class Instruction extends Bundle {
  val pc  = UInt(LGC_ADDR_W.W)
  val imm = UInt(XLEN.W)
  val dec = new ControlSet
}

object ControlConst {
  val PC4           = 0.U(2.W)
  val PCReg         = 1.U(2.W)
  val Branch        = 2.U(2.W)
  val Jump          = 3.U(2.W)
  val NEXT_PC_W     = PC4.getWidth

  val AXXX          = 0.U(2.W)
  val AReg          = 0.U(2.W)
  val ACP0          = 1.U(2.W)
  val AHi           = 2.U(2.W)
  val ALo           = 3.U(2.W)
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
  val D_NONE        = 6.U(3.W)
  val WRITE_TARGET_W= DXXX.getWidth

  val PathXXX       = N_A_PATH_TYPE.U(2.W)
  val PathALU       = ALU_PATH_TYPE.U(2.W)
  val PathBJU       = BJU_PATH_TYPE.U(2.W)
  val PathLSU       = LSU_PATH_TYPE.U(2.W)

  val FU_CTRL_W     = 5

  val FU_BREAK      = 29.U(FU_CTRL_W.W)
  val FU_SYSCALL    = 30.U(FU_CTRL_W.W)
  val FU_XXX        = 31.U(FU_CTRL_W.W)
}

import ControlConst._

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

class MIPSDecoder extends Module with ALUConfig with BJUConfig with LSUConfig with CP0Config {
  val io = IO(new Bundle{
    val pc = Input(UInt(LGC_ADDR_W.W))
    val inst = Input(UInt(XLEN.W))
    val imm = Output(UInt(32.W))
    val dec = Output(new ControlSet)
  })

  val SHAMT = io.inst(10,  6)
  val IMM   = io.inst(15,  0)
  val IRS   = io.inst(25, 21)
  val IRT   = io.inst(20, 16)
  val IRD   = io.inst(15, 11)
  val IRA   = 31.U(REG_ID_W.W)
  val IXX   = 0.U(REG_ID_W.W)

  val signed    = Wire(SInt(32.W))
  val signed_x4 = Wire(SInt(32.W))
  signed    := io.inst(15, 0).asSInt()
  signed_x4 := Cat(io.inst(15, 0), 0.U(2.W)).asSInt()

  val pc4 = io.pc + 4.U
  val IImm = io.inst(15, 0)
  val ISImm = signed.asUInt()
  val ISHT = io.inst(10, 6)
  val IBr = signed_x4.asUInt() + pc4
  val IJ = Cat(pc4(31, 28), io.inst(25, 0), 0.U(2.W))

  // Decode

val control_signal = ListLookup(io.inst,
                    List(  PC4     ,  AXXX   ,  BXXX   ,  DXXX   , FU_XXX    ,  PathALU   , IXX , IXX , IXX, IImm),
    Array(         //   |   PC     |   A     |   B     |  D      | Operation |  Path id   | rs1 | rs2 | rd | IImm|
                   //   | Select   | use rs1 | use rs2 | write   |   Type    |   Select   |     |     |    |     |
                   //   | next_pc  | param_a | param_b | wrt_tgt | fu_ctrl   | MultiIssue |     |     |    |     |
      NOP        -> List(  PC4     ,  AReg   ,  BReg   ,  DXXX   , ALU_XXX   ,  PathALU   , IRS , IRT , IXX, IImm),
      ADD        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_ADD   ,  PathALU   , IRS , IRT , IRD, ISImm),
      ADDI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_ADD   ,  PathALU   , IRS , IXX , IRT, ISImm),
      ADDU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_ADDU  ,  PathALU   , IRS , IRT , IRD, ISImm),
      ADDIU      -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_ADDU  ,  PathALU   , IRS , IXX , IRT, ISImm),
      SUB        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SUB   ,  PathALU   , IRS , IRT , IRD, ISImm),
      SUBU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SUBU  ,  PathALU   , IRS , IRT , IRD, IImm),
      SLT        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLT   ,  PathALU   , IRS , IRT , IRD, ISImm),
      SLTI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SLT   ,  PathALU   , IRS , IXX , IRT, ISImm),
      SLTU       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLTU  ,  PathALU   , IRS , IRT , IRD, ISImm),
      SLTIU      -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SLTU  ,  PathALU   , IRS , IXX , IRT, ISImm),
      DIV        -> List(  PC4     ,  AReg   ,  BReg   ,  DHiLo  , ALU_DIV   ,  PathALU   , IRS , IRT , IRD, IImm),
      DIVU       -> List(  PC4     ,  AReg   ,  BReg   ,  DHiLo  , ALU_DIVU  ,  PathALU   , IRS , IRT , IRD, IImm),
      MULT       -> List(  PC4     ,  AReg   ,  BReg   ,  DHiLo  , ALU_MUL   ,  PathALU   , IRS , IRT , IRD, IImm),
      MULTU      -> List(  PC4     ,  AReg   ,  BReg   ,  DHiLo  , ALU_MULU  ,  PathALU   , IRS , IRT , IRD, IImm),

      MUL        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_MUL   ,  PathALU   , IRS,  IRT , IRD, IImm),
           
      AND        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_AND   ,  PathALU   , IRS , IRT , IRD, IImm),
      ANDI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_AND   ,  PathALU   , IRS , IXX , IRT, IImm),
      LUI        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_LUI   ,  PathALU   , IXX , IXX , IRT, IImm),
      NOR        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_NOR   ,  PathALU   , IRS , IRT , IRD, IImm),
      OR         -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_OR    ,  PathALU   , IRS , IRT , IRD, IImm),
      ORI        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_OR    ,  PathALU   , IRS , IXX , IRT, IImm),
      XOR        -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_XOR   ,  PathALU   , IRS , IRT , IRD, IImm),
      XORI       -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_XOR   ,  PathALU   , IRS , IXX , IRT, IImm),
           
      SLLV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SLL   ,  PathALU   , IRT , IRS , IRD, ISHT),
      SLL        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SLL   ,  PathALU   , IRT , IXX , IRD, ISHT),
      SRAV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SRA   ,  PathALU   , IRT , IRS , IRD, ISHT),
      SRA        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SRA   ,  PathALU   , IRT , IXX , IRD, ISHT),
      SRLV       -> List(  PC4     ,  AReg   ,  BReg   ,  DReg   , ALU_SRL   ,  PathALU   , IRT , IRS , IRD, ISHT),
      SRL        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , ALU_SRL   ,  PathALU   , IRT , IXX , IRD, ISHT),
           
      BEQ        -> List(  Branch  ,  AReg   ,  BReg   ,  DXXX   , BrEQ      ,  PathALU   , IRS , IRT , IXX, IBr),
      BNE        -> List(  Branch  ,  AReg   ,  BReg   ,  DXXX   , BrNE      ,  PathALU   , IRS , IRT , IXX, IBr),
      BGEZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrGE      ,  PathALU   , IRS , IXX , IXX, IBr),
      BGTZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrGT      ,  PathALU   , IRS , IXX , IXX, IBr),
      BLEZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrLE      ,  PathALU   , IRS , IXX , IXX, IBr),
      BLTZ       -> List(  Branch  ,  AReg   ,  BXXX   ,  DXXX   , BrLT      ,  PathALU   , IRS , IXX , IXX, IBr),
      BGEZAL     -> List(  Branch  ,  AReg   ,  BXXX   ,  DReg   , BrGEPC    ,  PathALU   , IRS , IXX , IRA, IBr),
      BLTZAL     -> List(  Branch  ,  AReg   ,  BXXX   ,  DReg   , BrLTPC    ,  PathALU   , IRS , IXX , IRA, IBr),
           
      J          -> List(  Jump    ,  AXXX   ,  BXXX   ,  DXXX   , ALU_XXX   ,  PathALU   , IXX , IXX , IXX, IJ),
      JAL        -> List(  Jump    ,  AXXX   ,  BXXX   ,  DReg   , JPC       ,  PathALU   , IXX , IXX , IRA, IJ),
      JR         -> List(  PCReg   ,  AXXX   ,  BReg   ,  DXXX   , ALU_XXX   ,  PathALU   , IXX , IRS , IXX, IImm),
      JALR       -> List(  PCReg   ,  AXXX   ,  BReg   ,  DReg   , JPC       ,  PathALU   , IXX , IRS , IRA, IImm),
           
      MFHI       -> List(  PC4     ,  AHi    ,  BXXX   ,  DReg   , ALU_OR    ,  PathALU   , IXX , IXX , IRD, IImm),
      MFLO       -> List(  PC4     ,  ALo    ,  BXXX   ,  DReg   , ALU_OR    ,  PathALU   , IXX , IXX , IRD, IImm),
      MTHI       -> List(  PC4     ,  AReg   ,  BXXX   ,  DHi    , ALU_OR    ,  PathALU   , IRS , IXX , IXX, IImm),
      MTLO       -> List(  PC4     ,  AReg   ,  BXXX   ,  DLo    , ALU_OR    ,  PathALU   , IRS , IXX , IXX, IImm),
           
      BREAK      -> List(  PC4     ,  AXXX   ,  BXXX   ,  DReg   , FU_BREAK  ,  PathALU   , IXX , IXX , IXX, IImm),
      SYSCALL    -> List(  PC4     ,  AXXX   ,  BXXX   ,  DReg   , FU_SYSCALL,  PathALU   , IXX , IXX , IXX, IImm),
           
      LB         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemByte   ,  PathLSU   , IXX , IRS , IRT, ISImm),
      LBU        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemByteU  ,  PathLSU   , IXX , IRS , IRT, ISImm),
      LH         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemHalf   ,  PathLSU   , IXX , IRS , IRT, ISImm),
      LHU        -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemHalfU  ,  PathLSU   , IXX , IRS , IRT, ISImm),
      LW         -> List(  PC4     ,  AReg   ,  BImm   ,  DReg   , MemWord   ,  PathLSU   , IXX , IRS , IRT, ISImm),
      SB         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemByte   ,  PathLSU   , IRT , IRS , IXX, ISImm),
      SH         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemHalf   ,  PathLSU   , IRT , IRS , IXX, ISImm),
      SW         -> List(  PC4     ,  AReg   ,  BImm   ,  DMem   , MemWord   ,  PathLSU   , IRT , IRS , IXX, ISImm),
           
      ERET       -> List(  PC4     ,  ACP0   ,  BXXX   ,  DXXX   , ALU_ERET  ,  PathALU   , CP0_EPC_INDEX , IXX , IXX, IImm),
      MFC0       -> List(  PC4     ,  ACP0   ,  BXXX   ,  DReg   , ALU_OR    ,  PathALU   , IRD , IXX , IRT, IImm),
      MTC0       -> List(  PC4     ,  AReg   ,  BXXX   ,  DCP0   , ALU_OR    ,  PathALU   , IRT , IXX , IRD, IImm),

      TLBP       -> List(  PC4     ,  ACP0   ,  BXXX   ,  DCP0   , TLBProbe  ,  PathLSU   , CP0_ENTRY_HI_INDEX , IXX , CP0_INDEX_INDEX, ISImm),
      TLBR       -> List(  PC4     ,  ACP0   ,  BXXX   ,  DCP0   , TLBRead   ,  PathLSU   , CP0_INDEX_INDEX    , IXX , CP0_ENTRY_HI_INDEX, ISImm),
      TLBWI      -> List(  PC4     ,  ACP0   ,  BXXX   ,  DMem   , TLBWrite  ,  PathLSU   , CP0_INDEX_INDEX    , IXX , IXX, ISImm),
      TLBWR      -> List(  PC4     ,  ACP0   ,  BXXX   ,  DMem   , TLBWrite  ,  PathLSU   , CP0_INDEX_INDEX    , IXX , IXX, ISImm),
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

  io.imm               := control_signal(9)
}

// Construct an NOP instruction
// To issue a bubble, or as debug input
object NOPBubble {
  def apply() = {
    val item = Wire(new Instruction)
    item.pc               := 0.U(LGC_ADDR_W.W)
    item.imm              := 0.U(XLEN.W)
    item.dec.next_pc      := 0.U(NEXT_PC_W.W)
    item.dec.param_a      := 0.U(PARAM_A_W.W)
    item.dec.param_b      := 0.U(PARAM_B_W.W)
    item.dec.write_target := 0.U(WRITE_TARGET_W.W)
    item.dec.fu_ctrl      := 0.U(FU_CTRL_W.W)
    item.dec.path         := 1.U(PATH_W.W)
    item.dec.rs1          := 0.U(REG_ID_W.W)
    item.dec.rs2          := 0.U(REG_ID_W.W)
    item.dec.rd           := 0.U(REG_ID_W.W)
    item
  }
}

// Construct a decoded Instruction with given functions
object InstDecodedLitByPath extends ALUConfig with BJUConfig {
  // Construction By path
  def apply(path_type: Int, rs1: Int, rs2: Int, rd: Int): Instruction = {
    val inst = new Instruction
    // ALU
    if (path_type == 1) {
      inst.Lit(
        _.pc               -> 0.U(LGC_ADDR_W.W),
        _.imm              -> 0.U(XLEN.W),
        _.dec.next_pc      -> PC4,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DReg,
        _.dec.fu_ctrl      -> ALU_ADD,
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
        _.imm              -> rd.S(XLEN.W).asUInt(),
        _.dec.next_pc      -> Branch,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.fu_ctrl      -> BrEQ,
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
        _.imm              -> 0.U(XLEN.W),
        _.dec.next_pc      -> Branch,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.fu_ctrl      -> ALU_ADD,
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
        _.imm              -> 0.U(XLEN.W),
        _.dec.next_pc      -> PC4,
        _.dec.param_a      -> AXXX,
        _.dec.param_b      -> BXXX,
        _.dec.write_target -> DXXX,
        _.dec.fu_ctrl      -> ALU_ADD,
        _.dec.path         -> PathXXX,
        _.dec.rs1          -> rs1.U(5.W),
        _.dec.rs2          -> rs2.U(5.W),
        _.dec.rd           -> rd.U(5.W)
      )
    }
  }
}