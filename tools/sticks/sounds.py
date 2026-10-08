#!/usr/bin/env python3
"""[sticks] Synthesized sounds for the shooting sticks (numpy -> wav -> ogg via ffmpeg). Deterministic.

    deploy1/2  legs swing out, tubes slide, three lever locks snap, rubber feet tap down, the aluminium rings faintly
    fold1/2    locks thrown open, tubes slide home, the legs clatter together
    adjust1/2  a lock lever opens, a section slides, the lever snaps shut
    rest1/2    a forend settles into the rubber yoke
    lift1      the gun comes up off the yoke
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'patch', 'assets', 'frontierhunts', 'sounds', 'sticks')


def t(n):
    return np.arange(n) / SR


def env(n, attack, decay):
    x = t(n)
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    return a * np.exp(-x / max(decay, 1e-4))


def bandnoise(n, lo, hi, rng):
    x = rng.standard_normal(n)
    f = np.fft.rfft(x)
    fr = np.fft.rfftfreq(n, 1 / SR)
    m = (fr >= lo) & (fr <= hi)
    shape = np.where(m, 1.0, 0.0)
    # soft edges
    shape = np.convolve(shape, np.hanning(9) / np.hanning(9).sum(), mode='same')
    y = np.fft.irfft(f * shape, n)
    return y / (np.abs(y).max() + 1e-9)


def ring(n, freqs, decays, amps, rng):
    x = t(n)
    y = np.zeros(n)
    for f, d, a in zip(freqs, decays, amps):
        y += a * np.sin(2 * np.pi * f * x * (1 + rng.normal(0, 0.002)) + rng.uniform(0, 6.28)) * np.exp(-x / d)
    return y


def click(rng, bright=1.0, body=2600.0):
    """A lever lock snapping: a hard transient, a plastic body knock and a short metal ring."""
    n = int(0.09 * SR)
    y = bandnoise(n, 1500, 9000, rng) * env(n, 0.0002, 0.004) * 0.9 * bright
    y += ring(n, [body, body * 1.63, body * 2.4], [0.012, 0.008, 0.005], [0.5, 0.3, 0.2], rng)
    y += bandnoise(n, 300, 1200, rng) * env(n, 0.0005, 0.012) * 0.35
    return y


def slide(rng, dur, lo=900, hi=5000, sweep=1.0):
    """Aluminium tube sliding through a collar: gritty friction noise that rises (or falls) in pitch."""
    n = int(dur * SR)
    base = bandnoise(n, lo, hi, rng)
    grit = (rng.random(n) < 0.004) * rng.uniform(0.3, 1.0, n)
    grit = np.convolve(grit, np.exp(-np.arange(80) / 12.0), mode='same')
    x = np.linspace(0, 1, n)
    am = np.sin(np.pi * np.clip(x, 0, 1)) ** 0.7 * (0.7 + 0.3 * np.sin(2 * np.pi * (14 + 6 * sweep * x) * x))
    return (base * 0.45 + grit * 0.35) * am


def thud(rng, f=140.0, dur=0.12, amp=1.0):
    n = int(dur * SR)
    x = t(n)
    fall = f * (1 + 0.6 * np.exp(-x / 0.01))
    y = np.sin(2 * np.pi * np.cumsum(fall) / SR) * env(n, 0.001, dur / 4)
    y += bandnoise(n, 200, 1500, rng) * env(n, 0.0005, 0.01) * 0.3
    return y * amp


def tube_ring(rng, dur=0.5, amp=0.25):
    n = int(dur * SR)
    return ring(n, [1130, 2950, 5400], [0.07, 0.035, 0.018], [0.5, 0.3, 0.15], rng) * amp


def mixin(buf, sig, at):
    i = int(at * SR)
    j = min(len(buf), i + len(sig))
    if i < len(buf):
        buf[i:j] += sig[:j - i]


def finish(y, name, peak=0.7):
    y = y - np.mean(y)
    fade = int(0.01 * SR)
    y[-fade:] *= np.linspace(1, 0, fade)
    y = y / (np.abs(y).max() + 1e-9) * peak
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as d:
        wav = os.path.join(d, name + '.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((y * 32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)


def deploy(seed):
    rng = np.random.default_rng(seed)
    y = np.zeros(int(1.05 * SR))
    mixin(y, slide(rng, 0.32, 700, 4200) * 0.5, 0.0)
    for k, at in enumerate((0.16, 0.27, 0.36)):
        mixin(y, slide(rng, 0.12, 1200, 6000) * 0.35, at - 0.1)
        mixin(y, click(rng, 1.0 - 0.1 * k, 2400 + rng.uniform(-200, 300)) * 0.8, at)
    for k, at in enumerate((0.5, 0.56, 0.63)):
        mixin(y, thud(rng, 120 + 30 * k, 0.14, 0.9 - 0.15 * k), at + rng.uniform(-0.01, 0.01))
    mixin(y, tube_ring(rng, 0.3, 0.12), 0.5)
    return y


def fold(seed):
    rng = np.random.default_rng(seed)
    y = np.zeros(int(1.15 * SR))
    for k, at in enumerate((0.0, 0.08, 0.15)):
        mixin(y, click(rng, 0.7, 2000 + rng.uniform(-150, 200)) * 0.6, at)
    for k, at in enumerate((0.2, 0.3, 0.38)):
        mixin(y, slide(rng, 0.28, 800, 4600, sweep=-1.0) * 0.55, at)
    for k, at in enumerate((0.72, 0.8, 0.86)):
        dt = at + rng.uniform(-0.01, 0.01)
        mixin(y, tube_ring(rng, 0.3, 0.45), dt)
        mixin(y, thud(rng, 260, 0.06, 0.4), dt)
    return y


def adjust(seed):
    rng = np.random.default_rng(seed)
    y = np.zeros(int(0.75 * SR))
    mixin(y, click(rng, 0.6, 1900) * 0.6, 0.0)
    mixin(y, slide(rng, 0.3, 900, 5200) * 0.6, 0.08)
    mixin(y, click(rng, 1.0, 2500) * 0.85, 0.45)
    mixin(y, tube_ring(rng, 0.25, 0.08), 0.45)
    return y


def rest(seed):
    rng = np.random.default_rng(seed)
    y = np.zeros(int(0.45 * SR))
    mixin(y, thud(rng, 150, 0.18, 1.0), 0.02)
    n = int(0.12 * SR)
    squeak = bandnoise(n, 650, 1000, rng) * env(n, 0.01, 0.04) * 0.25
    mixin(y, squeak, 0.05)
    mixin(y, ring(int(0.06 * SR), [3800, 6100], [0.01, 0.006], [0.2, 0.1], rng), 0.03)  # sling swivel tick
    mixin(y, bandnoise(int(0.25 * SR), 300, 3000, rng) * env(int(0.25 * SR), 0.03, 0.06) * 0.12, 0.0)  # sleeve rustle
    return y


def lift(seed):
    rng = np.random.default_rng(seed)
    y = np.zeros(int(0.38 * SR))
    n = int(0.08 * SR)
    mixin(y, bandnoise(n, 500, 900, rng) * env(n, 0.005, 0.02) * 0.35, 0.0)
    mixin(y, bandnoise(int(0.3 * SR), 300, 3500, rng) * env(int(0.3 * SR), 0.04, 0.07) * 0.3, 0.02)
    mixin(y, thud(rng, 220, 0.05, 0.2), 0.0)
    return y


def main():
    finish(deploy(1), 'deploy1')
    finish(deploy(2), 'deploy2')
    finish(fold(3), 'fold1')
    finish(fold(4), 'fold2')
    finish(adjust(5), 'adjust1')
    finish(adjust(6), 'adjust2')
    finish(rest(7), 'rest1', 0.6)
    finish(rest(8), 'rest2', 0.6)
    finish(lift(9), 'lift1', 0.5)
    print('ok')


if __name__ == '__main__':
    main()
