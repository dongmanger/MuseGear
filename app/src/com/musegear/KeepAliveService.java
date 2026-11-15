package com.musegear;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * 常驻前台服务：保进程、保 WakeLock、保广播续播。
 *
 * 只做三件事：
 *  1) startForeground → 不被系统当低优先级进程杀
 *  2) PARTIAL_WAKE_LOCK → 息屏后 CPU 仍能跑 2.5s 续播
 *  3) 每 30s 打一条心跳日志（可在 App 内日志区/`/log` 看到，便于判断有没有被系统掐掉）
 */
public class KeepAliveService extends Service {

    public static final String CHANNEL = "musegear-keepalive";
    public static final int NOTIF_ID = 1001;

    private static volatile boolean running = false;
    private static volatile long startedAt = 0;

    private PowerManager.WakeLock lock;
    private Handler h;
    private Runnable beat;

    public static boolean isRunning() {
        return running;
    }

    public static String state() {
        if (!running) return "关";
        long s = (System.currentTimeMillis() - startedAt) / 1000;
        return "开(" + s + "s)";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Gear.init(getApplicationContext());
        createChannel();
        try {
            startForeground(NOTIF_ID, buildNotification());
        } catch (Throwable t) {
            PulseEngine.log("keepalive", "startForeground fail: " + t.getClass().getSimpleName());
        }
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MuseGear::keepalive");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Throwable t) {
            PulseEngine.log("keepalive", "wakelock fail: " + t.getClass().getSimpleName());
        }

        running = true;
        startedAt = System.currentTimeMillis();
        PulseEngine.log("keepalive", "service onCreate, wakelock=" + (lock != null && lock.isHeld()));

        h = new Handler(Looper.getMainLooper());
        beat = new Runnable() {
            public void run() {
                Gear.keepWarm();
                PulseEngine.log("keepalive", "beat up=" + ((System.currentTimeMillis() - startedAt) / 1000)
                        + "s last=" + Gear.lastCmd + " code=" + Gear.lastCode);
                h.postDelayed(this, 30000);
            }
        };
        h.postDelayed(beat, 30000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (h != null && beat != null) h.removeCallbacks(beat);
        if (lock != null && lock.isHeld()) {
            try { lock.release(); } catch (Throwable ignore) {}
        }
        PulseEngine.log("keepalive", "service onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "MuseGear 常驻", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("保持 2.4G 广播不被系统回收");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        return b.setContentTitle("MuseGear 常驻中")
                .setContentText("2.4G 广播保活 · 点击回到 App")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
