package xinyi_s5i4_bc.parts

import chisel3.util._
import utils._
import config.config._

object ISAPatterns {

  def ADD           = BitPat("b000000???????????????00000100000")
  def ADDI          = BitPat("b001000??????????????????????????")
  def ADDU          = BitPat("b000000???????????????00000100001")
  def ADDIU         = BitPat("b001001??????????????????????????")
  def SUB           = BitPat("b000000???????????????00000100010")
  def SUBU          = BitPat("b000000???????????????00000100011")
  def SLT           = BitPat("b000000???????????????00000101010")
  def SLTI          = BitPat("b001010??????????????????????????")
  def SLTU          = BitPat("b000000???????????????00000101011")
  def SLTIU         = BitPat("b001011??????????????????????????")
  def DIV           = BitPat("b000000??????????0000000000011010")
  def DIVU          = BitPat("b000000??????????0000000000011011")
  def MULT          = BitPat("b000000??????????0000000000011000")
  def MULTU         = BitPat("b000000??????????0000000000011001")
  
  def AND           = BitPat("b000000???????????????00000100100")
  def ANDI          = BitPat("b001100??????????????????????????")
  def LUI           = BitPat("b00111100000?????????????????????")
  def NOR           = BitPat("b000000???????????????00000100111")
  def OR            = BitPat("b000000???????????????00000100101")
  def ORI           = BitPat("b001101??????????????????????????")
  def XOR           = BitPat("b000000???????????????00000100110")
  def XORI          = BitPat("b001110??????????????????????????")
  
  def SLLV          = BitPat("b000000???????????????00000000100")
  def SLL           = BitPat("b00000000000???????????????000000")
  def SRAV          = BitPat("b000000???????????????00000000111")
  def SRA           = BitPat("b00000000000???????????????000011")
  def SRLV          = BitPat("b000000???????????????00000000110")
  def SRL           = BitPat("b00000000000???????????????000010")

  def BEQ           = BitPat("b000100??????????????????????????")
  def BNE           = BitPat("b000101??????????????????????????")
  def BGEZ          = BitPat("b000001?????00001????????????????")
  def BGTZ          = BitPat("b000111?????00000????????????????")
  def BLEZ          = BitPat("b000110?????00000????????????????")
  def BLTZ          = BitPat("b000001?????00000????????????????")
  def BGEZAL        = BitPat("b000001?????10001????????????????")
  def BLTZAL        = BitPat("b000001?????10000????????????????")
  
  def J             = BitPat("b000010??????????????????????????")
  def JAL           = BitPat("b000011??????????????????????????")
  def JR            = BitPat("b000000?????000000000000000001000")
  def JALR          = BitPat("b000000?????00000?????00000001001")
  
  def MFHI          = BitPat("b0000000000000000?????00000010000")
  def MFLO          = BitPat("b0000000000000000?????00000010010")
  def MTHI          = BitPat("b000000?????000000000000000010001")
  def MTLO          = BitPat("b000000?????000000000000000010011")
  def BREAK         = BitPat("b000000????????????????????001101")
  def SYSCALL       = BitPat("b000000????????????????????001100")
  
  def LB            = BitPat("b100000??????????????????????????")
  def LBU           = BitPat("b100100??????????????????????????")
  def LH            = BitPat("b100001??????????????????????????")
  def LHU           = BitPat("b100101??????????????????????????")
  def LW            = BitPat("b100011??????????????????????????")
  def SB            = BitPat("b101000??????????????????????????")
  def SH            = BitPat("b101001??????????????????????????")
  def SW            = BitPat("b101011??????????????????????????")
  
  def ERET          = BitPat("b01000010000000000000000000011000")
  def MFC0          = BitPat("b01000000000??????????00000000???")
  def MTC0          = BitPat("b01000000100??????????00000000???")

  def TLBP          = BitPat("b01000010000000000000000000001000")
  def TLBR          = BitPat("b01000010000000000000000000000001")
  def TLBWI         = BitPat("b01000010000000000000000000000010")
  def TLBWR         = BitPat("b01000010000000000000000000000110")
  
  def NOP           = BitPat("b00000000000000000000000000000000")
}
