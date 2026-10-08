"""[wingshot] Synthesized bird-flight and hit sounds (original, numpy/scipy -> mono 44.1 kHz Ogg Vorbis via ffmpeg).

  grouse_flush_{0,1,2}   the flush: a thunderous burst of very fast wingbeats, leaf litter kicked up, fading as it goes
  duck_takeoff_{0,1,2}   the puddle-duck jump: water thrown off, deep fast strokes with the mallard's wing whistle
  duck_wings             loop: cruising wingbeat at 5.4 Hz (11 beats in 2.04 s), whistle on the downstroke
  grouse_wings           loop: short-winged flutter at 12 Hz (12 beats in 1 s)
  splash_small_{0,1}     take-off / landing on water
  bird_hit_{0,1,2}       a pellet strike: a dull thwack and the puff of feathers
  body_thud_{0,1}        a shot bird hitting the ground
  body_splash_{0,1}      a shot bird hitting water
  slowmo                 the wing-shot moment: a low whoomp and a held breath of air

usage: python3 tools/wingshot/audio.py [repo]   (also writes waveform/spectrogram previews to /tmp/claude-0/ws_audio)
"""
import os, sys, subprocess
import numpy as np
from scipy import signal

SR = 44100
REPO = sys.argv[1] if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(REPO, 'patch', 'assets', 'frontierhunts', 'sounds', 'wingshot')
PREV = '/tmp/claude-0/ws_audio'


def bp(x, lo, hi, order=3):
    sos = signal.butter(order, [lo / (SR / 2), min(hi, SR / 2 - 100) / (SR / 2)], 'band', output='sos')
    return signal.sosfilt(sos, x)


def lp(x, f, order=3):
    return signal.sosfilt(signal.butter(order, f / (SR / 2), 'low', output='sos'), x)


def hp(x, f, order=3):
    return signal.sosfilt(signal.butter(order, f / (SR / 2), 'high', output='sos'), x)


def env_ad(n, a, d, shape=2.0):
    t = np.arange(n) / SR
    e = np.where(t < a, (t / max(a, 1e-4)), np.exp(-(t - a) / d))
    return e ** (1.0 if shape == 1 else 1.0)


def wingbeat(n, rate, rng, lo=300, hi=2600, whistle=0.0, whistle_f=1400, depth=0.85, jitter=0.03, strength=None, phase=0.0):
    """a train of wingbeats: each downstroke a whoosh (filtered noise), quieter upstroke, optional whistle tone"""
    t = np.arange(n) / SR
    noise = rng.standard_normal(n)
    air = bp(noise, lo, hi)
    # stroke phase with a little timing jitter
    ph = (t * rate + phase + np.cumsum(rng.standard_normal(n)) * jitter / SR * rate * 30) % 1.0
    down = np.clip(np.sin(np.pi * np.clip(ph / 0.55, 0, 1)), 0, 1) ** 1.6
    up = np.clip(np.sin(np.pi * np.clip((ph - 0.55) / 0.45, 0, 1)), 0, 1) ** 2 * 0.35
    stroke = (1 - depth) + depth * (down + up)
    out = air * stroke
    if whistle > 0:
        # the mallard's wing whistle: a breathy tone that rises and falls with the downstroke
        f = whistle_f * (1 + 0.08 * down)
        ph_t = np.cumsum(2 * np.pi * f / SR)
        tone = np.sin(ph_t) + 0.3 * np.sin(2 * ph_t)
        breath = bp(rng.standard_normal(n), whistle_f * 0.8, whistle_f * 1.25)
        out += whistle * (tone * 0.4 + breath * 3.0) * down ** 2 * 0.25
    if strength is not None:
        out *= strength
    return out


def norm(x, peak=0.89):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def fade(x, a=0.004, b=0.03):
    n = len(x)
    ia, ib = int(a * SR), int(b * SR)
    if ia > 0:
        x[:ia] *= np.linspace(0, 1, ia)
    if ib > 0:
        x[-ib:] *= np.linspace(1, 0, ib)
    return x


def loopify(x, xf=0.05):
    """crossfade the tail into the head so the loop has no click"""
    k = int(xf * SR)
    head = x[:k].copy()
    tail = x[-k:].copy()
    w = np.linspace(0, 1, k)
    x[:k] = head * w + tail * (1 - w)
    return x[:-k]


