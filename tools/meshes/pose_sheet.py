"""[meshes] Contact sheet of posed meshes dumped by MeshAudit (tools/meshes/audit.sh <which> dump).

usage: python3 pose_sheet.py <dump dir> <species> <out.png> [mode=sun|mc|clay] [states,...] [--views side,3/4]
"""
import sys, os, struct
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import render, load_tex, coat_for, label, to_img, sheet  # noqa: E402


def read_bin(fn):
    b = open(fn, 'rb').read()
    nv, nt = struct.unpack_from('<ii', b, 0)
    o = 8
    P = np.frombuffer(b, '<f4', nv * 3, o).reshape(-1, 3).astype(np.float64); o += nv * 12
    N = np.frombuffer(b, '<f4', nv * 3, o).reshape(-1, 3).astype(np.float64); o += nv * 12
    UV = np.frombuffer(b, '<f4', nv * 2, o).reshape(-1, 2).astype(np.float64); o += nv * 8
    T = np.frombuffer(b, '<i4', nt * 3, o).reshape(-1, 3)
    return P, N, UV, T


def cams(P, which):
    lo, hi = P.min(0), P.max(0)
    c = (lo + hi) / 2
    ext = max(hi - lo)
    d = ext * 1.9
    out = []
    for w in which:
        if w == 'side':
            out.append((c + np.array([d, 0.08 * ext, 0]), c))
        elif w == '3/4':
            out.append((c + np.array([d * 0.7, d * 0.35, -d * 0.7]), c))
        elif w == 'front':
            out.append((c + np.array([0.2 * d, 0.15 * ext, -d]), c))
        elif w == 'rear':
            out.append((c + np.array([-0.35 * d, 0.3 * ext, d]), c))
        elif w == 'top':
            out.append((c + np.array([0.2 * d, d, 0.15 * d]), c))
    return out


if __name__ == '__main__':
    d, sp, out = sys.argv[1:4]
    mode = sys.argv[4] if len(sys.argv) > 4 and not sys.argv[4].startswith('--') else 'sun'
    states = sys.argv[5].split(',') if len(sys.argv) > 5 and not sys.argv[5].startswith('--') else None
    views = ['side', '3/4']
    for a in sys.argv:
        if a.startswith('--views='):
            views = a.split('=')[1].split(',')
    files = sorted(f for f in os.listdir(d) if f.startswith(sp + '_') and f.endswith('.bin'))
    names = [f[len(sp) + 1:-4] for f in files]
    if states:
        names = [n for n in states if n in names]
    tex = load_tex(coat_for(sp))
    tiles = []
    for n in names:
        P, N, UV, T = read_bin(os.path.join(d, '%s_%s.bin' % (sp, n)))
        for v, (eye, tgt) in zip(views, cams(P, views)):
            img, _ = render(P, N, UV, T, tex, eye, tgt, size=(400, 300), mode=mode, fov=30)
            tiles.append(label(to_img(img), '%s %s %s' % (sp, n, v)))
    sheet(tiles, len(views) * 3 if len(views) <= 2 else len(views)).save(out)
