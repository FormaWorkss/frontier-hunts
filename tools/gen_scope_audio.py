#!/usr/bin/env python3
"""Synthesizes the riflescope power-ring sounds (original, procedural) -> patch/assets/frontierhunts/sounds/optic/*.ogg

  zoom_click_0/1 : one detent of the magnification ring - a tiny metal-on-metal tick: a 1-2 ms broadband
                   transient, two short ring modes of the steel ring (~3.4 / ~5.6 kHz) and a soft body knock
  zoom_stop      : the ring reaching its end stop - duller, lower, a little longer, no bright tick
"""
import os, subprocess, sys
import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), '..', 'patch', 'assets', 'frontierhunts', 'sounds', 'optic')


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-6), 0, 1)
    return a * np.exp(-t / decay)


def bandnoise(n, lo, hi, rng):
    x = rng.standard_normal(n)
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    spec *= ((f > lo) & (f < hi)) * 1.0
    y = np.fft.irfft(spec, n)
    return y / (np.max(np.abs(y)) + 1e-9)


def click(seed, stop=False):
    rng = np.random.default_rng(seed)
    n = int(SR * (0.085 if stop else 0.06))
    t = np.arange(n) / SR
    out = np.zeros(n)
    if not stop:
        # the tick: a very short bright noise burst
        out += bandnoise(n, 2500, 14000, rng) * env(n, 0.0002, 0.0011) * 0.9
        # ring modes, slightly detuned per variant
        for f, a, d in ((3400 + seed * 37, 0.32, 0.007), (5650 - seed * 53, 0.2, 0.0045), (8200, 0.08, 0.0025)):
            out += np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * env(n, 0.0003, d) * a
        # body knock
        out += np.sin(2 * np.pi * 420 * t) * env(n, 0.0006, 0.006) * 0.18
        # a second, quieter detent bounce 4-6 ms later
        k = int(SR * (0.0042 + 0.0011 * seed))
        out[k:] += (bandnoise(n - k, 3000, 12000, rng) * env(n - k, 0.0002, 0.0007) * 0.25)
    else:
        out += bandnoise(n, 600, 5000, rng) * env(n, 0.0004, 0.0035) * 0.7
        for f, a, d in ((1850, 0.3, 0.012), (2950, 0.16, 0.007)):
            out += np.sin(2 * np.pi * f * t) * env(n, 0.0005, d) * a
        out += np.sin(2 * np.pi * 230 * t) * env(n, 0.001, 0.012) * 0.3
    # fade tail, normalise to a modest level (the game volume does the rest)
    out *= np.clip((n - np.arange(n)) / (SR * 0.01), 0, 1)
    out = out / (np.max(np.abs(out)) + 1e-9) * 0.8
    pad = np.zeros(int(SR * 0.004))
    return np.concatenate([out, pad])


def write(name, data):
    os.makedirs(OUT, exist_ok=True)
    wav = os.path.join(OUT, name + '.wav')
    ogg = os.path.join(OUT, name + '.ogg')
    import wave
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(data, -1, 1) * 32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '6', ogg], check=True)
    os.remove(wav)
    print(ogg, os.path.getsize(ogg))


if __name__ == '__main__':
    write('zoom_click_0', click(0))
    write('zoom_click_1', click(1))
    write('zoom_stop', click(2, stop=True))
