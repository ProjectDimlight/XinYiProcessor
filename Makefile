# build tools and flags
SBT 			?= sbt
SBT_FLAGS 		?= -Dsbt.server.forcestart=true --batch
VERILATOR		?= verilator
VERILATOR_FLAGS	?= --cc --exe --top-module mycpu_top --threads 1 --assert --x-assign unique --output-split 20000 -O3 

# directories
VERILOG_DIR 	?= src/main/verilog
MYCPU_DIR 		?= src/main/verilog/mycpu

# source codes
MYCPU_V			?= $(MYCPU_DIR)/mycpu_top.v
DP_BRAM_V		?= $(MYCPU_DIR)/dual_port_bram.v
DP_LUTRAM_V		?= $(MYCPU_DIR)/dual_port_lutram.v
SP_BRAM_V		?= $(MYCPU_DIR)/single_port_bram.v


.PHONY: main no_ip_div verilator checkstyle clean


main:
	$(SBT) $(SBT_FLAGS) "runMain Main"
	cat $(DP_BRAM_V) >> $(MYCPU_V)
	cat $(DP_LUTRAM_V) >> $(MYCPU_V)
	cat $(SP_BRAM_V) >> $(MYCPU_V)

# generate verilog code for verilator
no_ip_div:
	$(SBT) $(SBT_FLAGS) "runMain Verilator"
	sed -i 's/\[32:0\]\sdiv_res_hi/\[31:0\] div_res_hi/g' $(MYCPU_V)
	sed -i 's/\[64:0\]\s_div_res_T_2/\[63:0\] _div_res_T_2/g' $(MYCPU_V)
	cat $(DP_BRAM_V) >> $(MYCPU_V)
	cat $(DP_LUTRAM_V) >> $(MYCPU_V)
	cat $(SP_BRAM_V) >> $(MYCPU_V)

	
verilator:
	$(VERILATOR) $(VERILATOR_FLAGS) $(MYCPU_V) $(AXI_V) 


checkstyle:
	$(SBT) $(SBT_FLAGS) scalastyle test:scalastyle


clean:
	-@rm -rf $(MYCPU_DIR)
	# -@find $(VERILOG_DIR)/AXICrossbar/. ! -name 'AXICrossbar.xci' -exec rm -r {} \;
	# -@find $(VERILOG_DIR)/DIVU/. ! -name 'DIVU.xci' -exec rm -r {} \;
	# -@find $(VERILOG_DIR)/DIV/. ! -name 'DIV.xci' -exec rm -r {} \;
