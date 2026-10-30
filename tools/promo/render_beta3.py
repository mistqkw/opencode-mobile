"""Video for v0.0.1 beta-3, 1080x1920, 30 fps, 15 bars at 140 BPM (~25.7 s), timed to audio_b3.py.

One screen per two bars. The phone flies in tilted, alternating from the left and the right,
next to it the scene number and a title, and a thin callout points at the relevant part of the screen. Between scenes
a flat blue wipe lasting one beat.

    python3 render_beta3.py <clean screenshots dir> <out.mp4> [audio.wav]

Needs screenshots: home, slash, skills, usage, phone, github-code (.png, after clean.py).
"""
import os
import subprocess
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(__file__))
from common import (BG, BLUE, DIM, LINE, PANEL, PEACH, TEXT, clamp, font, in_out_cubic, lerp,  # noqa: E402
                    out_back, out_cubic, phone_image, text, wordmark, wordmark_height)

W, H, FPS = 1080, 1920, 30
BEAT = 60 / 140
BAR = BEAT * 4
BARS = 15
DUR = BARS * BAR
PW = 560
PY = 520

# screenshot, two-line title, callout point (fraction of the screen), callout label
SCENES = [
    ("home", ("A ball", "that reacts"), (0.5, 0.36), "tap it, poke it,\nlet it nap"),
    ("slash", ("Skills", "behind a slash"), (0.5, 0.60), "/name loads\nthe skill"),
    ("skills", ("Your skills", "come along"), (0.27, 0.43), "from Claude or\nChatGPT, or make\none in the chat"),
    ("usage", ("Limits", "at a glance"), (0.5, 0.29), "5 hours, week\nand month"),
    ("phone", ("Hands on", "your phone"), (0.88, 0.265), "alarms, clipboard,\nphotos, location"),
    ("github-code", ("GitHub,", "signed as you"), (0.5, 0.27), "one code, then\ngit push with no\nco-authors"),
]
S0 = 2 * BAR            # start of the first scene
SL = 2 * BAR            # scene length
END = S0 + len(SCENES) * SL


def dots(c):
    d = ImageDraw.Draw(c)
    for x in range(30, W, 60):
        for y in range(30, H, 60):
            d.rectangle([x, y, x + 1, y + 1], fill=(30, 30, 30))


def overlay(c, draw_fn):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    c.paste(layer, (0, 0), layer)


def intro(c, t):
    s = "opencode mobile"
    shown = s[: max(0, int(t * 15))]
    up = in_out_cubic((t - 3.0) / 0.4) * 160
    y = 1180 - up
    f = font("mono", 54)
    text(c, (120, y), "›", "monob", 54, BLUE)
    text(c, (170, y), shown, "mono", 54, TEXT)
    if len(shown) < len(s) or int(t / BEAT * 2) % 2 == 0:
        x = 170 + ImageDraw.Draw(c).textlength(shown, font=f) + 6
        ImageDraw.Draw(c).rectangle([x, y + 4, x + 26, y + 62], fill=BLUE)
    if t >= BAR:
        wm_w = 880
        wordmark(c, 100, 700 - up, wm_w, enter=clamp((t - BAR) / (BEAT * 2)))
        p = out_back((t - BAR - BEAT * 2) / 0.35, 1.6)
        if p > 0:
            lbl = "beta-3"
            wd = ImageDraw.Draw(c).textlength(lbl, font=font("monob", 40)) + 56
            x0, y0 = 100, 700 - up + wordmark_height(wm_w) + 50
            sc = p
            overlay(c, lambda d: d.rounded_rectangle([x0, y0, x0 + wd * sc, y0 + 70], radius=35, fill=PEACH + (255,)))
            if p > 0.9:
                text(c, (x0 + 28, y0 + 35), lbl, "monob", 40, BG, "lm")


