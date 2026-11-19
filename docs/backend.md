# 后端接口约定

后端负责：保管私钥、发卡、验证卡密、记录激活、封禁。客户端 SDK 只做"验签 + 本地状态"。

## 1. 密钥管理

- 用 `tools/cardkey_tool.py keygen` 生成 RSA-2048 密钥对。
- `private.pem` **只放服务器**，永不进 app / git / 日志。
- `public_key_base64.txt` 的内容嵌入客户端 `CardKey.Config.publicKeyBase64`。
- 轮换密钥：客户端预留多公钥或做版本化升级（当前 SDK 单公钥，需升级 app 换公钥）。

## 2. 验卡接口（实现 `RemoteVerifier`）

SDK 默认实现 `HttpRemoteVerifier` 的请求/响应如下。你也可以注入自己的实现（Retrofit 等）。

**请求**

```
POST {endpoint}
Content-Type: application/json
Authorization: Bearer <可选 token>

{
  "code": "用户输入的卡密",
  "device_id": "当前设备码",
  "product_id": "产品标识"
}
```

**响应 200**

```json
{
  "ok": true,
  "license": "KVL1-<载荷Base32>-<签名Base32>"
}
```

`license` 是后端用私钥对载荷签发的凭证。**客户端会用公钥再验一次签**，所以即使响应被篡改也过不了。

**响应（业务拒绝，建议 200 或 4xx 均可）**

```json
{
  "ok": false,
  "reason": "invalid | used | expired | device_mismatch | revoked",
  "message": "给用户看的提示"
}
```

SDK 会把 `ok:false` 包装成 `LicenseException.Code.SERVER_REJECTED`。

## 3. 签发 license

服务端逻辑与 `tools/cardkey_tool.py issue` 完全一致（可直接移植到你的服务端语言）：

1. 构造载荷：`magic | version | productId | deviceId | issuedAt | expiresAt | serial`
   - 大端，`productId`/`deviceId` 为 `uint16 长度 + UTF-8`
   - `expiresAt=0` 表示永久，`deviceId=""` 表示不绑定设备
2. 对 payload 原始字节做 `SHA256withRSA` 签名（RSA-2048）
3. 输出 `KVL1-` + Base32(payload) + `-` + Base32(sig)

Python 参考实现见 `tools/cardkey_tool.py`。Java 服务端可用：

```java
Signature s = Signature.getInstance("SHA256withRSA");
s.initSign(privateKey);
s.update(payloadBytes);
byte[] sig = s.sign();
```

## 4. 后端状态机（建议）

```
未使用 ──激活成功──> 已绑定(device_id) ──到期──> 过期
    │                      │
    └──封禁──> 已撤销        └──换设备请求──> 人工/自助解绑
```

- 一机一码：签发时写 `deviceId`，激活时后端核对并锁定。
- 激活次数：记录 `code -> 激活次数`，超限时返回 `used`。
- 实时封禁：已撤销的码即使本地有凭证，业务关键操作也应回服务器二次确认（客户端本地校验无法立即感知封禁）。

## 5. 安全要点

- HTTPS 必开，接口加 token/签名防刷。
- 私钥泄露 = 系统作废，务必隔离存储（KMS / HSM / 环境变量）。
- 时间以服务器为准：客户端时间可被改，到期判断在敏感场景应由服务器裁定。
- 日志不要打印完整卡密与 license。
