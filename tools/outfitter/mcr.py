#!/usr/bin/env python3
"""[outfitter] Tiny offline Minecraft entity renderer (numpy z-buffer rasteriser) for checking worn gear.

* ModelPart / Cube exactly like net.minecraft.client.model.geom.ModelPart (texOffs, addBox, CubeDeformation, mirror,
  PartPose offset + rotationZYX, children), UVs per ModelPart.Cube/Polygon, cutout alpha (< 0.1 discarded), no culling.
* Vanilla humanoid poses from HumanoidModel.setupAnim (1.21.1): idle, walk/sprint, crouch, swim/crawl (prone),
  riding, sleeping, bow aim, crossbow hold; PlayerModel wide/slim arms.
* Triangle meshes in the mod's .fheq format (pack) can be drawn in a part's space.
"""
import math, zipfile, io, struct
import numpy as np
from PIL import Image

MC_JAR = '/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar'
_JAR = None


def mc_image(path):
    global _JAR
    if _JAR is None:
        _JAR = zipfile.ZipFile(MC_JAR)
    return Image.open(io.BytesIO(_JAR.read(path))).convert('RGBA')


def tex_array(img):
    if isinstance(img, str):
        img = Image.open(img)
    return np.asarray(img.convert('RGBA'), dtype=np.float32) / 255.0


# ------------------------------------------------------------------------------------------------ model parts
class Cube:
    def __init__(self, u, v, x, y, z, w, h, d, grow=0.0, mirror=False):
        self.u, self.v, self.x, self.y, self.z, self.w, self.h, self.d = u, v, x, y, z, w, h, d
        self.grow = grow if isinstance(grow, (tuple, list)) else (grow, grow, grow)
        self.mirror = mirror

    def quads(self, tw, th):
        """[(4 verts (px), 4 uvs (0..1))] exactly as ModelPart.Cube builds its polygons."""
        gx, gy, gz = self.grow
        x0, y0, z0 = self.x - gx, self.y - gy, self.z - gz
        x1, y1, z1 = self.x + self.w + gx, self.y + self.h + gy, self.z + self.d + gz
        if self.mirror:
            x0, x1 = x1, x0
        v7 = (x0, y0, z0); v = (x1, y0, z0); v1 = (x1, y1, z0); v2 = (x0, y1, z0)
        v3 = (x0, y0, z1); v4 = (x1, y0, z1); v5 = (x1, y1, z1); v6 = (x0, y1, z1)
        u, vv, W, H, D = self.u, self.v, self.w, self.h, self.d
        f4 = u; f5 = u + D; f6 = u + D + W; f7 = u + D + W + W; f8 = u + D + W + D; f9 = u + D + W + D + W
        f10 = vv; f11 = vv + D; f12 = vv + D + H
        polys = [
            ([v4, v3, v7, v], f5, f10, f6, f11),   # DOWN (visual top, minY)
            ([v1, v2, v6, v5], f6, f11, f7, f10),  # UP (visual bottom)
            ([v7, v3, v6, v2], f4, f11, f5, f12),  # WEST
            ([v, v7, v2, v1], f5, f11, f6, f12),   # NORTH (front)
            ([v4, v, v1, v5], f6, f11, f8, f12),   # EAST
            ([v3, v4, v5, v6], f8, f11, f9, f12),  # SOUTH (back)
        ]
        out = []
        for vs, ua, va, ub, vb in polys:
            uvs = [(ub / tw, va / th), (ua / tw, va / th), (ua / tw, vb / th), (ub / tw, vb / th)]
            if self.mirror:
                vs = vs[::-1]; uvs = uvs[::-1]
            out.append((vs, uvs))
        return out


class Part:
    def __init__(self, name, cubes=None, pose=(0, 0, 0), rot=(0, 0, 0)):
        self.name = name
        self.cubes = cubes or []
        self.x, self.y, self.z = pose
        self.xRot, self.yRot, self.zRot = rot
        self.children = {}
        self.visible = True
        self.meshes = []  # (fn(mat) -> emit) extra geometry in this part's space

    def child(self, name, cubes=None, pose=(0, 0, 0), rot=(0, 0, 0)):
        p = Part(name, cubes, pose, rot)
        self.children[name] = p
        return p

    def copy_pose(self, o):
        self.x, self.y, self.z, self.xRot, self.yRot, self.zRot = o.x, o.y, o.z, o.xRot, o.yRot, o.zRot

    def local(self):
        """4x4 matrix: translate(x,y,z) * rotationZYX(z, y, x) (px units)."""
        cx, sx = math.cos(self.xRot), math.sin(self.xRot)
        cy, sy = math.cos(self.yRot), math.sin(self.yRot)
        cz, sz = math.cos(self.zRot), math.sin(self.zRot)
        Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        M = np.eye(4)
        M[:3, :3] = Rz @ Ry @ Rx
        M[:3, 3] = (self.x, self.y, self.z)
        return M


