# build tools and flags
SBT 				?= sbt
SBT_FLAGS 			?= -Dsbt.server.forcestart=true --batch
VERILATOR			?= verilator
VERILATOR_FLAGS		?= --cc --exe --top-module mycpu_top --threads 1 --assert --x-assign unique --output-split 20000 -O3 

# directories
VERILOG_MODULES_DIR ?= verilog_modules
MYCPU_DIR			?= $(VERILOG_MODULES_DIR)/mycpu_top
AXI_DIR				?= $(VERILOG_MODULES_DIR)/AXI_complex

# source codes
MYCPU_V				?= $(MYCPU_DIR)/mycpu_top.v 
AXI_V				?= $(AXI_DIR)/AXI_complex_triport.v


.PHONY: main no_ip_div verilator checkstyle clean


main:
	$(SBT) $(SBT_FLAGS) "runMain Main"


# generate verilog code for verilator
no_ip_div:
	$(SBT) $(SBT_FLAGS) "runMain Verilator"
	sed -i 's/\[32:0\]\sdiv_res_hi/\[31:0\] div_res_hi/g' $(MYCPU_DIR)/mycpu_top.v
	sed -i 's/\[64:0\]\s_div_res_T_2/\[63:0\] _div_res_T_2/g' $(MYCPU_DIR)/mycpu_top.v
	
verilator:
	$(VERILATOR) $(VERILATOR_FLAGS) $(MYCPU_V) $(AXI_V) 

checkstyle:
	$(SBT) $(SBT_FLAGS) scalastyle test:scalastyle

clean:
	-@rm -rf $(MYCPU_DIR)/
