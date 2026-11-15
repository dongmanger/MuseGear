# -*- coding: utf-8 -*-
"""
toyctl.py —— 暮瑟 2.4G 玩具广播控制器（带 hold / demo / 服务UUID 变体）

  python toyctl.py demo             # 1~9 档逐档 2 秒循环两轮，用来验证链路
  python toyctl.py gear 5 30        # 5 档持续 30 秒
  python toyctl.py stop
  python toyctl.py raw 77624d5345ee
  python toyctl.py uuid on|off      # 是否附带 ServiceUUID 0000ae8f（默认 on，失败自动回退 off）
"""
import sys
import time
import uuid as _uuid

from winrt.windows.devices.bluetooth.advertisement import (
    BluetoothLEAdvertisementPublisher, BluetoothLEAdvertisementDataSection)
from winrt.windows.storage.streams import DataWriter

PREFIX = "77624d5345"          # "wbMSE"
COMPANY_ID = 255
SERVICE_UUID = "0000ae8f-0000-1000-8000-00805f9b34fb"
_use_uuid = True
_cur = None


def _build(payload_hex, with_uuid):
    pub = BluetoothLEAdvertisementPublisher()
    adv = pub.advertisement
    if with_uuid:
        adv.service_uuids.append(_uuid.UUID(SERVICE_UUID))
    sec = BluetoothLEAdvertisementDataSection()
    sec.data_type = 0xFF
    w = DataWriter()
    w.write_bytes(bytes([COMPANY_ID & 0xFF, COMPANY_ID >> 8]) + bytes.fromhex(payload_hex))
    sec.data = w.detach_buffer()
    adv.data_sections.append(sec)
    return pub


def start(payload_hex):
    global _cur, _use_uuid
    stop()
    for with_uuid in ([True, False] if _use_uuid else [False]):
        try:
            pub = _build(payload_hex, with_uuid)
            pub.start()
            for _ in range(30):
                if int(pub.status) >= 1:
                    break
                time.sleep(0.05)
            if int(pub.status) in (1, 2):
                _cur = pub
                _use_uuid = with_uuid
                print("[+] on air  FF00%s   uuid=%s  status=%s" % (payload_hex.upper(), with_uuid, pub.status))
                return True
            pub.stop()
        except Exception as e:
            if _use_uuid:
                print("[i] uuid 变体失败(%s)，回退不带 uuid" % e)
    print("[!] 启动失败")
    return False


def stop():
    global _cur
    if _cur is not None:
        try:
            _cur.stop()
        except Exception:
            pass
        _cur = None


def gear(level):
    return start(PREFIX + ("%02x" % level))


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]
    if cmd == "demo":
        try:
            for rnd in range(2):
                print("--- round %d ---" % (rnd + 1))
                for lv in range(1, 10):
                    if not gear(lv):
                        return
                    time.sleep(2)
                print("--- stop ---")
                start(PREFIX + "00")
                time.sleep(2)
        finally:
            stop()
    elif cmd == "gear":
        lv = int(sys.argv[2])
        hold = int(sys.argv[3]) if len(sys.argv) > 3 else 20
        if gear(lv):
            time.sleep(hold)
            stop()
    elif cmd == "stop":
        start(PREFIX + "00")
        time.sleep(1)
        stop()
    elif cmd == "raw":
        start(sys.argv[2])
        time.sleep(int(sys.argv[3]) if len(sys.argv) > 3 else 10)
        stop()
    elif cmd == "uuid":
        globals()["_use_uuid"] = (sys.argv[2] == "on")
        print("uuid =", globals()["_use_uuid"])
    else:
        print(__doc__)


if __name__ == "__main__":
    try:
        main()
    finally:
        stop()
