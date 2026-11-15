package com.musegear;

import java.util.ArrayList;
import java.util.List;

/**
 * 预设模式目录（还原原厂全部预设）。
 *
 * 全部 53 条命令均由 Muse v5.0.2 的 libble_arm64.so get_rf_payload(0x8AC) 仿真导出并经
 * 空口帧表 Rf 校验；每条都带原厂出处与触发条件，见 audit/REPORT.md。
 *
 * 关键事实（审计结论）：
 *  - 空口 payload = 8 字节定值头 + 1 字节命令 + 2 字节 CRC16；prefix 固定 77 62 4d 53 45。
 *  - 命令按【十六进制字符串】解析：档位 10 是 0x20，不是 0x1A；0x1A-0x1F 无产出方。
 *  - 9 档设备用 01-09，10 档设备用 11-19 + 20，压缩机型(0213/4180/3127)用 01-03。
 *  - 51-55 既是亮度 1-5，也是 arcType=2 机型的压力档 1-5（同字节，靠机型区分）。
 */
public final class Modes {

    private Modes() {}

    // ---------------------------------------------------------------- 设备档位体系
    /** 9 档设备：档位 = 0x01..0x09（节奏/振动模式同字节） */
    public static final int P_9 = 0;
    /** 10 档设备：档位 = 0x11..0x19，第 10 档 = 0x20 */
    public static final int P_10 = 1;
    /** 压缩机型（barcode 0213/4180/3127）：10 档压成 0x01/0x02/0x03 */
    public static final int P_SPECIAL = 2;
    /** 压力弧 arcType=1：档位 = 0x41..0x45，停止 = 0x40 */
    public static final int P_ARC40 = 3;
    /** 压力弧 arcType=2（barcode 4818/9947）：档位 = 0x51..0x55，停止 = 0x50 */
    public static final int P_ARC50 = 4;

    public static final String[] PROFILE_NAME = {
            "9档(01-09)", "10档(11-19,20)", "压缩(01-03)", "压力弧arc1(41-45)", "压力弧arc2(51-55)"
    };

    // ---------------------------------------------------------------- 命令总表
    public static final String G_STOP = "停止";
    public static final String G_PATTERN = "节奏";
    public static final String G_MUSIC = "声音跟随";
    public static final String G_GEAR = "档位";
    public static final String G_TOGGLE = "开关";
    public static final String G_WAVE = "互控";
    public static final String G_PRESS = "压力档";
    public static final String G_BRIGHT = "亮度";

    /** 53 条实证命令（hex 字面，升序） */
    public static final String[] HEX;
    public static final String[] NAME;
    public static final String[] GROUP;
    /** 原厂出处（文件:行号） */
    public static final String[] SRC;

    static {
        List<String> h = new ArrayList<String>();
        List<String> n = new ArrayList<String>();
        List<String> g = new ArrayList<String>();
        List<String> s = new ArrayList<String>();

        add(h, n, g, s, "00", "停止/归零/会话结束", G_STOP, "BleUtils.java:302(00 通发) DeviceManipulateBleHelper.java:38");
        for (int i = 1; i <= 9; i++) {
            add(h, n, g, s, hex2(i), "节奏模式 " + i + "（9档设备即档位 " + i + "）", G_PATTERN,
                    "InteractBleHelper.java:" + (264 + i * 3) + " waveShockChannelFactory a()/c()");
        }
        add(h, n, g, s, "10", "声音跟随/音乐模式码（单字节，无子档）", G_MUSIC, "InteractBleHelper.java:77 AiSoundInteractionFragment.java:119");
        for (int i = 1; i <= 9; i++) {
            add(h, n, g, s, hex2(0x10 + i), "10档体系 档位 " + i + "（逻辑串 \"" + (10 + i) + "\"）", G_GEAR,
                    "SpecialGearCommandMapper.java:59,32-51,117-137 Dispatcher:174,391");
        }
        add(h, n, g, s, "20", "档位 10（逻辑串 \"20\"）／互动启动／越界兜底", G_GEAR,
                "SpecialGearCommandMapper.java:59,28 InteractBleHelper.java:394");
        add(h, n, g, s, "24", "加热关／待机底座态", G_TOGGLE, "ManipulateActivity.java:944 VibratorEggClassicActivity.java:80,175");
        add(h, n, g, s, "25", "加热开（原厂借 Constants.VIA_REPORT_TYPE_CHAT_AUDIO）", G_TOGGLE, "ManipulateActivity.java:944 VibratorEggClassicActivity.java:197");
        add(h, n, g, s, "26", "放电/电击 关（VIA_REPORT_TYPE_CHAT_VIDEO）", G_TOGGLE, "ManipulateActivity.java:933,1000");
        add(h, n, g, s, "27", "放电/电击 开", G_TOGGLE, "ManipulateActivity.java:933,1000");
        add(h, n, g, s, "28", "声控 关（VIA_ACT_TYPE_TWENTY_EIGHT）", G_TOGGLE, "ManipulateActivity.java:989");
        add(h, n, g, s, "29", "声控 开", G_TOGGLE, "ManipulateActivity.java:989");
        add(h, n, g, s, "30", "互控隐藏态停止（bleStopHex）", G_WAVE, "WaveShockCodeResolver.java:86 WaveShockChannelFactory.java:44,63");
        for (int i = 1; i <= 9; i++) {
            add(h, n, g, s, hex2(0x30 + i), "互控通道 " + i + "（IM 码 \"" + i + "\"，+0x30）", G_WAVE,
                    "WaveShockChannelFactory.java:43-48,120-127");
        }
        add(h, n, g, s, "40", "互控/压力停止（非隐藏态，arcType=1 停止）", G_WAVE, "WaveShockCodeResolver.java:86 WaveShockChannelFactory.java:73,114");
        for (int i = 1; i <= 9; i++) {
            add(h, n, g, s, hex2(0x40 + i), "互控通道 " + (10 + i) + "（IM 码 \"" + (10 + i) + "\"，+0x30）；1-5 兼 arcType=1 压力档", G_WAVE,
                    "WaveShockChannelFactory.java:120-127 WaveShockIds.java:116 ClassicModeFragment.java:751");
        }
        add(h, n, g, s, "50", "压力档停止（arcType=2）", G_PRESS, "ClassicModeFragment.java:739");
        for (int i = 1; i <= 5; i++) {
            add(h, n, g, s, hex2(0x50 + i), "亮度 " + i + "（arcType=2 机型同字节＝压力档 " + i + "）", G_BRIGHT,
                    "ClassicModeFragment.java:569-577,760");
        }

        HEX = h.toArray(new String[0]);
        NAME = n.toArray(new String[0]);
        GROUP = g.toArray(new String[0]);
        SRC = s.toArray(new String[0]);
    }

