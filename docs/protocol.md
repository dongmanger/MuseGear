# 空口帧协议

设备通过 BLE 广播接收指令。**帧长 11 字节**（单字节命令），由 `libble.so` 的 `get_rf_payload` 生成。

## 一、Java 侧入口

```java
// BleUtils.java:74
private static final String RAW_ADDRESS = "77 62 4d 53 45";   // ASCII "wbMSE"

// BleUtils.java:345  getBleCommand(String) —— 单参入口
// 只有 isPrivateProtocol()==1 且设备自带前缀非空时才替换前缀，否则恒用 RAW_ADDRESS

// BleUtils.java:126  getBleCommand(prefix, command)
int i8 = length + length2 + 5;      // 前缀字节数 + 命令字节数 + 5
byte[] bArr3 = new byte[i8];
BLEUtil.get_rf_payload(bArr, length, bArr2, length2, bArr3);
```

命令字符串按 **hex** 解析（去空格、转小写、不足 2 字符左补 `0`）。所以 `"11"` → `0x11`，`"0a01"` → 两字节 `0x0A 0x01`。

## 二、native 侧算法（`libble.so`）

JNI 符号：

```
Java_com_example_nirjon_bledemo4_1advertising_util_BLEUtil_get_1rf_1payload   @ 0x8AC
_Z11check_crc16PKciS0_i      @ 0xC68    CRC16
_Z14whitening_initiPi        @ 0xE60    白化状态初始化
_Z17whitenging_encodePciPi   @ 0xEC4    白化（keystream）
_Z8invert_8c                 @ 0xBD0    8bit 位反转
_Z9invert_16t                @ 0xC24    16bit 位反转
```

构造过程（`pl` = 前缀字节数，`pyl` = 载荷字节数，内部缓冲 `total = pl + pyl + 20`）：

```
buf[15] = 0x71 ; buf[16] = 0x0F ; buf[17] = 0x55
buf[18 + i]                = prefix[pl-1-i]        # 前缀倒序
buf[18+pl ... 18+pl+pyl-1] = payload[]            # 原序
buf[18+pl+pyl]             = crc & 0xFF
buf[18+pl+pyl+1]           = (crc >> 8) & 0xFF

# 对 buf[15 .. 17+pl] 逐字节 8bit 位反转（invert_8）
# whitening_encode(buf+18, pl+2+pyl, state(ch=0x3F))
# whitening_encode(buf+0,  total,     state(ch=0x25))
# 输出 out[k] = buf[15+k]，共 pl+pyl+5 字节
```

白化是纯 keystream（7 个状态位更新与输入字节无关），所以缓冲前 15 字节未初始化不影响结果。

CRC16：init `0xFFFF`，poly `0x1021`；前缀**倒序**逐字节、载荷每字节先 **8bit 位反转**再逐字节；收尾 `crc = (~bitrev16(crc)) & 0xFFFF`。

## 三、可自检的帧结构

单字节命令的 payload 形状固定，**不需要跑 native 也能验**：

```
payload[0..7]  = 6D B6 43 CE 97 FE 42 7C      固定头（71/0F/55 + 倒序前缀，双次白化后定值）
payload[8]     = cmd ^ 0xE5                    命令异或
payload[9..10] = CRC16 低字节在前
```

抽验：`00→E5`、`05→E0`、`19→FC`、`20→C5`、`30→D5`、`41→A4`、`50→B5`。

两字节命令时 `payload[9] = cmd1 ^ 0x1D`（如 `0A01 → EF 1C B4 28`，12 字节）。

注意：**换前缀会让前 8 字节全变**（实测 `...346` 前缀 → `6DB6430E97FE427C…`），所以不能只改命令字节。

## 四、广播参数

```java
AdvertiseData.Builder()
  .addServiceUuid("0000ae8f-0000-1000-8000-00805f9b34fb")
  .addManufacturerData(255, payload)        // companyId 恒 255
```

```java
type==0  → setAdvertiseMode(1) BALANCED   + setTimeout(1500)
type!=0  → setAdvertiseMode(2) LOW_LATENCY + setTimeout(3000)
两者均   setConnectable(true) + setTxPowerLevel(3) HIGH
```

**`setConnectable(true)` 是硬要求**：它决定发的是 `ADV_IND`（0x00）而不是 `ADV_NONCONN_IND`（0x02）。用非连接型广播时，Android 回调照样返回成功，但设备按 PDU 类型过滤直接丢包 —— 这是"显示成功但不动"的头号原因。

