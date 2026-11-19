# 安全策略 / Security Policy

## 支持的版本
- `normal-android-app`、`xposed-injection-module` 两个分支的最新提交。

## 报告漏洞
请不要在公开 Issue 里披露安全问题。用 GitHub 的 **Security → Report a vulnerability** 私下报告，包含：
- 受影响分支 / 组件
- 复现步骤与影响
- 建议的修复（如有）

## 安全模型须知
- 本项目是**客户端 SDK**：私钥和发卡逻辑必须在你自己的服务器上，绝不能进 app。
- 离线模式（卡密即凭证）适合演示与信任环境；生产请用在线模式 + 后端二次确认。
- 客户端无法防逆向/二次打包；关键业务请由服务端裁定。
- 详见 [docs/backend.md](docs/backend.md) 与 README「安全模型」。
