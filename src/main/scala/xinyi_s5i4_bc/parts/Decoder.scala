package xinyi_s5i4_bc.parts

import chisel3._
import chisel3.util._
import wrap._

import ControlConst._
import ISAPatterns._

class Instruction extends Bundle with XinYiConfig {
  val pc   = UInt(lgc_addr_w.W)
  val inst = UInt(data_w.W)
  val dec  = new ControlSet
}

object ControlConst {

  val True  = true.B
  val False = false.B

  val InstXXX       = 0.U(4.W)
  val RType         = 1.U(4.W)
  val RSType        = 2.U(4.W)
  val RTType        = 3.U(4.W)
  val IType         = 4.U(4.W)
  val IBType        = 5.U(4.W)
  val IMType        = 6.U(4.W)
  val JType         = 7.U(4.W)
  val JRType        = 8.U(4.W)
  val SType         = 9.U(4.W)
  val Illegal       = 15.U(4.W)
  val inst_type_w   = InstXXX.getWidth

  val PC4           = 0.U(3.W)
  val PCReg         = 1.U(3.W)
  val Branch        = 2.U(3.W)
  val Jump          = 3.U(3.W)
  val Trap          = 4.U(3.W)
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

  val WBXXX         = 0.U(2.W)
  val WBReg         = 0.U(2.W)
  val WBALU         = 1.U(2.W)
  val WBMEM         = 2.U(2.W)
  val WBPC          = 3.U(2.W)
  val wb_from_w     = WBXXX.getWidth
  
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
  val alu_op_w      = ALUXXX.getWidth

  val MDUDIV        = 0.U(5.W)
  val MDUDIVU       = 1.U(5.W)
  val MDUMUL        = 2.U(5.W)
  val MDUMULU       = 3.U(5.W)

  val reg_id_w      = 5
}

class ControlSet extends Bundle with XinYiConfig {
  val inst_type     = UInt(inst_type_w.W)
  val next_pc       = UInt(next_pc_w.W)
  val mdu           = Bool()
  val branch_type   = UInt(branch_type_w.W)
  val param_a       = UInt(param_a_w.W)
  val param_b       = UInt(param_b_w.W)
  val write_target  = UInt(write_target_w.W)
  val alu_op        = UInt(alu_op_w.W)
  val mem_width     = UInt(mem_width_w.W)
  val wb_from       = UInt(wb_from_w.W)
  val rs1           = UInt(reg_id_w.W)
  val rs2           = UInt(reg_id_w.W)
  val rd            = UInt(reg_id_w.W)
}

