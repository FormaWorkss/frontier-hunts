"""[meshes] before/after close-up contact sheet: python3 compare.py <species> <out.png> [ultra|bal] [modes]"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import *
import smooth
s = sys.argv[1]; out = sys.argv[2]; lod = sys.argv[3] if len(sys.argv) > 3 else 'ultra'
modes = (sys.argv[4] if len(sys.argv) > 4 else 'clay,mc').split(',')
name = '%s_%s' % (s, lod)
before = fhsk.parse(smooth.source_bytes(name))
after = load_mesh(name) if '--shipped' in sys.argv else None
if after is None:
    k = dict(smooth.KNOBS['default']); k.update(smooth.KNOBS.get(s, {}))
    if lod == 'bal': k = dict(smooth.BAL_KNOBS)
    after, st = smooth.process(before, k, log=lambda *a: None)
tex = load_tex(coat_for(s, lod == 'bal'))
tiles = []
lo, hi = before.pos.min(0), before.pos.max(0); H = hi[1]; Lz = hi[2] - lo[2]
cams = [('3/4 close', np.array([0.9 + 0.7 * H, 0.95 * H + 0.3, lo[2] - 0.6 * Lz]), np.array([0, 0.62 * H, lo[2] + 0.35 * Lz]), 40),
        ('side', np.array([1.2 + 1.1 * Lz, 0.6 * H, 0.0]), np.array([0, 0.5 * H, 0]), 40),
        ('rear-top', np.array([-0.9 - 0.4 * H, 1.3 * H + 0.4, hi[2] + 0.7 * Lz]), np.array([0, 0.6 * H, 0.1 * Lz]), 40)]
for mode in modes:
    for nm, m in (('BEFORE', before), ('AFTER', after)):
        for cn, eye, tgt, fov in cams:
            img, _ = render(m.pos, m.nrm, m.uv, m.tris, tex, eye, tgt, size=(560, 420), mode=mode, fov=fov)
            tiles.append(label(to_img(img), '%s %s %s %s (%d tris)' % (s, nm, cn, mode, len(m.tris))))
sheet(tiles, 3).save(out)
