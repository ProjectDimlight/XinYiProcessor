SBT ?= sbt
SBT_FLAGS ?= -Dsbt.server.forcestart=true --batch


.PHONY: main verilator checkstyle clean



main:
	$(SBT) $(SBT_FLAGS) "runMain Main"


# generate verilog code for verilator
verilator:
	$(SBT) $(SBT_FLAGS) "runMain Verilator"
	sed -i 's/\[32:0\]\sdiv_res_lo/\[31:0\] div_res_lo/g' verilog/mycpu_top.v
	sed -i 's/\[64:0\]\s_div_res_T_2/\[63:0\] _div_res_T_2/g' verilog/mycpu_top.v
	
	


checkstyle:
	$(SBT) $(SBT_FLAGS) scalastyle test:scalastyle

clean:
	-@rm -rf verilog/