    private static String hex2(int v) {
        String t = Integer.toHexString(v).toUpperCase();
        return t.length() < 2 ? "0" + t : t;
    }

    private static void add(List<String> h, List<String> n, List<String> g, List<String> s,
                            String hex, String name, String group, String src) {
        h.add(hex);
        n.add(name);
        g.add(group);
        s.add(src);
    }

    // ---------------------------------------------------------------- 查询
    public static int indexOf(String hex) {
        if (hex == null) return -1;
        String k = hex.trim().toUpperCase();
        for (int i = 0; i < HEX.length; i++) if (HEX[i].equals(k)) return i;
        return -1;
    }

    /** 是否为本版本实证过的命令（防止 0x1A-0x1F 这类无产出方的字节被误发） */
    public static boolean isEvidenced(String hex) {
        return indexOf(hex) >= 0;
    }

    public static String nameOf(String hex) {
        int i = indexOf(hex);
        return i < 0 ? "未证实" : NAME[i];
    }

    public static String groupOf(String hex) {
        int i = indexOf(hex);
        return i < 0 ? "?" : GROUP[i];
    }

    public static int size() {
        return HEX.length;
    }

    public static String[] byGroup(String group) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < HEX.length; i++) if (GROUP[i].equals(group)) out.add(HEX[i]);
        return out.toArray(new String[0]);
    }

    // ---------------------------------------------------------------- 档位映射
    /** 档位 gear(1..10) -> 命令字节；返回 -1 表示该体系无此档 */
    public static int gearByte(int profile, int gear) {
        if (gear < 1) gear = 1;
        switch (profile) {
            case P_9:
                if (gear > 9) return -1;
                return gear;                       // 0x01..0x09
            case P_10:
                if (gear > 10) return -1;
                return gear >= 10 ? 0x20 : 0x10 + gear;   // 0x11..0x19 / 0x20
            case P_SPECIAL:
                if (gear > 10) return -1;
                if (gear <= 3) return 0x01;        // 11-13 -> 01
                if (gear <= 6) return 0x02;        // 14-16 -> 02
                return 0x03;                       // 17-20 -> 03
            case P_ARC40:
                return gear > 5 ? -1 : 0x40 + gear;
            case P_ARC50:
                return gear > 5 ? -1 : 0x50 + gear;
            default:
                return -1;
        }
    }

    /** 压力档 lv(1..5) -> 命令字节 */
    public static int pressByte(int profile, int lv) {
        if (lv < 1) lv = 1;
        if (lv > 5) lv = 5;
        switch (profile) {
            case P_ARC40:
                return 0x40 + lv;                  // 0x41..0x45
            case P_ARC50:
                return 0x50 + lv;                  // 0x51..0x55
            default:
                return 0x10 + lv;                  // 0x11..0x15（arcType=0 分支）
        }
    }

    /** 该体系的"停止"字节 */
    public static int stopByte(int profile) {
        switch (profile) {
            case P_ARC40:
                return 0x40;
            case P_ARC50:
                return 0x50;
            case P_WAVE_STOP_PROFILE:
                return 0x30;
            default:
                return 0x00;
        }
    }

    /** 伪 profile：互控停止用 0x30 */
    public static final int P_WAVE_STOP_PROFILE = 99;

    public static int channelByte(int n) {
        if (n < 1) n = 1;
        if (n > 9) n = 9;
        return 0x30 + n;                        // 0x31..0x39
    }

    public static int channelExtByte(int n) {
        if (n < 1) n = 1;
        if (n > 9) n = 9;
        return 0x40 + n;                        // 0x41..0x49
    }

    // ---------------------------------------------------------------- 执行
    /** 发一条命令（带实证校验 + 日志）；返回"这一条是否被接受发出"——跨线程时不谎报成功 */
    public static boolean play(String hex) {
        if (hex == null) return false;
        String k = hex.trim().toUpperCase();
        if (!isEvidenced(k)) {
            PulseEngine.log("mode", "REFUSE " + k + " (无实证空口帧)");
            return false;
        }
        Gear.sendHex(k);
        boolean onMain = android.os.Looper.myLooper() == android.os.Looper.getMainLooper();
        if (!onMain) {
            PulseEngine.log("mode", k + " " + nameOf(k) + " queued");
            return true;                       // 只是入队，真实结果看 /status 的 code
        }
        boolean ok = Gear.lastCode == 0;
        PulseEngine.log("mode", k + " " + nameOf(k) + (ok ? " ok" : " err=" + Gear.lastCode));
        return ok;
    }

    public static boolean gear(int profile, int g) {
        int b = gearByte(profile, g);
        if (b < 0) {
            PulseEngine.log("mode", "gear " + g + " 超出 " + PROFILE_NAME[clampProfile(profile)]);
            return false;
        }
        return play(hex2(b));
    }

    public static boolean press(int profile, int lv) {
        return play(hex2(pressByte(profile, lv)));
    }

    public static boolean channel(int n) {
        return play(hex2(channelByte(n)));
    }

    public static boolean channelExt(int n) {
        return play(hex2(channelExtByte(n)));
    }

    public static boolean stop(int profile) {
        return play(hex2(stopByte(profile)));
    }

    /** 手写板一次采样：x,y ∈ [0,1] → 档位 + 强度 */
    public static boolean pad(int profile, double x, double y) {
        double cx = clamp01(x);
        double cy = clamp01(y);
        int g = 1 + (int) Math.round(cx * 9.0);          // 1..10
        int lv = pressByte(profile, 1 + (int) Math.round((1.0 - cy) * 4.0)); // 上=5 下=1
        int gb = gearByte(profile, g);
        if (gb < 0) gb = gearByte(profile, 9);
        if (gb < 0) return false;
        boolean a = play(hex2(gb));
        if (lv != gb) play(hex2(lv));
        return a;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static int clampProfile(int p) {
        return (p >= 0 && p < PROFILE_NAME.length) ? p : 0;
    }

    // ---------------------------------------------------------------- 预设（名字 → 命令）
    public static final String[] PRESET_NAME = {
            "停止", "节奏1", "节奏2", "节奏3", "节奏4", "节奏5", "节奏6", "节奏7", "节奏8", "节奏9",
            "声音跟随", "档位1", "档位2", "档位3", "档位4", "档位5", "档位6", "档位7", "档位8", "档位9", "档位10",
            "加热开", "加热关", "放电开", "放电关", "声控开", "声控关", "互动",
            "互控停", "通道1", "通道2", "通道3", "通道4", "通道5", "通道6", "通道7", "通道8", "通道9",
            "扩展1", "扩展2", "扩展3", "扩展4", "扩展5", "扩展6", "扩展7", "扩展8", "扩展9",
            "亮度1", "亮度2", "亮度3", "亮度4", "亮度5", "压力停"
    };

    /** 与 PRESET_NAME 一一对应的 hex（档位按 10 档体系，压缩机型请改用 gear()） */
    public static final String[] PRESET_HEX = {
            "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
            "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "25", "24", "27", "26", "29", "28", "20",
            "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "52", "53", "54", "55", "50"
    };

    public static String presetHex(String name) {
        if (name == null) return null;
        for (int i = 0; i < PRESET_NAME.length; i++) if (PRESET_NAME[i].equals(name)) return PRESET_HEX[i];
        return null;
    }

    public static boolean preset(String name) {
        String hx = presetHex(name);
        if (hx == null) {
            PulseEngine.log("mode", "preset 未知: " + name);
            return false;
        }
        return play(hx);
    }

    // ---------------------------------------------------------------- 文档用文本
    public static String tableText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < HEX.length; i++) {
            sb.append(HEX[i]).append("  ").append(GROUP[i]).append("  ").append(NAME[i]).append('\n');
        }
        return sb.toString();
    }
}
