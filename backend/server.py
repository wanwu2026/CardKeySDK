#!/usr/bin/env python3
"""
CardKey 演示后端 —— 可直接运行的验卡/发卡服务。

特性：
  - POST /verify   验卡：核对状态机（一机一码 / 激活次数 / 封禁 / 到期），签发凭证
  - POST /issue    发卡：生成卡密并记录（管理接口，需 admin token）
  - GET  /health   健康检查
  - GET  /codes    列出卡密状态（管理接口，需 admin token）

设计遵循 docs/backend.md：私钥只在服务器；客户端会再验一次签，所以响应被篡改也无效。
依赖：仅 Python 标准库 + 系统 openssl（复用 tools/cardkey_tool.py）。

运行：
  python3 backend/server.py --private-key keys/private.pem \
      --public-key-base64 keys/public_key_base64.txt \
      --admin-token change-me --port 8080
"""
import argparse
import json
import os
import sqlite3
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 复用签发工具的纯函数（同目录上一级 tools/）
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))
import cardkey_tool as ck

DB_PATH = "backend/cards.db"
PRODUCT = "demo_app"


class Store:
    """SQLite 状态机：code -> 状态。线程安全（每操作独立连接 + 锁）。"""

    def __init__(self, path=DB_PATH):
        self.path = path
        self.lock = threading.Lock()
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        with self._conn() as c:
            c.execute("""CREATE TABLE IF NOT EXISTS cards (
                code TEXT PRIMARY KEY,
                device_id TEXT,
                max_activations INTEGER DEFAULT 1,
                activations INTEGER DEFAULT 0,
                expires_at INTEGER DEFAULT 0,
                serial INTEGER DEFAULT 1,
                revoked INTEGER DEFAULT 0,
                created_at INTEGER
            )""")

    def _conn(self):
        return sqlite3.connect(self.path)

    def add(self, code, expires_at, max_activations, serial):
        with self.lock, self._conn() as c:
            c.execute("INSERT OR REPLACE INTO cards VALUES (?,?,?,?,?,?,?,?)",
                      (code, "", max_activations, 0, expires_at, serial, 0,
                       int(time.time() * 1000)))

    def get(self, code):
        with self._conn() as c:
            row = c.execute("SELECT device_id,max_activations,activations,expires_at,serial,revoked FROM cards WHERE code=?",
                            (code,)).fetchone()
        if not row:
            return None
        return dict(device_id=row[0], max_activations=row[1], activations=row[2],
                    expires_at=row[3], serial=row[4], revoked=row[5])

    def activate(self, code, device_id):
        """尝试激活：返回 (ok, reason, card_state)。一机一码 + 次数上限。"""
        with self.lock, self._conn() as c:
            c.execute("BEGIN IMMEDIATE")
            row = c.execute("SELECT device_id,max_activations,activations,expires_at,serial,revoked FROM cards WHERE code=?",
                            (code,)).fetchone()
            if not row:
                c.rollback(); return False, "invalid", None
            dev, max_a, acts, expires, serial, revoked = row
            if revoked:
                c.rollback(); return False, "revoked", dict(device_id=dev, max_activations=max_a,
                    activations=acts, expires_at=expires, serial=serial, revoked=revoked)
            if expires and expires < int(time.time() * 1000):
                c.rollback(); return False, "expired", None
            if dev and dev != device_id:
                c.rollback(); return False, "device_mismatch", None
            if acts >= max_a and dev != device_id:
                c.rollback(); return False, "used", None
            new_dev = dev or device_id
            c.execute("UPDATE cards SET device_id=?, activations=activations+1 WHERE code=?",
                      (new_dev, code))
            c.commit()
            state = dict(device_id=new_dev, max_activations=max_a, activations=acts + 1,
                         expires_at=expires, serial=serial, revoked=0)
            return True, None, state

    def revoke(self, code):
        with self.lock, self._conn() as c:
            cur = c.execute("UPDATE cards SET revoked=1 WHERE code=?", (code,))
            return cur.rowcount > 0


