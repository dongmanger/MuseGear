package com.musegear;

/**
 * AloneRhythmPattern 单人节奏（原厂 InteractBleHelper.f(Intent) 逆向结果）
 *
 * 来源：classes5.dex -> baksmali -> InteractBleHelper.smali
 * 方法：.method public final f(Landroid/content/Intent;)V  (行 291..1544)
 * 输入：Intent extra "AloneRhythmData"
 *
 * 广播模式（noBleBluetooth=true，默认）→ q(cmd) 即发 cmd，1 秒后补 0x10：
 *   "0"->49  "1"->48  "2"->47  "3"->46  "4"->45
 *   "5"->44  "6"->43  "7"->42  "8"->41  "9"->39
 *
 * GATT 模式（noBleBluetooth=false）→ t([cmd,0x00], AA1)：
 *   "0"->49  "1"->48  "2"->47  "3"->46  "4"->45  "5"->44  "6"->43  "7"->42  "8"->41  "9"->39
 *   "11"->38 "12"->37 "13"->36 "14"->35 "15"->34 "16"->33 "17"->32 "18"->31 "19"->30
 *   "20"->00(停)  "30"->40
 */
public final class Alone {

    private Alone() {}

    /** AloneRhythmData 值 -> 单字节命令（十六进制串） */
    public static String cmdFor(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.equals("0")) return "49";
        if (s.equals("1")) return "48";
        if (s.equals("2")) return "47";
        if (s.equals("3")) return "46";
        if (s.equals("4")) return "45";
        if (s.equals("5")) return "44";
        if (s.equals("6")) return "43";
        if (s.equals("7")) return "42";
        if (s.equals("8")) return "41";
        if (s.equals("9")) return "39";
        // GATT 扩展档（2.4G 广播路径原厂不产这些，保留作映射说明）
        if (s.equals("11")) return "38";
        if (s.equals("12")) return "37";
        if (s.equals("13")) return "36";
        if (s.equals("14")) return "35";
        if (s.equals("15")) return "34";
        if (s.equals("16")) return "33";
        if (s.equals("17")) return "32";
        if (s.equals("18")) return "31";
        if (s.equals("19")) return "30";
        if (s.equals("20")) return "00";
        if (s.equals("30")) return "40";
        return null;
    }

    /** 发一条节奏档位；原厂 q() 语义 = 发完 1 秒补 0x10 */
    public static boolean play(String input, boolean fallback10) {
        String c = cmdFor(input);
        if (c == null) return false;
        Gear.profile = 1;
        Gear.sendHex(c);
        if (fallback10) {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(new Runnable() {
                public void run() { Gear.sendHex("10"); }
            }, 1000);
        }
        return true;
    }

    /** 序列播放：例如 "0,1,2,3,4,5,6,7,8,9"，每步 gapMs */
    public static int playSeq(String csv, int gapMs) {
        if (csv == null) return 0;
        final String[] parts = csv.split(",");
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        int n = 0;
        for (final String p : parts) {
            final String v = p.trim();
            if (cmdFor(v) == null) continue;
            h.postDelayed(new Runnable() {
                public void run() { play(v, false); }
            }, n * (long) gapMs);
            n++;
        }
        return n;
    }
}
