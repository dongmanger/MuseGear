# 踩过的坑

按时间顺序记录，每条都是**实际卡住过**的，附根因和验证方式。

**逆向目标**：暮瑟 2.4G 玩具 **产品编号 9333**（`BarCode`），心形头长柄款，`Wireless=2.4g`，`BroadcastPrefix="77 62 4d 53 45"`（ASCII `wbMSE`），`ServiceUuid` 为空 —— 纯广播接收端。下文所有帧与命令均在该型号上实测。

---

## 坑 1：照着反编译出来的字节发，设备一动不动

**现象**：`BleUtils.getBleCommand("05")` 读出来是「前缀 + 命令」，于是直接发 `FF 00 77 62 4D 53 45 05`。回调 `onStartSuccess`，设备毫无反应。

**根因**：`getBleCommand` 的尾部不是简单拼接：

```java
int i8 = length + length2 + 5;
byte[] bArr3 = new byte[i8];
BLEUtil.get_rf_payload(bArr, length, bArr2, length2, bArr3);   // native
```

真正的帧要经 `libble.so` 做**位反转 + 双重白化 + CRC16**，11 字节，形状是 `6D B6 43 CE 97 FE 42 7C <cmd^0xE5> <crc16>`。

**怎么发现的**：把 `get_rf_payload`（0x8AC）用 capstone 反汇编，再用 Unicorn 搭假 JNIEnv 直接执行它。第一次跑挂在 `blr x8` 跳到地址 0 —— 因为 `env->GetByteArrayElements` 是**两级解引用**：`*(JNIEnv*) = functionTable`，`functionTable[0x5C0/8] = fn`，只填一层表就会跳到空指针。

**教训**：反编译出来的「看起来像协议」的代码，只要出现 `native` 或 `+5` 这种魔法偏移，就必须把 native 那段也跑出来，不能猜。

---

## 坑 2：回调说成功，设备还是不动

**现象**：帧对了，广播器返回 `ADVERTISE_SUCCESS`，设备依然不动。

**根因**：`BeaconManager.createAdvSettings()` 里有 **`setConnectable(true)`**。

```java
type==0  → BALANCED   + timeout 1500
type!=0  → LOW_LATENCY + timeout 3000
两者均   setConnectable(true) + TxPowerLevel(3)
```

`connectable` 决定 Android 发的是 `ADV_IND`(0x00) 还是 `ADV_NONCONN_IND`(0x02)。2.4G 接收芯片按 PDU 类型过滤，非连接型直接丢包 —— **但 APP 层回调仍然是成功**。

**验证**：把 `setConnectable(false)` 改成 `true`，同一条命令立刻有反应。

**教训**：BLE 广告的「成功」只代表「controller 收下了」，不代表「空口上有人能收到」。这类失败必须在协议层（PDU 类型）而不是 API 层找。

---

## 坑 3：扫不到设备，以为硬件不支持

**现象**：用 BLE 扫描找设备，扫了 25 秒，一堆手环/家电，零命中 `wbMSE` 前缀。

**根因**：**这类 2.4G 玩具是纯接收端，根本不广播**。它只听空口上 `77 62 4D 53 45 <cmd>`。

**佐证**：`getdevicelist` 返回的设备里 `ServiceUuid`/`WriteUuid` 全空，`Wireless: "2.4g"`，`BroadcastPrefix` 是**型号级常量**（9333 恒为 `wbMSE`）而不是设备 MAC —— 说明它没有可被发现的身份。

**教训**：BLE 不等于「双向」。判断一个设备是可扫描外设还是单向接收器，看它有没有 ServiceUuid / 有没有广播自己的 MAC。

---

## 坑 4：一发指令 App 就闪退

**现象**：`/pulse` 请求返回空，之后整个 API 端口消失。

**根因**：HTTP 连接线程直接调了 `BluetoothLeAdvertiser.startAdvertising()`。部分 ROM 的蓝牙栈要求主线程，抛出的异常没人接，进程直接死。

