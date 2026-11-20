# CardKey 宣传片制作工程

成片：`build/CardKey-Promo.mp4`（1920×1080 / 30fps / H.264 / 约 64 秒，无配音）。
分镜见 [STORYBOARD.md](STORYBOARD.md)。

## 复现

```bash
# 0) 字体：中文字体 wqy-microhei（GPLv2，故不入库），下载后放到 assets/font.ttc
#    apt-get download fonts-wqy-microhei && dpkg-deb -x *.deb /tmp/f && cp /tmp/f/.../wqy-microhei.ttc assets/

# 1) 生成 8 个镜头的 HTML
python3 gen_scenes.py

# 2) Chrome headless 渲染 PNG（需自定义字体配置）
#    FONTCONFIG_FILE 指向含 <dir>assets</dir> 的 fonts.conf
for i in 01 02 03 04 05 06 07 08; do
  FONTCONFIG_FILE=/path/to/fonts.conf chromium-browser --headless --no-sandbox \
    --screenshot=frames/scene_$i.png --window-size=1920,1080 --hide-scrollbars \
    "file://$(pwd)/scene_$i.html"
done

# 3) ffmpeg 运镜 + xfade 转场合成
python3 build_video.py
```

## 工具链说明
- 本机 `npx hyperframes` 因网络受限拉不到 CLI，改用 Chrome headless 截图 + ffmpeg 合成
- ffmpeg 是精简构建，无 libx264，用 `libopenh264` 编码
- 中文渲染依赖文泉驿字体（GPLv2，不随仓库分发）；画面中的实拍截图来自 `docs/screenshots/`

## 素材口径
- 实拍画面：仓库 4 张真机截图（scene 5/6）
- 标题/文字画面：动态图形演示，**非实机界面**
