"""[hound2] Classic (vanilla-style) box hound: the part / box layout, the 64x64 redbone and bluetick textures, and a
textured preview of the poses using vanilla ModelPart maths (cube UV layout, ZYX part rotation, entity flip).
The layout and setupAnim here MUST match src/.../tracking/client/HoundModel.java.

  python3 classic.py          -> writes patch/.../textures/entity/wildlife/hound_{redbone,bluetick}.png
                                 and /tmp/claude-0/h2/classic_{coat}.png previews"""
import sys, os, math, numpy as np
sys.path.insert(0, '/home/claude/fh/ultra')
from PIL import Image, ImageDraw
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = ROOT + '/patch/assets/frontierhunts/textures/entity/wildlife/'
PREV = '/tmp/claude-0/h2/'

# part: (parent, pivot, rest rotation (x, y, z), [(texU, texV, x, y, z, w, h, d, role)])
PARTS = {
    'body': (None, (0, 15, 6), (0, 0, 0), [(0, 0, -3.5, -2.5, -13, 7, 6, 6, 'chest'), (0, 12, -3, -2.2, -7, 6, 4, 8, 'loin')]),
    'head': ('body', (0, -2, -12.5), (0, 0, 0), [(28, 0, -2, -3, -3, 4, 5, 4, 'neck'), (28, 12, -2.5, -4, -6, 5, 5, 5, 'skull'),
                                                (44, 0, -1.5, -2, -11, 3, 4, 5, 'muzzle')]),
    'ear_l': ('head', (-2.5, -3.5, -3), (0, 0, 0), [(48, 12, -1, 0, -1.5, 1, 7, 3, 'ear')]),
    'ear_r': ('head', (2.5, -3.5, -3), (0, 0, 0), [(56, 12, 0, 0, -1.5, 1, 7, 3, 'ear')]),
    'tail': ('body', (0, -1.7, 1), (2.2, 0, 0), [(56, 24, -0.5, 0, -0.5, 1, 6, 1, 'tail')]),
    'tail_tip': ('tail', (0, 6, 0), (-0.5, 0, 0), [(60, 24, -0.5, 0, -0.5, 1, 5, 1, 'tailtip')]),
    'leg_fl': (None, (-2, 16, -4.5), (0, 0, 0), [(0, 24, -1, 0, -1, 2, 8, 2, 'leg')]),
    'leg_fr': (None, (2, 16, -4.5), (0, 0, 0), [(8, 24, -1, 0, -1, 2, 8, 2, 'leg')]),
    'leg_bl': (None, (-2, 16, 5.5), (0, 0, 0), [(16, 24, -1, 0, -1, 2, 8, 2, 'hleg'), (32, 24, -1.5, -1, -1.5, 3, 5, 3, 'thigh')]),
    'leg_br': (None, (2, 16, 5.5), (0, 0, 0), [(24, 24, -1, 0, -1, 2, 8, 2, 'hleg'), (44, 24, -1.5, -1, -1.5, 3, 5, 3, 'thigh')]),
}
ORDER = ['body', 'head', 'ear_l', 'ear_r', 'tail', 'tail_tip', 'leg_fl', 'leg_fr', 'leg_bl', 'leg_br']


def face_rects(u, v, w, h, d):
    w, h, d = int(w), int(h), int(d)
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    # name: (u1, v1, u2, v2) in vanilla Cube order; 'top' is the visual top (model -y)
    return {'top': (f5, f10, f6, f11), 'bottom': (f6, f11, f7, f10), 'west': (f4, f11, f5, f12),
            'front': (f5, f11, f6, f12), 'east': (f6, f11, f8, f12), 'back': (f8, f11, f9, f12)}