**修法**：`Gear.send()` 里判断 `Looper.myLooper() != Looper.getMainLooper()` 就 post 到主线程；整条发送链兜 `Throwable`，异常只写进 `note` 不杀进程。

**教训**：Android BLE 的 API 表面 thread-safe，实际不是。跨线程调用要么强制 marshal，要么准备好进程被打死。

---

## 坑 5：WinRT 广播器带 ServiceUUID 起不来

**现象**：Windows 侧复现时，`manufacturer_data[255] = buf` 直接 `E_NOINTERFACE`；改用 `adv.service_uuids.append()` 能过，但 `publisher.start()` 抛 `E_INVALIDARG`。

**根因**：pywinrt 对 `IMap<UInt16, IBuffer>` 的投影有缺陷，赋值走不通；而**同时**加 ServiceUUID + 自定义 AD 结构在这块 MediaTek 网卡上不被接受。

**修法**：改用底层 `BluetoothLEAdvertisementDataSection`：

```python
sec.data_type = 0xFF                       # Manufacturer Specific Data
w.write_bytes(b"\xff\x00" + payload)       # companyId 小端 + payload
adv.data_sections.append(sec)
```

不附带 ServiceUUID（玩具只解析 manufacturer data，不影响功能）。

**教训**：厂商 SDK 的高层 API 不可靠时，退到底层 AD 结构；另外 **Windows 上要先把蓝牙射频打开**（`RadioState=2` 时一切 start 都会 `E_INVALIDARG`，报错完全不提示这一点）。

---

## 坑 6：把 `0x10` 当成音乐帧

**现象**：早期实现「音乐模式」发 `{0x10, level}` 两字节，设备不动。

**根因**：`0x10` 是**单字节模式码**，语义是「低音量/静音心跳」，唯一出处：

```java
// AiSoundInteractionFragment:119
if (minVoice) startAdvertising(255, BleUtils.getBleCommand("10"));
```

音乐模式**永远不产生 `0x10`**（gear≤0 走停止 `00`）。音乐档位是 `0x11`–`0x19` 和 `0x20`。

**教训**：同一个字节在不同模式里可能有完全不同的语义，必须按「谁发的、什么条件下发的」归类，不能按「看起来像」归类。

---

## 坑 7：`0x1A`–`0x1F` 根本不存在

**现象**：以为 10 档是 `0x11`–`0x1A` 连续，照着表发 `0x1A` 当第 10 档。

**根因**：这是个**十进制串被当十六进制解析**的经典坑：

```java
String.valueOf(clamp(gear + 11, 11, 20))   // 产出 "11".."19","20"（十进制字符串）
→ strToByte("20")                          // 按 HEX 解析 → 0x20
```

所以第 9 档是 `0x19`，第 10 档是 **`0x20`**，中间的 `0x1A`–`0x1F` 没有任何静态产出方。

**教训**：看到「数字拼成字符串再解析」的链路，一定先确认基数。这类坑在协议逆向里极其常见。

---

## 坑 8：命令表查不到时，代码悄悄发了废帧

**现象**：加了 `0A01`（GATT 专用）进 2.4G 表后，某些命令「看起来发了」但设备不动，日志还显示成功。

**根因**：`frame()` 的兜底是「查不到就返回裸 `前缀+命令`」—— 没有位反转、没有白化、没有 CRC。设备收到就是垃圾，且不会报错。

**修法**：查不到就**不发了**，明确回错：

```java
if (Rf.payloadFor(hex(cmd)) == null) {
    lastNote = "NO_FRAME:" + hex(cmd);
    lastCode = -4;
    return;
}
```

**教训**：兜底逻辑是静默失败的温床。宁可显式失败，也不要发一个「格式合法但语义无效」的包。

---

## 坑 9：jadx 反编译失败，方法体变成 `throw UnsupportedOperationException`