def write(name, x, peak=0.89, loop=False):
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(PREV, exist_ok=True)
    x = norm(x, peak)
    if not loop:
        x = fade(x)
    wav = os.path.join(PREV, name + '.wav')
    from scipy.io import wavfile
    wavfile.write(wav, SR, (x * 32767).astype(np.int16))
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-ar', str(SR), '-c:a', 'libvorbis', '-q:a', '5', os.path.join(OUT, name + '.ogg')],
                   check=True)
    return x


def grouse_flush(seed):
    rng = np.random.default_rng(seed)
    dur = 1.25
    n = int(dur * SR)
    t = np.arange(n) / SR
    rate = 17.0 + rng.random() * 3 - 3.5 * t  # slows a little as it gets going
    ph = np.cumsum(rate / SR)
    beat = (ph % 1.0)
    pulse = np.exp(-beat / 0.18) * (1 - np.exp(-beat / 0.01))
    thrum_noise = bp(rng.standard_normal(n), 90, 900, 4)
    body = lp(rng.standard_normal(n), 220) * 2.0
    burst = (thrum_noise + body) * pulse
    # the roar: loud at once, fading as the bird gets away
    amp = np.exp(-t / 0.55) * (1 - np.exp(-t / 0.012)) + 0.12 * np.exp(-t / 1.5)
    out = burst * amp
    # leaf litter and twigs kicked up at the start
    crack = np.zeros(n)
    for k in range(int(10 + rng.random() * 8)):
        p = int(rng.random() * 0.25 * SR)
        L = int(0.006 * SR)
        crack[p:p + L] += rng.standard_normal(L) * np.exp(-np.arange(L) / (0.0015 * SR)) * (0.6 + rng.random())
    leaves = hp(rng.standard_normal(n), 2500) * np.exp(-t / 0.18) * 0.25
    out += hp(crack, 1500) * 0.7 + leaves
    out += hp(burst, 1800) * 0.25 * amp
    return out


def duck_takeoff(seed):
    rng = np.random.default_rng(seed)
    dur = 1.15
    n = int(dur * SR)
    t = np.arange(n) / SR
    # water thrown off as she jumps
    spray = hp(rng.standard_normal(n), 1200) * np.exp(-t / 0.12) * 0.7
    drip = np.zeros(n)
    for k in range(40):
        p = int((0.05 + rng.random() * 0.55) * SR)
        f = 1500 + rng.random() * 2500
        L = int(0.02 * SR)
        tt = np.arange(L) / SR
        drip[p:p + L] += np.sin(2 * np.pi * f * (1 + 8 * tt) * tt) * np.exp(-tt / 0.006) * (0.2 + rng.random() * 0.3) * np.exp(-p / SR / 0.4)
    beats = wingbeat(n, 7.0, rng, lo=250, hi=3500, whistle=0.8, whistle_f=1300 + rng.random() * 200, depth=0.92)
    amp = (1 - np.exp(-t / 0.05)) * (0.6 + 0.4 * np.exp(-t / 0.5))
    thump = lp(beats, 400) * 1.5
    return beats * amp + thump * amp + spray + drip


def duck_wings():
    rng = np.random.default_rng(31)
    beats = 11
    rate = 5.4
    dur = beats / rate
    n = int((dur + 0.05) * SR)
    x = wingbeat(n, rate, rng, lo=350, hi=3000, whistle=1.0, whistle_f=1350, depth=0.8, jitter=0.0)
    x += lp(x, 350) * 0.8
    return loopify(x, 0.05)


def grouse_wings():
    rng = np.random.default_rng(41)
    rate = 12.0
    dur = 1.0
    n = int((dur + 0.04) * SR)
    x = wingbeat(n, rate, rng, lo=120, hi=1400, whistle=0.0, depth=0.9, jitter=0.0)
    x += lp(rng.standard_normal(n), 200) * 0.6 * (0.5 + 0.5 * np.sin(2 * np.pi * rate * np.arange(n) / SR))
    return loopify(x, 0.04)


