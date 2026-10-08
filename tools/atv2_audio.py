#!/usr/bin/env python3
"""[atv2] Synthesised water sounds for the ATV (original, generated here; numpy -> wav -> ogg via ffmpeg).

python3 tools/atv2_audio.py [repo_root]

  sounds/ride/atv_water_splash_{1,2,3}.ogg   ploughing into water: hull thump + sheet of water + fall-back + droplet tail
  sounds/ride/atv_water_churn.ogg            seamless loop: water churned by tyres and pushed by the hull (gurgle)
  sounds/ride/atv_water_spray.ogg            seamless loop: spray off the wheels (hiss + droplet patter)
  sounds/ride/atv_water_slosh_{1,2,3}.ogg    creeping / bogging sloshes
  sounds/ride/atv_water_flood.ogg            water in the intake: gulp, choke, rising bubbles
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/sounds/ride')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(2024)


def save(name, x, peak=0.85, fade_in=0.002, fade_out=0.012):
    x = np.asarray(x, float)
    x = x - np.mean(x)
    if fade_in:
        n = int(fade_in * SR)
        x[:n] *= np.linspace(0, 1, n)
    if fade_out:
        n = min(len(x) // 10, int(fade_out * SR))
        x[-n:] *= np.linspace(1, 0, n)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    tmp = tempfile.mktemp(suffix='.wav')
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


def noise(n):
    return rng.standard_normal(n)


def env_curve(n, attack, decay, shape=1.0):
    t = np.arange(n) / SR
    e = np.minimum(1, t / max(attack, 1e-4)) * np.exp(-np.maximum(0, t - attack) / decay)
    return e ** shape


def slow_mod(n, rate, depth, seed):
    r = np.random.default_rng(seed)
    k = max(4, int(n / SR * rate) + 4)
    pts = r.random(k)
    m = np.interp(np.linspace(0, k - 3, n), np.arange(k), pts)
    return 1 - depth + depth * m


def bubble(f0, tau, amp=1.0, rise=0.15):
    """Minnaert bubble: damped sine whose pitch rises as the bubble nears the surface."""
    n = int(tau * 6 * SR)
    t = np.arange(n) / SR
    f = f0 * (1 + rise * t / tau)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return amp * np.sin(ph) * np.exp(-t / tau) * np.minimum(1, t / 0.0008)


def place(buf, x, at):
    i = int(at * SR)
    if i >= len(buf):
        return
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i]


def bubbles(buf, t0, t1, rate, f_lo, f_hi, tau_lo, tau_hi, amp, density_env=None, seed=0):
    r = np.random.default_rng(seed)
    t = t0
    while t < t1:
        t += r.exponential(1 / rate)
        if density_env is not None and r.random() > density_env(min(1.0, (t - t0) / (t1 - t0))):
            continue
        f = np.exp(r.uniform(np.log(f_lo), np.log(f_hi)))
        place(buf, bubble(f, r.uniform(tau_lo, tau_hi), amp * r.uniform(0.3, 1.0), r.uniform(0.05, 0.4)), t)


def loopify(x, xfade):
    """Seamless loop: crossfade the tail into the head (equal power)."""
    n = int(xfade * SR)
    head, body, tail = x[:n], x[n:len(x) - n], x[len(x) - n:]
    a = np.sin(np.linspace(0, np.pi / 2, n))
    mixed = tail * np.cos(np.linspace(0, np.pi / 2, n)) + head * a
    return np.concatenate([body[: len(body)], mixed])


# ------------------------------------------------------------------ one-shots
def splash(seed, dur=1.6, size=1.0):
    r = np.random.default_rng(seed)
    n = int(dur * SR)
    out = np.zeros(n)
    # 1. hull / tyre impact: a low thump
    t = np.arange(int(0.35 * SR)) / SR
    f = 70 * size ** -0.3 * (1 - 0.35 * t / 0.35)
    thump = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.07) * 0.9
    thump += lp(noise(len(t)), 300) * np.exp(-t / 0.05) * 0.5
    place(out, thump, 0.0)
    # 2. the sheet of water thrown up: broadband, turbulent
    sheet = bp(noise(n), 250, 7000, 2) * env_curve(n, 0.012, 0.22 * size) * slow_mod(n, 40, 0.6, seed)
    sheet += hp(noise(n), 2500) * env_curve(n, 0.02, 0.12) * 0.35
    out += sheet * 0.8
    # 3. fall-back: the water landing again a moment later, softer and wider
    fb_at = r.uniform(0.28, 0.4)
    m = n - int(fb_at * SR)
    fall = bp(noise(m), 400, 6000, 2) * env_curve(m, 0.05, 0.25) * slow_mod(m, 60, 0.7, seed + 1) * 0.45
    place(out, fall, fb_at)
    # 4. droplet tail: patter of drops landing (high bubbles), thinning out
    bubbles(out, 0.12, dur - 0.1, 70 * size, 900, 4800, 0.004, 0.015, 0.22, lambda u: (1 - u) ** 1.5, seed + 2)
    # 5. big bubbles from the hull churn
    bubbles(out, 0.02, 0.5, 30, 180, 700, 0.015, 0.05, 0.35, lambda u: 1 - u, seed + 3)
    return out


def slosh(seed, dur=0.8):
    r = np.random.default_rng(seed)
    n = int(dur * SR)
    t = np.arange(n) / SR
    sw = np.sin(np.pi * np.clip(t / (dur * r.uniform(0.6, 0.85)), 0, 1)) ** 1.5
    x = bp(noise(n), 150, 1600, 2) * sw * slow_mod(n, 25, 0.7, seed)
    x += bp(noise(n), 1200, 4000, 2) * sw ** 3 * 0.25
    bubbles(x, 0.05, dur - 0.1, 22, 220, 1100, 0.01, 0.04, 0.4, lambda u: np.sin(np.pi * u), seed + 1)
    return x


def flood():
    dur = 2.2
    n = int(dur * SR)
    out = np.zeros(n)
    # gulp: the intake swallows water (low falling resonance)
    t = np.arange(int(0.45 * SR)) / SR
    f = 160 * np.exp(-t / 0.25) + 55
    g = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.18)
    g += lp(noise(len(t)), 500) * np.exp(-t / 0.1) * 0.6
    place(out, g * 0.9, 0.0)
    # choke: a couple of muffled coughs through water
    for at, a in ((0.25, 0.8), (0.55, 0.6), (0.9, 0.35)):
        m = int(0.18 * SR)
        c = lp(noise(m), 700) * env_curve(m, 0.005, 0.05) * a
        place(out, c, at)
    # boil of exhaust bubbles, then rising bubbles thinning out
    bubbles(out, 0.05, 1.2, 90, 90, 600, 0.02, 0.07, 0.5, lambda u: 1 - u * 0.6, 11)
    bubbles(out, 0.6, dur - 0.1, 25, 300, 1500, 0.01, 0.04, 0.3, lambda u: (1 - u) ** 2, 12)
    out += lp(noise(n), 400) * env_curve(n, 0.05, 0.6) * 0.25
    return out


# ------------------------------------------------------------------ loops
def churn(dur=4.0):
    n = int((dur + 1.0) * SR)
    x = lp(bp(noise(n), 70, 1100, 2), 900) * slow_mod(n, 6, 0.55, 31) * 0.8
    x += bp(noise(n), 900, 3500, 2) * slow_mod(n, 9, 0.8, 32) * 0.18
    bubbles(x, 0.0, dur + 1.0, 45, 120, 900, 0.012, 0.05, 0.45, None, 33)
    # occasional slap of the water against the body
    r = np.random.default_rng(34)
    for at in np.cumsum(r.exponential(0.45, 20)):
        if at > dur + 0.8:
            break
        m = int(0.12 * SR)
        place(x, bp(noise(m), 300, 2500) * env_curve(m, 0.006, 0.035) * r.uniform(0.3, 0.7), at)
    return loopify(x, 1.0)


def spray(dur=3.0):
    n = int((dur + 0.8) * SR)
    x = bp(noise(n), 1400, 9500, 2) * slow_mod(n, 14, 0.35, 41) * 0.55
    x += bp(noise(n), 500, 1800, 2) * slow_mod(n, 8, 0.6, 42) * 0.2
    bubbles(x, 0.0, dur + 0.8, 160, 1800, 7000, 0.002, 0.008, 0.18, None, 43)
    return loopify(x, 0.8)


if __name__ == '__main__':
    for k, (seed, dur, size) in enumerate(((101, 1.6, 1.0), (102, 1.4, 0.85), (103, 1.8, 1.2)), 1):
        save(f'atv_water_splash_{k}', splash(seed, dur, size), 0.9)
    for k, seed in enumerate((201, 202, 203), 1):
        save(f'atv_water_slosh_{k}', slosh(seed, 0.7 + 0.1 * k), 0.8)
    save('atv_water_flood', flood(), 0.9)
    save('atv_water_churn', churn(), 0.75, fade_in=0, fade_out=0)
    save('atv_water_spray', spray(), 0.7, fade_in=0, fade_out=0)
    print('done')
