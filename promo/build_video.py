#!/usr/bin/env python3
"""把 8 张画面用 ffmpeg 合成宣传片：zoompan 运镜 + xfade 转场。"""
import subprocess
import os

HERE = os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
FF = "ffmpeg"
DUR = 9          # 每段 9 秒
TRANS = 1.2      # 转场 1.2 秒
FPS = 30
FRAMES = DUR * FPS

# 每段运镜：zoom 起点/终点 + 平移方向
# zfrom->zto, x/y 用表达式（iw/2 居中，或轻微平移）
MOVES = [
    ("min(zoom+0.0006,1.10)", "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),  # 1 开场缓放
    ("1.06", "(iw-iw/zoom)/2 + 40*sin(on/60)", "ih/2-(ih/zoom/2)"),        # 2 痛点轻移
    ("min(zoom+0.0005,1.08)", "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),  # 3 方案缓放
    ("1.05", "iw/2-(iw/zoom/2) + 30*sin(on/90)", "ih/2-(ih/zoom/2)"),     # 4 能力轻移
    ("min(zoom+0.0007,1.10)", "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),  # 5 实拍缓放
    ("1.06", "iw/2-(iw/zoom/2) - 30*sin(on/90)", "ih/2-(ih/zoom/2)"),     # 6 详情轻移
    ("min(zoom+0.0006,1.09)", "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),  # 7 分支缓放
    ("min(zoom+0.0004,1.06)", "iw/2-(iw/zoom/2)", "ih/2-(ih/zoom/2)"),  # 8 结尾缓放
]

def run(cmd, desc):
    print(">>", desc)
    r = subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True)
    if r.returncode != 0:
        print(r.stderr[-1500:])
        raise SystemExit(f"失败: {desc}")

# 1) 每段生成 mp4
segs = []
for i, (z, x, y) in enumerate(MOVES, 1):
    out = f"build/seg{i}.mp4"
    vf = (f"scale=3840:2160,zoompan=z='{z}':x='{x}':y='{y}'"
          f":d={FRAMES}:s=1920x1080:fps={FPS},format=yuv420p")
    run([FF, "-y", "-loop", "1", "-i", f"frames/scene_{i:02d}.png",
         "-t", str(DUR), "-vf", vf,
         "-c:v", "libopenh264", "-b:v", "4M", "-preset", "medium", out],
        f"运镜 scene {i}")
    segs.append(out)

# 2) xfade 串接
# 累积时间轴：第 n 段起点 = n*(DUR-TRANS)
inputs = []
cmd = [FF, "-y"]
for s in segs:
    cmd += ["-i", s]
filters = []
prev = "0:v"
offset = DUR - TRANS
for i in range(1, len(segs)):
    out = f"v{i}"
    filters.append(f"[{prev}][{i}:v]xfade=transition=fade:duration={TRANS}:offset={offset * i:.3f}[{out}]")
    prev = out
filter_str = ";".join(filters)
cmd += ["-filter_complex", filter_str, "-map", f"[{prev}]",
        "-c:v", "libopenh264", "-b:v", "5M", "-preset", "medium",
        "-pix_fmt", "yuv420p", "-movflags", "+faststart",
        "build/CardKey-Promo.mp4"]
run(cmd, "xfade 串接成片")
print("✅ 成片: build/CardKey-Promo.mp4")
