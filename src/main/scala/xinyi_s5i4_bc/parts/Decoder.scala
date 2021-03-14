package xinyi_s5i4_bc.parts

import chisel3._
import wrap._

class ControlSet extends Bundle with XinYiConfig {
  val op = UInt(opw.W)

  val rs    = UInt(regnw.W)
  val rt    = UInt(regnw.W)
  val rd    = UInt(regnw.W)
  val sa    = UInt(shiftw.W)
  val func  = UInt(funcw.W)
  val imm   = UInt(immw.w)

  val is_arith  = Bool()
  val is_load   = Bool()
  val is_store  = Bool()
  val is_jump   = Bool()
  val is_branch = Bool()
  val is_sp     = Bool()
  val is_trap   = Bool()
  val wal       = Bool()
}

class MIPSDecoder extends Module with XinYiConfig {
  val io = IO(new Bundle{
    val inst = Input(UInt(instw.W))
    val ctrl = Output(new ControlSet)
  })

  val OP    = io.inst(31, 26)
  val IRS   = io.inst(25, 21)
  val IRT   = io.inst(20, 16)
  val IRD   = io.inst(15, 11)
  val SHAMT  = io.inst(10,  6)
  val FUNC  = io.inst( 5,  0)
  val IMM   = io.inst(15,  0)

  val 

  // Decode
  val controlSignal = ListLookup(io.inst,
                    List(Illegal ,  PC4     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IRD),
    Array(         /*      Inst  |   PC     | use      | Branch   |   A     |   B     | ALU     |  Mem    |     wb     |  wb      | rs1 | rs2 |  rd */
                   /*      Type  | Select   | mult     | Type     | use rs1 | use rs2 | Type    | Type    |   Select   | Enable   |     |     |     */
                   /*  Structure | NextPC   | Mult/Div | Brch/Jmp | ALUsrcA | ALUsrcB | ALU OP  | B/H/W   | MultiIssue | Enable   |     |     |     */
      NOP        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      ADD        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUADD  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      ADDI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      ADDU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUADD  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      ADDIU      -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      SUB        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSUB  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SUBU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSUB  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SLT        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSLT  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SLTI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUSLT  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      SLTU       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSLTU , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SLTIU      -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUSLTU , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      DIV        -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   , ALUDIV  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      DIVU       -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   , ALUDIVU , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      MULT       -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   , ALUMUL  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      MULTU      -> List(RType   ,  PC4     ,  True    ,  BrXXX   ,  AReg   ,  BReg   , ALUMULU , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      AND        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUAND  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      ANDI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUAND  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      LUI        -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALULUI  , MemXXX  ,  WBALU     , WEnReg   , IXX , IXX , IRT),
      NOR        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUNOR  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      OR         -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUOR   , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      ORI        -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUOR   , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      XOR        -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUXOR  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      XORI       -> List(IType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUXOR  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IRT),
      SLLV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSLL  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SLL        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   , ALUSLL  , MemXXX  ,  WBALU     , WEnReg   , IXX , IRT , IRD),
      SRAV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSRA  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SRA        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   , ALUSRA  , MemXXX  ,  WBALU     , WEnReg   , IXX , IXX , IRD),
      SRLV       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BReg   , ALUSRL  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRD),
      SRL        -> List(RSType  ,  PC4     ,  False   ,  BrXXX   ,  AShamt ,  BReg   , ALUSRL  , MemXXX  ,  WBALU     , WEnReg   , IXX , IXX , IRD),
      BEQ        -> List(IBType  ,  Branch  ,  False   ,  BEQ     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BNE        -> List(IBType  ,  Branch  ,  False   ,  BNE     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BGEZ       -> List(IBType  ,  Branch  ,  False   ,  BGE     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BGTZ       -> List(IBType  ,  Branch  ,  False   ,  BGT     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BLEZ       -> List(IBType  ,  Branch  ,  False   ,  BLE     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BLTZ       -> List(IBType  ,  Branch  ,  False   ,  BLT     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IRT , IXX),
      BGEZAL     -> List(IBType  ,  Branch  ,  False   ,  BGE     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRA),
      BLTZAL     -> List(IBType  ,  Branch  ,  False   ,  BLT     ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , IRS , IRT , IRA),
      J          -> List(JType   ,  Jump    ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IXX , IXX , IXX),
      JAL        -> List(JType   ,  Jump    ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBPC      , WEnReg   , IXX , IXX , IRA),
      JR         -> List(JRType  ,  Reg     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IRS , IXX , IRD),
      JALR       -> List(JRType  ,  Reg     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBPC      , WEnReg   , IRS , IXX , IRA),
      MFHI       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , IHI , IXX , IRD),
      MFLO       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , ILO , IXX , IRD),
      MTHI       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , IHI),
      MTLO       -> List(RType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   , ALUXXX  , MemXXX  ,  WBALU     , WEnReg   , IRS , IXX , ILO),
      BREAK      -> List(RTType  ,  PC4     ,  False   ,  Except  ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IXX , IXX , IXX),
      SYSCALL    -> List(RTType  ,  PC4     ,  False   ,  Except  ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IXX , IXX , IXX),
      LB         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemByte ,  WBMEM     , WEnReg   , IRS , IXX , IRT),
      LBU        -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemByteU,  WBMEM     , WEnReg   , IRS , IXX , IRT),
      LH         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemHalf ,  WBMEM     , WEnReg   , IRS , IXX , IRT),
      LHU        -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemHalfU,  WBMEM     , WEnReg   , IRS , IXX , IRT),
      LW         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemWord ,  WBMEM     , WEnReg   , IRS , IXX , IRT),
      SB         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemByte ,  WBXXX     , WEnMem   , IRS , IRT , IXX),
      SH         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemHalf ,  WBXXX     , WEnMem   , IRS , IRT , IXX),
      SW         -> List(IMType  ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BImm   , ALUADD  , MemWord ,  WBXXX     , WEnMem   , IRS , IRT , IXX),
      ERET       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  AXXX   ,  BXXX   , ALUXXX  , MemXXX  ,  WBXXX     , WEnXXX   , IXX , IXX , IXX),
      MFC0       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  AC0    ,  BXXX   , ALUXXX  , MemXXX  ,  WBReg     , WEnReg   , IC0 , IXX , IRT),
      MTC0       -> List(SType   ,  PC4     ,  False   ,  BrXXX   ,  AReg   ,  BXXX   , ALUXXX  , MemXXX  ,  WBReg     , WEnReg   , IXX , IRT , IC0),
  ))

  io.ctrl
}
