package com.musegear;

import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;

/**
 * 内置 HTTP API（按钮启动）。设计目标：回传鲁棒性优先。
 *   /health
 *   /status
 *   /gear?level=0..9
 *   /strength?n=1..5
 *   /musicgear?lv=1..10
 *   /pulse?hz=2&level=9&duty=0.5&dur=20
 *   /audio?on=1&mode=beat|amp&sens=0.7&level=9
 *   /stop
 *   /log?since=0
 */
public final class ApiServer {

    public static final int PORT = 8899;
    private static final String TAG = "MuseGear";

    private static ServerSocket server;
    private static Thread thread;
    private static PowerManager.WakeLock sWake;
    private static Context sApp;
    private static volatile boolean running = false;
    private static volatile int calls = 0;
    private static volatile int errors = 0;
    private static volatile long startedAt = 0;

    public static void attach(Context c) { sApp = c == null ? null : c.getApplicationContext(); }

    public static boolean isRunning() { return running; }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            while (ifs != null && ifs.hasMoreElements()) {
                NetworkInterface nif = ifs.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a.getHostAddress().indexOf(':') < 0) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignore) {}
        return "127.0.0.1";
    }

    public static synchronized String start() {
        if (running) return "http://" + localIp() + ":" + PORT;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(PORT), 32);
        } catch (Throwable t) {
            PulseEngine.log("api", "bind fail " + t.getClass().getSimpleName());
            return "ERR:" + t.getClass().getSimpleName();
        }
        try {
            if (sApp != null) {
                PowerManager pm = (PowerManager) sApp.getSystemService(Context.POWER_SERVICE);
                sWake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MuseGear:api");
                sWake.setReferenceCounted(false);
                sWake.acquire();
            }
        } catch (Throwable ignore) {}

        running = true;
        startedAt = System.currentTimeMillis();
        calls = 0;
        errors = 0;
        thread = new Thread(new Runnable() {
            public void run() {
                while (running) {
                    try {
                        final Socket s = server.accept();
                        Thread t = new Thread(new Runnable() { public void run() { handle(s); } }, "api-conn");
                        t.setDaemon(true);
                        t.start();
                    } catch (Throwable t) {
                        if (running) {
                            errors++;
                            try { Thread.sleep(50); } catch (InterruptedException ignore) {}
                        }
                    }
                }
            }
        }, "api-accept");
        thread.setDaemon(true);
        thread.start();
        String url = "http://" + localIp() + ":" + PORT;
        PulseEngine.log("api", "start " + url);
        return url;
    }

    public static synchronized void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignore) {}
        server = null;
        thread = null;
        try { if (sWake != null && sWake.isHeld()) sWake.release(); } catch (Throwable ignore) {}
        PulseEngine.log("api", "stopped");
    }

    private static void handle(Socket s) {
        long t0 = System.currentTimeMillis();
        String path = "/";
        String method = "GET";
        try {
            s.setSoTimeout(5000);
            s.setTcpNoDelay(true);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            String line = in.readLine();
            if (line == null) { close(s); return; }
            String[] parts = line.split(" ");
            if (parts.length > 0) method = parts[0];
            if (parts.length >= 2) path = parts[1];
            String h;
            int guard = 0;
            while ((h = in.readLine()) != null && h.length() > 0 && guard++ < 64) { }

            calls++;
            String q = "";
            int qi = path.indexOf('?');
            if (qi >= 0) { q = path.substring(qi + 1); path = path.substring(0, qi); }

            String body;
            String ctype = "application/json; charset=utf-8";
            if (path.equals("/doc")) {
                body = AutoDoc.html();
                ctype = "text/html; charset=utf-8";
            } else if (path.equals("/doc.txt")) {
                body = AutoDoc.text();
                ctype = "text/plain; charset=utf-8";
            } else if (path.equals("/modes")) {
                body = AutoDoc.modesJson();
            } else if (path.equals("/preset")) {
                String pn = param(q, "name");
                String hx = Modes.presetHex(pn);
                if (hx == null) {
                    body = "{\"ok\":false,\"err\":\"need name\",\"presets\":" + AutoDoc.presetsJson() + "}";
                } else {
                    boolean ok = Modes.preset(pn);
                    body = "{\"ok\":" + ok + ",\"preset\":\"" + pn + "\",\"hex\":\"" + hx
                            + "\",\"cmd\":\"" + Gear.lastCmd + "\"}";
                }
            } else if (path.equals("/health")) {
                body = "{\"ok\":true,\"up\":" + (System.currentTimeMillis() - startedAt) + "}";
            } else if (path.equals("/status")) {
                body = "{\"ok\":true,\"api\":true,\"calls\":" + calls + ",\"errors\":" + errors
                        + ",\"up\":" + (System.currentTimeMillis() - startedAt)
                        + ",\"audio\":" + AudioEngine.status()
                        + ",\"pulse\":" + PulseEngine.pulseStatus()
                        + ",\"gear\":" + Gear.lastCode + ",\"cmd\":\"" + Gear.lastCmd + "\""
                        + ",\"note\":\"" + Gear.lastNote + "\",\"tbl\":" + Rf.size() + "}";
            } else if (path.equals("/gear")) {
                int lv = parseInt(param(q, "level"), 5);
                Gear.level(lv);
                body = "{\"ok\":true,\"gear\":" + lv + ",\"cmd\":\"" + Gear.lastCmd + "\"}";
            } else if (path.equals("/strength")) {
                int n = parseInt(param(q, "n"), 3);
                Gear.strength(n);
                body = "{\"ok\":true,\"strength\":" + n + ",\"cmd\":\"" + Gear.lastCmd + "\"}";
            } else if (path.equals("/musicgear")) {
                int lv = parseInt(param(q, "lv"), 5);
                Gear.music(lv);
                body = "{\"ok\":true,\"musicgear\":" + lv + ",\"cmd\":\"" + Gear.lastCmd + "\"}";
            } else if (path.equals("/pulse")) {
                double hz = parseDouble(param(q, "hz"), 2.0);
                int lv = parseInt(param(q, "level"), 9);
                double duty = parseDouble(param(q, "duty"), 0.5);
                AudioEngine.stop();
                PulseEngine.startPulse(hz, lv, duty);
                int dur = parseInt(param(q, "dur"), 0);
                if (dur > 0) {
                    final int d = Math.min(dur, 600);
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            try { Thread.sleep(d * 1000L); } catch (InterruptedException ignore) {}
                            PulseEngine.stopPulse(true);
                            PulseEngine.log("pulse", "auto-stop " + d + "s");
                        }
                    }, "pulse-dur");
                    t.setDaemon(true);
                    t.start();
                }
                body = "{\"ok\":true,\"pulse\":" + PulseEngine.pulseStatus() + ",\"dur\":" + dur + "}";
            } else if (path.equals("/audio")) {
                int on = parseInt(param(q, "on"), 1);
                if (on == 1) {
                    PulseEngine.stopPulse(true);
                    String m = param(q, "mode");
                    double sens = parseDouble(param(q, "sens"), 0.7);
                    int lvl = parseInt(param(q, "level"), 9);
                    boolean ok = AudioEngine.start(sApp, m, sens, lvl);
                    body = "{\"ok\":" + ok + ",\"audio\":" + AudioEngine.status() + "}";
                } else {
                    AudioEngine.stop();
                    body = "{\"ok\":true,\"audio\":" + AudioEngine.status() + "}";
                }
            } else if (path.equals("/pad")) {

                android.util.DisplayMetrics dm = android.content.res.Resources.getSystem().getDisplayMetrics();
                double r;
                String rs = param(q, "r");
                if (rs != null) {
                    r = parseDouble(rs, 0);
                } else {
                    double nx = parseDouble(param(q, "x"), 0.5);
                    double ny = parseDouble(param(q, "y"), 0.5);
                    double dx = (nx - 0.5) * dm.widthPixels;
                    double dy = (ny - 0.5) * dm.heightPixels;
                    r = Math.sqrt(dx * dx + dy * dy);
                }
                int gear = (int) (r / 60.0);
                if (gear < 0) gear = 0;
                if (gear > 9) gear = 9;
                Gear.profile = 1;
                int cmd = 0x11 + gear;
                Gear.sendHex(String.format("%02X", cmd));
                body = "{\"ok\":true,\"pad\":{\"r\":" + Math.round(r) + ",\"gear\":" + gear
                        + ",\"cmd\":\"" + String.format("%02X", cmd) + "\"}}";
            } else if (path.equals("/padrelease")) {
                Gear.sendHex("00");
                body = "{\"ok\":true,\"release\":true,\"cmd\":\"00\"}";
            } else if (path.equals("/shake")) {

                double mag = parseDouble(param(q, "mag"), 26);
                int level = (int) ((mag - 20) / 5) + 1;
                Gear.profile = 0;
                int cmd;
                if (level <= 0) cmd = 0x11;
                else if (level >= 9) cmd = 0x20;
                else cmd = 0x11 + level;
                Gear.sendHex(String.format("%02X", cmd));
                body = "{\"ok\":true,\"shake\":{\"mag\":" + mag + ",\"level\":" + level
                        + ",\"cmd\":\"" + String.format("%02X", cmd) + "\"}}";
            } else if (path.equals("/raw")) {
                String hx = param(q, "hex");
                if (hx == null || hx.length() < 2) {
                    body = "{\"ok\":false,\"err\":\"need hex\"}";
                } else {
                    Gear.sendHex(hx.replace(" ", "").toUpperCase());
                    body = "{\"ok\":true,\"raw\":\"" + hx + "\",\"cmd\":\"" + Gear.lastCmd + "\"}";
                }
            } else if (path.equals("/channel")) {

                int n = parseInt(param(q, "n"), 5);
                if (n < 1) n = 1;
                if (n > 9) n = 9;
                Gear.profile = 1;
                int c = 0x30 + n;
                Gear.sendHex(String.format("%02X", c));
                body = "{\"ok\":true,\"channel\":" + n + ",\"cmd\":\"" + String.format("%02X", c) + "\"}";
            } else if (path.equals("/channelstop")) {
                String k = param(q, "k");
                int c = 0x30;
                if ("ext".equalsIgnoreCase(k) || "arc1".equalsIgnoreCase(k)) c = 0x40;
                else if ("arc2".equalsIgnoreCase(k)) c = 0x50;
                Gear.sendHex(String.format("%02X", c));
                body = "{\"ok\":true,\"cmd\":\"" + String.format("%02X", c) + "\"}";
            } else if (path.equals("/interact")) {

                int on = parseInt(param(q, "on"), 1);
                Gear.profile = 1;
                if (on == 1) {
                    Gear.sendHex("24");
                    body = "{\"ok\":true,\"interact\":\"keepalive\",\"cmd\":\"24\"}";
                } else {
                    Gear.sendHex("00");
                    body = "{\"ok\":true,\"interact\":\"off\",\"cmd\":\"00\"}";
                }
            } else if (path.equals("/interactcrit")) {

                Gear.profile = 1;
                Gear.sendHex("20");
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(1000); } catch (InterruptedException ignore) {}
                        Gear.sendHex("10");
                        PulseEngine.log("interact", "crit fallback 10");
                    }
                }, "crit-fallback");
                t.setDaemon(true);
                t.start();
                body = "{\"ok\":true,\"crit\":\"20\",\"fallback\":\"10\"}";
            } else if (path.equals("/seq")) {

                String hx = param(q, "hex");
                int gap = parseInt(param(q, "gap"), 100);
                if (gap < 20) gap = 20;
                if (hx == null) {
                    body = "{\"ok\":false,\"err\":\"need hex=01,02\"}";
                } else {
                    final String[] steps = hx.split(",");
                    final int g = gap;
                    Thread t = new Thread(new Runnable() {
                        public void run() {
                            for (String s : steps) {
                                Gear.sendHex(s.trim().toUpperCase());
                                try { Thread.sleep(g); } catch (InterruptedException ignore) {}
                            }
                            PulseEngine.log("seq", "done " + steps.length + " steps");
                        }
                    }, "seq-run");
                    t.setDaemon(true);
                    t.start();
                    body = "{\"ok\":true,\"seq\":" + steps.length + ",\"gap\":" + gap + "}";
                }            } else if (path.equals("/idle")) {
                // 0x10：声控低音量/静音心跳（非音乐帧）
                Gear.profile = 0;
                Gear.sendHex("10");
                body = "{\"ok\":true,\"idle\":\"10\",\"cmd\":\"" + Gear.lastCmd + "\"}";
            } else if (path.equals("/brightness")) {
                // 51..55 亮度 1..5（arcType=2 机型同字节也是压力档）
                int n = parseInt(param(q, "n"), 3);
                Gear.strength(n);
                body = "{\"ok\":true,\"brightness\":" + n + ",\"cmd\":\"" + Gear.lastCmd + "\"}";
            } else if (path.equals("/press")) {
                // 弧压力：arc=1 -> 41..45；arc=2 -> 51..55
                int g2 = parseInt(param(q, "g"), 3);
                if (g2 < 1) g2 = 1;
                if (g2 > 5) g2 = 5;
                int arc = parseInt(param(q, "arc"), 1);
                int c2 = (arc == 2) ? (0x50 + g2) : (0x40 + g2);
                Gear.profile = 1;
                Gear.sendHex(String.format("%02X", c2));
                body = "{\"ok\":true,\"press\":" + g2 + ",\"arc\":" + arc
                        + ",\"cmd\":\"" + String.format("%02X", c2) + "\"}";            } else if (path.equals("/alone")) {
                // AloneRhythmPattern：a=0..9 / 11..19 / 20(停) / 30；fb=1 时 1 秒后补 0x10（原厂 q() 语义）
                String av = param(q, "a");
                if (av == null) {
                    body = "{\"ok\":false,\"err\":\"need a=0..9|11..19|20|30\"}";
                } else {
                    boolean fb = parseInt(param(q, "fb"), 0) == 1;
                    String cc = Alone.cmdFor(av);
                    boolean ok = Alone.play(av, fb);
                    body = "{\"ok\":" + ok + ",\"alone\":\"" + av + "\",\"cmd\":\"" + (cc == null ? "" : cc)
                            + "\",\"fb10\":" + fb + "}";
                }
            } else if (path.equals("/aloneseq")) {
                // 序列：a=0,1,2,3&gap=400
                String sv = param(q, "a");
                int gap2 = parseInt(param(q, "gap"), 400);
                if (gap2 < 60) gap2 = 60;
                if (sv == null) {
                    body = "{\"ok\":false,\"err\":\"need a=0,1,2\"}";
                } else {
                    int n2 = Alone.playSeq(sv, gap2);
                    PulseEngine.log("alone", "seq " + sv + " steps=" + n2 + " gap=" + gap2);
                    body = "{\"ok\":true,\"steps\":" + n2 + ",\"gap\":" + gap2 + "}";
                }            } else if (path.equals("/stop")) {
                PulseEngine.stopPulse(true);
                AudioEngine.stop();
                Gear.stopAdvertising();
                Gear.level(0);
                body = "{\"ok\":true,\"stopped\":true}";
            } else if (path.equals("/log")) {
                long since = (long) parseDouble(param(q, "since"), 0);
                body = "{\"ok\":true,\"now\":" + PulseEngine.lastSeq() + ",\"events\":" + PulseEngine.jsonLog(since) + "}";
            } else {
                body = "{\"ok\":false,\"err\":\"unknown\",\"paths\":[\"/health\",\"/status\",\"/gear?level=5\","
                        + "\"/strength?n=3\",\"/musicgear?lv=6\",\"/pulse?hz=2&level=9&duty=0.5&dur=20\","
                        + "\"/audio?on=1&mode=beat&sens=0.7&level=9\",\"/audio?on=0\",\"/stop\",\"/log?since=0\"]}";
            }

            byte[] b = body.getBytes("UTF-8");
            OutputStream os = s.getOutputStream();
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: ").append(ctype).append("\r\n");
            sb.append("Access-Control-Allow-Origin: *\r\n");
            sb.append("Cache-Control: no-store\r\n");
            sb.append("X-Resp-Ms: ").append(System.currentTimeMillis() - t0).append("\r\n");
            sb.append("Content-Length: ").append(b.length).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            os.write(sb.toString().getBytes("UTF-8"));
            if (!"HEAD".equals(method)) os.write(b);
            os.flush();
            close(s);
        } catch (Throwable t) {
            errors++;
            Log.w(TAG, "api handle: " + t.getClass().getSimpleName());
            try {
                byte[] b = ("{\"ok\":false,\"err\":\"" + t.getClass().getSimpleName() + "\"}").getBytes("UTF-8");
                OutputStream os = s.getOutputStream();
                os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n"
                        + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                os.write(b);
                os.flush();
            } catch (Throwable ignore) {}
            close(s);
        }
    }

    private static void close(Socket s) {
        try { if (s != null) s.close(); } catch (Throwable ignore) {}
    }

    private static String param(String q, String key) {
        if (q == null) return null;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key)) return kv.substring(i + 1);
        }
        return null;
    }

    static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Throwable t) { return def; }
    }

    static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Throwable t) { return def; }
    }
}