class MIPSDecoder extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val inst = Input(UInt(data_w.W))
    val ctrl = Output(new ControlSet)
  })

  val SHAMT = io.inst(10,  6)
  val IMM   = io.inst(15,  0)
  val IRS   = io.inst(25, 21)
  val IRT   = io.inst(20, 16)
  val IRD   = io.inst(15, 11)
  val IRA   = 31.U(reg_id_w.W)
  val IXX   = 0.U(reg_id_w.W)

  // Decode
  val control_signal = ListLookup(io.inst,
                    List(Illegal ,  PC4     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IRD),
    Array(         /*      Inst  |   PC     | use      | Branch   |   A     |   B     |  D      | ALU     |  Mem    |     wb     | rs1 | rs2 |  rd */
                   /*      Type  | Select   | mult     | Type     | use rs1 | use rs2 | write   | Type    | Type    |   Select   |     |     |     */
                   /*  Structure | NextPC   | Mult/Div | Brch/Jmp | ALUsrcA | ALUsrcB | target  | ALU OP  | B/H/W   | MultiIssue |     |     |     */
      NOP        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      ADD        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUADD  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      ADDI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      ADDU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUADDU , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      ADDIU      -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADDU , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      SUB        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSUB  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SUBU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSUB  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SLT        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLT  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SLTI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUSLT  , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      SLTU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLTU , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SLTIU      -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUSLTU , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      DIV        -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUDIV  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      DIVU       -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUDIVU , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      MULT       -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUMUL  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      MULTU      -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , MDUMULU , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      
      AND        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUAND  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      ANDI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUAND  , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      LUI        -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALULUI  , MemXXX  ,  WBALU     , IXX , IXX , IRT),
      NOR        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUNOR  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      OR         -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUOR   , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      ORI        -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUOR   , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      XOR        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUXOR  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      XORI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUXOR  , MemXXX  ,  WBALU     , IRS , IXX , IRT),
      
      SLLV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSLL  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SLL        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSLL  , MemXXX  ,  WBALU     , IXX , IRT , IRD),
      SRAV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSRA  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SRA        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSRA  , MemXXX  ,  WBALU     , IXX , IXX , IRD),
      SRLV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   ,  DReg   , ALUSRL  , MemXXX  ,  WBALU     , IRS , IRT , IRD),
      SRL        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   ,  DReg   , ALUSRL  , MemXXX  ,  WBALU     , IXX , IXX , IRD),
      
      BEQ        -> List(IBType  ,  Branch  ,  False   ,  BrEQ    ,  AReg   ,  BReg   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BNE        -> List(IBType  ,  Branch  ,  False   ,  BrNE    ,  AReg   ,  BReg   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BGEZ       -> List(IBType  ,  Branch  ,  False   ,  BrGE    ,  AReg   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BGTZ       -> List(IBType  ,  Branch  ,  False   ,  BrGT    ,  AReg   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BLEZ       -> List(IBType  ,  Branch  ,  False   ,  BrLE    ,  AReg   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BLTZ       -> List(IBType  ,  Branch  ,  False   ,  BrLT    ,  AReg   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IRT , IXX),
      BGEZAL     -> List(IBType  ,  Branch  ,  False   ,  BrGE    ,  AReg   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBALU     , IRS , IRT , IRA),
      BLTZAL     -> List(IBType  ,  Branch  ,  False   ,  BrLT    ,  AReg   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBALU     , IRS , IRT , IRA),
      
      J          -> List(JType   ,  Jump    ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IXX , IXX , IXX),
      JAL        -> List(JType   ,  Jump    ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBPC      , IXX , IXX , IRA),
      JR         -> List(JRType  ,  PCReg   ,  False   ,  BrXXX   ,  AReg   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBXXX     , IRS , IXX , IRD),
      JALR       -> List(JRType  ,  PCReg   ,  False   ,  BrXXX   ,  AReg   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBPC      , IRS , IXX , IRA),
      
      MFHI       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AHi    ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBALU     , IXX , IXX , IRD),
      MFLO       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  ALo    ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBALU     , IXX , IXX , IRD),
      MTHI       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   ,  DHi    , ALUXXX  , MemXXX  ,  WBALU     , IRS , IXX , IXX),
      MTLO       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   ,  DLo    , ALUXXX  , MemXXX  ,  WBALU     , IRS , IXX , IXX),
      
      BREAK      -> List(RTType  ,  PC4     ,  False   ,  Except  ,  AXXX   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBXXX     , IXX , IXX , IXX),
      SYSCALL    -> List(RTType  ,  PC4     ,  False   ,  Except  ,  AXXX   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBXXX     , IXX , IXX , IXX),
      
      LB         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemByte ,  WBMEM     , IRS , IXX , IRT),
      LBU        -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemByteU,  WBMEM     , IRS , IXX , IRT),
      LH         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemHalf ,  WBMEM     , IRS , IXX , IRT),
      LHU        -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemHalfU,  WBMEM     , IRS , IXX , IRT),
      LW         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DReg   , ALUADD  , MemWord ,  WBMEM     , IRS , IXX , IRT),
      SB         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD  , MemByte ,  WBXXX     , IRS , IRT , IXX),
      SH         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD  , MemHalf ,  WBXXX     , IRS , IRT , IXX),
      SW         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   ,  DMem   , ALUADD  , MemWord ,  WBXXX     , IRS , IRT , IXX),
      
      ERET       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   ,  DXXX   , ALUXXX  , MemXXX  ,  WBXXX     , IXX , IXX , IXX),
      MFC0       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  ACP0   ,  BXXX   ,  DReg   , ALUXXX  , MemXXX  ,  WBReg     , IRT , IXX , IRT),
      MTC0       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   ,  DCP0   , ALUXXX  , MemXXX  ,  WBReg     , IXX , IRT , IRD)
  ))

  io.ctrl.inst_type     := control_signal(0)
  io.ctrl.next_pc       := control_signal(1)
  io.ctrl.mdu           := control_signal(2)
  io.ctrl.branch_type   := control_signal(3)
  io.ctrl.param_a       := control_signal(4)
  io.ctrl.param_b       := control_signal(5)
  io.ctrl.write_target  := control_signal(6)
  io.ctrl.alu_op        := control_signal(7)
  io.ctrl.mem_width     := control_signal(8)
  io.ctrl.wb_from       := control_signal(9)
  io.ctrl.rs1           := control_signal(10)
  io.ctrl.rs2           := control_signal(11)
  io.ctrl.rd            := control_signal(12)
}

object NOPBubble {
  def apply() = {
    val item = Wire(new Instruction)
    item.pc   := 0.U(32.W)
    item.inst := 0.U(32.W)
    item.dec.inst_type     := 0.U(4.W)
    item.dec.next_pc       := 0.U(3.W)
    item.dec.mdu           := 0.U(1.W)
    item.dec.branch_type   := 0.U(4.W)
    item.dec.param_a       := 0.U(3.W)
    item.dec.param_b       := 0.U(1.W)
    item.dec.write_target  := 0.U(3.W)
    item.dec.alu_op        := 0.U(4.W)
    item.dec.mem_width     := 0.U(3.W)
    item.dec.wb_from       := 0.U(2.W)
    item.dec.rs1           := 0.U(5.W)
    item.dec.rs2           := 0.U(5.W)
    item.dec.rd            := 0.U(5.W)
    item
  }
}