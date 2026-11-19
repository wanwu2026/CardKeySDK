package com.example.cardkey.core;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * 凭证编解码与验签（客户端只读，不需要私钥）。
 *   magic(4) | version(1) | productId(UTF) | deviceId(UTF) | issuedAt(8) | expiresAt(8) | serial(8)
 */
public final class LicenseVerifier {
    private static final int MAGIC = 0x4B564C31; // "KVL1"
    private static final byte VERSION = 1;
    private static final String SIG_ALGORITHM = "SHA256withRSA";
    private static final String KEY_ALGORITHM = "RSA";

    private LicenseVerifier() {}

    /** 对一份凭证做完整校验（不含持久化）。 */
    public static LicensePayload verify(String license, PublicKey publicKey, String actualDeviceId)
            throws LicenseException {
        LicenseParts parts = split(license);
        byte[] payloadBytes = Base32.decode(parts.payloadB32);
        byte[] sig = Base32.decode(parts.sigB32);

        // 先验签再解析，避免处理攻击者构造的数据
        if (!verifySignature(payloadBytes, sig, publicKey)) {
            throw new LicenseException(LicenseException.Code.INVALID_SIGNATURE, "签名验证失败");
        }

        LicensePayload p = decodePayload(payloadBytes);

        if (!p.matchesDevice(actualDeviceId)) {
            throw new LicenseException(LicenseException.Code.DEVICE_MISMATCH, "卡密绑定了其他设备");
        }

        if (p.isExpired()) {
            throw new LicenseException(LicenseException.Code.EXPIRED, "卡密已过期");
        }
        return p;
    }

    /** 从 X.509 (SPKI) DER 的 Base64 文本加载公钥。 */
    public static PublicKey publicKeyFromBase64(String base64) throws LicenseException {
        try {
            byte[] der = Base64.decode(base64, Base64.DEFAULT);
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new LicenseException(LicenseException.Code.BAD_PUBLIC_KEY, "公钥格式错误: " + e.getMessage());
        }
    }

    private static final class LicenseParts {
        String payloadB32;
        String sigB32;
    }

    /** 接受 PAYLOAD.SIG 或 KVL1-PAYLOAD-SIG。 */
    private static LicenseParts split(String license) throws LicenseException {
        if (license == null) throw new LicenseException(LicenseException.Code.MALFORMED, "卡密为空");
        String s = license.trim().replace(" ", "").replace("\r", "").replace("\n", "");
        if (s.regionMatches(true, 0, "KVL1-", 0, 5)) s = s.substring(5);
        s = s.replace('-', '.');
        int dot = s.indexOf('.');
        if (dot <= 0 || dot == s.length() - 1) {
            throw new LicenseException(LicenseException.Code.MALFORMED, "卡密格式错误");
        }
        LicenseParts parts = new LicenseParts();
        parts.payloadB32 = s.substring(0, dot);
        parts.sigB32 = s.substring(dot + 1);
        return parts;
    }

    private static boolean verifySignature(byte[] data, byte[] sig, PublicKey key) {
        try {
            Signature verifier = Signature.getInstance(SIG_ALGORITHM);
            verifier.initVerify(key);
            verifier.update(data);
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    private static LicensePayload decodePayload(byte[] b) throws LicenseException {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(b));
            int magic = in.readInt();
            if (magic != MAGIC) throw new LicenseException(LicenseException.Code.MALFORMED, "魔数不匹配");
            byte version = in.readByte();
            if (version != VERSION) throw new LicenseException(LicenseException.Code.UNSUPPORTED_VERSION, "版本不支持");
            String productId = readUtf(in);
            String deviceId = readUtf(in);
            long issuedAt = in.readLong();
            long expiresAt = in.readLong();
            long serial = in.readLong();
            return new LicensePayload(productId, deviceId, issuedAt, expiresAt, serial);
        } catch (IOException e) {
            throw new LicenseException(LicenseException.Code.MALFORMED, "载荷解析失败: " + e.getMessage());
        }
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        if (len < 0 || len > 1024) throw new IOException("非法字符串长度");
        byte[] buf = new byte[len];
        in.readFully(buf);
        return new String(buf, "UTF-8");
    }

    static byte[] buildPayloadForTest(LicensePayload p) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(MAGIC);
        out.writeByte(VERSION);
        writeUtf(out, p.getProductId());
        writeUtf(out, p.getDeviceId());
        out.writeLong(p.getIssuedAt());
        out.writeLong(p.getExpiresAt());
        out.writeLong(p.getSerial());
        return bos.toByteArray();
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException {
        byte[] b = (s == null ? "" : s).getBytes("UTF-8");
        out.writeShort(b.length);
        out.write(b);
    }

    /** 把原始字节与签名组装成可展示的卡密字符串。 */
    public static String assemble(byte[] payload, byte[] sig) {
        return Base32.encode(payload) + "." + Base32.encode(sig);
    }
}
