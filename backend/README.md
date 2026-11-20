# CardKey 演示后端 / Demo Backend

一个**可直接运行**的验卡/发卡服务，对应 [docs/backend.md](../docs/backend.md) 的约定。用于演示「私钥只放服务器」的完整闭环，以及一机一码、激活次数、封禁的策略。

> ⚠️ 这是**演示实现**，用 SQLite + 单文件密钥，适合学习与本地联调。生产请换 KMS/HSM 管私钥、PostgreSQL、HTTPS、限流与审计日志。

## 依赖
- Python 3.8+
- 系统 `openssl` 命令（签发/验签复用 `tools/cardkey_tool.py`）
- 零第三方 pip 包

## Docker 一键启动

```bash
docker build -t cardkey-backend -f backend/Dockerfile .
docker run -d -p 8080:8080 \
    -v $(pwd)/backend/keys:/app/backend/keys \
    -e ADMIN_TOKEN=change-me \
    -e PRIVATE_KEY=/app/backend/keys/private.pem \
    cardkey-backend
```

私钥通过卷挂载进容器，**不写进镜像层**；容器内置 `/health` 健康检查。

## 快速开始

```bash
# 1. 生成服务器密钥（私钥只在服务器，绝不进 git）
python3 tools/cardkey_tool.py keygen --out backend/keys
#    → backend/keys/private.pem（保密）
#    → backend/keys/public_key_base64.txt（把内容填进 App 的 PUBLIC_KEY_BASE64）

# 2. 启动服务
python3 backend/server.py \
    --private-key backend/keys/private.pem \
    --public-key-base64 backend/keys/public_key_base64.txt \
    --admin-token my-secret --port 8080
```

`backend/keys/` 已被 `.gitignore` 忽略。

## 接口

### `GET /health`
```json
{"ok": true, "service": "cardkey-demo-backend"}
```

### `POST /issue`（管理接口）
请求头：`Authorization: Bearer <admin-token>`
```json
{"serial": 1001, "max_activations": 1, "expires": "never"}
```
响应：
```json
{"ok": true, "code": "KVL1-...-...", "serial": 1001}
```
`expires` 可为 `"never"`/`0`（永久）或 epoch 毫秒整数。

### `POST /verify`（SDK 调用）
```json
{"code": "KVL1-...", "device_id": "设备码", "product_id": "demo_app"}
```
成功（200）：
```json
{"ok": true, "license": "KVL1-<payload>-<sig>"}
```
失败（200，`ok:false`）：
```json
{"ok": false, "reason": "invalid|used|expired|device_mismatch|revoked", "message": "卡密已使用"}
```
返回的 `license` 由服务器私钥签发，客户端会用内置公钥再验一次签——响应即使被篡改也过不了。

### `POST /revoke`（管理接口）
```json
{"code": "KVL1-..."}
```

### `GET /codes`（管理接口）
列出卡密状态（码只显示前 16 位）。

## 状态机
```
未激活 ──首次 verify(device_id)──> 已绑定(device_id)
   │                                  │
   └──revoke──> 已撤销               └──换设备 verify──> device_mismatch
```
- 一机一码：首次激活锁定 `device_id`，之后只认该设备。
- 激活次数：`max_activations` 上限（默认 1）。
- 封禁：`revoked` 的码一律拒绝。
- 到期：`expires_at` 过期拒绝。

## 测试
```bash
python3 backend/test_server.py
```
覆盖：发卡 → 验卡 → 验签 → 一机一码拒绝 → 重复激活 → 封禁 → 无效卡密。

## 接入 SDK
把 `HttpRemoteVerifier` 的 endpoint 指向本服务的 `/verify`：
```java
CardKey.init(new CardKey.Config(this)
    .productId("demo_app")
    .publicKeyBase64(PUBLIC_KEY_BASE64)   // 上面生成的公钥
    .mode(CardKey.Mode.ONLINE)
    .remoteVerifier(new HttpRemoteVerifier("http://你的服务器:8080/verify")));
```
Android 9+ 默认禁明文 HTTP，用 HTTPS 或在 `AndroidManifest` 配 `usesCleartextTraffic="true"`（仅演示）。
