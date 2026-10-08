#!/usr/bin/env python3
"""[ecology] Bone-find art: block models, blockstates, textures (Classic 16 px + realistic 64 px), item icons,
loot tables. Everything is procedural and original.

python3 tools/ecology/gen_bones.py [repo_root]        writes into <repo>/patch/...
python3 tools/ecology/preview_bones.py <repo> out.png renders the models for a visual check

Model space: Minecraft block model pixels (0..16 = one block, y up, north = -z). Every skull faces north; the
blockstate turns it. Bones are sunk 0.4-1.5 px into the ground (elements reach below y = 0), so they read as lying
half buried in the grass. Antler / rib / horn curves are chains of thin boxes, each turned about one axis by a
vanilla-legal angle (0, +-22.5, +-45 degrees) chosen to follow the curve best.
"""
import json, math, os, sys
import numpy as np
from PIL import Image

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(R, 'patch', 'assets', 'frontierhunts')
REAL = os.path.join(R, 'patch', 'resourcepacks', 'realistic_world', 'assets', 'frontierhunts')
DATA = os.path.join(R, 'patch', 'data', 'frontierhunts')
NS = 'frontierhunts'

ANGLES = [-45.0, -22.5, 0.0, 22.5, 45.0]


def rot(axis, deg, v):
    """right-handed rotation (as FaceBakery: Quaternionf.rotationAxis)"""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v
    if axis == 'x':
        return (x, y * c - z * s, y * s + z * c)
    if axis == 'y':
        return (x * c + z * s, y, -x * s + z * c)
    return (x * c - y * s, x * s + y * c, z)


def norm(v):
    l = math.sqrt(sum(c * c for c in v))
    return tuple(c / l for c in v)


class Model:
    def __init__(self):
        self.el = []
        self._rng = 1

    def rnd(self):
        # deterministic per element (uv offsets)
        self._rng = (self._rng * 1103515245 + 12345) & 0x7FFFFFFF
        return (self._rng >> 8) / float(1 << 23)

    def box(self, a, b, tex='#bone', rotation=None, top=None, skip_down=None, uvscale=1.0, faces=None):
        a = [float(v) for v in a]
        b = [float(v) for v in b]
        lo = [min(a[i], b[i]) for i in range(3)]
        hi = [max(a[i], b[i]) for i in range(3)]
        sx, sy, sz = (hi[0] - lo[0]), (hi[1] - lo[1]), (hi[2] - lo[2])
        dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
        fs = {}
        if skip_down is None:
            skip_down = lo[1] < -0.01 and rotation is None
        for f, (w, h) in dims.items():
            if faces is not None and f not in faces:
                continue
            if f == 'down' and skip_down:
                continue
            w = max(0.25, min(16.0, w * uvscale))
            h = max(0.25, min(16.0, h * uvscale))
            u0 = round(self.rnd() * (16.0 - w) * 4) / 4.0
            v0 = round(self.rnd() * (16.0 - h) * 4) / 4.0
            t = tex
            if f == 'up' and top:
                t = top
            fs[f] = {'uv': [u0, v0, round(u0 + w, 3), round(v0 + h, 3)], 'texture': t}
        e = {'from': [round(v, 3) for v in lo], 'to': [round(v, 3) for v in hi], 'faces': fs}
        if rotation:
            e['rotation'] = rotation
        self.el.append(e)
        return e

    def seg(self, p, d, length, t, tex='#bone', top=None, t2=None, along_v=True):
        """a thin rod from p, roughly along direction d, |d| ignored; returns its far end (as built)."""
        d = norm(d)
        best = None
        for ax in 'xyz':
            for sign in (1.0, -1.0):
                base = tuple(sign if a == ax else 0.0 for a in 'xyz')
                for rax in 'xyz':
                    if rax == ax:
                        continue
                    for ang in ANGLES:
                        v = rot(rax, ang, base)
                        dot = v[0] * d[0] + v[1] * d[1] + v[2] * d[2]
                        # prefer unrotated boxes when equally good
                        score = dot - (0.0 if ang == 0 else 1e-4)
                        if best is None or score > best[0]:
                            best = (score, ax, sign, rax, ang, v)
        _, ax, sign, rax, ang, v = best
        w = t if t2 is None else t2
        lo = list(p)
        hi = list(p)
        i = 'xyz'.index(ax)
        others = [k for k in range(3) if k != i]
        if sign > 0:
            hi[i] = p[i] + length
        else:
            lo[i] = p[i] - length
        lo[others[0]] = p[others[0]] - t / 2
        hi[others[0]] = p[others[0]] + t / 2
        lo[others[1]] = p[others[1]] - w / 2
        hi[others[1]] = p[others[1]] + w / 2
        rotation = None if ang == 0 else {'origin': [round(c, 3) for c in p], 'axis': rax, 'angle': ang}
        self.box(lo, hi, tex=tex, rotation=rotation, top=top, skip_down=False)
        return tuple(p[k] + v[k] * length for k in range(3))

    def json(self, textures):
        return {'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'minecraft:cutout',
                'textures': textures, 'elements': self.el}


def mirror_x(m):
    """mirrors every element across x = 8 (left antler -> right antler)"""
    out = []
    for e in m.el:
        e2 = json.loads(json.dumps(e))
        f, t = e2['from'], e2['to']
        e2['from'] = [round(16 - t[0], 3), f[1], f[2]]
        e2['to'] = [round(16 - f[0], 3), t[1], t[2]]
        if 'rotation' in e2:
            r = e2['rotation']
            r['origin'][0] = round(16 - r['origin'][0], 3)
            if r['axis'] in ('y', 'z'):
                r['angle'] = -r['angle']
        fs = e2['faces']
        if 'east' in fs or 'west' in fs:
            fs['east'], fs['west'] = fs.get('west'), fs.get('east')
            for k in ('east', 'west'):
                if fs[k] is None:
                    del fs[k]
        out.append(e2)
    return out


