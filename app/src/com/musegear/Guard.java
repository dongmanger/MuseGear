package com.musegear;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 后台常驻：忽略电池优化 + 前台服务保活。
 *
 * 广播式玩具最容易死在 Doze/省电策略上：息屏几分钟后 advertiser 被系统回收，
 * 表现为"手机不动了"。这里把两件事做掉：
 *   1) 申请忽略电池优化（REQUEST_IGNORE_BATTERY_OPTIMIZATIONS，一次授权长期有效）
 *   2) KeepAliveService 前台服务 + PARTIAL_WAKE_LOCK，保证进程与续播循环活着
 */
public final class Guard {

    private Guard() {}

    /** 是否已在电池优化白名单 */
    public static boolean ignoring(Context c) {
        try {
            PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return false;
            return Build.VERSION.SDK_INT >= 23 && pm.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    public static String status(Context c) {
        return "电池优化: " + (ignoring(c) ? "已豁免" : "受限") + " · 常驻: " + KeepAliveService.state();
    }

    /**
     * 弹系统授权框。部分 ROM 屏蔽该 Intent，自动回退到「电池优化设置页」。
     * @return true 表示已拉起系统界面（不代表用户点了允许）
     */
    public static boolean request(Context c) {
        if (ignoring(c)) {
            PulseEngine.log("guard", "already ignoring battery optimizations");
            return true;
        }
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            PulseEngine.log("guard", "request ignore-battery-optimizations");
            return true;
        } catch (Throwable t) {
            PulseEngine.log("guard", "request failed: " + t.getClass().getSimpleName() + " -> open settings");
            return openSettings(c);
        }
    }

    public static boolean openSettings(Context c) {
        try {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            PulseEngine.log("guard", "open settings failed: " + t.getClass().getSimpleName());
            return false;
        }
    }

    public static void start(Context c) {
        try {
            Intent i = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
            PulseEngine.log("guard", "keepalive start");
        } catch (Throwable t) {
            PulseEngine.log("guard", "keepalive start failed: " + t.getClass().getSimpleName());
        }
    }

    public static void stop(Context c) {
        try {
            c.stopService(new Intent(c, KeepAliveService.class));
            PulseEngine.log("guard", "keepalive stop");
        } catch (Throwable t) {
            PulseEngine.log("guard", "keepalive stop failed: " + t.getClass().getSimpleName());
        }
    }
}
