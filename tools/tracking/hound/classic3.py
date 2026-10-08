"""[hound3] Vanilla-preset box hound: the part / box layout of HoundModel.create(), its 64x64 redbone and bluetick
coats painted for that layout, and textured previews of the poses through a python mirror of HoundModel.setupAnim
(vanilla ModelPart maths: box UV layout with fractional sizes, ZYX part rotation, entity flip, 0.9 render scale).
The layout and setupAnim here MUST match src/.../tracking/client/HoundModel.java.

  python3 classic3.py          -> writes patch/.../textures/entity/wildlife/hound_{redbone,bluetick}.png
                                  and /tmp/claude-0/h3/classic3_{coat}.png previews"""
import sys, os, math, numpy as np
sys.path.insert(0, '/home/claude/fh/ultra')
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
OUT = ROOT + '/patch/assets/frontierhunts/textures/entity/wildlife/'
PREV = '/tmp/claude-0/h4/'
THIGH, GASKIN, META = -0.45, 1.0, -0.55

# part: (parent, pivot, rest rotation (x, y, z), [(texU, texV, x, y, z, w, h, d, role)])
PARTS = {
    'root': (None, (0, 0, 0), (0, 0, 0), []),
    'body': ('root', (0, 14, 0), (0, 0, 0), [(0, 0, -3, -0.5, -6.5, 6, 6, 7, 'chest'), (0, 13, -2.5, 0, 0.5, 5, 4, 3, 'loin'),
                                           (16, 13, -3, -0.3, 3.5, 6, 4.8, 3.5, 'croup')]),
    'neck': ('body', (0, 1.8, -5.2), (0.85, 0, 0), [(26, 0, -2, -4.5, -2, 4, 6, 4, 'neck')]),
    'head': ('neck', (0, -4.5, 0.2), (-0.85, 0, 0), [(42, 0, -2.5, -3, -3, 5, 4.5, 5, 'skull'), (42, 10, -1.5, -2.2, -7.5, 3, 3, 4.5, 'muzzle'),
                                                  (56, 18, -1, -2.4, -7.9, 2, 1.2, 1, 'nose'), (42, 18, -1.7, 0.4, -7.3, 3.4, 1.4, 3.6, 'flews')]),
    'ear_l': ('head', (-2.5, -2.4, -0.8), (0, 0, 0), [(0, 40, -1, 0, -1.5, 1, 7, 3, 'ear')]),
    'ear_r': ('head', (2.5, -2.4, -0.8), (0, 0, 0), [(8, 40, 0, 0, -1.5, 1, 7, 3, 'ear')]),
    'tail': ('body', (0, 0.6, 6.6), (-0.75, 0, 0), [(56, 24, -0.5, -5, -0.5, 1, 5, 1, 'tail')]),
    'tail_tip': ('tail', (0, -4.8, 0), (0.45, 0, 0), [(60, 24, -0.5, -4, -0.5, 1, 4, 1, 'tailtip')]),
}
for i, (nm, x) in enumerate((('leg_fl', -1.9), ('leg_fr', 1.9))):
    PARTS[nm] = ('root', (x, 16.5, -4.4), (0, 0, 0), [(0, 24, -1, -0.5, -1.25, 2, 4.5, 2.5, 'uleg')])
    PARTS[nm + '.lower'] = (nm, (0, 4, 0), (0, 0, 0), [(10, 24, -0.9, 0, -0.9, 1.8, 3.5, 1.8, 'lleg'), (18, 24, -1.1, 2.5, -1.9, 2.2, 1, 2.8, 'foot')])
for i, (nm, x) in enumerate((('leg_bl', -1.9), ('leg_br', 1.9))):
    PARTS[nm] = ('root', (x, 16, 4.6), (THIGH, 0, 0), [(0, 31, -1.4, -1, -1.6, 2.8, 4.6, 3.4, 'thigh')])
    PARTS[nm + '.lower'] = (nm, (0, 3.6, 0), (GASKIN, 0, 0), [(14, 31, -0.9, 0, -0.9, 1.8, 3.6, 1.8, 'gaskin')])
    PARTS[nm + '.foot'] = (nm + '.lower', (0, 3, 0), (META, 0, 0), [(22, 31, -0.8, 0, -0.8, 1.6, 2.2, 1.6, 'hock'),
                                                                     (30, 31, -1.1, 1.2, -2, 2.2, 1, 2.8, 'foot')])
