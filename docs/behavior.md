# 原厂 App 基本行为

样本：`暮瑟 Muse v5.0.2`，包名 `com.maxhom.weibu`，versionCode 318，minSdk 26 / targetSdk 35。
出品方：深圳市威步科技有限公司（落地页署名「深圳市威步智能物联有限公司」，粤ICP备2023081331号-5）。

## 一、产品定位

**智能情趣硬件远程遥控 + 1v1 音视频陪聊 + AI 角色陪聊 + 虚拟币充值/收益提现** 的成人向社交应用。
社交是入口，硬件遥控是差异点，变现靠充值（贝壳/珍珠）+ 收益提现。

同源马甲：`暮瑟(Muse)` ↔ `瑟友圈/瑟伴(com.wbkj.lovespouse)` ↔ `lovespouse` 系列，共用同一后端。
线上配置里有明确导流话术：「部分社交功能【灵魂匹配】【社区】暂时无法使用，请前往下载"瑟友圈"APP……使用"暮瑟"账户直接登录」。

## 二、功能结构（从 AndroidManifest + 代码还原）

| 模块 | 包路径 | 关键页面 |
|---|---|---|
| 设备控制 | `ui/device/` | `DeviceAddActivity`、`ManipulateActivity`、`VibratorEggClassicActivity`、`KegelTrainingActivity`、`StandaloneModeActivity` |
| 模式页 | `ui/device/play/` | `ClassicModeFragment`（经典）、`MusicalPageActivity`（音乐）、`ShakePatternFragment`（摇一摇）、`DelimitFragment`（划一划/触摸板）、`InteractChatSession`（互动）、`VideoShockModeFragment`（视频）、`GameFragment`（游戏） |
| AI 陪聊 | `ui/ai/` | `AiHomeActivity`、`AiChatActivity`、`AiConversationActivity`、`AiAudioChatFragment` |
| 漂流瓶 | `ui/bottle/` | 捞瓶、投瓶、积分榜、批量捞瓶规则 |
| 社区 | `ui/community/` | 动态、匹配、同城 |
| 收益 | `ui/mine/pearl/` | 珍珠提现、支付宝提现、身份证审核 |
| 账号 | `ui/user/login/` | 验证码登录、密码登录、注册、找回、二步验证 |

## 三、硬件行为

设备信息来自服务端 `GET/POST /muse_api/v1/device/getdevicelist`，关键字段：

```
BarCode         产品编号（如 9333），说明书上印的那个
BroadcastPrefix 广播前缀（9333 = "77 62 4d 53 45" = ASCII "wbMSE"）
ServiceUuid / WriteUuid
                为空 → 纯广播控类，无 GATT 通道
Wireless        2.4g
Func            能力位：classic/music/shake/intera/finger/video/game…
IsPrivateProtocol  1 时用设备自带前缀，0 时用常量 RAW_ADDRESS
```

控制路径有两条，由偏好 `noBleBluetooth` 决定：

- `true`（默认）：**BLE 广播**，`getBleCommand()` 生成 11 字节 manufacturer data，`BeaconManager.startAdvertising()` 发出
- `false`：**GATT 写入**，2 字节帧 `[level, flag]` 写到 `aaa0/aaa1`

广播参数（`BeaconManager`）：

```
type==0  → BALANCED + timeout 1500ms
type!=0  → LOW_LATENCY + timeout 3000ms
两者都 setConnectable(true)  ← 必须是 ADV_IND，否则设备不收
manufacturerId 恒为 255 (0xFF)
ServiceUUID 0000ae8f-0000-1000-8000-00805f9b34fb
```

App 靠定时重发续命（超时后再发一轮），并且 `MIN_ADVERTISE_WINDOW_MS=260` 会合并频繁切换。

## 四、登录与会话

- 登录接口：`/muse_api/v2/user/loginOld`（密码）、`/user/phoneLogin`（验证码）、`/user/loginSendCode`（发码）
- 返回 JWT（HS256，有效期 150 天），payload 含 `id / username / tel / groupId / permissions`
- 传输层：`AES/CBC/PKCS5Padding`，key = iv = App 内硬编码常量；带 `phonesystem: android` 头时整包加密
- 请求头 `token` + query `userid/sessionid`，服务端以 **JWT 里的 id 为准**（改 query 的 userid 无效）

## 五、计费

| 接口 | 行为 |
|---|---|
| `/muse_api/v2/ai/chat` | 每发一条扣 1 次 `msg_num`（服务端扣） |
| `/muse_api/v2/ai/buyPackage` | 校验余额，不足返回 `errorCode 10886` |
| `/muse_api/v2/ai/signIn` | 每日一次，重复返回「今日已签到」 |

AI 回复**不走 HTTP 同步返回**，而是通过腾讯 IM 下发（AI 是一个 IM 账号）；社交开关关闭时（`"社交功能升级中，暂停使用"`）对话回复拿不到。

## 六、音乐模式的真实行为

宣传是"旋律识别、跟随音乐震动"，代码里实际是：

```
Visualizer(0) 全局输出混音 → 10 Hz 取 1024 点波形 → 标准差 σ
→ gear = floor((min(σ,80)/80)² × 10) → 1..10 → 0x11..0x19 / 0x20
→ 1000ms 节流 + 同档去重
```

- **没有 FFT、没有 BPM、没有 beat/onset、没有频带**（`setDataCaptureListener(..., true, false)` 明确关掉 FFT）
- 失败时静默降级为麦克风（`MediaRecorder` + `getMaxAmplitude()`），UI 仍显示"正在聆听手机播放的音乐"，用户无法分辨
- `libaudioeffect.so` 里确实带了完整的 beat/downbeat 跟踪模型（sami / madmom / TF），但 Java 层从未 `loadLibrary("audioeffect")` —— **买了没接线**

MuseGear 的 `mode=beat` 是自己实现的 onset 检测，属于原厂没有的能力。

## 七、导出组件与第三方依赖

导出的关键组件：`WelcomeActivity`(launcher)、`AddFriendActivity`、`InitiateInvitationActivity`、`PlayService`、`PlotPlayService`、`WXEntryActivity`/`WXPayEntryActivity`、`WebViewInitProvider`。

主要 SDK：Agora RTC、腾讯 IM/TRTC、腾讯云人脸核身（KYC）、富友支付、支付宝、微信、穿山甲/优量汇/华为/快手广告、数美风控、高德定位。
