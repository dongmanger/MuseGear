package com.musegear;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 日志门面：读 PulseEngine 的环形事件日志，做「app 内展示 + 一键导出」。
 * 不另建一套日志系统，全部事件仍由 PulseEngine.log() 统一记录（/log 接口同源）。
 */
public final class Logs {

    private Logs() {}

    private static final SimpleDateFormat HMS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat FILE = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);

    /** 把 "ms|seq|tag|msg" 行格式化成 "12:00:01.123 [mode] 41 档位5" */
    public static String pretty(String raw) {
        if (raw == null) return "";
        String[] p = raw.split("\\|", 4);
        if (p.length < 4) return raw;
        long t;
        try {
            t = Long.parseLong(p[0]);
        } catch (NumberFormatException e) {
            return raw;
        }
        return HMS.format(new Date(t)) + " [" + p[2] + "] " + p[3];
    }

    public static String[] tail(int n) {
        String[] raw = PulseEngine.tailLines(n);
        String[] out = new String[raw.length];
        for (int i = 0; i < raw.length; i++) out[i] = pretty(raw[i]);
        return out;
    }

    /** 给 UI 用的紧凑文本（新→旧倒排，最多 n 行） */
    public static String tailText(int n) {
        String[] rows = tail(n);
        StringBuilder sb = new StringBuilder();
        for (int i = rows.length - 1; i >= 0; i--) {
            sb.append(rows[i]).append('\n');
        }
        if (sb.length() == 0) sb.append("(暂无事件)");
        return sb.toString();
    }

    public static int count() {
        return PulseEngine.logCount();
    }

    public static String summary() {
        return "事件 " + count() + " 条 · 帧表 " + Rf.size() + " 条 · 命令 " + Modes.size() + " 条";
    }

    /** 导出到 App 外部私有目录，返回文件绝对路径（失败返回错误说明） */
    public static String save(Context c) {
        try {
            File dir = c.getExternalFilesDir("logs");
            if (dir == null) dir = new File(c.getFilesDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) return "mkdir 失败: " + dir;
            File f = new File(dir, "musegear-" + FILE.format(new Date()) + ".log");
            String[] all = PulseEngine.allLines();
            FileOutputStream out = new FileOutputStream(f);
            try {
                out.write(("MuseGear v" + AutoDoc.VERSION + " log  count=" + all.length + "\n").getBytes("UTF-8"));
                out.write(("gear.lastCmd=" + Gear.lastCmd + " code=" + Gear.lastCode + " note=" + Gear.lastNote + "\n").getBytes("UTF-8"));
                out.write(("----------------------------------------\n").getBytes("UTF-8"));
                for (String s : all) out.write((pretty(s) + "\n").getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
            return f.getAbsolutePath();
        } catch (Throwable t) {
            return "导出失败: " + t.getClass().getSimpleName();
        }
    }
}
