"""[1.1.0] Realistic moving whitewater for the Frontier's creek and river surfaces (alpine_flow_0/1/2.png).

The old overlays were the same diagonal white blots at every strength, which read as a camouflage print. These are
built like real running water seen from the bank:
  - foam lines: thin, broken streaks drawn out along the current (what a riffle leaves on the surface),
  - a lacy foam network (cells drawn out along the current) where foam gathers, sparse on a run,
  - white caps: aerated water streaming along, soft edged and streaky, joined into a white tongue in rapids,
  - small bubbles riding along.
All of it moves downstream: each texture is 16 frames (128 x 128, 4 x 4 blocks; downstream = up the frame) made of two
layers that scroll with the current and cross-fade, so the loop is seamless at any speed. Patterns are periodic noise
(filtered in frequency space), so they tile without seams across and along the stream.

usage: python3 gen_whitewater.py <repo root>
"""
import sys, os
import numpy as np
from PIL import Image

N, F = 128, 16


def noise3(seed, su, sv, st):
    """periodic (frames, v, u) noise with gaussian spectra: su/sv/st = feature size in px / frames"""
    r = np.random.default_rng(seed)
    w = r.standard_normal((F, N, N))
    ft, fv, fu = np.meshgrid(np.fft.fftfreq(F), np.fft.fftfreq(N), np.fft.fftfreq(N), indexing='ij')
    g = np.exp(-2 * np.pi ** 2 * ((fu * su) ** 2 + (fv * sv) ** 2 + (ft * st) ** 2))
    x = np.real(np.fft.ifftn(np.fft.fftn(w) * g))
    return (x - x.mean()) / (x.std() + 1e-9)


def shift(a, dv):
    """move a (v, u) image down-the-stream (toward smaller v) by dv pixels (sub-pixel, periodic)"""
    k = np.fft.fftfreq(N)[:, None]
    return np.real(np.fft.ifft(np.fft.fft(a, axis=0) * np.exp(2j * np.pi * k * dv), axis=0))


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


WARP = None


def lace_field(seed, count, stretch, t):
    """worley cell edges (F2 - F1), periodic, cells drawn out along the stream and warped so no strand is straight;
    points drift on small loops"""
    global WARP
    if WARP is None:
        WARP = (noise3(901, 9.0, 12.0, 6.0), noise3(902, 9.0, 12.0, 6.0))
    r = np.random.default_rng(seed)
    P = r.random((count, 2)) * N
    ph = r.random(count) * 2 * np.pi
    ang = 2 * np.pi * t / F
    P = P + np.stack([np.cos(ph + ang), np.sin(ph + ang) * 1.5], 1) * 2.0
    v, u = np.mgrid[0:N, 0:N].astype(float)
    u = u + WARP[0][t] * 5.0
    v = v + WARP[1][t] * 7.0
    d1 = np.full((N, N), 1e9); d2 = np.full((N, N), 1e9)
    for px, py in P:
        for ox in (-N, 0, N):
            for oy in (-N, 0, N):
                d = np.hypot(u - (px + ox), (v - (py + oy)) / stretch)
                m = d < d1
                d2 = np.where(m, d1, np.minimum(d2, d))
                d1 = np.where(m, d, d1)
    return d2 - d1


def build(level, seed):
    # level 0: a gentle run, 1: a riffle, 2: rapids
    speed = (2.0, 4.0, 7.0)[level]
    lace_w = (1.1, 1.5, 2.0)[level]          # width of the foam network's strands, px
    lace_cover = (0.30, 0.55, 0.85)[level]   # how much of the surface carries the network
    cap = (1.4, 0.75, 0.05)[level]           # threshold of the white caps (rapids: big, joined)
    streak_a = (0.22, 0.32, 0.45)[level]
    lines = noise3(seed, 1.4, 26.0, 6.0)
    lines2 = noise3(seed + 1, 3.0, 40.0, 8.0)
    zone = noise3(seed + 2, 14.0, 26.0, 8.0)                     # where the lace / caps gather
    capn = noise3(seed + 3, 5.0, (24.0, 22.0, 16.0)[level], 4.0) * 0.7 + noise3(seed + 6, 2.0, 9.0, 3.0) * 0.3
    cap_a = (0.55, 0.75, 0.97)[level]
    grain = noise3(seed + 4, 0.9, 6.0, 2.0)                       # streaming texture inside the white water
    bub = noise3(seed + 5, 0.7, 0.9, 1.5)
    frames = []
    for t in range(F):
        acc_a = np.zeros((N, N)); acc_c = np.zeros((N, N))
        for phase in (0, F // 2):
            tt = (t + phase) % F
            wgt = 1.0 - abs(2.0 * tt / F - 1.0)
            dv = speed * tt + phase * 3.7
            lace = shift(lace_field(seed + 7 + phase, 26 + 8 * level, 2.4, tt), dv)
            L = shift(lines[tt], dv); L2 = shift(lines2[tt], dv * 0.8)
            Z = shift(zone[tt], dv); K = shift(capn[tt], dv); Gn = shift(grain[tt], dv); U = shift(bub[tt], dv)
            # foam lines along the flow
            streak = np.clip(1.0 - np.abs(L) * 2.4, 0, 1) ** 3 * smooth(-0.2, 0.9, L2)
            # the lacy foam network, only where foam gathers
            gather = smooth(1.0 - 2 * lace_cover, 1.4 - 2 * lace_cover, Z)
            wid = lace_w * (0.45 + 0.9 * smooth(-1.2, 1.2, Gn))
            breakup = smooth(-0.9, 0.4, shift(lines2[(tt + 5) % F], dv * 1.1))
            net = smooth(wid, 0.0, lace) * gather * breakup * (0.55 + 0.45 * smooth(-1.0, 1.0, Z))
            # white caps: aerated water streaming along, soft edged, streaky inside
            capm = smooth(cap, cap + 0.7, K + 0.25 * Z)
            white = capm * (0.72 + 0.28 * smooth(-1.0, 1.0, Gn))
            bubbles = smooth(2.1, 2.7, U) * 0.7
            a = np.clip(np.maximum.reduce([streak * streak_a, net * 0.7, white * cap_a, bubbles]), 0, 1)
            shade = 0.80 + 0.20 * np.clip(white + net * 0.6, 0, 1)
            acc_a += a * wgt; acc_c += shade * a * wgt
        alpha = np.clip(acc_a, 0, 1)
        shade = np.clip(acc_c / np.maximum(acc_a, 1e-6), 0, 1)
        rgba = np.zeros((N, N, 4))
        rgba[..., 0] = 236 * shade; rgba[..., 1] = 246 * shade; rgba[..., 2] = 248 * shade; rgba[..., 3] = 255 * alpha
        frames.append(rgba)
    return np.concatenate(frames, 0)


if __name__ == '__main__':
    R = sys.argv[1] if len(sys.argv) > 1 else '.'
    out = os.path.join(R, 'patch/assets/frontierhunts/textures/block')
    os.makedirs(out, exist_ok=True)
    for lv in range(3):
        img = build(lv, 1100 + lv * 17)
        Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), 'RGBA').save(os.path.join(out, f'alpine_flow_{lv}.png'), optimize=True)
        print('alpine_flow_%d.png' % lv, img.shape, 'mean alpha %.2f' % (img[..., 3].mean() / 255))
