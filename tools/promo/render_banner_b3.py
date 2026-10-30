"""Баннер для Telegram к v0.0.1 beta-3, 1280x720.

    python3 render_banner_b3.py <папка с чистыми скриншотами> <выход.png>
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import BG, BLUE, DIM, GREEN, LINE, PANEL, PEACH, TEXT, font, phone_image, text, wordmark, wordmark_height  # noqa: E402

W, H = 1280, 720


def main(shots, out):
    c = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(c)
    for x in range(40, W, 40):
        for y in range(40, H, 40):
            d.rectangle([x, y, x + 1, y + 1], fill=(28, 28, 28))

    wm_w = 520
    wordmark(c, 70, 84, wm_w)
    y = 84 + wordmark_height(wm_w) + 30
    text(c, (72, y), "mobile · v0.0.1 beta-3", "monob", 34, PEACH)
    text(c, (72, y + 78), "Skills, GitHub", "sans", 50, TEXT)
    text(c, (72, y + 140), "and your phone", "sans", 50, BLUE)

    x, cy = 72, y + 240
    for label, dot in (("/skills", BLUE), ("git push", GREEN), ("usage", PEACH)):
        f = font("mono", 24)
        wd = d.textlength(label, font=f)
        d.rounded_rectangle([x, cy, x + wd + 62, cy + 52], radius=26, fill=PANEL, outline=LINE, width=2)
        d.ellipse([x + 18, cy + 20, x + 30, cy + 32], fill=dot)
        d.text((x + 42, cy + 26), label, font=f, fill=TEXT, anchor="lm")
        x += wd + 62 + 12
    text(c, (72, H - 54), "github.com/mistqkw/opencode-mobile", "mono", 24, DIM)

    names = ["skills", "slash", "github-code"]
    specs = [(-9, 690, 170), (0, 830, 120), (9, 975, 190)]
    for name, (ang, px, py) in zip(names, specs):
        ph = phone_image(Image.open(os.path.join(shots, name + ".png")), 270)
        ph = ph.rotate(ang, resample=Image.BICUBIC, expand=True)
        c.paste(ph, (px - 20, py + (0 if ang == 0 else 30) - 40), ph)
    c.save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
