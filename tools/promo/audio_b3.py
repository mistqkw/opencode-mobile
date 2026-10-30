"""Music for the beta-3 video: 140 BPM, A minor, plucked arpeggio, sub bass, half-time clap.
The intro is only the arpeggio, drums come in on bar 2, the last bar is a held chord.

    python3 audio_b3.py <out.wav> [bars]
"""
import sys
import wave

import numpy as np

SR = 44100
BPM = 140.0
BEAT = 60.0 / BPM
BAR = BEAT * 4
rng = np.random.default_rng(3)

# Am – F – C – G, roots in Hz (second octave, for the bass)
ROOTS = [110.0, 87.31, 130.81, 98.0]
CHORDS = [[0, 3, 7, 12, 15], [0, 4, 7, 12, 16], [0, 4, 7, 12, 16], [0, 4, 7, 12, 14]]
ARP = [0, 2, 1, 3, 4, 3, 1, 2]


def note(f, length, bright=1.0):
    t = np.arange(int(length * SR)) / SR
    s = np.zeros_like(t)
    for h in range(1, 8):
        s += np.sin(2 * np.pi * f * h * t) / h * np.exp(-t * (6 + h * 9 / bright))
    return s * np.minimum(1, t / 0.003)


def kick():
    t = np.arange(int(0.3 * SR)) / SR
    f = 42 + 120 * np.exp(-t * 35)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t * 9)


def clap():
    t = np.arange(int(0.22 * SR)) / SR
    n = rng.uniform(-1, 1, t.size)
    n = np.diff(n, prepend=0)                         # crude high-pass
    env = np.exp(-t * 22) + 0.6 * np.exp(-((t - 0.012) % 0.011) * 400) * (t < 0.035)
    return n * env


def hat(open_=False):
    t = np.arange(int((0.16 if open_ else 0.04) * SR)) / SR
    n = np.diff(rng.uniform(-1, 1, t.size), prepend=0)
    return n * np.exp(-t * (18 if open_ else 110))


def main(out, bars=15):
    n = int(bars * BAR * SR) + SR
    mix = np.zeros(n)

    def add(t0, s, g):
        i = int(t0 * SR)
        j = min(n, i + s.size)
        if i < n:
            mix[i:j] += s[: j - i] * g

    K, C, H, HO = kick(), clap(), hat(), hat(True)
    for b in range(bars):
        t0 = b * BAR
        root = ROOTS[b % 4]
        last = b == bars - 1
        # sixteenth-note arpeggio; darker in the intro
        bright = 0.45 if b < 2 else 1.0
        if not last:
            for k in range(16):
                semi = CHORDS[b % 4][ARP[k % 8]]
                f = root * 4 * 2 ** (semi / 12)
                add(t0 + k * BEAT / 4, note(f, 0.28, bright), 0.16 if k % 4 else 0.22)
        else:
            for semi in CHORDS[b % 4]:
                add(t0, note(root * 4 * 2 ** (semi / 12), 2.4, 0.6), 0.12)
        drums = 2 <= b < bars - 1
        if drums:
            # sub bass: root on beat 1 and on the "and" of beat 3
            for at, ln in ((0, BEAT * 2.4), (BEAT * 2.5, BEAT * 1.4)):
                t = np.arange(int(ln * SR)) / SR
                s = np.sin(2 * np.pi * root * t) * np.minimum(1, t / 0.01) * np.minimum(1, (ln - t) / 0.05)
                add(t0 + at, s, 0.42)
            add(t0, K, 0.9)
            if b % 2 == 1:
                add(t0 + BEAT * 2.5, K, 0.6)
            add(t0 + BEAT * 2, C, 0.5)
            for k in range(8):
                add(t0 + k * BEAT / 2 + (0.018 if k % 2 else 0), HO if k % 4 == 3 else H, 0.16 if k % 2 else 0.1)
        # noise riser before the drums come in
        if b == 1:
            t = np.arange(int(BAR * SR)) / SR
            s = np.convolve(rng.uniform(-1, 1, t.size), np.ones(6) / 6, "same") * (t / BAR) ** 3
            add(t0, s, 0.35)
        if b == 2:
            add(t0, C, 0.8)

    mix = np.tanh(mix * 1.3) * 0.8
    fade = int(0.8 * SR)
    mix[-fade:] *= np.linspace(1, 0, fade)
    pcm = (mix * 32767).astype(np.int16)
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 15)
