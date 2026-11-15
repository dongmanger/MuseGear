# -*- coding: utf-8 -*-
"""批量仿真 libble.so 的 get_rf_payload，导出 0x00..0x6E 全量命令表"""
import json
import struct
import sys

from elftools.elf.elffile import ELFFile
from unicorn import *
from unicorn.arm64_const import *

SO = r"../evidence/native/libble_arm64.so"
FUNC = 0x8AC
GOT = {0x11fb0: 0xDEAD0001, 0x11fb8: 0x0EC4, 0x11fc0: 0xDEAD0001, 0x11fc8: 0xDEAD0002,
       0x11fd0: 0x0E60, 0x11fd8: 0x0BD0, 0x11fe0: 0x0C68, 0x11fe8: 0xDEAD0001, 0x11ff0: 0xDEAD0001}
PREFIX = bytes.fromhex("77624d5345")


def al(x, n=0x1000):
    return (x + n - 1) // n * n


elf = ELFFile(open(SO, "rb"))
SEG = []
for seg in elf.iter_segments():
    if seg["p_type"] == "PT_LOAD":
        SEG.append((seg["p_vaddr"], seg.data(), seg["p_memsz"]))
REL = []
s = elf.get_section_by_name(".rela.dyn")
if s:
    for r in s.iter_relocations():
        if r["r_info_type"] == 1027:
            REL.append((r["r_offset"], r["r_addend"]))


def run(cmd: bytes):
    mu = Uc(UC_ARCH_ARM64, UC_MODE_ARM)
    for va, d, ms in SEG:
        st = va & ~0xFFF
        e = al(va + max(len(d), ms))
        try:
            mu.mem_map(st, e - st)
        except UcError:
            pass
        if d:
            mu.mem_write(va, d)
    for off, t in GOT.items():
        mu.mem_write(off, struct.pack("<Q", t))
    for off, ad in REL:
        mu.mem_write(off, struct.pack("<Q", ad))
    for a, sz in ((0x1000000, 0x200000), (0x2000000, 0x200000), (0x3000000, 0x1000), (0x3100000, 0x1000),
                  (0x4000000, 0x1000), (0x5000000, 0x1000), (0x5100000, 0x1000), (0x5300000, 0x1000),
                  (0xDEAD0000, 0x1000)):
        mu.mem_map(a, sz)
    for off in range(0, 0x800, 8):
        mu.mem_write(0x3100000 + off, struct.pack("<Q", 0x4000000))
    mu.mem_write(0x3000000, struct.pack("<Q", 0x3100000))
    hh = {"h": 0x2000000}

    def cb(uc, a, sz, u):
        if a == 0x4000000:
            x1 = uc.reg_read(UC_ARM64_REG_X1)
            r = {0x1111: 0x5000000, 0x2222: 0x5100000, 0x3333: 0x5300000}.get(x1, 0)
            uc.reg_write(UC_ARM64_REG_X0, r)
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
        elif a == 0xDEAD0001:
            uc.reg_write(UC_ARM64_REG_X0, 0)
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))
        elif a == 0xDEAD0002:
            n = uc.reg_read(UC_ARM64_REG_X0)
            p = hh["h"]
            hh["h"] = al(p + max(n, 64) + 256, 0x100)
            uc.reg_write(UC_ARM64_REG_X0, p)
            uc.reg_write(UC_ARM64_REG_PC, uc.reg_read(UC_ARM64_REG_LR))

    mu.hook_add(UC_HOOK_CODE, cb)
    mu.mem_write(0x5000000, PREFIX + b"\0" * (0x1000 - len(PREFIX)))
    mu.mem_write(0x5100000, cmd + b"\0" * (0x1000 - len(cmd)))
    mu.mem_write(0x5300000, b"\0" * 0x1000)
    mu.reg_write(UC_ARM64_REG_SP, 0x1100000)
    mu.reg_write(UC_ARM64_REG_X0, 0x3000000)
    mu.reg_write(UC_ARM64_REG_X1, 0)
    mu.reg_write(UC_ARM64_REG_X2, 0x1111)
    mu.reg_write(UC_ARM64_REG_X3, len(PREFIX))
    mu.reg_write(UC_ARM64_REG_X4, 0x2222)
    mu.reg_write(UC_ARM64_REG_X5, len(cmd))
    mu.reg_write(UC_ARM64_REG_X6, 0x3333)
    mu.reg_write(UC_ARM64_REG_LR, 0xDEAD0000)
    try:
        mu.emu_start(FUNC, 0xDEAD0000, count=3000000)
    except UcError:
        pass
    n = len(PREFIX) + len(cmd) + 5
    return bytes(mu.mem_read(0x5300000, n))


def main():
    lo = int(sys.argv[1], 16) if len(sys.argv) > 1 else 0x00
    hi = int(sys.argv[2], 16) if len(sys.argv) > 2 else 0x6E
    out = {}
    for v in range(lo, hi + 1):
        cmd = bytes([v])
        key = "%02X" % v
        out[key] = run(cmd).hex().upper()
    # 额外多字节命令
    extra = {"0A01": bytes.fromhex("0a01")}
    for k, c in extra.items():
        out[k] = run(c).hex().upper()
    with open(r"../data/rf_table_full.json", "w") as f:
        json.dump(out, f, indent=0)
    print("entries=%d  ->  ../data/rf_table_full.json" % len(out))
    # 抽检
    for k in ("00", "05", "09", "11", "19", "20", "31", "49", "50", "6E"):
        if k in out:
            print("  %-4s %s" % (k, out[k]))


main()
