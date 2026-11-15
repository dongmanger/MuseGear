# 「暮瑟」Muse v5.0.2 — BLE 命令空间逐字节核对报告

审计对象：`Muse_v5.0.2.apk`、`jadx_out\sources`、`libble_arm64.so`、`rf5.py`、`rf_table.json`
对照成品：`apk\src\com\musegear\{Gear.java, Rf.java}`
产出目录：`audit\`（全程未修改 `apk\` 与任何原始材料）

可复跑脚本（全部产出落在 `audit\`）：

| 脚本 | 作用 | 产物 |
|---|---|---|
| `audit\collect_cmds.py` | 穷举 `getBleCommand*` 字面量实参 | 控制台 |
| `audit\branch_scan.py` | `isPrecise/getCateId/isPrivateProtocol/isCryptBle/isEncrypt/getBroadcastPrefix/getEncryptCommand/isGattBle` 全量使用点 | `branch_scan.log` |
| `audit\rf_batch.py` | 全空间跑 native 仿真（每条 2 遍）+ 与 `rf_table.json` 自动比对 | `rf_full.json`、`rf_diff.json`、`rf_batch.log` |
| `audit\cmd_space.py` | 按源码证据重算命令空间、分类 缺/多/外推 | `cmd_space.json`、`cmd_space.log` |
| `audit\gen_patch.py` | 生成可直接粘贴的 Java 补丁块 | `Rf_gap.java.txt`、`rf_missing_table.txt` |
| `audit\dis_native.py` / `dis_native2.py` | `get_rf_payload` 结构反汇编 | 控制台 |

---

## 0. 结论摘要（8 条）

1. **编码没错，缺的是覆盖面。** `rf_table.json` 的 27 条表项（24 个唯一命令）payload 与 native 仿真**逐字节一致：match=27 / mismatch=0 / table_only=0**（`audit\rf_diff.json`）。表不是算错，是没收全。
2. **缺 30 条有源码实证的命令**：`0x11–0x19`（10 档体系档位 1–9，9 条）、`0x30`、`0x31–0x39`（互控通道 1–9，9 条）、`0x40`、`0x41–0x49`（互控通道 11–19，9 条）、`0x50`（压力档停止）。全部用 `rf5.py` 跑出真实 payload（§3.1）。
3. **多 1 条：`0A01`。** 全工程没有任何空口路径发 2 字节命令；`0A 01` 只出现在 **GATT 写** `new byte[]{10, 1}`（`InteractBleHelper.java:399`，非 cryptBle 设备释放）。收进 RF 表属**语义串台**。
4. **档位字节空间要更正**：10 档体系的实际字节是 **`0x11–0x19` 加 `0x20`**，不是连续的 `0x11–0x20`。因为 `SpecialGearCommandMapper.b(int)` 产出的是十进制字符串 `"11".."19","20"`，再被 `strToByte` **按十六进制**解析，所以档位 10 = `0x20`（不是 `0x1A`）。`0x1A–0x1F` 无任何静态产出方（见 §2.3、§6）。
5. **语义标错**：`gear00–gear09` 在 9 档设备上确实是档位/节奏码，但它**不是**"10 档体系的档位 0–9"；`51–55` 一名两用（亮度 **和** arcType=2 压力档），靠广播 type 与机型区分；`20` 三义（档位10／互动启动／越界兜底）。
6. **`isPrecise` 在本版本不存在**：8 个 `.dex` 原始字节搜索 `Precise`/`IsPrecise` 命中 **0**。该字段没有实现，无法影响任何格式。
7. **真正改变"发什么"的字段**：`isPrivateProtocol()`（换前缀 → 整帧变化）、`isGattBle()`/`noBleBluetooth`（换传输：广播 vs GATT 写）、`isCryptBle()`（换加密 4 字节 GATT 协议）。`getCateId()==3` 与 `isEncrypt` **不参与**编码。
8. **成品 `Gear.java` 三处实伤**：`music()` 发 2 字节 → 查表必 miss → 落到 `frame()` 兜底（**未 invert/未白化/未 CRC 的裸帧**，设备解析不了）；`strength()` 只覆盖 `51–55`（漏 `11–15`、`01–03`、`41–45`）；`level()` 只覆盖 `00–09`（漏 `11–19`/`20`）。

---

## 1. 穷举命令来源（含动态拼接）

### 1.1 唯一编码入口（已确认）

`BleUtils.getBleCommand(String)` → `getBleCommand(prefix, command)` → `BLEUtil.get_rf_payload(prefix, len, cmd, len, out)`；输出长度 = `prefixLen + cmdLen + 5`。

| 事实 | 证据 |
|---|---|
| 通用前缀常量 `"77 62 4d 53 45"` | `common\bluetooth\BleUtils.java:74` |
| 前缀仅在 `isPrivateProtocol()==1` 且非空时被 `BroadcastPrefix` 替换 | `BleUtils.java:348-354`、`:361-369` |
| 前缀合法 3–5 字节，否则 toast 并返回空数组 | `BleUtils.java:132-135` |
| 命令按 **十六进制字符串** 解析（非十进制） | `BleUtils.java:136-167`、`charToByte/strToByte` `:100-102`、`:312-321` |
| 命令**奇数字节长度会静默截断**最后一个半字节：`"123"` → 只发 `0x12` | `BleUtils.java:159-167`（`length2 = len/2` 取整） |
| 命令含非 hex 字符 → `indexOf` 返回 -1 → 发出 `0x?F` 垃圾 | `BleUtils.java:101`、`:316-318` |
| GATT 路径用 `getBleCommand2()`，**十进制**解析 + 补 1 个 0 字节 | `BleUtils.java:177-179` |
| 广播 vs GATT 由 `PreferenceUtil.getBoolean("noBleBluetooth", true)` 决定（默认广播） | `BleUtils.java:302-309` |

### 1.2 广播参数（不改命令字节，但决定能否被收到）

| 调用形态 | 实际 settings | 证据 |
|---|---|---|
| `startAdvertising(1|2, 255, data)` | `createAdvSettings2()`：mode=2(LOW_LATENCY)、connectable=true、timeout=3000ms、txPower=3 | `BeaconManager.java:611-613`、`:544-553` |
| `startAdvertising(255, data)` → `(0,255,data)` | `createAdvSettings()`：mode=1(BALANCED)、connectable=true、timeout=**1500ms**、txPower=3 | `BeaconManager.java:583-585`、`:529-538` |
| 数据通道固定为 `addServiceUuid("0000ae8f-…")` + `addManufacturerData(manufacturerId=255, data)` | `BleUtils.createIBeaconAdvertiseData` `BleUtils.java:107-112`；调用 `BeaconManager.java:444` |
| 相同表项只刷新超时，不同表项排队切换（解释"先 00 再 24"的序列） | `BeaconManager.java:495-527` |

> 结论：首参 `0/1/2` 只切广播 profile，**不改变 payload**；`manufacturerId` 恒为 `0xFF`。

### 1.3 静态字面量实参（穷举，`audit\collect_cmds.py`）

| 命令 | 语义 | `文件:行号` |
|---|---|---|
| `"00"` | 停止／归零／会话结束（16 处） | `device\manipulate\DeviceManipulateBleHelper.java:38`；`ui\device\StandaloneModeBleHelper.java:51,59`；`ui\device\play\ClassicModeFragment.java:818,820,865,867`；`InteractBleHelper.java:334,347`；`VibratorEggClassicActivity.java:213`；`VideoShockModeFragment.java:310,312`；`SpecialHardwareCommandDispatcher.java:262,276`；`WaveShockCodeResolver.java:45` |
| `"01"–"09"` | 节奏/振动模式 1–9；9 档设备的档位 1–9 | `InteractBleHelper.java:268,271,274,277,280,283,286,289,292` |
| `"10"` | 声音跟随/音乐模式码 | `InteractBleHelper.java:77`；`AiSoundInteractionFragment.java:119` |
| `"20"` | 档位 10（逻辑串 `"20"`）／互动启动／越界兜底 | `InteractBleHelper.java:394,396`；`SpecialGearCommandMapper.java:28,32-53,59`；`SpecialHardwareCommandDispatcher.java:174,391` |
| `"24"` | 加热关／待机底座态 | `DeviceManipulateBleHelper.java:27`；`InteractBleHelper.java:59,72`；`VibratorEggClassicActivity.java:80,175`；`ManipulateActivity.java:944`（关分支） |
| `"25"` | 加热开（`Constants.VIA_REPORT_TYPE_CHAT_AUDIO`） | `VibratorEggClassicActivity.java:197`；`ManipulateActivity.java:944` |
| `"26"` | 放电/电击 **关**（`VIA_REPORT_TYPE_CHAT_VIDEO`="26"） | `ManipulateActivity.java:933,1000` |
| `"27"` | 放电/电击 **开** | `ManipulateActivity.java:933,1000` |
| `"28"` | 声控 **关**（`VIA_ACT_TYPE_TWENTY_EIGHT`="28"） | `ManipulateActivity.java:989` |
| `"29"` | 声控 **开** | `ManipulateActivity.java:989` |
| `"30"` | 互控隐藏态停止（bleStopHex） | `WaveShockCodeResolver.java:86`；`WaveShockChannelFactory.java:44,63` |
| `"40"` | 互控/压力（arcType=1）停止 | `WaveShockCodeResolver.java:86`；`WaveShockChannelFactory.java:73,114`；`ClassicModeFragment.java:741` |
| `"50"` | 压力档（arcType=2，barcode 4818/9947）停止 | `ClassicModeFragment.java:739` |
| `"51"–"55"` | 亮度 1–5；**同字节组**亦为 arcType=2 压力档 1–5 | `ClassicModeFragment.java:569,571,573,575,577`、`:760`；`InteractBleHelper.java:298,304,310,316,322` |
| 腾讯 `Constants` 被当命令用 | `25/26/28`、`17/18/19` | `Constants.java:147,150,157,159,160,170`；`SpecialGearCommandMapper.java:44,48,130,134`；`WaveShockIds.java:116,117` |

### 1.4 动态拼接来源（不补这些，表永远补不全）

| 生成式 | 实际字节 | 语义 | `文件:行号` |
|---|---|---|---|
| `String.valueOf(i4 + 10)`，i4=1..9，越界→`"20"` | `0x11..0x19`（+`0x20`） | 游戏/划一划档位（legacy 设备） | `SpecialHardwareCommandDispatcher.java:391`、`:174` |
| `String.valueOf(i4 + 40)`，i4=1..5 | `0x41..0x45` | 压力档（arcType=1） | `ClassicModeFragment.java:751` |
| `String.valueOf(i4 + 50)`，i4=1..5 | `0x51..0x55` | 压力档（arcType=2） | `ClassicModeFragment.java:760` |
| `String.valueOf(i4 + 10)`，arcType=0 | `0x11..0x15` | 压力档（普通 5 档） | `ClassicModeFragment.java:753-757` |
| `String.valueOf(coerceIn(i4+11, 11, 20))` | `0x11..0x19`+`0x20` | 10 档逻辑档位（special 协议入口） | `SpecialGearCommandMapper.java:59` |
| `a(String)` / `f(int)` 固定表 | `"0"→0x11 … "8"→0x19, 其它→0x20` | 拨动/摇一摇档位 | `SpecialGearCommandMapper.java:32-51`、`:117-137` |
| `e(String)` | 只留数字、左补 `0` 到 2 位 | 逻辑值归一化 | `SpecialGearCommandMapper.java:90-112` |
| `d(String)` | `11–13→0x01`、`14–16→0x02`、`17–20→0x03`（按十进制比较） | **10 档压成 3 档**，仅 barcode `0213/4180/3127` | `SpecialGearCommandMapper.java:80-86`；白名单 `SpecialBindCodeSupport.java:24`；落库路径 `SpecialHardwareCommandDispatcher.java:227` + `$scheduleFlushLocked$1.java:82` |
| `String.format("%02d", i4)` | `"00"..`（索引） | 独立模式波形选择 | `StandaloneModeBleHelper.java:65` |
| `String.format("%02X", n + 48)` | `"1".."9"`→`0x31..0x39`；`"11".."19"`→`0x41..0x49`；特例 `"9"`→`0x49` | **互控 IM→BLE 映射核心** | `WaveShockChannelFactory.java:120-127` |
| `String.format("%02X", i + 48)` | `i=1..9` → `0x31..0x39` | 互控通道码表 | `WaveShockChannelFactory.java:43-48`、`:65-70` |
| `String.format("%02d", i5 + 1)` | `0x01..0x09` | 高潮档/单档 | `WaveShockCodeResolver.java:58` |
| `String.format("%02d", i)` / `String.valueOf(i)` | `0x01..0x0N` / IM `"1".."9"` | 波形通道工厂 | `WaveShockChannelFactory.java:135-152` |
| `imCode.padStart(2,'0')` | 任意 IM 数据（≤2 字符原样） | 对手端下发码直接当命令 | `BleWaveShockSender.java:101,128`；`TelescopicBleSender.java:38` |
| `Byte.parseByte(cmd)` + `{cmd,1}` | 十进制 0–127（**十进制≠hex**） | AI 桥 GATT 写 | `AiBridgeShellHelper$createDevPort$1.java:160` |
| `Integer.parseInt(str,16)` | hex | GATT 写字节 | `BleUtils.java:178`；`SpecialHardwareCommandDispatcher.java:351` |
| **H5 JS 桥 `singleShock(String)`** | 任意 2 字符，**无白名单** | 直接 `sendControlCommand` | `web\WebJsBridgeHelper.java:318-326`（`:321` 只校验 `length==2`） |
| **H5 延时补发 `O()`** | 十进制 `11..20` 才补发 | 与上一行差 10 倍语义 | `WebJsBridgeHelper.java:500-513` |
| **JS 桥 `MineScanPort.c`** | 任意字符串 | 直接 `sendControlCommand` | `mine\MineBridgeScanHelper$createPort$1.java:78` |
| **AI 桥 `AiDevPort.v`** | 任意字符串 | `getBleCommand(deviceBean, cmd)` | `ai\bridge\AiBridgeShellHelper$createDevPort$1.java:160` |
| **剧情 `PlotBlePort.a(String)`** | 服务端下发 | `getBleCommand(str)` | `plot\bridge\PlotBridgeShellHelper.java:58` |
| **经典模式 `Mode.newCommand/stopCommand`** | 服务端 JSON 任意 hex | 直接当命令 | `common\bean\play\Mode.java:25-31`；`ClassicModeBleHelper.java:50,52,72,74`；`PlotDeviceCmdHelper.java:75,81` |
| **IM `CustomControlData`** | 对手端任意 hex | `g(...)` 直接发 | `InteractChatSession.java:406` → `InteractBleHelper.java:211-217` |
| **IM `AloneRhythmData`** | `"0".."9"`/`"11".."19"`/`"20"`/`"30"` | 单机节奏（映射细节未闭环，见 §6 推测） | `InteractChatSession.java:434` → `InteractBleHelper.java:203-209`（jadx 未反编译） |
| **IM `RhythmPatternData`** | `"1".."9"`/`"51".."54"`，`"0"`=停 | 节奏模式 | `InteractBleHelper.java:249-329` |

---

## 2. 差异表

自动比对：`audit\rf_diff.json`（match=27 / mismatch=0 / table_only=0）、`audit\cmd_space.json`（evidenced=53 / missing=30 / table_only=[0A01] / extrapolated=6）。

### 2.1 缺失 — 30 条（有源码实证，真机会发）

| 命令 | 语义 | 触发条件 / 证据 |
|---|---|---|
| `0x11`–`0x19`（9 条） | 10 档体系 档位 1–9 | `SpecialGearCommandMapper.java:59`（`coerceIn(i+11,11,20)`）、`:32-51`、`:117-137`；`SpecialHardwareCommandDispatcher.java:174,391`；`ClassicModeFragment.java:751-757`；`WebJsBridgeHelper.java:500-513`（H5 只补发 11..20） |
| `0x30` | 互控隐藏态停止（useAloneRhythm 模式） | `WaveShockCodeResolver.java:86`；`WaveShockChannelFactory.java:44,63` |
| `0x31`–`0x39`（9 条） | 互控通道 1–9（IM `"1".."9"`；特例 `"9"` 也落 `0x49`） | `WaveShockChannelFactory.java:120-127`、`:43-48`、`:65-70` |
| `0x40` | 互控/压力停止（非隐藏态） | `WaveShockCodeResolver.java:86`；`WaveShockChannelFactory.java:73,114`；`ClassicModeFragment.java:741` |
| `0x41`–`0x49`（9 条） | 互控通道 11–19（IM `"11".."19"`）；`41–45` 与 arcType=1 压力档**同字节** | `WaveShockChannelFactory.java:120-127`；`WaveShockIds.java:116`；`ClassicModeFragment.java:751` |
| `0x50` | 压力档停止（arcType=2） | `ClassicModeFragment.java:739` |

### 2.2 多余 / 语义可疑

| 表项 | 问题 | 判定 |
|---|---|---|
| `i0a01`（`0A01`，2 字节） | 无任何空口路径；`{0x0A,0x01}` 只见于 GATT 写 `new byte[]{10,1}`（`InteractBleHelper.java:399`） | **多余（串台）**：删除或标注 "GATT-only" |
| `gear00`–`gear09` | 字节合法（`00`=停、`01..09`=9 档/节奏），但命名为"gear 档位 0–9"会与 10 档体系（`11–19`/`20`）撞概念 | **改名** `pattern00..09` |
| `preheat` / `heatOff` / `w36` 三条同为 `24` | `0x24` = 加热关/待机底座；"preheat" 无源码依据 | **合并 + 改名** |
| `interact` = `20` | 同时是档位 10 与越界兜底 | 保留，注明三义 |
| `str1..str5` = `51..55` | 实为亮度 1–5，且与 arcType=2 压力档共用 | **改名** + 注明双义 |
| `music` = `10` | 源码中 `10` 是单字节模式码，没有子档；成品 `Gear.music(lv)` 却发 2 字节 | 表保留，**成品用法错** |

### 2.3 外推（付 payload，但不作为"已证实命令"）

| 命令 | 判定 |
|---|---|
| `0x1A`–`0x1F`（6 条） | **无静态产出方**。档位表 `coerceIn(i+11,11,20)` 只到 `"20"`；`String.valueOf(i4+10)` 有 `i4>=10 → "20"` 的守卫（`SpecialHardwareCommandDispatcher.java:174,391`）。仅服务端 `Mode.newCommand` 下发的 4-hex 串可能踩到。payload 已算好（`Rf_gap.java.txt` 末尾注释块），**默认不启用** |

### 2.4 命名/文档层面的偏差（本次误收 `0A01` 的根因）

表里混用两套进制：`gear00–09`、`str1–5`、`i0a01` 是 **hex 字面**；`w36/w37`、`off38/on39`、`off40/on41` 是 **十进制序号**（36→0x24、41→0x29）。补表时必须以 hex 字面 + 语义 + `文件:行号` 三列维护。

---

## 3. 仿真校验（rf5.py 原样输出）

环境：`python 3.12.10` / `unicorn 2.1.4`。命令：`python rf5.py 77624d5345 <hex>`。原始日志：`audit\rf5_missing.log`、`audit\rf_batch.log`。

> **rf5.py 的显示缺陷（已确认）**：脚本读 `len(P)+len(C)+8` 字节，故打印 `(14 bytes)` 且尾部带 `000000` 补零。**真实 payload 长度 = prefixLen+cmdLen+5 = 11 字节**（2 字节命令为 12）。下表已剔除补零，与 `rf_table.json` 的 11 字节口径一致；`audit\rf_batch.py` 按 `prefixLen+cmdLen+5` 取值，`len=11` 已逐条打印。

### 3.1 缺失命令的真实 payload（30 条，11 字节）

```
11  6DB643CE97FE427CF41D7C      30  6DB643CE97FE427CD5964C
12  6DB643CE97FE427CF7864E      31  6DB643CE97FE427CD41F5D
13  6DB643CE97FE427CF60F5F      32  6DB643CE97FE427CD7846F
14  6DB643CE97FE427CF1B02B      33  6DB643CE97FE427CD60D7E
15  6DB643CE97FE427CF0393A      34  6DB643CE97FE427CD1B20A
16  6DB643CE97FE427CF3A208      35  6DB643CE97FE427CD03B1B
17  6DB643CE97FE427CF22B19      36  6DB643CE97FE427CD3A029
18  6DB643CE97FE427CFDDCE1      37  6DB643CE97FE427CD22938
19  6DB643CE97FE427CFC55F0      38  6DB643CE97FE427CDDDEC0
                                39  6DB643CE97FE427CDC57D1
