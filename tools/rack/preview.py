#!/usr/bin/env python3
"""[1.1.8] Preview procedural racks on the Ultra cervid meshes (rest pose): python3 preview.py <dir> <out.png> name:species ..."""
import os, struct, subprocess, sys
import numpy as np
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
sys.path.insert(0, os.path.join(REPO, 'tools', 'meshes'))
from render import render, load_tex, to_img, label, sheet  # noqa
from smooth_cervids import parse  # noqa

# head-bone inverse bind and antler frame (DeerSkeleton, rows as printed by JOML)
SK = {
    'whitetail': ([[0.9998, -0.009768, 0.01874, 0.02397], [0.009768, -0.5729, -0.8196, 0.1406], [0.01874, 0.8196, -0.5727, -1.216], [0, 0, 0, 1]],
                  [[0.9998, -0.01357, 0.0162, 0.002089], [0.009768, -0.383, -0.9237, -0.02793], [0.01874, 0.9236, -0.3828, 0.03864], [0, 0, 0, 1]]),
    'elk': ([[1, 0, 0, 0], [0, 1, 0, -1.55], [0, 0, 1, 1.09], [0, 0, 0, 1]], [[1, 0, 0, 0.003898], [0, 1, 0, 0.04427], [0, 0, 1, -0.1173], [0, 0, 0, 1]]),
    'moose': ([[1, 0, 0, 0], [0, 1, 0, -1.546], [0, 0, 1, 1.163], [0, 0, 0, 1]], [[1, 0, 0, -0.01413], [0, 1, 0, 0.09279], [0, 0, 1, -0.06905], [0, 0, 0, 1]]),
}
MESH = {'whitetail': 'whitetail_v3', 'elk': 'elk', 'moose': 'moose'}


def load_rack(path):
    d = open(path, 'rb').read()
    nv, nt = struct.unpack_from('>ii', d, 0)
    o = 8
    P = np.frombuffer(d, '>f4', nv * 3, o).reshape(-1, 3).astype(float); o += nv * 12
    N = np.frombuffer(d, '>f4', nv * 3, o).reshape(-1, 3).astype(float); o += nv * 12
    UV = np.frombuffer(d, '>f4', nv * 2, o).reshape(-1, 2).astype(float); o += nv * 8
    S = np.frombuffer(d, '>f4', nv, o).astype(float); o += nv * 4
    T = np.frombuffer(d, '>i4', nt * 3, o).reshape(-1, 3).astype(np.int64)
    return P, N, UV, S, T


def place(species, P, N):
    ib, af = (np.array(m, float) for m in SK[species])
    M = np.linalg.inv(ib) @ af
    Pw = (M[:3, :3] @ P.T).T + M[:3, 3]
    Nw = (M[:3, :3] @ N.T).T
    return Pw, Nw


def views(species, rack):
    body = parse(open(os.path.join(REPO, 'patch/assets/frontierhunts/models/entity', MESH[species] + '.fhsk'), 'rb').read())
    v = body[1]
    T0 = body[2][0][1]
    keep = ((v['r'][T0] & 16) == 0).all(1)  # no shipped antlers
    T0 = T0[keep]
    P0, N0, UV0 = v['p'].astype(float), v['n'].astype(float), v['uv'].astype(float)
    coat = load_tex(os.path.join('/tmp/claude-0/mesh/assets/frontierhunts/textures/entity/realistic', species + '_coat_summer.png'))
    ant = load_tex('/tmp/claude-0/antler_tex.png')
    P, N, UV, S, T = rack
    Pw, Nw = place(species, P, N)
    head = Pw.mean(0)
    ext = max(np.ptp(Pw, 0).max(), 0.5)
    cams = {
        'side': (head + np.array([ext * 2.2, 0.0, 0.0]), head),
        'front': (head + np.array([0.3 * ext, 0.25 * ext, -2.2 * ext]), head),
        '3/4': (head + np.array([1.6 * ext, 0.9 * ext, -1.4 * ext]), head),
    }
    tiles = []
    for name, (eye, tgt) in cams.items():
        img, zb = render(P0, N0, UV0, T0, coat, eye, tgt, size=(420, 360), mode='sun')
        img, zb = render(Pw, Nw, UV, T, ant, eye, tgt, size=(420, 360), mode='sun', img=img, zbuf=zb)
        tiles.append(to_img(img))
    return tiles


if __name__ == '__main__':
    d, out = sys.argv[1], sys.argv[2]
    tiles = []
    for spec in sys.argv[3:]:
        name, species = spec.split(':')
        t = views(species, load_rack(os.path.join(d, name + '.rack')))
        tiles += [label(x, name) for x in t]
    sheet(tiles, 6).save(out)
