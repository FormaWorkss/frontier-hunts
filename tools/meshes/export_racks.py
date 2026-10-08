#!/usr/bin/env python3
"""[1.1.9] Export the sculpted elk and moose antlers as a rack the game can reshape per animal.

The shipped elk.fhsk / moose.fhsk keep every antler vertex (smooth_cervids2 drops only their triangles), so the game
reads positions, normals and UVs from the mesh it already has. This writes, per species,
patch/assets/frontierhunts/models/entity/<name>.fhrk:

    "FHRK0001"
    int vertexCount, then per vertex: int meshIndex, byte side (0 left, 1 right), float g, short branch
        g      = distance along the antler from the burr, over the longest on that side (0 burr .. 1 farthest tip)
        branch = the tine the vertex belongs to, -1 for the main beam (or the palm)
    int branchCount, then per branch: byte side, float[3] junction, float[3] axis (unit), float length, float junctionG,
        byte rank (0 = lowest tine on that side)
    int lodCount, then per LOD: int triangleCount, int[3*count] (local vertex indices)
    int anchorCount, then per anchor: int local vertex (main-beam surface where an extra point can grow)

Tines come from persistence on the distance field: walking down from the tips, a tine is born at its tip and dies where
it meets something longer; bumps shorter than the threshold are noise and fold into what they touch.

usage: export_racks.py <base jar with the sculpted antlers> <repo root>
"""
import heapq
import struct
import sys
import zipfile

import numpy as np

sys.path.insert(0, __file__.rsplit('/', 1)[0])
from smooth_cervids import parse, weld  # noqa: E402

ANTLER = 16
# name: (tine threshold in metres, anchor spacing)
SPECIES = {'elk': (0.06, 7), 'moose': (0.045, 9)}