# ------------------------------------------------------------------------------------------------ skulls

def cervid_skull(m, L, W, H, sink, snout_w, front_z=1.5):
    """generic deer-family skull facing north; L length, W width, H height (px)"""
    cz1 = front_z + L          # back of the braincase
    x0, x1 = 8 - W / 2, 8 + W / 2
    y0 = -sink
    # braincase
    m.box([x0, y0, cz1 - L * 0.36], [x1, y0 + H, cz1], top='#bone_top')
    # occipital ridge
    m.box([x0 + 0.5, y0 + H * 0.35, cz1], [x1 - 0.5, y0 + H * 0.85, cz1 + 0.6], top='#bone_top')
    # forehead / frontal
    fw = W * 0.86
    m.box([8 - fw / 2, y0 + H * 0.25, cz1 - L * 0.58], [8 + fw / 2, y0 + H * 0.92, cz1 - L * 0.36], top='#bone_top')
    # eye sockets (dark), each side of the frontal
    zo0, zo1 = cz1 - L * 0.55, cz1 - L * 0.40
    m.box([8 - fw / 2 - 0.15, y0 + H * 0.42, zo0], [8 - fw / 2 + 0.05, y0 + H * 0.8, zo1], tex='#socket')
    m.box([8 + fw / 2 - 0.05, y0 + H * 0.42, zo0], [8 + fw / 2 + 0.15, y0 + H * 0.8, zo1], tex='#socket')
    # cheek / maxilla
    sw = snout_w
    m.box([8 - sw / 2 - 0.3, y0, cz1 - L * 0.72], [8 + sw / 2 + 0.3, y0 + H * 0.62, cz1 - L * 0.56], top='#bone_top')
    # snout, tapering in two steps
    m.box([8 - sw / 2, y0, front_z + L * 0.12], [8 + sw / 2, y0 + H * 0.5, cz1 - L * 0.72], top='#bone_top')
    # nasal bones on top
    m.box([8 - sw / 2 + 0.25, y0 + H * 0.5, front_z + L * 0.2], [8 + sw / 2 - 0.25, y0 + H * 0.62, cz1 - L * 0.6], top='#bone_top')
    # premaxilla tip with the nasal opening
    m.box([8 - sw / 2 + 0.35, y0, front_z], [8 + sw / 2 - 0.35, y0 + H * 0.38, front_z + L * 0.12], top='#bone_top')
    m.box([8 - sw / 2 + 0.55, y0 + H * 0.38, front_z + L * 0.1], [8 + sw / 2 - 0.55, y0 + H * 0.5, front_z + L * 0.22], tex='#socket')
    # tooth row, a little proud of the jaw line
    m.box([8 - sw / 2 - 0.05, y0, cz1 - L * 0.7], [8 + sw / 2 + 0.05, y0 + 0.6, front_z + L * 0.35], tex='#bone')
    return cz1, x0, x1, y0


def whitetail(antlers):
    m = Model()
    cz1, x0, x1, y0 = cervid_skull(m, L=8.0, W=4.2, H=3.8, sink=1.0, snout_w=2.6, front_z=4.0)
    if antlers:
        side = Model()
        side._rng = 99
        # pedicle (bone) then the burr
        p = (6.6, y0 + 3.6, cz1 - 3.4)
        side.box([p[0] - 0.55, p[1] - 0.3, p[2] - 0.55], [p[0] + 0.55, p[1] + 0.35, p[2] + 0.55], tex='#bone')
        side.box([p[0] - 0.75, p[1] + 0.3, p[2] - 0.75], [p[0] + 0.75, p[1] + 0.75, p[2] + 0.75], tex='#antler_base')
        q = (p[0], p[1] + 0.7, p[2])
        q1 = side.seg(q, (-0.75, 0.5, 0.35), 2.6, 1.0, tex='#antler')        # out, up, back
        side.seg(q1, (0.05, 1.0, -0.15), 1.9, 0.6, tex='#antler')            # brow tine
        q2 = side.seg(q1, (-0.75, 0.1, -0.6), 3.0, 0.9, tex='#antler')       # sweeping out and forward
        side.seg(q2, (0.1, 1.0, 0.05), 3.4, 0.65, tex='#antler')             # G2
        q3 = side.seg(q2, (-0.1, 0.12, -1.0), 2.8, 0.85, tex='#antler')      # forward
        side.seg(q3, (0.15, 1.0, 0.0), 2.7, 0.6, tex='#antler')              # G3
        q4 = side.seg(q3, (0.65, 0.15, -0.75), 2.2, 0.7, tex='#antler')      # curving in
        side.seg(q4, (0.75, 0.25, -0.6), 1.2, 0.5, tex='#antler')            # tip
        m.el += side.el + mirror_x(side)
    return m


