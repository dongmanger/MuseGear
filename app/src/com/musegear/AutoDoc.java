package com.musegear;

/**
 * App 内自带 API 文档：一份 HTML（给 /doc 与手机外部访问），一份纯文本（给 App 内 TextView）。
 * 内容与 ApiServer 实际路由一致；命令表来自 Modes（53 条实证命令）。
 */
public final class AutoDoc {

    private AutoDoc() {}

    public static final String VERSION = "2.0";

    private static final String[] EP = {
            "GET /health", "存活探针 → {\"ok\":true,\"up\":ms}",
            "GET /status", "总状态：api/calls/errors/audio/pulse/gear/cmd/note/tbl",
            "GET /gear?level=1..9", "9 档体系档位（0x01-0x09）",
            "GET /strength?n=1..5", "亮度/强度（0x51-0x55）",
            "GET /musicgear?lv=1..10", "档位（0x11-0x19，10→0x20）",
            "GET /pulse?hz=&level=&duty=&dur=", "脉冲：周期性发档位，dur 秒后自停（≤600）",
            "GET /audio?on=1&mode=beat|amp&sens=&level=", "麦克风跟随；on=0 关闭",
            "GET /pad?x=0..1&y=0..1  (或 r=像素)", "手写板一次采样 → 档位+强度",
            "GET /padrelease", "松手：发 00 停止",
            "GET /shake?mag=", "加速度计换算档位（原厂 /shake 语义）",
            "GET /raw?hex=41", "直发任意 hex（仅接受实证命令，否则拒绝）",
            "GET /channel?n=1..9", "互控通道 0x31-0x39",
            "GET /channelstop?k=base|ext|arc1|arc2", "互控/压力停止 0x30/0x40/0x50",
            "GET /interact?on=1|0", "互动：1→0x24 底座，0→00 停",
            "GET /interactcrit", "互动暴击：0x20，1s 后回落 0x10",
            "GET /seq?hex=01,02,03&gap=100", "命令序列（gap≥20ms）",
            "GET /idle", "声控低音量心跳 0x10",
            "GET /brightness?n=1..5", "亮度（同 /strength）",
            "GET /press?g=1..5&arc=1|2", "压力档：arc1→0x41-45，arc2→0x51-55",
            "GET /stop", "全停：脉冲/音频/广播/00",
            "GET /log?since=seq", "增量事件日志",
            "GET /doc", "本页（HTML）",
            "GET /doc.txt", "本页（纯文本，UTF-8）",
            "GET /modes", "命令总表 JSON",
            "GET /preset?name=加热开", "按预设名执行（中文名见 /modes）"
    };

    // ------------------------------------------------------------------ 纯文本
    public static String text() {
        StringBuilder sb = new StringBuilder();
        sb.append("MuseGear v").append(VERSION).append(" 内嵌 API 文档\n");
        sb.append("================================\n\n");
        sb.append("【一、空口帧结构】\n");
        sb.append("payload = 8B 定值头 + 1B 命令(^0xE5) + 2B CRC16\n");
        sb.append("  定值头 = 6D B6 43 CE 97 FE 42 7C（同步字 71 0F 55 + 倒序前缀 45 53 4D 62 77）\n");
        sb.append("  前缀 = 77 62 4d 53 45（原厂 RAW_ADDRESS；私有协议设备可由服务端 BroadcastPrefix 覆盖）\n");
        sb.append("  广播：ManufacturerData(id=0xFF)，可选 ServiceUuid 0000ae8f\n");
        sb.append("  参数：LOW_LATENCY/connectable/timeout=3000ms/txPower=HIGH；每 2.5s 续播\n\n");
        sb.append("【二、档位体系（4 套，别混用）】\n");
        for (int i = 0; i < Modes.PROFILE_NAME.length; i++) {
            sb.append("  P").append(i).append(" ").append(Modes.PROFILE_NAME[i]).append('\n');
        }
        sb.append("  9档：1..9 → 01..09\n");
        sb.append("  10档：1..9 → 11..19，第10档 → 20（不是 1A）\n");
        sb.append("  压缩机型(0213/4180/3127)：1-3→01，4-6→02，7-10→03\n");
        sb.append("  arc1：1..5 → 41..45，停 40；arc2：1..5 → 51..55，停 50\n");
        sb.append("  51..55 同时是亮度 1..5（同字节，靠机型区分）\n\n");
        sb.append("【三、HTTP 端点】\n");
        for (int i = 0; i < EP.length; i += 2) {
            sb.append("  ").append(pad(EP[i], 46)).append(EP[i + 1]).append('\n');
        }
        sb.append("\n【四、电脑端接入】\n");
        sb.append("  adb forward tcp:8899 tcp:8899\n");
        sb.append("  curl http://127.0.0.1:8899/status\n");
        sb.append("  curl \"http://127.0.0.1:8899/preset?name=加热开\"\n");
        sb.append("  curl \"http://127.0.0.1:8899/press?g=3&arc=2\"\n");
        sb.append("  同 WiFi 可直接用手机 IP:8899\n\n");
        sb.append("【五、命令总表 ").append(Modes.size()).append(" 条】\n");
        sb.append("  hex  分组  语义\n");
        sb.append(Modes.tableText());
        return sb.toString();
    }