40  6DB643CE97FE427CA5113F      46  6DB643CE97FE427CA3275A
41  6DB643CE97FE427CA4982E      47  6DB643CE97FE427CA2AE4B
42  6DB643CE97FE427CA7031C      48  6DB643CE97FE427CAD59B3
43  6DB643CE97FE427CA68A0D      49  6DB643CE97FE427CACD0A2
44  6DB643CE97FE427CA13579      50  6DB643CE97FE427CB5902F
45  6DB643CE97FE427CA0BC68
```

`rf5.py` 原样片段（节选；完整见 `audit\rf5_missing.log`）：

```
### rf5.py 77624d5345 19
[ok]
prefix=77624D5345 cmd=19
RF PAYLOAD (14 bytes): 6DB643CE97FE427CFC55F0000000
### rf5.py 77624d5345 30
[ok]
prefix=77624D5345 cmd=30
RF PAYLOAD (14 bytes): 6DB643CE97FE427CD5964C000000
### rf5.py 77624d5345 41
[ok]
prefix=77624D5345 cmd=41
RF PAYLOAD (14 bytes): 6DB643CE97FE427CA4982E000000
### rf5.py 77624d5345 50
[ok]
prefix=77624D5345 cmd=50
RF PAYLOAD (14 bytes): 6DB643CE97FE427CB5902F000000
```

### 3.2 确定性

- 同一命令**同进程跑两遍**：`audit\rf_batch.py` 对 62 个输入比对 `payload == payload2` → 全 `det=True`。
- 同一命令**独立进程跑 3 遍**（`python rf5.py 77624d5345 30` ×3）→ 三行完全相同 `6DB643CE97FE427CD5964C`。
- 结论：**非加密广播路径（`isCryptBle()==false`）是纯确定性函数**，可离线预生成整表。
- 例外（不定）：`isCryptBle()` 设备走 4 字节加密帧，含 `Random` 随机化（`CryptBleUtils.java:170-174`、`:69`、`:588`），本表不适用。

### 3.3 payload 结构（反汇编确认，可用于免仿真生成/自检）

`Java_..._BLEUtil_get_1rf_1payload @ 0x8AC`（`.text` 从 `0x870` 起）实际动作：

1. 分配 `prefixLen + cmdLen + 0x14` 工作区（`0x0960-0x0974`）；
2. 偏移 `0x0F/0x10/0x11` 写同步字 `0x71, 0x0F, 0x55`（`0x097c-0x098c`：`#0xf71` 半字 + `#0x55`）；
3. 偏移 `0x12` 起**倒序**拷 prefix（`0x0994-0x09a8`，`w8 = len-1` 递减、目标递增），随后正向拷 cmd（`0x09b8-0x0a14`）；
4. 对 `[0x0F, 0x12+prefixLen)` 逐字节调 `invert_8c`（`0x0a2c-0x0a48`）；
5. `check_crc16(prefix,prefixLen,cmd,cmdLen)`，低字节在前写末尾 2 字节（`0x0a4c-0x0a78`）；
6. `whitenging_encode(buf+0x12, prefixLen+2+cmdLen)` 再 `whitening_output(buf, total)`（`0x0a7c`、`0x0a8c`）；
7. 输出 = 工作区自偏移 `0x0F` 起 `prefixLen+cmdLen+5` 字节（`0x0aac-0x0ad8`，长度算式即 `3 + prefixLen + cmdLen + 2`）。