def elk(antlers):
    m = Model()
    cz1, x0, x1, y0 = cervid_skull(m, L=11.0, W=5.2, H=4.6, sink=1.2, snout_w=3.0, front_z=1.0)
    if antlers:
        side = Model()
        side._rng = 3
        p = (6.3, y0 + 4.4, cz1 - 4.6)
        side.box([p[0] - 0.7, p[1] - 0.3, p[2] - 0.7], [p[0] + 0.7, p[1] + 0.4, p[2] + 0.7], tex='#bone')
        side.box([p[0] - 0.95, p[1] + 0.4, p[2] - 0.95], [p[0] + 0.95, p[1] + 0.95, p[2] + 0.95], tex='#antler_base')
        q = (p[0], p[1] + 0.9, p[2])
        side.seg(q, (-0.1, 0.35, -1.0), 4.0, 0.75, tex='#antler')            # brow tine, over the face
        q1 = side.seg(q, (-0.7, 0.45, 0.55), 3.0, 1.25, tex='#antler')
        side.seg(q1, (-0.1, 0.45, -1.0), 3.2, 0.7, tex='#antler')           # bez tine
        q2 = side.seg(q1, (-0.35, 0.15, 1.0), 4.6, 1.15, tex='#antler')     # main beam sweeping back, resting low
        side.seg(q2, (0.0, 1.0, -0.45), 3.4, 0.7, tex='#antler')            # trez
        q3 = side.seg(q2, (-0.05, 0.0, 1.0), 4.4, 1.05, tex='#antler')
        side.seg(q3, (0.0, 1.0, -0.2), 4.2, 0.75, tex='#antler')            # royal (dagger point)
        q4 = side.seg(q3, (0.15, 0.25, 1.0), 3.4, 0.95, tex='#antler')
        side.seg(q4, (0.05, 1.0, 0.1), 3.0, 0.65, tex='#antler')            # fifth
        q5 = side.seg(q4, (0.25, 0.6, 0.7), 2.2, 0.75, tex='#antler')
        side.seg(q5, (0.2, 1.0, -0.3), 1.4, 0.5, tex='#antler')             # fork at the end
        m.el += side.el + mirror_x(side)
    return m


def moose(antlers):
    m = Model()
    cz1, x0, x1, y0 = cervid_skull(m, L=13.0, W=5.6, H=4.8, sink=1.3, snout_w=3.6, front_z=0.5)
    # the heavy, overhanging moose nose
    m.box([8 - 2.2, y0 + 0.2, 0.2], [8 + 2.2, y0 + 3.4, 3.2], top='#bone_top')
    if antlers:
        side = Model()
        side._rng = 41
        p = (5.8, y0 + 4.6, cz1 - 5.0)
        side.box([p[0] - 0.8, p[1] - 0.3, p[2] - 0.8], [p[0] + 0.8, p[1] + 0.45, p[2] + 0.8], tex='#bone')
        side.box([p[0] - 1.0, p[1] + 0.45, p[2] - 1.0], [p[0] + 1.0, p[1] + 1.0, p[2] + 1.0], tex='#antler_base')
        q = (p[0], p[1] + 0.8, p[2])
        b = side.seg(q, (-1.0, 0.25, 0.15), 2.6, 1.3, tex='#antler')         # short beam out to the palm
        bx, by, bz = b
        o = [round(bx, 3), round(by, 3), round(bz, 3)]
        tilt = {'origin': o, 'axis': 'z', 'angle': -45.0}
        # the palm, cupped up at 45 degrees: a broad plate, a narrower outer lobe that rounds it off, a rear lobe
        side.box([bx - 6.0, by - 0.3, bz - 1.0], [bx + 0.3, by + 0.3, bz + 4.6], tex='#antler', top='#antler_top', rotation=tilt, skip_down=False)
        side.box([bx - 7.4, by - 0.25, bz - 0.2], [bx - 5.9, by + 0.25, bz + 3.8], tex='#antler', top='#antler_top', rotation=tilt, skip_down=False)
        side.box([bx - 4.6, by - 0.25, bz - 2.2], [bx + 0.3, by + 0.25, bz - 0.9], tex='#antler', top='#antler_top', rotation=tilt, skip_down=False)
        side.box([bx - 5.0, by - 0.25, bz + 4.5], [bx - 0.4, by + 0.25, bz + 6.2], tex='#antler', top='#antler_top', rotation=tilt, skip_down=False)
        # brow palm, forward and flatter
        side.box([bx - 4.0, by - 0.25, bz - 5.6], [bx + 0.2, by + 0.25, bz - 1.9], tex='#antler', top='#antler_top',
                 rotation={'origin': o, 'axis': 'z', 'angle': -22.5}, skip_down=False)
        # points round the rim of the palm (its outer edge sits ~7 px out along the 45 degree slope)
        c, s_ = math.cos(math.radians(45.0)), math.sin(math.radians(45.0))
        for zz, rr, ln in [(0.0, 7.4, 1.6), (1.8, 7.5, 2.0), (3.5, 7.3, 1.8), (5.0, 5.9, 1.5), (6.1, 4.8, 1.3), (-1.1, 5.9, 1.4), (-2.1, 4.4, 1.2)]:
            side.seg((bx - rr * c, by + rr * s_, bz + zz), (-0.5, 1.0, 0.0), ln, 0.55, tex='#antler')
        for zz in (-5.2, -3.6):
            side.seg((bx - 4.0 * math.cos(math.radians(22.5)), by + 4.0 * math.sin(math.radians(22.5)), bz + zz), (-0.6, 0.8, -0.4), 1.4, 0.5, tex='#antler')
        m.el += side.el + mirror_x(side)
    return m


def bison():
    m = Model()
    y0 = -1.2
    # broad, flat-fronted bison skull with a short face
    m.box([4.0, y0, 8.0], [12.0, y0 + 5.2, 12.5], top='#bone_top')            # braincase / poll
    m.box([4.6, y0 + 1.0, 6.2], [11.4, y0 + 5.0, 8.0], top='#bone_top')       # frontal
    m.box([3.8, y0 + 2.6, 10.4], [12.2, y0 + 4.6, 12.0], tex='#bone')        # boss ridge between the horns
    m.box([4.45, y0 + 2.4, 6.6], [4.7, y0 + 3.8, 7.8], tex='#socket')         # eye sockets
    m.box([11.3, y0 + 2.4, 6.6], [11.55, y0 + 3.8, 7.8], tex='#socket')
    m.box([5.4, y0, 3.0], [10.6, y0 + 3.4, 6.2], top='#bone_top')             # face
    m.box([5.9, y0, 1.0], [10.1, y0 + 2.4, 3.0], top='#bone_top')             # muzzle
    m.box([6.6, y0 + 2.4, 1.2], [9.4, y0 + 2.8, 2.6], tex='#socket')           # nasal opening
    m.box([5.3, y0, 3.5], [10.7, y0 + 0.5, 7.0], tex='#bone')                 # tooth row
    side = Model()
    side._rng = 17
    p = (4.0, y0 + 3.6, 10.6)
    side.box([p[0] - 1.4, p[1] - 0.9, p[2] - 0.9], [p[0] + 0.2, p[1] + 0.9, p[2] + 0.9], tex='#bone')  # horn core base
    q1 = side.seg((p[0] - 1.2, p[1], p[2]), (-1.0, 0.15, 0.0), 2.2, 1.7, tex='#horn')
    q2 = side.seg(q1, (-0.55, 1.0, 0.0), 2.2, 1.35, tex='#horn')
    q3 = side.seg(q2, (0.25, 1.0, -0.3), 1.8, 1.0, tex='#horn')
    side.seg(q3, (0.55, 0.7, -0.4), 1.2, 0.6, tex='#horn_tip')
    m.el += side.el + mirror_x(side)
    return m


