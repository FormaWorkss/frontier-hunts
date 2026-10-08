"""Original synthesized storm audio for the seasonal-weather workstream.

python3 tools/gen_weather_audio.py [repo_root]
Loops are built on a circular buffer (FFT filtering, integer-cycle modulation, wrapped grains) so they repeat
seamlessly. One-shots use time-varying spectral shaping (STFT). Output: patch/assets/frontierhunts/sounds/weather/*.ogg
"""
import os, subprocess, sys, tempfile
import numpy as np
from scipy.io import wavfile
from scipy.signal import stft, istft

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))
OUT = os.path.join(R, 'patch', 'assets', 'frontierhunts', 'sounds', 'weather')
os.makedirs(OUT, exist_ok=True)
SR = 44100
rng = np.random.default_rng(1227)


# ------------------------------------------------------------------ helpers
def white(n):
    return rng.standard_normal(n)


def shape(x, H):
    """Circular (periodic) filtering with a magnitude response H(f) given as a function of frequency in Hz."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1.0 / SR)
    X *= H(np.maximum(f, 1e-3))
    return np.fft.irfft(X, len(x))


def lp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def hp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / f) ** (2 * order))


def band(fc, octaves):
    return lambda f: np.exp(-0.5 * (np.log2(f / fc) / (octaves / 2.355)) ** 2)


def tilt(alpha):
    return lambda f: (np.maximum(f, 20.0) / 1000.0) ** (-alpha / 2.0)


def mul(*hs):
    return lambda f: np.prod([h(f) for h in hs], axis=0)


def rms(x):
    return float(np.sqrt(np.mean(x ** 2)) + 1e-12)


def norm(x, target_rms):
    return x * (target_rms / rms(x))


def lfo(n, cycles, phase=0.0):
    t = np.arange(n) / n
    return np.sin(2 * np.pi * cycles * t + phase)


def gust_curve(n, seed, comps=((1, 1.0), (2, 0.6), (3, 0.45), (5, 0.3), (8, 0.18))):
    r = np.random.default_rng(seed)
    g = np.zeros(n)
    for c, a in comps:
        g += a * lfo(n, c, r.random() * 2 * np.pi)
    g = (g - g.min()) / (g.max() - g.min())
    return g


def grains(n, count, kernel, amp_curve=None, seed=0):
    """Circularly wrapped impulses convolved with a kernel (for drops / grit / leaf ticks)."""
    r = np.random.default_rng(seed)
    imp = np.zeros(n)
    pos = r.integers(0, n, count)
    amps = r.random(count) ** 2 * (r.random(count) < 0.97) + (r.random(count) < 0.03) * 1.5
    if amp_curve is not None:
        amps *= amp_curve[pos]
    np.add.at(imp, pos, amps * np.where(r.random(count) < 0.5, 1, -1))
    k = np.zeros(n)
    k[:len(kernel)] = kernel
    return np.fft.irfft(np.fft.rfft(imp) * np.fft.rfft(k), n)


def multi_grains(n, count, ms, fc, octaves, amp_curve, seed, variants=8):
    """Grains spread over several kernel variants (random length/centre) so no comb-filter notches build up."""
    r = np.random.default_rng(seed)
    out = np.zeros(n)
    for v in range(variants):
        k = decay_kernel(ms * (0.6 + 0.8 * r.random()), fc * 2 ** ((r.random() - 0.5) * 0.8), octaves, seed * 31 + v)
        out += grains(n, max(1, count // variants), k, amp_curve, seed=seed * 17 + v)
    return out


def decay_kernel(ms, fc, octaves, seed):
    r = np.random.default_rng(seed)
    m = int(SR * ms / 1000.0)
    k = r.standard_normal(m) * np.exp(-np.linspace(0, 6, m))
    return shape(np.pad(k, (0, 4096)), band(fc, octaves))[:m + 512]


def write_ogg(name, x, stereo=False):
    x = np.asarray(x, dtype=np.float64)
    x = 0.95 * np.tanh(x / 0.95)  # soft limiter: keeps loudness, tames the odd grit/drop transient
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tf:
        wavfile.write(tf.name, SR, pcm.T if stereo else pcm)
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tf.name, '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')], check=True)
    os.unlink(tf.name)


def fade(x, a=0.05, b=0.3):
    n = len(x)
    na, nb = int(SR * a), int(SR * b)
    e = np.ones(n)
    e[:na] = np.sin(np.linspace(0, np.pi / 2, na)) ** 2
    e[n - nb:] = np.cos(np.linspace(0, np.pi / 2, nb)) ** 2
    return x * e


# ------------------------------------------------------------------ loops
def blizzard_loop(seconds=16):
    n = SR * seconds
    g = gust_curve(n, 11)
    chans = []
    for ch in range(2):
        gg = np.roll(g, ch * int(SR * 0.07))
        roar = norm(shape(white(n), mul(tilt(1.6), lp(380, 2), hp(28, 2))), 1.0) * (0.55 + 0.45 * gg)
        body = norm(shape(white(n), mul(band(520, 1.3), tilt(0.4))), 1.0) * (0.25 + 0.75 * gg ** 1.5)
        hiss = norm(shape(white(n), mul(hp(2600, 2), lp(9000, 2))), 1.0) * (0.2 + 0.8 * gg ** 2)
        howl = np.zeros(n)
        r = np.random.default_rng(40 + ch)
        for fc, cyc in ((330, 1), (405, 2), (470, 3), (560, 2), (640, 1)):
            q = shape(white(n), lambda f, fc=fc: np.exp(-0.5 * ((f - fc) / 7.0) ** 2) + 0.35 * np.exp(-0.5 * ((f - 2 * fc) / 10.0) ** 2))
            w = np.maximum(0.0, lfo(n, cyc, r.random() * 6.283)) ** 3
            howl += norm(q, 1.0) * w
        howl *= (0.3 + 0.7 * gg)
        mix = roar * 1.0 + body * 0.5 + hiss * 0.11 + howl * 0.16
        chans.append(mix)
    x = np.array(chans)
    return norm(x, 0.16)


def rain_loop(seconds=12):
    n = SR * seconds
    swell = 0.88 + 0.12 * gust_curve(n, 21, ((1, 1.0), (2, 0.5), (3, 0.3)))
    chans = []
    for ch in range(2):
        bed = norm(shape(white(n), mul(hp(350, 2), band(3200, 3.2), lp(12000, 2))), 1.0)
        rumble = norm(shape(white(n), mul(tilt(1.8), lp(170, 2), hp(30))), 1.0)
        ticks = multi_grains(n, seconds * 2600, 3, 5200, 1.5, swell, 100 + ch)
        splats = multi_grains(n, seconds * 900, 9, 2100, 1.8, swell, 200 + ch)
        plops = multi_grains(n, seconds * 120, 22, 750, 1.2, swell, 300 + ch)
        mix = bed * 0.55 * swell + rumble * 0.28 * swell + norm(ticks, 1) * 0.4 + norm(splats, 1) * 0.38 + norm(plops, 1) * 0.16
        chans.append(mix)
    return norm(np.array(chans), 0.15)


def dust_loop(seconds=12):
    n = SR * seconds
    g = gust_curve(n, 31)
    chans = []
    for ch in range(2):
        gg = np.roll(g, ch * int(SR * 0.05))
        roar = norm(shape(white(n), mul(band(300, 2.2), tilt(0.8))), 1.0) * (0.45 + 0.55 * gg)
        env = shape(np.abs(white(n)), lp(35, 2))
        env = np.maximum(env - np.percentile(env, 40), 0)
        sizzle = norm(shape(white(n), mul(hp(3200, 2), lp(11000, 2))), 1.0) * norm(env, 1.0) * (0.2 + 0.8 * gg ** 2)
        grit = multi_grains(n, seconds * 1500, 1.5, 6500, 1.2, 0.2 + 0.8 * gg ** 2, 400 + ch)
        mix = roar * 1.0 + sizzle * 0.3 + norm(grit, 1) * 0.22
        chans.append(mix)
    return norm(np.array(chans), 0.15)


def wind_loop(seconds=12):
    n = SR * seconds
    g = gust_curve(n, 41)
    chans = []
    for ch in range(2):
        gg = np.roll(g, ch * int(SR * 0.09))
        body = norm(shape(white(n), mul(band(620, 2.6), tilt(0.6))), 1.0) * (0.3 + 0.7 * gg ** 1.3)
        buffet = norm(shape(white(n), mul(tilt(1.8), lp(110, 2), hp(22))), 1.0) * (0.3 + 0.7 * gg)
        rustle = multi_grains(n, seconds * 2200, 6, 4200, 2.0, 0.1 + 0.9 * gg ** 2, 500 + ch)
        mix = body * 0.9 + buffet * 0.5 + norm(rustle, 1) * 0.3
        chans.append(mix)
    return norm(np.array(chans), 0.14)


# ------------------------------------------------------------------ one-shots (time-varying spectra)
def spectral(seconds, gain_fn, seed):
    n = int(SR * seconds)
    x = np.random.default_rng(seed).standard_normal(n)
    f, t, Z = stft(x, SR, nperseg=2048, noverlap=1536)
    G = gain_fn(f[:, None], t[None, :] / seconds)
    _, y = istft(Z * G, SR, nperseg=2048, noverlap=1536)
    return y[:n]


def env_curve(u, attack, release):
    a = np.clip(u / attack, 0, 1)
    r = np.clip((1 - u) / release, 0, 1)
    return (np.sin(a * np.pi / 2) ** 2) * (np.sin(r * np.pi / 2) ** 2)


def howl(seconds, f_lo, f_hi, seed):
    def G(f, u):
        e = env_curve(u, 0.3, 0.5)
        f0 = f_lo + (f_hi - f_lo) * np.sin(np.clip(u * 1.15, 0, 1) * np.pi) ** 1.2 + 6 * np.sin(u * seconds * 2 * np.pi * 3.1)
        res = np.exp(-0.5 * ((f - f0) / 14.0) ** 2) + 0.4 * np.exp(-0.5 * ((f - 2 * f0) / 20.0) ** 2) + 0.12 * np.exp(-0.5 * ((f - 3 * f0) / 26.0) ** 2)
        broad = 0.05 * (1 / np.sqrt(1 + (f / (500 + 900 * e)) ** 4)) * (np.maximum(f, 30) / 300) ** -0.3
        return (res * 1.0 + broad) * e
    y = spectral(seconds, G, seed)
    return norm(fade(y, 0.2, 0.8), 0.18)


def rush(seconds, seed):
    r = np.random.default_rng(seed)
    peak_u = 0.35 + 0.15 * r.random()

    def G(f, u):
        e = np.exp(-0.5 * ((u - peak_u) / 0.2) ** 2) * env_curve(u, 0.15, 0.3)
        fc = 250 + 2600 * e
        return (1 / np.sqrt(1 + (f / fc) ** 4)) * (np.maximum(f, 40) / 400) ** -0.45 * (0.05 + e)
    y = spectral(seconds, G, seed)
    n = len(y)
    u = np.arange(n) / n
    e = np.exp(-0.5 * ((u - peak_u) / 0.18) ** 2)
    leaves = multi_grains(n, int(seconds * 1800), 5, 4600, 2.0, e ** 2, seed + 2)
    y = norm(y, 1.0) + norm(leaves, 1.0) * 0.28
    return norm(fade(y, 0.05, 0.6), 0.2)


if __name__ == '__main__':
    write_ogg('blizzard_loop', blizzard_loop(), stereo=True)
    write_ogg('rain_loop', rain_loop(), stereo=True)
    write_ogg('dust_loop', dust_loop(), stereo=True)
    write_ogg('wind_loop', wind_loop(), stereo=True)
    for i, (lo, hi, sec) in enumerate(((300, 520, 6.0), (380, 660, 5.2), (270, 450, 6.8))):
        write_ogg(f'gust_howl_{i}', howl(sec, lo, hi, 700 + i))
    for i, sec in enumerate((3.4, 4.2, 3.0)):
        write_ogg(f'gust_rush_{i}', rush(sec, 800 + i * 7))
    print('weather audio written to', OUT)