def mat_translate(x, y, z):
    M = np.eye(4); M[:3, 3] = (x, y, z); return M


def mat_rot(axis, deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    M = np.eye(4)
    if axis == 'x':
        M[:3, :3] = [[1, 0, 0], [0, c, -s], [0, s, c]]
    elif axis == 'y':
        M[:3, :3] = [[c, 0, s], [0, 1, 0], [-s, 0, c]]
    else:
        M[:3, :3] = [[c, -s, 0], [s, c, 0], [0, 0, 1]]
    return M


def mat_scale(x, y, z):
    return np.diag([x, y, z, 1.0])


# ------------------------------------------------------------------------------------------------ scene (triangles)
class Scene:
    def __init__(self):
        self.tris = []  # (3x3 world pos, 3x2 uv, tex array, tint rgb, normal)

    def add_part(self, part, tex, tw, th, M=np.eye(4), tint=(1, 1, 1), only=None):
        if not part.visible:
            return
        M2 = M @ part.local()
        if only is None or part.name in only:
            for c in part.cubes:
                for vs, uvs in c.quads(tw, th):
                    P = np.array([list(p) + [1] for p in vs], float) @ M2.T
                    P = P[:, :3]
                    U = np.array(uvs, float)
                    self._quad(P, U, tex, tint)
        for fn in part.meshes:
            fn(self, M2)
        for ch in part.children.values():
            self.add_part(ch, tex, tw, th, M2, tint, None)

    def _quad(self, P, U, tex, tint):
        n = np.cross(P[1] - P[0], P[2] - P[0])
        if np.linalg.norm(n) < 1e-9:
            n = np.cross(P[2] - P[0], P[3] - P[0])
        ln = np.linalg.norm(n)
        if ln < 1e-12:
            return
        n = n / ln
        self.tris.append((P[[0, 1, 2]], U[[0, 1, 2]], tex, tint, n))
        self.tris.append((P[[0, 2, 3]], U[[0, 2, 3]], tex, tint, n))

    def add_tri(self, P, U, tex, color, n):
        self.tris.append((P, U, tex, color, n))


def load_fheq(path_or_bytes):
    data = open(path_or_bytes, 'rb').read() if isinstance(path_or_bytes, str) else path_or_bytes
    o = 0
    def ri():
        nonlocal o; v = struct.unpack('>i', data[o:o + 4])[0]; o += 4; return v
    def rf():
        nonlocal o; v = struct.unpack('>f', data[o:o + 4])[0]; o += 4; return v
    assert ri() == 1179141457 and ri() == 1
    parts = []
    for _ in range(ri()):
        pid, nv, nt = ri(), ri(), ri()
        V = np.zeros((nv, 8)); C = np.zeros(nv, np.int64)
        for i in range(nv):
            V[i] = [rf() for _ in range(8)]
            C[i] = ri()
        I = np.array([ri() for _ in range(nt * 3)]).reshape(-1, 3)
        parts.append((pid, V, C, I))
    return parts


def mesh_emitter(parts, tex, M_local):
    """fn(scene, M) adding the .fheq triangles (block units) transformed by M @ M_local (M_local maps block units->px)."""
    def fn(scene, M):
        MM = M @ M_local
        for pid, V, C, I in parts:
            P = np.c_[V[:, :3], np.ones(len(V))] @ MM.T
            for t in I:
                p = P[t, :3]
                n = np.cross(p[1] - p[0], p[2] - p[0]); ln = np.linalg.norm(n)
                if ln < 1e-12:
                    continue
                c = C[t[0]]
                col = (((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255)
                scene.add_tri(p, V[t, 6:8], tex, col, n / ln)
    return fn


# ------------------------------------------------------------------------------------------------ raster
LIGHT0 = np.array([0.2, 1.0, -0.7]); LIGHT0 /= np.linalg.norm(LIGHT0)
LIGHT1 = np.array([-0.2, 1.0, 0.7]); LIGHT1 /= np.linalg.norm(LIGHT1)


def render(scene, view_M, size=(300, 480), scale=10.0, center=(0, 0), ss=2, bg=(58, 62, 66), persp=0.0):
    """view_M: 4x4 world(px)->camera (x right, y up, z into the screen). Orthographic (persp=0) or weak perspective."""
    W, H = size[0] * ss, size[1] * ss
    zbuf = np.full((H, W), np.inf)
    img = np.zeros((H, W, 3)); img[:] = np.array(bg) / 255.0
    sc = scale * ss
    for P, U, tex, tint, n in scene.tris:
        Q = np.c_[P, np.ones(3)] @ view_M.T
        z = Q[:, 2]
        f = 1.0 / (1.0 + persp * z) if persp else np.ones(3)
        sx = W / 2 + (Q[:, 0] - center[0]) * sc * f
        sy = H / 2 - (Q[:, 1] - center[1]) * sc * f
        x0 = max(int(math.floor(sx.min())), 0); x1 = min(int(math.ceil(sx.max())), W - 1)
        y0 = max(int(math.floor(sy.min())), 0); y1 = min(int(math.ceil(sy.max())), H - 1)
        if x1 < x0 or y1 < y0:
            continue
        area = (sx[1] - sx[0]) * (sy[2] - sy[0]) - (sx[2] - sx[0]) * (sy[1] - sy[0])
        if abs(area) < 1e-9:
            continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1]
        px, py = xs + 0.5, ys + 0.5
        w0 = ((sx[1] - px) * (sy[2] - py) - (sx[2] - px) * (sy[1] - py)) / area
        w1 = ((sx[2] - px) * (sy[0] - py) - (sx[0] - px) * (sy[2] - py)) / area
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        zz = w0 * z[0] + w1 * z[1] + w2 * z[2]
        m &= zz < zbuf[y0:y1 + 1, x0:x1 + 1]
        if not m.any():
            continue
        uu = w0 * U[0, 0] + w1 * U[1, 0] + w2 * U[2, 0]
        vv = w0 * U[0, 1] + w1 * U[1, 1] + w2 * U[2, 1]
        th, tw = tex.shape[:2]
        ti = np.clip((vv * th).astype(int), 0, th - 1)
        tj = np.clip((uu * tw).astype(int), 0, tw - 1)
        texel = tex[ti, tj]
        m &= texel[..., 3] >= 0.1
        if not m.any():
            continue
        # vanilla-like entity light, two-sided (no-cull): flip the normal toward the viewer
        nn = n.copy()
        vn = (view_M[:3, :3] @ nn)
        if vn[2] > 0:
            nn = -nn
        l = 0.4 + 0.6 * (max(0, nn @ LIGHT0) + max(0, nn @ LIGHT1))
        l = min(1.0, l)
        col = texel[..., :3] * np.array(tint) * l
        sub = img[y0:y1 + 1, x0:x1 + 1]
        sub[m] = col[m]
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]
        zb[m] = zz[m]
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), 'RGB')
    if ss > 1:
        out = out.resize(size, Image.LANCZOS)
    return out