def scene(c, t, k, phones):
    name, (l1, l2), (fx, fy), label = SCENES[k]
    t0 = S0 + k * SL
    lt = t - t0
    right = k % 2 == 0
    ph = phones[k]
    px_rest = W - PW - 60 if right else 60
    # entry: from off-screen, tilted, with a spring
    p = out_back(lt / 0.5, 1.15)
    px = lerp(W + 40 if right else -PW - 40, px_rest, p)
    ang = (1 - clamp(lt / 0.5)) * (-9 if right else 9)
    if abs(ang) > 0.05:
        r = ph.rotate(ang, resample=Image.BICUBIC, expand=True)
        c.paste(r, (int(px - (r.width - ph.width) / 2), int(PY - (r.height - ph.height) / 2)), r)
    else:
        c.paste(ph, (int(px), PY), ph)

    # number and title: one line per beat
    tx = 70 if right else W - 70
    anc = "la" if right else "ra"
    text(c, (tx, 120), f"0{k + 1} / 06", "mono", 30, DIM, anc, clamp(lt / 0.2))
    for j, (s, col) in enumerate(((l1, TEXT), (l2, BLUE))):
        a = out_cubic((lt - j * BEAT) / 0.3)
        if a > 0:
            text(c, (tx + (1 - a) * (-50 if right else 50), 170 + j * 100), s, "sans", 88, col, anc, a)

    # callout: dot on the screen, line to the label on the free side
    rim = max(6, PW // 60)
    sw = PW - 2 * rim
    sh = ph.height - 2 * rim
    tx_, ty_ = px_rest + rim + fx * sw, PY + rim + fy * sh
    lp = in_out_cubic((lt - BEAT * 2) / 0.35)
    if lp > 0 and p > 0.99:
        lx_end = (px_rest - 40) if right else (px_rest + PW + 40)
        x2 = lerp(tx_, lx_end, lp)

        def draw(d):
            d.line([tx_, ty_, x2, ty_], fill=TEXT + (255,), width=2)
            d.ellipse([tx_ - 9, ty_ - 9, tx_ + 9, ty_ + 9], fill=BLUE + (255,), outline=TEXT + (255,), width=2)
        overlay(c, draw)
        if lp >= 1:
            lines = label.split("\n")
            total = sum(len(s) for s in lines)
            n = int((lt - BEAT * 2 - 0.35) * 70)
            for i, s in enumerate(lines):
                before = sum(len(x) for x in lines[:i])
                part = s[: max(0, min(len(s), n - before))]
                if part:
                    if right:
                        text(c, (lx_end - 10, ty_ - 16 + i * 46), part, "mono", 36, TEXT, "ra")
                    else:
                        text(c, (lx_end + 10, ty_ - 16 + i * 46), part, "mono", 36, TEXT, "la")
            _ = total


def outro(c, t):
    lt = t - END
    wm_w = 900
    y0 = 700
    wordmark(c, 90, y0, wm_w, enter=clamp(lt / (BEAT * 1.5)))
    hh = wordmark_height(wm_w)
    text(c, (W // 2, y0 + hh + 70), "mobile · v0.0.1 beta-3", "monob", 50, PEACH, "ma", out_cubic((lt - BEAT * 2) / 0.3))
    a = out_cubic((lt - BEAT * 3) / 0.3)
    if a > 0:
        s = "github.com/mistqkw/opencode-mobile"
        f = font("mono", 36)
        wd = ImageDraw.Draw(c).textlength(s, font=f)
        x0, yy = W / 2 - wd / 2 - 36, y0 + hh + 200 + (1 - a) * 30
        overlay(c, lambda d: d.rounded_rectangle([x0, yy, x0 + wd + 72, yy + 92], radius=46, fill=PANEL + (int(255 * a),), outline=LINE + (int(255 * a),), width=2))
        text(c, (W / 2, yy + 46), s, "mono", 36, TEXT, "mm", a)
    text(c, (W // 2, y0 + hh + 350), "Android · unofficial OpenCode client", "mono", 28, DIM, "ma", out_cubic((lt - BEAT * 4) / 0.3))


def wipe(c, t):
    """Blue wipe across the frame on every scene change."""
    for tc in [S0 + i * SL for i in range(len(SCENES) + 1)]:
        p = (t - (tc - 0.16)) / 0.32
        if 0 <= p <= 1:
            x0 = lerp(-W, W, in_out_cubic(p))
            ImageDraw.Draw(c).rectangle([x0, 0, x0 + W, H], fill=BLUE)


def render(shots_dir, out, audio=None):
    phones = [phone_image(Image.open(os.path.join(shots_dir, s[0] + ".png")), PW) for s in SCENES]
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-"]
    if audio:
        cmd += ["-i", audio, "-c:a", "aac", "-b:a", "160k", "-shortest"]
    cmd += ["-c:v", "libx264", "-preset", "medium", "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for fi in range(int(DUR * FPS)):
        t = fi / FPS
        c = Image.new("RGB", (W, H), BG)
        dots(c)
        if t < S0:
            intro(c, t)
        elif t < END:
            scene(c, t, int((t - S0) // SL), phones)
        else:
            outro(c, t)
        wipe(c, t)
        ff.stdin.write(c.tobytes())
    ff.stdin.close()
    ff.wait()


if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else None)
