# MuseGear HTTP API

默认监听 `0.0.0.0:8899`，全部响应为 JSON，`Connection: close`，带 `X-Resp-Ms` 头。

USB 连接更稳：

```bash
adb forward tcp:8899 tcp:8899
curl http://127.0.0.1:8899/status
```

## 端点

### 探测

| 端点 | 说明 |
|---|---|
| `GET /health` | 极轻量存活探测，不碰任何引擎 |
| `GET /status` | 完整状态（见下） |

```json
{
  "ok": true, "api": true, "calls": 12, "errors": 0, "up": 84213,
  "audio": {"on": true, "src": "visualizer", "mode": "beat", "sens": 0.70,
            "max": 9, "amp": 41, "gear": 6, "beats": 137, "cbMs": 100},
  "pulse": {"on": false, "hz": 2.00, "level": 9, "duty": 0.50},
  "gear": 0, "cmd": "6DB643CE97FE427CECD4E0",
  "note": "", "tbl": 111
}
```

- `audio.src` —— `visualizer`（系统全局混音）或 `microphone`（降级）
- `gear` —— 最近一次 Advertise 回调码：`0` 成功 / `1` 数据过大 / `2` 过于频繁 / `3` 已有广播（其他 App 占用）/ `4` 实例超限 / `-2` 缺权限 / `-3` 异常 / `-4` 无对应帧
- `cmd` —— 最近一次实际发出的空口 payload（hex）
- `note` —— 失败原因，如 `NO_FRAME:XX`

### 档位

| 端点 | 命令字节 |
|---|---|
| `GET /gear?level=0..9` | `00`–`09`（经典 9 档） |
| `GET /musicgear?lv=1..10` | `11`–`19`、`20`（10 档体系） |
| `GET /strength?n=1..5` | `51`–`55`（亮度，等价 `/brightness`） |
| `GET /brightness?n=1..5` | 同上，语义更明确 |
| `GET /press?g=1..5&arc=1` | `41`–`45`（弧1 压力） |
| `GET /press?g=1..5&arc=2` | `51`–`55`（弧2 压力） |
| `GET /channel?n=1..9` | `31`–`39`（波形通道） |
| `GET /channelstop?k=wave\|ext\|arc1\|arc2` | `30` / `40` / `40` / `50` |
| `GET /idle` | `10`（低音量心跳） |

### 节奏与序列

| 端点 | 说明 |
|---|---|
| `GET /pulse?hz=0.5..10&level=1..9&duty=0.05..0.95&dur=秒` | 脉冲；`dur` 到点自动停 |
| `GET /alone?a=0..9` | 单人节奏单步（倒序映射，`a=0` → `0x49`） |
| `GET /alone?a=5&fb=1` | 带原厂 1 秒回落 `0x10` |
| `GET /aloneseq?a=0,1,2,3,4&gap=400` | 序列播放 |
| `GET /seq?hex=01,02,05&gap=100` | 任意命令序列 |
| `GET /raw?hex=24` | 直接下发任意单/多字节命令 |

### 互动

| 端点 | 说明 |
|---|---|
| `GET /interact?on=1` | 保活 `24` |
| `GET /interact?on=0` | `00` |
| `GET /interactcrit` | 高潮 `20`，1 秒后回落 `10` |

### 音频跟随

| 端点 | 说明 |
|---|---|
| `GET /audio?on=1&mode=beat\|amp&sens=0.05..1&level=1..9` | 开启 |
| `GET /audio?on=0` | 关闭 |

- `mode=amp` —— 复刻原厂：`gear = floor((min(σ,80)/80)² × 10)`
- `mode=beat` —— 自研 onset：RMS 相对 EMA 基线的突增，阈值 `k = 2.2 - 1.6×sens`，命中后顶到 `level`，120ms 回落

### 手写板

| 端点 | 说明 |
|---|---|
| `GET /pad?x=0..1&y=0..1` | 归一化坐标，内部按 `√((x-W/2)²+(y-H/2)²)/60` 换算档位 0–9 |
| `GET /pad?r=像素半径` | 直接给半径 |
| `GET /padrelease` | 抬手，发 `00` |

### 摇一摇

| 端点 | 说明 |
|---|---|
| `GET /shake?mag=加速度` | `level = ((mag-20)/5)+1`，`level≥9` → `20` |

用 `BALANCED + timeout 1500` 的广播参数（与原厂摇一摇一致）。

### 其它

| 端点 | 说明 |
|---|---|
| `GET /stop` | 全停（脉冲、音频、广播、档位归零） |
| `GET /log?since=序号` | 事件回传，返回 `{now, events:[{t,seq,tag,msg}]}` |
| `GET /doc` | 内嵌 HTML 文档 |
| `GET /doc.txt` | 纯文本版 |
| `GET /modes` | 全部模式/预设 JSON |
| `GET /preset?name=xxx` | 按名触发预设 |

## adb 直控（不开 API）

```bash
adb shell am broadcast -a com.musegear.GEAR --ei level 5
adb shell am broadcast -a com.musegear.GEAR --es hex 53
adb shell am broadcast -a com.musegear.GEAR --es hex 10 --ei level 7
```

## 鲁棒性设计

- 每连接独立线程 + `SO_TIMEOUT=5s`，单条慢连接不阻塞服务
- `setReuseAddress` + backlog 32，重启不占端口
- 全链路 `catch Throwable`，异常只累加 `errors` 并返回 JSON，不杀进程
- 查不到帧时**拒发**并回 `NO_FRAME`，绝不发未白化的裸帧
- 所有 BLE 调用强制回主线程（部分 ROM 的蓝牙栈不接受跨线程调用）
- API 启动时持有 `PARTIAL_WAKE_LOCK`，熄屏不断供