# ------------------------------------------------------------------------------------------------ scattered bones

def ribs():
    m = Model()
    m._rng = 5
    # a stretch of spine on the ground, ribs arching up out of the grass from it
    m.box([6.4, -0.6, 1.0], [8.0, 0.9, 15.0], top='#bone_top')
    for i, z in enumerate([2.4, 4.4, 6.4, 8.4, 10.4, 12.4]):
        k = 1.0 - abs(i - 2.6) * 0.09
        p = (7.6, 0.6, z)
        q = m.seg(p, (0.25, 1.0, 0.0), 3.2 * k, 0.55, t2=0.7)
        q = m.seg(q, (0.75, 0.65, 0.0), 3.0 * k, 0.55, t2=0.7)
        q = m.seg(q, (1.0, -0.05, 0.0), 2.4 * k, 0.55, t2=0.7)
        if i not in (1, 4):
            q = m.seg(q, (0.65, -1.0, 0.0), 3.6 * k, 0.5, t2=0.65)   # back down into the ground
        else:
            m.seg(q, (0.7, -0.9, 0.0), 1.2, 0.5, t2=0.6)             # broken short
    # two ribs fallen flat on the other side
    m.seg((6.2, -0.1, 5.0), (-1.0, 0.0, -0.4), 5.0, 0.55, t2=0.45)
    m.seg((6.2, -0.1, 9.6), (-1.0, 0.0, 0.45), 4.4, 0.55, t2=0.45)
    return m


def spine():
    m = Model()
    m._rng = 11
    xs = [7.0, 7.4, 7.9, 8.5, 9.0, 9.3, 9.4]
    for i, x in enumerate(xs):
        z = 2.0 + i * 2.0
        m.box([x - 0.9, -0.5, z - 0.75], [x + 0.9, 0.85, z + 0.75], top='#bone_top')           # vertebral body
        m.box([x - 0.25, 0.85, z - 0.35], [x + 0.25, 2.0 - 0.1 * i, z + 0.35], top='#bone_top')  # spinous process
        m.box([x - 1.9, 0.2, z - 0.3], [x + 1.9, 0.6, z + 0.3], top='#bone_top')               # transverse processes
    # pelvis fragment at the end
    m.box([7.2, -0.6, 15.0], [11.6, 0.9, 16.6], top='#bone_top')
    m.box([6.6, -0.4, 14.6], [7.4, 0.5, 17.4], top='#bone_top')
    m.box([11.4, -0.4, 14.6], [12.2, 0.5, 17.4], top='#bone_top')
    return m


def long_bone(m, center, length, yaw, thick=1.0, knob=1.5):
    """a leg bone lying on the ground: shaft with knobby ends; yaw in vanilla steps about y"""
    cx, cy, cz = center
    half = length / 2
    rot_ = None if yaw == 0 else {'origin': [cx, cy, cz], 'axis': 'y', 'angle': yaw}
    m.box([cx - thick / 2, cy - thick / 2, cz - half + 0.8], [cx + thick / 2, cy + thick / 2, cz + half - 0.8], rotation=rot_, top='#bone_top', skip_down=False)
    m.box([cx - knob / 2, cy - knob / 2, cz - half], [cx + knob / 2, cy + knob / 2 + 0.15, cz - half + 1.1], rotation=rot_, top='#bone_top', skip_down=False)
    m.box([cx - knob / 2 * 0.85, cy - knob / 2, cz + half - 1.0], [cx + knob / 2 * 0.85, cy + knob / 2, cz + half], rotation=rot_, top='#bone_top', skip_down=False)


def legs():
    m = Model()
    m._rng = 23
    long_bone(m, (5.0, 0.15, 8.0), 11.0, 22.5, 1.05, 1.7)    # femur
    long_bone(m, (10.5, 0.1, 7.0), 9.5, -45.0, 0.9, 1.5)     # tibia
    long_bone(m, (9.0, 0.05, 12.5), 7.5, 0.0, 0.75, 1.2)     # cannon bone
    # a hoof capsule, dark and shrunken
    m.box([8.4, -0.4, 15.6], [9.6, 0.6, 17.0], tex='#socket')
    m.box([3.0, -0.3, 2.0], [4.6, 0.6, 3.4], top='#bone_top')  # loose knuckle
    return m


