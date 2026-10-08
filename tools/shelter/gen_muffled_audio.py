#!/usr/bin/env python3
"""[shelter] Muffled ("heard through walls / canvas") versions of the mod's own synthesized storm sounds.

Input: the weather workstream's original beds and gusts (patch/assets/frontierhunts/sounds/weather/*.ogg, themselves
synthesized by tools/gen_weather_audio.py). Output: patch/assets/frontierhunts/sounds/weather/muffled_*.ogg.

* Loops are filtered in the frequency domain over the whole (looping) buffer: circular filtering keeps the loop seam
  perfectly continuous.
* Filter = Butterworth-shaped low-pass magnitude (walls take the hiss and howl's top off) + a soft low-mid bump (the
  structure resonating / canvas drumming), narrower stereo (sound arrives through the walls, not from every side).
* Gust one-shots: same filter, zero-padded so the filtered tail is not cut, short fade at the end.

usage: python3 tools/shelter/gen_muffled_audio.py [repo]
"""
import os
import subprocess
import sys
import tempfile

import numpy as np

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
D = os.path.join(R, 'patch', 'assets', 'frontierhunts', 'sounds', 'weather')
SR = 44100


def load(path):
    with tempfile.NamedTemporaryFile(suffix='.f32', delete=False) as t:
        tmp = t.name
    info = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=channels', '-of', 'csv=p=0', path],
                          capture_output=True, text=True).stdout.strip()
    ch = int(info.splitlines()[0])
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', path, '-f', 'f32le', '-ar', str(SR), '-ac', str(ch), tmp], check=True)
    a = np.fromfile(tmp, dtype=np.float32).reshape(-1, ch).T.astype(np.float64)
    os.remove(tmp)
    return a


def save(path, a, q=5):
    a = np.clip(a, -1.0, 1.0).astype(np.float32)
    ch = a.shape[0]
    with tempfile.NamedTemporaryFile(suffix='.f32', delete=False) as t:
        tmp = t.name
    a.T.tofile(tmp)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', str(ch), '-i', tmp,
                    '-c:a', 'libvorbis', '-q:a', str(q), path], check=True)
    os.remove(tmp)


def response(n, fc, order, bump_hz, bump_db):
    f = np.fft.rfftfreq(n, 1.0 / SR)
    lp = 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))
    # gentle resonance bump (log-frequency gaussian), the walls / canvas themselves
    b = 10 ** (bump_db / 20.0) - 1.0
    bump = 1.0 + b * np.exp(-0.5 * (np.log2(np.maximum(f, 1.0) / bump_hz) / 0.7) ** 2)
    hp = 1.0 / np.sqrt(1.0 + (25.0 / np.maximum(f, 1e-3)) ** 4)  # no sub-sonic rumble build-up
    return lp * bump * hp


def muffle(a, fc, order, bump_hz, bump_db, width, circular):
    n = a.shape[1]
    pad = 0 if circular else int(0.6 * SR)
    m = n + pad
    h = response(m, fc, order, bump_hz, bump_db)
    out = np.zeros((a.shape[0], m))
    for c in range(a.shape[0]):
        x = np.zeros(m)
        x[:n] = a[c]
        out[c] = np.fft.irfft(np.fft.rfft(x) * h, m)
    if a.shape[0] == 2:
        mid, side = (out[0] + out[1]) * 0.5, (out[0] - out[1]) * 0.5 * width
        out = np.stack([mid + side, mid - side])
    if not circular:
        # trim trailing silence, fade the last 80 ms
        env = np.max(np.abs(out), axis=0)
        last = np.nonzero(env > 1e-4)[0]
        end = min(m, (last[-1] + 1) if len(last) else n)
        out = out[:, :end]
        fade = min(end, int(0.08 * SR))
        out[:, -fade:] *= np.linspace(1.0, 0.0, fade)
    return out


def rms(a):
    return float(np.sqrt(np.mean(a ** 2)) + 1e-12)


def make(src, dst, fc, order, bump_hz, bump_db, width, circular, level):
    a = load(os.path.join(D, src))
    y = muffle(a, fc, order, bump_hz, bump_db, width, circular)
    y *= (rms(a) * level) / rms(y)  # keep loudness comparable; the game sets the mix
    peak = np.max(np.abs(y))
    if peak > 0.95:
        y *= 0.95 / peak
    save(os.path.join(D, dst), y)
    print(f'{dst}: {y.shape[1] / SR:.2f} s, rms {rms(y):.3f} (src {rms(a):.3f}), peak {np.max(np.abs(y)):.3f}')
    return y


def seam_check(name):
    a = load(os.path.join(D, name))
    # loop continuity: jump at the seam vs typical sample-to-sample step
    step = np.median(np.abs(np.diff(a, axis=1)))
    seam = np.max(np.abs(a[:, 0] - a[:, -1]))
    print(f'  seam {name}: {seam:.4f} vs median step {step:.4f}')


if __name__ == '__main__':
    # beds (circular = seamless loops)
    make('blizzard_loop.ogg', 'muffled_blizzard_loop.ogg', fc=380, order=3, bump_hz=110, bump_db=4.0, width=0.45, circular=True, level=0.85)
    make('wind_loop.ogg', 'muffled_wind_loop.ogg', fc=340, order=3, bump_hz=95, bump_db=4.5, width=0.45, circular=True, level=0.85)
    make('dust_loop.ogg', 'muffled_dust_loop.ogg', fc=520, order=3, bump_hz=140, bump_db=3.0, width=0.45, circular=True, level=0.8)
    # rain on a roof / on canvas: brighter patter than the wind, a drum-like low-mid
    make('rain_loop.ogg', 'muffled_rain_loop.ogg', fc=1300, order=2, bump_hz=260, bump_db=5.0, width=0.6, circular=True, level=0.9)
    for i in range(3):
        make(f'gust_howl_{i}.ogg', f'muffled_gust_{i}.ogg', fc=420, order=3, bump_hz=100, bump_db=5.0, width=1.0, circular=False, level=0.9)
    for n in ('muffled_blizzard_loop.ogg', 'muffled_wind_loop.ogg', 'muffled_dust_loop.ogg', 'muffled_rain_loop.ogg'):
        seam_check(n)
