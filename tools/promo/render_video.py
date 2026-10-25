"""Ролик для TikTok 1080x1920, 30 fps, ~21 с: пиксельная надпись, телефон-карусель, титры.

    python3 render_video.py <папка с чистыми скриншотами> <выход.mp4> [аудио.wav]

Нужны скриншоты: claude-home, terminal-home, desktop-home, memory, claude-models (.png).
Кадры идут прямо в ffmpeg, на диск ничего не пишется.
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import (BG, BLUE, DIM, GREEN, LINE, PANEL, PEACH, TEXT, clamp, font, in_out_cubic, lerp, mix,  # noqa: E402
                    out_back, out_cubic, paste_alpha, phone_image, rounded_mask, text, wordmark, wordmark_height)

W, H, FPS = 1080, 1920, 30
DUR = float(os.environ.get("PROMO_DUR", "21.5"))
PW = 640          # ширина телефона
GAP = 80
SHOTS = ["claude-home", "terminal-home", "desktop-home", "memory", "claude-models"]
LABELS = {0: ("Claude", "serif"), 1: ("OpenCode Terminal", "mono"), 2: ("OpenCode Desktop", "sans")}


def idx_at(t):
    """Положение карусели: 0..4, переключения с плавным сдвигом."""
    v = 0.0
    for t0, to in [(7.4, 1), (9.1, 2), (11.1, 3), (14.4, 4)]:
        if t >= t0 + 0.5:
            v = float(to)
        elif t >= t0:
            v = lerp(to - 1, to, in_out_cubic((t - t0) / 0.5))
            break
        else:
            break
    return v


def lines(canvas, t, items, t_in, t_out):
    for s, kind, size, color, y, delay in items:
        p = out_cubic((t - t_in - delay) / 0.55)
        q = clamp((t - t_out) / 0.25)
        a = p * (1 - q)
        if a <= 0:
            continue
        yy = y + (1 - p) * 70 - q * 40
        for k, part in enumerate(s.split("\n")):
            text(canvas, (W // 2, yy + k * (size * 1.45)), part, kind, size, color, "ma", a)


def render(shots_dir, out, audio=None):
    shots = [Image.open(os.path.join(shots_dir, n + ".png")) for n in SHOTS]
    phones = [phone_image(s, PW) for s in shots]
    ph_h = phones[0].height
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-"]
    if audio:
        cmd += ["-i", audio, "-c:a", "aac", "-b:a", "160k", "-shortest"]
    cmd += ["-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    n = int(DUR * FPS)
    for fi in range(n):
        t = fi / FPS
        c = Image.new("RGB", (W, H), BG)

        # ---------- 0–3.3: надпись и печатная строка ----------
        if t < 3.6:
            wm_w = 940
            y0 = 760
            fade = clamp((t - 3.0) / 0.4)
            eff, p = None, 1.0
            if 2.0 <= t < 2.9:
                eff, p = "glitch", (t - 2.0) / 0.9
            wc = Image.new("RGB", (W, H), BG)
            wordmark(wc, 70, y0 - fade * 60, wm_w, enter=clamp(t / 1.3), effect=eff, p=p, sweep=((t - 1.3) / 1.2 if 1.3 < t < 2.5 else None))
            if fade > 0:
                c = Image.blend(wc, c, fade)
            else:
                c = wc
            s = "OpenCode. Now in your pocket."
            shown = s[: max(0, int((t - 1.5) * 20))]
            ty = y0 + wordmark_height(wm_w) + 90 - fade * 60
            f = font("mono", 40)
            fullw = ImageDraw.Draw(c).textlength(s, font=f)
            xs = W / 2 - fullw / 2
            if shown:
                text(c, (xs, ty), shown, "mono", 40, DIM, "la", 1 - fade)
            if t > 1.5 and (len(shown) < len(s) or int(t * 2.4) % 2 == 0) and fade < 1:
                cx0 = xs + ImageDraw.Draw(c).textlength(shown, font=f) + 4
                ImageDraw.Draw(c).rectangle([cx0, ty + 2, cx0 + 20, ty + 46], fill=mix(BLUE, BG, fade))

        # ---------- телефон-карусель 3.2–17.9 ----------
        phone_in = out_back((t - 3.2) / 0.75, 1.3)
        phone_out = in_out_cubic((t - 17.5) / 0.7)
        if 3.2 <= t < 18.3:
            py_rest = lerp(520, 450, in_out_cubic((t - 6.3) / 0.6))
            py = lerp(H + 40, py_rest, phone_in) + phone_out * (H - py_rest)
            idx = idx_at(t)
            cx = (W - PW) / 2
            peek = clamp((t - 6.2) / 0.5)
            for i, ph in enumerate(phones):
                off = (i - idx) * (PW + GAP)
                if abs(off) > W:
                    continue
                if i != round(idx) and peek <= 0 and abs(off) > 1:
                    continue
                paste_alpha(c, ph, cx + off, py, 1.0)

            # подпись редакции над телефоном (сцена 3)
            if 6.5 <= t < 11.0:
                chip_t = clamp((t - 6.7) / 0.4) * (1 - clamp((t - 10.8) / 0.25))
                for k, (label, kind) in LABELS.items():
                    a = chip_t * (1 - clamp(abs(idx - k) * 2))
                    if a <= 0:
                        continue
                    f = font(kind if kind != "serif" else "serif", 44)
                    wd = ImageDraw.Draw(c).textlength(label, font=f)
                    x0, y0c = W / 2 - wd / 2 - 34, 300
                    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                    ld = ImageDraw.Draw(layer)
                    ld.rounded_rectangle([x0, y0c, x0 + wd + 68, y0c + 86], radius=43 if k == 0 else (0 if k == 1 else 14), fill=PANEL + (int(255 * a),), outline=LINE + (int(255 * a),), width=2)
                    c.paste(layer, (0, 0), layer)
                    text(c, (W / 2, y0c + 43), label, kind, 44, TEXT, "mm", a)

            # плавающие «полки» (сцена 4)
            if 11.0 <= t < 14.4:
                chips = [("about-me.md", 720, -1), ("work.md", 900, 1), ("projects.md", 1080, -1)]
                for i, (label, y, side) in enumerate(chips):
                    p = out_back((t - 11.5 - i * 0.28) / 0.6, 1.2)
                    outq = clamp((t - 14.0) / 0.35)
                    if p <= 0:
                        continue
                    wd = 380
                    xt = 36 if side < 0 else W - 36 - wd
                    xs = -wd - 20 if side < 0 else W + 20
                    x = lerp(xs, xt, p) + (xs - xt) * outq * 0.0
                    yy = y + math.sin(t * 2.2 + i) * 8
                    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                    ld = ImageDraw.Draw(layer)
                    a = int(255 * (1 - outq))
                    ld.rounded_rectangle([x, yy, x + wd, yy + 96], radius=22, fill=(26, 26, 26, a), outline=LINE + (a,), width=2)
                    ld.ellipse([x + 26, yy + 38, x + 46, yy + 58], fill=GREEN + (a,))
                    c.paste(layer, (0, 0), layer)
                    text(c, (x + 66, yy + 48), label, "mono", 38, TEXT, "lm", 1 - outq)

        # ---------- титры ----------
        lines(c, t, [("Your own server", "sans", 104, TEXT, 170, 0), ("inside your phone", "sans", 104, BLUE, 290, 0.15),
                     ("Ubuntu + OpenCode · no computer needed", "mono", 34, DIM, 430, 0.35)], 3.45, 6.2)
        lines(c, t, [("Three interfaces", "sans", 104, TEXT, 150, 0)], 6.55, 10.9)
        lines(c, t, [("Memory", "sans", 104, TEXT, 150, 0), ("the agent puts facts\non shelves by itself", "mono", 36, DIM, 290, 0.2)], 11.1, 14.2)
        lines(c, t, [("Any model", "sans", 104, TEXT, 150, 0), ("free models included*", "mono", 34, DIM, 290, 0.2),
                     ("*free models share a daily limit", "mono", 26, DIM, 345, 0.35)], 14.5, 17.4)

        # ---------- финал ----------
        if t >= 17.6:
            wm_w = 900
            y0 = 640
            eff, p = (None, 1.0)
            if t >= 20.2:
                eff, p = "wave", clamp((t - 20.2) / 0.9)
            wordmark(c, 90, y0, wm_w, enter=clamp((t - 17.9) / 1.3), effect=eff, p=p)
            hh = wordmark_height(wm_w)
            a1 = out_cubic((t - 19.0) / 0.5)
            text(c, (W // 2, y0 + hh + 70), "mobile · v0.0.1 beta-1", "monob", 52, PEACH, "ma", a1)
            a2 = out_cubic((t - 19.5) / 0.5)
            if a2 > 0:
                f = font("mono", 38)
                s = "github.com/mistqkw/opencode-mobile"
                wd = ImageDraw.Draw(c).textlength(s, font=f)
                x0, yy = W / 2 - wd / 2 - 40, y0 + hh + 210 + (1 - a2) * 40
                layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(layer).rounded_rectangle([x0, yy, x0 + wd + 80, yy + 100], radius=16, fill=BLUE + (int(255 * a2),))
                c.paste(layer, (0, 0), layer)
                text(c, (W / 2, yy + 50), s, "mono", 38, (10, 10, 10), "mm", a2)
            text(c, (W // 2, y0 + hh + 360), "Android · unofficial OpenCode client", "mono", 30, DIM, "ma", out_cubic((t - 20.0) / 0.5))

        ff.stdin.write(c.tobytes())
    ff.stdin.close()
    ff.wait()


if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
