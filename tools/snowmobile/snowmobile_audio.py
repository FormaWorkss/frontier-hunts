#!/usr/bin/env python3
"""[1.2.5] Snowmobile sounds, synthesised here from scratch (numpy -> wav -> ogg via ffmpeg). Original work.

    python3 tools/snowmobile/snowmobile_audio.py [repo_root]

  sounds/ride/snowmobile_idle.ogg     seamless loop: a two-stroke twin burbling at idle (uneven firing, ring-ding)
  sounds/ride/snowmobile_engine.ogg   seamless loop: the same engine on the pipe (pitched up in game with the revs)
  sounds/ride/snowmobile_track.ogg    seamless loop: the track: lug slap, chaincase whine, snow hiss
All loops are built circularly (FFT convolution), so they repeat without a click.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

SR = 44100
ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/sounds/ride')
rng = np.random.default_rng(850)


def save(name, x, peak=0.8):
    x = np.asarray(x, float)
    x = x - x.mean()
    x = x / (np.abs(x).max() + 1e-9) * peak
    tmp = tempfile.mktemp(suffix='.wav')
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    os.makedirs(OUT, exist_ok=True)
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def cconv(x, h):
    """circular convolution: the loop's tail wraps onto its head"""
    n = len(x)
    hh = np.zeros(n)
    hh[:min(n, len(h))] = h[:n]
    return np.real(np.fft.ifft(np.fft.fft(x) * np.fft.fft(hh)))


def cband(x, lo, hi):
    """circular band-pass (brick wall with soft shoulders) in the frequency domain"""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    X = np.fft.rfft(x)
    g = 1 / (1 + ((lo / np.maximum(f, 1e-3)) ** 4)) / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * g, n)


def pulse(f_res, tau, n=4096, noise_amt=0.6, seed=0):
    t = np.arange(n) / SR
    r = np.random.default_rng(seed)
    p = np.exp(-t / tau) * (np.sin(2 * np.pi * f_res * t) + 0.5 * np.sin(2 * np.pi * f_res * 2.03 * t + 0.4))
    p += noise_amt * r.normal(0, 1, n) * np.exp(-t / (tau * 0.6))
    return p


def engine(f_fire, dur, jitter, amp_jit, res, tau, roar, seed):
    """f_fire: power pulses per second (two cylinders); a whole number of pulses in the loop"""
    r = np.random.default_rng(seed)
    n_p = int(round(dur * f_fire))
    n = int(round(n_p * SR / f_fire))
    x = np.zeros(n)
    period = n / n_p
    for i in range(n_p):
        at = int((i + r.normal(0, jitter)) * period) % n
        cyl = i % 2
        x[at] += (1.0 + (0.18 if cyl else -0.1)) * (1 + r.normal(0, amp_jit))
    body = cconv(x, pulse(res, tau, seed=seed))
    # the expansion chamber "ring": a narrow resonance excited by the pulses, plus a ding an octave up
    ring = cband(cconv(x, pulse(res * 1.9, tau * 1.8, noise_amt=0.2, seed=seed + 1)), res * 1.5, res * 2.6)
    # intake / exhaust roar: noise shaped by the firing
    nz = r.normal(0, 1, n)
    env = cconv(x, np.exp(-np.arange(2048) / SR / 0.006))
    roar_s = cband(nz * (0.3 + env / (env.max() + 1e-9)), 180, 2400)
    return body + 0.55 * ring + roar * roar_s


def track(dur=2.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    # lugs slapping the snow / the slide rails: 48 per second (fits the loop exactly)
    f_lug = 48.0
    x = np.zeros(n)
    k = int(round(dur * f_lug))
    for i in range(k):
        x[int(i * n / k)] += 1.0 + rng.normal(0, 0.15)
    slap = cconv(x, pulse(160, 0.004, noise_amt=1.2, seed=5))
    # chaincase / clutch whine (whole cycles in the loop)
    f_w = round(420 * dur) / dur
    whine = 0.25 * np.sin(2 * np.pi * f_w * t) + 0.08 * np.sin(2 * np.pi * 2 * f_w * t)
    # snow hiss and crunch
    hiss = cband(rng.normal(0, 1, n), 1500, 9000) * 0.8
    crunch = cband(rng.normal(0, 1, n) * (rng.random(n) < 0.004), 300, 3000) * 3
    return slap + whine + hiss + crunch


def main():
    # idle: about 1500 rpm on a twin = 50 pulses/s, lumpy
    save('snowmobile_idle', engine(50.0, 2.0, 0.05, 0.25, 210, 0.012, 0.25, 1), 0.75)
    # on the pipe: 120 pulses/s at pitch 1.0 (3600 rpm); in game pitch 0.6..2.0
    save('snowmobile_engine', engine(120.0, 2.0, 0.01, 0.06, 380, 0.007, 0.5, 2), 0.8)
    save('snowmobile_track', track(), 0.7)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