def camera(yaw_deg, pitch_deg=8.0, target=(0, 0, 0)):
    """Camera orbiting the world origin: yaw 0 = looking at the model's front (from -z), 90 = from the model's right."""
    M = mat_translate(-target[0], -target[1], -target[2])
    M = mat_rot('y', yaw_deg) @ M
    M = mat_rot('x', -pitch_deg) @ M
    return M


# ------------------------------------------------------------------------------------------------ humanoid
def humanoid_skin_parts(slim):
    """PlayerModel base + overlay boxes (64x64 skin)."""
    P = {}
    P['head'] = Part('head', [Cube(0, 0, -4, -8, -4, 8, 8, 8), Cube(32, 0, -4, -8, -4, 8, 8, 8, 0.5)])
    P['body'] = Part('body', [Cube(16, 16, -4, 0, -2, 8, 12, 4), Cube(16, 32, -4, 0, -2, 8, 12, 4, 0.25)])
    if slim:
        P['right_arm'] = Part('right_arm', [Cube(40, 16, -2, -2, -2, 3, 12, 4), Cube(40, 32, -2, -2, -2, 3, 12, 4, 0.25)], (-5, 2.5, 0))
        P['left_arm'] = Part('left_arm', [Cube(32, 48, -1, -2, -2, 3, 12, 4), Cube(48, 48, -1, -2, -2, 3, 12, 4, 0.25)], (5, 2.5, 0))
    else:
        P['right_arm'] = Part('right_arm', [Cube(40, 16, -3, -2, -2, 4, 12, 4), Cube(40, 32, -3, -2, -2, 4, 12, 4, 0.25)], (-5, 2, 0))
        P['left_arm'] = Part('left_arm', [Cube(32, 48, -1, -2, -2, 4, 12, 4), Cube(48, 48, -1, -2, -2, 4, 12, 4, 0.25)], (5, 2, 0))
    P['right_leg'] = Part('right_leg', [Cube(0, 16, -2, 0, -2, 4, 12, 4), Cube(0, 32, -2, 0, -2, 4, 12, 4, 0.25)], (-1.9, 12, 0))
    P['left_leg'] = Part('left_leg', [Cube(16, 48, -2, 0, -2, 4, 12, 4), Cube(0, 48, -2, 0, -2, 4, 12, 4, 0.25)], (1.9, 12, 0))
    return P


