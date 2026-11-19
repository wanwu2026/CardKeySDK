package com.example.cardkey.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;

import java.security.MessageDigest;
import java.util.Locale;

/** 设备标识工具。默认取 ANDROID_ID 并做 SHA-256，便于后续替换为自有设备码。 */
public final class DeviceId {
    private DeviceId() {}

    @SuppressLint("HardwareIds")
    public static String get(Context context) {
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        } catch (Throwable t) { /* 某些环境取不到，回退固定值 */ }
        if (androidId == null || androidId.length() == 0) androidId = "unknown-device";
        return sha256(androidId).substring(0, 16).toUpperCase(Locale.US);
    }

    private static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
