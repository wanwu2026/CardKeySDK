#!/usr/bin/env python3
"""生成 8 个镜头的 HTML（1920x1080），供 Chrome headless 截图。中文字体走 @font-face。"""
import os

FONT = "assets/font.ttc"
CSS_BASE = """
@font-face { font-family: WQY; src: url('__FONT__') format('truetype'); }
* { box-sizing: border-box; }
html,body { margin:0; width:1920px; height:1080px; overflow:hidden;
  font-family: WQY, sans-serif; color:#e8eef7;
  background: radial-gradient(circle at 30% 20%, #1b2a4a 0%, #0b1020 55%, #060911 100%); }
.grid { position:absolute; inset:0; opacity:.12;
  background-image: linear-gradient(#4a7bd0 1px,transparent 1px),linear-gradient(90deg,#4a7bd0 1px,transparent 1px);
  background-size:60px 60px; }
.glow { position:absolute; width:900px; height:900px; border-radius:50%; filter:blur(120px); opacity:.28; }
.tag { position:absolute; top:70px; left:100px; font-size:30px; letter-spacing:8px; color:#7fa8ff; }
.wrap { position:relative; padding:120px 120px; height:100%; display:flex; flex-direction:column; justify-content:center; }
h1 { font-size:118px; margin:0 0 24px; letter-spacing:4px;
  background:linear-gradient(90deg,#fff,#7fa8ff); -webkit-background-clip:text; color:transparent; }
h2 { font-size:74px; margin:0 0 40px; color:#fff; }
p { font-size:40px; color:#a9bdd8; margin:10px 0; line-height:1.5; }
.accent { color:#5ee0a0; }
.card { background:rgba(30,48,80,.55); border:1px solid rgba(127,168,255,.3); border-radius:24px;
  padding:42px 46px; backdrop-filter:blur(8px); }
.card h3 { font-size:46px; margin:0 0 16px; color:#fff; }
.card p { font-size:32px; }
.row { display:flex; gap:40px; }
.col { flex:1; }
.footer { position:absolute; bottom:60px; left:120px; font-size:28px; color:#5d7391; }
""".replace("__FONT__", FONT)

def scene(body):
    return f"<!DOCTYPE html><html><head><meta charset='utf-8'><style>{CSS_BASE}</style></head><body><div class='grid'></div>{body}</body></html>"

scenes = {}

# 1 开场
scenes['01'] = scene("""
<div class='glow' style='background:#3b6fe0; top:-200px; left:900px;'></div>
<div class='wrap'>
  <p class='tag'>OPEN SOURCE · 2026</p>
  <h1>CardKey</h1>
  <h2>Android 卡密激活 SDK</h2>
  <p>纯客户端 · 验签 / 设备绑定 / 防篡改存储</p>
  <p class='accent'>MIT License · GitHub 开源</p>
</div><div class='footer'>CardKey · 让卡密接入变简单</div>""")

# 2 痛点
scenes['02'] = scene("""
<div class='wrap'><h2>卡密系统，难在哪？</h2>
<div class='row'>
  <div class='card col'><h3>① 私钥安全</h3><p>私钥绝不能进 App<br>否则人人可签卡</p></div>
  <div class='card col'><h3>② 设备绑定</h3><p>一机一码<br>防止卡密被分享</p></div>
  <div class='card col'><h3>③ 防篡改</h3><p>本地状态可被修改<br>需重验签 + 校验</p></div>
</div></div><div class='footer'>CardKey · 直面卡密系统的核心难题</div>""")

# 3 方案
scenes['03'] = scene("""
<div class='wrap'><h2>答案：客户端 SDK + 你的后端</h2>
<div class='row'>
  <div class='card col'><h3>客户端 SDK（本项目）</h3>
    <p>激活界面 + 验签逻辑<br>设备码 / 有效期 / 防篡改<br>零第三方依赖</p></div>
  <div class='card col'><h3>后端（开发者自备）</h3>
    <p>保管私钥 · 发卡验卡<br>一机一码 · 实时封禁<br>仓库提供接口约定与演示后端</p></div>
</div></div><div class='footer'>CardKey · 安全边界在后端</div>""")

