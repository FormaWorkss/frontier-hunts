#!/usr/bin/env python3
"""[deersign] Original synthesized sounds of a buck making sign (numpy -> wav -> ogg vorbis via ffmpeg).

deer_rub            antlers raking bark: friction chatter (stick-slip), fibre crackle, a woody knock as the rack meets
                    the trunk, tine ticks
deer_paw            a hoof dragged back through leaf litter and soil: soft thud, crunchy drag, grit ticks
deer_licking_branch twigs and leaves worked overhead: leaf rustle with flutter, small twig creaks
deer_twig_snap      the licking branch tip breaking: sharp crack with a woody ring and splinters
deer_trickle        a quiet trickle on leaves (rub-urination)

    python3 tools/deersign/gen_sounds.py <repo>/patch/assets/frontierhunts/sounds/deersign
"""
import os, subprocess, sys, wave
import numpy as np

SR = 44100
OUT = sys.argv[1] if len(sys.argv) > 1 else 'patch/assets/frontierhunts/sounds/deersign'


def bandpass(x, lo, hi):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    w = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) * 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * w, len(x))


def env(n, a, r, shape=1.0):
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(a, 1e-4)) * np.exp(-np.maximum(0, t - a) / r)
    return e ** shape


def reson(n, f, decay, rng):
    t = np.arange(n) / SR
    return np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * np.exp(-t / decay)


def place(dst, src, at):
    at = int(at)
    if at >= len(dst):
        return
    m = min(len(src), len(dst) - at)
    dst[at:at + m] += src[:m]


def finish(x, peak=0.85):
    x = x - np.mean(x)
    n = len(x)
    fade = int(0.006 * SR)
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade * 3:] *= np.linspace(1, 0, fade * 3)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def rub(rng):
    dur = rng.uniform(0.45, 0.8)
    n = int(dur * SR)
    t = np.arange(n) / SR
    # stroke envelope: quick bite, held drag, release
    e = np.minimum(1, t / 0.05) * np.clip((dur - t) / 0.18, 0, 1) ** 1.5
    # stick-slip chatter: jittered pulse train modulating the friction noise
    f0 = rng.uniform(38, 72)
    ph = np.cumsum(f0 * (1 + 0.25 * np.sin(2 * np.pi * rng.uniform(2, 5) * t) + 0.15 * rng.standard_normal(n) * 0.05)) / SR
    chatter = 0.35 + 0.65 * np.clip(np.sin(2 * np.pi * ph), 0, 1) ** 3
    fric = bandpass(rng.standard_normal(n), 700, 4200) * chatter
    rasp = bandpass(rng.standard_normal(n), 2500, 9000) * 0.35 * chatter
    # bark fibres crackling
    crack = np.zeros(n)
    for _ in range(int(dur * rng.uniform(50, 90))):
        k = reson(int(0.006 * SR), rng.uniform(1800, 6500), rng.uniform(0.0008, 0.002), rng) * rng.uniform(0.3, 1.0)
        place(crack, k, rng.uniform(0, n - 300))
    x = (fric + rasp) * e + crack * e * 0.9
    # the rack meeting the trunk: woody knock at the start, a tine tick or two
    knock = (reson(int(0.12 * SR), rng.uniform(170, 240), 0.03, rng) * 1.2 + reson(int(0.12 * SR), rng.uniform(480, 640), 0.018, rng) * 0.7)
    place(x, knock * rng.uniform(1.6, 2.4), 0)
    for _ in range(rng.integers(1, 3)):
        place(x, reson(int(0.05 * SR), rng.uniform(1900, 3100), 0.007, rng) * rng.uniform(0.8, 1.4), rng.uniform(0.1, 0.8) * n)
    return finish(x, 0.8)


def paw(rng):
    dur = rng.uniform(0.32, 0.5)
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = np.zeros(n)
    # hoof strikes the litter
    thud = reson(int(0.09 * SR), rng.uniform(70, 110), 0.025, rng) * 1.5 + bandpass(rng.standard_normal(int(0.09 * SR)), 150, 900) * env(int(0.09 * SR), 0.002, 0.02)
    place(x, thud, 0)
    # dragged back: soil and leaves
    start = int(rng.uniform(0.04, 0.07) * SR)
    m = n - start
    e = env(m, 0.03, rng.uniform(0.09, 0.15), 0.8)
    drag = bandpass(rng.standard_normal(m), 250, 2600) * e * 0.9
    leaves = np.zeros(m)
    for _ in range(int(m / SR * rng.uniform(140, 220))):
        k = reson(int(0.004 * SR), rng.uniform(1500, 7000), rng.uniform(0.0004, 0.0012), rng)
        place(leaves, k * rng.uniform(0.2, 1.0), rng.uniform(0, m - 200))
    leaves *= e
    grit = np.zeros(m)
    for _ in range(rng.integers(3, 8)):
        place(grit, reson(int(0.01 * SR), rng.uniform(3000, 7000), 0.0015, rng) * rng.uniform(0.4, 0.9), rng.uniform(0, 0.7) * m)
    place(x, drag + leaves * 0.8 + grit * e * 0.6, start)
    return finish(x, 0.8)