导入符号（`.dynsym`）与任务描述一致，另注二进制里函数名拼写就是 `whitenging_encode`：
`_Z8invert_8c`、`_Z11check_crc16PKciS0_i`、`_Z14whitening_initiPi`、`_Z16whitening_outputPi`、`_Z17whitenging_encodePciPi`、`_Z9invert_16t`。

由此得到**可手工验算**的规律（prefix 固定 `77624d5345`）：

```
payload[0..7]  = 6D B6 43 CE 97 FE 42 7C     // 同步字 + 倒序前缀，白化后定值
payload[8]     = cmd_byte XOR 0xE5            // 1 字节命令
payload[9..10] = CRC16，低字节在前
```

抽验：`0x00→E5`、`0x19→FC`、`0x30→D5`、`0x41→A4`、`0x50→B5` 全部吻合。
2 字节命令时 `payload[8]=cmd0^0xE5`、`payload[9]=cmd1^0x1D`（`0A01 → EF 1C`，与 `rf_table.json` 一致）。

### 3.4 前缀一致性核对

| 核对项 | 值 | 证据 |
|---|---|---|
| 设备上报 `BroadcastPrefix` | `"77 62 4d 53 45"` | 服务端 JSON 字段 `@SerializedName("BroadcastPrefix")` `DeviceBean.java:33-35` |
| 源码常量 `RAW_ADDRESS` | `"77 62 4d 53 45"` | `BleUtils.java:74` |
| 日志硬编码串 | `"77 62 4d 53 45"` | `BleUtils.java:353,368` |

