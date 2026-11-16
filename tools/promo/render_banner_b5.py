"""Telegram banner for desktop 0.0.1 beta-1 + mobile 0.0.2 beta-1, 1280x720: the same paper and
handwritten "cl0udly" as beta-4, and what's new: the mascot itself, big,
coming up from the bottom edge and looking up at a few of its pixel reactions in the same ink.

    python3 render_banner_b5.py <out.png>
"""
import math
import sys
import os

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import font  # noqa: E402
from handwriting import INK, Word, _catmull, _noise, paper  # noqa: E402

W, H = 1280, 720
TEXT = (58, 56, 52)
DIM = (120, 116, 108)


def ink_line(img, pts, width, seed, head_at=(), wobble=3.0):
    """A pen line through the points with a little wobble; arrow heads at the given ends (0 or -1)."""
    ss = 2
    layer = Image.new("L", (img.width * ss, img.height * ss), 0)
    d = ImageDraw.Draw(layer)
    wob = [(x + _noise(seed, i) * wobble, y + _noise(seed, i + 50) * wobble) for i, (x, y) in enumerate(pts)]
    line = _catmull(wob, per=30)

    def stroke(poly):
        for (x0, y0), (x1, y1) in zip(poly, poly[1:]):
            d.line([(x0 * ss, y0 * ss), (x1 * ss, y1 * ss)], fill=255, width=int(width * ss))
        for x, y in poly:
            r = width * ss / 2
            d.ellipse([x * ss - r, y * ss - r, x * ss + r, y * ss + r], fill=255)

    stroke(line)
    for end in head_at:
        tip = line[end]
        back = line[end - 12 if end == -1 else 12]
        a = math.atan2(tip[1] - back[1], tip[0] - back[0])
        for k, s in ((1, 1), (2, -1)):
            b = a + math.pi - s * 0.5 + _noise(seed, 90 + k) * 0.12
            stroke([tip, (tip[0] + 22 * math.cos(b), tip[1] + 22 * math.sin(b))])
    layer = layer.resize(img.size, Image.LANCZOS)
    img.paste(Image.new("RGB", img.size, INK), (0, 0), layer.point(lambda v: int(v * 0.92)))


# the mascot as the app draws it (CloudUi.drawIconCloud), happy: eyes as "^"
CW, CH = 748, 428
SCREEN = (12, 12, 13)


OUTLINE = (24, 24, 27)
HEART = [".x.x.", "xxxxx", "xxxxx", ".xxx.", "..x.."]
STAR = [".x.", "xxx", ".x."]


def mascot(img, x, y, w, eye=OUTLINE, look_up=0.0):
    """On a light background, with the dark outline the app gives it there."""
    d = ImageDraw.Draw(img)
    k = w / CW

    def r(x0, y0, x1, y1, col):
        d.rectangle([x + round(x0 * k), y + round(y0 * k), x + round(x1 * k) - 1, y + round(y1 * k) - 1], fill=col)

    white, grey = (255, 255, 255), (186, 186, 188)
    o = 22
    r(-o, 113 - o, CW + o, CH + o, OUTLINE)
    r(152 - o, -o, 635 + o, 113, OUTLINE)
    r(0, 113, CW, CH, white)
    r(152, 0, 565, 114, white)
    r(565, 0, 635, 113, grey)
    r(635, 113, 704, 193, grey)
    r(0, 213, 62, CH, grey)
    r(62, 333, 127, CH, grey)
    # plain open eyes, raised by look_up (share of width) as when it looks up at something
    ew, eh, top = 68, 198, 110 - look_up * CW
    for ex in (245, 430):
        r(ex, top, ex + ew, top + eh, eye)


def sprite(img, x, y, rows, px, col):
    d = ImageDraw.Draw(img)
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "x":
                d.rectangle([x + i * px, y + j * px, x + (i + 1) * px - 1, y + (j + 1) * px - 1], fill=col)


def centered(d, y, text, f, fill):
    d.text((W / 2, y), text, font=f, fill=fill, anchor="ma")


def main(out):
    c = paper(W, H, seed=5)
    d = ImageDraw.Draw(c)

    word = Word(unit=62, seed=5)
    ink = word.render(99)
    c.paste(ink, ((W - ink.width) // 2, 138 - word.baseline), ink)
    centered(d, 196, "now on your computer too", font("sansm", 34), TEXT)
    centered(d, 246, "Linux, Windows and macOS. Chats and memory move between them and the phone.", font("sansm", 22), DIM)

    # the mascot, big, coming up from the bottom edge and looking up at its reactions
    mw = 600
    mascot(c, (W - mw) // 2, 400, mw, look_up=0.09)
    sprite(c, 250, 360, STAR, 12, INK)
    sprite(c, 200, 480, STAR, 8, INK)
    sprite(c, 950, 320, HEART, 15, INK)
    sprite(c, 1070, 450, STAR, 9, INK)
    d.text((60, 672), "desktop v0.0.1 beta-1", font=font("sansm", 22), fill=DIM)
    d.text((W - 60, 672), "mobile v0.0.2 beta-1", font=font("sansm", 22), fill=DIM, anchor="ra")
    c.save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1])