ORDER = list(PARTS)
LEGS = ['leg_fl', 'leg_fr', 'leg_bl', 'leg_br']


def face_rects(u, v, w, h, d):
    f4, f5, f6, f7, f8, f9 = u, u + d, u + d + w, u + d + w + w, u + d + w + d, u + d + w + d + w
    f10, f11, f12 = v, v + d, v + d + h
    # vanilla Cube polygons: 'top' is the visual top (model -y = Direction.DOWN polygon)
    return {'top': (f5, f10, f6, f11), 'bottom': (f6, f11, f7, f10), 'west': (f4, f11, f5, f12),
            'front': (f5, f11, f6, f12), 'east': (f6, f11, f8, f12), 'back': (f8, f11, f9, f12)}


# ------------------------------------------------------------------------------------------------ texture
# [hound4] brighter: the old red (128, 44, 19) went near-black on the shaded sides under Minecraft entity lighting
RED = dict(base=np.array([170, 70, 30]), deep=np.array([136, 50, 20]), light=np.array([198, 100, 50]), dark=np.array([80, 30, 12]))
BLUE = dict(white=np.array([200, 204, 210]), black=np.array([24, 24, 28]), tick=np.array([58, 68, 88]), tan=np.array([166, 96, 44]))


def paint(kind, seed):
    rng = np.random.default_rng(seed)
    img = np.zeros((64, 64, 4))
    blue = kind == 'bluetick'

    def px(x, y, c):
        if 0 <= x < 64 and 0 <= y < 64:
            img[y, x, :3] = np.clip(c, 0, 255); img[y, x, 3] = 255

    def coat(fx, fy, role, face):
        n = rng.integers(-6, 7)
        if not blue:
            b, d, l, k = RED['base'].astype(float), RED['deep'], RED['light'], RED['dark']
            c = b * (0.94 + 0.12 * fy)
            if face == 'top':
                c = d * 0.45 + b * 0.55
            elif face == 'bottom':
                c = l * 0.95
            if role == 'chest' and face == 'front':
                c = b * 0.7 + l * 0.3
            if role in ('loin',) and face in ('west', 'east') and fy > 0.6:
                c = l * 0.9
            if role in ('muzzle', 'flews'):
                c = b * 1.03 if fy < 0.6 or face == 'top' else d
            if role == 'nose':
                c = np.array([30, 22, 20]) * (1.0 + 0.2 * (fy < 0.4))
            if role == 'ear':
                c = d * (0.92 + 0.18 * fy)
            if role in ('foot',):
                c = d * 0.9 if face != 'bottom' else k
            if role in ('lleg', 'hock') and fy > 0.8:
                c = d * 0.95
            if role == 'tailtip':
                c = b * (1.0 - 0.15 * fy)
            return c + n
        w_, bk, tk, tn = BLUE['white'].astype(float), BLUE['black'].astype(float), BLUE['tick'], BLUE['tan']
        c = w_.copy()
        if rng.random() < 0.26:
            c = (tk if rng.random() < 0.6 else tk * 0.6) + rng.integers(-6, 7)
        if role in ('chest', 'loin', 'croup', 'thigh'):
            saddle = face == 'top' or face in ('west', 'east') and fy < (0.45 if role != 'thigh' else 0.3)
            if role == 'croup' and face == 'back' and fy < 0.4:
                saddle = True
            if saddle:
                c = bk.copy()
            if role == 'chest' and face == 'front':
                c = w_ * 0.95 if rng.random() > 0.3 else tk
        if role in ('skull', 'ear'):
            c = bk.copy()
        if role == 'neck':
            c = bk.copy() if face in ('top', 'back') or face in ('west', 'east') and fx > 0.45 else c
        if role in ('muzzle', 'flews'):
            c = tn * 0.95 if face in ('bottom', 'front') or fy > 0.4 else bk * 1.15
        if role == 'nose':
            c = np.array([26, 22, 22])
        if role in ('lleg', 'gaskin', 'hock', 'foot') and fy > 0.35:
            c = tn * (1.0 - 0.12 * (fy > 0.85))
        if role == 'tail':
            c = bk.copy() if fy < 0.35 else c
        if role == 'tailtip':
            c = w_ * 0.85 if fy < 0.35 else c
        return c + n

    for name in ORDER:
        for (u, v, x, y, z, w, h, d, role) in PARTS[name][3]:
            R = face_rects(u, v, w, h, d)
            for face, (u1, v1, u2, v2) in R.items():
                x0, x1 = sorted((u1, u2)); y0, y1 = sorted((v1, v2))
                # texels whose centres fall inside the (possibly fractional) face rectangle
                for yy in range(int(math.floor(y0)), int(math.ceil(y1))):
                    for xx in range(int(math.floor(x0)), int(math.ceil(x1))):
                        if not (x0 <= xx + 0.5 <= x1 + 0.35 and y0 <= yy + 0.5 <= y1 + 0.35):
                            continue
                        fx = (xx + 0.5 - x0) / max(0.5, x1 - x0); fy = (yy + 0.5 - y0) / max(0.5, y1 - y0)
                        if face == 'bottom':
                            fy = 1.0
                        px(xx, yy, coat(min(fx, 1), min(fy, 1), role, face))
            if role == 'skull':
                # eyes + brows at the front of each side face, (bluetick) tan pips over the eyes on the front
                for f in ('west', 'east'):
                    u1, v1, u2, v2 = R[f]
                    ex = int(math.floor(min(u1, u2))) if f == 'west' else int(math.ceil(max(u1, u2))) - 1
                    px(ex, int(v1) + 2, [30, 16, 10])
                    px(ex, int(v1) + 1, BLUE['tan'] * 1.05 if blue else RED['light'] * 1.12)
                u1, v1, u2, v2 = R['front']
                xs = list(range(int(min(u1, u2)), int(math.ceil(max(u1, u2)))))
                px(xs[0], int(v1) + 2, [30, 16, 10]); px(xs[-1], int(v1) + 2, [30, 16, 10])
                if blue:
                    px(xs[0], int(v1) + 1, BLUE['tan']); px(xs[-1], int(v1) + 1, BLUE['tan'])
            if role == 'muzzle':
                u1, v1, u2, v2 = R['front']
                for xx in range(int(min(u1, u2)), int(math.ceil(max(u1, u2)))):
                    px(xx, int(v2) - 1, [44, 26, 20] if not blue else [60, 40, 30])   # lip line
    # bleed island colours one texel into the empty space (no dark seams in mips)
    a = img[..., 3] > 0
    for _ in range(2):
        out = img.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh = np.roll(np.roll(img, dy, 0), dx, 1); m = (~a) & (sh[..., 3] > 0)
            out[m, :3] = sh[m, :3]; out[m, 3] = 0
        img = out
    return Image.fromarray(img.astype('uint8'), 'RGBA')