    private static String pad(String s, int w) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < w) sb.append(' ');
        return sb.toString();
    }

    // ------------------------------------------------------------------ HTML
    public static String html() {
        StringBuilder b = new StringBuilder(16384);
        b.append("<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
         .append("<title>MuseGear v").append(VERSION).append(" API</title><style>")
         .append("body{font:14px/1.55 -apple-system,Segoe UI,Roboto,sans-serif;margin:0;padding:18px;background:#fafafa;color:#222}")
         .append("h1{font-size:20px;color:#D81B60;margin:0 0 6px}h2{font-size:15px;margin:22px 0 8px;padding-bottom:4px;border-bottom:1px solid #e5e5e5}")
         .append("code,kbd{background:#eee;padding:1px 5px;border-radius:3px;font-size:12.5px}")
         .append("table{border-collapse:collapse;width:100%;margin:6px 0;font-size:13px}")
         .append("th,td{border:1px solid #e0e0e0;padding:5px 8px;text-align:left;vertical-align:top}")
         .append("th{background:#f2f2f2}tr:nth-child(even) td{background:#fff}")
         .append("pre{background:#272727;color:#eaeaea;padding:10px;border-radius:5px;overflow:auto;font-size:12.5px}")
         .append(".g{color:#777;font-size:12px}")
         .append("</style></head><body>");

        b.append("<h1>MuseGear v").append(VERSION).append(" · 内嵌 API 文档</h1>");
        b.append("<div class=\"g\">本页由 App 自身提供：<code>http://手机IP:8899/doc</code>　·　纯文本版：<code>/doc.txt</code>　·　命令表 JSON：<code>/modes</code></div>");

        b.append("<h2>一、空口帧结构</h2>");
        b.append("<table><tr><th>字段</th><th>值</th><th>说明</th></tr>")
         .append("<tr><td>定值头 8B</td><td><code>6D B6 43 CE 97 FE 42 7C</code></td><td>同步字 <code>71 0F 55</code> + 倒序前缀，白化后定值</td></tr>")
         .append("<tr><td>命令 1B</td><td><code>cmd ^ 0xE5</code></td><td>2 字节命令时第 2 字节 <code>^ 0x1D</code></td></tr>")
         .append("<tr><td>CRC 2B</td><td>低字节在前</td><td>CRC16 over (prefix, cmd)</td></tr>")
         .append("<tr><td>总长</td><td>11B</td><td>prefixLen(5)+cmdLen(1)+5</td></tr>")
         .append("</table>");
        b.append("<p>前缀 <code>77 62 4d 53 45</code>（原厂 <code>BleUtils.RAW_ADDRESS</code>）。仅当设备 <code>IsPrivateProtocol==1</code> 且服务端下发 <code>BroadcastPrefix</code> 时才替换——换前缀会让整帧（含前 8 字节）变化。</p>");
        b.append("<p>广播：<code>ManufacturerData(id=0xFF)</code> + 可选 <code>ServiceUuid 0000ae8f</code>；<code>LOW_LATENCY / connectable / timeout 3000ms / txPower HIGH</code>，每 2.5s 续播。</p>");

        b.append("<h2>二、档位体系（4 套，别混用）</h2><table><tr><th>体系</th><th>档位字节</th><th>停止</th><th>适用</th></tr>")
         .append("<tr><td>9 档</td><td><code>01..09</code></td><td><code>00</code></td><td>9 档设备（节奏模式同字节）</td></tr>")
         .append("<tr><td>10 档</td><td><code>11..19</code>，第 10 档 <code>20</code></td><td><code>00</code></td><td>10 档设备；<b>0x1A–0x1F 无产出方</b></td></tr>")
         .append("<tr><td>压缩</td><td><code>01/02/03</code></td><td><code>00</code></td><td>barcode 0213 / 4180 / 3127</td></tr>")
         .append("<tr><td>压力弧 arc1</td><td><code>41..45</code></td><td><code>40</code></td><td>arcType=1 机型</td></tr>")
         .append("<tr><td>压力弧 arc2</td><td><code>51..55</code></td><td><code>50</code></td><td>barcode 4818 / 9947；与亮度同字节</td></tr>")
         .append("</table>");

        b.append("<h2>三、HTTP 端点</h2><table><tr><th style=\"width:46%\">端点</th><th>说明</th></tr>");
        for (int i = 0; i < EP.length; i += 2) {
            b.append("<tr><td><code>").append(esc(EP[i])).append("</code></td><td>").append(esc(EP[i + 1])).append("</td></tr>");
        }
        b.append("</table>");

        b.append("<h2>四、电脑端接入</h2><pre>adb forward tcp:8899 tcp:8899\n")
         .append("curl http://127.0.0.1:8899/status\n")
         .append("curl \"http://127.0.0.1:8899/preset?name=加热开\"\n")
         .append("curl \"http://127.0.0.1:8899/press?g=3&amp;arc=2\"\n")
         .append("curl \"http://127.0.0.1:8899/pad?x=0.8&amp;y=0.2\"\n")
         .append("# 同 WiFi 直接用手机 IP:8899</pre>");

        b.append("<h2>五、命令总表（").append(Modes.size()).append(" 条实证命令）</h2>")
         .append("<table><tr><th>hex</th><th>分组</th><th>语义</th><th>原厂出处</th></tr>");
        for (int i = 0; i < Modes.size(); i++) {
            b.append("<tr><td><code>").append(Modes.HEX[i]).append("</code></td><td>").append(esc(Modes.GROUP[i]))
             .append("</td><td>").append(esc(Modes.NAME[i])).append("</td><td class=\"g\">").append(esc(Modes.SRC[i]))
             .append("</td></tr>");
        }
        b.append("</table>");

        b.append("<h2>六、校验原则</h2><ul>")
         .append("<li>只发<b>实证命令</b>：<code>Modes.isEvidenced()</code> 拦截无空口帧的字节（如 <code>1A</code>），避免发废帧。</li>")
         .append("<li>帧一律查表（<code>Rf</code>，由 <code>libble_arm64.so</code> 仿真导出），<b>绝不拼裸帧</b>。</li>")
         .append("<li>同一命令跨进程可复现（确定性），可离线预生成。</li>")
         .append("</ul>");

        b.append("<div class=\"g\" style=\"margin-top:24px\">MuseGear v").append(VERSION)
         .append(" · 命令表 ").append(Modes.size()).append(" 条 · 帧表 ").append(Rf.size()).append(" 条</div>");
        b.append("</body></html>");
        return b.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder o = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': o.append("&lt;"); break;
                case '>': o.append("&gt;"); break;
                case '&': o.append("&amp;"); break;
                case '"': o.append("&quot;"); break;
                default: o.append(c);
            }
        }
        return o.toString();
    }

    /** 命令表 JSON（/modes） */
    public static String modesJson() {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\"ok\":true,\"version\":\"").append(VERSION).append("\",\"count\":").append(Modes.size()).append(",\"cmds\":[");
        for (int i = 0; i < Modes.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"hex\":\"").append(Modes.HEX[i]).append("\",\"group\":\"").append(json(Modes.GROUP[i]))
              .append("\",\"name\":\"").append(json(Modes.NAME[i])).append("\"}");
        }
        sb.append("],\"presets\":[");
        for (int i = 0; i < Modes.PRESET_NAME.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(json(Modes.PRESET_NAME[i])).append("\",\"hex\":\"")
              .append(Modes.PRESET_HEX[i]).append("\"}");
        }
        sb.append("],\"profiles\":[");
        for (int i = 0; i < Modes.PROFILE_NAME.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(json(Modes.PROFILE_NAME[i])).append('"');
        }
        sb.append("]}");
        return sb.toString();
    }

    /** 预设名列表 JSON（/preset 缺参时回显） */
    public static String presetsJson() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('[');
        for (int i = 0; i < Modes.PRESET_NAME.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(json(Modes.PRESET_NAME[i])).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String json(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
