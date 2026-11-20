# CardKey 宣传片 · B站发布文案

> 以下数字均来自仓库实际验证记录，未验证的不写。

## 标题（30 字内，二选一）
1. 60秒看懂：Android 卡密激活 SDK，开源、双模式、带演示后端
2. 给 App 加卡密授权很难？这个开源 SDK 把验签/设备绑定/防篡改都做好了

## 简介
CardKey 是一个纯客户端的 Android 卡密（兑换码）激活 SDK，面向普通 App 与 Xposed 模块。

它能做什么：
- RSA-2048 签名验卡，卡密格式 KVL1-<Base32 载荷>-<Base32 签名>
- 离线 / 在线双模式：离线卡密即凭证本地验签；在线走你自己的服务器
- 设备绑定（ANDROID_ID 哈希）、有效期、防篡改持久化（改 SharedPreferences 无效）
- 零第三方运行时依赖，仅用 Android 系统 API

仓库有三个分支：
- normal-android-app：独立安卓应用（默认分支）
- xposed-injection-module：Xposed 注入式模块
- demo-backend：可直接运行的 Python 演示后端（发卡/验卡/封禁）

视频内容：功能介绍 + 真机实拍激活演示（未激活→输入卡密→激活成功→激活详情）。

开源地址：github.com/wanwu2026/CardKeySDK（MIT License）
说明：本项目由 AI 生成；视频中的实机画面为真机实拍，文字画面为动态图形演示。

## 标签
Android  开源  卡密  授权验证  Xposed  SDK  开发者工具  AI生成

## 分区建议
科技 → 软件应用 / 或 知识 → 编程开发

## 发布检查清单
- [ ] 封面用 cover-bilibili.jpg（1920×1200，16:10）
- [ ] 视频文件 CardKey-Promo.mp4（1920×1080 / 30fps / 约64秒 / 19MB）
- [ ] 简介里的仓库地址可点开
- [ ] 勾选"自制"或按平台要求标注 AI 生成