**现象**：`InteractBleHelper.f(Intent)` 在 jadx 里是：

```java
throw new UnsupportedOperationException("Method not decompiled: ...")
```

`SwitchOverStringVisitor` 抛异常导致整个方法被替换。

**修法**：用 **baksmali 3.0.10** 反汇编 `classes5.dex`，直接读 smali：

```bash
java -jar baksmali.jar d classes5.dex -o smali5
# 目标：smali5/com/maxhom/weibu/ui/device/play/InteractBleHelper.smali
# 方法 .method public final f(Landroid/content/Intent;)V  → 行 291–1544
```

里面是 `packed-switch` + `array-data`，很直白地读出了 `"0"→0x49`、`"9"→0x39` 的**倒序**映射，以及 GATT 路径的 20 个 2 字节帧。

**教训**：jadx 失败不代表代码不可读，smali 是最终真相。尤其 `switch-over-string` 这种优化，jadx 容易崩。

---

## 坑 10：以为音乐模式在算鼓点

**现象**：宣传语是「旋律识别、跟随音乐震动」，我先按 beat detection 做了一版，发现和原厂行为对不上。

**真相**：`setDataCaptureListener(..., true, false)` —— 第四参 **fft=false**，`onFftDataCapture` 是空壳。算法只有标准差：

```java
σ = sqrt(mean((x-μ)²))
gear = floor((min(σ,80)/80)² × 10)
```

全包里 `getFft` 0 命中、`bpm` 0 命中。而 `libaudioeffect.so` 里确实躺着完整的 `sami::BeatTrackingFeature`、`DBNDownBeatTrackingProcessor`、TF 模型 —— **买了没接线**，Java 层 0 处 `loadLibrary("audioeffect")`。

**教训**：宣传语不是规格。逆向时以代码为准，尤其要区分「这个 SDK 被依赖了」和「这个 SDK 被调用了」。

---

## 坑 11：Visualizer(0) 在部分机型上静默降级

**现象**：用户反馈「跟音乐没反应」，但 UI 显示在聆听。

**根因**：原厂 `VisualizerHelper` 失败时**静默**降级为麦克风（`MediaRecorder` + `getMaxAmplitude()`），UI 文案不变，用户无法分辨。

**MuseGear 的做法**：`/status` 里显式暴露 `audio.src`（`visualizer` / `microphone`），降级立刻可见。

**教训**：降级路径必须可观测。静默降级等于让用户去猜。

---

## 坑 12：API「回传成功」不等于「设备动了」

**现象**：`/status` 显示 `gear:0`（成功），但没感觉。

**根因层次**：一次下发要过五关，任何一关断了都可能表现为「成功但不动」：

```
1. 命令是否在表里       → 查不到会发废帧（坑 8）
2. 帧是否白化+CRC 正确  → 明文发不动（坑 1）
3. PDU 是否 connectable → 非连接型被丢（坑 2）
4. 广播器是否被占用     → 暮瑟 App 在跑时回调码 3/4
5. 设备是否在射频范围   → 距离 >5–8 米基本收不到
```

**修法**：把失败码原样暴露出来（`0/1/2/3/4/-2/-3/-4`），并把每次实际发出的 `payload` 回传，让「失败在第几关」可以直接定位。

**教训**：可观测性不是锦上添花。这类问题没有日志就是纯玄学。

---

## 坑 13：长连接/熄屏后不动了

**现象**：手机锁屏几分钟后 API 失联或不再下发。

**根因**：Android 后台限制 + Doze。广播器和 HTTP 服务都会被挂起。

**修法**：前台服务（`foregroundServiceType=connectedDevice`）+ `PARTIAL_WAKE_LOCK` + 每 30 秒 `keepWarm()` 心跳，并申请忽略电池优化。

**教训**：涉及硬件的长时任务，必须走前台服务；且心跳要打日志，否则「什么时候断的」永远查不到。
