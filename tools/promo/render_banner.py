"""Telegram banner for beta-1, 1280x720.

    python3 render_banner.py <clean screenshots dir> <out.png>
"""
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import BG, BLUE, DIM, LINE, PANEL, PEACH, TEXT, font, phone_image, text, wordmark, wordmark_height  # noqa: E402

W, H = 1280, 720


def main(shots, out):
    c = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(c)
    # fine dot grid as texture
    for x in range(40, W, 40):
        for y in range(40, H, 40):
            d.rectangle([x, y, x + 1, y + 1], fill=(28, 28, 28))

    wm_w = 560
    wordmark(c, 70, 96, wm_w)
    y = 96 + wordmark_height(wm_w) + 34
    text(c, (72, y), "mobile · v0.0.1 beta-1", "monob", 36, PEACH)
    text(c, (72, y + 84), "Your own OpenCode server", "sans", 48, TEXT)
    text(c, (72, y + 144), "right in your phone", "sans", 48, BLUE)

    # the three looks as chips
    x = 72
    cy = y + 250
    for label, r in (("Claude", 26), ("Terminal", 0), ("Desktop", 9)):
        f = font("mono", 24)
        wd = d.textlength(label, font=f)
        d.rounded_rectangle([x, cy, x + wd + 36, cy + 52], radius=r, fill=PANEL, outline=LINE, width=2)
        d.text((x + 18, cy + 26), label, font=f, fill=TEXT, anchor="lm")
        x += wd + 36 + 14
    text(c, (72, H - 54), "github.com/mistqkw/opencode-mobile", "mono", 24, DIM)

    names = ["claude-home", "terminal-home", "desktop-home"]
    specs = [(-9, 700, 170), (0, 830, 130), (9, 960, 190)]  # angle, x, y
    for name, (ang, px, py) in zip(names, specs):
        ph = phone_image(Image.open(os.path.join(shots, name + ".png")), 276)
        ph = ph.rotate(ang, resample=Image.BICUBIC, expand=True)
        c.paste(ph, (px - 20, py + (0 if ang == 0 else 30) - 40), ph)
    c.save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
