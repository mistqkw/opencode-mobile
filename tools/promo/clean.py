"""Готовит скриншоты для README и ролика: убирает строку состояния (время, уведомления) и ставит чистую.

    python3 clean.py <папка с сырыми скриншотами> <куда сложить>

Сырые — PNG 1080x2424 с телефона (adb exec-out screencap -p). Результат — 720 px по ширине.
"""
import os
import sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import font  # noqa: E402

BAR = 104  # высота строки состояния в пикселях исходника


def clean(src, dst):
    im = Image.open(src).convert("RGB")
    w, h = im.size
    d = ImageDraw.Draw(im)
    bg = im.getpixel((w // 2, BAR + 6))
    d.rectangle([0, 0, w, BAR], fill=bg)
    lum = sum(bg) / 3
    fg = (240, 240, 240) if lum < 128 else (30, 30, 30)
    d.text((78, BAR // 2 + 4), "9:41", font=font("sans", 44), fill=fg, anchor="lm")
    # батарея справа
    bx, by = w - 150, BAR // 2 - 14
    d.rounded_rectangle([bx, by, bx + 60, by + 28], radius=7, outline=fg, width=3)
    d.rectangle([bx + 6, by + 6, bx + 46, by + 22], fill=fg)
    d.rectangle([bx + 62, by + 8, bx + 66, by + 20], fill=fg)
    # сеть: три полоски
    for i in range(3):
        d.rectangle([bx - 70 + i * 16, by + 24 - i * 8 - 4, bx - 70 + i * 16 + 10, by + 24], fill=fg)
    nw = 720
    im = im.resize((nw, round(h * nw / w)), Image.LANCZOS)
    im.save(dst, optimize=True)


if __name__ == "__main__":
    srcdir, dstdir = sys.argv[1], sys.argv[2]
    os.makedirs(dstdir, exist_ok=True)
    for name in sorted(os.listdir(srcdir)):
        if name.endswith(".png"):
            clean(os.path.join(srcdir, name), os.path.join(dstdir, name))
            print("ok", name)