# ------------------------------------------------------------------------------------------------ setupAnim mirror
OFF_WALK, OFF_TROT, OFF_GALLOP = (0.25, 0.75, 0.0, 0.5), (0.25, 0.75, 0.75, 0.25), (0.2, 0.1, 0.7, 0.8)


def circ(l, wa, wb, wc):
    x = wa * math.cos(OFF_WALK[l] * 2 * math.pi) + wb * math.cos(OFF_TROT[l] * 2 * math.pi) + wc * math.cos(OFF_GALLOP[l] * 2 * math.pi)
    y = wa * math.sin(OFF_WALK[l] * 2 * math.pi) + wb * math.sin(OFF_TROT[l] * 2 * math.pi) + wc * math.sin(OFF_GALLOP[l] * 2 * math.pi)
    return math.atan2(y, x) / (2 * math.pi)


def stride_for(walk, trot, gallop, speed):
    """[hound4] mirror of HoundRig.strideFor"""
    gw = max(walk + trot + gallop, 1e-4)
    duty = (walk * 0.64 + trot * 0.46 + gallop * 0.30) / gw
    stride = (walk * 0.56 + trot * 0.86 + gallop * 1.45) / gw
    if gw < 1e-3:
        duty, stride = 0.64, 0.56
    L = min(stride * duty * (0.65 + 0.35 * min(max(speed / 0.2, 0), 1.6)), 0.40)
    return L / duty


