#!/usr/bin/env python3
"""
从一张源图生成 Android 全套启动器图标。

用法：
    python3 ui/tools/make_icons.py [源图路径]

默认读 ui/icon_source.png，写到 app/app/src/main/res/。

生成什么：
    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png          旧版方形图标
    mipmap-{...}/ic_launcher_round.png                                旧版圆形图标
    mipmap-{...}/ic_launcher_foreground.png                           自适应图标前景（透明底）
    mipmap-anydpi-v26/ic_launcher.xml (+_round)                       自适应图标描述
    values/ic_launcher_background.xml                                 背景色

设计要点（踩过的坑）：

1. 背景色从源图左上角取，不写死。以后换图不用改代码。

2. 自适应图标的前景层是 108dp 画布，但安全区只有中间 66.7%
   （72/108）—— 启动器会按自己的形状裁（圆形 / 圆角方 / 水滴形…），
   超出安全区的部分会被切掉。所以水滴目标高度取画布的 52%，
   留出余量。

3. **缩放要按水滴自身的高度算，不是按源图高度。**
   源图里水滴周围有一圈背景留白，水滴只占源图高的 60% 左右。
   如果直接 `target = 画布*0.52` 然后按源图缩，水滴实际只有画布的 31%
   —— 看起来小得可怜。这个 bug 第一版就踩了。
   正确做法：先抠图、取 alpha 包围盒裁到水滴本身，再按水滴高度算缩放。

4. 需要 Pillow。系统 Python 有 PEP 668 保护，用 venv：
       python3 -m venv /tmp/imgenv && /tmp/imgenv/bin/pip install Pillow
       /tmp/imgenv/bin/python ui/tools/make_icons.py
"""

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SRC = sys.argv[1] if len(sys.argv) > 1 else 'ui/icon_source.png'
RES = 'app/app/src/main/res'

# 抠图容差：离背景色多远算「不是背景」。
# 26 是试出来的 —— 再小会把抗锯齿边缘当主体（一圈脏边），
# 再大会把黑轮廓吃掉一点。
TOL = 26

# 水滴在自适应图标画布里占多高。安全区是 66.7%，留到 52% 是给
# 各种形状的裁切留余量，也让图标四周有呼吸感。
DROP_RATIO = 0.52

# 各密度：名字 → 旧版图标边长（dp 值 ×1）
DENS = [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]


def cut_out(im):
    """把主体从纯色背景里抠出来，返回 (抠好的图, 主体包围盒)。"""
    W, H = im.size
    px = im.load()
    bg = px[10, 10]

    out = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    op = out.load()
    for y in range(H):
        for x in range(W):
            r, g, b = px[x, y]
            d = math.sqrt((r - bg[0]) ** 2 + (g - bg[1]) ** 2 + (b - bg[2]) ** 2)
            if d > TOL:
                # 边缘做一点羽化，不然缩小后是一圈硬齿
                a = 255 if d > TOL * 1.6 else int(255 * (d - TOL) / (TOL * 0.6))
                op[x, y] = (r, g, b, max(0, min(255, a)))

    # 轻微模糊把羽化抹匀
    out = out.filter(ImageFilter.GaussianBlur(0.6))
    return out, out.getchannel('A').getbbox()


def main():
    im = Image.open(SRC).convert('RGB')
    W, H = im.size
    bg = im.load()[10, 10]
    bg_hex = '#%02X%02X%02X' % bg
    print(f'源图 {SRC}  {W}×{H}   背景色 {bg_hex}')

    fg, bbox = cut_out(im)
    if bbox is None:
        sys.exit('抠不出主体 —— 是不是整张图都跟背景色一样？')
    bx0, by0, bx1, by1 = bbox
    dw, dh = bx1 - bx0, by1 - by0
    print(f'主体 {dw}×{dh}   占源图高 {dh / H * 100:.1f}%')
    drop = fg.crop(bbox)          # 裁到主体本身，后面按它算缩放

    for name, sz in DENS:
        d = f'{RES}/mipmap-{name}'
        os.makedirs(d, exist_ok=True)

        # 旧版方形：整张源图缩（保留原图的留白，视觉上跟系统图标一致）
        im.resize((sz, sz), Image.LANCZOS).save(f'{d}/ic_launcher.png')

        # 旧版圆形
        circ = im.resize((sz, sz), Image.LANCZOS).convert('RGBA')
        mask = Image.new('L', (sz, sz), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, sz - 1, sz - 1), fill=255)
        circ.putalpha(mask)
        circ.save(f'{d}/ic_launcher_round.png')

        # 自适应前景：108dp 画布 = 1.5 × 图标边长
        fsz = int(sz * 1.5)
        canvas = Image.new('RGBA', (fsz, fsz), (0, 0, 0, 0))
        target_h = int(fsz * DROP_RATIO)
        scale = target_h / dh                    # ← 按主体自身高度算（见文件头第 3 条）
        nd = drop.resize((max(1, round(dw * scale)), target_h), Image.LANCZOS)
        canvas.paste(nd, ((fsz - nd.width) // 2, (fsz - nd.height) // 2), nd)
        canvas.save(f'{d}/ic_launcher_foreground.png')

    # 自适应图标描述
    anydpi = f'{RES}/mipmap-anydpi-v26'
    os.makedirs(anydpi, exist_ok=True)
    xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
    <!-- monochrome 给 Android 13+ 的「主题图标」用：
         系统会把它涂成跟壁纸协调的单色。同一个前景够用，
         因为主体本来就是单色的。 -->
    <monochrome android:drawable="@mipmap/ic_launcher_foreground"/>
</adaptive-icon>
'''
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        open(f'{anydpi}/{n}', 'w').write(xml)

    # 背景色资源
    vals = f'{RES}/values'
    os.makedirs(vals, exist_ok=True)
    open(f'{vals}/ic_launcher_background.xml', 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
        f'    <color name="ic_launcher_background">{bg_hex}</color>\n</resources>\n')

    print(f'写好 {len(DENS)} 组密度 + anydpi-v26 + 背景色资源')
    print('别忘了 AndroidManifest 里要有 android:icon / android:roundIcon')


if __name__ == '__main__':
    main()
