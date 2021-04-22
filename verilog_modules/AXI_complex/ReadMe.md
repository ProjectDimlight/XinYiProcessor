CPU AXI控制器
===========================

特性
---------------------------
指令数据双端口  
支持多个读指令并行  
自带RAW与WAR检测，读取命中待写地址自动Forwarding，写入命中读取地址则待冲突结束后写入   
（有一定限制的）参数化  
（仅三口版本）两个数据端口竞争读写，0口优先

文件说明
---------------------------
AXI_complex.v   上一个双口版本  
AXI_complex_triport.v   新的三口版本

参数说明
---------------------------
```verilog
    parameter   PORT_INST_WIDTH     =   64;     //指令端口位宽
    parameter   PORT_DATA_WIDTH     =   64;     //数据端口位宽
    parameter   AXI_R_BUS_WIDTH     =   64;     //AXI总线读位宽
    parameter   AXI_W_BUS_WIDTH     =   64;     //AXI总线写位宽
    parameter   AXI_R_ID_WIDTH      =   4;      //AXI总线读ID位宽
    parameter   AXI_W_ID_WIDTH      =   4;      //AXI总线写ID位宽
    parameter   AXI_RQUEUE_SIZE     =   4;      //AXI控制器读队列深度，不小于2，不大于读ID个数
```