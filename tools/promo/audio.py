"""Звук к ролику: тихий бит 110 BPM, аккорды и «свуши» на смене сцен. Без внешних семплов, только math.

    python3 audio.py <выход.wav> [длительность]
"""
import math
import random
import struct
import sys
import wave

SR = 44100
BPM = 110.0
BEAT = 60.0 / BPM


def main(out, dur=21.5):
    n = int(dur * SR)
    buf = [0.0] * n
    rnd = random.Random(7)

    def add(t0, samples, gain=1.0):
        i0 = int(t0 * SR)
        for k, v in enumerate(samples):
            if i0 + k >= n:
                break
            buf[i0 + k] += v * gain

    def kick():
        L = int(0.22 * SR)
        ph = 0.0
        s = []
        for k in range(L):
            tt = k / SR
            f = 45 + 110 * math.exp(-tt * 28)
            ph += 2 * math.pi * f / SR
            s.append(math.sin(ph) * math.exp(-tt * 14))
        return s

    def hat():
        L = int(0.05 * SR)
        prev = 0.0
        s = []
        for k in range(L):
            x = rnd.uniform(-1, 1)
            s.append((x - prev) * math.exp(-k / SR * 90))
            prev = x
        return s

    def whoosh(length=0.55):
        L = int(length * SR)
        lp = 0.0
        s = []
        for k in range(L):
            tt = k / L
            a = 0.02 + 0.5 * tt
            lp += a * (rnd.uniform(-1, 1) - lp)
            s.append(lp * math.sin(math.pi * tt) ** 1.5)
        return s

    def tone(freq, length, atk=0.02, rel=0.25):
        L = int(length * SR)
        s = []
        for k in range(L):
            tt = k / SR
            env = min(1.0, tt / atk) * min(1.0, (length - tt) / rel)
            s.append(env * (math.sin(2 * math.pi * freq * tt) * 0.7 + math.sin(2 * math.pi * freq * 2 * tt) * 0.15))
        return s

    K, Hh = kick(), hat()
    # бит с 3.2 с (появляется телефон) до конца
    t = 3.2
    beat = 0
    while t < dur - 0.2:
        if beat % 4 in (0, 2) or beat % 8 == 7:
            add(t, K, 0.55)
        add(t + BEAT / 2, Hh, 0.12)
        if beat % 4 == 1 or beat % 4 == 3:
            add(t, Hh, 0.08)
        t += BEAT
        beat += 1
    # аккорды Am - F - C - G, по такту
    chords = [(220.0, 261.63, 329.63), (174.61, 220.0, 261.63), (261.63, 329.63, 392.0), (196.0, 246.94, 293.66)]
    bar = BEAT * 4
    t, i = 0.0, 0
    while t < dur - 0.3:
        for f in chords[i % 4]:
            add(t, tone(f, bar, 0.08, 0.5), 0.05)
        add(t, tone(chords[i % 4][0] / 2, bar, 0.02, 0.3), 0.09)
        t += bar
        i += 1
    # сборка надписи: тики по клеткам в начале, свуши на сменах сцен
    for k in range(18):
        add(0.15 + k * 0.055, tone(900 + 60 * k, 0.03, 0.002, 0.02), 0.05)
    for ts in (3.1, 6.4, 7.35, 9.05, 11.05, 14.35, 17.45):
        add(ts, whoosh(), 0.28)
    # финальный аркпеджио
    for k, f in enumerate((440.0, 523.25, 659.25, 880.0)):
        add(19.0 + k * 0.12, tone(f, 0.5, 0.01, 0.4), 0.1)
    peak = max(1e-6, max(abs(v) for v in buf))
    gain = 0.8 / peak
    fade = int(0.8 * SR)
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for k, v in enumerate(buf):
            v *= gain
            if k > n - fade:
                v *= (n - k) / fade
            frames += struct.pack("<h", max(-32767, min(32767, int(v * 32767))))
        w.writeframes(bytes(frames))


if __name__ == "__main__":
    main(sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 21.5)