def build(data, thresh, anchor_every):
    head, v, lods, tail = parse(data)
    p = v['p'].astype(np.float64)
    r = v['r'].astype(np.int64)
    ant = np.where((r & ANTLER) != 0)[0]
    local = np.full(len(p), -1, np.int64)
    local[ant] = np.arange(len(ant))
    lod_faces = []
    for mode, f in lods:
        fa = f[((r[f] & ANTLER) != 0).all(1)]
        lod_faces.append(local[fa])
    P = p[ant]
    side = (P[:, 0] > 0).astype(np.int64)
    # welded graph over every LOD's antler triangles (UV seams closed)
    idx, inv = weld(P)
    W = P[idx]
    nw = len(W)
    adj = [dict() for _ in range(nw)]
    for f in lod_faces:
        fw = inv[f]
        for a, b in ((0, 1), (1, 2), (2, 0)):
            for x, y in zip(fw[:, a], fw[:, b]):
                if x != y:
                    d = float(np.linalg.norm(W[x] - W[y]))
                    adj[x][y] = d
                    adj[y][x] = d
    wside = np.zeros(nw, np.int64)
    wside[inv] = side
    # the burr: on each side, the welded points closest to where the antler leaves the head (lowest, innermost)
    g = np.full(nw, np.inf)
    heap = []
    for s in (0, 1):
        ids = np.where(wside == s)[0]
        lat = np.abs(W[ids, 0])
        key = W[ids, 1] + 0.6 * lat  # low and close to the midline
        base = ids[key <= key.min() + 0.035]
        for b in base:
            g[b] = 0.0
            heapq.heappush(heap, (0.0, int(b)))
    while heap:
        d, x = heapq.heappop(heap)
        if d > g[x]:
            continue
        for y, w in adj[x].items():
            nd = d + w
            if nd < g[y]:
                g[y] = nd
                heapq.heappush(heap, (nd, y))
    unreached = ~np.isfinite(g)
    if unreached.any():
        # islands (loose bits of the sculpt): take the distance of the nearest reached point
        ok = np.where(~unreached)[0]
        for x in np.where(unreached)[0]:
            j = ok[np.argmin(((W[ok] - W[x]) ** 2).sum(1))]
            g[x] = g[j] + float(np.linalg.norm(W[j] - W[x]))
    gmax = np.array([g[wside == s].max() for s in (0, 1)])
    # persistence: superlevel sets of g, merged from the tips down
    order = np.argsort(-g)
    comp = -np.ones(nw, np.int64)
    parent = {}
    birth = {}

    def find(c):
        while parent[c] != c:
            parent[c] = parent[parent[c]]
            c = parent[c]
        return c
    label = -np.ones(nw, np.int64)
    branches = {}  # root id -> (tip, junction g)
    for x in order:
        roots = {find(comp[y]) for y in adj[x] if comp[y] >= 0}
        if not roots:
            comp[x] = x
            parent[x] = x
            birth[x] = g[x]
            label[x] = x
            continue
        roots = sorted(roots, key=lambda c: -birth[c])
        keep = roots[0]
        for c in roots[1:]:
            if birth[c] - g[x] >= thresh:
                branches[c] = (c, float(g[x]))
            parent[c] = keep
        comp[x] = keep
        label[x] = keep
    # every vertex: its own component at the time it joined, then follow noise merges up to a real tine or the beam
    final = np.empty(nw, np.int64)
    for x in range(nw):
        c = label[x]
        # walk the parent chain until a recorded tine or the side's survivor
        while c not in branches and parent[c] != c:
            c = parent[c]
        final[x] = c if c in branches else -1
    # branch geometry
    bid = {c: i for i, c in enumerate(sorted(branches, key=lambda c: (wside[c], branches[c][1])))}
    out_branches = []
    for c, i in sorted(bid.items(), key=lambda kv: kv[1]):
        tip, jg = branches[c]
        mem = np.where(final == c)[0]
        ring = mem[g[mem] <= jg + 0.02]
        if len(ring) == 0:
            ring = mem[np.argsort(g[mem])[:6]]
        J = W[ring].mean(0)
        T = W[tip]
        ax = T - J
        ln = float(np.linalg.norm(ax))
        out_branches.append([int(wside[tip]), J, ax / max(ln, 1e-9), ln, jg / gmax[wside[tip]], 0])
    for s in (0, 1):
        ids = [i for i, b in enumerate(out_branches) if b[0] == s]
        for rank, i in enumerate(sorted(ids, key=lambda i: out_branches[i][4])):
            out_branches[i][5] = rank
    vbranch = np.array([bid[final[inv[k]]] if final[inv[k]] >= 0 else -1 for k in range(len(P))], np.int64)
    gn = g[inv] / gmax[side]
    # anchors for extra points: main beam between a fifth and four fifths of the way out, every few points
    cand = np.where((vbranch < 0) & (gn > 0.2) & (gn < 0.8))[0]
    anchors = cand[::anchor_every]
    out = bytearray(b'FHRK0001')
    out += struct.pack('>i', len(P))
    for k in range(len(P)):
        out += struct.pack('>ibfh', int(ant[k]), int(side[k]), float(gn[k]), int(vbranch[k]))
    out += struct.pack('>i', len(out_branches))
    for s, J, ax, ln, jg, rank in out_branches:
        out += struct.pack('>b3f3fffb', s, *map(float, J), *map(float, ax), float(ln), float(jg), rank)
    out += struct.pack('>i', len(lod_faces))
    for f in lod_faces:
        out += struct.pack('>i', len(f))
        out += f.astype('>i4').tobytes()
    out += struct.pack('>i', len(anchors))
    out += anchors.astype('>i4').tobytes()
    stats = dict(vertices=len(P), branches=[(b[0], b[5], round(b[3], 3), round(b[4], 2)) for b in out_branches],
                 tris=[len(f) for f in lod_faces], anchors=len(anchors), gmax=gmax.round(3).tolist())
    return bytes(out), stats


def main():
    jar, repo = sys.argv[1], sys.argv[2]
    z = zipfile.ZipFile(jar)
    for name, (th, every) in SPECIES.items():
        data = z.read(f'assets/frontierhunts/models/entity/{name}.fhsk')
        out, st = build(data, th, every)
        open(f'{repo}/patch/assets/frontierhunts/models/entity/{name}.fhrk', 'wb').write(out)
        print(name, st['vertices'], 'vertices', st['tris'], 'triangles per LOD', st['anchors'], 'anchors, longest', st['gmax'])
        for s in (0, 1):
            print('   side', s, [(b[1], b[2], b[3]) for b in st['branches'] if b[0] == s])


if __name__ == '__main__':
    main()
