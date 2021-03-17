# XinYiProcessor

## 简介

芯仪CPU是我们对RISC进行的一系列尝试。  
将会支持MIPS和RISC-V指令集。  

名称起源于我的一位好朋友（星xin翼yi）。

## 方案

### 流水段方案

- 5段
    - F，D，I，F，W
    - 基础版
    - 然而只有一段Func可能不太行
- 6段
    - F，D，I，F，F，W
    - 两段Func，以此为蓝本
- 2发射，4线，2写回
    - ALU
    - ALU
    - MDU
    - LSU
    - 没有BranchUnit，在处理Branch

大概不会再做更长的流水级了。

### 分支预测方案

- BTB + BHT
    - 经典
- BC
    - 有挑战性的方案
    - 优点是即便预测错了也不需要等
    - 缺点是需要的Cache容积比较大
