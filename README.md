# MuseGear

### 用一条 curl 控制你的玩具。

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3ddc84.svg)](#快速开始)
[![Protocol](https://img.shields.io/badge/protocol-2.4G%20broadcast-d81b60.svg)](docs/protocol.md)
[![Commands](https://img.shields.io/badge/commands-111%20mapped-informational.svg)](data/rf_table_full.json)
[![API](https://img.shields.io/badge/HTTP%20API-14%20endpoints-00695c.svg)](docs/api.md)

```bash
curl "http://<phone>:8899/gear?level=5"      # 5 档
curl "http://<phone>:8899/pulse?hz=3&level=9"  # 3 Hz 脉冲
curl "http://<phone>:8899/audio?on=1&mode=beat" # 跟着鼓点
```

暮瑟（`com.maxhom.weibu`）2.4G 情趣硬件的**独立控制器**：不依赖原厂 App、不登录、不联网、不喂积分。用一套逆向出来的广播帧协议直接驱动设备，并把控制面暴露成 HTTP API。

> 本仓库只包含：**MuseGear 源码与成品 APK**、**原始样本 APK**、**逆向证据与协议文档**。
> 不包含任何个人账号、凭据或会话数据。

## 它解决什么问题

原厂 App 把玩具控制锁在自己的 UI 里，且必须登录、必须保持前台。MuseGear 做了三件事：

1. **协议层还原** —— 从 `libble.so` 的 `get_rf_payload` 反推出完整的空口帧构造（固定头 + 白化 + CRC16），能生成任意命令的真实字节。
2. **控制面 API 化** —— 14 个 HTTP 端点，覆盖原厂 7 个模式的全部命令，支持任意序列、任意频率脉冲、音频跟随、手写板。
3. **常驻与可观测** —— 前台服务 + 唤醒锁保证熄屏不断供；`/status` `/log` 把每一次下发、每一个回调码都吐出来，失败会明确报错而不是静默。

## 已实现的能力

| 能力 | 说明 |
|---|---|
| 经典档位 | `0x00`–`0x09` |
| 10 档体系 | `0x11`–`0x19` + `0x20`（音乐 / 触摸板 / 摇一摇 / 游戏共用） |
| 亮度 / 压力 | `0x51`–`0x55`、`0x41`–`0x49` |
| 波形通道 | `0x31`–`0x39` |
| 互动 | 保活 `0x24`、高潮 `0x20`、回落 `0x10` |
| 单人节奏 | `0x39`–`0x49`（原厂 `AloneRhythmPattern` 的原始映射） |
| 脉冲 | 0.5–10 Hz 可调，占空比可调 |
| 音频跟随 | `Visualizer(0)` 系统全局混音；`amp`=原厂算法，`beat`=自研 onset 鼓点检测 |
| 手写板 | 横滑=档位 1–10，纵滑=压力 1–5，抬手自动停 |
| 任意序列 | `/seq?hex=01,02,03&gap=100` |

## 快速开始

```bash
# 装（用已构建的 APK，或自己编）
adb install -r app/MuseGear-2.1.apk
```

打开 App → 授予蓝牙/麦克风权限 → 点「启动 API」→ 屏幕显示 `http://<手机IP>:8899`。

```bash
# USB 更稳
adb forward tcp:8899 tcp:8899
curl http://127.0.0.1:8899/status

# 5 档
curl "http://127.0.0.1:8899/gear?level=5"

# 3 Hz 脉冲，20 秒
curl "http://127.0.0.1:8899/pulse?hz=3&level=9&dur=20"

# 跟鼓点
curl "http://127.0.0.1:8899/audio?on=1&mode=beat&sens=0.7&level=9"

# 单人节奏整段
curl "http://127.0.0.1:8899/aloneseq?a=0,1,2,3,4,5,6,7,8,9&gap=400"
```

API 完整说明见 [`docs/api.md`](docs/api.md)，手机浏览器直接开 `http://<手机IP>:8899/doc` 也能看。

## 自己编译

需要 JDK + Android SDK（build-tools 35+、platform android-35）：

```powershell
$env:ANDROID_HOME = "C:\Android\sdk"
powershell -ExecutionPolicy Bypass -File app/build.ps1
```

产物在 `app/build/MuseGear.apk`。

## 目录结构

```
app/            MuseGear 源码 + Manifest + 构建脚本 + 成品 APK
apk/            原始样本 APK（150 MB，Git LFS）
data/           命令 → 空口帧 的完整映射表（111 条）
docs/           行为说明 / 协议文档 / API 文档 / 证据索引
evidence/       逆向审计报告、原始日志、命令空间清单
tools/          帧生成、设备扫描、本地播控脚本
```

## 关键文档

- [`docs/behavior.md`](docs/behavior.md) —— 原厂 App 的行为与功能结构
- [`docs/protocol.md`](docs/protocol.md) —— 空口帧结构、白化/CRC、命令全表
- [`docs/api.md`](docs/api.md) —— MuseGear HTTP API
- [`docs/pitfalls.md`](docs/pitfalls.md) —— **踩过的 15 个坑**（协议、广播、构建、编码、可观测性）
- [`docs/evidence.md`](docs/evidence.md) —— 每条结论的证据来源与复现方法

## 免责声明

本项目用于对**自己持有的设备**做互操作性与自动化控制研究。样本 APK 仅作逆向分析留档，版权归原厂所有；MuseGear 自身代码以 MIT 发布。

涉及硬件为射频设备，请勿长时间高负荷运行；`0x20` 满档连续激励可能导致电机过热。
