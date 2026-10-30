"""Ролик к v0.0.1 beta-3, 1080x1920, 30 fps, ~25 с: надпись, телефон с новыми экранами, титры, финал.

    python3 render_beta3.py <папка с чистыми скриншотами> <выход.mp4> [аудио.wav]

Нужны скриншоты: home, slash, skills, usage, phone, github-code (.png, после clean.py).
"""
import math
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import (BG, BLUE, DIM, GREEN, LINE, PANEL, PEACH, TEXT, clamp, font, in_out_cubic, lerp, mix,  # noqa: E402
                    out_back, out_cubic, paste_alpha, phone_image, text, wordmark, wordmark_height)

W, H, FPS = 1080, 1920, 30
DUR = 25.0
PW = 600
GAP = 280
SHOTS = ["home", "slash", "skills", "usage", "phone", "github-code"]
# начало каждой сцены; телефон переезжает к следующему кадру за 0.5 с
SCENES = [3.4, 6.8, 10.2, 13.4, 16.4, 19.4]
TITLES = [
    ("Meet the ball", "tap it, poke it, let it nap"),
    ("Skills", "type / and pick one"),
    ("Bring your skills", "import from Claude or ChatGPT,\nor make one right in the chat"),
    ("Usage at a glance", "5-hour, weekly and monthly limits"),
    ("It acts on your phone", "alarms, clipboard, photos, location"),
    ("GitHub, as you", "commits and PRs with no co-authors"),
]
END = 22.4


def idx_at(t):
    v = 0.0
    for k, t0 in enumerate(SCENES[1:], start=1):
        t0 -= 0.25
        if t >= t0 + 0.5:
            v = float(k)
        elif t >= t0:
            v = lerp(k - 1, k, in_out_cubic((t - t0) / 0.5))
            break
        else:
            break
    return v


