"""Handwritten "cl0udly" in ballpoint ink on paper, drawn stroke by stroke.

The letters are hand-made pen paths (not a font): plain lowercase print, slightly slanted,
with a little wobble so it looks written, but every letter stays easy to read.
Coordinates are in x-height units: baseline 0, x-height 1, ascenders ~1.8, descenders ~-0.75.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

PAPER = (243, 239, 230)
INK = (22, 36, 104)

# letter -> (advance, strokes); a stroke is (points, joined) where joined means the pen
# doesn't lift before it (a sharp turn inside one movement)
LETTERS = {
    "c": (0.95, [([(0.80, 0.80), (0.56, 1.00), (0.26, 0.93), (0.07, 0.62), (0.08, 0.26),
                   (0.30, 0.02), (0.60, 0.02), (0.84, 0.20)], False)]),
    "l": (0.58, [([(0.34, 1.80), (0.27, 1.20), (0.22, 0.50), (0.22, 0.16), (0.32, 0.01),
                   (0.50, 0.06)], False)]),
    "0": (0.92, [([(0.46, 1.48), (0.18, 1.30), (0.05, 0.78), (0.10, 0.24), (0.36, 0.00),
                   (0.64, 0.14), (0.78, 0.68), (0.72, 1.24), (0.50, 1.48), (0.34, 1.42)], False)]),
    "u": (1.00, [([(0.06, 1.00), (0.06, 0.46), (0.18, 0.08), (0.42, 0.00), (0.64, 0.18),
                   (0.74, 0.60), (0.77, 1.00)], False),
                 ([(0.77, 1.00), (0.76, 0.42), (0.79, 0.06), (0.94, 0.00)], True)]),
    "d": (1.02, [([(0.74, 0.80), (0.50, 1.00), (0.20, 0.90), (0.04, 0.50), (0.12, 0.10),
                   (0.36, 0.00), (0.60, 0.12), (0.74, 0.42)], False),
                 ([(0.80, 1.82), (0.78, 1.10), (0.76, 0.34), (0.80, 0.04), (0.96, 0.00)], False)]),
    "y": (0.94, [([(0.06, 1.00), (0.08, 0.52), (0.22, 0.14), (0.46, 0.08), (0.66, 0.30),
                   (0.75, 0.70), (0.78, 1.00)], False),
                 ([(0.78, 1.00), (0.76, 0.20), (0.70, -0.40), (0.52, -0.72), (0.26, -0.76),
                   (0.10, -0.60)], True)]),
}

SLANT = 0.17
LIFT = 0.07          # seconds the pen spends in the air between strokes


def _noise(seed, k):
    h = (seed * 374761393 + k * 668265263) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0 - 0.5


def _catmull(pts, per=24):
    """Smooth path through the points (Catmull-Rom), returned as a dense polyline."""
    p = [pts[0]] + list(pts) + [pts[-1]]
    out = []
    for i in range(1, len(p) - 2):
        p0, p1, p2, p3 = p[i - 1], p[i], p[i + 1], p[i + 2]
        for s in range(per):
            t = s / per
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                                    + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3) for j in (0, 1)))
    out.append(pts[-1])
    return out


class Word:
    """A written word laid out at a size; `render(progress)` gives the ink so far as an RGBA image."""

    def __init__(self, text="cl0udly", unit=150, seed=5, pen=0.062, ss=2):
        self.unit, self.ss = unit, ss
        strokes = []                       # (polyline in px, joined)
        x = 0.0
        for li, ch in enumerate(text):
            adv, parts = LETTERS[ch]
            dy = _noise(seed, li * 7) * 0.05
            sx = 1 + _noise(seed, li * 7 + 1) * 0.05
            for si, (pts, joined) in enumerate(parts):
                jit = [(px * sx + _noise(seed, li * 97 + si * 13 + k) * 0.02,
                        py + dy + _noise(seed, li * 89 + si * 11 + k) * 0.02) for k, (px, py) in enumerate(pts)]
                line = [((x + px + SLANT * py), py) for px, py in _catmull(jit)]
                strokes.append((line, joined))
            x += adv * sx
        xs = [p[0] for s, _ in strokes for p in s]
        ys = [p[1] for s, _ in strokes for p in s]
        self.pad = pen * 2
        self.x0, self.y1 = min(xs) - self.pad, max(ys) + self.pad
        self.w = int((max(xs) + self.pad - self.x0) * unit)
        self.h = int((self.y1 - (min(ys) - self.pad)) * unit)
        self.baseline = int(self.y1 * unit)          # px from the top of the image to the baseline
        k = unit * ss
        # stamps: (x, y, radius) in supersampled px, plus the pen time at which each appears
        self.stamps = []
        t = 0.0
        speed = 13.0                                  # x-height units per second at full speed
        for si, (line, joined) in enumerate(strokes):
            if si and not joined:
                t += LIFT
            seg = [math.dist(line[i], line[i + 1]) for i in range(len(line) - 1)]
            L = sum(seg) or 1e-6
            acc = 0.0
            for i, (px, py) in enumerate(line):
                f = acc / L
                taper = min(1.0, 0.55 + f * 6, 0.55 + (1 - f) * 7)
                press = 1 + _noise(seed + si, i // 6) * 0.18
                r = pen * 0.5 * taper * press
                # pen slows a little at the start and end of each stroke
                ease = 0.6 + 0.4 * math.sin(math.pi * min(1, max(0, f)))
                self.stamps.append(((px - self.x0) * k, (self.y1 - py) * k, r * k, t))
                if i < len(seg):
                    acc += seg[i]
                    t += seg[i] / (speed * ease)
        self.duration = t
        rng = np.random.default_rng(seed)
        grain = rng.uniform(0.74, 1.0, (self.h * ss, self.w * ss))
        self._grain = grain
        self._mask = Image.new("L", (self.w * ss, self.h * ss), 0)
        self._drawn = 0

    def render(self, upto):
        """Ink up to pen time `upto` (seconds). Calls must not go backwards."""
        d = ImageDraw.Draw(self._mask)
        while self._drawn < len(self.stamps) and self.stamps[self._drawn][3] <= upto:
            x, y, r, _ = self.stamps[self._drawn]
            d.ellipse([x - r, y - r, x + r, y + r], fill=250)
            self._drawn += 1
        m = np.asarray(self._mask, dtype=np.float32) * self._grain
        m = Image.fromarray(m.astype(np.uint8)).resize((self.w, self.h), Image.LANCZOS)
        out = Image.new("RGBA", (self.w, self.h), INK + (0,))
        out.putalpha(m)
        return out

    def reset(self):
        self._mask = Image.new("L", self._mask.size, 0)
        self._drawn = 0


def paper(w, h, seed=2):
    """Plain off-white paper with a fine grain and a few faint fibres."""
    rng = np.random.default_rng(seed)
    base = np.ones((h, w, 3), np.float32) * np.array(PAPER, np.float32)
    g = rng.normal(0, 2.2, (h // 2 + 1, w // 2 + 1)).repeat(2, 0).repeat(2, 1)[:h, :w]
    base += g[..., None]
    img = Image.fromarray(np.clip(base, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img, "RGBA")
    for _ in range(140):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        a = rng.uniform(0, math.pi)
        L = rng.uniform(8, 30)
        d.line([x, y, x + math.cos(a) * L, y + math.sin(a) * L], fill=(150, 140, 120, 18), width=1)
    return img