def shed():
    m = Model()
    m._rng = 61
    # a whitetail antler lying on its side: burr, the beam curving round, tines standing up
    p = (3.2, 0.2, 11.5)
    m.box([p[0] - 1.0, -0.3, p[2] - 1.0], [p[0] + 1.0, 1.3, p[2] + 1.0], tex='#antler_base')
    q1 = m.seg((p[0] + 0.8, 0.5, p[2]), (1.0, 0.0, -0.5), 3.2, 1.0, tex='#antler')
    m.seg(q1, (0.0, 1.0, 0.1), 2.0, 0.6, tex='#antler')                    # brow tine
    q2 = m.seg(q1, (0.6, 0.0, -1.0), 3.4, 0.9, tex='#antler')
    m.seg(q2, (-0.1, 1.0, 0.0), 3.4, 0.6, tex='#antler')                   # G2
    q3 = m.seg(q2, (0.0, 0.05, -1.0), 3.0, 0.8, tex='#antler')
    m.seg(q3, (-0.2, 1.0, 0.0), 2.8, 0.55, tex='#antler')                  # G3
    q4 = m.seg(q3, (-0.6, 0.0, -0.8), 2.4, 0.65, tex='#antler')
    m.seg(q4, (-0.8, 0.0, -0.6), 1.3, 0.5, tex='#antler')
    return m


# ------------------------------------------------------------------------------------------------ textures

def vnoise(n, cells, seed):
    rs = np.random.RandomState(seed)
    g = rs.rand(cells + 1, cells + 1)
    g[-1, :] = g[0, :]
    g[:, -1] = g[:, 0]
    t = np.linspace(0, cells, n, endpoint=False)
    i = t.astype(int)
    f = t - i
    f = f * f * (3 - 2 * f)
    a = g[i][:, i]
    b = g[i + 1][:, i]
    c = g[i][:, i + 1]
    d = g[i + 1][:, i + 1]
    fy = f[:, None]
    fx = f[None, :]
    return (a * (1 - fy) * (1 - fx) + b * fy * (1 - fx) + c * (1 - fy) * fx + d * fy * fx)


def fbm(n, seed, octaves=4, base=2):
    s = np.zeros((n, n))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        cells = min(n, base * 2 ** o)
        s += amp * vnoise(n, cells, seed + o * 31)
        tot += amp
        amp *= 0.5
    return s / tot


def to_img(rgb):
    a = np.clip(rgb, 0, 255).astype(np.uint8)
    alpha = np.full(a.shape[:2] + (1,), 255, np.uint8)
    return Image.fromarray(np.concatenate([a, alpha], axis=2), 'RGBA')


def mix(a, b, t):
    t = t[..., None] if np.ndim(t) == 2 else t
    return a * (1 - t) + b * t


def cracks(n, seed, count, strength):
    rs = np.random.RandomState(seed)
    m = np.zeros((n, n))
    for _ in range(count):
        x, y = rs.rand() * n, rs.rand() * n
        ang = rs.rand() * math.pi * 2
        for _ in range(int(n * (0.25 + rs.rand() * 0.35))):
            ix, iy = int(x) % n, int(y) % n
            m[iy, ix] = max(m[iy, ix], strength)
            ang += (rs.rand() - 0.5) * 0.8
            x += math.cos(ang)
            y += math.sin(ang) * 0.6
    return m


def bone_tex(n, kind, seed=3):
    """kind: weathered | mossy | mossy_top | fresh"""
    hi = n >= 32
    base = np.array([214.0, 204.0, 182.0])
    warm = np.array([196.0, 178.0, 146.0])
    pale = np.array([233.0, 228.0, 214.0])
    f = fbm(n, seed, 5 if hi else 3, 2 if hi else 2)
    g = fbm(n, seed + 9, 4, 4)
    col = mix(np.broadcast_to(warm, (n, n, 3)), np.broadcast_to(pale, (n, n, 3)), np.clip(f * 1.3 - 0.15, 0, 1))
    col = col * (0.9 + 0.16 * g[..., None])
    # large-scale weathering: greyer and yellower patches
    hue = fbm(n, seed + 77, 3, 2)[..., None]
    col = col * (np.array([1.0, 1.0, 1.0]) * (1 - hue) + np.array([0.93, 0.92, 0.9]) * hue) * (0.97 + 0.06 * (1 - hue))
    # porous pits, clustered
    rs = np.random.RandomState(seed + 5)
    cl = fbm(n, seed + 88, 3, 4)
    pits = rs.rand(n, n) < (0.09 if hi else 0.07) * np.clip(cl * 1.6 - 0.3, 0, 1.5)
    col[pits] *= 0.74
    # hairline cracks
    c = cracks(n, seed + 2, 3 if hi else 2, 1.0)
    col = col * (1 - 0.32 * c[..., None])
    # weathering grime toward the bottom rows (the side that lies in the dirt)
    rows = np.linspace(0, 1, n)[:, None]
    col = col * (1 - 0.12 * np.clip(rows - 0.55, 0, 1)[..., None] * 2)
    if kind == 'fresh':
        # greasy, blood-stained: warmer, pink-red streaks, dried brown blotches
        col = col * np.array([1.0, 0.94, 0.88])
        streak = fbm(n, seed + 21, 4, 3)
        s = np.clip((streak - 0.6) * 4.0, 0, 1)
        col = mix(col, np.broadcast_to(np.array([112.0, 40.0, 32.0]), (n, n, 3)), s * 0.7)
        dried = np.clip((fbm(n, seed + 33, 3, 2) - 0.62) * 5.0, 0, 1)
        col = mix(col, np.broadcast_to(np.array([92.0, 52.0, 36.0]), (n, n, 3)), dried * 0.6)
    if kind in ('mossy', 'mossy_top'):
        moss_n = fbm(n, seed + 44, 5 if hi else 3, 2)
        cover = 0.34 if kind == 'mossy' else 0.58
        if kind == 'mossy':
            moss_n = moss_n + np.clip(rows - 0.35, 0, 1) * 0.45   # moss creeps up from the ground
        m = np.clip((moss_n - (1 - cover)) * (6.0 if hi else 9.0), 0, 1)
        moss_col = mix(np.broadcast_to(np.array([66.0, 92.0, 38.0]), (n, n, 3)),
                       np.broadcast_to(np.array([112.0, 138.0, 54.0]), (n, n, 3)), fbm(n, seed + 51, 4, 4))
        if hi:
            # little clumps: brighter tips, dark gaps
            tuft = rs.rand(n, n)
            moss_col = moss_col * (0.8 + 0.35 * tuft[..., None])
        col = mix(col, moss_col, m)
    return to_img(col)


