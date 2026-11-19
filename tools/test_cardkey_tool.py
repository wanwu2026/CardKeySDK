#!/usr/bin/env python3
"""cardkey_tool.py 单元测试：Base32 / 载荷格式 / 签名往返。仅用标准库 unittest。"""
import os
import subprocess
import tempfile
import unittest
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cardkey_tool as ck

NEEDS_OPENSSL = subprocess.run(
    ["openssl", "version"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
).returncode == 0


class TestBase32(unittest.TestCase):
    def test_rfc4648_vectors(self):
        cases = {
            b"": "", b"f": "MY", b"fo": "MZXQ", b"foo": "MZXW6",
            b"foob": "MZXW6YQ", b"fooba": "MZXW6YTB", b"foobar": "MZXW6YTBOI",
        }
        for raw, enc in cases.items():
            with self.subTest(raw=raw):
                self.assertEqual(ck.b32encode(raw), enc)
                self.assertEqual(ck.b32decode(enc), raw)

    def test_roundtrip_binary(self):
        raw = bytes(range(256))
        self.assertEqual(ck.b32decode(ck.b32encode(raw)), raw)

    def test_lowercase_and_padding_tolerated(self):
        self.assertEqual(ck.b32decode("mzxw6ytboi======"), b"foobar")


class TestPayload(unittest.TestCase):
    def test_roundtrip(self):
        p = ck.build_payload("demo_app", "DEVICE123", 1700000000000,
                             1893456000000, 42)
        info = ck.parse_payload(p)
        self.assertEqual(info, dict(product="demo_app", device="DEVICE123",
                                    issued=1700000000000, expires=1893456000000,
                                    serial=42))

    def test_utf8_product(self):
        p = ck.build_payload("产品_测试", "", 1, 0, 1)
        self.assertEqual(ck.parse_payload(p)["product"], "产品_测试")

    def test_empty_device(self):
        p = ck.build_payload("app", "", 1, 0, 1)
        self.assertEqual(ck.parse_payload(p)["device"], "")

    def test_bad_magic_rejected(self):
        p = bytearray(ck.build_payload("app", "", 1, 0, 1))
        p[0] = 0
        with self.assertRaises(ValueError):
            ck.parse_payload(bytes(p))

    @unittest.skipUnless(NEEDS_OPENSSL, "需要 openssl")
    def test_tampered_license_fails(self):
        with tempfile.TemporaryDirectory() as d:
            ck.gen_keypair(d)
            priv = os.path.join(d, "private.pem")
            with open(os.path.join(d, "public_key_base64.txt")) as f:
                pub = f.read().strip()
            payload = ck.build_payload("app", "", 1, 0, 1)
            sig = ck.sign(payload, priv)
            self.assertTrue(ck.verify_sig(payload, sig, pub))
            # 篡改载荷后签名应失效
            tampered = bytearray(payload)
            tampered[-1] ^= 0x01
            self.assertFalse(ck.verify_sig(bytes(tampered), sig, pub))


if __name__ == "__main__":
    unittest.main(verbosity=2)
