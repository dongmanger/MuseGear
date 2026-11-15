package com.musegear;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * muse 2.4G toy advertising core.
 *
 * On-air: ManufacturerSpecificData(companyId=255) = FF 00 + RF payload from libble.so
 * Settings must match BeaconManager.createAdvSettings2():
 *   mode=LOW_LATENCY, connectable=TRUE, timeout=3000, txPower=HIGH
 * payload re-issued every 2.5s (app renews before the 3s timeout).
 */
public final class Gear {

    public static final String TAG = "MuseGear";
    public static final int COMPANY_ID = 255;
    public static final ParcelUuid SVC =
            ParcelUuid.fromString("0000ae8f-0000-1000-8000-00805f9b34fb");

    public static volatile int lastCode = -1;
    public static volatile String lastCmd = "";
    public static volatile String lastNote = "";
    public static volatile boolean useUuid = true;
    /** 1 = LOW_LATENCY/timeout3000（划一划·音乐/原厂 createAdvSettings2）；0 = BALANCED/timeout1500（摇一摇/原厂 createAdvSettings） */
    public static volatile int profile = 1;

    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static BluetoothLeAdvertiser sAdv;
    private static AdvertiseCallback sCb;
    private static Context sCtx;
    private static Runnable sRenew;
    private static byte[] sCmd;

    private Gear() {}

    public static void init(Context c) {
        if (sCtx == null && c != null) sCtx = c.getApplicationContext();
    }

    public static String describe(int code) {
        switch (code) {
            case 0: return "OK";
            case 1: return "data too large";
            case 2: return "too frequent";
            case 3: return "already advertising (other app holds it)";
            case 4: return "too many advertisers";
            default: return "no-callback/" + code;
        }
    }

    public static String diag() {
        if (sCtx == null) return "ctx=null";
        BluetoothManager bm = (BluetoothManager) sCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null) return "no adapter";
        StringBuilder sb = new StringBuilder();
        sb.append("BT=").append(ad.isEnabled() ? "on" : "off");
        sb.append(" multiAdv=").append(ad.isMultipleAdvertisementSupported() ? "yes" : "no");
        sb.append(" advertiser=").append(ad.getBluetoothLeAdvertiser() == null ? "null" : "ok");
        sb.append(" uuid=").append(useUuid ? "on" : "off");
        return sb.toString();
    }

    public static void cancelRenew() {
        if (sRenew != null) {
            sHandler.removeCallbacks(sRenew);
            sRenew = null;
        }
    }

    public static void stopAdvertising() {
        cancelRenew();
        try {
            if (sCb != null && sAdv != null) sAdv.stopAdvertising(sCb);
        } catch (Throwable ignore) {}
        sCb = null;
    }

    /** 续播一次（前台服务心跳用）：手上还有命令就立刻重发一轮广播，防止超时后被系统静默 */
    public static void keepWarm() {
        if (sCtx == null || sCmd == null) return;
        sHandler.post(new Runnable() {
            public void run() {
                cancelRenew();
                startOnce();
                scheduleRenew();
            }
        });
    }

    public static void sendHex(String hex) {
        if (hex == null || hex.length() < 2) return;
        int n = hex.length() / 2;
        byte[] cmd = new byte[n];
        for (int i = 0; i < n; i++) cmd[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        send(cmd);
    }

    public static void level(int lv) {
        if (lv < 0) lv = 0;
        if (lv > 9) lv = 9;
        send(new byte[]{(byte) lv});
    }

    public static void strength(int s) {
        if (s < 1) s = 1;
        if (s > 5) s = 5;
        send(new byte[]{(byte) (0x50 + s)});
    }

    /**
     * 音乐档位（原厂 MusicBleCommandHelper/SpecialGearCommandMapper 结论）：
     *   gear 1..9 -> 0x11..0x19 ；gear 10 -> 0x20 ；gear<=0 -> 0x00
     */
    public static void music(int lv) {
        if (lv <= 0) { send(new byte[]{0x00}); return; }
        if (lv >= 10) { send(new byte[]{0x20}); return; }
        send(new byte[]{(byte) (0x10 + lv)});
    }

    public static void musicStop() {
        send(new byte[]{0x00});
    }

    public static void stop() {
        send(new byte[]{0x00});
    }

    public static void sendAuto(byte[] cmd) { send(cmd); }

    private static AdvertiseSettings settings() {
        AdvertiseSettings.Builder b = new AdvertiseSettings.Builder()
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true);
        if (profile == 0) {
            b.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
            b.setTimeout(1500);
        } else {
            b.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
            b.setTimeout(3000);
        }
        return b.build();
    }

    private static void startOnce() {
        if (sCtx == null || sCmd == null) return;
        BluetoothManager bm = (BluetoothManager) sCtx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
        if (ad == null || !ad.isEnabled()) { lastNote = "bt off"; return; }
        BluetoothLeAdvertiser adv = ad.getBluetoothLeAdvertiser();
        if (adv == null) { lastNote = "no advertiser"; return; }
        try {
            if (sCb != null) adv.stopAdvertising(sCb);
        } catch (Throwable ignore) {}

        AdvertiseData.Builder db = new AdvertiseData.Builder()
                .addManufacturerData(COMPANY_ID, frame(sCmd));
        if (useUuid) db.addServiceUuid(SVC);

        sCb = new AdvertiseCallback() {
            @Override public void onStartSuccess(AdvertiseSettings s) {
                lastCode = 0;
                Log.i(TAG, "OK " + lastCmd);
            }
            @Override public void onStartFailure(int code) {
                lastCode = code;
                Log.e(TAG, "FAIL " + lastCmd + " code=" + code);
            }
        };
        sAdv = adv;
        try {
            adv.startAdvertising(settings(), db.build(), sCb);
        } catch (SecurityException se) {
            lastNote = "need BLUETOOTH_ADVERTISE";
            lastCode = -2;
        } catch (Throwable t) {
            lastNote = "ex:" + t.getClass().getSimpleName();
            lastCode = -3;
        }
    }

    public static void send(final byte[] cmd) {
        if (cmd == null) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            sHandler.post(new Runnable() { public void run() { send(cmd); } });
            return;
        }
        try { sendOnMain(cmd); } catch (Throwable t) { lastNote = "send ex:" + t.getClass().getSimpleName(); lastCode = -3; }
    }

    private static synchronized void sendOnMain(byte[] cmd) {
        if (Rf.payloadFor(hex(cmd)) == null) { lastNote = "NO_FRAME:" + hex(cmd); lastCode = -4; return; }
        sCmd = cmd;
        lastCmd = hex(frame(cmd));
        lastNote = "";
        cancelRenew();
        startOnce();
        scheduleRenew();
    }

    private static void scheduleRenew() {
        sRenew = new Runnable() {
            public void run() {
                startOnce();
                sHandler.postDelayed(this, 2500);
            }
        };
        sHandler.postDelayed(sRenew, 2500);
    }

    /** 只查 libble.so 仿真表；查不到返回 null（绝不发未白化裸帧） */
    private static byte[] frame(byte[] cmd) {
        return Rf.payloadFor(hex(cmd));
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
