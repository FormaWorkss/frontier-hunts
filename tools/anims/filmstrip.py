"""[anims] Side-view filmstrips of the gait dumps (GaitAudit -Dgait.dump=dir): 8 frames of a walk/trot/gallop with the
ground line and paw tracks, coat-textured, at in-game-like distance. usage: filmstrip.py <dump dir> <out dir> [names...]"""
import os, sys, struct, glob
import numpy as np
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, '..', 'meshes'))
import render as R  # noqa: E402


def frames(path):
    d = open(path, 'rb').read()
    nv = struct.unpack_from('>i', d, 0)[0]
    o = 4
    out = []
    while o < len(d):
        x = struct.unpack_from('>f', d, o)[0]; o += 4
        a = np.frombuffer(d, '>f4', nv * 6, o).reshape(nv, 6).astype(np.float32); o += nv * 24
        out.append((x, a))
    return out


def strip(name, dump_dir, out_dir, n=8, size=(300, 230)):
    sp = name.rsplit('_', 1)[0]
    m = R.load_mesh(sp + '_ultra')
    tex = R.load_tex(R.coat_for(sp))
    fr = frames(os.path.join(dump_dir, name + '.bin'))
    T = np.asarray(m.tris).reshape(-1, 3)
    UV = np.asarray(m.uv)
    step = max(1, len(fr) // n)
    tiles = []
    P0 = fr[0][1][:, :3]
    lo, hi = P0.min(0), P0.max(0)
    ext = max(hi - lo)
    for i in range(0, min(len(fr), n * step), step):
        x, a = fr[i]
        P, N = a[:, :3].copy(), a[:, 3:].copy()
        c = np.array([0.0, (hi[1]) * 0.45, (lo[2] + hi[2]) / 2])
        eye = c + np.array([ext * 2.2, 0.08 * ext, 0])
        img, zb = R.render(P, N, UV, T, tex, eye, c, size=size, fov=30, mode='sun')
        im = R.to_img(img)
        d = ImageDraw.Draw(im)
        # ground line y=0
        Rm, e = R.look_at(eye, c)
        def proj(p):
            C = (np.asarray(p) - e) @ Rm.T
            f = 0.5 * size[1] / np.tan(np.radians(30) / 2)
            return size[0] / 2 + f * C[0] / -C[2], size[1] / 2 - f * C[1] / -C[2]
        g0, g1 = proj([0, 0, lo[2] - 0.4]), proj([0, 0, hi[2] + 0.4])
        d.line([g0, g1], fill=(90, 70, 50), width=2)
        # world-fixed ticks every 0.25 blocks (the ground moving under the animal): planted paws stay on a tick
        for k in range(-12, 13):
            wz = round((x) / 0.25) * 0.25 + k * 0.25
            mz = -(wz - x)  # model z of a world point
            if lo[2] - 0.4 < mz < hi[2] + 0.4:
                p = proj([0, 0, mz]); d.line([p[0], p[1] - 3, p[0], p[1] + 3], fill=(60, 40, 20))
        R.label(im, f'{name} {i}')
        tiles.append(im)
    sh = R.sheet(tiles, 4)
    sh.save(os.path.join(out_dir, name + '.png'))
    return sh


if __name__ == '__main__':
    dd, od = sys.argv[1], sys.argv[2]
    os.makedirs(od, exist_ok=True)
    names = sys.argv[3:] or [os.path.basename(p)[:-4] for p in sorted(glob.glob(os.path.join(dd, '*.bin')))]
    for nm in names:
        strip(nm, dd, od)
        print('wrote', nm)