class CardKeyHandler(BaseHTTPRequestHandler):
    store = None
    private_key = None
    pub_b64 = None
    admin_token = None

    def log_message(self, fmt, *args):
        # 不打印完整卡密（安全要点）
        sys.stderr.write("[backend] " + (fmt % args) + "\n")

    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        n = int(self.headers.get("Content-Length", 0))
        try:
            return json.loads(self.rfile.read(n).decode("utf-8")) if n else {}
        except Exception:
            return {}

    def _authed(self):
        return self.headers.get("Authorization") == "Bearer " + self.admin_token

    def do_GET(self):
        if self.path == "/health":
            return self._json(200, {"ok": True, "service": "cardkey-demo-backend"})
        if self.path == "/codes":
            if not self._authed():
                return self._json(401, {"ok": False, "message": "unauthorized"})
            with self.store._conn() as c:
                rows = c.execute("SELECT code,device_id,activations,max_activations,revoked FROM cards").fetchall()
            return self._json(200, {"ok": True, "codes": [
                {"code": r[0][:16] + "...", "device_id": r[1], "activations": r[2],
                 "max_activations": r[3], "revoked": bool(r[4])} for r in rows]})
        return self._json(404, {"ok": False})

    def do_POST(self):
        if self.path == "/verify":
            return self._verify()
        if self.path == "/issue":
            return self._issue()
        if self.path == "/revoke":
            return self._revoke()
        return self._json(404, {"ok": False})

    def _verify(self):
        d = self._read_json()
        code = (d.get("code") or "").strip()
        device_id = d.get("device_id") or ""
        if not code:
            return self._json(200, {"ok": False, "reason": "invalid", "message": "卡密为空"})
        ok, reason, state = self.store.activate(code, device_id)
        if not ok:
            messages = {"invalid": "卡密无效", "used": "卡密已使用", "expired": "卡密已过期",
                        "device_mismatch": "设备不匹配", "revoked": "卡密已封禁"}
            return self._json(200, {"ok": False, "reason": reason,
                                    "message": messages.get(reason, "验证失败")})
        # 用服务器私钥签发凭证；设备绑定写回状态机里的设备码
        payload = ck.build_payload(d.get("product_id") or PRODUCT, state["device_id"],
                                   int(time.time() * 1000), state["expires_at"], state["serial"])
        sig = ck.sign(payload, self.private_key)
        license_str = "KVL1-" + ck.b32encode(payload) + "-" + ck.b32encode(sig)
        return self._json(200, {"ok": True, "license": license_str})

    def _issue(self):
        if not self._authed():
            return self._json(401, {"ok": False, "message": "unauthorized"})
        d = self._read_json()
        serial = int(d.get("serial") or int(time.time() * 1000))
        expires = 0 if d.get("expires", "never") in ("never", "0", 0, None) else int(d["expires"])
        max_a = int(d.get("max_activations") or 1)
        # 卡密本体 = 临时私钥签发的凭证（离线语义）；此处用主私钥签发一张长期凭证作卡密
        payload = ck.build_payload(PRODUCT, "", int(time.time() * 1000), expires, serial)
        sig = ck.sign(payload, self.private_key)
        code = "KVL1-" + ck.b32encode(payload) + "-" + ck.b32encode(sig)
        self.store.add(code, expires, max_a, serial)
        return self._json(200, {"ok": True, "code": code, "serial": serial})

    def _revoke(self):
        if not self._authed():
            return self._json(401, {"ok": False, "message": "unauthorized"})
        code = (self._read_json().get("code") or "").strip()
        if self.store.revoke(code):
            return self._json(200, {"ok": True})
        return self._json(200, {"ok": False, "message": "not found"})


def main():
    ap = argparse.ArgumentParser(description="CardKey 演示后端")
    ap.add_argument("--private-key", required=True, help="服务器私钥路径（绝不进 git）")
    ap.add_argument("--public-key-base64", default="", help="公钥 base64（仅日志记录用，可选）")
    ap.add_argument("--admin-token", default="change-me", help="管理接口 Bearer token")
    ap.add_argument("--port", type=int, default=8080)
    ap.add_argument("--db", default=DB_PATH)
    args = ap.parse_args()
    if not os.path.exists(args.private_key):
        raise SystemExit("找不到私钥: " + args.private_key)

    CardKeyHandler.store = Store(args.db)
    CardKeyHandler.private_key = args.private_key
    CardKeyHandler.pub_b64 = args.public_key_base64
    CardKeyHandler.admin_token = args.admin_token

    srv = ThreadingHTTPServer(("0.0.0.0", args.port), CardKeyHandler)
    print(f"CardKey 演示后端启动: http://0.0.0.0:{args.port}")
    print("  管理接口需 Authorization: Bearer <admin-token>")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        print("\n已停止")
        srv.shutdown()


if __name__ == "__main__":
    main()
