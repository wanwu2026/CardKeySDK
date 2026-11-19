# 贡献指南 / Contributing

欢迎 Issue 与 PR。为了让改动能被快速合并，请遵守以下约定。

## 报告问题 / Issues
- 用 Issue 模板，写清：复现步骤、期望行为、实际行为、环境（Android 版本 / 分支名 / SDK 版本）。
- 卡密相关的安全问题**不要公开 Issue**，见 [SECURITY.md](SECURITY.md)。

## 提交代码 / Pull Requests
1. Fork 后从目标分支建 feature 分支：`normal-android-app`（独立 App）或 `xposed-injection-module`（Xposed 模块）。
2. 保持改动聚焦；一个 PR 只做一件事。
3. 改 Python 工具后跑：`python3 tools/test_cardkey_tool.py`，确保全绿。
4. 改 Java 代码后：在 Android Studio 里编译 `demo` 模块（compileSdk 34 / JDK 17），确认无 error。
5. 提交信息用约定式前缀：`feat:` / `fix:` / `docs:` / `test:` / `refactor:`。
6. PR 描述里写清：改了什么、为什么、怎么验证的。

## 重要原则
- **私钥绝不进仓库**：任何 `*.pem` / `keys/` / 私钥字符串都不要提交。
- 客户端验证逻辑（验签 → 解析 → 设备 → 时间）的顺序不要随意改。
- 文档改动请同时更新 README 的中英文两节。

## 语言
- 中文、英文 Issue / PR 都欢迎。代码注释保持精简的常规风格。
