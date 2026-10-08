#!/usr/bin/env python3
"""[hunts] Original synthesized duck call (a reed mallard call blown as a hail call: a long first quack, then a
descending, quickening run of shorter ones). Mono 44.1 kHz ogg, 3 variants.

python3 tools/hunts/duck_call.py <repo> [preview.png]

Per quack: a band-limited buzzy source (harmonics ~1/k^0.75) on a rise-and-fall pitch contour with jitter, a reed
rasp (period doubling), nasal formant resonators (~0.8 / 1.25 / 2.7 kHz), a fast attack and a clipped "-ck" ending
with a little breath noise; a faint open-marsh reflection on the whole call.
"""
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np
from scipy.signal import lfilter

SR = 44100
RNG = np.random.default_rng(11)


def resonator(x, f, bw):
    r = np.exp(-np.pi * bw / SR)
    th = 2 * np.pi * f / SR
    a = [1.0, -2 * r * np.cos(th), r * r]
    b = [(1 - r) * np.sqrt(1 - 2 * r * np.cos(2 * th) + r * r)]
    return lfilter(b, a, x)


def quack(dur, f0, rise=1.12, fall=0.72, rasp=0.35, bright=1.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = t / dur
    # pitch: quick rise to the peak at ~25 %, then a fall toward the "-ck"
    contour = np.where(u < 0.25, 1.0 + (rise - 1.0) * (u / 0.25), rise - (rise - fall) * (np.maximum(u - 0.25, 0.0) / 0.75) ** 1.3)
    jitter = 1.0 + 0.012 * np.convolve(RNG.standard_normal(n), np.ones(220) / 220, mode='same') * 8
    f = f0 * contour * jitter
    ph = 2 * np.pi * np.cumsum(f) / SR
    src = np.zeros(n)
    kmax = int(7500 / (f0 * rise))
    for k in range(1, kmax + 1):
        src += np.sin(k * ph) / k ** 0.75
    # reed rasp: period doubling -> subharmonic growl
    src *= 1.0 + rasp * np.sin(ph / 2.0)
    src += 0.06 * RNG.standard_normal(n)
    y = 0.9 * resonator(src, 820, 260) + 1.25 * bright * resonator(src, 1250, 330) + 0.7 * bright * resonator(src, 2700, 520)
    y += 0.15 * resonator(src, 4200, 900)
    # envelope: 6 ms attack, slight swell, then a clipped close
    env = np.minimum(1.0, t / 0.006)
    env *= 0.85 + 0.15 * np.sin(np.pi * np.minimum(1.0, u / 0.5))
    close = np.clip((1.0 - u) / 0.12, 0.0, 1.0) ** 1.5
    env *= close
    y *= env
    # breathy "-ck" click at the end
    ck = int(0.012 * SR)
    burst = RNG.standard_normal(ck) * np.linspace(1, 0, ck) ** 2 * 0.35
    burst = resonator(burst, 3000, 1500)
    y[-ck:] += burst[: len(y[-ck:])]
    return y


def hail(count, f0, first, tempo, seed):
    global RNG
    RNG = np.random.default_rng(seed)
    parts = []
    gap0 = 0.12 * tempo
    for i in range(count):
        k = i / max(1, count - 1)
        dur = first if i == 0 else (0.20 - 0.07 * k) * tempo
        f = f0 * (1.0 - 0.16 * k) * (1.04 if i == 0 else 1.0)
        q = quack(dur, f, rise=1.14 if i == 0 else 1.10, fall=0.74, rasp=0.32 + 0.1 * k, bright=1.0 - 0.15 * k)
        q *= (1.0 if i == 0 else 0.92 - 0.30 * k)
        parts.append(q)
        gap = gap0 * (1.0 - 0.35 * k) * (1.0 + 0.1 * RNG.standard_normal())
        parts.append(np.zeros(int(max(0.05, gap) * SR)))
    x = np.concatenate([np.zeros(int(0.02 * SR))] + parts + [np.zeros(int(0.25 * SR))])
    # open marsh: a couple of weak, damped reflections
    out = x.copy()
    for d, g in ((0.043, 0.16), (0.091, 0.09), (0.17, 0.05)):
        k = int(d * SR)
        out[k:] += g * resonator(x[:-k], 1500, 2500)
    return out


def finish(x, peak=0.8):
    x = x - np.mean(x)
    fade = int(0.05 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def write_ogg(path, x):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        tmp = tf.name
    with wave.open(tmp, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', path], check=True)
    os.unlink(tmp)


def main(repo, preview=None):
    out = os.path.join(repo, 'patch/assets/frontierhunts/sounds/hunts')
    os.makedirs(out, exist_ok=True)
    calls = [hail(6, 470, 0.36, 1.0, 1), hail(5, 500, 0.32, 0.92, 2), hail(7, 455, 0.40, 1.06, 3)]
    for i, c in enumerate(calls):
        write_ogg(os.path.join(out, 'duck_call_%d.ogg' % i), finish(c))
    if preview:
        from PIL import Image
        from scipy.signal import spectrogram
        rows = []
        for c in calls:
            f, t, S = spectrogram(finish(c), SR, nperseg=1024, noverlap=768)
            S = 10 * np.log10(S[f < 6000] + 1e-10)
            S = np.clip((S - S.max() + 70) / 70, 0, 1)[::-1]
            rows.append((S * 255).astype(np.uint8))
        w = max(r.shape[1] for r in rows)
        img = np.zeros((sum(r.shape[0] for r in rows) + 4 * len(rows), w), np.uint8)
        y = 0
        for r in rows:
            img[y:y + r.shape[0], :r.shape[1]] = r
            y += r.shape[0] + 4
        Image.fromarray(img).resize((w * 2, img.shape[0])).save(preview)
    print('wrote', len(calls), 'duck calls')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.', sys.argv[2] if len(sys.argv) > 2 else None)