def anim(sniff=0., sit=0., bay=0., tuck=0., wag=0., lie=0., point=0., walk=0., trot=0., gallop=0., phase=0., speed=0., age=0., yaw=0., pitch=0.):
    P = {n: [*PARTS[n][1], *PARTS[n][2]] for n in ORDER}
    up = lambda l: P[LEGS[l]]
    low = lambda l: P[LEGS[l] + '.lower']
    foot = lambda l: P[LEGS[l] + '.foot']
    still = max(sit, lie)
    gw = min(max(walk + trot + gallop, 0), 1)
    mv = gw * min(max(speed / 0.03, 0), 1) * (1 - still)
    gal = gallop * mv
    cyc = phase * 2 * math.pi
    duty = (0.64 * walk + 0.46 * trot + 0.3 * gallop) / max(gw, 1e-3)
    swingA = math.asin(min(max(stride_for(walk, trot, gallop, speed) * duty / 0.84, 0), 0.8))  # [hound4] HoundModel
    for l in range(4):
        p = phase + circ(l, walk + 1e-4, trot, gallop); p -= math.floor(p)
        if p < duty:
            sw = 1 - 2 * p / duty; fold = 0
        else:
            q = (p - duty) / (1 - duty); sw = -1 + 2 * q * q * (3 - 2 * q); fold = math.sin(q * math.pi)
        up(l)[3] += -swingA * sw * mv
        if l < 2:
            low(l)[3] += 1.3 * fold * mv
        else:
            low(l)[3] += 0.5 * fold * mv; foot(l)[3] -= 0.4 * fold * mv
    B, N, H, T, TT = P['body'], P['neck'], P['head'], P['tail'], P['tail_tip']
    B[1] += -0.35 * mv * (0.5 + 0.5 * math.cos(2 * cyc)) * (1 - gallop) - 0.8 * gal * math.sin(cyc + 1.1)
    B[3] += 0.08 * gal * math.sin(cyc - 0.3)
    look = (1 - sniff) * (1 - bay * 0.7)
    H[4] += math.radians(max(-60, min(60, yaw))) * look; H[3] += math.radians(pitch) * look
    N[3] += 0.2 * trot * mv + 0.35 * gal; H[3] -= 0.1 * trot * mv + 0.2 * gal
    N[3] += 1.25 * sniff; H[3] -= 0.35 * sniff; N[4] += 0.3 * sniff * math.sin(age * 0.32)
    N[3] -= 0.5 * bay; H[3] -= 0.7 * bay
    N[3] += 0.3 * point; H[3] -= 0.3 * point; up(0)[3] -= 0.5 * point; low(0)[3] += 1.6 * point
    N[3] += 0.5 * tuck; H[3] -= 0.2 * tuck; B[1] += 1.0 * tuck
    wg = wag * 0.5 * math.sin(age * 1.45) + 0.05 * math.sin(age * 0.09)
    T[4] += wg * (1 - point); TT[4] += wg * 0.6 * (1 - point)
    T[3] += 0.25 * sniff - 0.4 * gal - 0.8 * point - 1.9 * tuck; TT[3] -= 0.4 * point
    if sit > 0:
        s = sit
        B[3] -= 0.62 * s; B[1] += 2.4 * s; N[3] += 0.55 * s
        for l in (0, 1):
            up(l)[1] += 0.4 * s; up(l)[2] += 0.8 * s
        for l in (2, 3):
            up(l)[1] += 4.6 * s; up(l)[2] -= 0.6 * s; up(l)[3] += -1.0 * s; low(l)[3] += 1.04 * s; foot(l)[3] += -1.61 * s
        T[3] += -0.25 * s; TT[3] -= 0.45 * s
    if lie > 0:
        s = lie
        B[1] += 4.6 * s; N[3] -= 0.1 * s
        for l in (0, 1):
            up(l)[1] += 5.6 * s; up(l)[3] += -1.45 * s; low(l)[3] += -0.1 * s
        for l in (2, 3):
            up(l)[1] += 5.4 * s; up(l)[3] += -1.0 * s; low(l)[3] += 1.1 * s; foot(l)[3] += -1.67 * s
        T[3] += -0.8 * s; TT[3] -= 0.45 * s
    hang = H[3] + N[3] + B[3]
    flop = math.sin(2 * cyc) * 0.12 * mv + math.sin(age * 0.1) * 0.03
    P['ear_l'][3] -= hang * 0.95; P['ear_r'][3] -= hang * 0.95
    P['ear_l'][5] += 0.05 + flop + 0.12 * bay + 0.15 * gal
    P['ear_r'][5] -= 0.05 + flop + 0.12 * bay + 0.15 * gal
    return P