## 五、命令全表

### 5.1 经典 9 档

| cmd | 语义 |
|---|---|
| `00` | 停 |
| `01`–`09` | 档位 1–9 |

### 5.2 10 档体系（音乐 / 触摸板 / 摇一摇 / 游戏共用）

| cmd | 语义 |
|---|---|
| `11`–`19` | 档位 1–9 |
| `20` | 档位 10（满档） |

> 注意 `0x1A`–`0x1F` **不是**档位：`String.valueOf(clamp(g+11,11,20))` 产生十进制串，再按 hex 解析，所以第 10 档是 `0x20` 而不是 `0x1A`。

### 5.3 其它

| cmd | 语义 | 出处 |
|---|---|---|
| `10` | 低音量/静音心跳（**不是**音乐帧） | `AiSoundInteractionFragment:119` |
| `24` | 互动保活 / 加热关 | `InteractBleHelper:59,72` |
| `25` | 加热开 | `VibratorEggClassicActivity:197` |
| `26`/`27` | 放电 关/开 | `ManipulateActivity:935` |
| `28`/`29` | 声音触发 关/开 | `ManipulateActivity:991` |
| `30` | 波形通道停 | `WaveShockCodeResolver:86` |
| `31`–`39` | 波形/互通通道 1–9 开 | `WaveShockChannelFactory:44` |
| `40` | 扩展通道停 / 弧1 归零 | 同上 `:110` |
| `41`–`49` | 扩展通道 11–19 开（41–45 亦为弧1 压力） | `:123` |
| `50` | 弧2 压力归零 | `ClassicModeFragment:739` |
| `51`–`55` | 亮度 1–5（弧2 机型同字节为压力） | `ClassicModeFragment:569-577` |
| `11`–`6E` | 压力条扫描区（`String.valueOf(i+10)`，i 最大 100） | `ClassicModeFragment:757` |

### 5.4 单人节奏 `AloneRhythmPattern`

输入 `AloneRhythmData`，**倒序**映射（`InteractBleHelper.f(Intent)`，baksmali 反汇编所得）：

| 输入 | 广播 cmd | GATT 2 字节 |
|---|---|---|
| `"0"` | `49` | `[0x49,0x00]` |
| `"1"` | `48` | `[0x48,0x00]` |
| `"2"` | `47` | `[0x47,0x00]` |
| `"3"` | `46` | `[0x46,0x00]` |
| `"4"` | `45` | `[0x45,0x00]` |
| `"5"` | `44` | `[0x44,0x00]` |
| `"6"` | `43` | `[0x43,0x00]` |
| `"7"` | `42` | `[0x42,0x00]` |
| `"8"` | `41` | `[0x41,0x00]` |
| `"9"` | `39` | `[0x39,0x00]` |
| `"11"`–`"19"` | 广播不产 | `[0x38,0x00]`…`[0x30,0x00]` |
| `"20"` | 广播不产 | `[0x00,0x00]` 停 |
| `"30"` | 广播不产 | `[0x40,0x00]` |

广播模式下发后原厂还会在 **1 秒后补发 `0x10`** 回落（`q()` 语义）；`r()` 语义为只发不回落。

## 六、GATT 链路（备用）

`noBleBluetooth=false` 时走 BLE 写入，服务 `0000aaa0-…` / 特征 `0000aaa1-…`，固定 2 字节帧：

| 帧 | 语义 |
|---|---|
| `[level, 0x01]` | 档位 / 摇一摇 / 游戏 / 音乐 / 划一划 |
| `[level, 0x00]` | `getBleCommand2()` 形态 |
| `[0x00, 0x00]` | 停 |
| `[0x0A, 0x01]` | 互动高潮 |
| `[0x20, 0x00]` | 加密款（`isCryptBle`）高潮 |
| `[rawPressure/20, 0x01]` | 凯格尔（仅 GATT） |

## 七、完整帧表

`data/rf_table_full.json` 收录了 `0x00`–`0x6E` 共 111 条命令的真实 11 字节 payload，由 Unicorn 仿真 `get_rf_payload` 生成，与真机代码逐字节一致。

复现：

```bash
python tools/rf_gen.py 77624d5345 05
# → 6DB643CE97FE427CE0B82A
```