# 4 核心能力
scenes['04'] = scene("""
<div class='wrap'><h2>四件事，做到位</h2>
<div class='row' style='flex-wrap:wrap'>
  <div class='card' style='width:46%'><h3>RSA-2048 签名</h3><p>SHA256withRSA · KVL1 凭证格式</p></div>
  <div class='card' style='width:46%'><h3>双模式</h3><p>离线 / 在线 · 统一验证逻辑</p></div>
  <div class='card' style='width:46%'><h3>防篡改持久化</h3><p>存凭证串 · 每次读取重验签</p></div>
  <div class='card' style='width:46%'><h3>设备绑定</h3><p>ANDROID_ID 哈希 · 换机失效</p></div>
</div></div><div class='footer'>CardKey · 核心能力一览</div>""")

# 5 实拍演示①（三张截图并排）
scenes['05'] = scene("""
<div class='wrap'><h2>三步完成激活</h2>
<div class='row' style='align-items:flex-start'>
  <div class='card col' style='text-align:center'><h3>① 未激活</h3>
    <img src='assets/01-inactive.jpg' style='width:100%;height:520px;object-fit:cover;object-position:top;border-radius:14px;'></div>
  <div class='card col' style='text-align:center'><h3>② 输入卡密</h3>
    <img src='assets/03-activate-dialog.jpg' style='width:100%;height:520px;object-fit:cover;object-position:top;border-radius:14px;'></div>
  <div class='card col' style='text-align:center'><h3>③ 激活成功</h3>
    <img src='assets/02-activated.jpg' style='width:100%;height:520px;object-fit:cover;object-position:top;border-radius:14px;'></div>
</div></div><div class='footer'>真机实拍 · 演示卡密见仓库 demo/demo_license.txt</div>""")

# 6 实拍演示②（详情大图）
scenes['06'] = scene("""
<div class='wrap' style='flex-direction:row;align-items:center;gap:80px'>
  <div style='flex:1'><h2>激活状态</h2>
    <p>一目了然</p><p class='accent'>✓ 已激活</p>
    <p>设备码 · 产品 · 到期 · 序列号<br>全部本地可查，篡改即失效</p></div>
  <div style='flex:0 0 620px'>
    <img src='assets/04-activated-detail.jpg' style='width:620px;height:880px;object-fit:cover;object-position:top;border-radius:28px;border:1px solid rgba(127,168,255,.3);'></div>
</div></div><div class='footer'>真机实拍 · 激活详情界面</div>""")

# 7 三分支
scenes['07'] = scene("""
<div class='wrap'><h2>三个版本，按需取用</h2>
<div class='row'>
  <div class='card col'><h3>普通安卓应用</h3><p>独立 App<br>开箱即用的激活 Demo</p></div>
  <div class='card col'><h3>XP 注入模块</h3><p>Xposed 注入式<br>hook 目标 App 做授权</p></div>
  <div class='card col'><h3>演示后端</h3><p>可运行的服务<br>发卡 / 验卡 / 封禁</p></div>
</div></div><div class='footer'>一个仓库 · 三个分支 · 中英双语文档</div>""")

# 8 结尾
scenes['08'] = scene("""
<div class='glow' style='background:#3b6fe0; top:300px; left:700px;'></div>
<div class='wrap' style='text-align:center;align-items:center'>
  <h1 style='font-size:96px'>开始使用 CardKey</h1>
  <p style='font-size:46px;margin-top:30px'>github.com/wanwu2026/CardKeySDK</p>
  <div class='card' style='margin-top:50px'><p class='accent' style='font-size:38px'>Star · Fork · 二次开发</p>
  <p style='font-size:30px'>MIT License · 由 AI 生成</p></div>
</div><div class='footer' style='width:100%;text-align:center'>CardKey · 感谢观看</div>""")

for k, v in scenes.items():
    with open(f"scene_{k}.html", "w", encoding="utf-8") as f:
        f.write(v)
    print(f"生成 scene_{k}.html ({len(v)} bytes)")
print("done")