def branch(rng):
    dur = rng.uniform(0.5, 0.9)
    n = int(dur * SR)
    t = np.arange(n) / SR
    e = np.sin(np.pi * np.clip(t / dur, 0, 1)) ** 0.7
    flutter = 0.3 + 0.7 * np.abs(bandpass(rng.standard_normal(n), 6, 32))
    flutter /= np.max(flutter)
    rustle = bandpass(rng.standard_normal(n), 1800, 9500) * flutter
    tick = np.zeros(n)
    for _ in range(int(dur * rng.uniform(60, 110))):
        place(tick, reson(int(0.004 * SR), rng.uniform(2500, 8000), 0.0006, rng) * rng.uniform(0.2, 0.8), rng.uniform(0, n - 200))
    creak = np.zeros(n)
    for _ in range(rng.integers(0, 3)):
        m = int(rng.uniform(0.04, 0.09) * SR)
        f = rng.uniform(500, 900)
        tt = np.arange(m) / SR
        c = np.sin(2 * np.pi * (f * tt + rng.uniform(-200, 200) * tt * tt)) * np.sin(np.pi * tt / tt[-1]) * (0.6 + 0.4 * np.sign(np.sin(2 * np.pi * 90 * tt)))
        place(creak, bandpass(c, 300, 3000) * 0.5, rng.uniform(0.1, 0.7) * n)
    return finish((rustle * 0.9 + tick) * e + creak, 0.7)


def snap(rng):
    n = int(0.35 * SR)
    x = np.zeros(n)
    crack = bandpass(rng.standard_normal(int(0.012 * SR)), 900, 12000) * env(int(0.012 * SR), 0.0003, 0.003)
    place(x, crack * 2.0, 0)
    place(x, reson(int(0.15 * SR), rng.uniform(1100, 1700), 0.022, rng) * 0.9, 0)
    place(x, reson(int(0.15 * SR), rng.uniform(2300, 3200), 0.012, rng) * 0.5, 0)
    for _ in range(rng.integers(3, 7)):
        place(x, bandpass(rng.standard_normal(300), 1500, 9000) * env(300, 0.0002, 0.0015) * rng.uniform(0.3, 0.9), rng.uniform(0.01, 0.12) * SR)
    # leaves shaking after the break
    m = int(0.25 * SR)
    place(x, bandpass(rng.standard_normal(m), 2500, 9000) * env(m, 0.01, 0.07) * 0.25, int(0.03 * SR))
    return finish(x, 0.85)


def trickle(rng):
    dur = 2.6
    n = int(dur * SR)
    t = np.arange(n) / SR
    e = np.minimum(1, t / 0.4) * np.clip((dur - t) / 0.6, 0, 1)
    x = bandpass(rng.standard_normal(n), 300, 2500) * 0.12
    for _ in range(int(dur * 55)):
        m = int(rng.uniform(0.006, 0.02) * SR)
        tt = np.arange(m) / SR
        f = rng.uniform(700, 1900)
        b = np.sin(2 * np.pi * (f * tt + f * 2.5 * tt * tt / (tt[-1] + 1e-6) * 0.5)) * np.exp(-tt / (tt[-1] * 0.35))
        place(x, b * rng.uniform(0.15, 0.5), rng.uniform(0, n - m))
    x += bandpass(rng.standard_normal(n), 3000, 9000) * 0.06
    return finish(x * e, 0.55)


def write(name, x):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + '.wav')
    ogg = os.path.join(OUT, name + '.ogg')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', ogg], check=True)
    os.remove(wav)


if __name__ == '__main__':
    rng = np.random.default_rng(20261002)
    for i in range(5):
        write(f'rub_{i}', rub(rng))
    for i in range(5):
        write(f'paw_{i}', paw(rng))
    for i in range(4):
        write(f'branch_{i}', branch(rng))
    for i in range(3):
        write(f'snap_{i}', snap(rng))
    for i in range(2):
        write(f'trickle_{i}', trickle(rng))
    print('ok', sorted(os.listdir(OUT)))
