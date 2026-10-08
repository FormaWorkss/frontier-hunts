#!/usr/bin/env python3
"""[1.2.0] Synthesised toboggan sounds (original, generated here; numpy -> wav -> ogg via ffmpeg).

python3 tools/sled/sled_audio.py [repo_root]

  sounds/ride/sled_glide.ogg   seamless loop: wooden runners hissing over packed snow, the deck humming, a soft
                               crystalline grain (played louder and higher with speed)
  sounds/ride/sled_carve.ogg   seamless loop: the snow sheeting off the runners in a hard turn (a breathier spray)
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy import signal
from scipy.io import wavfile

SR = 44100
ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/sounds/ride')
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(1190)


def bp(x, lo, hi, order=2):
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], 'band')
    return signal.lfilter(b, a, x)


def lp(x, f, order=2):
    b, a = signal.butter(order, f / (SR / 2), 'low')
    return signal.lfilter(b, a, x)


def loop(x, xfade=0.6):
    """make x loop seamlessly: crossfade its tail into its head (equal power)"""
    n = int(xfade * SR)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    t = np.linspace(0, np.pi / 2, n)
    mixed = tail * np.cos(t) + head * np.sin(t)
    return np.concatenate([mixed, body])


def save(name, x, peak=0.8):
    x = np.asarray(x, float)
    x -= x.mean()
    x = x / (np.max(np.abs(x)) + 1e-9) * peak
    tmp = tempfile.mktemp(suffix='.wav')
    wavfile.write(tmp, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(tmp)


def glide(seconds=4.6):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    # the hiss: broadband noise shaped like snow under a runner (bright, with a softer low body)
    hiss = bp(rng.standard_normal(n), 1800, 7500) * 0.55 + bp(rng.standard_normal(n), 500, 1800) * 0.35
    # slow swells as the runners cross firmer and softer snow
    swell = 0.78 + 0.14 * np.sin(2 * np.pi * 0.37 * t + 1.1) + 0.08 * np.sin(2 * np.pi * 0.91 * t)
    # the deck: a low wooden hum and rumble from the bumps
    rumble = lp(rng.standard_normal(n), 140, 3) * 2.2
    hum = 0.05 * np.sin(2 * np.pi * 92 * t + 0.3 * np.sin(2 * np.pi * 3 * t))
    # crystalline grain: tiny soft ticks as the crust breaks
    grain = np.zeros(n)
    for i in rng.integers(0, n - 400, int(seconds * 140)):
        k = np.arange(300)
        grain[i:i + 300] += rng.uniform(0.2, 1.0) * np.exp(-k / rng.uniform(25, 70)) * rng.standard_normal(300)
    grain = bp(grain, 2500, 9000) * 0.35
    x = hiss * swell + rumble * 0.35 + hum + grain
    return loop(x)


def carve(seconds=3.4):
    n = int(seconds * SR)
    t = np.arange(n) / SR
    spray = bp(rng.standard_normal(n), 900, 5200) * (0.75 + 0.25 * np.sin(2 * np.pi * 0.6 * t))
    scrape = bp(rng.standard_normal(n), 300, 900) * 0.4
    return loop(spray + scrape)


if __name__ == '__main__':
    save('sled_glide', glide())
    save('sled_carve', carve(), peak=0.7)
    print('wrote', OUT)
