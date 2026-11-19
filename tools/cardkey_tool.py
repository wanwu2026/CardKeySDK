#!/usr/bin/env python3
"""
cardkey_tool.py —— 卡密签发工具（开发者/后端用，不进 app）

功能：
  1) 生成 RSA-2048 密钥对，输出私钥 private.pem（你自己保管，别进仓库）
     和公钥 public_key_base64.txt（嵌进 app 的 CardKey.Config）
  2) 签发卡密（离线模式：卡密本身就是签名凭证）
  3) 校验卡密（用来验证与 Java SDK 的算法一致性）

卡密二进制格式（大端，与 Java DataOutputStream 一致）：
  magic:uint32 | version:uint8 | productId:len+utf8 | deviceId:len+utf8 |
  issuedAt:int64 | expiresAt:int64 | serial:int64
签名：RSASSA-PKCS1-v1_5 / SHA-256，对上面整个 payload 字节签名
展示：Base32(payload).Base32(sig)  （RFC4648，无 = 填充）

依赖：仅 Python 标准库 + 系统 openssl 命令（用 cryptography 更好，但这里保持零依赖）。
"""
import argparse
import base64
import datetime
import os
import struct
import subprocess
import tempfile

MAGIC = 0x4B564C31  # "KVL1"
VERSION = 1
B32ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"


# ----------------------------- Base32 (RFC4648, no padding) -----------------------------
def b32encode(data: bytes) -> str:
    bits = ""
    for b in data:
        bits += format(b, "08b")
    while len(bits) % 5:
        bits += "0"
    return "".join(B32ALPHABET[int(bits[i:i + 5], 2)] for i in range(0, len(bits), 5))


def b32decode(text: str) -> bytes:
    s = "".join(c for c in text.upper() if c in B32ALPHABET)
    bits = "".join(format(B32ALPHABET.index(c), "05b") for c in s)
    out = bytearray()
    for i in range(0, len(bits) - len(bits) % 8, 8):
        out.append(int(bits[i:i + 8], 2))
    return bytes(out)


# ----------------------------- payload -----------------------------
def build_payload(product_id: str, device_id: str, issued_at: int, expires_at: int, serial: int) -> bytes:
    pb = product_id.encode("utf-8")
    db = device_id.encode("utf-8")
    if len(pb) > 1024 or len(db) > 1024:
        raise ValueError("字符串过长")
    return (struct.pack(">I", MAGIC)
            + struct.pack(">B", VERSION)
            + struct.pack(">H", len(pb)) + pb
            + struct.pack(">H", len(db)) + db
            + struct.pack(">q", issued_at)
            + struct.pack(">q", expires_at)
            + struct.pack(">q", serial))


def parse_payload(data: bytes) -> dict:
    off = 0
    magic = struct.unpack_from(">I", data, off)[0]; off += 4
    version = struct.unpack_from(">B", data, off)[0]; off += 1
    if magic != MAGIC:
        raise ValueError("magic 不匹配")
    if version != VERSION:
        raise ValueError("版本不支持")
    plen = struct.unpack_from(">H", data, off)[0]; off += 2
    product = data[off:off + plen].decode("utf-8"); off += plen
    dlen = struct.unpack_from(">H", data, off)[0]; off += 2
    device = data[off:off + dlen].decode("utf-8"); off += dlen
    issued = struct.unpack_from(">q", data, off)[0]; off += 8
    expires = struct.unpack_from(">q", data, off)[0]; off += 8
    serial = struct.unpack_from(">q", data, off)[0]; off += 8
    return dict(product=product, device=device, issued=issued, expires=expires, serial=serial)


