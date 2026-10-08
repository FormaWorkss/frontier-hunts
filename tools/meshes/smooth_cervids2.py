#!/usr/bin/env python3
"""[1.1.8] Smooth whitetail / elk / moose skin for the Ultra look: no lumps, no little shaded facets.

Supersedes smooth_cervids.py (1.1.6). Starts again from the shipped base meshes (FHSK0001 in the base jar) and writes
patch/assets/frontierhunts/models/entity/<name>.fhsk:

1. Stronger Taubin smoothing (non-shrinking) of the body on welded positions, so the sculpt's small lumps go and UV
   seams stay closed. Antler, cap (pedicles) and tail stay exactly as shipped; boundary and non-manifold vertices too.
2. Ears get a light pass of their own (rims fixed), which takes the crumpled look off them.
3. One smooth normal field for every LOD: angle-weighted normals of the smoothed full mesh, then relaxed over the
   surface. A decimated LOD reuses the normals of the same points on the full surface, so its big triangles shade as
   one curved hide instead of a patchwork of flat facets (what showed as "little shapes" under shader packs).

usage: smooth_cervids2.py <base jar> <repo root>
"""
import sys
import zipfile

import numpy as np

sys.path.insert(0, __file__.rsplit('/', 1)[0])
from smooth_cervids import parse, write, weld, edges, taubin, normals  # noqa: E402

# name: (body Taubin iterations, ear iterations, normal relax iterations)
SPECIES = {
    'whitetail_v3': (9, 3, 4),
    'elk': (11, 3, 4),
    'moose': (10, 3, 4),
}
EAR, TAIL, CAP, ANTLER, FCAP, MALE = 2, 4, 8, 16, 32, 64


def relax_normals(n, fw, iters):
    """average each normal with its neighbours (edge-connected) a few times, renormalising: a smooth normal field"""
    u, _ = edges(fw)
    a, b = u[:, 0], u[:, 1]
    deg = np.bincount(a, minlength=len(n)) + np.bincount(b, minlength=len(n))
    m = n.copy()
    for _ in range(iters):
        s = m.copy()
        np.add.at(s, a, m[b])
        np.add.at(s, b, m[a])
        s /= (1 + deg)[:, None]
        ln = np.linalg.norm(s, axis=1)
        ok = ln > 1e-9
        m[ok] = s[ok] / ln[ok, None]
    return m


def process(data, body_it, ear_it, relax_it, drop_antlers=False):
    head, v, lods, tail = parse(data)
    nv0 = len(v)
    p = v['p'].astype(np.float64)
    idx, inv = weld(p)
    pw = p[idx]
    r = v['r'].astype(np.int64)
    flag_any = np.zeros(len(pw), np.int64)
    np.bitwise_or.at(flag_any, inv, r)
    f0w = inv[lods[0][1]]
    fixed = (flag_any & (TAIL | CAP | ANTLER | FCAP | MALE)) != 0
    is_ear = (flag_any & EAR) != 0
    # body first (ears held), then the ears alone (body held), each Taubin keeps boundaries fixed
    pw2 = taubin(pw, f0w, ~fixed & ~is_ear, body_it)
    pw2 = taubin(pw2, f0w, ~fixed & is_ear, ear_it)
    n0, ok0 = normals(pw2, f0w)
    nr = relax_normals(n0, f0w, relax_it)
    # points only a lower LOD uses (none of the full mesh's triangles touch them) follow the nearest full-mesh point:
    # same displacement, same normal - otherwise they poke out of the smoothed surface (dark ear tips at mid range)
    lone = np.where(~ok0)[0]
    anchor = np.where(ok0)[0]
    if len(lone) and len(anchor):
        for k in range(0, len(lone), 512):
            chunk = lone[k:k + 512]
            d2 = ((pw[chunk][:, None, :] - pw[anchor][None, :, :]) ** 2).sum(-1)
            near = anchor[np.argmin(d2, axis=1)]
            pw2[chunk] = pw[chunk] + (pw2[near] - pw[near])
            nr[chunk] = nr[near]
        ok0 = ok0.copy()
        ok0[lone] = True
    moved = np.linalg.norm(pw2 - pw, axis=1)
    v['p'] = pw2[inv].astype(np.float32)
    # antlers keep their shipped normals (crisp tines); everything else takes the smooth field
    body = ((r & ANTLER) == 0) & ok0[inv]
    nn = v['n'].copy()
    nn[body] = nr[inv][body].astype(np.float32)
    v['n'] = nn
    # lower LODs: same vertex indices into the full set (their own copies are no longer needed).
    # [1.1.8] elk and moose: the sculpted antlers come out of the body mesh - the procedural racks (client.rack) draw
    # every bull's antlers now, with variants, so the body keeps only the head
    out_lods = []
    for mode, f in lods:
        if drop_antlers:
            f = f[~((r[f] & ANTLER) != 0).all(1)]
        out_lods.append((mode, f))
    stats = dict(moved_mm=(float(np.percentile(moved, 50) * 1000), float(np.percentile(moved, 99) * 1000), float(moved.max() * 1000)))
    return write(head, v, out_lods, tail), stats


def main():
    jar, repo = sys.argv[1], sys.argv[2]
    z = zipfile.ZipFile(jar)
    for name, (bi, ei, ri) in SPECIES.items():
        data = z.read(f'assets/frontierhunts/models/entity/{name}.fhsk')
        out, st = process(data, bi, ei, ri, drop_antlers=name in ('elk', 'moose'))
        h, v, l, t = parse(out)
        assert len(l) == 4 and t == parse(data)[3]
        print('  triangles per LOD', [len(f) for _, f in l])
        dst = f'{repo}/patch/assets/frontierhunts/models/entity/{name}.fhsk'
        open(dst, 'wb').write(out)
        print(name, 'moved median %.2f mm, p99 %.2f mm, max %.2f mm' % st['moved_mm'], len(data), '->', len(out), 'bytes')


if __name__ == '__main__':
    main()
