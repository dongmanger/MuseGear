package com.musegear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * adb 外部控制入口：
 *   adb shell am broadcast -a com.musegear.GEAR --ei level 5
 *   adb shell am broadcast -a com.musegear.GEAR --es hex 53
 *   adb shell am broadcast -a com.musegear.GEAR --es hex 10 --ei level 7
 *   adb shell am broadcast -a com.musegear.GEAR --ei level 0
 */
public class GearReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent it) {
        Gear.init(ctx);
        String hex = it.getStringExtra("hex");
        int level = it.getIntExtra("level", -1);

        if (hex != null && hex.length() >= 2) {
            if (hex.startsWith("10") && level >= 0) {
                Gear.sendAuto(new byte[]{0x10, (byte) level});
            } else {
                Gear.sendHex(hex);
            }
            Log.i(Gear.TAG, "adb hex=" + hex + " level=" + level + " code=" + Gear.lastCode);
            return;
        }
        if (level >= 0) {
            Gear.sendAuto(new byte[]{(byte) level});
            Log.i(Gear.TAG, "adb level=" + level + " code=" + Gear.lastCode);
            return;
        }
        Log.e(Gear.TAG, "adb: 缺 --ei level 或 --es hex");
    }
}