def socket_tex(n, seed=8):
    f = fbm(n, seed, 3, 2)
    base = mix(np.broadcast_to(np.array([38.0, 30.0, 24.0]), (n, n, 3)), np.broadcast_to(np.array([74.0, 60.0, 46.0]), (n, n, 3)), f)
    rows = np.linspace(0, 1, n)[:, None, None]
    return to_img(base * (0.85 + 0.25 * rows))


def antler_tex(n, kind, seed=12):
    """kind: weathered (bleached, chalky) | fresh (brown) | base (burr rosette) | top (palm top)"""
    hi = n >= 32
    xs = np.arange(n)[None, :]
    grooves = 0.5 + 0.5 * np.sin(xs / n * math.pi * 2 * (7 if hi else 4) + fbm(n, seed, 3, 2) * 5.0)
    f = fbm(n, seed + 3, 4 if hi else 3, 2)
    if kind == 'fresh':
        dark = np.array([96.0, 74.0, 52.0])
        light = np.array([168.0, 140.0, 104.0])
    else:
        dark = np.array([150.0, 138.0, 116.0])
        light = np.array([214.0, 206.0, 186.0])
    t = np.clip(0.25 + 0.45 * f + 0.3 * grooves * (0.6 if kind != 'top' else 0.25), 0, 1)
    col = mix(np.broadcast_to(dark, (n, n, 3)), np.broadcast_to(light, (n, n, 3)), t)
    rows = np.linspace(0, 1, n)[:, None]
    # polished pale tips toward the top of the texture (tine ends use the upper rows)
    tip = np.clip(0.35 - rows, 0, 1)[..., None] * 1.6
    col = mix(col, np.broadcast_to(np.array([236.0, 230.0, 214.0]), (n, n, 3)), np.clip(tip[..., 0], 0.0, 0.7))
    if kind == 'base':
        # the burr: knobbly pearling
        rs = np.random.RandomState(seed + 7)
        knobs = rs.rand(n, n)
        col = col * (0.75 + 0.4 * knobs[..., None])
    if kind != 'fresh' and hi:
        # rodent gnawing at the edges, chalky flecks
        rs = np.random.RandomState(seed + 9)
        fl = rs.rand(n, n) < 0.03
        col[fl] = col[fl] * 1.08 + 8
    return to_img(col)


def horn_tex(n, kind, seed=19):
    hi = n >= 32
    ys = np.arange(n)[:, None]
    rings = 0.5 + 0.5 * np.sin(ys / n * math.pi * 2 * (9 if hi else 5) + fbm(n, seed, 3, 2) * 3.0)
    f = fbm(n, seed + 1, 4, 2)
    dark = np.array([34.0, 30.0, 28.0])
    light = np.array([92.0, 84.0, 76.0]) if kind == 'horn' else np.array([150.0, 140.0, 124.0])
    t = np.clip(0.15 + 0.35 * f + 0.25 * rings, 0, 1)
    if kind == 'tip':
        t = np.clip(t + 0.25, 0, 1)
    col = mix(np.broadcast_to(dark, (n, n, 3)), np.broadcast_to(light, (n, n, 3)), t)
    # dry, flaking keratin streaks
    streak = np.clip((fbm(n, seed + 5, 4, 6) - 0.6) * 3, 0, 1)
    col = mix(col, np.broadcast_to(np.array([128.0, 118.0, 104.0]), (n, n, 3)), streak * 0.45)
    return to_img(col)


# ------------------------------------------------------------------------------------------------ item icons

OUT = (52, 44, 34, 255)


def icon(draw_fn):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    pts = draw_fn()
    for (x, y), c in pts.items():
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c
    # dark outline around the silhouette (like the mod's other icons)
    out = im.copy()
    po = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < 16 and 0 <= yy < 16 and px[xx, yy][3] > 0:
                        po[x, y] = OUT
                        break
    return out


BONE = [(236, 230, 214, 255), (216, 206, 184, 255), (188, 176, 150, 255), (150, 138, 116, 255)]
ANT = [(222, 214, 194, 255), (190, 176, 150, 255), (156, 140, 114, 255)]
DARK = (44, 34, 28, 255)
HORN = [(110, 100, 90, 255), (70, 64, 58, 255), (40, 36, 34, 255)]


def line(pts, a, b, c):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        pts[(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n))] = c


