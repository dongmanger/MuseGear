package com.musegear;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status, diag, apiText, logText, guardText, docText, padInfo;
    private PadView pad;
    private SeekBar bar;
    private int level = 5;
    private int padProfile = Modes.P_10;
    private double hz = 2.0;
    private double sens = 0.65;
    private boolean granted = true;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Gear.init(this);
        ApiServer.attach(this);
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                granted = false;
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.RECORD_AUDIO}, 1);
            }
        } else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        }
        setContentView(buildUi());
        tick();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        granted = r.length > 0;
        for (int x : r) if (x != PackageManager.PERMISSION_GRANTED) granted = false;
        refresh();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("MuseGear v" + AutoDoc.VERSION);
        title.setTextSize(21);
        title.setTextColor(Color.parseColor("#D81B60"));
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        diag = new TextView(this);
        diag.setTextSize(11);
        diag.setTextColor(Color.parseColor("#555555"));
        diag.setPadding(0, dp(6), 0, dp(6));
        root.addView(diag);

        status = new TextView(this);
        status.setTextSize(15);
        status.setTextColor(Color.BLACK);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        root.addView(label("HTTP API 电脑端可调"));
        LinearLayout apiRow = new LinearLayout(this);
        apiRow.addView(big("启动 API", new View.OnClickListener() {
            public void onClick(View v) { apiText.setText(ApiServer.start()); }
        }));
        apiRow.addView(big("停止 API", new View.OnClickListener() {
            public void onClick(View v) { ApiServer.stop(); apiText.setText("API stopped"); }
        }));
        apiRow.addView(big("刷新地址", new View.OnClickListener() {
            public void onClick(View v) {
                apiText.setText(ApiServer.isRunning() ? "http://" + ApiServer.localIp() + ":8899" : "API not running");
            }
        }));
        root.addView(apiRow);

        apiText = new TextView(this);
        apiText.setTextSize(12);
        apiText.setTextColor(Color.parseColor("#00695C"));
        apiText.setPadding(0, dp(6), 0, dp(6));
        apiText.setText(ApiServer.isRunning() ? "running" : "API 未启动");
        root.addView(apiText);

        root.addView(label("档位"));
        bar = new SeekBar(this);
        bar.setMax(9);
        bar.setProgress(level);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                level = p;
                if (fromUser) { PulseEngine.stopPulse(true); Gear.level(p); }
            }
            public void onStartTrackingTouch(SeekBar s) { PulseEngine.stopPulse(true); }
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(bar);

        LinearLayout row1 = new LinearLayout(this);
        for (int i = 1; i <= 9; i++) {
            final int lv = i;
            Button bt = btn(String.valueOf(i));
            bt.setTextSize(13);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { PulseEngine.stopPulse(true); level = lv; bar.setProgress(lv); Gear.level(lv); }
            });
            row1.addView(bt, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(row1);

        root.addView(label("强度 51-55"));
        LinearLayout row2 = new LinearLayout(this);
        for (int i = 1; i <= 5; i++) {
            final int n = i;
            Button bt = btn("S" + i);
            bt.setTextSize(12);
            bt.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Gear.strength(n); }
            });
            row2.addView(bt, new LinearLayout.LayoutParams(0, dp(44), 1f));
        }
        root.addView(row2);

        // ---------------- 预设模式：还原原厂全部预设 ----------------
        root.addView(label("预设模式（" + Modes.size() + " 条实证命令 / " + Modes.PRESET_NAME.length + " 个预设）"));
        LinearLayout prow = null;
        for (int i = 0; i < Modes.PRESET_NAME.length; i++) {
            if (i % 6 == 0) { prow = new LinearLayout(this); root.addView(prow); }
            final String pname = Modes.PRESET_NAME[i];
            Button pb = btn(pname);
            pb.setTextSize(10);
            pb.setPadding(0, 0, 0, 0);
            pb.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { PulseEngine.stopPulse(false); Modes.preset(pname); refresh(); }
            });
            prow.addView(pb, new LinearLayout.LayoutParams(0, dp(40), 1f));
        }

        // ---------------- 手写板：滑动控制振动 ----------------
        root.addView(label("手写板：横向=档位，纵向=压力/强度，抬手停"));
        pad = new PadView(this);
        pad.setProfile(padProfile);
        pad.setListener(new PadView.Listener() {
            public void onPad(int g, int p, String hex, String name, boolean touching) {
                padInfo.setText("档位 " + g + "/10 · 压力 " + p + "/5 · " + hex + " " + name);
            }
        });
        root.addView(pad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(220)));
        padInfo = new TextView(this);
        padInfo.setTextSize(11);
        padInfo.setTextColor(Color.parseColor("#00695C"));
        padInfo.setText("体系 → " + Modes.PROFILE_NAME[padProfile]);
        root.addView(padInfo);

        LinearLayout profRow = new LinearLayout(this);
        for (int i = 0; i < Modes.PROFILE_NAME.length; i++) {
            final int pi = i;
            Button b2 = btn("P" + i);
            b2.setTextSize(10);
            b2.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    padProfile = pi; pad.setProfile(pi);
                    padInfo.setText("体系 → " + Modes.PROFILE_NAME[pi]);
                }
            });
            profRow.addView(b2, new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        root.addView(profRow);

        // ---------------- 后台常驻 ----------------
        root.addView(label("后台常驻（忽略电池优化 + 前台服务）"));
        LinearLayout guardRow = new LinearLayout(this);
        guardRow.addView(big("申请豁免", new View.OnClickListener() {
            public void onClick(View v) { Guard.request(MainActivity.this); refresh(); }
        }));
        guardRow.addView(big("常驻开", new View.OnClickListener() {
            public void onClick(View v) { Guard.start(MainActivity.this); refresh(); }
        }));
        guardRow.addView(big("常驻关", new View.OnClickListener() {
            public void onClick(View v) { Guard.stop(MainActivity.this); refresh(); }
        }));
        root.addView(guardRow);
        guardText = new TextView(this);
        guardText.setTextSize(11);
        guardText.setTextColor(Color.parseColor("#555555"));
        guardText.setText(Guard.status(this));
        root.addView(guardText);

        // ---------------- 文档 / 日志导出 ----------------
        LinearLayout docRow = new LinearLayout(this);
        docRow.addView(big("查看 API 文档", new View.OnClickListener() {
            public void onClick(View v) {
                if (docText.getVisibility() == View.VISIBLE) { docText.setVisibility(View.GONE); return; }
                docText.setText(AutoDoc.text());
                docText.setVisibility(View.VISIBLE);
            }
        }));
        docRow.addView(big("导出日志", new View.OnClickListener() {
            public void onClick(View v) {
                String p = Logs.save(MainActivity.this);
                PulseEngine.log("log", "export " + p);
                logText.setText("日志已导出:\n" + p);
            }
        }));
        root.addView(docRow);

        docText = new TextView(this);
        docText.setTextSize(9);
        docText.setTextColor(Color.parseColor("#333333"));
        docText.setTypeface(android.graphics.Typeface.MONOSPACE);
        docText.setVisibility(View.GONE);
        docText.setPadding(0, dp(8), 0, dp(8));
        root.addView(docText);

        root.addView(label("脉冲频率 0.5-10 Hz"));
        SeekBar hzb = new SeekBar(this);
        hzb.setMax(95);
        hzb.setProgress(15);
        final TextView hzt = new TextView(this);
        hzt.setText("2.0 Hz");
        hzt.setTextSize(13);
        hzb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                hz = 0.5 + p / 10.0;
                hzt.setText(String.format("%.1f Hz", hz));
                if (PulseEngine.pulseActive()) PulseEngine.startPulse(hz, level, 0.5);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(hzb);
        root.addView(hzt);

        LinearLayout row3 = new LinearLayout(this);
        row3.addView(big("开脉冲", new View.OnClickListener() {
            public void onClick(View v) { PulseEngine.startPulse(hz, level, 0.5); }
        }));
        row3.addView(big("关脉冲", new View.OnClickListener() {
            public void onClick(View v) { PulseEngine.stopPulse(true); }
        }));
        row3.addView(big("全停", new View.OnClickListener() {
            public void onClick(View v) { PulseEngine.stopAll(); }
        }));
        root.addView(row3);

        root.addView(label("音乐节奏灵敏度"));
        SeekBar sensb = new SeekBar(this);
        sensb.setMax(100);
        sensb.setProgress(65);
        final TextView sensT = new TextView(this);
        sensT.setTextSize(13);
        sensT.setText("0.65");
        sensb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                sens = Math.max(0.1, p / 100.0);
                sensT.setText(String.format("%.2f", sens));
                if (AudioEngine.isRunning()) { AudioEngine.stop(); AudioEngine.start(MainActivity.this, "beat", sens, level); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(sensb);
        root.addView(sensT);

        LinearLayout row4 = new LinearLayout(this);
        row4.addView(big("开音乐跟随", new View.OnClickListener() {
            public void onClick(View v) { AudioEngine.start(MainActivity.this, "beat", sens, level); }
        }));
        row4.addView(big("跟音量(原厂)", new View.OnClickListener() {
            public void onClick(View v) { AudioEngine.start(MainActivity.this, "amp", sens, level); }
        }));
        row4.addView(big("关音频", new View.OnClickListener() {
            public void onClick(View v) { AudioEngine.stop(); }
        }));
        root.addView(row4);

        logText = new TextView(this);
        logText.setTextSize(10);
        logText.setTextColor(Color.parseColor("#777777"));
        logText.setPadding(0, dp(12), 0, 0);
        root.addView(logText);

        TextView tip = new TextView(this);
        tip.setTextSize(11);
        tip.setTextColor(Color.GRAY);
        tip.setPadding(0, dp(12), 0, 0);
        tip.setText("电脑端走 USB：\n"
                + "  adb forward tcp:8899 tcp:8899\n"
                + "  curl http://127.0.0.1:8899/status\n"
                + "  curl \"http://127.0.0.1:8899/pulse?hz=3&level=9&dur=20\"\n"
                + "同 WiFi 也可直接用手机 IP:8899");
        root.addView(tip);
        return sv;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Color.BLACK);
        t.setPadding(0, dp(12), 0, dp(4));
        return t;
    }

    private Button btn(String s) {
        Button b = new Button(this);
        b.setText(s);
        return b;
    }

    private Button big(String s, View.OnClickListener l) {
        Button b = btn(s);
        b.setTextSize(12);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void refresh() {
        String code = Gear.lastCode == 0 ? "成功" : "码=" + Gear.lastCode + " (" + Gear.describe(Gear.lastCode) + ")";
        status.setText("上一条: " + Gear.lastCmd + "   " + code
                + (Gear.lastNote.length() > 0 ? "  " + Gear.lastNote : "")
                + (granted ? "" : "\n权限未授予"));
        diag.setText(Gear.diag());
    }

    private void tick() {
        ui.postDelayed(new Runnable() {
            public void run() {
                refresh();
                if (ApiServer.isRunning()) apiText.setText("http://" + ApiServer.localIp() + ":8899");
                if (guardText != null) guardText.setText(Guard.status(MainActivity.this));
                logText.setText("音频: " + AudioEngine.status() + " · " + Logs.summary()
                        + "\n" + Logs.tailText(8));
                tick();
            }
        }, 800);
    }
}