def splash(seed, big=1.0):
    rng = np.random.default_rng(seed)
    dur = 0.7
    n = int(dur * SR)
    t = np.arange(n) / SR
    body = bp(rng.standard_normal(n), 300, 6000) * np.exp(-t / (0.09 * big)) * (1 - np.exp(-t / 0.004))
    low = lp(rng.standard_normal(n), 300) * np.exp(-t / 0.06) * 1.5 * big
    bub = np.zeros(n)
    for k in range(int(30 * big)):
        p = int((0.02 + rng.random() * 0.45) * SR)
        f = 600 + rng.random() * 1800
        L = int(0.03 * SR)
        tt = np.arange(L) / SR
        bub[p:p + L] += np.sin(2 * np.pi * f * (1 + 6 * tt) * tt) * np.exp(-tt / 0.008) * (0.15 + rng.random() * 0.25)
    return body + low + bub * np.exp(-t / 0.3)


def bird_hit(seed):
    rng = np.random.default_rng(seed)
    dur = 0.35
    n = int(dur * SR)
    t = np.arange(n) / SR
    thwack = lp(rng.standard_normal(n), 900) * np.exp(-t / 0.018) * 2.0
    thump = np.sin(2 * np.pi * (140 - 200 * t) * t) * np.exp(-t / 0.03) * 0.8
    puff = bp(rng.standard_normal(n), 1500, 9000) * np.exp(-t / 0.07) * (1 - np.exp(-t / 0.006)) * 0.55
    return thwack + thump + puff


def body_thud(seed):
    rng = np.random.default_rng(seed)
    dur = 0.4
    n = int(dur * SR)
    t = np.arange(n) / SR
    thud = np.sin(2 * np.pi * (95 - 60 * t) * t) * np.exp(-t / 0.05) + lp(rng.standard_normal(n), 250) * np.exp(-t / 0.04) * 1.2
    rustle = bp(rng.standard_normal(n), 1800, 7000) * np.exp(-t / 0.08) * 0.35
    return thud + rustle


def slowmo():
    rng = np.random.default_rng(77)
    dur = 1.7
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = 75 * np.exp(-t / 0.5) + 32
    whoomp = np.sin(np.cumsum(2 * np.pi * f / SR)) * np.exp(-t / 0.45) * (1 - np.exp(-t / 0.01))
    air = lp(rng.standard_normal(n), 900) * (np.exp(-((t - 0.5) / 0.35) ** 2)) * 0.5
    swell = bp(rng.standard_normal(n), 200, 2000) * np.exp(-t / 0.25) * 0.25
    return whoomp * 1.2 + air + swell


def preview(name, x):
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(2, 1, figsize=(10, 5))
        ax[0].plot(np.arange(len(x)) / SR, x, lw=0.3)
        ax[1].specgram(x, NFFT=1024, Fs=SR, noverlap=768, cmap='magma', vmin=-120)
        ax[1].set_ylim(0, 10000)
        fig.suptitle(name)
        plt.tight_layout()
        plt.savefig(os.path.join(PREV, name + '.png'), dpi=55)
        plt.close(fig)
    except Exception as e:
        print('preview failed', e)


def main():
    made = {}
    for i in range(3):
        made['grouse_flush_%d' % i] = write('grouse_flush_%d' % i, grouse_flush(10 + i))
        made['duck_takeoff_%d' % i] = write('duck_takeoff_%d' % i, duck_takeoff(20 + i))
        made['bird_hit_%d' % i] = write('bird_hit_%d' % i, bird_hit(30 + i))
    made['duck_wings'] = write('duck_wings', duck_wings(), 0.8, loop=True)
    made['grouse_wings'] = write('grouse_wings', grouse_wings(), 0.8, loop=True)
    for i in range(2):
        made['splash_small_%d' % i] = write('splash_small_%d' % i, splash(40 + i))
        made['body_thud_%d' % i] = write('body_thud_%d' % i, body_thud(50 + i))
        made['body_splash_%d' % i] = write('body_splash_%d' % i, splash(60 + i, 1.4))
    made['slowmo'] = write('slowmo', slowmo())
    for k in ('grouse_flush_0', 'duck_takeoff_0', 'duck_wings', 'grouse_wings', 'bird_hit_0', 'splash_small_0', 'slowmo'):
        preview(k, made[k])
    print('wrote', len(made), 'sounds to', OUT)


if __name__ == '__main__':
    main()
