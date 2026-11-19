# 密钥生成与签发指南

工具：`tools/cardkey_tool.py`（Python 3 + 系统 `openssl` 命令，无第三方库）。

## 1. 生成密钥对

```bash
python3 tools/cardkey_tool.py keygen --out keys
```

输出：

- `keys/private.pem` —— **私钥，服务器保管，绝不进 git / app**
- `keys/public_key_base64.txt` —— 公钥 Base64，填进客户端

把公钥内容复制到 `DemoApp.java`（或你的 app）的 `PUBLIC_KEY_BASE64`。

## 2. 签发卡密（离线模式：卡密即凭证）

```bash
python3 tools/cardkey_tool.py issue \
  --private-key keys/private.pem \
  --product my_app \
  --device ABCD1234EFGH5678 \
  --expires 2030-01-01 \
  --serial 1001 \
  --out card.txt
```

参数：

| 参数 | 说明 |
|---|---|
| `--product` | 产品标识，须与客户端 `productId` 一致 |
| `--device` | 绑定设备码；留空则不绑定（一码多处可用） |
| `--issued` | `now`（默认）或 `YYYY-MM-DD` |
| `--expires` | `0`/`never`=永久，或 `YYYY-MM-DD` |
| `--serial` | 序列号（整数），便于追溯 |

生成形如 `KVL1-....-....` 的卡密，发给用户即可。

> 设备码从哪来？Demo 启动后界面会显示"设备码：XXXX"。一机一码时，让用户提供这个码，你签发时 `--device` 填它。

## 3. 校验卡密（排查问题用）

```bash
python3 tools/cardkey_tool.py check "KVL1-....-...." \
  --public-key-file keys/public_key_base64.txt \
  --device ABCD1234EFGH5678
```

会打印载荷、签发/到期时间、签名是否有效、设备是否匹配。

## 4. 在线模式

在线模式下，卡密可以只是普通的激活码（如 `ABCD-EFGH-IJKL`），由你的服务器按 `docs/backend.md` 验证后返回签名 license。签发逻辑（上面第 2 步）放在服务端执行。

## 5. 生产注意事项

- 私钥绝不进客户端、不进 git。仓库 `.gitignore` 已忽略 `keys/` 和 `private.pem`。
- 当前 SDK 内置**单个公钥**。轮换密钥需要升级客户端（或自行扩展为多公钥/版本化）。
- 时间来源：敏感业务应以服务器时间为准，客户端时间可被用户修改。