**结论：三者完全一致（归一化后同 `77624d5345`），无差异。**
补充（已确认）：只有 `isPrivateProtocol()==1` 且 `BroadcastPrefix` 非空才替换前缀（`BleUtils.java:349,364`）。一旦前缀不同，**整帧（含前 8 字节）都变**——实测 `prefix=77624d5346 → 6DB6430E97FE427CD5E54B`（前 8 字节已不同）。成品 `Gear.java` 把前缀硬编码为 `{0x77,0x62,0x4D,0x53,0x45}`（`:197`）。

---

## 4. 分支字段核查

全量使用点见 `audit\branch_scan.log`。

| 字段 | 命中 | 改命令字节？ | 改广播参数？ | 结论 |
|---|---|---|---|---|
| `isPrecise` / `IsPrecise` | **0**（jadx 源码 + 8 个 `.dex` 原始字节级搜索 `Precise`） | — | — | **本版本无此字段**（字节级负结论） |
| `getCateId()==3` | 9 处（`BleWaveShockSender.java:63`；`ClassicModeBleHelper.java:54,76`；`ClassicModeFragment.java:830,849,869`） | 否 | 否 | 仅统计上报 / `DeviceBridgeJvm.o/q/e/r` 埋点 |
| `isPrivateProtocol()==0/1` | 2 处（`BleUtils.java:349,364`） | **间接是**：==1 且前缀非空 → 换 prefix → 整帧变；==0 → 恒用 `RAW_ADDRESS` | 否 | 只决定前缀 |
| `isEncrypt=0` | 1 处（`ManipulateInitPlanBuilder.java:204`） | 否 | 否 | 只置 `ManipulateInitPlan` 的加密支持标志（配 `getEncryptCommand()`：`ManipulateActivity.java:237,267,293`） |
| `isCryptBle()` | 8 处（`BleUtils.java:275,334`；`BleConnectionManager.java:187,552`；`CryptBleUtils.java:372,460,480,522`；`DeviceViewModel.java:705`；`InteractBleHelper.java:399`） | **换协议**：true → `CryptBleUtils` 4 字节加密帧（含随机数）；`heatingWriteBytes` 由 `{0/1,1}` 变 `{36/37,0}`（`BleUtils.java:273-279`）；`s()` 由 `{10,1}` 变 `{32,0}` | 否 | 只影响 GATT 加密通道；**不是确定性表** |
| `getBroadcastPrefix()` | 2 处（`BleUtils.java:348,364`） | **是**（见上） | 否 | 仅与 `isPrivateProtocol==1` 联用 |
| `isGattBle()`（顺带） | 31 处，关键 `WebJsBridgeHelper.java:347`、`DeviceBarHelper.java:302` 写 `noBleBluetooth = !isGattBle` | **换传输**：广播 RF 帧 ↔ `getBleCommand2()` 十进制 2 字节 GATT 写 | 否 | 这才是"发广播还是发 GATT"的开关（`BleUtils.java:302-309`） |