# ------------------------------------------------------------------------------------------------ mesh + render
def Rzyx(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    X = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]); Y = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Z = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Z @ Y @ X


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


def mesh(pose, scale=0.9):
    Ms = {}
    Pp = []; UV = []
    for n in ORDER:
        par = PARTS[n][0]
        x, y, z, rx, ry, rz = pose[n]
        M = np.eye(4); M[:3, :3] = Rzyx(rx, ry, rz); M[:3, 3] = [x / 16, y / 16, z / 16]
        if par:
            M = Ms[par] @ M
        Ms[n] = M
        for (u, v, bx, by, bz, w, h, d, role) in PARTS[n][3]:
            for (a, b, c, ua, ub, uc) in cube_tris(bx, by, bz, w, h, d, u, v):
                for p_, uv in ((a, ua), (b, ub), (c, uc)):
                    q = M @ np.r_[np.array(p_) / 16, 1]
                    # LivingEntityRenderer: scale(-1,-1,1), translate(0,-1.501,0); HoundRenderer: scale 0.9 about the feet
                    Pp.append(np.array([-q[0], -q[1] + 1.501, q[2]]) * scale); UV.append([uv[0] / 64, uv[1] / 64])
    return np.array(Pp), np.array(UV)


POSES = [('stand', {}), ('walk', dict(walk=1, speed=0.09, phase=0.1)), ('trot', dict(trot=1, speed=0.18, phase=0.3)),
         ('gallop', dict(gallop=1, speed=0.32, phase=0.6)), ('sniff', dict(sniff=1, wag=1, age=5)), ('bay', dict(bay=1, wag=1, age=3)),
         ('point', dict(point=1)), ('sit', dict(sit=1)), ('lie', dict(lie=1)), ('look', dict(yaw=45, pitch=-15)), ('tuck', dict(tuck=1, walk=1, speed=0.08, phase=0.2))]


def render(tex, fn, views=((90, 5), (35, 15), (180, 5))):
    from sheet import render_parts
    t = np.asarray(tex.convert('RGBA').resize((256, 256), Image.NEAREST)).astype(np.float32) / 255
    W, H = 260, 200
    out = Image.new('RGB', (W * len(POSES), H * len(views))); dr = ImageDraw.Draw(out)
    for c, (nm, kw) in enumerate(POSES):
        P, UV = mesh(anim(**kw))
        T = P.reshape(-1, 3, 3)
        N = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0]); N = N / (np.linalg.norm(N, axis=1, keepdims=True) + 1e-9)
        N = np.repeat(N, 3, 0)
        for r, (yy, pp) in enumerate(views):
            img = render_parts([(P, UV, N, t)], yy, pp, W, H, dist=2.0, center=np.array([0, 0.42, 0]),
                               extra=[((0, 0, -0.7), (0, 0, 0.7), (230, 230, 230))])
            out.paste(img, (c * W, r * H))
        dr.text((c * W + 4, 4), '%s low %.3f' % (nm, P[:, 1].min()), fill=(255, 255, 0))
    out.save(fn)


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True); os.makedirs(PREV, exist_ok=True)
    kinds = sys.argv[1:] or ['redbone', 'bluetick']
    for kind, seed in (('redbone', 11), ('bluetick', 12)):
        if kind not in kinds:
            continue
        tex = paint(kind, seed)
        tex.save(OUT + f'hound_{kind}.png', optimize=True)
        render(tex, PREV + f'classic3_{kind}.png')
    print('ok')