def skull_icon(species):
    def f():
        pts = {}
        # skull seen from the front-top: long narrow face down the middle
        if species == 'bison':
            for y in range(5, 15):
                w = 4 if y < 9 else (3 if y < 12 else 2)
                for x in range(8 - w, 8 + w):
                    pts[(x, y)] = BONE[1] if (x + y) % 3 else BONE[0]
            for x in range(4, 12):
                pts[(x, 5)] = BONE[0]
            pts[(5, 8)] = DARK; pts[(10, 8)] = DARK
            pts[(7, 13)] = DARK; pts[(8, 13)] = DARK
            # horns: out then up and in
            for s in (-1, 1):
                cx = 8 + s * 4 - (1 if s > 0 else 0)
                line(pts, (cx, 6), (cx + s * 2, 6), HORN[1])
                line(pts, (cx + s * 2, 5), (cx + s * 3, 3), HORN[1])
                line(pts, (cx + s * 3, 2), (cx + s * 2, 1), HORN[0])
                pts[(cx + s * 1, 5)] = HORN[2]
            return pts
        top = 7 if species != 'whitetail' else 8
        for y in range(top, 16):
            w = 2 if y < top + 3 else (2 if y < 13 else 1)
            if y == top:
                w = 2
            for x in range(8 - w, 8 + w):
                pts[(x, y)] = BONE[0] if x < 8 else BONE[1]
        pts[(6, top + 2)] = DARK; pts[(9, top + 2)] = DARK
        pts[(7, 15)] = BONE[2]; pts[(8, 15)] = BONE[2]
        if species == 'whitetail':
            for s in (-1, 1):
                x0 = 7 if s < 0 else 8
                line(pts, (x0, top - 1), (x0 + s * 3, top - 3), ANT[1])
                line(pts, (x0 + s * 3, top - 3), (x0 + s * 5, top - 1), ANT[1])
                line(pts, (x0 + s * 5, top - 1), (x0 + s * 6, top + 2), ANT[1])
                pts[(x0 + s * 2, top - 4)] = ANT[0]; pts[(x0 + s * 2, top - 5)] = ANT[0]
                pts[(x0 + s * 4, top - 4)] = ANT[0]; pts[(x0 + s * 4, top - 5)] = ANT[0]
                pts[(x0 + s * 6, top)] = ANT[0]
        elif species == 'elk':
            for s in (-1, 1):
                x0 = 7 if s < 0 else 8
                line(pts, (x0, top - 1), (x0 + s * 3, top - 4), ANT[1])
                line(pts, (x0 + s * 3, top - 4), (x0 + s * 5, top - 7), ANT[1])
                line(pts, (x0 + s * 5, top - 7), (x0 + s * 6, top - 7), ANT[0])
                line(pts, (x0 + s * 1, top - 2), (x0 + s * 1, top - 4), ANT[0])
                line(pts, (x0 + s * 3, top - 4), (x0 + s * 2, top - 6), ANT[0])
                line(pts, (x0 + s * 4, top - 5), (x0 + s * 6, top - 4), ANT[0])
        else:  # moose palms
            for s in (-1, 1):
                x0 = 7 if s < 0 else 8
                line(pts, (x0, top), (x0 + s * 2, top - 1), ANT[1])
                for x in range(2, 8):
                    for y in range(top - 4, top + 1):
                        if (x - 2) + (top - y) < 8 and not (x > 5 and y > top - 2):
                            pts[(x0 + s * x, y)] = ANT[1] if (x + y) % 2 else ANT[2]
                for x in (3, 5, 7):
                    pts[(x0 + s * x, top - 5)] = ANT[0]
        return pts
    return icon(f)


def bones_icon():
    def f():
        pts = {}
        # two crossed leg bones with knobby ends
        line(pts, (3, 12), (12, 3), BONE[1])
        line(pts, (4, 12), (13, 3), BONE[0])
        for (x, y) in ((2, 12), (3, 13), (2, 13), (12, 2), (13, 2), (13, 3), (14, 2)):
            pts[(x, y)] = BONE[0]
        line(pts, (3, 5), (12, 13), BONE[2])
        for (x, y) in ((2, 4), (3, 4), (2, 5), (12, 14), (13, 13), (13, 14)):
            pts[(x, y)] = BONE[1]
        return pts
    return icon(f)


def shed_icon():
    def f():
        pts = {}
        line(pts, (3, 13), (6, 10), ANT[2])
        line(pts, (6, 10), (10, 8), ANT[1])
        line(pts, (10, 8), (13, 5), ANT[1])
        line(pts, (13, 5), (13, 3), ANT[0])
        line(pts, (6, 10), (5, 7), ANT[0])
        line(pts, (9, 8), (8, 4), ANT[0])
        line(pts, (11, 7), (11, 3), ANT[0])
        for (x, y) in ((2, 13), (3, 14), (2, 14), (4, 14)):
            pts[(x, y)] = ANT[2]
        return pts
    return icon(f)


# ------------------------------------------------------------------------------------------------ writing

def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=1)


def tex_set(look):
    """texture variables for natural (weathered / mossy) and kill (fresh) variants"""
    b = f'{NS}:block/'
    if look == 'fresh':
        return {'bone': b + 'bone_fresh', 'bone_top': b + 'bone_fresh', 'antler': b + 'antler_fresh', 'antler_base': b + 'antler_fresh_base',
                'antler_top': b + 'antler_fresh', 'socket': b + 'bone_socket', 'horn': b + 'bison_horn', 'horn_tip': b + 'bison_horn_tip',
                'particle': b + 'bone_fresh'}
    mossy = look == 'mossy'
    return {'bone': b + ('bone_mossy' if mossy else 'bone_weathered'), 'bone_top': b + ('bone_mossy_top' if mossy else 'bone_weathered'),
            'antler': b + 'antler_weathered', 'antler_base': b + 'antler_weathered_base',
            'antler_top': b + ('bone_mossy_top' if mossy else 'antler_weathered'), 'socket': b + 'bone_socket',
            'horn': b + 'bison_horn', 'horn_tip': b + 'bison_horn_tip', 'particle': b + 'bone_weathered'}


LOOKS = ['plain', 'mossy', 'fresh']