class Pose:
    """Inputs to HumanoidModel.setupAnim + entity-level rotation."""
    def __init__(self, name, limb=0.0, amount=0.0, age=0.0, yaw=0.0, pitch=0.0, crouch=False, riding=False,
                 swim=0.0, arms=('empty', 'empty'), entity='stand'):
        self.name, self.limb, self.amount, self.age, self.yaw, self.pitch = name, limb, amount, age, yaw, pitch
        self.crouch, self.riding, self.swim, self.arms, self.entity = crouch, riding, swim, arms, entity


POSES = {
    'idle': Pose('idle'),
    'walk_a': Pose('walk_a', limb=math.pi / 0.6662 * 0.0, amount=0.75),
    'walk_b': Pose('walk_b', limb=math.pi / 0.6662, amount=0.75),
    'sprint': Pose('sprint', limb=0.0, amount=1.0, pitch=-5),
    'sprint_b': Pose('sprint_b', limb=math.pi / 0.6662, amount=1.0),
    'sneak': Pose('sneak', crouch=True),
    'sneak_walk': Pose('sneak_walk', crouch=True, limb=0.0, amount=0.6),
    'swim': Pose('swim', swim=1.0, limb=1.5, amount=0.6, entity='swim'),
    'prone': Pose('prone', swim=1.0, limb=4.0, amount=0.3, entity='crawl'),
    'riding': Pose('riding', riding=True, entity='ride'),
    'sleeping': Pose('sleeping', entity='sleep'),
    'bow': Pose('bow', arms=('bow', 'bow'), pitch=-4),
    'rifle': Pose('rifle', arms=('crossbow', 'crossbow'), pitch=-2),
    'sneak_bow': Pose('sneak_bow', crouch=True, arms=('bow', 'bow')),
}


