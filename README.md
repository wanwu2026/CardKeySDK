# CardKey · Android 卡密激活 SDK

一个**纯客户端（前端）**的卡密激活 SDK，面向 Android 应用与 Xposed 模块。

- 激活界面（输入卡密 → 验证 → 解锁）+ 客户端验证逻辑，我（AI）已写全
- **发卡、验卡、设备管理在后端**——由开发者自行实现，仓库里给出接口约定和签发工具
- 零第三方运行时依赖（仅用 Android 系统 API：`java.security`、`org.json`、`HttpURLConnection`）
- 两种模式：**离线**（卡密即凭证，本地验签）/ **在线**（服务器验卡并返回签名凭证），客户端验证逻辑统一

> ⚠️ **诚实说明（先看这条）**：本仓库是"客户端部分"。安全的卡密系统，**私钥和发卡逻辑绝不能放进 app**——一旦私钥进包，任何人都能自己签卡。真实产品必须有一台你掌控的服务器来保管私钥、验卡、封禁。本仓库的离线模式适合演示与信任环境；要商用请用在线模式 + 后端。详见 [安全模型](#安全模型)。

---

## 目录结构

```
CardKeySDK/
├── sdk/                         # SDK 库（可单独发布/引入）
│   └── src/main/
│       ├── java/com/example/cardkey/
│       │   ├── CardKey.java              # 单例入口：init / isActivated / activate
│       │   ├── core/
│       │   │   ├── Base32.java           # Base32 编解码
│       │   │   ├── LicensePayload.java   # 凭证载荷
│       │   │   ├── LicenseVerifier.java  # 验签 + 解析 + 设备/时间校验
│       │   │   ├── LicenseException.java # 可判别的错误码
│       │   │   ├── DeviceId.java         # 设备码（ANDROID_ID 哈希）
│       │   │   └── ActivationStore.java  # 防篡改持久化
│       │   ├── remote/
│       │   │   ├── RemoteVerifier.java   # 可插拔远程验卡接口
│       │   │   └── HttpRemoteVerifier.java # 默认 HTTP 实现
│       │   └── ui/ActivationDialog.java  # 通用激活对话框
│       └── res/layout/ck_dialog_activation.xml
├── demo/                        # 示例 app（含 Xposed 接入示例）
│   ├── demo_license.txt                  # 演示卡密（免设备绑定，直接复制激活）
│   └── src/main/java/com/example/cardkeydemo/
│       ├── DemoApp.java                  # 初始化 SDK（已内嵌演示公钥）
│       ├── MainActivity.java             # 判断激活状态 + 弹窗
│       └── XposedCardKeyGate.java        # Xposed 模块接入骨架
├── tools/cardkey_tool.py        # 密钥生成 / 签发 / 校验工具（不进 app）
├── docs/backend.md              # 后端接口约定
├── docs/keygen.md               # 密钥生成与签发指南
└── settings.gradle / build.gradle / */build.gradle
```

---

## 快速开始（跑 Demo）

1. 用 Android Studio 打开本目录（需要 Gradle 8.x + Android SDK 34）。
2. 编译 `demo` 模块，安装到手机。
3. 打开 app，点「激活 / 更换卡密」，粘贴 `demo/demo_license.txt` 里的卡密。
4. 显示「✓ 已激活」即成功。点「清除激活状态」可反复测试。

Demo 已内置一对演示密钥和一张**免设备绑定、2035 年到期**的卡密，开箱即可验证流程。它只用于演示——换成你自己的密钥见 [docs/keygen.md](docs/keygen.md)。

> 本机未安装 JDK / Android SDK，**未在本机编译验证**。Demo 的 Java 代码经过逐文件自查 + 核心算法用 Python/OpenSSL 交叉验证（见文末"验证记录"），但请在你的 Android Studio 里实际编译运行一次。

---

## 接入你的 App（3 步）

**1. 依赖**：把 `sdk` 模块作为 library 引入（`implementation project(':sdk')`），或把它打包成 AAR。

**2. 初始化**（建议在 `Application.onCreate`）：

```java
CardKey.init(new CardKey.Config(this)
        .productId("my_app")                  // 你的产品标识
        .publicKeyBase64(PUBLIC_KEY_BASE64)   // 后端公钥（见 keygen.md）
        .mode(CardKey.Mode.ONLINE)            // 或 OFFLINE
        .remoteVerifier(new HttpRemoteVerifier("https://your.api/verify"))
);
```

**3. 业务判断 / 激活**：

```java
// 是否解锁功能
if (CardKey.get().isActivated()) {
    // 已激活，放行
}

// 弹出激活窗
new ActivationDialog(activity)
    .setOnActivatedListener(p -> { /* 激活成功 */ })
    .show();

// 或直接调用（无 UI）
CardKey.get().activate(code, new CardKey.Callback() {
    public void onSuccess(LicensePayload p) { /* 成功 */ }
    public void onError(LicenseException e) { toast(e.friendlyMessage()); }
});
```

---

## 卡密格式

凭证 = `KVL1-<载荷Base32>-<签名Base32>`（`KVL1` 前缀可省略）。

载荷为定长大端二进制：

| 字段 | 类型 | 说明 |
|---|---|---|
| magic | uint32 | `0x4B564C31`（"KVL1"） |
| version | uint8 | 当前为 1 |
| productId | uint16 长度 + UTF-8 | 产品标识 |
| deviceId | uint16 长度 + UTF-8 | 绑定的设备码，空=不绑定 |
| issuedAt | int64 | 签发时间 epoch ms |
| expiresAt | int64 | 到期时间 epoch ms，0=永久 |
| serial | int64 | 序列号 |

签名 = `RSASSA-PKCS1-v1_5 / SHA-256`，对上面整个 payload 原始字节签名，RSA-2048（256 字节）。Base32 用 RFC 4648 字母表 `A-Z2-7`，无 `=` 填充。

客户端验证顺序：**验签 → 解析 → 设备绑定校验 → 有效期校验**。任何一步失败即拒绝。

---

## 安全模型

- **验签永远在客户端做一遍**：即使在线模式下服务器返回凭证，客户端也用内置公钥验签，防中间人/被攻陷的服务器伪造。
- **持久化不是存 boolean**：保存的是已验签的凭证串，每次读取重新验签 + 校验设备 + 校验时间，单独改 SharedPreferences 无效。
- **设备绑定**：签发时写入 `deviceId`，换设备即用不了（ANDROID_ID 在恢复出厂/部分 ROM 会变，属已知限制）。
- **fail-closed**：Xposed 示例里拿不到状态就当未激活。

**做不到的（别指望客户端能防）**：
- 防逆向/二次打包：把你的激活判断逻辑 patch 掉。缓解靠代码混淆 + 服务端校验业务关键数据。
- 防"分享合法卡密"：靠设备绑定 + 后端记录激活次数。
- 防 root/Xposed 环境本身：这是攻防对抗，无银弹。

**结论**：真正的安全边界在后端（私钥不泄露 + 一机一码 + 实时封禁）。客户端 SDK 负责把体验做好、把明显的篡改挡住。

---

## 后端要做什么

只需提供一个验卡接口，返回签名凭证。完整约定见 [docs/backend.md](docs/backend.md)，签发工具见 [docs/keygen.md](docs/keygen.md)。

请求：

```json
POST /verify
{"code":"卡密","device_id":"设备码","product_id":"产品标识"}
```

响应：

```json
// 成功
{"ok":true,"license":"KVL1-....-...."}
// 失败
{"ok":false,"reason":"invalid|used|expired","message":"卡密已使用"}
```

`license` 由后端用**私钥**对载荷签名生成——直接复用 `tools/cardkey_tool.py issue` 的逻辑即可。

---

## 验证记录

本机环境：OpenSSL 3.5 / Python 3.13 / git；**无 JDK / Gradle / Android SDK**。

| 项目 | 状态 |
|---|---|
| 密钥生成 → 签发 → 校验（Python+OpenSSL 端到端） | ✅ 通过 |
| 载荷二进制格式 round-trip（含中文/UTF-8、时间戳、设备码） | ✅ 通过 |
| 签名有效性 / 设备匹配判别 | ✅ 通过 |
| Base32 与 Java 实现一致性（同字母表、无填充） | ✅ 人工核对 |
| Android 模块编译 | ⚠️ 未在本机编译（无 SDK），请在 Android Studio 验证 |

---

## License

MIT —— 见 [LICENSE](LICENSE)。用它做闭源商业软件也可以；但请遵守"私钥不进客户端"的安全原则。
