#!/usr/bin/env python3
"""[sticks] Offline mocks of a hunter on the sticks, with the mod's real lever-rifle mesh (fheq from the built jar) and the
sticks mesh, using the same maths as the Java:

  third person (SticksPose: the trigger arm pointed from the shoulder at the yoke, the gun drawn along it through
  ItemInHandLayer's transforms; kneel / sit legs; riderSpot placement) at the three heights, from the side and behind;
  first person (FieldWeaponFirstPerson's rested hold from SticksView.pose, the set drawn in the hand pass under the
  forend) - ready and aimed down the iron sights.

    python3 tools/sticks/mock_views.py <built mod jar>
"""
import io
import math
import os
import struct
import sys
import zipfile

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import mesh as M  # noqa: E402
import raster as R  # noqa: E402

JAR = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/fh2/.build/FrontierHunts-1.3.0.jar'
PREV = os.path.join(HERE, 'previews')
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))

HEIGHTS = {'standing': (1.445, 0.0, 0.95), 'kneeling': (1.025, 0.42, 0.95), 'sitting': (0.825, 0.62, 1.0)}
K = 16.0 / 0.9375
# rested 'ready' hold (SticksView.pose): offsets right / down / forward, tilt, cant, yaw
RX, RY, RZ, TILT, ROLL, YAW = [float(v) for v in os.environ.get('STICKS_HOLD', '0.075,0.095,0.01,1.2,-1.0,2.5').split(',')]


def load_fheq(z, name):
    d = z.read('assets/frontierhunts/models/equipment/' + name + '.fheq')
    o = 0

    def rd(fmt):
        nonlocal o
        v = struct.unpack_from('>' + fmt, d, o)
        o += struct.calcsize('>' + fmt)
        return v
    magic, ver, n = rd('iii')
    tris = []
    for _ in range(n):
        pid, nv, nt = rd('iii')
        verts = []
        for _ in range(nv):
            f = rd('8f')
            c = rd('i')[0]
            verts.append((f, c))
        idx = rd('%di' % (nt * 3))
        for k in range(nt):
            tris.append([verts[idx[k * 3 + j]] for j in range(3)])
    return tris


