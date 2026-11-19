package com.example.cardkey.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;

/**
 * 激活状态持久化。
 *
 * 关键点：不是"存一个 boolean 说已激活"（那太容易被改），而是保存验签通过的
 * 完整凭证字符串，每次读取时重新验签 + 校验设备 + 校验有效期。
 * 因此单独篡改 payload 会因签名不匹配而失效。
 *
 * 诚实说明：客户端加固只能防"顺手改 SharedPreferences"，无法防反编译后
 * 替换成另一张合法卡密——那是发卡方要靠设备绑定 + 在线验卡解决的问题。
 */
public final class ActivationStore {
    private static final String PREF = "ck_activation";
    private static final String KEY_LICENSE = "license";
    private static final String KEY_DEVICE = "device";
    private static final String KEY_CHECKSUM = "checksum";

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
        String checksum = checksum(license, deviceId);
        prefs().edit()
                .putString(KEY_LICENSE, license)
                .putString(KEY_DEVICE, deviceId)
                .putString(KEY_CHECKSUM, checksum)
                .apply();
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

    /** 用内置公钥重新验证已保存的凭证；返回有效载荷，失败返回 null。 */
    public LicensePayload loadVerified(java.security.PublicKey publicKey) {
        String license = loadRaw();
        if (license == null) return null;
        try {
            return LicenseVerifier.verify(license, publicKey, deviceId);
        } catch (LicenseException e) {
            clear();
            return null;
        }
    }

    public void clear() {
        prefs().edit().clear().apply();
    }

    public boolean isActivated(java.security.PublicKey publicKey) {
        return loadVerified(publicKey) != null;
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