# ------------------------------------------------------------------------------------------------ texture
def paint(kind, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((64, 64, 4))
    blue = kind == 'bluetick'
    if not blue:
        base = np.array([132, 46, 20]); deep = np.array([98, 30, 12]); light = np.array([160, 72, 34])
    else:
        white = np.array([196, 200, 206]); black = np.array([26, 26, 30]); tick = np.array([52, 62, 82]); tan = np.array([170, 98, 44])

    def px(x, y, c):
        img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255

    def coat(x, y, fx, fy, role, face):
        """colour of one pixel; fx, fy = 0..1 position across the face (fy: 0 = top edge)"""
        n = rng.integers(-7, 8)
        if not blue:
            c = base.astype(float)
            if face == 'top': c = deep * 0.7 + base * 0.3
            elif face == 'bottom': c = light
            else: c = base * (1.0 + 0.10 * (fy - 0.5)) if role not in ('chest', 'loin') else base * (0.9 + 0.2 * fy)
            if role in ('chest',) and face == 'front': c = c * 0.75 + light * 0.25
            if role == 'muzzle':
                c = base * 1.04
                if face == 'bottom' or fy > 0.75: c = deep * 0.9        # flews / lip line
            if role == 'ear': c = deep * (0.95 + 0.15 * fy)
            if role in ('leg', 'hleg') and fy > 0.85: c = deep * 0.85     # pads / toes
            return c + n
        # bluetick
        c = white.astype(float)
        if rng.random() < 0.24: c = (tick if rng.random() < 0.6 else tick * 0.6) + rng.integers(-6, 7)
        if role in ('chest', 'loin', 'thigh'):
            if face == 'top' or (face in ('west', 'east') and fy < 0.35): c = black.astype(float)
            if role == 'loin' and face in ('west', 'east') and 0.3 < fx < 0.65 and fy < 0.7: c = black.astype(float)
            if face == 'bottom': c = white * 0.92 if rng.random() > 0.2 else tick
        if role in ('skull', 'neck', 'ear'): c = black.astype(float)
        if role == 'neck' and face == 'bottom': c = white * 0.9 if rng.random() > 0.3 else tick
        if role == 'muzzle':
            c = tan * 0.95 if (face == 'bottom' or fy > 0.45 or face == 'front') else black * 1.2
        if role in ('leg', 'hleg') and fy > 0.45: c = tan * (1.0 - 0.15 * (fy > 0.85))
        if role in ('tail', 'tailtip'): c = (tick * 1.25 if rng.random() < 0.55 else white * 0.8) if role == 'tail' else black * (1.2 if fy < 0.6 else 1.0)
        return c + n

    for name in ORDER:
        for (u, v, x, y, z, w, h, d, role) in PARTS[name][3]:
            for face, (u1, v1, u2, v2) in face_rects(u, v, w, h, d).items():
                x0, x1 = sorted((u1, u2)); y0, y1 = sorted((v1, v2))
                for yy in range(y0, y1):
                    for xx in range(x0, x1):
                        fx = (xx - x0 + 0.5) / max(1, x1 - x0); fy = (yy - y0 + 0.5) / max(1, y1 - y0)
                        if face == 'bottom': fy = 1.0
                        px(xx, yy, coat(xx, yy, fx, fy, role, face))
            # faces: eyes / brows on the skull sides near the front, nose leather on the muzzle front
            R = face_rects(u, v, w, h, d)
            if role == 'skull':
                for f in ('west', 'east'):
                    u1, v1, u2, v2 = R[f]; x0 = min(u1, u2); x1 = max(u1, u2)
                    ex = x0 if f == 'west' else x1 - 1        # the front edge of each side face
                    px(ex, v1 + 2, [34, 18, 10]);            # dark eye
                    px(ex, v1 + 1, tan * 1.05 if blue else light * 1.1)      # brow
                u1, v1, u2, v2 = R['front']
                for xx in range(min(u1, u2), max(u1, u2)):
                    if blue and rng.random() < 0.5: pass
                if blue:
                    px(min(u1, u2) + 1, v1 + 1, tan); px(max(u1, u2) - 2, v1 + 1, tan)   # tan pips over the eyes
            if role == 'muzzle':
                u1, v1, u2, v2 = R['front']; xs = range(min(u1, u2), max(u1, u2))
                for xx in xs:
                    px(xx, v1, [28, 22, 20])                 # nose leather: top row, nostril pip below it
                px(xs[1], v1 + 1, [40, 30, 26])
                u1, v1, u2, v2 = R['top']
                for xx in range(min(u1, u2), max(u1, u2)):
                    px(xx, min(v1, v2), [30, 24, 22])        # nose leather seen from above (front row)
    return Image.fromarray(img.astype('uint8'), 'RGBA')


# ------------------------------------------------------------------------------------------------ pose + render
def Rzyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


def anim(limb=0.0, amount=0.0, age=0.0, sniff=0.0, sit=0.0, bay=0.0, tuck=0.0, wag=0.0):
    """mirror of HoundModel.setupAnim: returns {part: [x, y, z, xRot, yRot, zRot]}"""
    P = {n: [*PARTS[n][1], *PARTS[n][2]] for n in ORDER}
    sw = limb * 0.6662; a = math.cos(sw) * 1.4 * amount
    P['leg_fl'][3] += a; P['leg_br'][3] += a; P['leg_fr'][3] -= a; P['leg_bl'][3] -= a
    P['body'][1] += abs(math.sin(sw)) * -0.4 * amount
    flop = math.sin(sw) * 0.12 * amount + math.sin(age * 0.1) * 0.03
    P['tail'][3] += 0.3 * sniff + 0.15 * bay - 1.9 * tuck
    P['tail'][4] += wag * 0.5 * math.sin(age * 1.55) + 0.05 * math.sin(age * 0.1)
    P['tail_tip'][4] += wag * 0.3 * math.sin(age * 1.55 - 0.9)
    head0 = P['head'][3]
    if sniff > 0:
        P['head'][3] += 0.95 * sniff; P['head'][4] += 0.3 * sniff * math.sin(age * 0.32); P['body'][3] += 0.06 * sniff
    P['head'][3] -= 0.85 * bay
    P['head'][3] += 0.35 * tuck
    if sit > 0:
        s = sit
        P['body'][3] -= 0.55 * s; P['body'][1] += 5.6 * s
        P['head'][3] += 0.45 * s
        for l in ('leg_fl', 'leg_fr'):
            P[l][1] += -0.04 * s; P[l][2] += 1.0 * s
        for l in ('leg_bl', 'leg_br'):
            P[l][3] = P[l][3] * (1 - s) - 1.5708 * s; P[l][1] += 6.0 * s; P[l][2] -= 2.0 * s
        P['tail'][3] -= 0.35 * s
    # long leathers hang with gravity: undo most of the head + body pitch, flare out a little with the gait
    pitch = (P['head'][3] - head0) + P['body'][3]
    for n, sg in (('ear_l', 1), ('ear_r', -1)):
        P[n][3] -= 0.85 * pitch
        P[n][5] += sg * (0.06 + flop + 0.12 * bay)
    return P


def cube_tris(x, y, z, w, h, d, u, v):
    x0, y0, z0, x1, y1, z1 = x, y, z, x + w, y + h, z + d
    V = [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]
    R = face_rects(u, v, w, h, d)
    faces = {'top': [5, 4, 0, 1], 'bottom': [2, 3, 7, 6], 'west': [0, 4, 7, 3], 'front': [1, 0, 3, 2], 'east': [5, 1, 2, 6], 'back': [4, 5, 6, 7]}
    out = []
    for f, idx in faces.items():
        u1, v1, u2, v2 = R[f]
        uv = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
        q = [V[i] for i in idx]
        out.append((q[0], q[1], q[2], uv[0], uv[1], uv[2]))
        out.append((q[0], q[2], q[3], uv[0], uv[2], uv[3]))
    return out


def mesh(pose):
    Ms = {}
    Pp = []; UV = []
    for n in ORDER:
        par = PARTS[n][0]
        x, y, z, rx, ry, rz = pose[n]
        M = np.eye(4); M[:3, :3] = Rzyx(rx, ry, rz); M[:3, 3] = [x / 16, y / 16, z / 16]
        if par: M = Ms[par] @ M
        Ms[n] = M
        for (u, v, bx, by, bz, w, h, d, role) in PARTS[n][3]:
            for (a, b, c, ua, ub, uc) in cube_tris(bx, by, bz, w, h, d, u, v):
                for p_, uv in ((a, ua), (b, ub), (c, uc)):
                    q = M @ np.r_[np.array(p_) / 16, 1]
                    Pp.append([-q[0], -q[1] + 1.501, q[2]]); UV.append([uv[0] / 64, uv[1] / 64])
    return np.array(Pp), np.array(UV)


def render(tex, poses, fn, views=((90, 5), (35, 15))):
    from sheet import render_parts
    t = np.asarray(tex.convert('RGBA').resize((256, 256), Image.NEAREST)).astype(np.float32) / 255
    W, H = 300, 230
    out = Image.new('RGB', (W * len(poses), H * len(views))); dr = ImageDraw.Draw(out)
    for c, (nm, kw) in enumerate(poses):
        P, UV = mesh(anim(**kw)); P = P * 0.9
        T = P.reshape(-1, 3, 3)
        N = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]); N = N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-9)
        N = np.repeat(N, 3, 0)
        for r, (yy, pp) in enumerate(views):
            img = render_parts([(P, UV, N, t)], yy, pp, W, H, dist=2.3, center=np.array([0, 0.45, 0]),
                               extra=[((0, 0, -0.7), (0, 0, 0.7), (230, 230, 230))])
            out.paste(img, (c * W, r * H))
        dr.text((c * W + 4, 4), nm, fill=(255, 255, 0))
    out.save(fn)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    poses = [('stand', {}), ('trot', dict(limb=1.2, amount=0.8)), ('sniff', dict(sniff=1, wag=1, age=5)), ('sit', dict(sit=1)),
             ('bay', dict(bay=1, wag=1, age=3)), ('tuck', dict(tuck=1))]
    for kind, seed in (('redbone', 11), ('bluetick', 12)):
        tex = paint(kind, seed)
        tex.save(OUT + f'hound_{kind}.png', optimize=True)
        render(tex, poses, PREV + f'classic_{kind}.png', views=((90, 5), (35, 15), (180, 5)))
    print('ok')
