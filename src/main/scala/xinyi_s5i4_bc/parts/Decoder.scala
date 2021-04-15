
package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._

import ControlConst._
import ISAPatterns._
import config.config._

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
  val inst_type_w   = InstXXX.getWidth

  val PC4           = 0.U(3.W)
  val PCReg         = 1.U(3.W)
  val Branch        = 2.U(3.W)
  val Jump          = 3.U(3.W)
  val Trap          = 4.U(3.W)
  val Ret           = 5.U(3.W)
  val next_pc_w     = PC4.getWidth

  val BrXXX         = 0.U(4.W)
  val BrEQ          = 1.U(4.W)
  val BrNE          = 2.U(4.W)
  val BrGE          = 3.U(4.W)
  val BrGT          = 4.U(4.W)
  val BrLE          = 5.U(4.W)
  val BrLT          = 6.U(4.W)
  val Except        = 9.U(4.W)
  val branch_type_w = BrXXX.getWidth

  val AXXX          = 0.U(3.W)
  val AReg          = 0.U(3.W)
  val ACP0          = 1.U(3.W)
  val AHi           = 2.U(3.W)
  val ALo           = 3.U(3.W)
  val AShamt        = 4.U(3.W)
  val param_a_w     = AXXX.getWidth

  val BXXX          = 0.U(1.W)
  val BReg          = 0.U(1.W)
  val BImm          = 1.U(1.W)
  val param_b_w     = BXXX.getWidth

  val DXXX          = 0.U(3.W)
  val DMem          = 1.U(3.W)
  val DReg          = 2.U(3.W)
  val DCP0          = 3.U(3.W)
  val DHi           = 4.U(3.W)
  val DLo           = 5.U(3.W)
  val write_target_w= DXXX.getWidth

  val MemXXX        = 0.U(3.W)
  val MemWord       = 1.U(3.W)
  val MemByte       = 2.U(3.W)
  val MemByteU      = 3.U(3.W)
  val MemHalf       = 4.U(3.W)
  val MemHalfU      = 5.U(3.W)
  val mem_width_w   = MemXXX.getWidth

  val PathXXX       = N_A_PATH_TYPE.U(2.W)
  val PathALU       = ALU_PATH_TYPE.U(2.W)
  val PathBJU       = BJU_PATH_TYPE.U(2.W)
  val PathLSU       = LSU_PATH_TYPE.U(2.W)
  
  val ALUXXX        = 0.U(4.W)
  val ALUADD        = 0.U(4.W)
  val ALUADDU       = 1.U(4.W)
  val ALUSUB        = 2.U(4.W)
  val ALUSLT        = 3.U(4.W)
  val ALUSLTU       = 4.U(4.W)
  val ALUAND        = 5.U(4.W)
  val ALULUI        = 6.U(4.W)
  val ALUNOR        = 7.U(4.W)
  val ALUOR         = 8.U(4.W)
  val ALUXOR        = 9.U(4.W)
  val ALUSLL        = 10.U(4.W)
  val ALUSRA        = 11.U(4.W)
  val ALUSRL        = 12.U(4.W)
  val ALUPC         = 15.U(4.W)
  val alu_op_w      = ALUXXX.getWidth

  val MDUDIV        = 0.U(5.W)
  val MDUDIVU       = 1.U(5.W)
  val MDUMUL        = 2.U(5.W)
  val MDUMULU       = 3.U(5.W)
}

class ControlSet extends Bundle {
  val inst_type     = UInt(inst_type_w.W)
  val next_pc       = UInt(next_pc_w.W)
  val branch_type   = UInt(branch_type_w.W)
  val param_a       = UInt(param_a_w.W)
  val param_b       = UInt(param_b_w.W)
  val write_target  = UInt(write_target_w.W)
  val alu_op        = UInt(alu_op_w.W)
  val mem_width     = UInt(mem_width_w.W)
  val path          = UInt(PATH_W.W)
  val rs1           = UInt(REG_ID_W.W)
  val rs2           = UInt(REG_ID_W.W)
  val rd            = UInt(REG_ID_W.W)
}

