#!/bin/sh

sbt "runMain CacheTB"
mkdir -p /home/zhxj/XinYiProcessor/build/verilator
verilator --cc --exe --top-module CacheTB --assert --x-assign unique --output-split 20000 -O3 -I/home/zhxj/XinYiProcessor/src/main/verilog/mycpu -CFLAGS "-O3 -std=c++11 -g -I/home/zhxj/XinYiProcessor/src/test/verilator -I/home/zhxj/XinYiProcessor/build/verilator/build" -Wno-fatal -Wno-lint -Wno-style -o /home/zhxj/XinYiProcessor/build/verilator/emulator -Mdir /home/zhxj/XinYiProcessor/build/verilator/build /home/zhxj/XinYiProcessor/src/test/verilator/cache.cpp /home/zhxj/XinYiProcessor/src/main/verilog/mycpu/CacheTB.v
make -C /home/zhxj/XinYiProcessor/build/verilator/build -f /home/zhxj/XinYiProcessor/build/verilator/build/VCacheTB.mk
./build/verilator/emulator