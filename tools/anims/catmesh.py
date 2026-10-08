"""[anims] Lion mane clean-up on the shipped (gear.16) Ultra and mid bodies, without redoing them.

The scan's mane was decimated into deep crevices and lumpy blobs: at 3-6 blocks it read as faceted clay, not a full
mane. Here, on the mane only (vertices carried by the neck / chest / back of the head, behind the face), with a soft
falloff into the face and the shoulders:
  * Taubin (lambda|mu) smoothing that keeps volume, extra relaxation of concave crevices (they fill in, the crests stay),
  * a little more volume: the mane pushed out along its normal (most at its middle, none at its edges),
  * normals recomputed on the welded surface over the edited region (no seams, no facets).
Positions and normals only: UVs, skin weights, bones and triangle lists are untouched (the rig, the LOD swap and the
coat stay compatible). Sources: git gear.16 (6cbba9d); output patch/.  usage: catmesh.py
"""
import os, sys, gzip, struct, subprocess
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
sys.path.insert(0, os.path.join(REPO, 'tools', 'wingshot'))
import fhsk  # noqa: E402

REL = 'patch/assets/frontierhunts/models/wildlife/'
BASE = '6cbba9d'
REC = np.dtype([('p', '>f4', 3), ('n', '>f4', 3), ('uv', '>f4', 2), ('b', 'u1', 4), ('w', 'u1', 4)])


def raw(name):
    data = subprocess.run(['git', '-C', REPO, 'show', '%s:%s%s.fhsk' % (BASE, REL, name)], check=True, capture_output=True).stdout
    return data


def split(data):
    """(header bytes, vertex records, tail bytes) of an .fhsk"""
    b = gzip.decompress(data)
    o = 8 + 32
    nb, = struct.unpack_from('>h', b, o); o += 2
    v2 = struct.unpack_from('>i', b, 0)[0] == 0x46485332
    for _ in range(nb):
        ln, = struct.unpack_from('>h', b, o); o += 2 + ln + 2 + (36 if v2 else 24)
    nv, = struct.unpack_from('>i', b, o); o += 4
    rec = np.frombuffer(b, REC, nv, o).copy()
    return b[:o], rec, b[o + nv * REC.itemsize:]


def process(name):
    data = raw(name)
    m = fhsk.parse(data)
    head, rec, tail = split(data)
    P = rec['p'].astype(np.float64)
    k = np.round(P / 1e-5).astype(np.int64)
    _, idx, inv = np.unique(k, axis=0, return_index=True, return_inverse=True)
    inv = inv.ravel(); n = len(idx)
    Q = P[idx].copy()
    F = inv[m.tris]
    nb = [set() for _ in range(n)]
    for a, b, c in F:
        nb[a].update((b, c)); nb[b].update((a, c)); nb[c].update((a, b))
    nbl = [np.fromiter(s, np.int64) for s in nb]
    # mane mask (welded): neck / chest / head bones, behind the face, fading in over the face and onto the shoulders
    nm = m.names
    dom = m.bone[np.arange(len(P)), np.argmax(m.weight, 1)]
    hv = m.pos[dom == nm.index('head')]
    nose = hv[:, 2].min()
    hj = m.joint[nm.index('head')]; cj = m.joint[nm.index('chest')]
    H = m.pos[:, 1].max()
    on = np.isin(dom, [nm.index('neck'), nm.index('chest'), nm.index('head')]).astype(float)
    wv = np.zeros(n); np.maximum.at(wv, inv, on)
    z = Q[:, 2]
    face = np.clip((z - (hj[2] - 0.02 * H)) / (0.08 * H), 0, 1)          # 0 on the face, 1 behind the cheeks
    back = np.clip(((cj[2] + 0.12 * H) - z) / (0.1 * H), 0, 1)          # fades out over the shoulders
    low = np.clip((Q[:, 1] - 0.3 * H) / (0.1 * H), 0, 1)                # not down the forelegs
    w = wv * face * back * low
    for _ in range(3):   # soften the mask
        w = 0.5 * w + 0.5 * np.array([w[x].mean() if len(x) else 0 for x in nbl]) * (w > 0.0) + 0.0
    print(name, 'mane vertices', int((w > 0.05).sum()), 'of', n)

    def lap(X):
        return np.array([X[x].mean(0) if len(x) else X[i] for i, x in enumerate(nbl)]) - X

    def normals(X):
        fn = np.cross(X[F[:, 1]] - X[F[:, 0]], X[F[:, 2]] - X[F[:, 0]])
        N = np.zeros_like(X)
        for c in range(3):
            np.add.at(N, F[:, c], fn)
        return N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-12)

    N0 = normals(Q)
    for it in range(8):
        L = lap(Q)
        # crevices (Laplacian pointing out of the surface = concave) relax harder than crests
        conc = np.clip((L * N0).sum(1) / (0.004 * H), 0, 1)
        Q = Q + (0.5 + 0.3 * conc)[:, None] * w[:, None] * L
        Q = Q - 0.53 * w[:, None] * lap(Q)
    N1 = normals(Q)
    Q = Q + N1 * (0.012 * H * w ** 1.5)[:, None]
    N1 = normals(Q)
    edited = w > 0.01
    newP = Q[inv]
    rec['p'] = np.where(edited[inv][:, None], newP, P).astype(np.float32)
    old_n = rec['n'].astype(np.float64)
    blend = np.clip(w[inv] * 4, 0, 1)[:, None]
    nn = old_n * (1 - blend) + N1[inv] * blend
    rec['n'] = (nn / (np.linalg.norm(nn, axis=1, keepdims=True) + 1e-12)).astype(np.float32)
    out = head + rec.tobytes() + tail
    with gzip.open(os.path.join(REPO, REL, name + '.fhsk'), 'wb', compresslevel=9) as f:
        f.write(out)
    moved = np.linalg.norm(newP - P, axis=1)[edited[inv]]
    print('  moved: mean %.4f max %.4f blocks' % (moved.mean(), moved.max()))


if __name__ == '__main__':
    for nm in ('lion_ultra', 'lion_mid'):
        process(nm)