class MIPSDecoder extends Module {
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
                    List(Illegal ,  PC4     ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IXX),
    Array(         /*      Inst  |   PC     | Branch   |   A     |   B     |  D      | ALU      |  Mem    |  Path id   | rs1 | rs2 |  rd */
                   /*      Type  | Select   | Type     | use rs1 | use rs2 | write   | Type     | Type    |   Select   |     |     |     */
                   /*  Structure | NextPC   | Brch/Jmp | alusrcA | alusrcB | target  | alu OP   | B/H/W   | MultiIssue |     |     |     */
      NOP        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      ADD        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      ADDI       -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      ADDU       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUADDU  , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      ADDIU      -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADDU  , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      SUB        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSUB   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SUBU       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSUB   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SLT        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLT   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SLTI       -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUSLT   , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      SLTU       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLTU  , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SLTIU      -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUSLTU  , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      DIV        -> List(RMDType ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUDIV   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      DIVU       -> List(RMDType ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUDIVU  , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      MULT       -> List(RMDType ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUMUL   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      MULTU      -> List(RMDType ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUMULU  , MemXXX  ,  PathALU   , IRS , IRT , IRD),
           
      AND        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUAND   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      ANDI       -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUAND   , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      LUI        -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALULUI   , MemXXX  ,  PathALU   , IXX , IXX , IRT),
      NOR        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUNOR   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      OR         -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUOR    , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      ORI        -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUOR    , MemXXX  ,  PathALU   , IRS , IXX , IRT),
      XOR        -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUXOR   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      XORI       -> List(IType   ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUXOR   , MemXXX  ,  PathALU   , IRS , IXX , IRT),
           
      SLLV       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLL   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SLL        -> List(RSType  ,  PC4     ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSLL   , MemXXX  ,  PathALU   , IXX , IRT , IRD),
      SRAV       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSRA   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SRA        -> List(RSType  ,  PC4     ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSRA   , MemXXX  ,  PathALU   , IXX , IXX , IRD),
      SRLV       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSRL   , MemXXX  ,  PathALU   , IRS , IRT , IRD),
      SRL        -> List(RSType  ,  PC4     ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSRL   , MemXXX  ,  PathALU   , IXX , IXX , IRD),
           
      BEQ        -> List(IBType  ,  Branch  ,  BrEQ    ,  AReg   ,  BReg   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BNE        -> List(IBType  ,  Branch  ,  BrNE    ,  AReg   ,  BReg   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BGEZ       -> List(IBType  ,  Branch  ,  BrGE    ,  AReg   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BGTZ       -> List(IBType  ,  Branch  ,  BrGT    ,  AReg   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BLEZ       -> List(IBType  ,  Branch  ,  BrLE    ,  AReg   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BLTZ       -> List(IBType  ,  Branch  ,  BrLT    ,  AReg   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IRS , IRT , IXX),
      BGEZAL     -> List(IBType  ,  Branch  ,  BrGE    ,  AReg   ,  BXXX   ,  DReg   , ALUPC    , MemXXX  ,  PathALU   , IRS , IRT , IRA),
      BLTZAL     -> List(IBType  ,  Branch  ,  BrLT    ,  AReg   ,  BXXX   ,  DReg   , ALUPC    , MemXXX  ,  PathALU   , IRS , IRT , IRA),
           
      J          -> List(JType   ,  Jump    ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IXX),
      JAL        -> List(JType   ,  Jump    ,  BrXXX   ,  AXXX   ,  BXXX   ,  DReg   , ALUPC    , MemXXX  ,  PathALU   , IXX , IXX , IRA),
      JR         -> List(JRType  ,  PCReg   ,  BrXXX   ,  AReg   ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IRS , IXX , IRD),
      JALR       -> List(JRType  ,  PCReg   ,  BrXXX   ,  AReg   ,  BXXX   ,  DReg   , ALUPC    , MemXXX  ,  PathALU   , IRS , IXX , IRA),
           
      MFHI       -> List(RType   ,  PC4     ,  BrXXX   ,  AHi    ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IRD),
      MFLO       -> List(RType   ,  PC4     ,  BrXXX   ,  ALo    ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IRD),
      MTHI       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BXXX   ,  DHi    , ALUADD   , MemXXX  ,  PathALU   , IRS , IXX , IXX),
      MTLO       -> List(RType   ,  PC4     ,  BrXXX   ,  AReg   ,  BXXX   ,  DLo    , ALUADD   , MemXXX  ,  PathALU   , IRS , IXX , IXX),
           
      BREAK      -> List(RTType  ,  PC4     ,  BrXXX   ,  AXXX   ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IXX),
      SYSCALL    -> List(RTType  ,  PC4     ,  BrXXX   ,  AXXX   ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IXX),
           
      LB         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemByte ,  PathLSU   , IRS , IXX , IRT),
      LBU        -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemByteU,  PathLSU   , IRS , IXX , IRT),
      LH         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemHalf ,  PathLSU   , IRS , IXX , IRT),
      LHU        -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemHalfU,  PathLSU   , IRS , IXX , IRT),
      LW         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD   , MemWord ,  PathLSU   , IRS , IXX , IRT),
      SB         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD   , MemByte ,  PathLSU   , IRS , IRT , IXX),
      SH         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD   , MemHalf ,  PathLSU   , IRS , IRT , IXX),
      SW         -> List(IMType  ,  PC4     ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD   , MemWord ,  PathLSU   , IRS , IRT , IXX),
           
      ERET       -> List(SType   ,  PC4     ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUADD   , MemXXX  ,  PathALU   , IXX , IXX , IXX),
      MFC0       -> List(SType   ,  PC4     ,  BrXXX   ,  ACP0   ,  BXXX   ,  DReg   , ALUADD   , MemXXX  ,  PathALU   , IRD , IXX , IRT),
      MTC0       -> List(SType   ,  PC4     ,  BrXXX   ,  AReg   ,  BXXX   ,  DCP0   , ALUADD   , MemXXX  ,  PathALU   , IRT , IXX , IRD)
  ))

  io.dec.inst_type     := control_signal(0)
  io.dec.next_pc       := control_signal(1)
  io.dec.branch_type   := control_signal(2)
  io.dec.param_a       := control_signal(3)
  io.dec.param_b       := control_signal(4)
  io.dec.write_target  := control_signal(5)
  io.dec.alu_op        := control_signal(6)
  io.dec.mem_width     := control_signal(7)
  io.dec.path          := control_signal(8)
  io.dec.rs1           := control_signal(9)
  io.dec.rs2           := control_signal(10)
  io.dec.rd            := control_signal(11)
}

