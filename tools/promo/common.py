"""Общее для ролика и баннера: шрифты, плавность, пиксельная надпись, макет телефона.

Только Pillow. Шрифты берутся из системы; пути можно переопределить переменными
PROMO_SANS, PROMO_SANS_MEDIUM, PROMO_MONO, PROMO_SERIF.
"""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFont

BG = (10, 10, 10)
TEXT = (238, 238, 238)
DIM = (128, 128, 128)
BLUE = (92, 156, 245)
PEACH = (250, 178, 131)
GREEN = (127, 216, 143)
PANEL = (20, 20, 20)
LINE = (60, 60, 60)

_FONT_PATHS = {
    "sans": [os.environ.get("PROMO_SANS", ""), os.path.expanduser("~/.local/share/fonts/texfi/Inter-SemiBold.ttf")],
    "sansm": [os.environ.get("PROMO_SANS_MEDIUM", ""), os.path.expanduser("~/.local/share/fonts/texfi/Inter-Medium.ttf")],
    "mono": [os.environ.get("PROMO_MONO", ""), "/usr/share/fonts/TTF/JetBrainsMonoNerdFontMono-Regular.ttf", "/usr/share/fonts/TTF/DejaVuSansMono.ttf"],
    "monob": [os.environ.get("PROMO_MONO_BOLD", ""), "/usr/share/fonts/TTF/JetBrainsMonoNerdFontMono-Bold.ttf", "/usr/share/fonts/TTF/DejaVuSansMono-Bold.ttf"],
    "serif": [os.environ.get("PROMO_SERIF", ""), "/usr/share/fonts/TTF/DejaVuSerif-Bold.ttf"],
}
_cache = {}


def font(kind, size):
    key = (kind, size)
    if key not in _cache:
        for p in _FONT_PATHS[kind]:
            if p and os.path.exists(p):
                _cache[key] = ImageFont.truetype(p, size)
                break
        else:
            _cache[key] = ImageFont.load_default()
    return _cache[key]


# ---------- плавность ----------
def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def out_cubic(t):
    t = clamp(t)
    return 1 - (1 - t) ** 3


def in_out_cubic(t):
    t = clamp(t)
    return 4 * t * t * t if t < 0.5 else 1 - (-2 * t + 2) ** 3 / 2


def out_back(t, s=1.5):
    t = clamp(t)
    return 1 + (s + 1) * (t - 1) ** 3 + s * (t - 1) ** 2


def lerp(a, b, t):
    return a + (b - a) * t


def mix(c1, c2, t):
    return tuple(int(lerp(a, b, t)) for a, b in zip(c1, c2))


# ---------- пиксельная надпись (та же сетка, что в приложении) ----------
GLYPHS = {
    "o": ["....", "XXXX", "X..X", "X..X", "X..X", "XXXX", "....", "...."],
    "p": ["....", "XXXX", "X..X", "X..X", "X..X", "XXXX", "X...", "X..."],
    "e": ["....", "XXXX", "X..X", "XXXX", "X...", "XXXX", "....", "...."],
    "n": ["....", "XXX.", "X..X", "X..X", "X..X", "X..X", "....", "...."],
    "c": ["....", "XXXX", "X...", "X...", "X...", "XXXX", "....", "...."],
    "d": ["...X", "...X", "XXXX", "X..X", "X..X", "XXXX", "....", "...."],
}
WORD = "opencode"
COLS = len(WORD) * 4 + (len(WORD) - 1)
ROWS = 8


