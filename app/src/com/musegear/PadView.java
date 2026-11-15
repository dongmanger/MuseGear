package com.musegear;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

/**
 * 手写板：手指在板面滑动 → 连续改变振动档位/压力档，抬手停止。
 *
 * 映射（一次采样只发 1 条命令，避免和原厂广播节流打架）：
 *   横向 x：左→右 = 档位 1..10（按 profile 决定字节：01-09 / 11-19+20 / 01-03）
 *   纵向 y：下→上 = 压力档/强度 1..5（arc1→41-45，arc2→51-55，其它→11-15）
 *   取"本次位移更大的轴"这一条发出去；两轴都变化时轮流命中，观感连续。
 * 抬手：stopOnRelease=true 时发该体系的停止字节（00/40/50）。
 */
public class PadView extends View {

    public interface Listener {
        void onPad(int gear, int press, String hex, String name, boolean touching);
    }

    private final Paint bg = new Paint();
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private int profile = Modes.P_10;
    private boolean stopOnRelease = true;
    private Listener listener;

    private float cx = 0.5f, cy = 0.5f;   // 归一化坐标
    private float lastX = -1, lastY = -1;
    private int gear = 5, press = 3;
    private long lastEmit = 0;
    private boolean touching = false;
    private String lastHex = "", lastName = "";

    private static final long THROTTLE_MS = 100;

    public PadView(Context c) {
        super(c);
        bg.setColor(Color.parseColor("#101014"));
        grid.setColor(Color.parseColor("#22222A"));
        grid.setStrokeWidth(1f);
        trail.setColor(Color.parseColor("#D81B60"));
        trail.setStrokeWidth(dp(3));
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        trail.setStrokeJoin(Paint.Join.ROUND);
        trail.setAlpha(200);
        dot.setColor(Color.parseColor("#FF4081"));
        hud.setColor(Color.parseColor("#ECECEC"));
        hud.setTextSize(dp(11));
        setFocusable(true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setProfile(int p) {
        this.profile = p;
        invalidate();
    }

    public int getProfile() {
        return profile;
    }

    public void setStopOnRelease(boolean b) {
        this.stopOnRelease = b;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    // ------------------------------------------------------------------ 触摸
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                path.reset();
                cx = clamp(e.getX() / w);
                cy = clamp(e.getY() / h);
                lastX = cx;
                lastY = cy;
                path.moveTo(e.getX(), e.getY());
                computeTargets();
                emit(true);
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                cx = clamp(e.getX() / w);
                cy = clamp(e.getY() / h);
                path.lineTo(e.getX(), e.getY());
                computeTargets();
                emit(false);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                if (stopOnRelease) {
                    String hx = String.format("%02X", Modes.stopByte(profile));
                    Modes.play(hx);
                    lastHex = hx;
                    lastName = "停止";
                    if (listener != null) listener.onPad(gear, press, hx, lastName, false);
                } else if (listener != null) {
                    listener.onPad(gear, press, lastHex, lastName, false);
                }
                invalidate();
                return true;
            default:
                return false;
        }
    }

    private static float clamp(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private void computeTargets() {
        gear = 1 + Math.round(cx * 9f);          // 1..10
        press = 1 + Math.round((1f - cy) * 4f);  // 上=5 下=1
        if (gear < 1) gear = 1;
        if (gear > 10) gear = 10;
        if (press < 1) press = 1;
        if (press > 5) press = 5;
    }

    /** 主位移轴优先，一次只发一条命令 */
    private void emit(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastEmit < THROTTLE_MS) return;
        float dx = Math.abs(cx - lastX);
        float dy = Math.abs(cy - lastY);
        if (!force && dx < 0.004f && dy < 0.004f) return;

        int byteVal;
        String name;
        if (dx >= dy) {
            int b = Modes.gearByte(profile, gear);
            if (b < 0) b = Modes.gearByte(profile, 9);
            byteVal = b;
            name = "档位 " + gear;
        } else {
            byteVal = Modes.pressByte(profile, press);
            name = "压力 " + press;
        }
        if (byteVal < 0) return;
        String hx = String.format("%02X", byteVal);
        boolean ok = Modes.play(hx);
        lastHex = hx;
        lastName = name + (ok ? "" : " (拒发)");
        lastX = cx;
        lastY = cy;
        lastEmit = now;
        if (listener != null) listener.onPad(gear, press, hx, lastName, true);
    }

    // ------------------------------------------------------------------ 绘制
    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);

        // 网格 10x5
        for (int i = 1; i < 10; i++) {
            float x = w * i / 10f;
            c.drawLine(x, 0, x, h, grid);
        }
        for (int i = 1; i < 5; i++) {
            float y = h * i / 5f;
            c.drawLine(0, y, w, y, grid);
        }

        // 轨迹 + 当前点
        c.drawPath(path, trail);
        if (path.isEmpty()) {
            c.drawCircle(w * cx, h * cy, dp(10), dot);
        } else {
            c.drawCircle(w * cx, h * cy, dp(9), dot);
        }

        // HUD
        String l1 = "档位 " + gear + "/10   压力 " + press + "/5";
        String l2 = "体系 " + Modes.PROFILE_NAME[profile] + (touching ? "   ● 滑动中" : "   ○ 待触");
        String l3 = lastHex.length() == 0 ? "" : ("发 " + lastHex + "  " + lastName);
        c.drawText(l1, dp(8), dp(16), hud);
        c.drawText(l2, dp(8), dp(32), hud);
        if (l3.length() > 0) c.drawText(l3, dp(8), h - dp(8), hud);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = resolveSize((int) dp(320), ws);
        int h = resolveSize((int) dp(220), hs);
        setMeasuredDimension(w, h);
    }
}
