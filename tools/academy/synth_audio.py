"""[academy] Original synthesized academy sounds -> patch/assets/frontierhunts/sounds/academy/*.ogg (mono, 44.1 kHz, libvorbis)."""
import os, sys, subprocess, json
import numpy as np
ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = os.path.join(ROOT, 'patch/assets/frontierhunts/sounds/academy')
os.makedirs(OUT, exist_ok=True)
SR = 44100
rng = np.random.default_rng(7)
def t(sec): return np.arange(int(SR * sec)) / SR
def env(n, a, d):  # attack seconds, exponential decay constant seconds
    x = np.arange(n) / SR
    return np.minimum(1, x / max(a, 1e-4)) * np.exp(-x / d)
def norm(x, peak=0.85):
    return x / (np.max(np.abs(x)) + 1e-9) * peak
def lp(x, a):  # one-pole low pass, a in 0..1
    y = np.zeros_like(x); s = 0.0
    for i, v in enumerate(x): s += a * (v - s); y[i] = s
    return y
def save(name, x):
    import wave
    p = os.path.join(OUT, name)
    x = np.clip(x, -1, 1)
    with wave.open(p + '.wav', 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x * 32767).astype(np.int16).tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', p + '.wav', '-c:a', 'libvorbis', '-q:a', '5', '-ac', '1', p + '.ogg'], check=True)
    os.remove(p + '.wav')
    print('wrote', name, len(x) / SR, 's')

def plate_ring(f0, dur=1.9):
    # steel plate: inharmonic partials (thin plate modes), quick strike, long ring, a little beating
    x = t(dur); y = np.zeros_like(x)
    ratios = [1.0, 1.594, 2.136, 2.296, 2.653, 3.156, 3.60, 4.06]
    for i, r in enumerate(ratios):
        amp = 0.9 / (1 + i * 0.7)
        d = 0.9 / (1 + i * 0.45)
        f = f0 * r
        y += amp * np.sin(2 * np.pi * f * x + rng.uniform(0, 6)) * np.exp(-x / d) * (1 + 0.08 * np.sin(2 * np.pi * (1.3 + i) * x))
    strike = rng.normal(0, 1, len(x)) * np.exp(-x / 0.004) * 0.6
    y = y * np.minimum(1, x / 0.0015) + strike
    return norm(y, 0.8)

save('ring', plate_ring(560))
save('ring_gold', norm(plate_ring(760) + 0.5 * plate_ring(1140, 1.4)[:len(t(1.9))] if False else plate_ring(760), 0.85))

# objective tick: two soft wooden-bell notes (marimba-like)
def note(f, dur, d=0.25):
    x = t(dur)
    y = np.sin(2 * np.pi * f * x) + 0.35 * np.sin(2 * np.pi * f * 4.0 * x) * np.exp(-x / 0.05) + 0.2 * np.sin(2 * np.pi * f * 2.0 * x)
    return y * env(len(x), 0.002, d)
a = note(880, 0.5); b = note(1318.5, 0.6)
tick = np.zeros(int(SR * 0.75)); tick[:len(a)] += a; tick[int(SR * 0.09):int(SR * 0.09) + len(b)] += b
save('tick', norm(tick, 0.7))

# course passed: warm brass-ish chord arpeggio (D major) with a soft swell
def brass(f, dur, att=0.05, d=1.2):
    x = t(dur); y = np.zeros_like(x)
    for h in range(1, 9):
        y += np.sin(2 * np.pi * f * h * x * (1 + 0.0007 * np.sin(2 * np.pi * 5 * x))) / (h ** 1.25)
    return y * np.minimum(1, x / att) * np.exp(-x / d)
fan = np.zeros(int(SR * 3.2))
for i, (f, st) in enumerate([(293.7, 0.0), (370.0, 0.16), (440.0, 0.32), (587.3, 0.48)]):
    n = brass(f, 2.7 - st, d=1.4 + i * 0.2); s = int(SR * st); fan[s:s + len(n)] += n * (0.8 if i < 3 else 1.0)
pad = sum(brass(f, 3.2, att=0.6, d=2.5) for f in (146.8, 220.0)) * 0.35
fan += pad[:len(fan)]
save('passed', norm(lp(fan, 0.35), 0.8))

# arrival: wind swell + low bell
x = t(2.6)
wind = lp(rng.normal(0, 1, len(x)), 0.02) * np.sin(np.pi * np.minimum(1, x / 2.6)) ** 2
bell = sum(np.sin(2 * np.pi * f * x) * np.exp(-x / d) for f, d in ((196.0, 1.6), (392.0 * 1.003, 1.0), (587.0, 0.6), (784.0 * 1.01, 0.35)))
bell = bell * np.minimum(1, np.maximum(0, x - 0.35) / 0.003) * (x > 0.35)
save('arrive', norm(norm(wind, 0.5) + 0.45 * norm(bell), 0.8))

# depart: filtered noise whoosh rising then falling
x = t(1.1)
n = rng.normal(0, 1, len(x))
wh = np.zeros_like(n); s = 0.0
for i in range(len(n)):
    a_ = 0.01 + 0.12 * np.sin(np.pi * min(1, x[i] / 1.1))
    s += a_ * (n[i] - s); wh[i] = s
save('depart', norm(wh * np.sin(np.pi * x / 1.1) ** 1.5, 0.6))

# busted: a deer's alarm snort (breathy noise burst with a nasal resonance) twice
def snort(dur=0.32):
    x = t(dur); nz = rng.normal(0, 1, len(x))
    y = lp(nz, 0.25) * np.exp(-x / 0.09) * np.minimum(1, x / 0.008)
    res = np.sin(2 * np.pi * 420 * x) * np.exp(-x / 0.05) * 0.4
    return y + res * lp(nz, 0.5)
bs = np.zeros(int(SR * 0.9)); s1 = snort(); bs[:len(s1)] += s1; s2 = snort(0.25); o = int(SR * 0.42); bs[o:o + len(s2)] += 0.7 * s2
save('busted', norm(bs, 0.75))

# page: paper flick
x = t(0.35); nz = rng.normal(0, 1, len(x))
pg = (nz - lp(nz, 0.3)) * np.exp(-x / 0.06) * np.minimum(1, x / 0.01)
save('page', norm(pg, 0.5))

frag = {}
for name in ['ring', 'ring_gold', 'tick', 'passed', 'arrive', 'depart', 'busted', 'page']:
    e = {'sounds': [{'name': 'frontierhunts:academy/' + name}], 'subtitle': 'subtitles.frontierhunts.academy.' + name}
    frag['academy.' + name] = e
d = os.path.join(ROOT, 'patch/_merge/assets/frontierhunts/sounds.json')
os.makedirs(d, exist_ok=True)
json.dump(frag, open(os.path.join(d, 'academy.json'), 'w'), indent=1)
