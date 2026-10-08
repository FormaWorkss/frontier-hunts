#!/usr/bin/env python3
"""[rutfight] Cuts single antler clacks and short grinding passages out of the mod's own antler-rattle recordings
(assets/frontierhunts/sounds/equipment/antler_rattle_*.ogg in the base jar) for the rut fight sounds:
  sounds/rutfight/clash_0..3.ogg  one hard crack each (contact, re-clash, wrench free)
  sounds/rutfight/grind_0..2.ogg  locked racks ticking and scraping while the bulls twist and shove
Usage: python3 tools/rutfight/cut_sounds.py [repo root]"""
import os, subprocess, sys, zipfile, tempfile
import numpy as np

R = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..')
OUT = os.path.join(R, 'patch/assets/frontierhunts/sounds/rutfight')
SR = 44100
os.makedirs(OUT, exist_ok=True)
jar = zipfile.ZipFile('/home/claude/fh/merged62g8.jar')

def load(i):
    with tempfile.NamedTemporaryFile(suffix='.ogg') as f:
        f.write(jar.read(f'assets/frontierhunts/sounds/equipment/antler_rattle_{i}.ogg')); f.flush()
        raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', f.name, '-f', 's16le', '-ac', '1', '-ar', str(SR), '-'], capture_output=True).stdout
    return np.frombuffer(raw, np.int16).astype(np.float32) / 32768

def save(x, name):
    x = x / max(1e-6, np.abs(x).max()) * 0.89
    pcm = (x * 32767).astype(np.int16).tobytes()
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '1', '-i', '-', '-c:a', 'libvorbis', '-q:a', '5',
                    os.path.join(OUT, name)], input=pcm, check=True)

def env(a, w=441):
    n = len(a) // w
    return np.sqrt((a[:n * w].reshape(n, w) ** 2).mean(1))

clacks, grinds = [], []
for i in range(4):
    a = load(i)
    e = env(a)
    # onsets: frames much louder than the 60 ms before them
    for k in range(4, len(e) - 20):
        if e[k] > 0.12 and e[k] > 2.2 * e[k - 4:k].mean() and e[k] >= e[k - 1:k + 2].max():
            clacks.append((e[k], i, k))
    # grinding: 0.9 s stretches with steady, moderate energy and no single huge spike
    for k in range(0, len(e) - 90, 10):
        seg = e[k:k + 90]
        if 0.012 < seg.mean() < 0.16 and seg.max() < 0.32:
            grinds.append((seg.std() / seg.mean(), i, k))
    globals()[f'a{i}'] = a

def cut(i, start, length):
    a = globals()[f'a{i}']
    s0 = max(0, start)
    x = a[s0:s0 + length].copy()
    fade_in = int(0.004 * SR); fade_out = int(length * 0.45)
    x[:fade_in] *= np.linspace(0, 1, fade_in)
    x[-fade_out:] *= np.linspace(1, 0, fade_out) ** 1.6
    return x

clacks.sort(reverse=True)
used = []
n = 0
for amp, i, k in clacks:
    if any(i == j and abs(k - kk) < 25 for j, kk in used):
        continue
    used.append((i, k))
    save(cut(i, k * 441 - int(0.015 * SR), int(0.34 * SR)), f'clash_{n}.ogg')
    n += 1
    if n == 4:
        break
grinds.sort()
n = 0
used = []
for score, i, k in grinds:
    if any(i == j and abs(k - kk) < 100 for j, kk in used):
        continue
    used.append((i, k))
    x = cut(i, k * 441, int(0.9 * SR))
    x[:int(0.06 * SR)] *= np.linspace(0, 1, int(0.06 * SR))
    save(x, f'grind_{n}.ogg')
    n += 1
    if n == 3:
        break
print('wrote', sorted(os.listdir(OUT)))
