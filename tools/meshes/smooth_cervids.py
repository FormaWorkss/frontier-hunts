#!/usr/bin/env python3
"""[1.1.6] Smoother whitetail / elk / moose meshes for the Ultra look - data only, no renderer code changes.

Reads the shipped FHSK0001 deer meshes from the base jar and writes improved copies to
patch/assets/frontierhunts/models/entity/<name>.fhsk:

1. Taubin smoothing (non-shrinking) of the body surface, on welded positions so UV seams stay closed. Antlers, ears,
   tail, eye/cap regions and boundary / non-manifold vertices are left exactly where they are. This takes the
   lumpy, crumpled look off the elk mane and the moose neck without losing the silhouette.
2. Per-LOD normals. The three lower LODs used to share the full-detail mesh's normals, so a decimated surface was
   shaded as if it still had the missing detail - the faceted, blocky look at mid range. Every lower LOD now gets its
   own copy of the vertices it uses (appended after the originals, same skin weights) with angle-weighted normals
   computed from its own triangles. LOD 0 vertex indices are unchanged.
3. Elk LOD 3 was a duplicate of LOD 2; it stays (the renderer expects four LODs).

usage: smooth_cervids.py <base jar> <repo root>
"""
import io
import struct
import sys
import zipfile

import numpy as np

SPECIES = {  # name: Taubin iterations
    'whitetail_v3': 2,
    'elk': 4,
    'moose': 3,
}
VDT = np.dtype([('p', '>f4', 3), ('n', '>f4', 3), ('uv', '>f4', 2), ('c', '>f4', 3), ('j', 'u1', 4), ('w', 'u1', 4), ('r', 'i1')])
KEEP_REGIONS = 2 | 4 | 8 | 16 | 32 | 64  # ear, tail, cap, antler, female cap, male only


def parse(d):
    o = 0

    def rd(fmt):
        nonlocal o
        v = struct.unpack_from('>' + fmt, d, o)
        o += struct.calcsize('>' + fmt)
        return v

    def utf():
        nonlocal o
        n, = rd('H')
        o += n

    assert d[:8] == b'FHSK0001'
    o = 8
    nb, = rd('i')
    for _ in range(nb):
        utf()
        rd('i')
        rd('3f')
        rd('4f')
        rd('16f')
    rd('16f')
    rd('f')
    head_end = o
    nv, = rd('i')
    v = np.frombuffer(d, dtype=VDT, count=nv, offset=o).copy()
    o += nv * VDT.itemsize
    nl, = rd('i')
    lods = []
    for _ in range(nl):
        mode, nt = rd('ii')
        f = np.frombuffer(d, dtype='>i4', count=nt * 3, offset=o).reshape(-1, 3).astype(np.int64)
        o += nt * 12
        lods.append((mode, f))
    return d[:head_end], v, lods, d[o:]


def write(head, v, lods, tail):
    out = io.BytesIO()
    out.write(head)
    out.write(struct.pack('>i', len(v)))
    out.write(v.astype(VDT).tobytes())
    out.write(struct.pack('>i', len(lods)))
    for mode, f in lods:
        out.write(struct.pack('>ii', mode, len(f)))
        out.write(f.astype('>i4').tobytes())
    out.write(tail)
    return out.getvalue()


def weld(p):
    key = np.round(p / 1e-5).astype(np.int64)
    _, idx, inv = np.unique(key, axis=0, return_index=True, return_inverse=True)
    return idx, inv.ravel()


def edges(fw):
    e = np.concatenate([fw[:, [0, 1]], fw[:, [1, 2]], fw[:, [2, 0]]])
    e.sort(axis=1)
    u, c = np.unique(e, axis=0, return_counts=True)
    return u, c


def taubin(pw, fw, movable, iters, lam=0.5, mu=-0.53):
    u, c = edges(fw)
    n = len(pw)
    # vertices on boundary / non-manifold edges stay put
    bad = np.zeros(n, bool)
    bad[u[c != 2].ravel()] = True
    a, b = u[:, 0], u[:, 1]
    deg = np.bincount(a, minlength=n) + np.bincount(b, minlength=n)
    move = movable & ~bad & (deg > 0)  # vertices no LOD 0 triangle uses stay put
    p = pw.copy()
    for _ in range(iters):
        for f in (lam, mu):
            s = np.zeros_like(p)
            np.add.at(s, a, p[b])
            np.add.at(s, b, p[a])
            lap = s / np.maximum(deg, 1)[:, None] - p
            p[move] += f * lap[move]
    return p


