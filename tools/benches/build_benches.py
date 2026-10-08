#!/usr/bin/env python3
"""[benchart] Build the three workbench models + baked atlases + item models.

python3 tools/benches/build_benches.py <repo> [bench ids...] [--noao]
"""
import json
import math
import os
import sys
import time

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from benchlib import split, cull_faces, Baker, write_bench  # noqa: E402
import materials as m  # noqa: E402

BENCHES = {}


def register():
    import frontier
    import gunsmith
    import reloading
    BENCHES['frontier_workbench'] = (frontier.build, 'oak')
    BENCHES['gunsmith_bench'] = (gunsmith.build, 'walnut')
    BENCHES['reloading_bench'] = (reloading.build, 'pine')


def wood_tile(pal):
    def fn(path):
        from benchlib import El, Mat
        e = El([0, 0, 0], [16, 16, 2], None, grain=0, seed=77)
        ys, xs = np.mgrid[0:32, 0:32]
        P = np.stack([(xs.ravel() + 0.5) / 2, 16 - (ys.ravel() + 0.5) / 2, np.full(1024, 2.0)], 1)
        ctx = dict(P=P, W=P, s=P[:, 0], t=16 - P[:, 1], fs=16, ft=16, face='south', n=np.array([0, 0, 1.0]), el=e)
        col = m.wood(pal)(ctx).reshape(32, 32, 3)
        Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8)).save(path)
    return fn


def rotm(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def fit(rot, bounds, target, yoff=0.0):
    """scale + translation (in 1/16 block) so the rotated model box is centred and its larger projected extent is
    `target` blocks"""
    lo, hi = np.array(bounds[0]) / 16 - 0.5, np.array(bounds[1]) / 16 - 0.5
    R = rotm('x', rot[0]) @ rotm('y', rot[1]) @ rotm('z', rot[2])
    C = np.array([[lo[0] if i & 1 == 0 else hi[0], lo[1] if i & 2 == 0 else hi[1], lo[2] if i & 4 == 0 else hi[2]] for i in range(8)])
    Q = C @ R.T
    ext = (Q.max(0) - Q.min(0))[:2].max()
    s = target / ext
    ctr = (Q.max(0) + Q.min(0)) / 2 * s
    t = -ctr * 16
    t[1] += yoff
    return [round(float(s), 4)] * 3, [round(float(v), 3) for v in t]


def item_model(repo, bid, bounds):
    disp = {}
    for key, rot, target, yoff in (
            ('gui', [28, 150, 0], 0.98, 0.0),
            ('ground', [0, 0, 0], 0.42, 2.0),
            ('fixed', [0, 180, 0], 0.9, 0.0),
            ('thirdperson_righthand', [75, 45, 0], 0.55, 2.5),
            ('thirdperson_lefthand', [75, 225, 0], 0.55, 2.5),
            ('firstperson_righthand', [0, 45, 0], 0.62, 1.5),
            ('firstperson_lefthand', [0, 225, 0], 0.62, 1.5),
            ('head', [0, 0, 0], 0.8, 0.0)):
        sc, tr = fit(rot, bounds, target, yoff)
        disp[key] = {'rotation': rot, 'translation': tr, 'scale': sc}
    body = {'parent': f'frontierhunts:block/{bid}_inventory', 'display': disp}
    d = os.path.join(repo, 'patch', 'assets', 'frontierhunts', 'models', 'item')
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, bid + '.json'), 'w') as fh:
        json.dump(body, fh, indent=1)


def main():
    repo = os.path.abspath(sys.argv[1])
    ids = [a for a in sys.argv[2:] if not a.startswith('--')]
    ao = '--noao' not in sys.argv
    register()
    stats = {}
    for bid in ids or list(BENCHES):
        t0 = time.time()
        build, pal = BENCHES[bid]
        sc = build()
        n0 = len(sc.els)
        L, R = split(sc.els)
        els = L + R
        removed = cull_faces(els)
        els = [e for e in els if e.faces]
        bk = Baker(els)
        bk.bake(ao=ao)
        counts = write_bench(repo, bid, els, bk, wood_tile(pal))
        lo = np.min([e.world_aabb()[0] for e in els], 0)
        hi = np.max([e.world_aabb()[1] for e in els], 0)
        item_model(repo, bid, (lo, hi))
        nf = sum(len(e.faces) for e in els)
        stats[bid] = dict(authored=n0, faces=nf, culled=removed, **counts, atlas=bk.size, bounds=[lo.tolist(), hi.tolist()])
        print(bid, stats[bid], f'{time.time() - t0:.1f}s')
    out = os.path.join(repo, 'tools', 'benches', 'stats.json')
    old = json.load(open(out)) if os.path.exists(out) else {}
    old.update(stats)
    json.dump(old, open(out, 'w'), indent=1)


if __name__ == '__main__':
    main()
