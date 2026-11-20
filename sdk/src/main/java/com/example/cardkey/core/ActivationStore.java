package com.example.cardkey.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;

/** 激活状态持久化：保存验签通过的凭证，读取时重新验签并校验设备与有效期。 */
public final class ActivationStore {
    private static final String PREF = "ck_activation";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_DEVICE = "device";
    private static final String KEY_CHECKSUM = "checksum";
    private static final String KEY_CODE = "code";

    private final Context ctx;
    private final String deviceId;

    public ActivationStore(Context context, String deviceId) {
        this.ctx = context.getApplicationContext();
        this.deviceId = deviceId;
    }

    private SharedPreferences prefs() {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 保存一份已验证通过的凭证。 */
    public void save(String license) {
        save(license, null);
    }

    /** license 为已验签凭证；code 为原始卡密（在线模式下用于事后向服务器复核撤销）。 */
    public void save(String license, String code) {
        String checksum = checksum(license, deviceId);
        android.content.SharedPreferences.Editor e = prefs().edit()
                .putString(KEY_LICENSE, license)
                .putString(KEY_DEVICE, deviceId)
                .putString(KEY_CHECKSUM, checksum);
        if (code != null) e.putString(KEY_CODE, code);
        e.apply();
    }

    /** 原始卡密；未保存返回 null（离线模式或无历史数据）。 */
    public String loadCode() {
        return prefs().getString(KEY_CODE, null);
    }

    /** 读取凭证；若被篡改（校验和不符 / 设备不符）返回 null 并清空。 */
    public String loadRaw() {
        SharedPreferences p = prefs();
        String license = p.getString(KEY_LICENSE, null);
        String device = p.getString(KEY_DEVICE, null);
        String checksum = p.getString(KEY_CHECKSUM, null);
        if (license == null || device == null || checksum == null) return null;
        if (!device.equals(deviceId) || !checksum.equals(checksum(license, deviceId))) {
            clear();
            return null;
        }
        return license;
    }

    /** 重新验签已保存的凭证，失败返回 null。 */
    public LicensePayload loadVerified(java.security.PublicKey... publicKeys) {
        String license = loadRaw();
        if (license == null) return null;
        try {
            return LicenseVerifier.verifyAny(license, publicKeys, deviceId);
        } catch (LicenseException e) {
            clear();
            return null;
        }
    }

    public void clear() {
        prefs().edit().clear().apply();
    }

    public boolean isActivated(java.security.PublicKey... publicKeys) {
        return loadVerified(publicKeys) != null;
    }

    private static String checksum(String license, String device) {
        try {
            byte[] data = (license + "|" + device).getBytes("UTF-8");
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
