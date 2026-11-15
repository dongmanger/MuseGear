import struct
from elftools.elf.elffile import ELFFile
from unicorn import *
from unicorn.arm64_const import *
SO=r"../evidence/native/libble_arm64.so"; FUNC=0x8AC
GOT={0x11fb0:0xDEAD0001,0x11fb8:0x0EC4,0x11fc0:0xDEAD0001,0x11fc8:0xDEAD0002,
     0x11fd0:0x0E60,0x11fd8:0x0BD0,0x11fe0:0x0C68,0x11fe8:0xDEAD0001,0x11ff0:0xDEAD0001}
def al(x,n=0x1000): return (x+n-1)//n*n
mu=Uc(UC_ARCH_ARM64,UC_MODE_ARM)
elf=ELFFile(open(SO,"rb"))
for seg in elf.iter_segments():
    if seg["p_type"]!="PT_LOAD": continue
    va=seg["p_vaddr"]; d=seg.data(); s=va&~0xFFF; e=al(va+max(len(d),seg["p_memsz"]))
    try: mu.mem_map(s,e-s)
    except UcError: pass
    if d: mu.mem_write(va,d)
for off,t in GOT.items(): mu.mem_write(off,struct.pack("<Q",t))
s=elf.get_section_by_name(".rela.dyn")
if s:
    for r in s.iter_relocations():
        if r["r_info_type"]==1027: mu.mem_write(r["r_offset"],struct.pack("<Q",r["r_addend"]))
for a,sz in ((0x1000000,0x200000),(0x2000000,0x200000),(0x3000000,0x1000),(0x3100000,0x1000),
             (0x4000000,0x1000),(0x5000000,0x1000),(0x5100000,0x1000),(0x5300000,0x1000),(0xDEAD0000,0x1000)):
    mu.mem_map(a,sz)
for off in range(0,0x800,8): mu.mem_write(0x3100000+off,struct.pack("<Q",0x4000000))
mu.mem_write(0x3000000,struct.pack("<Q",0x3100000))
st={"h":0x2000000}
def cb(uc,a,sz,u):
    if a==0x4000000:
        x1=uc.reg_read(UC_ARM64_REG_X1)
        r=0
        if x1==0x1111: r=0x5000000
        elif x1==0x2222: r=0x5100000
        elif x1==0x3333: r=0x5300000
        uc.reg_write(UC_ARM64_REG_X0,r)
        uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    elif a==0xDEAD0001:
        uc.reg_write(UC_ARM64_REG_X0,0); uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
    elif a==0xDEAD0002:
        n=uc.reg_read(UC_ARM64_REG_X0); p=st["h"]; st["h"]=al(p+max(n,64)+256,0x100)
        uc.reg_write(UC_ARM64_REG_X0,p); uc.reg_write(UC_ARM64_REG_PC,uc.reg_read(UC_ARM64_REG_LR))
mu.hook_add(UC_HOOK_CODE,cb)
import sys
P=bytes.fromhex(sys.argv[1] if len(sys.argv)>1 else "77624d5345")
C=bytes.fromhex(sys.argv[2] if len(sys.argv)>2 else "05")
mu.mem_write(0x5000000,P+b"\0"*(0x1000-len(P)))
mu.mem_write(0x5100000,C+b"\0"*(0x1000-len(C)))
mu.mem_write(0x5300000,b"\0"*0x1000)
mu.reg_write(UC_ARM64_REG_SP,0x1100000)
mu.reg_write(UC_ARM64_REG_X0,0x3000000);mu.reg_write(UC_ARM64_REG_X1,0)
mu.reg_write(UC_ARM64_REG_X2,0x1111);mu.reg_write(UC_ARM64_REG_X3,len(P))
mu.reg_write(UC_ARM64_REG_X4,0x2222);mu.reg_write(UC_ARM64_REG_X5,len(C))
mu.reg_write(UC_ARM64_REG_X6,0x3333)
mu.reg_write(UC_ARM64_REG_LR,0xDEAD0000)
try:
    mu.emu_start(FUNC,0xDEAD0000,count=3000000); print("[ok]")
except UcError as e: print("[!]",e,"pc=%#x"%mu.reg_read(UC_ARM64_REG_PC))
out=bytes(mu.mem_read(0x5300000,len(P)+len(C)+8))
print("prefix=%s cmd=%s"%(P.hex().upper(),C.hex().upper()))
print("RF PAYLOAD (%d bytes): %s"%(len(out),out.hex().upper()))
