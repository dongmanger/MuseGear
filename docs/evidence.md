# 证据与方法

每条结论都有可复现的来源。按主题列出。

## 一、样本

| 项 | 值 |
|---|---|
| 文件 | `apk/Muse_v5.0.2.apk`（150.44 MB，Git LFS） |
| SHA-256 | `39D9BC3F0022D1F4D81928D7BEDFDC73650D975541F79C9366E9626162AE7AF2` |
| MD5 | `E7A43CAA5D5332E6422B7A2604AA660A` |
| 下载源 | `https://cdn.melodyquest2025.com/download/Muse_v5.0.2.apk`（落地页 `dl.artlovespte.com/dowload.html`） |
| 包名 | `com.maxhom.weibu`，versionCode 318，versionName 5.0.2 |

## 二、方法链

```
1. 落地页 → 调 /muse_api/v2/common/appDowloadUrl 拿到真实下载地址
2. jadx 1.5.6 全量反编译（21,261 类）→ jadx_out/
3. apktool 解 AndroidManifest + assets
4. dex 字符串挖掘（自写 PowerShell + Latin1 解码）定位域名/接口/常量
5. 关键类人工阅读：BleUtils / BeaconManager / SpecialHardwareCommandDispatcher
   / SpecialGearCommandMapper / WaveShockChannelFactory / MusicBleCommandHelper
   / VisualizerHelper / MySurfaceView / ShakePatternFragment / InteractBleHelper
6. jadx 反编译失败的 InteractBleHelper.f(Intent) → baksmali 3.0.10 反汇编 classes5.dex
   → 读 .method f(Landroid/content/Intent;)V 的 packed-switch 与 array-data
7. libble.so 的 native 函数 get_rf_payload → capstone 反汇编（842 字节）
   → 用 Unicorn 2.1.4 搭 fake JNIEnv 直接执行，导出 111 条命令的真实 payload
8. 线上实测：AES 信封自造、登录、接口调用、广播下发
```

## 三、关键结论 → 证据位置

### 3.1 帧构造（白化 + CRC16）

- native 符号：`Java_com_example_nirjon_bledemo4_1advertising_util_BLEUtil_get_1rf_1payload` @ `0x8AC`
- 依赖符号：`check_crc16`(0xC68)、`whitening_init`(0xE60)、`whitenging_encode`(0xEC4)、`invert_8c`(0xBD0)、`invert_16t`(0xC24)
- 算法还原见 `docs/protocol.md` §2
- 可自检公式：`payload[8] = cmd ^ 0xE5`，末 2 字节 CRC16
- 复现：`python tools/rf_gen.py 77624d5345 05` → `6DB643CE97FE427CE0B82A`
- 完整表：`data/rf_table_full.json`（111 条）

### 3.2 广播必须 connectable

- `BeaconManager.java:529-556` `createAdvSettings()` / `createAdvSettings2()` 均 `setConnectable(true)`
- `type==0` → BALANCED + 1500ms；`type!=0` → LOW_LATENCY + 3000ms
- 反证：本项目早期版本用 `setConnectable(false)`，`onStartSuccess` 回调 `0`（成功）但设备无任何反应 —— 因为发的是 `ADV_NONCONN_IND`

### 3.3 命令空间

- `BleUtils.java:74` `RAW_ADDRESS = "77 62 4d 53 45"`
- `SpecialGearCommandMapper.java:59` `String.valueOf(clamp(i+11,11,20))` → 十进制串按 hex 解析 → `0x11`–`0x19` + `0x20`
- `WaveShockChannelFactory.java:44` `String.format("%02X", i+48)` → `0x31`–`0x39`
- `ClassicModeFragment.java:569-577` 亮度 `51`–`55`；`:739-760` 压力 `40/41-45/50/51-55`
- `SpecialBindCodeSupport.java:24` 特殊机型 `{"0213","4180","3127"}` 才折叠成 `01/02/03`
- 审计差异表与全部 payload：`evidence/REPORT.md`、`evidence/rf_full.json`、`evidence/rf_diff.json`

### 3.4 单人节奏（jadx 失败方法）

- 位置：`classes5.dex` → `com/maxhom/weibu/ui/device/play/InteractBleHelper.smali`，方法 `.method public final f(Landroid/content/Intent;)V`（行 291–1544）
- 输入 extra：`"AloneRhythmData"`
- 两个 `packed-switch`（`0x30` = `"0"` 的 hashCode；`0x620`），对应 `q()` 广播路径与 `t([B,uuid)` GATT 路径
- `array-data` 块 `array_42c`–`array_4a4` 给出全部 GATT 2 字节帧
- 完整映射见 `docs/protocol.md` §5.4

