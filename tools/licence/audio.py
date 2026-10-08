#!/usr/bin/env python3
"""[licence] Original synthesized sounds (numpy -> ogg via ffmpeg): licence stamp, tag punch, warden notice,
Dutch oven simmer and lid clank.  usage: python3 tools/licence/audio.py <repo>"""
import os, subprocess, sys, wave
import numpy as np

R = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = os.path.join(R, 'patch/assets/frontierhunts/sounds')
SR = 44100


def t(sec):
    return np.arange(int(sec * SR)) / SR


def lowpass(x, cut):
    a = np.exp(-2 * np.pi * cut / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def highpass(x, cut):
    return x - lowpass(x, cut)


def env(n, attack, decay):
    tt = np.arange(n) / SR
    e = np.minimum(1, tt / max(attack, 1e-4)) * np.exp(-tt / decay)
    return e


def write(name, x, gain=0.8):
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    fade = min(len(x), int(0.01 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    path = os.path.join(OUT, name + '.ogg')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path[:-4] + '.wav'
    with wave.open(tmp, 'w') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '4', path], check=True)
    os.remove(tmp)


def place(buf, x, at):
    i = int(at * SR)
    buf[i:i + len(x)] += x[:max(0, len(buf) - i)]


rng = np.random.default_rng(7)


def stamp(seed):
    r = np.random.default_rng(seed)
    out = np.zeros(int(0.45 * SR))
    n = int(0.12 * SR)
    thud = np.sin(2 * np.pi * (95 + 30 * r.random()) * t(0.12)) * env(n, 0.002, 0.03)
    body = lowpass(r.standard_normal(n), 900) * env(n, 0.001, 0.02) * 3
    place(out, thud + body, 0.01)
    rustle = highpass(r.standard_normal(int(0.3 * SR)), 2500) * env(int(0.3 * SR), 0.03, 0.08) * 0.12
    place(out, rustle, 0.06)
    return out


def punch(seed):
    r = np.random.default_rng(seed)
    out = np.zeros(int(0.35 * SR))
    for k, at in enumerate((0.0, 0.07 + 0.02 * r.random())):
        n = int(0.06 * SR)
        click = highpass(r.standard_normal(n), 3000) * env(n, 0.0005, 0.006)
        ring = sum(np.sin(2 * np.pi * f * t(0.06)) * a for f, a in ((3100 + 300 * r.random(), 0.5), (5200, 0.25), (1900, 0.3)))
        ring *= env(n, 0.0005, 0.015 if k == 0 else 0.025)
        place(out, click * 1.4 + ring * (0.6 if k == 0 else 1.0), 0.01 + at)
    return out


def warden():
    out = np.zeros(int(0.9 * SR))
    for at in (0.02, 0.17):
        n = int(0.15 * SR)
        knock = np.sin(2 * np.pi * 160 * t(0.15)) * env(n, 0.001, 0.035) + np.sin(2 * np.pi * 410 * t(0.15)) * env(n, 0.001, 0.02) * 0.6
        knock += lowpass(rng.standard_normal(n), 1500) * env(n, 0.0005, 0.008) * 2
        place(out, knock, at)
    n = int(0.45 * SR)
    paper = highpass(rng.standard_normal(n), 1800) * env(n, 0.05, 0.12) * (0.6 + 0.4 * np.sin(2 * np.pi * 13 * t(0.45)))
    place(out, paper * 0.35, 0.35)
    return out


def simmer(seed):
    r = np.random.default_rng(seed)
    dur = 2.6
    n = int(dur * SR)
    rumble = lowpass(lowpass(r.standard_normal(n), 220), 220) * 3.0
    hiss = highpass(r.standard_normal(n), 4000) * 0.025
    out = rumble * 0.25 + hiss
    for _ in range(int(26 + 10 * r.random())):
        at = r.random() * (dur - 0.1)
        bn = int((0.02 + 0.05 * r.random()) * SR)
        f0 = 280 + 700 * r.random()
        tt = np.arange(bn) / SR
        f = f0 * (1 + 2.5 * tt / (bn / SR))                       # bubbles rise in pitch as they burst
        ph = 2 * np.pi * np.cumsum(f) / SR
        pop = np.sin(ph) * env(bn, 0.002, 0.012 + 0.01 * r.random()) * (0.3 + 0.7 * r.random())
        place(out, pop, at)
    fade = int(0.25 * SR)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def lid(seed):
    r = np.random.default_rng(seed)
    out = np.zeros(int(0.8 * SR))
    n = int(0.75 * SR)
    partials = ((612, 1.0, 0.18), (1488, 0.55, 0.12), (2730, 0.35, 0.08), (3940, 0.2, 0.05), (5210, 0.1, 0.03))
    clank = sum(np.sin(2 * np.pi * f * (1 + 0.02 * r.standard_normal()) * t(0.75)) * a * env(n, 0.0008, d) for f, a, d in partials)
    clank += highpass(r.standard_normal(n), 2000) * env(n, 0.0005, 0.006) * 1.2
    place(out, clank, 0.01)
    second = clank[: int(0.4 * SR)] * 0.35
    place(out, second, 0.09 + 0.03 * r.random())
    return out


if __name__ == '__main__':
    write('licence/stamp_0', stamp(1)); write('licence/stamp_1', stamp(2))
    write('licence/punch_0', punch(3)); write('licence/punch_1', punch(4))
    write('licence/warden', warden(), 0.7)
    for i in range(3):
        write(f'campcook/simmer_{i}', simmer(10 + i), 0.55)
    write('campcook/lid_0', lid(21), 0.7); write('campcook/lid_1', lid(22), 0.7)
    print('ok')
