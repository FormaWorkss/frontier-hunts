#!/usr/bin/env python3
"""[guns3] Procedural (original) water-impact sounds for bullets and shot -> patch/assets/frontierhunts/sounds/guns/.

Physics-flavoured layers, mixed per size:
  crack    a 2-4 ms broadband tick: the round slapping the surface
  thump    the air cavity collapsing: a damped low tone gliding down (the deep "whoomp" that makes it feel heavy)
  spray    the crown and column going up: band-limited hiss that swells and dies away
  slap     the surface itself being struck: a short dense burst ([gear.20] replaces the bubble plinks and the dripping
           tail, which sounded wrong)

  water_hit_small_[0-2]    pistol / revolver / 5.56: sharp, bright, quick
  water_hit_large_[0-2]    .30-30 / .308: a heavy whoomp, a tall hissing column and a long fall of drops
  water_hit_shot_[0-2]     a shotgun charge: a dozen hits smeared over ~50 ms, a broad wall of spray, dense rain
usage: python3 tools/gen_water_impact_audio.py [repo root]
"""
import os, subprocess, sys, wave
import numpy as np

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(R, 'patch/assets/frontierhunts/sounds/guns')
SR = 44100


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-5), 0, 1)
    return a * np.exp(-np.maximum(0, t - attack) / decay)


def bandnoise(n, lo, hi, rng):
    x = rng.standard_normal(n)
    f = np.fft.rfft(x)
    fr = np.fft.rfftfreq(n, 1 / SR)
    mask = (fr >= lo) & (fr <= hi)
    soft = np.exp(-((np.maximum(0, lo - fr)) / (lo * 0.3 + 1)) ** 2) * np.exp(-((np.maximum(0, fr - hi)) / (hi * 0.3 + 1)) ** 2)
    y = np.fft.irfft(f * np.where(mask, 1.0, soft), n)
    return y / (np.std(y) + 1e-9)


def add(out, start, sig):
    s = int(start * SR)
    if s >= len(out):
        return
    m = min(len(sig), len(out) - s)
    out[s:s + m] += sig[:m]


def bubble(f0, tau, amp, rng):
    n = int(SR * tau * 6)
    t = np.arange(n) / SR
    f = f0 * (1 + 0.18 * t / tau)  # a collapsing bubble's pitch rises
    ph = 2 * np.pi * np.cumsum(f) / SR + rng.uniform(0, 6.28)
    return np.sin(ph) * np.exp(-t / tau) * env(n, 0.0008, 10) * amp


def thump(f_hi, f_lo, dur, amp, rng):
    n = int(SR * dur * 4)
    t = np.arange(n) / SR
    f = f_lo + (f_hi - f_lo) * np.exp(-t / (dur * 0.35))
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = np.sin(ph) + 0.35 * np.sin(2 * ph + 0.5)
    body = bandnoise(n, 40, 420, rng) * 0.6
    return (tone + body) * env(n, 0.004, dur) * amp


def crack(amp, rng, length=0.004):
    n = int(SR * 0.03)
    return bandnoise(n, 2500, 16000, rng) * env(n, 0.0002, length) * amp


def spray(dur, swell, lo, hi, amp, rng):
    n = int(SR * (dur * 3 + swell))
    return bandnoise(n, lo, hi, rng) * env(n, swell, dur) * amp


def rain(start, length, count, fmin, fmax, amp, rng, out):
    for k in range(count):
        u = rng.random() ** 1.7  # dense early, thinning out
        t0 = start + u * length
        f = rng.uniform(fmin, fmax)
        add(out, t0, bubble(f, rng.uniform(0.006, 0.02), amp * rng.uniform(0.3, 1.0) * (1 - 0.6 * u), rng))
        if rng.random() < 0.35:
            add(out, t0, bandnoise(int(SR * 0.02), 3000, 12000, rng) * env(int(SR * 0.02), 0.0003, 0.003) * amp * 0.25)


def slap(dur, lo, hi, amp, rng):
    """the surface being hit: a short, dense, band-limited burst (the "thwack")"""
    n = int(SR * dur * 5)
    return bandnoise(n, lo, hi, rng) * env(n, 0.0008, dur) * amp


def small(seed):
    # [gear.20] no bubbles, no dripping tail: a crack, a sharp slap and a short burst of spray
    rng = np.random.default_rng(1000 + seed)
    out = np.zeros(int(SR * 0.45))
    add(out, 0.0, crack(0.75, rng))
    add(out, 0.001, slap(0.035, 500, 4500, 0.9, rng))
    add(out, 0.002, thump(220, 110, 0.05, 0.45, rng))
    add(out, 0.006, spray(0.08, 0.01, 1800, 9000, 0.35, rng))
    return out


def large(seed):
    rng = np.random.default_rng(2000 + seed)
    out = np.zeros(int(SR * 0.7))
    add(out, 0.0, crack(0.9, rng, 0.005))
    add(out, 0.001, slap(0.05, 350, 4000, 1.0, rng))
    add(out, 0.002, thump(150, 55, 0.12, 1.1, rng))
    add(out, 0.008, spray(0.13, 0.02, 1200, 8500, 0.5, rng))
    return out


def shot(seed):
    rng = np.random.default_rng(3000 + seed)
    out = np.zeros(int(SR * 0.7))
    for k in range(12):
        t0 = rng.random() ** 1.5 * 0.035
        add(out, t0, crack(rng.uniform(0.3, 0.6), rng, 0.003))
        add(out, t0, slap(rng.uniform(0.02, 0.035), 600, 5000, rng.uniform(0.35, 0.6), rng))
    add(out, 0.003, thump(140, 60, 0.11, 0.9, rng))
    add(out, 0.008, spray(0.16, 0.02, 1000, 9000, 0.6, rng))
    return out


def finish(x, peak):
    fade = int(SR * 0.08)
    x[-fade:] *= np.linspace(1, 0, fade)
    x = np.tanh(x / (np.max(np.abs(x)) + 1e-9) * 1.4)  # gentle saturation: punch without clipping
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + '.wav')
    ogg = os.path.join(OUT, name + '.ogg')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(data, -1, 1) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '6', ogg], check=True)
    os.remove(wav)
    print(ogg, os.path.getsize(ogg))


for s in range(3):
    write(f'water_hit_small_{s}', finish(small(s), 0.85))
    write(f'water_hit_large_{s}', finish(large(s), 0.95))
    write(f'water_hit_shot_{s}', finish(shot(s), 0.95))