def setup_anim(P, pose, slim=False):
    """Vanilla HumanoidModel.setupAnim (1.21.1) on parts dict (subset: head body arms legs)."""
    h, b = P['head'], P['body']
    ra, la, rl, ll = P['right_arm'], P['left_arm'], P['right_leg'], P['left_leg']
    L, A, age = pose.limb, pose.amount, pose.age
    swimming = pose.swim > 0
    h.yRot = math.radians(pose.yaw)
    h.xRot = (-math.pi / 4) if pose.entity == 'swim' else math.radians(pose.pitch)
    b.yRot = 0
    ra.z = 0; ra.x = -5; la.z = 0; la.x = 5
    ra.xRot = math.cos(L * 0.6662 + math.pi) * 2.0 * A * 0.5
    la.xRot = math.cos(L * 0.6662) * 2.0 * A * 0.5
    ra.zRot = 0; la.zRot = 0
    rl.xRot = math.cos(L * 0.6662) * 1.4 * A
    ll.xRot = math.cos(L * 0.6662 + math.pi) * 1.4 * A
    rl.yRot = 0.005; ll.yRot = -0.005; rl.zRot = 0.005; ll.zRot = -0.005
    if pose.riding:
        ra.xRot += -math.pi / 5; la.xRot += -math.pi / 5
        rl.xRot = -1.4137167; rl.yRot = math.pi / 10; rl.zRot = 0.07853982
        ll.xRot = -1.4137167; ll.yRot = -math.pi / 10; ll.zRot = -0.07853982
    ra.yRot = 0; la.yRot = 0
    rp, lp = pose.arms
    if rp == 'bow':
        ra.yRot = -0.1 + h.yRot; la.yRot = 0.1 + h.yRot + 0.4
        ra.xRot = -math.pi / 2 + h.xRot; la.xRot = -math.pi / 2 + h.xRot
    elif rp == 'crossbow':
        # AnimationUtils.animateCrossbowHold(right, left, head, rightHanded=true)
        ra.yRot = -0.3 + h.yRot; la.yRot = 0.6 + h.yRot
        ra.xRot = -math.pi / 2 + h.xRot + 0.1; la.xRot = -1.5 + h.xRot
    if pose.crouch:
        b.xRot = 0.5; ra.xRot += 0.4; la.xRot += 0.4
        rl.z = 4.0; ll.z = 4.0; rl.y = 12.2; ll.y = 12.2
        h.y = 4.2; b.y = 3.2; la.y = 5.2; ra.y = 5.2
    else:
        b.xRot = 0; rl.z = 0.1; ll.z = 0.1; rl.y = 12; ll.y = 12
        h.y = 0; b.y = 0; la.y = 2; ra.y = 2
    if rp not in ('bow', 'crossbow'):
        ra.zRot += math.cos(age * 0.09) * 0.05 + 0.05; ra.xRot += math.sin(age * 0.067) * 0.05
        la.zRot += -(math.cos(age * 0.09) * 0.05 + 0.05); la.xRot += -(math.sin(age * 0.067) * 0.05)
    if swimming:
        s = pose.swim
        f5 = L % 26.0
        def rl_(a, bb, t):
            return a + (bb - a) * t
        q = quad(f5) / quad(14.0)
        if f5 < 14.0:
            tgt = (0.0, math.pi, math.pi + 1.8707964 * q, 0.0, math.pi, math.pi - 1.8707964 * q)
        elif f5 < 22.0:
            f6 = (f5 - 14.0) / 8.0
            tgt = (math.pi / 2 * f6, math.pi, 5.012389 - 1.8707964 * f6, math.pi / 2 * f6, math.pi, 1.2707963 + 1.8707964 * f6)
        else:
            f3 = (f5 - 22.0) / 4.0
            tgt = (math.pi / 2 - math.pi / 2 * f3, math.pi, math.pi, math.pi / 2 - math.pi / 2 * f3, math.pi, math.pi)
        la.xRot = rl_(la.xRot, tgt[0], s); la.yRot = rl_(la.yRot, tgt[1], s); la.zRot = rl_(la.zRot, tgt[2], s)
        ra.xRot = rl_(ra.xRot, tgt[3], s); ra.yRot = rl_(ra.yRot, tgt[4], s); ra.zRot = rl_(ra.zRot, tgt[5], s)
        rl.xRot = rl_(rl.xRot, 0.3 * math.cos(L * 0.33333334 + math.pi), s)
        ll.xRot = rl_(ll.xRot, 0.3 * math.cos(L * 0.33333334), s)


def quad(x):
    return -0.065 * x * x + x


def entity_matrix(pose):
    """World(px, y up, model front toward -z) <- model space. Model y-down -> world y-up rotation about z by 180 (scale -1,-1,1)."""
    M = mat_translate(0, 24.0, 0) @ mat_scale(-1, -1, 1)
    if pose.entity in ('swim', 'crawl'):
        # PlayerRenderer.setupRotations: X rotation -90 (crawl) then translate(0,-1,0.3) while visually swimming.
        M = mat_translate(0, 4.0, 0) @ mat_rot('x', -90) @ mat_translate(0, -24.0, 0) @ M  #crawl
    elif pose.entity == 'sleep':
        M = mat_translate(0, 3.0, 0) @ mat_rot('x', 90) @ mat_translate(0, -24.0, 0) @ M
    elif pose.entity == 'ride':
        M = mat_translate(0, -4.0, 0) @ M
    return M


def posed_humanoid(pose, slim=False):
    P = humanoid_skin_parts(slim)
    setup_anim(P, pose, slim)
    return P