# ----------------------------- openssl 封装 -----------------------------
def gen_keypair(outdir="."):
    os.makedirs(outdir, exist_ok=True)
    priv = os.path.join(outdir, "private.pem")
    pub_der = os.path.join(outdir, "public.der")
    subprocess.run(["openssl", "genrsa", "-out", priv, "2048"], check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    subprocess.run(["openssl", "rsa", "-in", priv, "-pubout", "-outform", "DER", "-out", pub_der],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    with open(pub_der, "rb") as f:
        b64 = base64.b64encode(f.read()).decode("ascii")
    with open(os.path.join(outdir, "public_key_base64.txt"), "w") as f:
        f.write(b64 + "\n")
    os.remove(pub_der)
    print("已生成：")
    print("  ", priv, "  <- 私钥，务必保密，不要进 git")
    print("  ", os.path.join(outdir, "public_key_base64.txt"), "  <- 把内容填进 app 的 PUBLIC_KEY_BASE64")
    print("\n公钥 Base64（前 80 字符预览）:", b64[:80], "...")


def sign(payload: bytes, priv_path: str) -> bytes:
    with tempfile.NamedTemporaryFile(delete=False) as tf:
        tf.write(payload)
        data_path = tf.name
    sig_path = data_path + ".sig"
    try:
        subprocess.run(["openssl", "dgst", "-sha256", "-sign", priv_path, "-out", sig_path, data_path],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        with open(sig_path, "rb") as f:
            return f.read()
    finally:
        os.remove(data_path)
        if os.path.exists(sig_path):
            os.remove(sig_path)


def verify_sig(payload: bytes, sig: bytes, pub_b64: str) -> bool:
    with tempfile.TemporaryDirectory() as d:
        der = os.path.join(d, "pub.der")
        data = os.path.join(d, "data")
        sg = os.path.join(d, "sig")
        with open(der, "wb") as f:
            f.write(base64.b64decode(pub_b64))
        with open(data, "wb") as f:
            f.write(payload)
        with open(sg, "wb") as f:
            f.write(sig)
        r = subprocess.run(["openssl", "dgst", "-sha256", "-verify", der, "-signature", sg, data],
                           stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        return r.returncode == 0


def ts_to_millis(s: str) -> int:
    """支持 YYYY-MM-DD 或 YYYY-MM-DD HH:MM:SS；0/never 表示永久"""
    if s in ("0", "never", "永久"):
        return 0
    fmt = "%Y-%m-%d %H:%M:%S" if " " in s else "%Y-%m-%d"
    dt = datetime.datetime.strptime(s, fmt)
    return int(dt.timestamp() * 1000)


def millis_to_str(ms: int) -> str:
    if ms == 0:
        return "永久"
    return datetime.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M:%S")


def issue(args):
    priv = args.private_key
    if not os.path.exists(priv):
        raise SystemExit("找不到私钥: " + priv)
    issued = int(datetime.datetime.now().timestamp() * 1000) if args.issued == "now" else ts_to_millis(args.issued)
    payload = build_payload(args.product, args.device, issued, ts_to_millis(args.expires), args.serial)
    sig = sign(payload, priv)
    license_str = "KVL1-" + b32encode(payload) + "-" + b32encode(sig)
    print("卡密已签发：\n" + license_str)
    print("\n载荷：", parse_payload(payload))
    if args.out:
        with open(args.out, "w") as f:
            f.write(license_str + "\n")
        print("已写入", args.out)


def check(args):
    lic = args.license.strip()
    if lic.upper().startswith("KVL1-"):
        lic = lic[5:]
    parts = lic.replace("-", ".").split(".")
    if len(parts) != 2:
        raise SystemExit("卡密格式错误")
    payload = b32decode(parts[0])
    sig = b32decode(parts[1])
    info = parse_payload(payload)
    pub = args.public_key_base64
    if not pub:
        with open(args.public_key_file) as f:
            pub = f.read().strip()
    ok = verify_sig(payload, sig, pub)
    print("载荷:", info)
    print("签发:", millis_to_str(info["issued"]))
    print("到期:", millis_to_str(info["expires"]))
    print("签名:", "✓ 有效" if ok else "✗ 无效")
    if args.device:
        match = (info["device"] == "" or info["device"] == args.device)
        print("设备:", info["device"] or "(未绑定)", "输入:", args.device, "=>", "✓ 匹配" if match else "✗ 不匹配")
    if not ok:
        raise SystemExit(1)


def main():
    p = argparse.ArgumentParser(description="CardKey 卡密签发/校验工具")
    sub = p.add_subparsers(dest="cmd")

    g = sub.add_parser("keygen", help="生成密钥对")
    g.add_argument("--out", default="keys", help="输出目录")

    i = sub.add_parser("issue", help="签发卡密")
    i.add_argument("--private-key", required=True)
    i.add_argument("--product", default="demo_app")
    i.add_argument("--device", default="", help="绑定的设备码，留空则不绑定")
    i.add_argument("--issued", default="now", help="now 或 YYYY-MM-DD")
    i.add_argument("--expires", default="0", help="0/never=永久 或 YYYY-MM-DD")
    i.add_argument("--serial", type=int, default=1)
    i.add_argument("--out", default="")
    i.set_defaults(func=issue)

    c = sub.add_parser("check", help="校验卡密")
    c.add_argument("license", help="卡密字符串")
    c.add_argument("--public-key-base64", default="")
    c.add_argument("--public-key-file", default="public_key_base64.txt")
    c.add_argument("--device", default="")
    c.set_defaults(func=check)

    args = p.parse_args()
    if args.cmd == "keygen":
        gen_keypair(args.out)
    elif args.cmd in ("issue", "check"):
        args.func(args)
    else:
        p.print_help()


if __name__ == "__main__":
    main()