**对题面四问的直接回答**：`isPrecise`（无实现）、`getCateId()==3`（纯上报）、`isEncrypt=0`（纯 UI 标志）**都不改变**命令格式或广播参数；`isPrivateProtocol()` 改**前缀**，`isCryptBle()` 改**传输协议**——两者会让"同一命令字节"产生完全不同的空口字节，但都不改变 `getBleCommand` 的编码规则本身。

---

## 5. 成品源码缺口

### 5.1 `Rf.java`

- 缺 30 条（§2.1），现表 24 条唯一项。
- 含 1 条无空口依据的 `0A01`（`:19`）。
- 表仅对 `prefix=77624d5345` 成立（§3.4）。

### 5.2 `Gear.java`

| 缺陷 | 位置 | 后果 |
|---|---|---|
| `music(lv)` 发 `{0x10, lv}` 2 字节 | `:107-111` | `payloadFor("1000".."1009")` 必 miss → 走裸帧兜底 |
| `frame()` 兜底 = `77 62 4D 53 45 + cmd`，**无 invert/白化/CRC** | `:194-202` | 任何 miss 的命令都发出**设备无法解析的废帧**（静默失效，最难排查） |
| `strength(s)` 只映射 `0x51..0x55` | `:101-105` | 普通 5 档需 `0x11..0x15`；special(0213/4180/3127) 需 `0x01..0x03`；arcType=1 需 `0x41..0x45`。四套语义压成一套 |
| `level(lv)` 只映射 `0x00..0x09` | `:95-99` | 10 档体系（`0x11..0x19`/`0x20`）不可用 |
| 无互控通道（`0x30–0x39`、`0x40–0x49`） | — | 互控/波形玩法不可用 |
| 前缀硬编码 | `:197` | `isPrivateProtocol==1` 设备全错 |

