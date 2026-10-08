#!/usr/bin/env python3
"""[smalls] Software previews of an OBJ item model (neoforge:obj + its .mtl + textures) the way Minecraft 1.21.1 places it:
first person (ItemInHandRenderer: translate(+-0.56, -0.52, -0.72), the display transform, -0.5; FOV 70, 16:9), third person
(a Steve-sized player, ItemInHandLayer: arm pivot, X -90, Y 180, translate(+-1/16, 0.125, -0.625)), on the ground, in a
frame, and a turntable. Perspective, z-buffered, back faces culled (as the item render types do), nearest texel sampling,
entity-style two-light diffuse (0.4 ambient + 0.6 per light), emissive materials (Ka > 0) at full brightness.

    python3 tools/smalls/lamp_preview.py <repo> <model json under models/item, no .json> <out dir>
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

R = os.path.abspath(sys.argv[1])
NAME = sys.argv[2]
OUT = sys.argv[3]
os.makedirs(OUT, exist_ok=True)
A = os.path.join(R, 'patch', 'assets', 'frontierhunts')


def tex_path(rl):
    ns, p = rl.split(':', 1)
    return os.path.join(A, 'textures', p + '.png')


# ============================================================================================ load


def load():
    js = json.load(open(os.path.join(A, 'models', 'item', NAME + '.json')))
    obj = js['model'].split(':', 1)[1]
    objp = os.path.join(A, obj)
    V, VT, VN, F = [], [], [], []
    mat = None
    mtl = {}
    for line in open(objp):
        t = line.split()
        if not t:
            continue
        if t[0] == 'mtllib':
            for ml in open(os.path.join(os.path.dirname(objp), t[1])):
                q = ml.split()
                if not q:
                    continue
                if q[0] == 'newmtl':
                    cur = q[1]
                    mtl[cur] = {'ka': 0.0}
                elif q[0] == 'map_Kd':
                    mtl[cur]['tex'] = np.asarray(Image.open(tex_path(q[1])).convert('RGBA')).astype(np.float32) / 255.0
                elif q[0] == 'Ka':
                    mtl[cur]['ka'] = sum(float(x) for x in q[1:4]) / 3.0
        elif t[0] == 'v':
            V.append([float(x) for x in t[1:4]])
        elif t[0] == 'vt':
            VT.append([float(x) for x in t[1:3]])
        elif t[0] == 'vn':
            VN.append([float(x) for x in t[1:4]])
        elif t[0] == 'usemtl':
            mat = t[1]
        elif t[0] == 'f':
            idx = [[int(k) - 1 for k in c.split('/')] for c in t[1:]]
            for i in range(1, len(idx) - 1):  # fan to triangles
                F.append((mat, idx[0], idx[i], idx[i + 1]))
    return js, np.array(V), np.array(VT), np.array(VN), F, mtl


JS, V, VT, VN, FACES, MTL = load()
DISPLAY = JS['display']

# ============================================================================================ matrices


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def RX(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1.0]])


def RY(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1.0]])


def RZ(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1.0]])


def D(a):
    return math.radians(a)


def display(ctx, left=False):
    """ItemTransform.apply: translate(+-t/16), rotationXYZ (y, z mirrored for the left hand), scale; then the -0.5 centring"""
    d = DISPLAY.get(ctx, {})
    r = d.get('rotation', [0, 0, 0])
    t = [v / 16.0 for v in d.get('translation', [0, 0, 0])]
    s = d.get('scale', [1, 1, 1])
    rx, ry, rz = r
    if left:
        ry, rz = -ry, -rz
    m = T((-1 if left else 1) * t[0], t[1], t[2]) @ RX(D(rx)) @ RY(D(ry)) @ RZ(D(rz)) @ S(*s)
    return m @ T(-0.5, -0.5, -0.5)


# ============================================================================================ raster


class Canvas:
    def __init__(self, w, h, bg=None, fov=70.0, ortho=None):
        self.w, self.h = w, h
        self.col = np.zeros((h, w, 3), np.float32) if bg is None else bg.astype(np.float32).copy()
        self.z = np.full((h, w), np.inf, np.float32)
        self.f = 1.0 / math.tan(math.radians(fov) / 2)
        self.ortho = ortho

    def project(self, p):
        """view space (camera at 0 looking -z, +y up) -> pixels, depth"""
        if self.ortho:
            sc = self.h / self.ortho
            return np.stack([self.w / 2 + p[:, 0] * sc, self.h / 2 - p[:, 1] * sc, 10.0 - p[:, 2]], 1)
        zz = -p[:, 2]
        sx = self.w / 2 + p[:, 0] * self.f / zz * self.h / 2
        sy = self.h / 2 - p[:, 1] * self.f / zz * self.h / 2
        return np.stack([sx, sy, zz], 1)

    def draw(self, M, light_dirs, ambient=0.4, per=0.6, tint=1.0):
        """draws the model with view matrix M (model block space -> view); lights given in view space"""
        Vh = np.c_[V, np.ones(len(V))] @ M.T
        P = self.project(Vh[:, :3])
        N = VN @ M[:3, :3].T
        N /= np.linalg.norm(N, axis=1, keepdims=True) + 1e-9
        L = [np.asarray(l, float) / np.linalg.norm(l) for l in light_dirs]
        for mat, a, b, c in FACES:
            ia, ib, ic = a[0], b[0], c[0]
            pa, pb, pc = P[ia], P[ib], P[ic]
            if min(pa[2], pb[2], pc[2]) <= 0.01:
                continue
            area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pc[0] - pa[0]) * (pb[1] - pa[1])
            if area >= 0:  # screen y is down: CCW from outside has negative area here -> cull the rest
                continue
            x0 = int(max(0, math.floor(min(pa[0], pb[0], pc[0]))))
            x1 = int(min(self.w - 1, math.ceil(max(pa[0], pb[0], pc[0]))))
            y0 = int(max(0, math.floor(min(pa[1], pb[1], pc[1]))))
            y1 = int(min(self.h - 1, math.ceil(max(pa[1], pb[1], pc[1]))))
            if x1 < x0 or y1 < y0:
                continue
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float32)
            xs += 0.5
            ys += 0.5
            w0 = ((pb[0] - xs) * (pc[1] - ys) - (pc[0] - xs) * (pb[1] - ys)) / area
            w1 = ((pc[0] - xs) * (pa[1] - ys) - (pa[0] - xs) * (pc[1] - ys)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            if not inside.any():
                continue
            # perspective-correct interpolation
            iz = w0 / pa[2] + w1 / pb[2] + w2 / pc[2]
            depth = 1.0 / iz
            zb = self.z[y0:y1 + 1, x0:x1 + 1]
            vis = inside & (depth < zb)
            if not vis.any():
                continue
            k0, k1, k2 = w0 / pa[2] / iz, w1 / pb[2] / iz, w2 / pc[2] / iz
            uv = VT[a[1]][None, None, :] * k0[..., None] + VT[b[1]][None, None, :] * k1[..., None] + VT[c[1]][None, None, :] * k2[..., None]
            n = N[a[2]][None, None, :] * k0[..., None] + N[b[2]][None, None, :] * k1[..., None] + N[c[2]][None, None, :] * k2[..., None]
            n /= np.linalg.norm(n, axis=2, keepdims=True) + 1e-9
            m = MTL[mat]
            tx = m['tex']
            th, tw = tx.shape[:2]
            ui = np.clip((uv[..., 0] % 1.0001) * tw, 0, tw - 1).astype(int)
            vi = np.clip(uv[..., 1] * th, 0, th - 1).astype(int)
            texel = tx[vi, ui, :3]
            if m['ka'] > 0:
                shade = np.ones(n.shape[:2], np.float32)
            else:
                shade = ambient + sum(per * np.clip((n * l[None, None, :]).sum(2), 0, 1) for l in L)
                shade = np.minimum(shade, 1.0) * tint
            colr = texel * shade[..., None]
            region = self.col[y0:y1 + 1, x0:x1 + 1]
            region[vis] = colr[vis] * 255.0
            zb[vis] = depth[vis]

    def image(self):
        return Image.fromarray(np.clip(self.col, 0, 255).astype(np.uint8), 'RGB')


# entity lighting (Lighting.DIFFUSE_LIGHT_0/1), in world space
L0 = (0.2, 1.0, -0.7)
L1 = (-0.2, 1.0, 0.7)


def view_lights(view):
    return [view[:3, :3] @ np.array(L0), view[:3, :3] @ np.array(L1)]


# ============================================================================================ scenes


def sky(w, h, top=(52, 70, 96), bottom=(150, 160, 150)):
    t = np.linspace(0, 1, h)[:, None, None]
    return np.array(top, np.float32)[None, None, :] * (1 - t) + np.array(bottom, np.float32)[None, None, :] * t + np.zeros((h, w, 3))


def forest_bg(w, h, seed=3):
    """a dusk forest floor seen from eye height: sky, a band of dark trees, the ground falling away; plus a blood trail"""
    rng = np.random.default_rng(seed)
    img = sky(w, h, (38, 48, 66), (96, 92, 84))
    im = Image.fromarray(img.astype(np.uint8))
    d = ImageDraw.Draw(im)
    hz = int(h * 0.46)
    for i in range(70):
        x = rng.uniform(0, w)
        tw = rng.uniform(w * 0.01, w * 0.035)
        th = rng.uniform(h * 0.25, h * 0.55)
        c = tuple(int(v) for v in rng.uniform(22, 40, 3) * (0.8, 0.95, 0.8))
        d.rectangle([x, hz - th, x + tw, hz + 4], fill=c)
    d.rectangle([0, hz, w, h], fill=(54, 58, 40))
    for i in range(1600):
        x, y = rng.uniform(0, w), rng.uniform(hz, h)
        s = 1 + (y - hz) / h * 10
        c = tuple(int(v) for v in rng.uniform(40, 80, 3) * (1.0, 0.95, 0.6))
        d.rectangle([x, y, x + s * 2, y + s * 0.6], fill=c)
    for i in range(14):  # the blood the lamp shows: bright red drops leading off
        y = hz + (h - hz) * (0.05 + i / 16.0) ** 1.6
        x = w * 0.5 + math.sin(i * 0.7) * w * 0.05 * (1 + i / 6)
        s = 1.5 + (y - hz) / h * 18
        d.ellipse([x - s, y - s * 0.4, x + s, y + s * 0.4], fill=(220, 20, 26))
    return np.asarray(im).astype(np.float32)


def hud(im, w, h, gs):
    d = ImageDraw.Draw(im)
    cx, cy = w // 2, h // 2
    d.rectangle([cx - gs * 4, cy - gs // 2, cx + gs * 4, cy + gs // 2], fill=(235, 235, 235))
    d.rectangle([cx - gs // 2, cy - gs * 4, cx + gs // 2, cy + gs * 4], fill=(235, 235, 235))
    hw, hh = 182 * gs, 22 * gs
    x0, y0 = (w - hw) // 2, h - hh
    d.rectangle([x0, y0, x0 + hw, y0 + hh], fill=(40, 40, 40), outline=(120, 120, 120), width=gs)
    for i in range(9):
        d.rectangle([x0 + gs + i * 20 * gs, y0 + gs, x0 + gs + i * 20 * gs + 20 * gs, y0 + hh - gs], outline=(90, 90, 90), width=gs)
    d.rectangle([x0, y0, x0 + 24 * gs, y0 + hh], outline=(240, 240, 240), width=gs)
    return im


def first_person(path, left=False, w=1920, h=1080, equip=0.0):
    bg = forest_bg(w, h)
    cv = Canvas(w, h, bg, fov=70)
    i = -1 if left else 1
    M = T(i * 0.56, -0.52 + equip * -0.6, -0.72) @ display('firstperson_lefthand' if left else 'firstperson_righthand', left)
    view = np.eye(4) @ RX(D(-25))  # lights for a player looking slightly down at the trail
    cv.draw(M, view_lights(view), tint=0.85)
    im = hud(cv.image(), w, h, 3)
    im.save(path)


def steve_boxes():
    """(pivot, size, offset, colour) in ModelPart px (y down) for head, body, arms, legs"""
    skin, shirt, pants = (190, 140, 110), (60, 90, 70), (60, 60, 90)
    return {
        'head': ((0, 0, 0), (8, 8, 8), (-4, -8, -4), skin),
        'body': ((0, 0, 0), (8, 12, 4), (-4, 0, -2), shirt),
        'right_arm': ((-5, 2, 0), (4, 12, 4), (-3, -2, -2), shirt),
        'left_arm': ((5, 2, 0), (4, 12, 4), (-1, -2, -2), shirt),
        'right_leg': ((-1.9, 12, 0), (4, 12, 4), (-2, 0, -2), pants),
        'left_leg': ((1.9, 12, 0), (4, 12, 4), (-2, 0, -2), pants),
    }


def box_tris(size, offset):
    x0, y0, z0 = offset
    sx, sy, sz = size
    c = np.array([[x0, y0, z0], [x0 + sx, y0, z0], [x0 + sx, y0 + sy, z0], [x0, y0 + sy, z0], [x0, y0, z0 + sz], [x0 + sx, y0, z0 + sz],
                  [x0 + sx, y0 + sy, z0 + sz], [x0, y0 + sy, z0 + sz]], float)
    quads = [(0, 1, 2, 3), (5, 4, 7, 6), (4, 0, 3, 7), (1, 5, 6, 2), (4, 5, 1, 0), (3, 2, 6, 7)]
    return c, quads


def draw_boxes(cv, parts, view, lights):
    for M, size, offset, col in parts:
        c, quads = box_tris(size, offset)
        P = (np.c_[c / 16.0, np.ones(8)] @ (view @ M).T)[:, :3]
        S_ = cv.project(P)
        for q in quads:
            for tri in ((q[0], q[1], q[2]), (q[0], q[2], q[3])):
                pa, pb, pc = S_[tri[0]], S_[tri[1]], S_[tri[2]]
                area = (pb[0] - pa[0]) * (pc[1] - pa[1]) - (pc[0] - pa[0]) * (pb[1] - pa[1])
                x0 = int(max(0, min(pa[0], pb[0], pc[0])))
                x1 = int(min(cv.w - 1, max(pa[0], pb[0], pc[0]) + 1))
                y0 = int(max(0, min(pa[1], pb[1], pc[1])))
                y1 = int(min(cv.h - 1, max(pa[1], pb[1], pc[1]) + 1))
                if x1 < x0 or y1 < y0 or abs(area) < 1e-6:
                    continue
                n = np.cross(P[tri[1]] - P[tri[0]], P[tri[2]] - P[tri[0]])
                n /= np.linalg.norm(n) + 1e-9
                ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float32) + 0.5
                w0 = ((pb[0] - xs) * (pc[1] - ys) - (pc[0] - xs) * (pb[1] - ys)) / area
                w1 = ((pc[0] - xs) * (pa[1] - ys) - (pa[0] - xs) * (pc[1] - ys)) / area
                w2 = 1 - w0 - w1
                ins = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
                iz = w0 / pa[2] + w1 / pb[2] + w2 / pc[2]
                dep = 1 / np.maximum(iz, 1e-9)
                zb = cv.z[y0:y1 + 1, x0:x1 + 1]
                vis = ins & (dep < zb)
                sh = min(1.0, 0.4 + sum(0.6 * max(0.0, abs(float(np.dot(n, l / np.linalg.norm(l))))) for l in lights))
                cv.col[y0:y1 + 1, x0:x1 + 1][vis] = np.array(col, np.float32) * sh
                zb[vis] = dep[vis]


def third_person(path, left=False, yaw=35.0, w=900, h=1100):
    """a player standing, arm forward in the ITEM pose; camera in front at a 3/4 angle"""
    bg = sky(w, h, (120, 150, 190), (150, 170, 140))
    cv = Canvas(w, h, bg, fov=40)
    # entity -> world: LivingEntityRenderer scale(-1,-1,1), translate(0,-1.501,0), body yaw 180
    ent = T(0, 0, 0) @ RY(D(180)) @ S(-1, -1, 1) @ T(0, -1.501, 0)
    view = T(0, -1.05, -3.6) @ RX(D(8)) @ RY(D(yaw))
    boxes = steve_boxes()
    arm_x = -0.31416 * (1.0) * 1.0  # ArmPose.ITEM: xRot * 0.5 - 0.31415927
    parts = []
    for k, (piv, size, off, col) in boxes.items():
        M = ent @ T(piv[0] / 16, piv[1] / 16, piv[2] / 16)
        if (k == 'right_arm' and not left) or (k == 'left_arm' and left):
            M = M @ RX(arm_x)
        parts.append((M, size, off, col))
    lights = view_lights(view)
    draw_boxes(cv, parts, view, lights)
    side = 'left_arm' if left else 'right_arm'
    piv = boxes[side][0]
    M = ent @ T(piv[0] / 16, piv[1] / 16, piv[2] / 16) @ RX(arm_x) @ RX(D(-90)) @ RY(D(180)) @ T((-1 if left else 1) / 16.0, 0.125, -0.625)
    M = M @ display('thirdperson_lefthand' if left else 'thirdperson_righthand', left)
    cv.draw(view @ M, lights)
    cv.image().save(path)


def ground(path, w=700, h=500):
    bg = sky(w, h, (130, 160, 200), (140, 150, 120))
    cv = Canvas(w, h, bg, fov=50)
    view = T(0, -0.15, -1.6) @ RX(D(30)) @ RY(D(30))
    # a grass block top under it
    parts = [(np.eye(4) @ T(-0.5, -1.0, -0.5), (16, 16, 16), (0, 0, 0), (96, 140, 70))]
    draw_boxes(cv, [(T(-0.5, -1.0, -0.5), (16, 16, 16), (0, 0, 0), (96, 140, 70))], view, view_lights(view))
    gs = DISPLAY['ground']['scale'][1]
    M = T(0, 0.125 + 0.25 * gs, 0) @ RY(D(20)) @ display('ground')
    cv.draw(view @ M, view_lights(view))
    cv.image().save(path)


def turntable(path, size=520, yaws=(30, 120, 210, 300), pitch=20, extra=()):
    tiles = []
    for yaw in list(yaws) + list(extra):
        if isinstance(yaw, tuple):
            yaw, pt = yaw
        else:
            pt = pitch
        cv = Canvas(size, size, sky(size, size, (70, 74, 80), (110, 112, 116)), ortho=1.0)
        view = RX(D(pt)) @ RY(D(yaw))
        M = view @ T(-0.5, -0.5, -0.5)
        cv.draw(M, view_lights(view))
        tiles.append(cv.image())
    out = Image.new('RGB', (size * len(tiles), size))
    for i, t in enumerate(tiles):
        out.paste(t, (i * size, 0))
    out.save(path)


def closeup(path, size=900, yaw=200, pitch=18, zoom=0.62, cx=0.0, cy=0.0):
    cv = Canvas(size, size, sky(size, size, (60, 64, 70), (104, 106, 110)), ortho=zoom)
    view = T(cx, cy, 0) @ RX(D(pitch)) @ RY(D(yaw))
    cv.draw(view @ T(-0.5, -0.5, -0.5), view_lights(view))
    cv.image().save(path)


def icon(path, px=32, ss=12, yaw=-130, roll=42, tilt=12, skip=('cord', 'lock')):
    """the 32x32 inventory icon: rendered from the mesh with flat, front-lit GUI-style shading, box-filtered down, hard
    alpha and a one-pixel dark outline like the mod's other painted gear icons"""
    n = px * ss
    cv = Canvas(n, n, np.zeros((n, n, 3)), ortho=0.92)
    view = RZ(D(roll)) @ RX(D(tilt)) @ RY(D(yaw))
    keep = [f for f in FACES if f[0] not in skip]
    saved = FACES[:]
    FACES[:] = keep
    cv.draw(view @ T(-0.5, -0.47, -0.5), [np.array([-0.4, 0.6, 1.0]), np.array([0.3, 0.2, 1.0])], ambient=0.62, per=0.45)
    FACES[:] = saved
    mask = np.isfinite(cv.z).astype(np.float32)
    col = cv.col
    a = mask.reshape(px, ss, px, ss).mean((1, 3))
    c = (col * mask[..., None]).reshape(px, ss, px, ss, 3).sum((1, 3)) / np.maximum(1e-6, mask.reshape(px, ss, px, ss).sum((1, 3)))[..., None]
    solid = a > 0.42
    out = np.zeros((px, px, 4), np.float32)
    # a touch of contrast so it reads at 1x
    c = np.clip((c - 128) * 1.18 + 132, 0, 255)
    out[solid, :3] = c[solid]
    out[solid, 3] = 255
    edge = np.zeros_like(solid)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(solid, (dy, dx), (0, 1))
    edge &= ~solid
    out[edge] = (22, 18, 16, 255)
    Image.fromarray(out.astype(np.uint8), 'RGBA').save(path)


if __name__ == '__main__':
    first_person(os.path.join(OUT, 'lamp_firstperson_right.png'))
    first_person(os.path.join(OUT, 'lamp_firstperson_left.png'), left=True)
    third_person(os.path.join(OUT, 'lamp_thirdperson.png'))
    ground(os.path.join(OUT, 'lamp_ground.png'))
    turntable(os.path.join(OUT, 'lamp_turntable.png'))
    closeup(os.path.join(OUT, 'lamp_closeup_front.png'), yaw=150, pitch=15)
    closeup(os.path.join(OUT, 'lamp_closeup_tail.png'), yaw=330, pitch=-10)
    # the inventory icon (separate_transforms gui perspective), straight into the patch
    icon(os.path.join(A, 'textures', 'item', 'tracking_lamp.png'))
    Image.open(os.path.join(A, 'textures', 'item', 'tracking_lamp.png')).resize((256, 256), Image.NEAREST).save(os.path.join(OUT, 'lamp_icon_x8.png'))
    print('previews in', OUT)