def normals(pw, fw):
    """angle-weighted vertex normals on welded positions"""
    acc = np.zeros_like(pw)
    fn = np.cross(pw[fw[:, 1]] - pw[fw[:, 0]], pw[fw[:, 2]] - pw[fw[:, 0]])
    fnu = fn / np.maximum(np.linalg.norm(fn, axis=1)[:, None], 1e-12)
    for k in range(3):
        a, b, c = pw[fw[:, k]], pw[fw[:, (k + 1) % 3]], pw[fw[:, (k + 2) % 3]]
        x, y = b - a, c - a
        cos = (x * y).sum(1) / np.maximum(np.linalg.norm(x, axis=1) * np.linalg.norm(y, axis=1), 1e-12)
        ang = np.arccos(np.clip(cos, -1, 1))
        np.add.at(acc, fw[:, k], fnu * ang[:, None])
    ln = np.linalg.norm(acc, axis=1)
    return acc / np.maximum(ln[:, None], 1e-12), ln > 1e-9


def process(data, iters):
    head, v, lods, tail = parse(data)
    nv0 = len(v)
    p = v['p'].astype(np.float64)
    idx, inv = weld(p)
    pw = p[idx]
    # a welded position may move only if every copy of it is plain body
    keep = np.zeros(len(pw), bool)
    np.logical_or.at(keep, inv, (v['r'].astype(np.int64) & KEEP_REGIONS) != 0)
    f0w = inv[lods[0][1]]
    pw2 = taubin(pw, f0w, ~keep, iters)
    moved = np.linalg.norm(pw2 - pw, axis=1)
    v['p'] = pw2[inv].astype(np.float32)
    # LOD 0 normals from the smoothed full mesh (smooth across UV seams; antler normals kept as shipped)
    n0, ok0 = normals(pw2, f0w)
    body = ((v['r'].astype(np.int64) & 16) == 0) & ok0[inv]
    nn = v['n'].copy()
    nn[body] = n0[inv][body]
    v['n'] = nn
    out_lods = [lods[0]]
    extra = []
    base = nv0
    for mode, f in lods[1:]:
        used = np.unique(f)
        remap = np.full(nv0, -1, np.int64)
        remap[used] = np.arange(len(used)) + base
        copy = v[used].copy()
        fw = inv[f]
        nl, okl = normals(pw2, fw)
        ant = (copy['r'].astype(np.int64) & 16) != 0
        use = okl[inv[used]] & ~ant
        cn = copy['n'].copy()
        cn[use] = nl[inv[used]][use]
        copy['n'] = cn
        extra.append(copy)
        out_lods.append((mode, remap[f]))
        base += len(used)
    v_all = np.concatenate([v] + extra)
    stats = dict(verts=(nv0, len(v_all)), moved_mm=(float(np.percentile(moved, 50) * 1000), float(np.percentile(moved, 99) * 1000), float(moved.max() * 1000)))
    return write(head, v_all, out_lods, tail), stats


def main():
    jar, repo = sys.argv[1], sys.argv[2]
    z = zipfile.ZipFile(jar)
    for name, iters in SPECIES.items():
        data = z.read(f'assets/frontierhunts/models/entity/{name}.fhsk')
        out, st = process(data, iters)
        # round trip
        h, v, l, t = parse(out)
        assert len(v) == st['verts'][1] and len(l) == 4 and t == parse(data)[3]
        dst = f'{repo}/patch/assets/frontierhunts/models/entity/{name}.fhsk'
        open(dst, 'wb').write(out)
        print(name, 'verts %d -> %d' % st['verts'], 'moved median %.2f mm, p99 %.2f mm, max %.2f mm' % st['moved_mm'], len(data), '->', len(out), 'bytes')


if __name__ == '__main__':
    main()
