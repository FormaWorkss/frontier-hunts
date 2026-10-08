#!/usr/bin/env python3
"""[ecology] Synthesised wolf howls (original audio): a solo howl in three variants and a pack chorus in two.

A wolf howl is close to a pure tone: a strong fundamental (here 260-620 Hz) with a few weak harmonics, a smooth
rise at the start, a long held note that drifts and wavers a little (slow vibrato, pitch jitter), often a break
upward or a dip, and a falling end; a little breath noise rides on it. The far-carrying, open-air feel comes from a
soft high cut and a diffuse outdoor tail (decaying filtered noise convolution). A chorus layers several such howls
of different pitches and starts, with a few short yips from the young ones.

python3 tools/ecology/gen_howl.py [repo_root]   -> patch/assets/frontierhunts/sounds/wildlife/*.ogg (mono, 44.1 kHz)
"""
import os, subprocess, sys, tempfile, wave
import numpy as np

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(R, 'patch', 'assets', 'frontierhunts', 'sounds', 'wildlife')
SR = 44100


def smooth_noise(n, rate_hz, rs):
    """band-limited random walk, ~rate_hz changes per second, values ~[-1, 1]"""
    k = max(2, int(n / SR * rate_hz) + 2)
    pts = rs.uniform(-1, 1, k)
    x = np.linspace(0, k - 1, n)
    i = np.floor(x).astype(int)
    f = x - i
    f = f * f * (3 - 2 * f)
    i2 = np.minimum(i + 1, k - 1)
    return pts[i] * (1 - f) + pts[i2] * f


def lowpass(x, cutoff):
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def lp_fast(x, cutoff, passes=2):
    # one-pole filters via scipy if available, else the loop above
    try:
        from scipy.signal import lfilter
        a = np.exp(-2.0 * np.pi * cutoff / SR)
        for _ in range(passes):
            x = lfilter([1 - a], [1, -a], x)
        return x
    except ImportError:
        for _ in range(passes):
            x = lowpass(x, cutoff)
        return x


def hp_fast(x, cutoff):
    return x - lp_fast(x, cutoff, 1)


def howl(dur, f_start, f_hold, f_end, rs, brk=None, yip=False):
    """one howl. brk: (time fraction, semitone jump) for the upward break many howls have"""
    n = int(dur * SR)
    t = np.arange(n) / SR
    u = t / dur
    # pitch contour: rise (first ~15-20%), hold with a slow drift, fall at the end
    rise = np.clip(u / 0.18, 0, 1)
    rise = 1 - (1 - rise) ** 2.2
    fall = np.clip((u - 0.78) / 0.22, 0, 1) ** 1.6
    f = f_start + (f_hold - f_start) * rise
    f = f * (1 - fall) + f_end * fall
    f *= 1 + 0.025 * smooth_noise(n, 0.8, rs)                      # slow drift
    f *= 1 + 0.006 * np.sin(2 * np.pi * (4.6 + rs.rand()) * t)     # gentle vibrato
    f *= 1 + 0.003 * smooth_noise(n, 30, rs)                       # jitter
    if brk is not None:
        bt, semis = brk
        step = 1 / (1 + np.exp(-(u - bt) * 140))
        f *= 2 ** (semis / 12 * step)
    if yip:
        f *= 1 + 0.25 * np.exp(-((u - 0.1) / 0.06) ** 2)
    phase = 2 * np.pi * np.cumsum(f) / SR
    # harmonics: fundamental dominant; a soft 'oo' vowel lifts the 2nd a little
    amps = [1.0, 0.22, 0.09, 0.045, 0.02]
    sig = np.zeros(n)
    for k, a in enumerate(amps, start=1):
        shimmer = 1 + 0.08 * smooth_noise(n, 6, rs)
        sig += a * shimmer * np.sin(k * phase + rs.rand() * 6.28)
    # breath: noise shaped by the voice envelope, high-passed
    breath = hp_fast(rs.normal(0, 1, n), 900) * 0.035
    # amplitude envelope: soft onset, swell, tail
    att = np.clip(u / 0.08, 0, 1) ** 1.5
    rel = np.clip((1 - u) / 0.12, 0, 1) ** 1.2
    swell = 0.8 + 0.2 * np.sin(np.pi * np.clip(u * 1.1, 0, 1))
    env = att * rel * swell * (1 + 0.06 * smooth_noise(n, 3, rs))
    return (sig + breath) * env


