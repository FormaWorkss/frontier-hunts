"""[meshes] dominant-bone colour view of a mesh: python3 weights_view.py <mesh name|path> <out.png> [--git]"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import *
import smooth
name = sys.argv[1]; out = sys.argv[2]
m = fhsk.parse(smooth.source_bytes(name)) if '--git' in sys.argv else load_mesh(name)
BASE = dict(pelvis=(0.2, 0.75, 0.2), spine=(0.95, 0.9, 0.2), chest=(1.0, 0.55, 0.1), neck=(0.9, 0.15, 0.15),
            head=(0.85, 0.3, 0.85), tail1=(0.2, 0.85, 0.9), tail2=(0.1, 0.3, 1.0), wing_l=(0.6, 0.4, 0.2), wing_r=(0.4, 0.6, 0.2),
            ear_l=(1, 1, 1), ear_r=(1, 1, 1))
LEG = dict(fl=(0.55, 0.35, 0.95), fr=(0.25, 0.55, 0.55), bl=(0.95, 0.45, 0.6), br=(0.45, 0.45, 0.25), l=(0.55, 0.35, 0.95), r=(0.95, 0.45, 0.6))
SEG = dict(upper=1.0, lower=0.8, foot=0.6, toe=0.4)
pal = np.zeros((len(m.names), 3))
for i, n in enumerate(m.names):
    if n in BASE:
        pal[i] = BASE[n]
    else:
        side, seg = n.split('_')
        pal[i] = np.array(LEG[side]) * SEG[seg]
# vertex colour = weight-blended bone colours, baked into a tiny texture via per-vertex UVs
W = np.zeros((len(m.pos), len(m.names)))
for k in range(4):
    W[np.arange(len(m.pos)), m.bone[:, k]] += m.weight[:, k]
col = W @ pal
tiles = []
for vn, (eye, tgt) in views_for(m.pos).items():
    if vn == 'head':
        continue
    img, _ = render(m.pos, m.nrm, m.uv, m.tris, None, eye, tgt, size=(420, 320), mode='mc', fov=30, vcol=col)
    tiles.append(label(to_img(img), name + ' ' + vn))
# flat (vertex colour interpolation is per pixel nearest; fine for a region view)
sheet(tiles, 4).save(out)
