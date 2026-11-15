package com.musegear;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * 脉冲引擎 + 音乐节奏引擎 + 事件日志
 */
public final class PulseEngine {

    // ---------- 事件日志（回传给电脑端） ----------
    private static final int LOG_MAX = 200;
    private static final Deque<String> LOG = new ArrayDeque<>();
    private static long seq = 0;

    public static void log(String tag, String msg) {
        synchronized (LOG) {
            seq++;
            LOG.addLast(String.format(Locale.US, "%d|%d|%s|%s", System.currentTimeMillis(), seq, tag, msg));
            while (LOG.size() > LOG_MAX) LOG.removeFirst();
        }
    }

    public static String jsonLog(long sinceSeq) {
        StringBuilder sb = new StringBuilder("[");
        synchronized (LOG) {
            boolean first = true;
            for (String s : LOG) {
                String[] p = s.split("\\|", 4);
                if (p.length < 4) continue;
                long q = Long.parseLong(p[1]);
                if (q <= sinceSeq) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"t\":").append(p[0])
                  .append(",\"seq\":").append(q)
                  .append(",\"tag\":\"").append(p[2]).append("\"")
                  .append(",\"msg\":\"").append(p[3].replace("\"", "'")).append("\"}");
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static long lastSeq() { synchronized (LOG) { return seq; } }

    /** 最近 n 条原始日志行（"ms|seq|tag|msg"），供 Logs/UI 展示与导出 */
    public static String[] tailLines(int n) {
        synchronized (LOG) {
            int size = LOG.size();
            if (n > size) n = size;
            String[] out = new String[n];
            int skip = size - n;
            int i = 0;
            for (String s : LOG) {
                if (skip-- > 0) continue;
                out[i++] = s;
            }
            return out;
        }
    }

    /** 全部日志行（导出用） */
    public static String[] allLines() {
        synchronized (LOG) { return LOG.toArray(new String[0]); }
    }

    public static int logCount() { synchronized (LOG) { return LOG.size(); } }

    // ---------- 脉冲模式 ----------
    private static final Handler H = new Handler(Looper.getMainLooper());
    private static Runnable pulseR;
    private static volatile boolean pulseOn = false;
    private static volatile double pulseHz = 2.0;
    private static volatile int pulseLevel = 9;
    private static volatile double pulseDuty = 0.5;

    public static String pulseStatus() {
        return String.format(Locale.US, "{\"on\":%b,\"hz\":%.2f,\"level\":%d,\"duty\":%.2f}",
                pulseOn, pulseHz, pulseLevel, pulseDuty);
    }

    public static synchronized void startPulse(double hz, int level, double duty) {
        if (hz < 0.2) hz = 0.2;
        if (hz > 20) hz = 20;
        if (level < 1) level = 1;
        if (level > 9) level = 9;
        if (duty < 0.05) duty = 0.05;
        if (duty > 0.95) duty = 0.95;
        pulseHz = hz; pulseLevel = level; pulseDuty = duty;
        stopPulse(false);
        pulseOn = true;
        final long period = (long) (1000.0 / hz);
        final long onMs = Math.max(60, (long) (period * duty));
        final long offMs = Math.max(20, period - onMs);
        log("pulse", String.format(Locale.US, "start hz=%.2f level=%d duty=%.2f period=%dms", hz, level, duty, period));
        pulseR = new Runnable() {
            public void run() {
                if (!pulseOn) return;
                Gear.level(pulseLevel);
                H.postDelayed(new Runnable() {
                    public void run() {
                        if (!pulseOn) return;
                        Gear.level(0);
                        if (pulseR != null) H.postDelayed(pulseR, offMs);
                    }
                }, onMs);
            }
        };
        H.post(pulseR);
    }

    public static synchronized void stopPulse(boolean sendZero) {
        pulseOn = false;
        if (pulseR != null) { H.removeCallbacks(pulseR); pulseR = null; }
        if (sendZero) { Gear.level(0); log("pulse", "stop"); }
    }

    public static boolean pulseActive() { return pulseOn; }

    // ---------- 音乐节奏模式 ----------
    private static volatile boolean musicOn = false;
    private static volatile double musicSens = 0.65;
    private static Thread musicThread;
    private static AudioRecord record;
    private static volatile int musicLevel = 0;

    public static String musicStatus() {
        return String.format(Locale.US, "{\"on\":%b,\"sens\":%.2f,\"level\":%d}", musicOn, musicSens, musicLevel);
    }

    public static synchronized void startMusic(double sens) {
        if (sens < 0.1) sens = 0.1;
        if (sens > 1.0) sens = 1.0;
        musicSens = sens;
        if (musicOn) { log("music", "already running, sens=" + sens); return; }

        int rate = 16000;
        int minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 8192));
        } catch (Throwable t) {
            log("music", "AudioRecord fail: " + t.getClass().getSimpleName());
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            log("music", "mic not ready (permission?)");
            return;
        }
        musicOn = true;
        record.startRecording();
        log("music", "start sens=" + sens);

        musicThread = new Thread(new Runnable() {
            public void run() {
                short[] buf = new short[1024];
                double peak = 0.02;
                long lastSend = 0;
                while (musicOn) {
                    int n = record.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    double sum = 0;
                    for (int i = 0; i < n; i++) {
                        double v = buf[i] / 32768.0;
                        sum += v * v;
                    }
                    double rms = Math.sqrt(sum / n);
                    peak = Math.max(rms, peak * 0.995);
                    double norm = peak > 1e-6 ? rms / peak : 0;
                    int lv = (int) Math.round(norm * musicSens * 9.0);
                    if (lv < 0) lv = 0;
                    if (lv > 9) lv = 9;
                    long now = System.currentTimeMillis();
                    if (Math.abs(lv - musicLevel) >= 1 && now - lastSend >= 90) {
                        musicLevel = lv;
                        lastSend = now;
                        Gear.level(lv);
                    }
                }
            }
        }, "music-engine");
        musicThread.start();
    }

    public static synchronized void stopMusic() {
        musicOn = false;
        try { if (record != null) record.stop(); } catch (Throwable ignore) {}
        try { if (record != null) record.release(); } catch (Throwable ignore) {}
        record = null;
        musicThread = null;
        Gear.level(0);
        log("music", "stop");
    }

    public static boolean musicActive() { return musicOn; }

    public static void stopAll() {
        stopPulse(true);
        if (musicOn) stopMusic();
        Gear.stopAdvertising();
    }
}
