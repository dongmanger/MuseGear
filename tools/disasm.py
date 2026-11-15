from elftools.elf.elffile import ELFFile
from capstone import *

path = r"../evidence/native/libble_arm64.so"
f = open(path, "rb")
elf = ELFFile(f)
symtab = elf.get_section_by_name(".dynsym")
target = None
for s in symtab.iter_symbols():
    if "get_1rf_1payload" in s.name:
        target = s
        break
print("symbol:", target.name)
addr = target["st_value"]; size = target["st_size"]
print("addr=%#x size=%d" % (addr, size))

# 找到包含该地址的节
data = None
for sec in elf.iter_sections():
    a = sec["sh_addr"]; sz = sec["sh_size"]
    if a <= addr < a + sz and sec["sh_type"] == "SHT_PROGBITS":
        data = sec.data()
        off = addr - a
        if size == 0: size = 512
        code = data[off:off + size + 64]
        print("section:", sec.name)
        break

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
md.detail = True
for i in md.disasm(code, addr):
    print("%#010x  %-8s %s" % (i.address, i.mnemonic, i.op_str))