def _h(a, b):
    h = (a * 374761393 + b * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def wordmark(img, x, y, width, enter=1.0, effect=None, p=1.0, bg=BG, dim=None, bright=None, sweep=None):
    """Рисует «opencode» клетками. enter 0..1 — сборка, effect: wave/scan/scatter/glitch/build + прогресс p."""
    d = ImageDraw.Draw(img)
    cell = width / COLS
    dim = dim or (150, 150, 150)
    bright = bright or (245, 245, 240)
    for li, ch in enumerate(WORD):
        g = GLYPHS[ch]
        x0 = li * 5
        tone = dim if li < 4 else bright
        for r in range(ROWS):
            for k in range(4):
                if g[r][k] != "X":
                    continue
                col = x0 + k
                start = (col / COLS) * 0.55 + (r / ROWS) * 0.2
                local = clamp((enter - start) / 0.3)
                if local <= 0:
                    continue
                s = 1.0 if local >= 1 else clamp(local * local * (2.7 * local - 1.7), 0, 1.15)
                color = mix(tone, bg, 0.45) if r >= 4 else tone
                if sweep is not None:
                    dd = abs(col / COLS - sweep)
                    if dd < 0.07:
                        color = mix(color, bright, (1 - dd / 0.07) * 0.55)
                side = (cell + 0.8) * s
                ox = oy = 0.0
                if effect and p < 1:
                    if effect == "wave":
                        ph = clamp(p * 1.7 - li * 0.1)
                        oy = -math.sin(ph * math.pi) * cell * 2.4
                    elif effect == "scan":
                        fx = col / COLS
                        xx = p * 1.4 - 0.2
                        sw = (bright if li < 4 else dim) if fx < xx else tone
                        color = mix(sw, bg, 0.45) if r >= 4 else sw
                        if abs(fx - xx) < 0.05:
                            color = BLUE
                    elif effect == "scatter":
                        h, h2 = _h(col, r), _h(r, col + 99)
                        out = 1 - (1 - p / 0.35) ** 2 if p < 0.35 else (lambda q: 1 - q * q * (3 - 2 * q))((p - 0.35) / 0.65)
                        a = h * 2 * math.pi
                        ox = math.cos(a) * out * cell * (5 + 12 * h2)
                        oy = math.sin(a) * out * cell * (5 + 12 * h2)
                        side *= 1 - out * 0.55
                    elif effect == "glitch":
                        step = int(p * 14)
                        if p < 0.85 and _h(r, step) > 0.5:
                            ox = (_h(step, r + 7) - 0.5) * cell * 10
                            color = PEACH
                    elif effect == "build":
                        need = (ROWS - 1 - r) / ROWS * 0.75 + _h(col, r) * 0.15
                        q = clamp((p - need) / 0.25)
                        if q <= 0:
                            continue
                        oy = (1 - q) * cell * 3
                        side *= 0.4 + 0.6 * q
                        if q < 1:
                            color = TEXT
                cx = x + col * cell + (cell - side) / 2 + ox
                cy = y + r * cell + (cell - side) / 2 + oy
                d.rectangle([cx, cy, cx + side, cy + side], fill=color)


def wordmark_height(width):
    return width / COLS * ROWS


# ---------- телефон ----------
def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def phone_image(screen, width):
    """Скриншот в плоской рамке телефона. Возвращает RGBA."""
    rim = max(6, width // 60)
    sw = width - 2 * rim
    sh = int(sw * screen.height / screen.width)
    scr = screen.convert("RGB").resize((sw, sh), Image.LANCZOS)
    out = Image.new("RGBA", (width, sh + 2 * rim), (0, 0, 0, 0))
    body = Image.new("RGBA", out.size, (36, 36, 36, 255))
    out.paste(body, (0, 0), rounded_mask(out.size, width // 9))
    inner = rounded_mask((sw, sh), width // 11)
    out.paste(scr, (rim, rim), inner)
    return out


def paste_alpha(canvas, im, x, y, alpha=1.0):
    if alpha < 1:
        a = im.getchannel("A").point(lambda v: int(v * alpha))
        im = im.copy()
        im.putalpha(a)
    canvas.paste(im, (int(x), int(y)), im)


def text(img, xy, s, kind="sans", size=64, fill=TEXT, anchor="la", alpha=1.0):
    if alpha <= 0:
        return
    f = font(kind, size)
    if alpha >= 1:
        ImageDraw.Draw(img).text(xy, s, font=f, fill=fill, anchor=anchor)
        return
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).text(xy, s, font=f, fill=fill + (int(255 * alpha),), anchor=anchor)
    img.paste(layer, (0, 0), layer)