现成补丁：`audit\Rf_gap.java.txt`（30 条已证实 + 6 条外推注释块，格式已对齐 `Rf.T`）。

---

## 6. 已确认 vs 推测

### 已确认（字节级 / 反汇编 / 实测交叉验证）

1. 编码 = `get_rf_payload(prefix,prefixLen,cmd,cmdLen)`，输出 `prefixLen+cmdLen+5`；`payload[8]=cmd^0xE5`（1 字节命令），末 2 字节 CRC16 低前高后（§3.3）。
2. `rf_table.json` 全部 27 条表项与 native 仿真**逐字节相同**，0 mismatch。
3. 30 条缺失命令的真实 payload 已实测（§3.1），跨进程确定性（3 次一致）。
4. `RAW_ADDRESS` = 设备上报 `BroadcastPrefix` = `"77 62 4d 53 45"`，无差异。
5. `isPrecise` 在本 APK 中**不存在**（8 dex 原始字节搜索 0 命中）。
6. `getCateId()==3`、`isEncrypt` 不影响编码/广播参数；`isPrivateProtocol` 只换前缀；`isCryptBle` 只换加密 GATT 协议。
7. `0x01–0x03` 压缩档位只对 barcode `0213/4180/3127` 生效（`SpecialBindCodeSupport.java:24`）。
8. `isCryptBle` 通道含随机数，**非**确定性 payload（`CryptBleUtils.java:170-174`）。
9. `startAdvertising` 首参只切 settings（0→1500ms/BALANCED；1|2→3000ms/LOW_LATENCY）。
10. **档位字节空间 = `0x11–0x19` + `0x20`**；`0x1A–0x1F` 无静态产出方（`coerceIn(...,20)` 与 `i4>=10→"20"` 双重守卫）。
11. `0x24/0x25/0x26/0x27/0x28/0x29` 的开关语义与 GATT 等价字节（`{0/1,1}`、`{36/37,0}`、`{38,0}/{39,0}`、`{40,0}/{41,0}`）一一对应。

