"""[meshes] highlight cut (split) vertices of a shipped mesh: python3 cuts_view.py <mesh> <out.png>"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import *
from collections import Counter
m = load_mesh(sys.argv[1])
k = np.round(m.pos * 1e5).astype(np.int64)
_, inv = np.unique(k, axis=0, return_inverse=True); inv = inv.ravel()
# a cut vertex: same position, different dominant bone chain among its copies
dom = m.bone[np.arange(len(m.pos)), np.argmax(m.weight, 1)]
groups = {}
for v, w in enumerate(inv):
    groups.setdefault(w, set()).add(int(dom[v]))
cut = np.array([len(groups[w]) > 1 and len({m.names[b][:2] for b in groups[w]}) > 1 for w in inv])
col = np.where(cut[:, None], np.array([1.0, 0.1, 0.1]), np.array([0.75, 0.75, 0.75]))
print('cut copies', cut.sum(), Counter(tuple(sorted(m.names[b] for b in groups[w])) for w in set(inv[cut])).most_common(8))
lo, hi = m.pos.min(0), m.pos.max(0); c = (lo + hi) / 2; e = max(hi - lo)
cams = {'rear-low': c + np.array([0.3 * e, -0.2 * e, 1.6 * e]), 'side': c + np.array([1.8 * e, 0.0, 0]),
        'below': c + np.array([0.4 * e, -1.4 * e, 0.3 * e]), 'front-low': c + np.array([0.4 * e, -0.1 * e, -1.6 * e])}
tiles = []
for n, eye in cams.items():
    img, _ = render(m.pos, m.nrm, m.uv, m.tris, None, eye, c, size=(420, 320), mode='sun', fov=32, vcol=col)
    tiles.append(label(to_img(img), sys.argv[1] + ' ' + n))
sheet(tiles, 4).save(sys.argv[2])
