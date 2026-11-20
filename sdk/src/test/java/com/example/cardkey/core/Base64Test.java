package com.example.cardkey.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 纯 Java Base64 与标准向量对照。 */
public class Base64Test {
    @Test
    public void rfc4648Vectors() {
        assertArrayEquals(new byte[0], Base64.decode(""));
        assertEquals("Zg==", Base64.encodeToString(new byte[]{'f'}));
        assertEquals("Zm8=", Base64.encodeToString(new byte[]{'f','o'}));
        assertEquals("Zm9v", Base64.encodeToString(new byte[]{'f','o','o'}));
        assertEquals("Zm9vYg==", Base64.encodeToString(new byte[]{'f','o','o','b'}));
        assertEquals("Zm9vYmE=", Base64.encodeToString(new byte[]{'f','o','o','b','a'}));
        assertEquals("Zm9vYmFy", Base64.encodeToString(new byte[]{'f','o','o','b','a','r'}));
    }

    @Test
    public void roundTripBinary() {
        byte[] raw = new byte[256];
        for (int i = 0; i < 256; i++) raw[i] = (byte) i;
        assertArrayEquals(raw, Base64.decode(Base64.encodeToString(raw)));
    }

    @Test
    public void toleratesWhitespaceAndPadding() {
        assertArrayEquals(new byte[]{'f','o','o'}, Base64.decode(" Zm9v\r\n"));
    }
}