def yips(dur, f0, rs, count):
    n = int(dur * SR)
    out = np.zeros(n)
    for _ in range(count):
        d = 0.12 + rs.rand() * 0.1
        y = howl(d, f0 * 1.1, f0 * 1.45, f0 * 0.9, rs, yip=True) * 0.55
        s = int(rs.rand() * (n - len(y) - 1))
        out[s:s + len(y)] += y
    return out


def outdoors(x, rs, tail=1.6, wet=0.32, cut=3600):
    """distance and open-air diffusion: soft high cut and a sparse decaying tail"""
    x = lp_fast(x, cut, 2)
    n = int(tail * SR)
    t = np.arange(n) / SR
    ir = rs.normal(0, 1, n) * np.exp(-t / (tail / 5.5))
    ir = lp_fast(ir, 2200, 2)
    ir[: int(0.035 * SR)] *= np.linspace(0, 1, int(0.035 * SR))   # pre-delay: first reflections from the treeline
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    try:
        from scipy.signal import fftconvolve
        rev = fftconvolve(x, ir)[: len(x) + n]
    except ImportError:
        rev = np.convolve(x, ir)[: len(x) + n]
    dry = np.concatenate([x, np.zeros(n)])
    if len(rev) < len(dry):
        rev = np.concatenate([rev, np.zeros(len(dry) - len(rev))])
    return dry * (1 - wet) + rev[: len(dry)] * wet * 2.2


def finish(x, peak=0.82):
    x = x - np.mean(x)
    fade = int(0.08 * SR)
    x[-fade:] *= np.linspace(1, 0, fade)
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def write_ogg(name, x):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        path = tf.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', path, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(OUT, name + '.ogg')], check=True)
    os.unlink(path)
    np.save(os.path.join(tempfile.gettempdir(), name + '.npy'), x[::4])


def main():
    # solo howls
    specs = [
        (4.2, 300, 430, 330, (0.55, 1.2), 7),
        (3.6, 340, 470, 360, None, 11),
        (4.8, 270, 380, 250, (0.42, 2.0), 13),
    ]
    for i, (dur, a, b, c, brk, seed) in enumerate(specs):
        rs = np.random.RandomState(seed)
        x = howl(dur, a, b, c, rs, brk)
        write_ogg(f'wolf_howl_{i}', finish(outdoors(x, rs)))
    # choruses: an adult opens, others join at different pitches, young ones yip
    for j, seed in enumerate((21, 34)):
        rs = np.random.RandomState(seed)
        total = 8.5
        n = int(total * SR)
        mix = np.zeros(n)
        voices = [(0.0, 4.6, 290, 400, 320, (0.5, 1.5), 1.0),
                  (1.1, 4.2, 360, 520, 410, None, 0.8),
                  (1.9, 3.8, 420, 610, 470, (0.35, 2.0), 0.62),
                  (2.8, 4.4, 310, 450, 300, None, 0.75),
                  (3.4, 3.0, 480, 660, 520, None, 0.5)]
        if j == 1:
            voices = [(0.0, 4.2, 320, 440, 340, None, 1.0),
                      (0.7, 4.8, 280, 370, 260, (0.6, 1.0), 0.85),
                      (2.0, 3.6, 450, 600, 480, (0.4, 1.5), 0.6),
                      (3.0, 4.0, 380, 540, 420, None, 0.7)]
        for (st, dur, a, b, c, brk, g) in voices:
            v = howl(dur * (0.92 + rs.rand() * 0.16), a * (0.97 + rs.rand() * 0.06), b, c, rs, brk) * g
            s = int(st * SR)
            e = min(n, s + len(v))
            mix[s:e] += v[: e - s]
        mix += yips(total, 520, rs, 6) * 0.35
        write_ogg(f'wolf_chorus_{j}', finish(outdoors(mix, rs, tail=2.0, wet=0.36)))
    print('wrote howls to', OUT)


if __name__ == '__main__':
    main()
