# -*- coding: utf-8 -*-
"""
play.py —— 暮瑟 2.4G 玩具 播控器（验证通过：FF00 77624D5345 <档位>）

  python play.py ramp 12         # 爬坡：1→9 匀速 12 秒
  python play.py pulse 3 1 1     # 脉冲：9档1秒 / 停1秒，做 3 组
  python play.py wave 2          # 波浪：1→9→1 平滑两个来回
  python play.py tease 20        # 挑逗：随机在 0/1/3/5/9 之间快切，持续 20 秒
  python play.py hold 6 30       # 定档 6 档 30 秒
  python play.py stop
  python play.py seq 9,0,9,0,5   # 自定义序列，每步 1 秒
"""
import sys
import time
import random

from winrt.windows.devices.bluetooth.advertisement import (
    BluetoothLEAdvertisementPublisher, BluetoothLEAdvertisementDataSection)
from winrt.windows.storage.streams import DataWriter

PREFIX = bytes.fromhex("77624d5345")   # "wbMSE"
cur = None


def _stop_pub():
    global cur
    if cur is not None:
        try:
            cur.stop()
        except Exception:
            pass
        cur = None


def gear(level):
    """level 0~9，返回是否成功上到空口"""
    global cur
    _stop_pub()
    payload = PREFIX + bytes([level & 0xFF])
    p = BluetoothLEAdvertisementPublisher()
    s = BluetoothLEAdvertisementDataSection()
    s.data_type = 0xFF
    w = DataWriter()
    w.write_bytes(b"\xff\x00" + payload)
    s.data = w.detach_buffer()
    p.advertisement.data_sections.append(s)
    p.start()
    for _ in range(40):
        if int(p.status) >= 1:
            break
        time.sleep(0.02)
    cur = p
    return int(p.status) in (1, 2)


def hold(level, secs):
    gear(level)
    time.sleep(secs)


def ramp(secs):
    steps = 9
    dt = secs / steps
    for lv in range(1, 10):
        gear(lv)
        print("  ramp %d" % lv, flush=True)
        time.sleep(dt)


def pulse(times=3, on_s=1.0, off_s=1.0, level=9):
    for i in range(times):
        gear(level); print("  pulse #%d ON  %d" % (i + 1, level), flush=True)
        time.sleep(on_s)
        gear(0); print("  pulse #%d off" % (i + 1), flush=True)
        time.sleep(off_s)


def wave(rounds=2, step=0.35):
    for r in range(rounds):
        for lv in list(range(1, 10)) + list(range(9, 0, -1)):
            gear(lv)
            time.sleep(step)
        print("  wave round %d" % (r + 1), flush=True)


def tease(secs, choices=(0, 1, 3, 5, 9)):
    end = time.time() + secs
    while time.time() < end:
        lv = random.choice(choices)
        gear(lv)
        print("  tease %d" % lv, flush=True)
        time.sleep(random.uniform(0.6, 2.2))


def seq(levels, step=1.0):
    for lv in levels:
        gear(lv)
        print("  seq %d" % lv, flush=True)
        time.sleep(step)


def main():
    if len(sys.argv) < 2:
        print(__doc__); return
    c = sys.argv[1]
    a = sys.argv[2:]
    try:
        if c == "stop":
            gear(0); time.sleep(1)
        elif c == "hold":
            hold(int(a[0]), float(a[1]) if len(a) > 1 else 20)
        elif c == "ramp":
            ramp(float(a[0]) if a else 12)
        elif c == "pulse":
            pulse(int(a[0]) if a else 3,
                  float(a[1]) if len(a) > 1 else 1.0,
                  float(a[2]) if len(a) > 2 else 1.0)
        elif c == "wave":
            wave(int(a[0]) if a else 2)
        elif c == "tease":
            tease(float(a[0]) if a else 20)
        elif c == "seq":
            seq([int(x) for x in a[0].split(",")], float(a[1]) if len(a) > 1 else 1.0)
        else:
            print(__doc__)
    finally:
        _stop_pub()
        print("  [已停]", flush=True)


if __name__ == "__main__":
    main()
