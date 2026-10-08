#!/usr/bin/env python3
"""[phone] Synthesises the Field Phone's sounds (UI, games, alarm) to .ogg and writes the sounds.json fragment.

python3 tools/phone/audio.py <repo>
  -> patch/assets/frontierhunts/sounds/phone/<name>.ogg
  -> patch/_merge/assets/frontierhunts/sounds.json/zzzzzzzz_phone.json   (events frontierhunts:phone.<name>)
Every name matches phone.client.ui.PhoneActions.Sfx (lower case).
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
SR = 44100
rng = np.random.default_rng(11)
OUT = os.path.join(R, 'patch', 'assets', 'frontierhunts', 'sounds', 'phone')
os.makedirs(OUT, exist_ok=True)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a=0.004, d=0.1, sustain=0.0):
    x = np.arange(n) / SR
    e = np.minimum(1.0, x / max(a, 1e-4)) * (sustain + (1 - sustain) * np.exp(-np.maximum(0, x - a) / max(d, 1e-4)))
    tail = min(n, int(0.006 * SR))
    e[-tail:] *= np.linspace(1, 0, tail)
    return e


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def bandnoise(sec, lo, hi):
    n = int(SR * sec)
    spec = np.fft.rfft(rng.normal(0, 1, n))
    f = np.fft.rfftfreq(n, 1 / SR)
    spec[(f < lo) | (f > hi)] = 0
    y = np.fft.irfft(spec, n)
    return y / (np.abs(y).max() + 1e-9)


def tone(freq, sec, kind='sine', a=0.004, d=0.2, harmonics=None, vib=0.0):
    x = t(sec)
    ph = 2 * np.pi * np.cumsum(np.full(len(x), freq) * (1 + vib * np.sin(2 * np.pi * 5.5 * x))) / SR
    if harmonics:
        y = sum(w * np.sin(ph * (k + 1)) for k, w in enumerate(harmonics))
    elif kind == 'tri':
        y = 2 / np.pi * np.arcsin(np.sin(ph))
    else:
        y = np.sin(ph)
    return y * env(len(x), a, d)


def marimba(freq, sec=0.35):
    return tone(freq, sec, harmonics=[1.0, 0.0, 0.0, 0.35, 0.0, 0.0, 0.0, 0.0, 0.0, 0.12], d=sec / 3.0)


def bell(freq, sec=0.5):
    return tone(freq, sec, harmonics=[1.0, 0.45, 0.0, 0.2, 0.0, 0.08], a=0.002, d=sec / 2.5)


def place(total, parts):
    out = np.zeros(int(SR * total))
    for start, sig in parts:
        i = int(SR * start)
        j = min(len(out), i + len(sig))
        out[i:j] += sig[:j - i]
    return out


def click(sec=0.02, freq=2400, noise=0.5):
    x = t(sec)
    y = np.sin(2 * np.pi * freq * x) * (1 - noise) + rng.normal(0, 1, len(x)) * noise
    return y * env(len(x), 0.0005, sec / 5)


def knock(freq=420, sec=0.18, weight=1.0):
    n = bandnoise(sec, freq * 0.5, freq * 3.0) * env(int(SR * sec), 0.001, 0.025)
    b = tone(freq, sec, harmonics=[1.0, 0.3, 0.15], a=0.001, d=0.04 * weight)
    return 0.6 * n + 0.8 * b


def note(n):
    return 440.0 * 2 ** ((n - 69) / 12.0)


S = {}
S['tap'] = click(0.03, 2200, 0.35) * 0.6
S['key'] = click(0.022, 3200, 0.5) * 0.45
S['open'] = place(0.32, [(0, bell(note(76), 0.25) * 0.5), (0.07, bell(note(83), 0.25) * 0.5)])
S['close'] = place(0.32, [(0, bell(note(83), 0.22) * 0.45), (0.07, bell(note(76), 0.25) * 0.45)])
S['back'] = place(0.12, [(0, click(0.025, 1500, 0.4) * 0.5), (0.03, tone(note(72), 0.08, d=0.03) * 0.25)])
S['toggle'] = place(0.09, [(0, click(0.02, 2600, 0.3) * 0.5), (0.045, click(0.02, 1900, 0.3) * 0.45)])
S['notify'] = place(0.7, [(0, marimba(note(84))), (0.09, marimba(note(88))), (0.18, marimba(note(91), 0.5))]) * 0.45
S['error'] = place(0.32, [(0, tone(196, 0.12, harmonics=[1, 0, 0.3, 0, 0.12], d=0.08)), (0.15, tone(185, 0.14, harmonics=[1, 0, 0.3, 0, 0.12], d=0.1))]) * 0.5
S['success'] = place(0.6, [(0, marimba(note(79))), (0.07, marimba(note(83))), (0.14, marimba(note(86))), (0.21, bell(note(91), 0.4))]) * 0.4
sh = bandnoise(0.06, 1500, 9000) * env(int(SR * 0.06), 0.0005, 0.01)
S['shutter'] = place(0.2, [(0, sh * 0.7), (0.075, sh * 0.5 + click(0.06, 900, 0.6)[:len(sh)] * 0.2)])
S['chess_move'] = knock(380, 0.2) * 0.7
S['chess_capture'] = place(0.3, [(0, knock(330, 0.2, 1.5) * 0.8), (0.06, knock(460, 0.18) * 0.5)])
S['chess_check'] = place(0.5, [(0, knock(380, 0.2) * 0.6), (0.08, bell(note(88), 0.4) * 0.35)])
roll = np.zeros(int(SR * 0.7))
for k in range(26):
    st = rng.uniform(0, 0.55) * (0.6 + 0.4 * k / 26)
    c = knock(rng.uniform(700, 1400), 0.06, 0.3) * rng.uniform(0.2, 0.6) * (1 - st / 0.75)
    roll = place(0.7, [(0, roll), (st, c)])
S['dice_roll'] = roll / (np.abs(roll).max() + 1e-9) * 0.7
S['dice_hold'] = knock(900, 0.08, 0.4) * 0.55
S['dice_score'] = place(0.6, [(0, bell(note(88), 0.45) * 0.4), (0.06, bell(note(95), 0.45) * 0.3)])
# shotgun: a hard transient, a low thump and a rolling tail
shot = bandnoise(0.6, 60, 7000) * env(int(SR * 0.6), 0.0008, 0.09)
thump = tone(70, 0.3, a=0.001, d=0.06)
S['flush_shot'] = place(0.65, [(0, shot * 0.9 + np.pad(thump, (0, len(shot) - len(thump))) * 0.8),
                               (0.12, bandnoise(0.45, 80, 900) * env(int(SR * 0.45), 0.02, 0.15) * 0.25)])
S['flush_hit'] = place(0.4, [(0, bandnoise(0.12, 300, 3000) * env(int(SR * 0.12), 0.002, 0.03) * 0.6),
                             (0.05, bell(note(93), 0.3) * 0.25)])
# wings: noise chopped by a fast flutter that slows down
n = int(SR * 0.7)
x = np.arange(n) / SR
flut = 0.5 + 0.5 * np.sin(2 * np.pi * np.cumsum(np.linspace(22, 12, n)) / SR)
S['flush_flush'] = bandnoise(0.7, 200, 2500) * flut ** 2 * env(n, 0.01, 0.35) * 0.7
S['flush_reload'] = place(0.4, [(0, bandnoise(0.12, 400, 4000) * env(int(SR * 0.12), 0.02, 0.05) * 0.4),
                                (0.16, knock(1200, 0.08, 0.4) * 0.6), (0.24, knock(800, 0.1, 0.5) * 0.7)])
# hawk: a falling, rough scream
x = t(0.75)
f = np.linspace(2900, 1900, len(x)) * (1 + 0.03 * np.sin(2 * np.pi * 38 * x))
ph = 2 * np.pi * np.cumsum(f) / SR
S['flush_warn'] = (np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.15 * rng.normal(0, 1, len(x))) * env(len(x), 0.03, 0.3) * 0.35
S['win'] = place(1.1, [(0, marimba(note(72))), (0.1, marimba(note(76))), (0.2, marimba(note(79))),
                       (0.3, bell(note(84), 0.7)), (0.3, bell(note(88), 0.7) * 0.6)]) * 0.4
S['lose'] = place(0.9, [(0, marimba(note(72), 0.4)), (0.16, marimba(note(68), 0.4)), (0.32, marimba(note(63), 0.6))]) * 0.4
S['message_in'] = place(0.5, [(0, bell(note(86), 0.3) * 0.45), (0.11, bell(note(93), 0.35) * 0.4)])
sw = bandnoise(0.22, 800, 6000) * np.sin(np.linspace(0, np.pi, int(SR * 0.22))) ** 2
S['message_out'] = place(0.3, [(0, sw * 0.35), (0.12, tone(note(91), 0.12, d=0.04) * 0.15)])
S['unlock'] = place(0.4, [(0, click(0.025, 1800, 0.3) * 0.5), (0.03, bell(note(88), 0.3) * 0.25), (0.08, bell(note(95), 0.3) * 0.2)])
beep = tone(880, 0.09, harmonics=[1, 0, 0.25], a=0.002, d=1.0) * env(int(SR * 0.09), 0.002, 1.0, 1.0)
S['alarm'] = place(1.3, [(0.0, beep), (0.14, beep), (0.28, beep), (0.42, beep)]) * 0.5

frag = {}
with tempfile.TemporaryDirectory() as tmp:
    for name, sig in S.items():
        sig = np.asarray(sig, float)
        peak = np.abs(sig).max()
        if peak > 0.95:
            sig = sig / peak * 0.95
        pcm = (sig * 32767).astype(np.int16)
        wav = os.path.join(tmp, name + '.wav')
        with wave.open(wav, 'wb') as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, name + '.ogg')], check=True)
        frag['phone.' + name] = {'sounds': [{'name': 'frontierhunts:phone/' + name}]}
fd = os.path.join(R, 'patch', '_merge', 'assets', 'frontierhunts', 'sounds.json')
os.makedirs(fd, exist_ok=True)
with open(os.path.join(fd, 'zzzzzzzz_phone.json'), 'w') as f:
    json.dump(frag, f, indent=1)
print('wrote', len(S), 'sounds')
