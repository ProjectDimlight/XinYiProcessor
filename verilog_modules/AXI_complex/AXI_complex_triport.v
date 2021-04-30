`timescale 1ns / 1ps
//////////////////////////////////////////////////////////////////////////////////
// Company: Athlon System
// Engineer: Athlon
// 
// Create Date: 2021/03/27
// Design Name: 
// Module Name: AXI_complex
// Project Name: 
// Target Devices: 
// Tool Versions: 
// Description: 
// 3 Port variant of AXI controller
// Dependencies: 
// 
// Revision: 1.00
// Revision 0.01 - File Created
// Revision 1.00 - Simple function passed
// Revision 2.00 - Enable multiple pending commands
// Additional Comments:
// Enable extra efficiency
//////////////////////////////////////////////////////////////////////////////////


module AXI_complex(
    input                                   clk,
    input                                   rst, 

    //i
    input   [ 31:0]                         i_addr_in   ,
    input                                   i_en        ,
    output  [ 31:0]                         i_addr_out  ,
    output  [PORT_INST_WIDTH-1:0]           i_data      ,
    output                                  i_stall     ,
    output                                  i_valid     ,
    //d_0
    input   [ 31:0]                         d_0_addr_in ,
    input   [PORT_DATA_WIDTH-1:0]           d_0_data_in ,
    input                                   d_0_wr      ,
    input                                   d_0_rd      ,
    input   [  2:0]                         d_0_size    ,
    output  [ 31:0]                         d_0_addr_out,
    output  [PORT_DATA_WIDTH-1:0]           d_0_data_out,
    output                                  d_0_stall   ,
    output                                  d_0_valid   ,
    //d_1
    input   [ 31:0]                         d_1_addr_in ,
    input   [PORT_DATA_WIDTH-1:0]           d_1_data_in ,
    input                                   d_1_wr      ,
    input                                   d_1_rd      ,
    input   [  2:0]                         d_1_size    ,
    output  [ 31:0]                         d_1_addr_out,
    output  [PORT_DATA_WIDTH-1:0]           d_1_data_out,
    output                                  d_1_stall   ,
    output                                  d_1_valid   ,
    //axi
    //ar
    output  [AXI_R_ID_WIDTH-1:0]            arid         ,
    output  [31:0]                          araddr       ,
    output  [7 :0]                          arlen        ,
    output  [2 :0]                          arsize       ,
    output  [1 :0]                          arburst      ,
    output  [1 :0]                          arlock       ,
    output  [3 :0]                          arcache      ,
    output  [2 :0]                          arprot       ,
    output                                  arvalid      ,
    input                                   arready      ,
    //r           
    input  [AXI_R_ID_WIDTH-1:0]             rid          ,
    input  [AXI_R_BUS_WIDTH-1:0]            rdata        ,
    input  [1 :0]                           rresp        ,
    input                                   rlast        ,
    input                                   rvalid       ,
    output                                  rready       ,
    //aw          
    output [AXI_W_ID_WIDTH-1:0]             awid         ,
    output [31:0]                           awaddr       ,
    output [7 :0]                           awlen        ,
    output [2 :0]                           awsize       ,
    output [1 :0]                           awburst      ,
    output [1 :0]                           awlock       ,
    output [3 :0]                           awcache      ,
    output [2 :0]                           awprot       ,
    output                                  awvalid      ,
    input                                   awready      ,
    //w          
    output [AXI_W_ID_WIDTH-1:0]             wid          ,
    output [AXI_W_BUS_WIDTH-1:0]            wdata        ,
    output [(AXI_W_BUS_WIDTH / 8)-1:0]      wstrb        ,
    output                                  wlast        ,
    output                                  wvalid       ,
    input                                   wready       ,
    //b           
    input  [AXI_W_ID_WIDTH-1:0]             bid          ,
    input  [1 :0]                           bresp        ,
    input                                   bvalid       ,
    output                                  bready   

    );

    //Read queue size MUST greater than 2
    parameter   PORT_INST_WIDTH     =   64;
    parameter   PORT_DATA_WIDTH     =   64;
    parameter   PORT_INST_SIZE      =   $clog2(PORT_INST_WIDTH);
    parameter   AXI_R_BUS_WIDTH     =   32;
    parameter   AXI_W_BUS_WIDTH     =   32;
    parameter   AXI_R_ID_WIDTH      =   4;
    parameter   AXI_W_ID_WIDTH      =   4;
    parameter   AXI_RQUEUE_SIZE     =   4;
    parameter   PORT_MAX_WIDTH      =   PORT_INST_WIDTH > PORT_DATA_WIDTH ? PORT_INST_WIDTH : PORT_DATA_WIDTH;
    parameter   AXI_R_BUS_SIZE      =   $clog2(AXI_R_BUS_WIDTH / 8);
    parameter   AXI_W_BUS_SIZE      =   $clog2(AXI_W_BUS_WIDTH / 8);
    parameter   AXI_W_BUS_SIZE_R    =   $clog2(PORT_DATA_WIDTH / AXI_W_BUS_WIDTH);
    parameter   AXI_RQUEUE_SIZE_B   =   $clog2(AXI_RQUEUE_SIZE);

    reg     [  1:0]                                         laststs   ;
    wire    [  1:0]                                         nextsts   ;
    wire    [ 31:0]                                         d_addr_in ;
    wire    [PORT_DATA_WIDTH-1:0]                           d_data_in ;
    wire                                                    d_wr      ;
    wire                                                    d_rd      ;
    wire    [  2:0]                                         d_size    ;
    wire                                                    d_src     ;
    wire                                                    d_dst     ;
    wire    [ 31:0]                                         d_addr_out;
    wire    [PORT_DATA_WIDTH-1:0]                           d_data_out;
    wire                                                    d_stall   ;
    wire                                                    d_valid   ;
    
    //Arbiter Logic
    assign  nextsts     =   (laststs == 2'b11)  ?   2'b10               :
                            {(d_1_wr || d_1_rd), (d_0_wr || d_0_rd)}    ;

    always @(posedge clk) begin
        if (~rst) begin
            //if (~d_stall) begin
            if ((laststs == 2'b0) | d_valid | bvalid) begin
                laststs     <=  nextsts;
            end
        end
        else begin
            laststs     <=  2'b00;
        end
    end

    assign  d_addr_in       =   (laststs[1] && ~laststs[0])     ?   d_1_addr_in    : d_0_addr_in;
    assign  d_data_in       =   (laststs[1] && ~laststs[0])     ?   d_1_data_in    : d_0_data_in;
    assign  d_wr            =   (laststs[1] && ~laststs[0])     ?   d_1_wr         : d_0_wr;
    assign  d_rd            =   (laststs[1] && ~laststs[0])     ?   d_1_rd         : d_0_rd;
    assign  d_size          =   (laststs[1] && ~laststs[0])     ?   d_1_size       : d_0_size;
    assign  d_src           =   (laststs[1] && ~laststs[0])     ?   1'b1           : 1'b0;

    assign  d_0_addr_out    =   d_addr_out;
    assign  d_0_data_out    =   d_data_out;
    assign  d_0_stall       =   (laststs[0] && d_stall) || (laststs[1] && ~laststs[0]);
    assign  d_0_valid       =   (~d_dst && d_valid) | (~d_src & bvalid);

    assign  d_1_addr_out    =   d_addr_out;
    assign  d_1_data_out    =   d_data_out;
    assign  d_1_stall       =   (laststs[1] && ~laststs[0] && d_stall) || ~(laststs[1] && ~laststs[0]);
    assign  d_1_valid       =   ( d_dst && d_valid) | ( d_src & bvalid);

    //Known-working Part
    reg     [ 31:0]                                         axi_rd_reg_addr     [AXI_RQUEUE_SIZE-1:0];
    reg     [PORT_MAX_WIDTH-1:0]                            axi_rd_reg_data_out [AXI_RQUEUE_SIZE-1:0];
    reg     [  2:0]                                         axi_rd_reg_size     [AXI_RQUEUE_SIZE-1:0];
    reg                                                     axi_rd_reg_valid    [AXI_RQUEUE_SIZE-1:0];
    reg                                                     axi_rd_reg_done     [AXI_RQUEUE_SIZE-1:0];
    reg     [  1:0]                                         axi_rd_reg_type     [AXI_RQUEUE_SIZE-1:0];

    reg     [ 31:0]                                         axi_wr_reg_addr     ;
    reg     [PORT_DATA_WIDTH-1:0]                           axi_wr_reg_data_in  ;
    reg     [  2:0]                                         axi_wr_reg_size     ;
    reg     [AXI_RQUEUE_SIZE_B-1:0]                         axi_wr_reg_idlim    ;
    reg                                                     axi_wr_reg_idlimen  ;
    reg                                                     axi_wr_reg_valid    ;

    reg     [AXI_RQUEUE_SIZE_B-1:0]                         axi_rdqueue_start   ;
    wire    [AXI_RQUEUE_SIZE_B-1:0]                         axi_rdqueue_start_1 ;
    reg     [AXI_RQUEUE_SIZE_B-1:0]                         axi_rdqueue_end     ;
    reg     [AXI_RQUEUE_SIZE_B-1:0]                         axi_rdqueue_addrptr ;
    wire                                                    axi_rdqueue_empty   ;
    wire                                                    axi_rdqueue_full    ;
    wire                                                    axi_rdqueue_full_2  ;

    reg                                                     axi_wrqueue_addr    ;
    reg     [AXI_W_BUS_SIZE_R+1:0]                          axi_wrqueue_data    ;

    wire                                                    axi_wr_addr_recv    ;
    wire                                                    axi_wr_data_recv    ;
    wire                                                    axi_wr_done         ;
    wire                                                    axi_rd_addr_recv    ;
    wire                                                    axi_rd_done         ;

    wire                                                    d_stall_wr;
    wire                                                    d_stall_rd;

    reg                                                     axi_lasttype;

    wire                                                    axi_id_confilct;

    assign  axi_rdqueue_empty   =   (axi_rdqueue_start == axi_rdqueue_end);
    assign  axi_rdqueue_full    =   (axi_rdqueue_end == (axi_rdqueue_start + 1'b1));
    assign  axi_rdqueue_full_2  =   (axi_rdqueue_end == (axi_rdqueue_start_1 + 1'b1));

    assign  axi_wr_addr_recv    =   (awvalid && awready);
    assign  axi_wr_data_recv    =   (wvalid && wready);
    assign  axi_wr_done         =   (bvalid && bready);
    assign  axi_rd_addr_recv    =   (arvalid && arready);
    assign  axi_rd_done         =   (rvalid && rready && rlast);

    assign  axi_id_confilct     =   (i_addr_in == d_addr_in) && i_en && d_wr; 

    always @(posedge clk) begin
        axi_rdqueue_start   <=  rst                                                                             ? 'b0                           :
                                i_en && d_rd && !axi_rdqueue_full && (!axi_rdqueue_full_2 || axi_rd_done)       ? axi_rdqueue_start + 2'b10     :
                                (i_en || d_rd) && (!axi_rdqueue_full || axi_rd_done)                            ? axi_rdqueue_start + 1'b1      :
                                axi_rdqueue_start;
        axi_rdqueue_end     <=  rst                                                                             ? 'b0                           :
                                axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == axi_rdqueue_end                    ? axi_rdqueue_end + 1'b1        :
                                axi_rd_reg_done [axi_rdqueue_end]                                               ? axi_rdqueue_end + 1'b1        :
                                axi_rdqueue_end;
        axi_rdqueue_addrptr <=  rst                                                                             ? 'b0                           :
                                //axi_rd_addr_recv && axi_rd_reg_done [axi_rdqueue_addrptr + 1'b1]                ? axi_rdqueue_addrptr + 2'b10   :   //Prone to be dangerous
                                axi_rd_addr_recv || axi_rd_reg_done [axi_rdqueue_addrptr]                       ? axi_rdqueue_addrptr + 1'b1    :
                                axi_rdqueue_addrptr;
    end

    assign  axi_rdqueue_start_1 =   axi_rdqueue_start + 1'b1;

    genvar i;
    integer n;
    wire                            queuehit_i;         //inst read request RAW
    wire                            queuehit_d_wr;      //data read request RAW
    wire    [AXI_RQUEUE_SIZE-1:0]   queuehit_d_rd;      //data write request WAR
    wire    [AXI_RQUEUE_SIZE-1:0]   queuehit_d_rd_mov;
    reg     [AXI_RQUEUE_SIZE_B-1:0] queuehit_d_rd_enc;
    reg     [AXI_RQUEUE_SIZE_B:0]   queuehit_d_rd_id;

    assign  queuehit_i          =   (axi_wr_reg_addr == i_addr_in && axi_wr_reg_valid);
    assign  queuehit_d_wr       =   (axi_wr_reg_addr == d_addr_in && axi_wr_reg_valid);

    assign  queuehit_d_rd_mov   =   (queuehit_d_rd >> axi_rdqueue_start) | (queuehit_d_rd << (AXI_RQUEUE_SIZE - axi_rdqueue_start));

    generate
        for (i = 0; i < AXI_RQUEUE_SIZE; i = i + 1) begin
            assign queuehit_d_rd[i]         =   (axi_rd_reg_addr[i] == d_addr_in && axi_rd_reg_valid[i]);
        end

        if (AXI_RQUEUE_SIZE <= 4) begin
            always @(*) begin
                if (queuehit_d_rd_mov[0])
                    queuehit_d_rd_enc   =   2'b00;
                else if (queuehit_d_rd_mov[1])
                    queuehit_d_rd_enc   =   2'b01;
                else if (queuehit_d_rd_mov[2])
                    queuehit_d_rd_enc   =   2'b10;
                else if (queuehit_d_rd_mov[3])
                    queuehit_d_rd_enc   =   2'b11;
                else
                    queuehit_d_rd_enc   =   2'b00;
            end
        end
        else if (AXI_RQUEUE_SIZE <= 8) begin
            always @(*) begin
                if (queuehit_d_rd_mov[0])
                    queuehit_d_rd_enc   =   3'b000;
                else if (queuehit_d_rd_mov[1])
                    queuehit_d_rd_enc   =   3'b001;
                else if (queuehit_d_rd_mov[2])
                    queuehit_d_rd_enc   =   3'b010;
                else if (queuehit_d_rd_mov[3])
                    queuehit_d_rd_enc   =   3'b011;
                else if (queuehit_d_rd_mov[4])
                    queuehit_d_rd_enc   =   3'b100;
                else if (queuehit_d_rd_mov[5])
                    queuehit_d_rd_enc   =   3'b101;
                else if (queuehit_d_rd_mov[6])
                    queuehit_d_rd_enc   =   3'b110;
                else if (queuehit_d_rd_mov[7])
                    queuehit_d_rd_enc   =   3'b111;
                else
                    queuehit_d_rd_enc   =   3'b000;
            end
        end
        else begin
            always @(*) begin
                if (queuehit_d_rd_mov[0])
                    queuehit_d_rd_enc   =   4'b0000;
                else if (queuehit_d_rd_mov[1])
                    queuehit_d_rd_enc   =   4'b0001;
                else if (queuehit_d_rd_mov[2])
                    queuehit_d_rd_enc   =   4'b0010;
                else if (queuehit_d_rd_mov[3])
                    queuehit_d_rd_enc   =   4'b0011;
                else if (queuehit_d_rd_mov[4])
                    queuehit_d_rd_enc   =   4'b0100;
                else if (queuehit_d_rd_mov[5])
                    queuehit_d_rd_enc   =   4'b0101;
                else if (queuehit_d_rd_mov[6])
                    queuehit_d_rd_enc   =   4'b0110;
                else if (queuehit_d_rd_mov[7])
                    queuehit_d_rd_enc   =   4'b0111;
                else if (queuehit_d_rd_mov[8])
                    queuehit_d_rd_enc   =   4'b1000;
                else if (queuehit_d_rd_mov[9])
                    queuehit_d_rd_enc   =   4'b1001;
                else if (queuehit_d_rd_mov[10])
                    queuehit_d_rd_enc   =   4'b1010;
                else if (queuehit_d_rd_mov[11])
                    queuehit_d_rd_enc   =   4'b1011;
                else if (queuehit_d_rd_mov[12])
                    queuehit_d_rd_enc   =   4'b1100;
                else if (queuehit_d_rd_mov[13])
                    queuehit_d_rd_enc   =   4'b1101;
                else if (queuehit_d_rd_mov[14])
                    queuehit_d_rd_enc   =   4'b1110;
                else if (queuehit_d_rd_mov[15])
                    queuehit_d_rd_enc   =   4'b1111;
                else
                    queuehit_d_rd_enc   =   4'b0000;
            end
        end
    endgenerate

    always @(*) begin
        queuehit_d_rd_id[AXI_RQUEUE_SIZE_B-1:0] =   (queuehit_d_rd == {AXI_RQUEUE_SIZE{1'b0}}) ? {(AXI_RQUEUE_SIZE_B-1){1'b0}} : queuehit_d_rd_enc + axi_rdqueue_start;
        queuehit_d_rd_id[AXI_RQUEUE_SIZE_B]     =   (queuehit_d_rd == {AXI_RQUEUE_SIZE{1'b0}}) ? 1'b0 : 1'b1;
    end

    always @(posedge clk) begin
        if (rst) begin
            for (n = 0; n < AXI_RQUEUE_SIZE; n = n + 1) begin
                axi_rd_reg_addr     [n] <=  32'b0;
                axi_rd_reg_data_out [n] <=  {PORT_MAX_WIDTH{1'b0}};
                axi_rd_reg_size     [n] <=  3'b0;
                axi_rd_reg_valid    [n] <=  1'b0;
                axi_rd_reg_type     [n] <=  2'b0;
                axi_rd_reg_done     [n] <=  1'b0;
            end
        end
        else begin
            //add new request in queue
            if (i_en && d_rd && !axi_rdqueue_full && (!axi_rdqueue_full_2 || axi_rd_done)) begin
                axi_rd_reg_addr     [axi_rdqueue_start]     <=  i_addr_in;
                axi_rd_reg_data_out [axi_rdqueue_start]     <=  queuehit_i ? axi_wr_reg_data_in : {PORT_MAX_WIDTH{1'b0}};
                axi_rd_reg_size     [axi_rdqueue_start]     <=  PORT_INST_SIZE[2:0];
                axi_rd_reg_valid    [axi_rdqueue_start]     <=  1'b1;
                axi_rd_reg_done     [axi_rdqueue_start]     <=  queuehit_i ? 1'b1 : 1'b0;
                axi_rd_reg_type     [axi_rdqueue_start]     <=  2'b00;

                axi_rd_reg_addr     [axi_rdqueue_start_1]   <=  d_addr_in;
                axi_rd_reg_data_out [axi_rdqueue_start_1]   <=  queuehit_d_wr ? axi_wr_reg_data_in : {PORT_MAX_WIDTH{1'b0}};
                axi_rd_reg_size     [axi_rdqueue_start_1]   <=  d_size;
                axi_rd_reg_valid    [axi_rdqueue_start_1]   <=  1'b1;
                axi_rd_reg_done     [axi_rdqueue_start_1]   <=  queuehit_d_wr ? 1'b1 : 1'b0;
                axi_rd_reg_type     [axi_rdqueue_start_1]   <=  {1'b1, d_src};

                axi_lasttype                                <=  1'b1;
            end
            else if ((i_en || d_rd) && (!axi_rdqueue_full || axi_rd_done)) begin
                if (i_en && (~d_rd || axi_lasttype)) begin
                    axi_rd_reg_addr     [axi_rdqueue_start] <=  i_addr_in;
                    axi_rd_reg_data_out [axi_rdqueue_start] <=  axi_id_confilct ? d_data_in : 
                                                                queuehit_i      ? axi_wr_reg_data_in :
                                                                {PORT_MAX_WIDTH{1'b0}};
                    axi_rd_reg_size     [axi_rdqueue_start] <=  $clog2(PORT_INST_WIDTH / 8);
                    axi_rd_reg_valid    [axi_rdqueue_start] <=  1'b1;
                    axi_rd_reg_type     [axi_rdqueue_start] <=  2'b00;
                    axi_rd_reg_done     [axi_rdqueue_start] <=  axi_id_confilct || queuehit_i ? 1'b1 : 1'b0;
                    axi_lasttype                            <=  1'b0;
                end
                else begin
                    axi_rd_reg_addr     [axi_rdqueue_start] <=  d_addr_in;
                    axi_rd_reg_data_out [axi_rdqueue_start] <=  queuehit_d_wr ? axi_wr_reg_data_in : {PORT_MAX_WIDTH{1'b0}};;
                    axi_rd_reg_size     [axi_rdqueue_start] <=  d_size;
                    axi_rd_reg_valid    [axi_rdqueue_start] <=  1'b1;
                    axi_rd_reg_type     [axi_rdqueue_start] <=  {1'b1, d_src};
                    axi_rd_reg_done     [axi_rdqueue_start] <=  queuehit_d_wr ? 1'b1 : 1'b0;;
                    axi_lasttype                            <=  1'b1;            
                end
            end
            //issue request when available
            if (rready && rvalid) begin
                axi_rd_reg_data_out [rid[AXI_RQUEUE_SIZE_B-1:0]]    <=  {axi_rd_reg_data_out [rid[AXI_RQUEUE_SIZE_B-1:0]][PORT_MAX_WIDTH-AXI_R_BUS_WIDTH-1:0] << AXI_R_BUS_WIDTH, rdata};
            end
            //delete completed request
            if (axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] != axi_rdqueue_end) begin
                axi_rd_reg_done [rid[AXI_RQUEUE_SIZE_B-1:0]]        <=  1'b1;
            end
            if ((axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == axi_rdqueue_end) || axi_rd_reg_done[axi_rdqueue_end]) begin
                if (i_en && d_rd && !axi_rdqueue_full && (!axi_rdqueue_full_2 || axi_rd_done) && (axi_rdqueue_end != axi_rdqueue_start && axi_rdqueue_end != axi_rdqueue_start_1)) begin
                    axi_rd_reg_valid [axi_rdqueue_end]              <=  1'b0;
                    axi_rd_reg_done  [axi_rdqueue_end]              <=  1'b0;
                end
                else if ((i_en || d_rd) && (!axi_rdqueue_full || axi_rd_done) && axi_rdqueue_end != axi_rdqueue_start) begin
                    axi_rd_reg_valid [axi_rdqueue_end]              <=  1'b0;
                    axi_rd_reg_done  [axi_rdqueue_end]              <=  1'b0;
                end
                else if (!(i_en || d_rd)) begin //new
                    axi_rd_reg_valid [axi_rdqueue_end]              <=  1'b0;
                    axi_rd_reg_done  [axi_rdqueue_end]              <=  1'b0;
                end
            end
        end
    end

    always @(posedge clk) begin
        if (rst) begin
            axi_wr_reg_addr     <=  32'b0;
            axi_wr_reg_data_in  <=  {PORT_DATA_WIDTH{1'b0}};
            axi_wr_reg_size     <=  3'b0;
            axi_wr_reg_idlim    <=  {AXI_RQUEUE_SIZE_B{1'b0}};
            axi_wr_reg_idlimen  <=  1'b0;
            axi_wr_reg_valid    <=  1'b0;  
        end
        else begin
            if (d_wr && (!axi_wr_reg_valid || axi_wr_done)) begin
                axi_wr_reg_addr     <=  d_addr_in;
                axi_wr_reg_data_in  <=  d_data_in;
                axi_wr_reg_size     <=  d_size;
                axi_wr_reg_idlim    <=  queuehit_d_rd_id[AXI_RQUEUE_SIZE_B-1:0];
                axi_wr_reg_idlimen  <=  queuehit_d_rd_id[AXI_RQUEUE_SIZE_B] && !(axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == queuehit_d_rd_id[AXI_RQUEUE_SIZE_B-1:0]);
                axi_wr_reg_valid    <=  1'b1;
            end
            else if (!d_wr && axi_wr_reg_valid && axi_wr_done) begin
                axi_wr_reg_valid    <=  1'b0;
            end
            else if (((axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == axi_rdqueue_end) || axi_rd_reg_done[axi_rdqueue_end]) && axi_rdqueue_end == axi_wr_reg_idlim) begin
                axi_wr_reg_idlimen  <=  1'b0;
            end
        end
    end

    always @(posedge clk) begin
        axi_wrqueue_addr    <=  rst                     ? 1'b0                      :
                                awvalid&&awready        ? 1'b1                      :
                                axi_wr_done             ? 1'b0                      : 
                                axi_wrqueue_addr;
        axi_wrqueue_data    <=  rst                     ? 'b0                       :
                                wvalid&&wready          ? axi_wrqueue_data + 1'b1   :
                                axi_wr_done             ? 'b0                       : 
                                axi_wrqueue_data;
    end

    generate
        if (PORT_INST_WIDTH-AXI_R_BUS_WIDTH <= 0) begin
            assign  i_data      =   (axi_rdqueue_end == rid[AXI_RQUEUE_SIZE_B-1:0] ? rdata : axi_rd_reg_data_out[axi_rdqueue_end][PORT_INST_WIDTH-1:0]);
            assign  i_addr_out  =   axi_rd_reg_addr[axi_rdqueue_end];
        end
        else begin
            assign  i_data      =   (axi_rdqueue_end == rid[AXI_RQUEUE_SIZE_B-1:0] ? {rdata, axi_rd_reg_data_out[axi_rdqueue_end][PORT_INST_WIDTH-AXI_R_BUS_WIDTH-1:0]} : axi_rd_reg_data_out[axi_rdqueue_end][PORT_INST_WIDTH-1:0]);
            assign  i_addr_out  =   axi_rd_reg_addr[axi_rdqueue_end];
        end

        if (PORT_DATA_WIDTH-AXI_R_BUS_WIDTH <= 0) begin
            assign  d_data_out  =   (axi_rdqueue_end == rid[AXI_RQUEUE_SIZE_B-1:0] ? rdata : axi_rd_reg_data_out[axi_rdqueue_end][PORT_DATA_WIDTH-1:0]);
            assign  d_addr_out  =   axi_rd_reg_addr[axi_rdqueue_end];
            assign  d_dst       =   axi_rd_reg_type[axi_rdqueue_end][0];
        end
        else begin
            assign  d_data_out  =   (axi_rdqueue_end == rid[AXI_RQUEUE_SIZE_B-1:0] ? {rdata, axi_rd_reg_data_out[axi_rdqueue_end][PORT_DATA_WIDTH-AXI_R_BUS_WIDTH-1:0]} : axi_rd_reg_data_out[axi_rdqueue_end][PORT_DATA_WIDTH-1:0]);
            assign  d_addr_out  =   axi_rd_reg_addr[axi_rdqueue_end];
            assign  d_dst       =   axi_rd_reg_type[axi_rdqueue_end][0];
        end
    endgenerate


    assign  i_valid     =   ((axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == axi_rdqueue_end) || axi_rd_reg_done[axi_rdqueue_end]) && !axi_rd_reg_type[axi_rdqueue_end][1];
    assign  i_stall     =   (axi_rdqueue_full && !axi_rd_done)                                                                                                          ? 1'b1 :
                            d_rd && ((axi_rdqueue_full && axi_rd_done) || (axi_rdqueue_full_2 && !axi_rd_done)) && !axi_lasttype                                        ? 1'b1 :
                            1'b0;
    assign  d_valid     =   ((axi_rd_done && rid[AXI_RQUEUE_SIZE_B-1:0] == axi_rdqueue_end) || axi_rd_reg_done[axi_rdqueue_end]) && axi_rd_reg_type[axi_rdqueue_end][1];
    assign  d_stall_wr  =   (axi_wr_reg_valid && !axi_wr_done);
    assign  d_stall_rd  =   (axi_rdqueue_full && !axi_rd_done);
    assign  d_stall     =   d_stall_wr || d_stall_rd || 
                           (i_en && ((axi_rdqueue_full && axi_rd_done) || (axi_rdqueue_full_2 && !axi_rd_done)) && axi_lasttype);

    //ar
    assign arid     =   {2'b0, axi_rdqueue_addrptr};
    assign araddr   =   axi_rd_reg_addr[axi_rdqueue_addrptr];
    assign arlen    =   (axi_rd_reg_size[axi_rdqueue_addrptr] > AXI_R_BUS_SIZE[2:0]) ? (8'b1 << (axi_rd_reg_size[axi_rdqueue_addrptr] - AXI_R_BUS_SIZE[2:0])) >> 1'b1 : 8'b0;
    assign arsize   =   (axi_rd_reg_size[axi_rdqueue_addrptr] > AXI_R_BUS_SIZE[2:0]) ? AXI_R_BUS_SIZE[2:0] : axi_rd_reg_size[axi_rdqueue_addrptr];
    assign arburst  =   (axi_rd_reg_size[axi_rdqueue_addrptr] > AXI_R_BUS_SIZE[2:0]) ? 2'b1 : 2'b0;
    assign arlock   =   2'd0;
    assign arcache  =   4'd0;
    assign arprot   =   3'd0;
    assign arvalid  =   !(axi_rdqueue_addrptr == axi_rdqueue_start) && !axi_rd_reg_done[axi_rdqueue_addrptr];
    //r
    assign rready   =   1'b1;

    //aw
    assign awid     =   4'd0;
    assign awaddr   =   axi_wr_reg_addr;
    assign awlen    =   (axi_wr_reg_size > AXI_W_BUS_SIZE[2:0]) ? (8'b1 << (axi_wr_reg_size - AXI_W_BUS_SIZE[2:0])) >> 1'b1 : 8'b0;
    assign awsize   =   (axi_wr_reg_size > AXI_W_BUS_SIZE[2:0]) ? AXI_W_BUS_SIZE[2:0] : axi_wr_reg_size;
    assign awburst  =   (axi_wr_reg_size > AXI_W_BUS_SIZE[2:0]) ? 2'b1 : 2'b0;
    assign awlock   =   2'd0;
    assign awcache  =   4'd0;
    assign awprot   =   3'd0;
    assign awvalid  =   axi_wr_reg_valid && !axi_wrqueue_addr && !axi_wr_reg_idlimen;
    //w
    assign wid      =   4'd0;
    assign wdata    =   (axi_wr_reg_size <= AXI_W_BUS_SIZE) ? axi_wr_reg_data_in [31:0] << (8 * axi_wr_reg_addr[$clog2(AXI_W_BUS_WIDTH / 8) - 1:0]) : axi_wr_reg_data_in[AXI_W_BUS_WIDTH * axi_wrqueue_data +:AXI_W_BUS_WIDTH];
    assign wstrb    =   axi_wr_reg_size == 3'd0 ? 1 << axi_wr_reg_addr[$clog2(AXI_W_BUS_WIDTH / 8) - 1:0] :
                        axi_wr_reg_size == 3'd1 ? 3 << axi_wr_reg_addr[$clog2(AXI_W_BUS_WIDTH / 8) - 1:0] : 
                        axi_wr_reg_size == 3'd2 ? 15 << axi_wr_reg_addr[$clog2(AXI_W_BUS_WIDTH / 8) - 1:0] : 
                        {(AXI_W_BUS_WIDTH / 8){1'b1}};
    
    assign wlast    =   (axi_wr_reg_size <= AXI_W_BUS_SIZE)
                        ? 1'd1 
                        : (axi_wrqueue_data[AXI_W_BUS_SIZE_R-1:0] == {AXI_W_BUS_SIZE_R{1'b1}});
    assign wvalid   =   (axi_wr_reg_size <= AXI_W_BUS_SIZE)
                        ? axi_wr_reg_valid && !axi_wrqueue_data
                        : axi_wr_reg_valid && !(axi_wrqueue_data[AXI_W_BUS_SIZE_R]);
    //endgenerate
    //b
    assign bready   =   1'b1;

endmodule