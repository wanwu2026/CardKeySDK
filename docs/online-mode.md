# 在线模式接入指南 / Online Mode Guide

离线模式（卡密即凭证、本地验签）适合演示与信任环境。要商用、要一机一码、
实时封禁，需要**在线模式**：卡密发给你的服务器，服务器验卡后返回签名凭证，
客户端再用内置公钥验一遍签，最后持久化。

本指南以仓库自带的 `demo-backend` 为例，走通完整闭环。它与你自己的后端
的差异只在于接口地址与密钥。

## 1. 准备工作

1. 拉取 `demo-backend` 分支，生成服务器密钥：
   ```bash
   python3 tools/cardkey_tool.py keygen --out backend/keys
   ```
2. 启动后端（三选一）：
   ```bash
   # 直接运行
   python3 backend/server.py \
       --private-key backend/keys/private.pem \
       --public-key-base64 backend/keys/public_key_base64.txt \
       --admin-token my-secret --port 8080

   # 或 Docker（私钥卷挂载，不进镜像）
   docker build -t cardkey-backend -f backend/Dockerfile .
   docker run -d -p 8080:8080 \
       -v $(pwd)/backend/keys:/app/backend/keys \
       -e ADMIN_TOKEN=my-secret \
       -e PRIVATE_KEY=/app/backend/keys/private.pem \
       cardkey-backend
   ```
3. 记住两点：
   - `private.pem` 只放服务器，绝不进 App / git。
   - `public_key_base64.txt` 的内容要填进客户端。

## 2. 发卡（管理接口）

```bash
curl -X POST http://127.0.0.1:8080/issue \
  -H "Authorization: Bearer my-secret" \
  -H "Content-Type: application/json" \
  -d '{"serial": 1001, "max_activations": 1, "expires": "never"}'
# → {"ok":true,"code":"KVL1-...-...","serial":1001}
```

`code` 就是发给用户的卡密。后端把它的状态存在 SQLite（`backend/cards.db`）。

## 3. 客户端接入在线模式

初始化时把 `mode` 设为 `ONLINE`，并传入指向你服务器 `/verify` 的 verifier：

```java
CardKey.init(new CardKey.Config(this)
        .productId("demo_app")
        .publicKeyBase64(PUBLIC_KEY_BASE64)   // 第 1 步生成的公钥
        .mode(CardKey.Mode.ONLINE)
        .remoteVerifier(new HttpRemoteVerifier(
                "http://192.168.1.10:8080/verify")));  // 你的服务器地址
```

激活流程与离线模式**完全一样**（`CardKey.get().activate(...)`），因为在线
模式的差异只发生在内部：`HttpRemoteVerifier` 把卡密 + 设备码 + 产品 ID POST
到服务器，服务器返回 `{"ok":true,"license":"KVL1-..."}`，SDK 再用内置公钥
对返回的 license 验签、绑定设备、保存。

## 4. 一机一码与封禁（服务器侧自动生效）

- 首次激活：服务器把 `device_id` 写入该卡密记录，之后**换设备**会返回
  `device_mismatch`，客户端拒绝。
- 重复激活：同设备可反复激活；换设备被拒。
- 封禁：管理员调用 `POST /revoke`（带 admin token），该码之后一律拒绝。

```bash
curl -X POST http://127.0.0.1:8080/revoke \
  -H "Authorization: Bearer my-secret" -H "Content-Type: application/json" \
  -d '{"code":"KVL1-...-..."}'
# → {"ok":true}
```

## 5. 安全要点（在线模式）

- **HTTPS 必开**：示例用明文 HTTP 仅为本地演示。生产必须走 HTTPS，否则
  服务器返回的 license 会被中间人看到（虽然后续验签能挡住伪造，但隐私与
  完整性仍需 TLS）。
- **客户端总是再验一遍签**：即使服务器是可信的，`CardKey` 仍会用内置公钥
  验证返回的 license。这意味着**哪怕服务器被攻陷/返回被篡改**，客户端也
  不会接受伪造凭证。
- **撤销的实时性**：本地缓存一旦写入，下次启动仍有效。如果你需要「一旦
  封禁立即失效」，业务关键操作应回服务器二次确认（见 README 安全模型）。

## 6. 与离线模式的取舍

| | 离线 | 在线 |
|---|---|---|
| 联网 | 不需要 | 需要 |
| 一机一码 | 靠签发时写死 deviceId | 服务器动态绑定 |
| 封禁/撤销 | 无（只能等到期） | 实时 |
| 私钥 | 服务器签发后即用 | 服务器每次验卡后签发 |
| 适用 | 演示、信任环境 | 商用、需要风控 |

> ⚠️ 离线模式的卡密本质上是一张「可被到处复制的签名凭证」；在线模式才能
> 做到"这张卡只在这台设备、这个时刻有效"。真实产品请用在线。