def mat(rx=0, ry=0, rz=0):
    """rotation matrix applied as Rz*Ry*Rx (ModelPart rotationZYX)."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def aff(Rm=None, t=(0, 0, 0), s=1.0):
    A = np.eye(4)
    if Rm is not None:
        A[:3, :3] = Rm
    A[:3, :3] *= s
    A[:3, 3] = t
    return A


def scale(sx, sy, sz):
    A = np.eye(4)
    A[0, 0], A[1, 1], A[2, 2] = sx, sy, sz
    return A


def apply(A, p):
    return (A @ np.array([p[0], p[1], p[2], 1.0]))[:3]


def draw_tris(cv, tris, A, tex, tint=None):
    Nm = A[:3, :3]
    for t in tris:
        P = [apply(A, v[0][:3]) for v in t]
        N = [Nm @ np.array(v[0][5:8]) for v in t]
        N = [n / (np.linalg.norm(n) + 1e-9) for n in N]
        UV = [v[0][3:5] for v in t]
        cv.tri(P, UV, N, tex)


def draw_quads(cv, quads, A, tex):
    Nm = A[:3, :3]
    for q in quads:
        P = [apply(A, v[:3]) for v in q]
        N = [Nm @ np.array(v[5:8]) for v in q]
        N = [n / (np.linalg.norm(n) + 1e-9) for n in N]
        UV = [v[3:5] for v in q]
        cv.tri([P[0], P[1], P[2]], [UV[0], UV[1], UV[2]], [N[0], N[1], N[2]], tex)
        if not np.allclose(P[2], P[3]):
            cv.tri([P[0], P[2], P[3]], [UV[0], UV[2], UV[3]], [N[0], N[2], N[3]], tex)


def sticks_quads(parts, contact, yaw_deg=0.0, yoke_yaw=0.0, tilt=0.0):
    q = M.assemble(parts, contact, yoke_yaw=yoke_yaw, yoke_tilt=tilt)
    # assemble faces +Z for yaw 0 (legs not yawed); turn the set by the entity yaw (MC: rotateY(-yaw))
    a = math.radians(-yaw_deg)
    c, s = math.cos(a), math.sin(a)
    out = []
    for qq in q:
        out.append([(v[0] * c + v[2] * s, v[1], -v[0] * s + v[2] * c, v[3], v[4], v[5] * c + v[7] * s, v[6], -v[5] * s + v[7] * c) for v in qq])
    return out


# ------------------------------------------------------------------------------------------- the player model

BOX = {  # part: (pivot px, box from, box to) in model space (Y down), PlayerModel wide arms
    'head': ((0, 0, 0), (-4, -8, -4), (4, 0, 4)),
    'body': ((0, 0, 0), (-4, 0, -2), (4, 12, 2)),
    'rightArm': ((-5, 2, 0), (-3, -2, -2), (1, 10, 2)),
    'leftArm': ((5, 2, 0), (-1, -2, -2), (3, 10, 2)),
    'rightLeg': ((-1.9, 12, 0), (-2, 0, -2), (2, 12, 2)),
    'leftLeg': ((1.9, 12, 0), (-2, 0, -2), (2, 12, 2)),
}
COLORS = {'head': (0.78, 0.62, 0.48), 'body': (0.30, 0.34, 0.24), 'rightArm': (0.33, 0.37, 0.26), 'leftArm': (0.33, 0.37, 0.26),
          'rightLeg': (0.36, 0.30, 0.22), 'leftLeg': (0.36, 0.30, 0.22)}


def aim_arm(pivot, target):
    v = np.array(target, float) - np.array(pivot, float)
    v /= np.linalg.norm(v)
    xr = -math.acos(max(-1, min(1, v[1])))
    yr = math.atan2(-v[0], -v[2])
    return xr, yr, 0.0


def pose_player(height, feet, body_yaw_deg, yoke):
    """SticksPose.apply in python: part rotations + the model->world matrix."""
    yaw = math.radians(body_yaw_deg)
    fx, fz = -math.sin(yaw), math.cos(yaw)
    rx, rz = -math.cos(yaw), -math.sin(yaw)
    d = np.array(yoke) - np.array(feet)
    fwd = d[0] * fx + d[2] * fz
    right = d[0] * rx + d[2] * rz
    t = (-right * K, 24.0 - d[1] * K, -fwd * K)
    rot = {k: (0.0, 0.0, 0.0) for k in BOX}
    rot['rightArm'] = aim_arm(BOX['rightArm'][0], (t[0], t[1] + 0.18 * K, t[2]))
    rot['leftArm'] = aim_arm(BOX['leftArm'][0], (t[0], t[1] + 0.20 * K, t[2] + 0.12 * K))
    legs = {'standing': ((0.1, 0.06, 0.05), (-0.16, -0.08, -0.06)), 'kneeling': ((1.16, 0.1, 0.03), (-1.16, -0.12, -0.04)),
            'sitting': ((-1.38, 0.36, 0.05), (-1.38, -0.36, -0.05))}[height]
    rot['rightLeg'], rot['leftLeg'] = legs
    # LivingEntityRenderer: Ry(180 - bodyYaw) * scale(-1,-1,1) * 0.9375 * translate(0,-1.501,0); model px / 16
    W = aff(t=feet) @ aff(mat(ry=math.radians(180 - body_yaw_deg))) @ scale(-1, -1, 1) @ scale(0.9375, 0.9375, 0.9375) @ aff(t=(0, -1.501, 0))
    return rot, W


def draw_player(cv, rot, W):
    for name, (piv, lo, hi) in BOX.items():
        A = W @ aff(t=np.array(piv) / 16.0) @ aff(mat(*rot[name]))
        lo16, hi16 = np.array(lo) / 16.0, np.array(hi) / 16.0
        cv.box(lo16, hi16, COLORS[name], xf=lambda p, A=A: apply(A, p))


def gun_matrix(rot, W):
    """ItemInHandLayer for the right hand with the gun's thirdperson_righthand display (translation 0,1,-2 px, scale 1.105)."""
    piv = np.array(BOX['rightArm'][0]) / 16.0
    A = W @ aff(t=piv) @ aff(mat(*rot['rightArm']))
    A = A @ aff(mat(rx=math.radians(-90))) @ aff(mat(ry=math.radians(180)))
    A = A @ aff(t=(1 / 16, 0.125, -0.625))
    A = A @ aff(t=(0, 1 / 16, -2 / 16)) @ scale(1.105, 1.105, 1.105)
    return A