// Construct an NOP instruction
// To issue a bubble, or as debug input
object NOPBubble {
  def apply() = {
    val item = Wire(new Instruction)
    item.pc               := 0.U(LGC_ADDR_W.W)
    item.inst             := 0.U(DATA_W.W)
    item.dec.inst_type    := 0.U(inst_type_w.W)
    item.dec.next_pc      := 0.U(next_pc_w.W)
    item.dec.branch_type  := 0.U(branch_type_w.W)
    item.dec.param_a      := 0.U(param_a_w.W)
    item.dec.param_b      := 0.U(param_b_w.W)
    item.dec.write_target := 0.U(write_target_w.W)
    item.dec.alu_op       := 0.U(alu_op_w.W)
    item.dec.mem_width    := 0.U(mem_width_w.W)
    item.dec.path         := 0.U(PATH_W.W)
    item.dec.rs1          := 0.U(REG_ID_W.W)
    item.dec.rs2          := 0.U(REG_ID_W.W)
    item.dec.rd           := 0.U(REG_ID_W.W)
    item
  }
}

// Construct a decoded Instruction with given functions
object InstDecodedLitByPath {
  // Construction By path
  def apply(path_type: Int, rs1: Int, rs2: Int, rd: Int): Instruction = {
    val inst = new Instruction
    // ALU
    if (path_type == 1) {
      inst.Lit(
        _.pc               -> 0.U(LGC_ADDR_W.W),
        _.inst             -> 0.U(DATA_W.W),
        _.dec.inst_type    -> RType,
        _.dec.next_pc      -> PC4,
        _.dec.branch_type  -> BrXXX,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DReg,
        _.dec.alu_op       -> ALUADD,
        _.dec.mem_width    -> MemXXX,
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
        _.dec.inst_type    -> RType,
        _.dec.next_pc      -> Branch,
        _.dec.branch_type  -> BrEQ,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> ALUADD,
        _.dec.mem_width    -> MemXXX,
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
        _.dec.inst_type    -> IBType,
        _.dec.next_pc      -> Branch,
        _.dec.branch_type  -> BrEQ,
        _.dec.param_a      -> AReg,
        _.dec.param_b      -> BReg,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> ALUADD,
        _.dec.mem_width    -> MemXXX,
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
        _.dec.inst_type    -> InstXXX,
        _.dec.next_pc      -> PC4,
        _.dec.branch_type  -> BrXXX,
        _.dec.param_a      -> AXXX,
        _.dec.param_b      -> BXXX,
        _.dec.write_target -> DXXX,
        _.dec.alu_op       -> ALUXXX,
        _.dec.mem_width    -> MemXXX,
        _.dec.path         -> PathXXX,
        _.dec.rs1          -> rs1.U(5.W),
        _.dec.rs2          -> rs2.U(5.W),
        _.dec.rd           -> rd.U(5.W)
      )
    }
  }
}