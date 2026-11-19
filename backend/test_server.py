#!/usr/bin/env python3
"""演示后端端到端测试：发卡 → 验卡 → 验签 → 一机一码 → 封禁。仅标准库。"""
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "tools"))
import server as srv
import cardkey_tool as ck

ADMIN = "test-admin-token"


class TestBackend(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        ck.gen_keypair(cls.tmp.name)
        cls.priv = os.path.join(cls.tmp.name, "private.pem")
        with open(os.path.join(cls.tmp.name, "public_key_base64.txt")) as f:
            cls.pub = f.read().strip()
        srv.CardKeyHandler.store = srv.Store(os.path.join(cls.tmp.name, "cards.db"))
        srv.CardKeyHandler.private_key = cls.priv
        srv.CardKeyHandler.pub_b64 = cls.pub
        srv.CardKeyHandler.admin_token = ADMIN
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), srv.CardKeyHandler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        cls.tmp.cleanup()

    def req(self, path, payload=None, token=None):
        data = json.dumps(payload).encode() if payload is not None else None
        headers = {"Content-Type": "application/json"}
        if data:
            headers["Authorization"] = "Bearer " + (token or "")
        r = urllib.request.Request(f"http://127.0.0.1:{self.port}{path}",
                                   data=data, headers=headers, method="POST" if data else "GET")
        try:
            with urllib.request.urlopen(r, timeout=10) as resp:
                return json.loads(resp.read().decode())
        except urllib.error.HTTPError as e:
            # 4xx 也是合法响应（如 401 unauthorized），读它的 body
            return json.loads(e.read().decode())

    def test_health(self):
        self.assertTrue(self.req("/health")["ok"])

    def test_issue_requires_auth(self):
        r = self.req("/issue", {"serial": 1}, token="wrong")
        self.assertFalse(r["ok"])

    def test_full_flow(self):
        # 1. 发卡（一机一码）
        issued = self.req("/issue", {"serial": 1001, "max_activations": 1}, token=ADMIN)
        self.assertTrue(issued["ok"])
        code = issued["code"]

        # 2. 验卡成功，返回 license
        v = self.req("/verify", {"code": code, "device_id": "DEV-A", "product_id": "demo_app"})
        self.assertTrue(v["ok"], v)
        lic = v["license"]

        # 3. 验证返回的 license 签名有效、设备绑定正确
        parts = lic[5:].replace("-", ".").split(".")
        payload = ck.b32decode(parts[0])
        sig = ck.b32decode(parts[1])
        info = ck.parse_payload(payload)
        self.assertTrue(ck.verify_sig(payload, sig, self.pub))
        self.assertEqual(info["device"], "DEV-A")
        self.assertEqual(info["serial"], 1001)

        # 4. 换设备被拒（一机一码）
        v2 = self.req("/verify", {"code": code, "device_id": "DEV-B"})
        self.assertFalse(v2["ok"])
        self.assertEqual(v2["reason"], "device_mismatch")

        # 5. 原设备仍可重复激活
        v3 = self.req("/verify", {"code": code, "device_id": "DEV-A"})
        self.assertTrue(v3["ok"])

        # 6. 封禁后拒绝
        rv = self.req("/revoke", {"code": code}, token=ADMIN)
        self.assertTrue(rv["ok"])
        v4 = self.req("/verify", {"code": code, "device_id": "DEV-A"})
        self.assertFalse(v4["ok"])
        self.assertEqual(v4["reason"], "revoked")

    def test_invalid_code(self):
        v = self.req("/verify", {"code": "KVL1-BOGUS-BOGUS", "device_id": "X"})
        self.assertFalse(v["ok"])
        self.assertEqual(v["reason"], "invalid")


if __name__ == "__main__":
    unittest.main(verbosity=2)
