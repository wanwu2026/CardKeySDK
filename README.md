# CardKey · Android 卡密激活 SDK

> 🤖 本项目由 AI 生成。

一个**纯客户端（前端）**的卡密激活 SDK，面向 Android 应用与 Xposed 模块。

- 激活界面（输入卡密 → 验证 → 解锁）+ 客户端验证逻辑，已实现
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
├── docs/
│   ├── backend.md               # 后端接口约定
│   ├── keygen.md                # 密钥生成与签发指南
│   └── screenshots/             # App 效果截图（见"截图展示"）
├── .github/workflows/build-apk.yml  # CI：云端编译并发布 Demo APK 到 Releases
└── settings.gradle / build.gradle / */build.gradle
```

---

## 快速开始（跑 Demo）

### 方式一：直接装预编译 APK（最快）

到 [Releases](https://github.com/wanwu2026/CardKeySDK/releases) 下载 `CardKeyDemo-release.apk`，传到 Android 手机安装（需允许"安装未知来源应用"）。

> 该 APK 由 GitHub Actions 云端真编译、用临时密钥签名，每次构建签名都会变——**重装前请先卸载旧版**（签名不同无法覆盖安装）。

1. 打开 app，会显示「未激活」和当前设备码。
2. 点「激活 / 更换卡密」，粘贴下面这张演示卡密：
   ```
   KVL1-JNLEYMIBAAEGIZLNN5PWC4DQAAAAAAABUHZQ535UAAAADXMWMHWAAAAAAAAAAAAD5E-S6IZXUQAQOKLJZEKS6KADYSB5CCCBXYZGG66AYHXSI6OSUJMHMAQK5XITEKRGOLTCRE4WXBQHEJQGUSVSQZCKKD6FOTZ73SU5LNKHYE2VPYLBG3RVI6ZDFS4WDPH7TFFAWJWIU6JBKWAZ7LSOHDHUWKL2M72TJDMDUVLCGITDBWGTSFY74C65NGP2UKGHSXA3A5XPW35PF7ZECNJWESL7ZBPEGUECXOAQMLDV7UETSLH3CRMKMQIYY43NMLSMP6TA6XZESNCL4MIIVR3JVOTM4HBLG55IEYUDFRRTNSFD2LQWG5LNZANYXZOSBWDRH32BC6HVNBP56ANLD3GGCMK2KWBTUAF2VTUC7BCXZE6N4LI2NPSG6AZ5QH6Z4G4IO3JWGF2CIFZKYUG2E2AG6OYLZRDRY
   ```
   （同一张也在 `demo/demo_license.txt`，**免设备绑定、2035 年到期**，任何设备可用）
3. 显示「✓ 已激活」即成功。点「清除激活状态」可反复测试。

### 方式二：自己编译

用 Android Studio 打开本目录（Gradle 8.7 + Android SDK 34 + JDK 17），编译 `demo` 模块安装到手机。离线第一次构建需联网下载依赖。

> APK 是云端真编译产出的（见 [.github/workflows/build-apk.yml](.github/workflows/build-apk.yml)）。核心算法另用 Python/OpenSSL 交叉验证（见文末"验证记录"）。本仓库不内置 Android SDK 与签名密钥。

---

## 截图展示

> 📷 效果截图将由作者补充。以下为预留位置（把图片放进 `docs/screenshots/` 后，替换对应路径即可显示）。

| 未激活 | 激活成功 |
|---|---|
| ![未激活界面](docs/screenshots/01-inactive.png) | ![激活成功](docs/screenshots/02-activated.png) |

| 输入卡密 | 设备码 / Xposed 场景 |
|---|---|
| ![输入卡密](docs/screenshots/03-activate-dialog.png) | ![Xposed 场景](docs/screenshots/04-xposed.png) |

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

## 常见问题（FAQ）

<details>
<summary><b>Q：演示卡密为什么在任何设备都能用？</b></summary>

它签发时 `deviceId` 为空（不绑定设备），且到期时间设为 2035 年——故意做成"开箱即用"。真正的一机一码请用 `cardkey_tool.py issue --device <设备码>` 签发，或走在线模式由后端绑定。
</details>

<details>
<summary><b>Q：提示"此卡密已在其他设备激活"是怎么回事？</b></summary>

卡密签发时绑定了某台设备的 `deviceId`（ANDROID_ID 的 SHA256 前 16 位）。恢复出厂、换机、部分 ROM 重置 ANDROID_ID 都会变，导致不匹配。需要后端做解绑/换机流程。
</details>

<details>
<summary><b>Q：改了系统时间是不是就能一直用？</b></summary>

客户端会读系统时间判断到期，改时间能骗过**本地**校验。敏感业务必须把到期/封禁放在服务器裁定（在线模式 + 关键操作二次确认）。这是客户端方案的固有限制，不是 bug。
</details>

<details>
<summary><b>Q：APK 安装时报"签名冲突 / 应用未安装"？</b></summary>

CI 每次构建用新生成的临时签名。请先卸载旧版 Demo 再装新版。正式发布请用你自己的固定 keystore。
</details>

<details>
<summary><b>Q：我要发自己的卡密，私钥放哪？</b></summary>

放你自己的服务器或离线安全环境，**绝不进 app、不进 git**。`tools/cardkey_tool.py keygen` 生成后，`private.pem` 自己保管，公钥填进客户端。详见 [docs/keygen.md](docs/keygen.md)。
</details>

<details>
<summary><b>Q：离线模式够安全吗？</b></summary>

演示/小工具够用。但它挡不住"把合法卡密到处分享"。要商用请用在线模式 + 一机一码 + 激活次数限制 + 实时封禁，见 [安全模型](#安全模型) 和 [docs/backend.md](docs/backend.md)。
</details>

---

## 验证记录

| 项目 | 状态 |
|---|---|
| 密钥生成 → 签发 → 校验（Python + OpenSSL 端到端） | ✅ 通过 |
| 载荷二进制格式 round-trip（含 UTF-8、时间戳、设备码） | ✅ 通过 |
| 签名有效性 / 设备匹配判别 | ✅ 通过 |
| Base32 与 Java 实现一致性（同字母表、无填充） | ✅ 人工核对 |
| Demo APK 云端编译（GitHub Actions, Ubuntu + JDK 17 + SDK 34） | ✅ 见 [Releases](https://github.com/wanwu2026/CardKeySDK/releases) |
| 真机运行 | ⚠️ 请在你的设备上实测 |

说明：作者机器为 aarch64，Google 未提供该架构的 Android build-tools 原生包，故 APK 由 GitHub Actions 的 x86_64 runner 真编译产出，而非本机模拟。

---

## License

MIT —— 见 [LICENSE](LICENSE)。用它做闭源商业软件也可以；但请遵守"私钥不进客户端"的安全原则。
