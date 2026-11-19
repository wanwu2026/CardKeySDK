# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。

## [Unreleased]

### Added
- `docs/screenshots/`：四张真机实拍截图，README「截图展示」直接可见
- 分支 `xposed-injection-module`：Xposed 注入式模块版本（hook 目标 App）
- 分支 `normal-android-app`：普通独立 Android 应用版本（默认分支）
- README 中英双语、分支互跳链接
- `tools/test_cardkey_tool.py`：Base32 / 载荷 / 签名单元测试（8 项）

### Changed
- 代码注释精简为常规工程风格
- CI 按分支独立构建、独立 Release tag

## [1.0.0] - 2026-11-19

### Added
- 首个可用版本：RSA-2048 / SHA-256 卡密签发与客户端验签
- `KVL1-<Base32 payload>-<Base32 sig>` 卡密格式
- OFFLINE / ONLINE 双模式，统一客户端验证逻辑
- 设备绑定、有效期、防篡改持久化
- `tools/cardkey_tool.py`：keygen / issue / check
- GitHub Actions 云端编译并发布 Demo APK
