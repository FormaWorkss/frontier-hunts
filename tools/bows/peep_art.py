# [bows] Peep sight ring as seen at full draw: an out-of-focus dark rubber/aluminium ring with a soft inner edge,
# a faint cool highlight on its upper-left inner lip and a feathered outer fade (no visible quad edge).
import numpy as np
from PIL import Image
N = 256
c = (N - 1) / 2.0
y, x = np.mgrid[0:N, 0:N].astype(np.float64)
dx, dy = x - c, y - c
r = np.hypot(dx, dy) / (N / 2.0)          # 0 centre .. 1 at the quad's inscribed edge
ang = np.arctan2(dy, dx)

def smooth(e0, e1, v):
    t = np.clip((v - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

R_IN = 0.5                                  # inner aperture edge (the HUD scales the quad from this)
inner = smooth(R_IN - 0.04, R_IN + 0.06, r)          # blurry aperture edge
core = 1.0 - smooth(0.66, 0.84, r)                     # ring body
outer = (1.0 - smooth(0.80, 0.99, r)) * 0.42           # out-of-focus shadow beyond the ring
alpha = np.maximum(inner * core * 0.9, inner * outer)
alpha[r >= 0.995] = 0.0

base = np.full((N, N, 3), 18.0)
# brushed-metal lip highlight on the inner edge, upper left (light from above the shooter)
lip = np.exp(-((r - (R_IN + 0.05)) / 0.03) ** 2) * np.clip(np.cos(ang - (-2.35)), 0, 1) ** 2
base += lip[..., None] * np.array([46.0, 50.0, 56.0])
# very slight radial grain so it never reads as a flat vector circle
rng = np.random.default_rng(3)
grain = rng.normal(0, 1.2, (N, N))
base += grain[..., None]
img = np.dstack([np.clip(base, 0, 255), np.clip(alpha * 255, 0, 255)]).astype(np.uint8)
Image.fromarray(img, 'RGBA').save('/home/claude/work/bows/patch/assets/frontierhunts/textures/gui/bow_peep.png')

# preview on a meadow-ish background
bg = np.zeros((N, N, 3)); bg[..., 0] = 120; bg[..., 1] = 150; bg[..., 2] = 95
a = alpha[..., None]
prev = bg * (1 - a) + base * a
Image.fromarray(prev.astype(np.uint8)).resize((512, 512)).save('/tmp/claude-0/peep_preview.png')
print('ok')
