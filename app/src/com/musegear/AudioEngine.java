package com.musegear;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;

/**
 * 音频跟随引擎（对齐原厂 VisualizerHelper 的实现，并加了真·鼓点检测）
 *
 * 原厂做法（逆向结论）：
 *   Visualizer(0) 取系统全局输出混音（第三方音乐 App 也吃得到），时域波形，
 *   算 RMS(0..127) → gear = floor((min(amp,80)/80)^2 * 10)，节流 1000ms。
 *   失败降级 MediaRecorder MIC + getMaxAmplitude()，线性映射。
 *
 * 原厂没有的东西：鼓点（onset）检测。这里加一档 mode=beat：
 *   用波形 RMS 与 EMA 基线的差值做 onset，超过自适应阈值就判定为一下鼓点，
 *   立刻顶到 maxLevel，120ms 后回落，形成「每一下鼓点顶一下」。
 *
 * 音乐档位命令（原厂 MusicBleCommandHelper + SpecialGearCommandMapper）：
 *   gear 1..9 -> 0x11..0x19 ；gear 10 -> 0x20 ；停 -> 0x00
 */
public final class AudioEngine {

    public static final String SRC_NONE = "none";
    public static final String SRC_VIS = "visualizer";
    public static final String SRC_MIC = "microphone";

    private static final Handler H = new Handler(Looper.getMainLooper());

    private static volatile boolean running = false;
    private static volatile String source = SRC_NONE;
    private static volatile String mode = "beat";      // beat | amp
    private static volatile double sens = 0.7;
    private static volatile int maxLevel = 9;
    private static volatile int holdMs = 120;
    private static volatile long minInterval = 90;

    private static volatile int lastAmp = 0;
    private static volatile int lastGear = 0;
    private static volatile long beats = 0;
    private static volatile long callbackMs = 0;

    private static Visualizer vis;
    private static MediaRecorder mic;
    private static Thread micThread;
    private static Runnable decayR;
    private static long lastSendAt = 0;
    private static double ema = 0;

    private AudioEngine() {}

    public static String status() {
        return String.format(Locale.US,
                "{\"on\":%b,\"src\":\"%s\",\"mode\":\"%s\",\"sens\":%.2f,\"max\":%d,\"amp\":%d,\"gear\":%d,\"beats\":%d,\"cbMs\":%d}",
                running, source, mode, sens, maxLevel, lastAmp, lastGear, beats, callbackMs);
    }

    public static boolean isRunning() { return running; }

    public static synchronized boolean start(Context ctx, String m, double s, int max) {
        stop();
        mode = (m != null && m.equalsIgnoreCase("amp")) ? "amp" : "beat";
        sens = Math.max(0.05, Math.min(1.0, s));
        maxLevel = Math.max(1, Math.min(9, max));
        running = true;
        ema = 0;
        lastGear = 0;
        beats = 0;

        if (startVisualizer(ctx)) {
            source = SRC_VIS;
            PulseEngine.log("audio", "start visualizer mode=" + mode + " sens=" + sens + " max=" + maxLevel);
            return true;
        }
        if (startMic(ctx)) {
            source = SRC_MIC;
            PulseEngine.log("audio", "start microphone(fallback) mode=" + mode);
            return true;
        }
        running = false;
        source = SRC_NONE;
        PulseEngine.log("audio", "start FAILED (no visualizer, no mic)");
        return false;
    }

