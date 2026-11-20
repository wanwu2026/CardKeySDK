package com.example.cardkey.core;

/**
 * 纯 Java Base64（RFC 4648），用于卡密/公钥的编解码。
 * 不依赖 android.util，使核心验签逻辑可在纯 JVM（本地单测/服务端）运行。
 */
public final class Base64 {
    private static final char[] ENC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
    private static final int[] DEC = new int[128];

    static {
        java.util.Arrays.fill(DEC, -1);
        for (int i = 0; i < ENC.length; i++) DEC[ENC[i]] = i;
    }

    private Base64() {}

    /** 编码为标准 Base64（带 padding）。 */
    public static String encodeToString(byte[] data) {
        StringBuilder sb = new StringBuilder(((data.length + 2) / 3) * 4);
        int i = 0;
        while (i + 3 <= data.length) {
            int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff);
            sb.append(ENC[(n >>> 18) & 63]);
            sb.append(ENC[(n >>> 12) & 63]);
            sb.append(ENC[(n >>> 6) & 63]);
            sb.append(ENC[n & 63]);
            i += 3;
        }
        int rem = data.length - i;
        if (rem == 1) {
            int n = (data[i] & 0xff) << 16;
            sb.append(ENC[(n >>> 18) & 63]).append(ENC[(n >>> 12) & 63]).append("==");
        } else if (rem == 2) {
            int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
            sb.append(ENC[(n >>> 18) & 63]).append(ENC[(n >>> 12) & 63]).append(ENC[(n >>> 6) & 63]).append('=');
        }
        return sb.toString();
    }

    /** 解码标准 Base64；容忍 padding、换行与空白。 */
    public static byte[] decode(String s) {
        StringBuilder clean = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '=' || c == '\n' || c == '\r' || c == ' ' || c == '\t') continue;
            if (c > 127 || DEC[c] < 0) throw new IllegalArgumentException("非法 Base64 字符: " + c);
            clean.append(c);
        }
        String t = clean.toString();
        if (t.length() % 4 == 1) throw new IllegalArgumentException("非法 Base64 长度");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(t.length() * 3 / 4);
        int buf = 0, bits = 0;
        for (int i = 0; i < t.length(); i++) {
            buf = (buf << 6) | DEC[t.charAt(i)];
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buf >>> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}