### 推测（有强证据但未闭环，引用时请标注）

1. **`InteractBleHelper.f(Intent)`（`"AloneRhythmData"`）的逐条映射**：该方法 jadx 反编译失败（`InteractBleHelper.java:203-209` 抛 `UnsupportedOperationException`）。从其残留 switch 可读出**输入码集合** = `"0".."9"`、`"11".."19"`（哈希 1568–1576）、`"20"`（1598）、`"30"`（1629），但**每条落到哪个字节未确认**。旁证：`WaveShockChannelFactory.f()` 给出 `"1".."9"→0x31–0x39`、`"11".."19"→0x41–0x49`、`"9"→0x49`。
2. `0x10` 的确切语义（声音跟随 vs AI 低音量回落）：两处调用点都只发单字节，标签按上下文推定。
3. `0x24` 的"待机底座/preheat"含义：由 `"00"→500/1000ms→"24"` 固定序列推定（`DeviceManipulateBleHelper.java:38-44`、`InteractBleHelper.java:334-340`）。
4. `0x27` 同时被"放电"和"电击"两个 toggle 使用（`ManipulateActivity.java:933,1000`），设备侧是否区分无法静态判定。
5. 2 字节 RF 命令在协议上可行（native 接受任意 `cmdLen`，`0A01` 实测 len=12），但本 APK 静态路径无实例；`Mode.newCommand` 若下发 4 hex 字符，则会踩到 2 字节分支。
6. `0x41–0x45` 与 arcType=1 压力档同字节、`0x51–0x55` 与亮度同字节：两者是否被机型隔离，静态代码只给出"不同机型走不同 UI"，未做协议层校验。

---

## 7. 建议处置顺序

1. 先修 `Gear.frame()` 的裸帧兜底（miss 时不发，也别发废帧）——这是当前最隐蔽的失效点。
2. 用 `audit\Rf_gap.java.txt` 补 `Rf.T`（30 条）；`0A01` 删除或降级为 GATT-only。
3. `level()/strength()` 改为**按机型/`arcType`/`special` 选表**（4 套语义：`01–03`、`11–19`+`20`、`41–45`、`51–55`）。
4. 表按"hex 命令 + 语义 + 触发条件 + `文件:行号`"维护，废弃十进制命名。
5. 若要覆盖 `isCryptBle` 设备，需单独逆向 `CryptBleUtils.cryptBleCmd`（4 字节帧 + 随机数），不能复用本表。
