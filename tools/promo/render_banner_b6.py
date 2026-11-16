"""beta-2 banner: the night sky from the app's new backdrop, pixel clouds, the mascot on one of them."""
import math
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, __file__.rsplit("/", 1)[0])
from common import font  # noqa: E402
from render_banner_b5 import mascot  # noqa: E402

W, H, PX = 1280, 720, 4
GW, GH = W // PX, H // PX
BG = (4, 7, 12)
# the app's NIGHT tones: top light, light, mid, shade, base band, streak
NIGHT = [(0xDD, 0xD5, 0xC7), (0xAB, 0xA0, 0x90), (0x7C, 0x74, 0x66), (0x4F, 0x4A, 0x42), (0x37, 0x34, 0x2F), (0x2A, 0x28, 0x25)]
TEXT, DIM = (230, 232, 235), (139, 145, 153)


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def cloud(p, rnd, cx, base, w, tones):
    """Same shape as CloudUi.paintCloud: lit puffs on a solid body, a flat base running out into tails."""
    core = w * 0.6
    floor = base - 2
    n = max(4, int(core / 9))
    puffs = []
    for i in range(n):
        f = i / (n - 1)
        bell = math.sin(math.pi * (0.12 + 0.76 * f))
        r = core * (0.11 + 0.13 * bell) * (0.85 + rnd.random() * 0.3)
        puffs.append((cx - core / 2 + f * core + rnd.uniform(-1.5, 1.5), floor - r * (0.35 + 0.55 * bell), r))
    top = {}
    for pcx, pcy, r in puffs:
        for x in range(int(pcx - r), int(pcx + r) + 1):
            dx = x + 0.5 - pcx
            if dx * dx > r * r:
                continue
            top[x] = min(top.get(x, 10**9), int(pcy - math.sqrt(r * r - dx * dx)))

    def put(x, y, c):
        if 0 <= x < GW and 0 <= y < GH:
            p[x, y] = c

    for x, t in top.items():
        h = max(1, floor - t)
        for y in range(max(0, t), floor):
            put(x, y, tones[2] if y > floor - h * 0.38 else tones[1])
    for pcx, pcy, r in sorted(puffs, key=lambda q: q[1]):
        for y in range(int(pcy - r) - 1, floor):
            for x in range(int(pcx - r) - 1, int(pcx + r) + 2):
                dx, dy = (x + 0.5 - pcx) / r, (y + 0.5 - pcy) / r
                if dx * dx + dy * dy > 1 or dy > -0.15:
                    continue
                put(x, y, tones[0] if dy < -0.55 and abs(dx) < 0.75 else tones[1])
    xs = sorted(top)
    for _ in range(max(2, n - 1)):
        x = rnd.choice(xs[len(xs) // 8:-len(xs) // 8 or None])
        t = top[x]
        h = floor - t
        if h < 6:
            continue
        r = h * rnd.uniform(0.28, 0.4)
        pcx, pcy = x + 0.5, t + h * rnd.uniform(0.45, 0.6) + r * 0.3
        for y in range(int(pcy - r) - 1, floor):
            for xx in range(int(pcx - r) - 1, int(pcx + r) + 2):
                if xx not in top or y < top[xx] + 1:
                    continue
                dx, dy = (xx + 0.5 - pcx) / r, (y + 0.5 - pcy) / r
                if dx * dx + dy * dy > 1 or dy > -0.2:
                    continue
                put(xx, y, tones[0] if dy < -0.6 and abs(dx) < 0.6 else tones[1])
    l, rr = min(top), max(top)
    tl, tr = int(w * rnd.uniform(0.22, 0.32)), int(w * rnd.uniform(0.28, 0.4))
    for x in range(l - 2, rr + 3):
        put(x, floor, tones[3])
        put(x, floor + 1, tones[3])
    for x in range(l - tl // 2, rr + tr // 2):
        if 0 <= x < GW and floor + 1 < GH and p[x, floor + 1] == BG:
            put(x, floor + 1, tones[4])
    for x in range(l - tl, rr + tr):
        put(x, floor + 2, tones[4] if l - 2 <= x <= rr + 2 else tones[5])
    for _ in range(3):
        y = floor + rnd.choice([-2, 4, 5, 7])
        x0 = int(cx + rnd.uniform(-w * 0.75, w * 0.55))
        for x in range(x0, x0 + rnd.randint(6, 18)):
            if 0 <= x < GW and 0 <= y < GH and p[x, y] == BG:
                p[x, y] = tones[5]
    return top, floor


def moon(p, cx, cy, r):
    """A flat pixel moon, its shadow side dithered."""
    lit, mid = mix(BG, NIGHT[0], 0.85), mix(BG, NIGHT[1], 0.6)
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            if dx * dx + dy * dy > r * r:
                continue
            shade = (dx + r * 0.35) ** 2 + (dy + r * 0.3) ** 2 > (r * 0.95) ** 2
            if shade:
                if (x + y) % 2 == 0:
                    p[x, y] = mid
            else:
                p[x, y] = lit


def main(out):
    sky = Image.new("RGB", (GW, GH), BG)
    p = sky.load()
    rnd = random.Random(11)
    for _ in range(90):
        x, y = rnd.randrange(GW), rnd.randrange(int(GH * 0.75))
        if 20 <= x < 160 and 34 <= y < 104:  # keep the text clear
            continue
        p[x, y] = mix(BG, NIGHT[0], rnd.choice([0.25, 0.4, 0.7]))
    for _ in range(6):  # a few bigger ones, a plus of four cells
        x, y = rnd.randrange(8, GW - 8), rnd.randrange(6, int(GH * 0.5))
        if 8 <= x < 165 and 30 <= y < 108:
            continue
        c = mix(BG, NIGHT[0], 0.8)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            p[x + dx, y + dy] = c
    moon(p, 262, 34, 13)

    far = [mix(BG, t, 0.45) for t in NIGHT]
    near = [mix(BG, t, 0.95) for t in NIGHT]
    cloud(p, random.Random(3), 40, 128, 64, far)
    cloud(p, random.Random(8), 290, 100, 56, far)
    cloud(p, random.Random(5), 92, 156, 58, near)
    top, floor = cloud(p, random.Random(4), 236, 166, 92, near)

    img = sky.resize((W, H), Image.NEAREST)
    d = ImageDraw.Draw(img)

    # the mascot sits on the big cloud
    mw = 220
    mh = round(mw * 428 / 748)
    mx = 236 * PX - mw // 2
    rim = min(top[x] for x in range(236 - 8, 236 + 8))
    mascot(img, mx, (rim + 3) * PX - mh, mw, look_up=0.05)

    d.text((96, 150), "cl0udly", font=font("sans", 112), fill=TEXT)
    d.text((100, 292), "a new look, after dark", font=font("sansm", 34), fill=TEXT)
    d.text((100, 342), "Pixel clouds, floating menus, a calmer input.", font=font("sansm", 24), fill=DIM)
    d.text((100, 378), "And a pile of fixed bugs.", font=font("sansm", 24), fill=DIM)
    d.text((60, 672), "mobile v0.0.2 beta-2", font=font("sansm", 22), fill=DIM)
    d.text((W - 60, 672), "desktop v0.0.1 beta-2", font=font("sansm", 22), fill=DIM, anchor="ra")
    img.save(out, optimize=True)


if __name__ == "__main__":
    main(sys.argv[1])