    private static boolean startVisualizer(Context ctx) {
        try {
            vis = new Visualizer(0);                     // session 0 = 全局输出混音
            if (vis.getEnabled()) vis.setEnabled(false);
            int[] range = Visualizer.getCaptureSizeRange();
            int size = range[1];                          // 最大 1024
            int rate = Visualizer.getMaxCaptureRate() / 2; // 约 10Hz
            callbackMs = rate <= 0 ? 100 : 1000 / Math.max(1, rate / 1000);
            vis.setCaptureSize(size);
            int r = vis.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                public void onWaveFormDataCapture(Visualizer v, byte[] wf, int samplingRate) {
                    onWave(wf);
                }
                public void onFftDataCapture(Visualizer v, byte[] fft, int samplingRate) {
                    /* 原厂也没用 FFT */
                }
            }, rate, true, false);
            int en = vis.setEnabled(true);
            boolean ok = (en == Visualizer.SUCCESS);
            if (r != Visualizer.SUCCESS || !ok) {
                PulseEngine.log("audio", "visualizer not enabled r=" + r + " ok=" + ok);
                try { vis.release(); } catch (Throwable ignore) {}
                vis = null;
                return false;
            }
            return true;
        } catch (Throwable t) {
            PulseEngine.log("audio", "visualizer ex:" + t.getClass().getSimpleName());
            try { if (vis != null) vis.release(); } catch (Throwable ignore) {}
            vis = null;
            return false;
        }
    }

    private static boolean startMic(Context ctx) {
        try {
            mic = new MediaRecorder();
            mic.setAudioSource(MediaRecorder.AudioSource.MIC);
            mic.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mic.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mic.setOutputFile("/dev/null");
            mic.prepare();
            mic.start();
            callbackMs = 50;
            micThread = new Thread(new Runnable() {
                public void run() {
                    while (running && mic != null) {
                        try {
                            int amp = mic.getMaxAmplitude();   // 0..32767
                            onMicAmp(amp);
                            Thread.sleep(50);
                        } catch (Throwable t) {
                            break;
                        }
                    }
                }
            }, "mic-engine");
            micThread.start();
            return true;
        } catch (Throwable t) {
            PulseEngine.log("audio", "mic ex:" + t.getClass().getSimpleName());
            try { if (mic != null) mic.release(); } catch (Throwable ignore) {}
            mic = null;
            return false;
        }
    }

    // ---------- 波形处理 ----------
    private static void onWave(byte[] wf) {
        if (wf == null || wf.length == 0) return;
        long sum = 0;
        for (byte b : wf) sum += (b & 0xFF);
        double mean = sum / (double) wf.length;
        double acc = 0;
        for (byte b : wf) {
            double d = (b & 0xFF) - mean;
            acc += d * d;
        }
        double rms = Math.sqrt(acc / wf.length);       // 0..127
        lastAmp = (int) Math.round(rms);
        if ("amp".equals(mode)) {
            // 原厂公式：gear = floor((min(amp,80)/80)^2 * 10)，<=0 归 1
            double t = Math.min(rms, 80.0) / 80.0;
            int gear = (int) (t * t * 10);
            if (gear <= 0) gear = 1;
            if (gear > 10) gear = 10;
            sendGear(gear);
        } else {
            detectBeat(rms);
        }
    }

    private static void onMicAmp(int amp) {
        lastAmp = amp;
        if ("amp".equals(mode)) {
            int gear = (int) (Math.min(amp, 32767) * 10.0 / 32767.0);
            if (gear <= 0) gear = 1;
            if (gear > 10) gear = 10;
            sendGear(gear);
        } else {
            detectBeat(amp * 127.0 / 32767.0);
        }
    }

    /** 真·鼓点检测：RMS 相对 EMA 基线的突增 */
    private static void detectBeat(double rms) {
        ema = ema * 0.82 + rms * 0.18;
        double floor = Math.max(1.5, ema);
        double k = 2.2 - 1.6 * sens;          // sens 越大，阈值越低（越敏感）
        if (rms > floor * k && rms > 2.0) {
            beats++;
            int gear = maxLevel;
            sendGear(gear);
            if (decayR != null) H.removeCallbacks(decayR);
            decayR = new Runnable() {
                public void run() {
                    if (running) sendGear(Math.max(1, maxLevel / 3));
                }
            };
            H.postDelayed(decayR, holdMs);
        }
    }

    private static void sendGear(int gear) {
        long now = System.currentTimeMillis();
        if (now - lastSendAt < minInterval) return;
        if (gear == lastGear) return;
        lastSendAt = now;
        lastGear = gear;
        Gear.music(gear);
    }

    public static synchronized void stop() {
        running = false;
        if (decayR != null) { H.removeCallbacks(decayR); decayR = null; }
        try { if (vis != null) { vis.setEnabled(false); vis.release(); } } catch (Throwable ignore) {}
        vis = null;
        try { if (mic != null) { mic.stop(); } } catch (Throwable ignore) {}
        try { if (mic != null) { mic.release(); } } catch (Throwable ignore) {}
        mic = null;
        micThread = null;
        if (SRC_NONE.equals(source) == false) {
            PulseEngine.log("audio", "stop");
        }
        source = SRC_NONE;
        Gear.musicStop();
    }
}
