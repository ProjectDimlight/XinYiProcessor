# build tools and flags
SBT 			?= sbt
SBT_FLAGS 		?= -Dsbt.server.forcestart=true --batch

# directories
VERILOG_DIR 	?= verilog_modules
MYCPU_DIR		?= $(VERILOG_DIR)/mycpu_top

# source codes
MYCPU_V			?= $(MYCPU_DIR)/mycpu_top.v 

.PHONY: main no_ip_div verilator copy_axi checkstyle clean


main:
	$(SBT) $(SBT_FLAGS) "runMain Main"

# generate verilog code for verilator
verilator:
	$(SBT) $(SBT_FLAGS) "runMain Verilator"
	sed -i 's/\[32:0\]\sdiv_res_hi/\[31:0\] div_res_hi/g' $(MYCPU_DIR)/mycpu_top.v
	sed -i 's/\[64:0\]\s_div_res_T_2/\[63:0\] _div_res_T_2/g' $(MYCPU_DIR)/mycpu_top.v

checkstyle:
	$(SBT) $(SBT_FLAGS) scalastyle test:scalastyle


clean:
	-@rm -rf $(MYCPU_DIR)/
