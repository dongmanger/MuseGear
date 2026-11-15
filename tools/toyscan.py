# -*- coding: utf-8 -*-
"""toyscan.py —— 扫附近 BLE 广播，确认暮瑟设备是否已开机在广播（找 wbMSE 前缀）"""
import asyncio
import sys

from bleak import BleakScanner

MAGIC = "77624d5345"   # "wbMSE"


def fmt_mfr(d):
    out = []
    for cid, val in (d.manufacturer_data or {}).items():
        h = val.hex()
        tag = "  <== wbMSE 命中" if MAGIC in h else ""
        out.append("cid=0x%04X data=%s%s" % (cid, h, tag))
    return "; ".join(out)


async def main(secs=12):
    print("[*] 扫描 %ds ..." % secs, flush=True)
    devs = await BleakScanner.discover(timeout=secs)
    hit = 0
    for d in sorted(devs, key=lambda x: (x.rssi or -999), reverse=True):
        mfr = fmt_mfr(d)
        if MAGIC in mfr:
            hit += 1
        print("%-18s rssi=%-5s %-22s %s" % (d.address, d.rssi, (d.name or "-")[:22], mfr), flush=True)
    print("\n[*] 共 %d 个设备，wbMSE 命中 %d 个" % (len(devs), hit), flush=True)
    if hit == 0:
        print("[!] 没扫到 wbMSE。检查：玩具是否开机 / 是否在 1-2 米内 / 是否已被手机 App 连走", flush=True)


if __name__ == "__main__":
    secs = int(sys.argv[1]) if len(sys.argv) > 1 else 12
    asyncio.run(main(secs))