### 3.5 音乐模式不是鼓点

- `VisualizerHelper.java:185` `new Visualizer(0)` —— sessionId 0 = 全局输出混音
- `:203` `setDataCaptureListener(..., getMaxCaptureRate()/2, true, false)` —— 第三参 waveform、第四参 **fft=false**
- `:190-192` `onFftDataCapture` 函数体只有非空断言（死代码）
- `:102-119` 算法 = `sqrt(mean((x-μ)²))` 标准差
- `MusicBleCommandHelper.java:59-64` `gear = floor((min(σ,80)/80)² × 10)`
- 全包搜索 `getFft` 0 命中、`bpm` 0 命中、`spectrum` 0 命中；`tempo`/`BEAT`/`onset` 的命中分别是 `ItemPosition`/`Heartbeat`/`onSetting` 子串误报
- `libaudioeffect.so` 内确有 `sami::BeatTrackingFeature`、`DBNDownBeatTrackingProcessor`、TF 模型，但 Java 层 0 处 `loadLibrary("audioeffect")` —— 买了未接线

### 3.6 触摸板只有半径

- `MySurfaceView.java:207-220` `gear = clamp((int)√((x-W/2)²+(y-H/2)²)/60, 0, 9)`
- 全包 `GestureDetector`/`VelocityTracker`/`onFling` 在触摸板相关代码 0 命中 → 无速度换算
- `DelimitFragment.java:66` 采样 100ms，`:203` 下发 `SpecialHardwareCommandDispatcher.F(gear, true)`

### 3.7 摇一摇

- `ShakePatternFragment.java:51` 阈值 `17.0f`，`:229-232` `getDefaultSensor(1)` + `SENSOR_DELAY_GAME`
- `:150-173` `level = ((int)((max-20)/5))+1` → `f(level)` → `11`–`19` / `20`

### 3.8 计费与登录

- 登录返回 JWT（HS256，150 天），payload 含 `id/username/tel/groupId`
- 服务端只认 JWT 的 `id`：把 query 的 `userid` 改成别的账号，返回仍是本账号数据（实测）
- `ai/chat` 每发一条 `msg_num` 减 1（实测 10→9）
- `ai/buyPackage` 余额不足返回 `10886`
- `ai/signIn` 重复返回 `400 今日已签到`
- AI 回复经腾讯 IM 下发，社交开关关闭时（`社交功能升级中，暂停使用`）取不到内容

### 3.9 传输层加密

- `CryptUtil.java`：`AES/CBC/PKCS5Padding`，key = iv = 固定 16 字节常量，Base64(`NO_WRAP`)
- 带 `phonesystem: android` 头时整包加密；GET 时全部 query 压进单个 `data=` 参数
- 实测：自造密文服务端能解（返回正常业务错误码），说明该层只是防抓包混淆，不具备鉴权意义

### 3.10 云端凭证现状

- `ai/config` 每次请求明文返回火山语音凭证（appId + token），**语音 TTS 实测可用**（`code:3000` + base64 音频）
- 同一响应里的 ARK `apiKey` 鉴权有效，但唯一绑定的端点返回 `InvalidEndpoint.ClosedEndpoint`；其余模型名全部 `NotFound` → **语言模型不可用**
- 该结论通过对照实验确认：不带 key → `AuthenticationError`；带 key + 随机模型名 → `NotFound`；带 key + 那个模型名 → `ClosedEndpoint`

## 四、复现环境

- Windows 10/11 + JDK 21 + Android SDK（build-tools 36.1.0 / platform android-35）
- jadx 1.5.6、apktool、baksmali 3.0.10、Unicorn 2.1.4、capstone 5.0.7、pyelftools
- 真机：Android 10 / HarmonyOS 2.0，BLE 外设角色可用

## 五、未确认项

| 项 | 状态 |
|---|---|
| `libaudioeffect.so` 的 beat tracking 是否被 TTS 链路间接调用 | 无 Java 绑定，静态无法确认 |
| 部分华为/荣耀机型是否屏蔽全局 output-mix Visualizer | 需实机；MuseGear 会降级为麦克风并在 `/status` 的 `src` 里标明 |
| `0x27` 被"放电"与"电击"两个 toggle 共用，设备侧是否区分 | 静态无法判定 |
| 服务端 `Mode.newCommand` 是否下发 4 位 hex（会踩到 2 字节命令） | 需在线抓包 |
