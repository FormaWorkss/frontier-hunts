"""[meshes] zoomed before/after crops: python3 zoom.py <species> <out.png> [taubin] [budget]"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import *
import smooth
s = sys.argv[1]; out = sys.argv[2]
before = fhsk.parse(smooth.source_bytes(s + '_ultra'))
k = dict(smooth.KNOBS['default']); k.update(smooth.KNOBS.get(s, {}))
if len(sys.argv) > 3: k['taubin'] = int(sys.argv[3])
if len(sys.argv) > 4: k['budget'] = float(sys.argv[4])
after, st = smooth.process(before, k)
print(st)
tex = load_tex(coat_for(s))
lo, hi = before.pos.min(0), before.pos.max(0); H = hi[1]; Lz = hi[2] - lo[2]
head = np.array([0, 0.75 * H, lo[2] + 0.18 * Lz]); body = np.array([0, 0.62 * H, 0.1 * Lz])
cams = [('head', head + np.array([0.55 * H, 0.15 * H, -0.45 * H]), head, 45),
        ('shoulder', body + np.array([0.75 * H, 0.35 * H, -0.2 * H]), body + np.array([0, 0.1 * H, -0.15 * Lz]), 45),
        ('back', body + np.array([0.3 * H, 0.9 * H, 0.5 * H]), body, 45)]
tiles = []
for mode in ('clay', 'sun'):
    for nm, m in (('BEFORE', before), ('AFTER', after)):
        for cn, eye, tgt, fov in cams:
            img, _ = render(m.pos, m.nrm, m.uv, m.tris, tex, eye, tgt, size=(560, 420), mode=mode, fov=fov)
            tiles.append(label(to_img(img), '%s %s %s %s (%d tris)' % (s, nm, cn, mode, len(m.tris))))
sheet(tiles, 3).save(out)
