#!/usr/bin/env python3
"""Offline check: where a sleeper lies in each compact tent vs the bed roll the tent model draws.

usage: python3 tools/tent/sleep_geometry.py [repo_root]   (reads merged62g8.jar overlaid with <repo>/patch)

Mirrors the Java: CompactTent.sleepOffset rotation, CompactTent.getBedDirection, TentSleepPose (origin =
bed cell centre + (0, 0.6875, 0) + sleepOffset), TentSleepView (eye = origin + head*0.12 + up*0.30), and the
vanilla lying player model (PlayerRenderer scale 0.9375; head a in [-0.117, 0.352], torso/arms/legs +-0.117 deep,
head +-0.234; a = along the head direction from the origin, feet at a = -1.523). For every tent and facing it
samples the tent mesh (shelter_faces of every cell model, rotated like the blockstates do) and reports:
pad surface under the torso+legs, body back height, gap, how deep any tent geometry cuts into the body
(the head is twice as deep as the torso, so like on a vanilla bed the back of the head sinks into the
pillow / bag hood; contact up to 0.06 above the back plane is expected and excluded), eye height above the pad and clearance.
"""
import json, sys, os, zipfile, math
import numpy as np

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
JAR = '/home/claude/fh/merged62g8.jar'
z = zipfile.ZipFile(JAR)


def read(path):
    p = os.path.join(R, 'patch', path)
    if os.path.exists(p):
        return json.load(open(p))
    return json.loads(z.read(path))


FACINGS = ['north', 'east', 'south', 'west']
HALF = 0.117          # half depth of the lying torso / limbs (4px * 0.9375 / 2)
EYE_FWD, EYE_UP = 0.12, 0.30


def rot_off(v, k):    # CompactTent.offset / sleepOffset: NORTH as is, then (x,z)->(-z,x) per clockwise step
    x, y, zz = v
    for _ in range(k):
        x, zz = -zz, x
    return np.array([x, y, zz], float)


def roty_model(p, deg):  # blockstate y rotation of a model about the block centre
    x, y, zz = p
    for _ in range((deg // 90) % 4):
        x, zz = 1 - zz, x
    return (x, y, zz)


def body_boxes():
    return {'head': (-0.117, 0.352, -0.234, 0.234, -0.234, 0.234), 'torso': (-0.820, -0.117, -0.234, 0.234, -HALF, HALF),
            'armL': (-0.820, -0.117, 0.234, 0.469, -HALF, HALF), 'armR': (-0.820, -0.117, -0.469, -0.234, -HALF, HALF),
            'legs': (-1.523, -0.820, -0.234, 0.234, -HALF, HALF)}


def sample(P, step=1 / 64):
    n1 = max(2, int(np.linalg.norm(P[1] - P[0]) / step) + 1); n2 = max(2, int(np.linalg.norm(P[2] - P[1]) / step) + 1)
    U, V = np.meshgrid(np.linspace(0, 1, n1), np.linspace(0, 1, n2)); U = U.ravel()[:, None]; V = V.ravel()[:, None]
    return (1 - U) * (1 - V) * P[0] + U * (1 - V) * P[1] + U * V * P[2] + (1 - U) * V * P[3]


def check(design, facing):
    k = FACINGS.index(facing)
    d = read(f'data/frontierhunts/compact_tents/{design}.json')
    bs = read(f'assets/frontierhunts/blockstates/{design}.json')['variants']
    pts = []
    for i, c in enumerate(d['cells']):
        v = bs[f'facing={facing},open=false,part={i}']
        m = read('assets/frontierhunts/models/' + v['model'].split(':')[1] + '.json')
        off = rot_off(c, k)
        for f in m.get('shelter_faces', []):
            P = np.array([roty_model(p[:3], v.get('y', 0)) for p in f['v']]) + off
            pts.append(sample(P))
    pts = np.concatenate(pts)
    bed = rot_off(d['cells'][d['bed']], k)
    face = rot_off((0, 0, -1), k)
    bd = d.get('bed_dir', 'back')
    head = {'front': face, 'right': np.array([-face[2], 0, face[0]]), 'left': np.array([face[2], 0, -face[0]])}.get(bd, -face)
    lat = np.array([-head[2], 0, head[0]])
    T = bed + np.array([0.5, 0.6875, 0.5]) + rot_off(d['sleep'], k)
    q = pts - T
    L = np.stack([q @ head, q @ lat, q[:, 1]], 1)
    under = (L[:, 0] > -1.523) & (L[:, 0] < -0.117) & (np.abs(L[:, 1]) < 0.234) & (L[:, 2] < 0.0)
    pad = pts[under, 1].max()
    worst = {}
    for name, (a0, a1, b0, b1, h0, h1) in body_boxes().items():
        msk = (L[:, 0] > a0) & (L[:, 0] < a1) & (L[:, 1] > b0) & (L[:, 1] < b1) & (L[:, 2] > h0) & (L[:, 2] < h1)
        if name == 'head':
            msk &= ~(L[:, 2] < -HALF + 0.06)   # pillow / bag hood under the back of the head (up to 1px proud of the pad)
        if msk.any():
            Q = L[msk]
            dd = np.min(np.stack([Q[:, 0] - a0, a1 - Q[:, 0], Q[:, 1] - b0, b1 - Q[:, 1], Q[:, 2] - h0, h1 - Q[:, 2]]), 0)
            worst[name] = float(dd.max())
    eye = T + head * EYE_FWD + np.array([0, EYE_UP, 0])
    clear = float(np.sqrt(((pts - eye) ** 2).sum(1)).min())
    feet = T - head * 1.523; top = T + head * 0.352
    return dict(origin=T, pad=pad, back=T[1] - HALF, gap=T[1] - HALF - pad, clip=worst, eye=eye, eye_above_pad=eye[1] - pad,
                clear=clear, feet=feet, top=top, head=head)


if __name__ == '__main__':
    bad = 0
    for design in ('solo_ridge_tent', 'backpacker_dome_tent', 'hunters_canvas_tent'):
        ref = None
        for facing in FACINGS:
            r = check(design, facing)
            clip = max(r['clip'].values()) if r['clip'] else 0.0
            print(f"{design:22s} {facing:5s} origin={np.round(r['origin'], 3)} pad_top={r['pad']:.4f} body_back={r['back']:.4f} "
                  f"gap={r['gap']:+.4f} max_clip={clip:.3f} {({k: round(v, 3) for k, v in r['clip'].items()})} "
                  f"eye_above_pad={r['eye_above_pad']:.3f} eye_clearance={r['clear']:.3f}")
            if not (0.0 <= r['gap'] <= 0.02) or clip > 0.02 or r['clear'] < 0.15:
                bad += 1
            key = (round(r['pad'], 3), round(r['gap'], 3), round(clip, 3), round(r['clear'], 2))
            if ref is None:
                ref = key
            elif key != ref:
                print('   !! differs from north', key, ref); bad += 1
    print('FAIL' if bad else 'OK', bad)
    sys.exit(1 if bad else 0)