def third_person(parts, stex, gun, gtex):
    tiles = []
    for name, (contact, lower, reach) in HEIGHTS.items():
        for view in ('side', 'behind'):
            yaw = 0.0  # gun pointing +Z
            feet = (math.sin(math.radians(yaw)) * reach, -lower, -math.cos(math.radians(yaw)) * reach)
            yoke = (0.0, contact, 0.0)
            rot, W = pose_player(name, feet, yaw, yoke)
            eye = (3.0, 1.3, -0.5) if view == 'side' else (1.2, 1.9, -3.0)
            cam = R.Cam(eye, (0, contact * 0.6, -0.3), fov=38, w=440, h=380)
            cv = R.Canvas(cam)
            ground_plane(cv)
            draw_quads(cv, sticks_quads(parts, contact, 0.0, yaw, 0.0), np.eye(4), stex)
            draw_player(cv, rot, W)
            draw_tris(cv, gun, gun_matrix(rot, W), gtex)
            im = cv.result()
            tiles.append(label(im, '%s, %s' % (name, view)))
    grid(tiles, 2).save(os.path.join(PREV, 'thirdperson_heights.png'))


def ground_plane(cv, size=3.0, tile=0.5):
    n = int(size / tile)
    for i in range(-n, n):
        for k in range(-n, n):
            c = (0.37, 0.49, 0.26) if (i + k) % 2 == 0 else (0.34, 0.46, 0.24)
            P = [(i * tile, 0, k * tile), ((i + 1) * tile, 0, k * tile), ((i + 1) * tile, 0, (k + 1) * tile), (i * tile, 0, (k + 1) * tile)]
            N = [(0, 1, 0)] * 3
            cv.tri([P[0], P[1], P[2]], None, N, None, color=c)
            cv.tri([P[0], P[2], P[3]], None, N, None, color=c)


def label(im, text):
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, len(text) * 6 + 10, 14], fill=(20, 20, 20))
    d.text((5, 1), text, fill=(235, 235, 225))
    return im


def grid(tiles, cols):
    w, h = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (w * cols, h * rows))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * w, (i // cols) * h))
    return out


# ------------------------------------------------------------------------------------------- first person

def first_person(parts, stex, gun, gtex):
    # lever rifle, iron sights (FieldWeaponSockets): ironY 0.111 (axis), eyeZ 0.2, support (0.027, -0.26)
    s = 1.35
    axis, eyez = 0.111 * s, 0.2 * s
    tiles = []
    for aim, name in ((0.0, 'rested, ready'), (1.0, 'rested, iron sights')):
        free = 1 - aim
        T = np.array([RX * free, -axis - RY * free, -eyez - RZ * free])
        tilt, roll, yawd = TILT * free, ROLL * free, YAW * free
        G = aff(t=T) @ aff(mat(rx=math.radians(tilt))) @ aff(mat(ry=math.radians(yawd))) @ aff(mat(rz=math.radians(-3.0 * free + roll))) @ scale(s, s, s)
        anchor = T + s * np.array([0.0, 0.027 - 0.004, -0.26 - 0.095])
        cam = R.Cam((0, 0, 0), (0, 0, -1), fov=70 if aim == 0 else 70, w=800, h=450)
        cv = R.Canvas(cam, bg=(0.66, 0.75, 0.84))
        # the world behind (eye 1.62 above the ground; world fov = hand fov here; 1.5x for irons in game)
        for i in range(-12, 12):
            for k in range(-40, 1):
                c = (0.37, 0.49, 0.26) if (i + k) % 2 == 0 else (0.34, 0.46, 0.24)
                P = [(i, -1.62, k), (i + 1, -1.62, k), (i + 1, -1.62, k + 1), (i, -1.62, k + 1)]
                N = [(0, 1, 0)] * 3
                cv.tri([P[0], P[1], P[2]], None, N, None, color=c)
                cv.tri([P[0], P[2], P[3]], None, N, None, color=c)
        cv.z[:] = np.inf  # the hand pass clears depth
        # the set, world-oriented (looking along its forward leg), its contact on the forend's underside
        contact = HEIGHTS['standing'][0]
        S = aff(t=anchor) @ aff(mat(ry=math.radians(180))) @ aff(t=(0, -contact, 0))
        draw_quads(cv, sticks_quads(parts, contact), S, stex)
        draw_tris(cv, gun, G, gtex)
        tiles.append(label(cv.result(), name))
    grid(tiles, 1).save(os.path.join(PREV, 'firstperson_rested.png'))


def main():
    os.makedirs(PREV, exist_ok=True)
    z = zipfile.ZipFile(JAR)
    gun = load_fheq(z, 'lever_rifle_close')
    gtex = np.asarray(Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/item/firearms_v2.png'))).convert('RGBA')).astype(np.float32) / 255
    stex = np.asarray(Image.open(os.path.join(REPO, 'patch/assets/frontierhunts/textures/entity/shooting_sticks.png')).convert('RGBA')).astype(np.float32) / 255
    parts = M.build()
    third_person(parts, stex, gun, gtex)
    first_person(parts, stex, gun, gtex)
    print('ok')


if __name__ == '__main__':
    main()
