"""[meshes] the three levels side by side (close and mid-range): python3 lods.py <species> <out.png> [mode]"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import *
s = sys.argv[1]; out = sys.argv[2]; mode = sys.argv[3] if len(sys.argv) > 3 else 'sun'
tiles = []
for lod in ('ultra', 'mid', 'bal'):
    m = load_mesh('%s_%s' % (s, lod)); tex = load_tex(coat_for(s, lod == 'bal'))
    lo, hi = m.pos.min(0), m.pos.max(0); H = hi[1]; c = (lo + hi) / 2
    for dist, fov in ((2.2, 40), (8.0, 30)):
        eye = c + np.array([0.75, 0.35, -0.6]) * dist * max(H, 0.5) * 1.3
        img, _ = render(m.pos, m.nrm, m.uv, m.tris, tex, eye, c, size=(420, 315), mode=mode, fov=fov)
        tiles.append(label(to_img(img), '%s %s %d tris d=%.0fH' % (s, lod, len(m.tris), dist)))
sheet(tiles, 2).save(out)
