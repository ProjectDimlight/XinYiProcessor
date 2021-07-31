#include <iostream>
#include <fstream>
#include <iomanip>
#include <string>
#include <vector>
#include <cstdio>

#include "VCacheTB.h"
#include "verilated.h"

#ifdef VM_TRACE // --trace
#include <verilated_vcd_c.h>
static VerilatedVcdC *fp; //to form *.vcd file
#endif

#define emu_t VCacheTB

void reset(emu_t *dut, uint cycle)
{
    for (int i = 0; i < cycle; i++)
    {
        dut->reset = 1;
        dut->clock = 0;
        dut->eval();
        dut->clock = 1;
        dut->eval();
        dut->reset = 0;
    }
}

void step(emu_t *dut, uint32_t cycle)
{
    for (int i = 0; i < cycle; i++)
    {
        dut->clock = 0;
        dut->eval();
#if VM_TRACE
        tfp->dump(static_cast<vluint64_t>(trace_count * 2));
#endif
        dut->clock = 1;
        dut->eval();
#if VM_TRACE
        tfp->dump(static_cast<vluint64_t>(trace_count * 2 + 1));
#endif
    }
}

int main(int argc, char **argv)
{
    Verilated::commandArgs(argc, argv);
    emu_t *dut = new emu_t;
#if VM_TRACE
    Verilated::traceEverOn(true); // Verilator must compute traced signals
    tfp = new VerilatedVcdC;
    dut->trace(tfp, 99); // Trace 99 levels of hierarchy
    tfp->open("sim.vcd");
    std::cerr << ANSI_COLOR_MAGENTA "TFP successfully opened sim.vcd\n"
              << ANSI_COLOR_RESET << std::endl;
#endif

    reset(dut, 10);
    std::ifstream infile;
    infile.open(argv[1], std::ifstream::in);
    // FILE* infile = fopen("/home/zhxj/XinYiProcessor/src/test/verilator/simple.data", "r");
    std::string op;
    // char op;
    uint32_t addr, data;
    bool process = false;
    bool last_stall = 0;
    int cycle = 0;
    bool end_next = false;
    bool err = false;

    while (!Verilated::gotFinish())
    {
        // std::cerr << "*-------- Round " << cycle << " --------*" << std::endl;
        cycle++;
        if (!process)
        {
            infile >> op >> std::hex >> addr >> data >> std::dec;
            // std::cerr << "read one line from file: " << op << std::hex << " " << addr << " " << data << std::dec << std::endl;
            process = true;
            end_next = infile.eof();
        }
        dut->io_cpu_rd = !(op == "w");
        dut->io_cpu_wr = op == "w";
        dut->io_cpu_uncached = 0;
        dut->io_cpu_size = 2;
        dut->io_cpu_strb = 0xf;
        dut->io_cpu_addr = addr;
        dut->io_cpu_din = data;
        dut->io_last_stall = last_stall;
        last_stall = dut->io_cpu_stall_req;
        bool new_request = !dut->io_last_stall;

        // std::cerr << "io_cpu_rd: " << static_cast<int>(dut->io_cpu_rd) << std::endl;
        // std::cerr << "io_cpu_wr: " << static_cast<int>(dut->io_cpu_wr) << std::endl;
        // std::cerr << "io_cpu_uncached: " << static_cast<int>(dut->io_cpu_uncached) << std::endl;
        // std::cerr << "io_cpu_size: " << static_cast<int>(dut->io_cpu_size) << std::endl;
        // std::cerr << "io_cpu_strb: " << static_cast<int>(dut->io_cpu_strb) << std::endl;
        // std::cerr << "io_cpu_addr: " << static_cast<int>(dut->io_cpu_addr) << std::endl;
        // std::cerr << "io_cpu_din: " << static_cast<int>(dut->io_cpu_din) << std::endl;
        // std::cerr << "io_cpu_dout: " << static_cast<int>(dut->io_cpu_dout) << std::endl;
        // std::cerr << "io_cpu_stall_req: " << static_cast<int>(dut->io_cpu_stall_req) << std::endl;
        // std::cerr << "io_last_stall: " << static_cast<int>(dut->io_last_stall) << std::endl;

        if (!new_request && !dut->io_cpu_stall_req)
        {
            if (op == "r" && dut->io_cpu_dout != data)
            {
                std::cerr << "error: " << op << std::hex << " " << addr << " " << data << " " << dut->io_cpu_dout << std::dec << std::endl;
                err = true;
                break;
            }
            else
            {
                // std::cerr << "pass: " << op << std::hex << " " << addr << " " << data << std::dec << std::endl;
                if (end_next)
                {
                    break;
                }
            }
            process = 0;
        }

        step(dut, 1);
    }

    std::cerr << (err ? "Test Failed" : "Test Pass") << std::endl;

    delete dut;
#if VM_TRACE
    if (tfp)
    {
        tfp->close();
    }
#endif
    infile.close();

    exit(0);
}
