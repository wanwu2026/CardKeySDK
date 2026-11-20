package com.example.cardkey.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/** 卡密全链路：签发(组装) → 验签 → 解析 → 设备/时间校验，含各失败分支。 */
public class LicenseVerifierTest {
    private PrivateKey priv;
    private PublicKey pub;
    private String pubB64;

    @Before
    public void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        priv = kp.getPrivate();
        pub = kp.getPublic();
        pubB64 = Base64.encodeToString(pub.getEncoded());
    }

    private LicensePayload makePayload(String deviceId, long expiresAt) {
        return new LicensePayload("test_app", deviceId, 1700000000000L, expiresAt, 42L);
    }

    /** 就地签一张凭证字符串。 */
    private String sign(LicensePayload p) throws Exception {
        byte[] payload = LicenseVerifier.buildPayloadForTest(p);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(payload);
        byte[] sig = s.sign();
        return LicenseVerifier.assemble(payload, sig);
    }

    @Test
    public void validLicenseVerifies() throws Exception {
        String lic = sign(makePayload("DEV-1", 0));
        LicensePayload p = LicenseVerifier.verify(lic, pub, "DEV-1");
        assertEquals("test_app", p.getProductId());
        assertEquals("DEV-1", p.getDeviceId());
        assertEquals(42L, p.getSerial());
        assertFalse(p.isExpired());
    }

    @Test
    public void unboundLicenseWorksAnyDevice() throws Exception {
        String lic = sign(makePayload("", 0));
        assertNotNull(LicenseVerifier.verify(lic, pub, "SOME-OTHER-DEVICE"));
    }

    @Test
    public void tamperedPayloadFailsSignature() throws Exception {
        LicensePayload p = makePayload("DEV-1", 0);
        byte[] payload = LicenseVerifier.buildPayloadForTest(p);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(priv);
        s.update(payload);
        byte[] sig = s.sign();
        // 篡改载荷最后一个字节（serial），得到签名不匹配
        byte[] tampered = payload.clone();
        tampered[tampered.length - 1] ^= 0x01;
        String lic = LicenseVerifier.assemble(tampered, sig);
        try {
            LicenseVerifier.verify(lic, pub, "DEV-1");
            fail("应为 INVALID_SIGNATURE");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.INVALID_SIGNATURE, e.getCode());
        }
    }

    @Test
    public void deviceMismatchRejected() throws Exception {
        String lic = sign(makePayload("DEV-1", 0));
        try {
            LicenseVerifier.verify(lic, pub, "DEV-2");
            fail("应为 DEVICE_MISMATCH");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.DEVICE_MISMATCH, e.getCode());
        }
    }

    @Test
    public void expiredRejected() throws Exception {
        // 到期时间 = 2003 年，肯定过期
        String lic = sign(makePayload("DEV-1", 1050084123456L));
        try {
            LicenseVerifier.verify(lic, pub, "DEV-1");
            fail("应为 EXPIRED");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.EXPIRED, e.getCode());
        }
    }

    @Test
    public void malformedLicenseRejected() throws Exception {
        try {
            LicenseVerifier.verify("not-a-license", pub, "DEV-1");
            fail("应为 MALFORMED");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.MALFORMED, e.getCode());
        }
    }

    @Test
    public void wrongPublicKeyRejected() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        PublicKey otherPub = g.generateKeyPair().getPublic();
        String lic = sign(makePayload("DEV-1", 0));
        try {
            LicenseVerifier.verify(lic, otherPub, "DEV-1");
            fail("应为 INVALID_SIGNATURE");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.INVALID_SIGNATURE, e.getCode());
        }
    }

    @Test
    public void publicKeyFromBase64Works() throws Exception {
        PublicKey loaded = LicenseVerifier.publicKeyFromBase64(pubB64);
        assertTrue(loaded != null);
        assertEquals("RSA", loaded.getAlgorithm());
    }

    @Test
    public void multiKeyRotationAcceptsOldAndNew() throws Exception {
        // 模拟密钥轮换：新私钥签的卡，用 [旧公钥, 新公钥] 都能验通过
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair rotated = g.generateKeyPair();
        String lic = sign(makePayload("DEV-1", 0)); // 用旧私钥签

        // 只用旧公钥：[旧] 通过
        LicensePayload p1 = LicenseVerifier.verifyAny(lic, new PublicKey[]{pub}, "DEV-1");
        assertEquals(42L, p1.getSerial());

        // 轮换列表 [旧, 新] 也能通过
        LicensePayload p2 = LicenseVerifier.verifyAny(lic, new PublicKey[]{pub, rotated.getPublic()}, "DEV-1");
        assertEquals(42L, p2.getSerial());

        // 只用新公钥：必须失败（证明 verifyAny 真的逐个尝试而非放行）
        try {
            LicenseVerifier.verifyAny(lic, new PublicKey[]{rotated.getPublic()}, "DEV-1");
            fail("仅新公钥时应当失败");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.INVALID_SIGNATURE, e.getCode());
        }

        // 空公钥列表：可判别错误
        try {
            LicenseVerifier.verifyAny(lic, new PublicKey[0], "DEV-1");
            fail("空公钥列表应报错");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.BAD_PUBLIC_KEY, e.getCode());
        }
    }

    @Test
    public void badPublicKeyRejected() {
        try {
            LicenseVerifier.publicKeyFromBase64("!!!not-base64!!!");
            fail("应为 BAD_PUBLIC_KEY");
        } catch (LicenseException e) {
            assertEquals(LicenseException.Code.BAD_PUBLIC_KEY, e.getCode());
        }
    }
}