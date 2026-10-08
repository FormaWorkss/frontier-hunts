"""[1.1.2] Frontier snow textures: a fine-grained 4 x 4 block snow surface (512 px, 128 px per block) that tiles without
seams, with labPBR normal (_n) and specular (_s) maps so shader packs give it soft relief, subsurface glow and glints,
plus the small star sprite of the sun glints.  usage: python3 gen_snow_textures.py <repo root>"""
import sys, os
import numpy as np
from PIL import Image

R = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = os.path.join(R, 'patch/assets/frontierhunts/textures/block')
PART = os.path.join(R, 'patch/assets/frontierhunts/textures/particle')
os.makedirs(OUT, exist_ok=True); os.makedirs(PART, exist_ok=True)
S = 512
rng = np.random.default_rng(1121)


def noise(lo, hi, power=1.0):
    """periodic band-limited noise: wavelengths between S/lo and S/hi pixels, unit variance"""
    f = np.fft.fftfreq(S) * S
    fx, fy = np.meshgrid(f, f)
    r = np.sqrt(fx ** 2 + fy ** 2)
    band = ((r >= lo) & (r <= hi)).astype(float) / np.maximum(r, 1) ** power
    w = rng.normal(size=(S, S)) + 1j * rng.normal(size=(S, S))
    n = np.real(np.fft.ifft2(np.fft.fft2(np.real(np.fft.ifft2(w))) * band))
    return (n - n.mean()) / (n.std() + 1e-12)


low = noise(2, 8, 1.0)        # drifts across the tile (a block or two)
mid = noise(8, 40, 1.0)       # wind-packed patches
fine = noise(40, 160, 0.6)    # the grain
grain = noise(160, 256, 0.0)  # crystals
height = 0.45 * low + 0.35 * mid + 0.15 * fine + 0.05 * grain

# albedo: bright, very slightly cool snow; hollows a touch bluer, crystals a touch brighter
base = np.array([241.0, 245.0, 251.0])
lum = 1.0 + 0.016 * low + 0.012 * mid + 0.014 * fine + 0.010 * grain
alb = base[None, None, :] * lum[..., None]
cold = np.clip(-height, 0, None)[..., None] * np.array([-3.0, -1.5, 0.5])
alb = alb + cold
spark = grain > 2.6                     # ~0.5 % of texels: ice crystals
alb[spark] = [255, 255, 255]
dull = grain < -2.9                     # a few grey-blue shadowed crystals
alb[dull] = alb[dull] * np.array([0.93, 0.95, 0.99])
alb = np.clip(alb, 0, 255).astype(np.uint8)
Image.fromarray(alb, 'RGB').convert('RGBA').save(os.path.join(OUT, 'frontier_snow.png'))

# labPBR normal: RG = normal xy, B = ambient occlusion, A = height
hn = height * 1.6 + 0.5 * grain
gx = (np.roll(hn, -1, 1) - np.roll(hn, 1, 1)) * 0.5
gy = (np.roll(hn, -1, 0) - np.roll(hn, 1, 0)) * 0.5
k = 0.12
nx, ny, nz = -gx * k, -gy * k, np.ones_like(gx)
ln = np.sqrt(nx ** 2 + ny ** 2 + nz ** 2)
nx, ny = nx / ln, ny / ln
ao = np.clip(1.0 - 0.10 * np.clip(-height, 0, None), 0.8, 1.0)
ht = np.clip(0.75 + 0.12 * height, 0, 1)
nrm = np.stack([(nx * 0.5 + 0.5) * 255, (ny * 0.5 + 0.5) * 255, ao * 255, ht * 255], -1).astype(np.uint8)
Image.fromarray(nrm, 'RGBA').save(os.path.join(OUT, 'frontier_snow_n.png'))

# labPBR specular: R = perceptual smoothness, G = F0 (ice ~2 %), B = subsurface scattering (65..255), A = emission (255 = none)
smooth = np.clip(70 + 18 * fine, 40, 110)
smooth[spark] = 245
f0 = np.full((S, S), 5.0)
f0[spark] = 12
sss = np.full((S, S), 190.0)
spec = np.stack([smooth, f0, sss, np.full((S, S), 255.0)], -1).astype(np.uint8)
Image.fromarray(spec, 'RGBA').save(os.path.join(OUT, 'frontier_snow_s.png'))

# the glint: a tiny four-pointed star with a soft core (8 x 8)
g = np.zeros((8, 8, 4))
for y in range(8):
    for x in range(8):
        dx, dy = x - 3.5, y - 3.5
        r = np.hypot(dx, dy)
        star = max(0.0, 1.0 - abs(dx) * 0.9) * max(0.0, 1.0 - abs(dy) / 4.0) + max(0.0, 1.0 - abs(dy) * 0.9) * max(0.0, 1.0 - abs(dx) / 4.0)
        a = np.clip(max(star * 0.8, np.exp(-r * r / 1.2)), 0, 1)
        g[y, x] = [255, 255, 255, a * 255]
Image.fromarray(g.astype(np.uint8), 'RGBA').save(os.path.join(PART, 'snow_glint.png'))
print('albedo mean', alb.reshape(-1, 3).mean(0).round(1), 'sparkle texels %.2f%%' % (spark.mean() * 100))
