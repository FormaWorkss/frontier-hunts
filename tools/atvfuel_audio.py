#!/usr/bin/env python3
"""[atvfuel] Synthesised sounds for the ATV fuel / rig workstream (original; the stall/sputter layers resample the
mod's own atv_engine loop so the dying engine has the same voice as the running one).
usage: python3 tools/atvfuel_audio.py <atv_engine.ogg> [repo_root]"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
SRC = sys.argv[1]
ROOT = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/sounds/ride')
rng = np.random.default_rng(1234)


def load(path):
    tmp = tempfile.mktemp(suffix='.wav')
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', path, '-ac', '1', '-ar', str(SR), tmp], check=True)
    sr, d = wavfile.read(tmp)
    os.remove(tmp)
    return d.astype(np.float64) / 32768.0


def save(name, x, peak=0.85):
    x = np.asarray(x, float)
    x = x - np.mean(x)
    fade = min(len(x) // 10, int(0.012 * SR))
    x[:int(0.002 * SR)] *= np.linspace(0, 1, int(0.002 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    tmp = tempfile.mktemp(suffix='.wav')
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def t(dur):
    return np.arange(int(dur * SR)) / SR


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def hp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'high')
    return signal.lfilter(b, a, x)


ENGINE = load(SRC)


def resample_loop(pitch, start=0.0):
    """Play the engine loop with a time-varying pitch curve (per output sample)."""
    ph = start * SR + np.cumsum(pitch)
    i = np.floor(ph).astype(int)
    f = ph - i
    n = len(ENGINE)
    return ENGINE[i % n] * (1 - f) + ENGINE[(i + 1) % n] * f


def pop(dur=0.05, bright=1.0):
    n = int(dur * SR)
    e = np.exp(-np.arange(n) / (SR * 0.012))
    x = rng.standard_normal(n) * e
    x = lp(x, 2500 * bright) + 0.6 * np.sin(2 * np.pi * 70 * np.arange(n) / SR) * e
    return x


def place(buf, x, at):
    i = int(at * SR)
    j = min(len(buf), i + len(x))
    buf[i:j] += x[:j - i]


def stall():
    d = 1.9
    tt = t(d)
    p = 1.0 - 0.62 * np.clip(tt / 1.5, 0, 1) ** 0.8
    x = resample_loop(p * 0.95, 0.3)
    env = np.clip(1.0 - (tt / 1.55) ** 2.2, 0, 1)
    # stumbling: dropouts as the last fuel runs out
    for c, w in ((0.32, 0.06), (0.55, 0.09), (0.83, 0.12), (1.1, 0.16)):
        env *= 1 - 0.85 * np.exp(-((tt - c) / w) ** 2 * 3)
    x = lp(x, 3500) * env
    for c in (0.36, 0.6, 0.9, 1.18):
        place(x, pop(0.07, 0.8) * 0.5, c + 0.03)
    # final shudder + clunk of the engine settling on its mounts
    n = len(t(0.35))
    clunk = np.sin(2 * np.pi * 48 * t(0.35)) * np.exp(-t(0.35) / 0.07) + lp(rng.standard_normal(n), 800) * np.exp(-t(0.35) / 0.03) * 0.5
    place(x, clunk * 0.7, 1.45)
    # cooling tick
    for c in (1.7, 1.82):
        place(x, hp(rng.standard_normal(400), 3000) * np.exp(-np.arange(400) / 60) * 0.12, c)
    return x


def sputter(seed):
    r = np.random.default_rng(seed)
    d = 0.75
    tt = t(d)
    p = 0.95 + 0.1 * np.sin(tt * 9 + seed)
    x = resample_loop(p, r.uniform(0, 1.5))
    env = np.ones_like(tt)
    gaps = sorted(r.uniform(0.08, 0.6, 3))
    for c in gaps:
        env *= 1 - 0.92 * (np.abs(tt - c) < r.uniform(0.04, 0.08))
    env = lp(env, 60, 1)
    x = lp(x, 4000) * env * np.clip(1 - (tt - 0.6) / 0.15, 0, 1)
    for c in gaps:
        place(x, pop(0.06, r.uniform(0.7, 1.2)) * r.uniform(0.6, 1.0), c + 0.05)
    return x


def crank():
    d = 1.6
    tt = t(d)
    # starter motor whine with compression strokes (~6 Hz), never catching
    comp = 0.55 + 0.45 * np.abs(np.sin(np.pi * 6.2 * tt)) ** 0.6
    f = 170 * comp * np.clip(tt / 0.08, 0.3, 1)
    ph = 2 * np.pi * np.cumsum(f) / SR
    whine = signal.sawtooth(ph) * 0.4 + np.sin(2 * ph) * 0.25 + np.sin(3.02 * ph) * 0.12
    whine = bp(whine, 120, 3000)
    rough = lp(rng.standard_normal(len(tt)), 900) * 0.4
    thump = np.zeros_like(tt)
    for k in range(int(d * 6.2)):
        place(thump, np.sin(2 * np.pi * 55 * t(0.08)) * np.exp(-t(0.08) / 0.025), k / 6.2 + 0.07)
    env = np.clip(tt / 0.04, 0, 1) * np.clip((1.38 - tt) / 0.06, 0, 1)
    x = (whine + rough) * comp * env + thump * 0.6 * env
    # solenoid clicks
    click = hp(rng.standard_normal(600), 2000) * np.exp(-np.arange(600) / 80)
    place(x, click * 0.5, 0.0)
    place(x, click * 0.35, 1.4)
    return x


def glug(seed, dur=0.32, n=3, base=320):
    r = np.random.default_rng(seed)
    x = np.zeros(len(t(dur)))
    for k in range(n):
        L = r.uniform(0.06, 0.12)
        tk = t(L)
        f0 = base * r.uniform(0.8, 1.3)
        f = f0 * (1 + 1.3 * tk / L)
        b = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tk / (L * 0.4)) * np.clip(tk / 0.004, 0, 1)
        place(x, b * r.uniform(0.6, 1.0), r.uniform(0.0, dur - L))
    stream = bp(rng.standard_normal(len(x)), 900, 5000) * 0.12 * (0.6 + 0.4 * np.sin(2 * np.pi * 11 * t(dur)))
    return x + stream


def attach():
    d = 0.8
    x = np.zeros(len(t(d)))
    # hollow plastic thud of the box seating on the rack
    tt = t(0.25)
    place(x, (np.sin(2 * np.pi * 95 * tt) + 0.5 * np.sin(2 * np.pi * 180 * tt)) * np.exp(-tt / 0.05) + lp(rng.standard_normal(len(tt)), 1200) * np.exp(-tt / 0.02) * 0.6, 0.0)
    # steel clamp clank: inharmonic partials
    tt = t(0.4)
    clank = sum(a * np.sin(2 * np.pi * f * tt) * np.exp(-tt / dcy) for f, a, dcy in ((1180, .5, .09), (1730, .35, .07), (2650, .25, .05), (3990, .15, .03)))
    place(x, clank * 0.6, 0.12)
    # ratchet strap clicks
    for k in range(5):
        c = hp(rng.standard_normal(500), 2500) * np.exp(-np.arange(500) / 70)
        place(x, c * (0.5 - k * 0.05), 0.36 + k * 0.07)
    return x


def can_fill():
    d = 1.3
    tt = t(d)
    sizzle = hp(rng.standard_normal(len(tt)), 3000) * (0.25 + 0.2 * (rng.random(len(tt)) > 0.995))
    sizzle *= np.clip(1 - tt / 1.2, 0, 1) ** 0.7 * np.clip(tt / 0.05, 0, 1)
    crackle = np.zeros_like(tt)
    for c in rng.uniform(0.0, 0.9, 25):
        place(crackle, hp(rng.standard_normal(200), 1500) * np.exp(-np.arange(200) / 30), c)
    x = sizzle + crackle * 0.4
    place(x, glug(9, 0.9, 7, 220) * 0.9, 0.2)
    # hollow can resonance
    can = bp(rng.standard_normal(len(tt)), 380, 460, 2) * np.exp(-tt / 0.6) * 0.8
    return x + can


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    save('atv_stall', stall())
    save('atv_sputter_1', sputter(1), 0.75)
    save('atv_sputter_2', sputter(2), 0.75)
    save('atv_crank', crank(), 0.75)
    for i in range(1, 4):
        save(f'fuel_pour_{i}', glug(100 + i), 0.6)
    save('rig_attach', attach(), 0.8)
    save('jerry_can_fill', can_fill(), 0.75)
    print('ok')