def main():
    models = {
        'whitetail_skull': whitetail(True), 'whitetail_skull_bare': whitetail(False),
        'elk_skull': elk(True), 'elk_skull_bare': elk(False),
        'moose_skull': moose(True), 'moose_skull_bare': moose(False),
        'bison_skull': bison(),
        'scattered_bones_ribs': ribs(), 'scattered_bones_spine': spine(), 'scattered_bones_legs': legs(),
        'shed_antler': shed(),
    }
    mdir = os.path.join(ASSETS, 'models', 'block')
    counts = {}
    for name, m in models.items():
        # one geometry model (template) + one small model per look
        for v in m.el:
            for c in v['from'] + v['to']:
                assert -16 <= c <= 32, (name, v)
        wjson(os.path.join(mdir, 'bones', name + '_template.json'), {**m.json({}), 'textures': {}})
        for look in LOOKS:
            wjson(os.path.join(mdir, 'bones', f'{name}_{look}.json'),
                  {'parent': f'{NS}:block/bones/{name}_template', 'textures': tex_set(look)})
        counts[name] = len(m.el)
    print('elements per model:', counts)

    facings = {'north': 0, 'east': 90, 'south': 180, 'west': 270}

    def look_of(mossy, natural):
        return 'fresh' if natural == 'false' else ('mossy' if mossy == 'true' else 'plain')

    bdir = os.path.join(ASSETS, 'blockstates')
    for sp in ('whitetail', 'elk', 'moose', 'bison'):
        v = {}
        for fa, yr in facings.items():
            for ant in ('true', 'false'):
                for mo in ('true', 'false'):
                    for na in ('true', 'false'):
                        geo = f'{sp}_skull' if (ant == 'true' or sp == 'bison') else f'{sp}_skull_bare'
                        e = {'model': f'{NS}:block/bones/{geo}_{look_of(mo, na)}'}
                        if yr:
                            e['y'] = yr
                        v[f'antlers={ant},facing={fa},mossy={mo},natural={na}'] = e
        wjson(os.path.join(bdir, f'{sp}_skull.json'), {'variants': v})
    v = {}
    for fa, yr in facings.items():
        for part in ('ribs', 'spine', 'legs'):
            for mo in ('true', 'false'):
                for na in ('true', 'false'):
                    e = {'model': f'{NS}:block/bones/scattered_bones_{part}_{look_of(mo, na)}'}
                    if yr:
                        e['y'] = yr
                    v[f'facing={fa},mossy={mo},natural={na},part={part}'] = e
    wjson(os.path.join(bdir, 'scattered_bones.json'), {'variants': v})
    v = {}
    for fa, yr in facings.items():
        for mo in ('true', 'false'):
            for na in ('true', 'false'):
                e = {'model': f'{NS}:block/bones/shed_antler_{"mossy" if mo == "true" else "plain"}'}
                if yr:
                    e['y'] = yr
                v[f'facing={fa},mossy={mo},natural={na}'] = e
    wjson(os.path.join(bdir, 'shed_antler.json'), {'variants': v})

    # textures: Classic 16 px, realistic pack 64 px
    for root, n in ((os.path.join(ASSETS, 'textures', 'block'), 16), (os.path.join(REAL, 'textures', 'block'), 64)):
        os.makedirs(root, exist_ok=True)
        bone_tex(n, 'weathered').save(os.path.join(root, 'bone_weathered.png'))
        bone_tex(n, 'mossy').save(os.path.join(root, 'bone_mossy.png'))
        bone_tex(n, 'mossy_top').save(os.path.join(root, 'bone_mossy_top.png'))
        bone_tex(n, 'fresh').save(os.path.join(root, 'bone_fresh.png'))
        socket_tex(n).save(os.path.join(root, 'bone_socket.png'))
        antler_tex(n, 'weathered').save(os.path.join(root, 'antler_weathered.png'))
        antler_tex(n, 'base', seed=14).save(os.path.join(root, 'antler_weathered_base.png'))
        antler_tex(n, 'fresh').save(os.path.join(root, 'antler_fresh.png'))
        antler_tex(n, 'fresh', seed=15).save(os.path.join(root, 'antler_fresh_base.png'))
        horn_tex(n, 'horn').save(os.path.join(root, 'bison_horn.png'))
        horn_tex(n, 'tip').save(os.path.join(root, 'bison_horn_tip.png'))

    # item icons (16 px, outlined like the mod's other icons) and item models
    # [polish] the 32x32 inventory icons are re-rendered from the models by tools/polish/bone_icons.py: run it after this script
    idir = os.path.join(ASSETS, 'textures', 'item')
    os.makedirs(idir, exist_ok=True)
    for sp in ('whitetail', 'elk', 'moose', 'bison'):
        skull_icon(sp).save(os.path.join(idir, f'{sp}_skull.png'))
        wjson(os.path.join(ASSETS, 'models', 'item', f'{sp}_skull.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{sp}_skull'}})
    bones_icon().save(os.path.join(idir, 'scattered_bones.png'))
    wjson(os.path.join(ASSETS, 'models', 'item', 'scattered_bones.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/scattered_bones'}})
    shed_icon().save(os.path.join(idir, 'shed_antler.png'))
    wjson(os.path.join(ASSETS, 'models', 'item', 'shed_antler.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/shed_antler'}})

    # loot: antlered skulls, bison skulls and sheds drop themselves; bare skulls and scattered bones give bones
    ldir = os.path.join(DATA, 'loot_table', 'blocks')

    def self_drop(name):
        return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
                                                      'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
                'random_sequence': f'{NS}:blocks/{name}'}

    def bones_drop(name, lo, hi, cond=None):
        pool = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': 'minecraft:bone',
                                         'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}},
                                                       {'function': 'minecraft:explosion_decay'}]}]}
        if cond:
            pool['conditions'] = cond
        return pool

    for sp in ('whitetail', 'elk', 'moose'):
        name = f'{sp}_skull'
        antlered = [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{name}', 'properties': {'antlers': 'true'}}]
        bare = [{'condition': 'minecraft:block_state_property', 'block': f'{NS}:{name}', 'properties': {'antlers': 'false'}}]
        wjson(os.path.join(ldir, name + '.json'), {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
             'conditions': antlered + [{'condition': 'minecraft:survives_explosion'}]},
            bones_drop(name, 1, 2, bare)], 'random_sequence': f'{NS}:blocks/{name}'})
    wjson(os.path.join(ldir, 'bison_skull.json'), self_drop('bison_skull'))
    wjson(os.path.join(ldir, 'shed_antler.json'), self_drop('shed_antler'))
    wjson(os.path.join(ldir, 'scattered_bones.json'), {'type': 'minecraft:block', 'pools': [bones_drop('scattered_bones', 1, 3)],
                                                        'random_sequence': f'{NS}:blocks/scattered_bones'})
    print('done')


if __name__ == '__main__':
    main()
