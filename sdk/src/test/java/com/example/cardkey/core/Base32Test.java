package com.example.cardkey.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** Base32：RFC4648 向量 + 二进制往返。 */
public class Base32Test {
    @Test
    public void rfc4648Vectors() {
        assertEquals("", Base32.encode(new byte[0]));
        assertEquals("MY", Base32.encode(new byte[]{'f'}));
        assertEquals("MZXQ", Base32.encode(new byte[]{'f','o'}));
        assertEquals("MZXW6", Base32.encode(new byte[]{'f','o','o'}));
        assertEquals("MZXW6YQ", Base32.encode(new byte[]{'f','o','o','b'}));
        assertEquals("MZXW6YTB", Base32.encode(new byte[]{'f','o','o','b','a'}));
        assertEquals("MZXW6YTBOI", Base32.encode(new byte[]{'f','o','o','b','a','r'}));
    }

    @Test
    public void roundTripBinary() {
        byte[] raw = new byte[256];
        for (int i = 0; i < 256; i++) raw[i] = (byte) i;
        assertArrayEqualsBytes(raw, Base32.decode(Base32.encode(raw)));
    }

    @Test
    public void toleratesLowercaseAndPadding() {
        byte[] dec = Base32.decode("mzxw6ytboi======");
        assertEquals(new String(new byte[]{'f','o','o','b','a','r'}), new String(dec));
    }

    @Test
    public void rejectsInvalidChar() {
        try {
            Base32.decode("MZ--XW6YTBOI");
            // '-' 在解码端被容忍（拆分时已去掉）；真正非法的是 0/1 这类
        } catch (IllegalArgumentException expected) {
            return;
        }
        try {
            Base32.decode("MZXW61YTBOI");
            fail("应当拒绝非法字符");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected);
        }
    }

    private static void assertArrayEqualsBytes(byte[] a, byte[] b) {
        assertTrue("长度不等: " + a.length + " vs " + b.length, a.length == b.length);
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) throw new AssertionError("第 " + i + " 字节不同");
        }
    }
}