def caption(c, t, k):
    t_in, t_out = SCENES[k], (SCENES[k + 1] if k + 1 < len(SCENES) else END) - 0.3
    title, sub = TITLES[k]
    for j, (s, kind, size, color, y) in enumerate([(title, "sans", 96, TEXT, 130), (sub, "mono", 36, DIM, 262)]):
        p = out_cubic((t - t_in - j * 0.15) / 0.5)
        q = clamp((t - t_out) / 0.25)
        a = p * (1 - q)
        if a <= 0:
            continue
        yy = y + (1 - p) * 60 - q * 30
        for n, part in enumerate(s.split("\n")):
            text(c, (W // 2, yy + n * 50), part, kind, size, color, "ma", a)


def chip(c, x, y, label, a, dot=BLUE, w=None):
    f = font("mono", 36)
    wd = w or ImageDraw.Draw(c).textlength(label, font=f) + 96
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    al = int(255 * a)
    ld.rounded_rectangle([x, y, x + wd, y + 84], radius=42, fill=(24, 24, 24, al), outline=LINE + (al,), width=2)
    ld.ellipse([x + 26, y + 33, x + 44, y + 51], fill=dot + (al,))
    c.paste(layer, (0, 0), layer)
    text(c, (x + 62, y + 42), label, "mono", 36, TEXT, "lm", a)


def render(shots_dir, out, audio=None):
    phones = [phone_image(Image.open(os.path.join(shots_dir, n + ".png")), PW) for n in SHOTS]
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-"]
    if audio:
        cmd += ["-i", audio, "-c:a", "aac", "-b:a", "160k", "-shortest"]
    cmd += ["-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for fi in range(int(DUR * FPS)):
        t = fi / FPS
        c = Image.new("RGB", (W, H), BG)

        # ---------- 0–3.6: надпись и печатная строка ----------
        if t < 3.6:
            wm_w, y0 = 940, 760
            fade = clamp((t - 3.0) / 0.4)
            eff, p = (("glitch", (t - 2.0) / 0.9) if 2.0 <= t < 2.9 else (None, 1.0))
            wc = Image.new("RGB", (W, H), BG)
            wordmark(wc, 70, y0 - fade * 60, wm_w, enter=clamp(t / 1.3), effect=eff, p=p, sweep=((t - 1.3) / 1.2 if 1.3 < t < 2.5 else None))
            c = Image.blend(wc, c, fade) if fade > 0 else wc
            s = "beta-3: skills, GitHub, your phone."
            shown = s[: max(0, int((t - 1.4) * 22))]
            ty = y0 + wordmark_height(wm_w) + 90 - fade * 60
            f = font("mono", 38)
            fullw = ImageDraw.Draw(c).textlength(s, font=f)
            xs = W / 2 - fullw / 2
            if shown:
                text(c, (xs, ty), shown, "mono", 38, DIM, "la", 1 - fade)
            if t > 1.4 and (len(shown) < len(s) or int(t * 2.4) % 2 == 0) and fade < 1:
                cx0 = xs + ImageDraw.Draw(c).textlength(shown, font=f) + 4
                ImageDraw.Draw(c).rectangle([cx0, ty + 2, cx0 + 19, ty + 44], fill=mix(BLUE, BG, fade))

        # ---------- телефон 3.2–22.6 ----------
        if 3.2 <= t < END + 0.4:
            phone_in = out_back((t - 3.2) / 0.75, 1.3)
            phone_out = in_out_cubic((t - END) / 0.6)
            py_rest = 400
            py = lerp(H + 40, py_rest, phone_in) + phone_out * (H - py_rest)
            idx = idx_at(t)
            cx = (W - PW) / 2
            for i, ph in enumerate(phones):
                off = (i - idx) * (PW + GAP)
                if abs(off) < W:
                    paste_alpha(c, ph, cx + off, py, 1.0)

            # навыки вылетают по бокам телефона
            k = int(round(idx))
            if k in (1, 2):
                base = SCENES[1]
                outq = clamp((t - (SCENES[3] - 0.5)) / 0.35)
                for j, (label, y, side) in enumerate([("/madkid", 760, -1), ("/texfi", 980, 1), ("/flstudio", 1200, -1), ("/review", 1420, 1)]):
                    p = out_back((t - base - 0.5 - j * 0.25) / 0.6, 1.2)
                    if p <= 0:
                        continue
                    wd = 330
                    xt = 24 if side < 0 else W - 24 - wd
                    xs = -wd - 20 if side < 0 else W + 20
                    chip(c, lerp(xs, xt, p), y + math.sin(t * 2.0 + j) * 8, label, 1 - outq, w=wd)
            if k == 5:
                p = out_back((t - SCENES[5] - 0.6) / 0.6, 1.2)
                if p > 0:
                    wd = 520
                    chip(c, lerp(W + 20, (W - wd) / 2, p), 1520, "git push · as you", 1 - clamp((t - END + 0.4) / 0.3), dot=GREEN, w=wd)

            for k2 in range(len(SCENES)):
                caption(c, t, k2)

        # ---------- финал ----------
        if t >= END - 0.1:
            wm_w, y0 = 900, 640
            eff, p = (("wave", clamp((t - (END + 2.4)) / 0.9)) if t >= END + 2.4 else (None, 1.0))
            wordmark(c, 90, y0, wm_w, enter=clamp((t - END + 0.1) / 1.2), effect=eff, p=p)
            hh = wordmark_height(wm_w)
            text(c, (W // 2, y0 + hh + 70), "mobile · v0.0.1 beta-3", "monob", 52, PEACH, "ma", out_cubic((t - END - 0.9) / 0.5))
            a2 = out_cubic((t - END - 1.3) / 0.5)
            if a2 > 0:
                f = font("mono", 38)
                s = "github.com/mistqkw/opencode-mobile"
                wd = ImageDraw.Draw(c).textlength(s, font=f)
                x0, yy = W / 2 - wd / 2 - 40, y0 + hh + 210 + (1 - a2) * 40
                layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(layer).rounded_rectangle([x0, yy, x0 + wd + 80, yy + 100], radius=16, fill=BLUE + (int(255 * a2),))
                c.paste(layer, (0, 0), layer)
                text(c, (W / 2, yy + 50), s, "mono", 38, (10, 10, 10), "mm", a2)
            text(c, (W // 2, y0 + hh + 360), "Android · unofficial OpenCode client", "mono", 30, DIM, "ma", out_cubic((t - END - 1.8) / 0.5))

        ff.stdin.write(c.tobytes())
    ff.stdin.close()
    ff.wait()


if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
