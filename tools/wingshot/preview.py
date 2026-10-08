"""[wingshot] Software preview of the posed Ultra birds (body + feathered wings) dumped by PoseHarness.

usage: python3 preview.py <poses dir> <out.png> [species] [poses...]
Renders each pose from three cameras (3/4 above, side, below) with the real coat texture, the wing atlas
(alpha-cutout, back faces culled like the game's render type) and simple sun + sky lighting.
"""
import struct, sys, os, zipfile, io
import numpy as np
from PIL import Image, ImageDraw

JAR = '/home/claude/fh/merged62g8.jar'
REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))


def load_tex(path_in_jar=None, file=None):
    if file:
        im = Image.open(file).convert('RGBA')
    else:
        with zipfile.ZipFile(JAR) as z:
            im = Image.open(io.BytesIO(z.read(path_in_jar))).convert('RGBA')
    return np.asarray(im).astype(np.float32) / 255.0


def read_pose(fn):
    b = open(fn, 'rb').read()
    nb, nw = struct.unpack_from('<ii', b, 0)
    bank, pitch, limp, tumble, down, rest = struct.unpack_from('<6f', b, 8)
    o = 32
    body = np.frombuffer(b, '<f4', nb * 8, o).reshape(-1, 8)
    o += nb * 8 * 4
    wing = np.frombuffer(b, '<f4', nw * 8, o).reshape(-1, 8)
    return body, wing, dict(bank=bank, pitch=pitch, limp=limp, tumble=tumble, down=down, rest=rest)


def rot(axis, a):
    c, s = np.cos(a), np.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def attitude(info, h):
    """the BirdRender transform in model space (model faces -z; BirdRender's local +z forward = model -z)"""
    if info['limp'] > 0.01:
        if info['down'] > 0.5:
            R = rot('z', -info['rest']) @ rot('x', -0.08)
        else:
            R = rot('z', -info['tumble']) @ rot('x', -(info['tumble'] * 0.6 + 0.4))
    else:
        # bank: right wing (+x) down for + ; pitch: nose (-z) up for +
        R = rot('z', -info['bank']) @ rot('x', info['pitch'])
    return R


def raster(img, zbuf, P, UV, N, tex, cull, light_dir, alpha_test=True, base=None):
    H, W = zbuf.shape
    th, tw = tex.shape[:2]
    for t in range(0, len(P), 3):
        p0, p1, p2 = P[t], P[t + 1], P[t + 2]
        # screen-space area (y down): counter-clockwise in view = front
        area = (p1[0] - p0[0]) * (p2[1] - p0[1]) - (p2[0] - p0[0]) * (p1[1] - p0[1])
        if cull and area >= 0:
            continue
        if abs(area) < 1e-9:
            continue
        xs = [p0[0], p1[0], p2[0]]
        ys = [p0[1], p1[1], p2[1]]
        x0, x1 = max(int(min(xs)), 0), min(int(max(xs)) + 1, W - 1)
        y0, y1 = max(int(min(ys)), 0), min(int(max(ys)) + 1, H - 1)
        if x0 > x1 or y0 > y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        w0 = ((p1[0] - gx) * (p2[1] - gy) - (p2[0] - gx) * (p1[1] - gy)) / area
        w1 = ((p2[0] - gx) * (p0[1] - gy) - (p0[0] - gx) * (p2[1] - gy)) / area
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any():
            continue
        z = w0 * p0[2] + w1 * p1[2] + w2 * p2[2]
        zb = zbuf[y0:y1 + 1, x0:x1 + 1]
        m &= z < zb
        if not m.any():
            continue
        u = w0 * UV[t][0] + w1 * UV[t + 1][0] + w2 * UV[t + 2][0]
        v = w0 * UV[t][1] + w1 * UV[t + 1][1] + w2 * UV[t + 2][1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((v * th).astype(int), 0, th - 1)
        c = tex[ty, tx]
        if alpha_test:
            m &= c[..., 3] > 0.1
        if not m.any():
            continue
        n = N[t] * w0[..., None] + N[t + 1] * w1[..., None] + N[t + 2] * w2[..., None]
        n /= np.linalg.norm(n, axis=-1, keepdims=True) + 1e-9
        # Minecraft-ish entity lighting: two lights + ambient
        l = 0.45 + 0.55 * np.clip(n @ light_dir, 0, 1) * 0.9 + 0.12 * np.clip(n[..., 1], 0, 1)
        col = c[..., :3] * np.clip(l, 0, 1.15)[..., None]
        sub = img[y0:y1 + 1, x0:x1 + 1]
        sub[m] = col[m]
        zb[m] = z[m]


def render(body, wing, info, body_tex, wing_tex, view, size=360):
    img = np.zeros((size, size, 3), np.float32)
    img[:] = (0.62, 0.72, 0.82)
    zbuf = np.full((size, size), 1e9, np.float32)
    h = 0.19
    R = attitude(info, h)
    piv = np.array([0, h, 0])

    def tf(p):
        return (p - piv) @ R.T + piv

    yaw, pitch_v = view
    V = rot('x', pitch_v) @ rot('y', yaw)
    scale = size / 1.05
    center = np.array([0, 0.2, 0])

    def proj(p):
        q = (p - center) @ V.T
        return np.stack([size / 2 + q[:, 0] * scale, size / 2 - q[:, 1] * scale, -q[:, 2]], 1)

    light = np.array([0.3, 0.9, -0.3]); light /= np.linalg.norm(light)
    light_v = light @ V.T  # normals are transformed to view below
    for P0, tex, cull in ((body, body_tex, False), (wing, wing_tex, True)):
        if len(P0) == 0:
            continue
        pos = tf(P0[:, :3])
        nrm = P0[:, 3:6] @ R.T
        sp = proj(pos)
        nv = nrm @ V.T
        raster(img, zbuf, sp, P0[:, 6:8], nv, tex, cull, light_v)
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


def main():
    d, out = sys.argv[1], sys.argv[2]
    species = sys.argv[3] if len(sys.argv) > 3 else 'duck'
    poses = sys.argv[4:] or ['folded', 'stroke_top', 'stroke_mid_down', 'stroke_bottom', 'stroke_mid_up', 'takeoff', 'glide_bank', 'set_circle',
                             'landing', 'drum', 'dead_fall', 'dead_down', 'dead_water']
    body_tex = load_tex('assets/frontierhunts/textures/entity/wildlife/real/%s.png' % species)
    wing_file = os.path.join(REPO, 'patch/assets/frontierhunts/textures/entity/wildlife/wings/%s.png' % species)
    if os.path.exists(wing_file):
        wing_tex = load_tex(file=wing_file)
    else:
        wing_tex = np.ones((256, 256, 4), np.float32)
        wing_tex[..., :3] = (0.9, 0.4, 0.2)
    views = [(0.7, 0.45), (1.5708, 0.05), (2.6, -0.55)]
    rows = []
    names = []
    for p in poses:
        fn = os.path.join(d, '%s_%s.bin' % (species, p))
        if not os.path.exists(fn):
            continue
        body, wing, info = read_pose(fn)
        rows.append(np.concatenate([render(body, wing, info, body_tex, wing_tex, v) for v in views], 1))
        names.append(p)
    sheet = np.concatenate(rows, 0)
    im = Image.fromarray(sheet)
    dr = ImageDraw.Draw(im)
    for i, n in enumerate(names):
        dr.text((6, i * 360 + 4), n, fill=(0, 0, 0))
    im.save(out)


if __name__ == '__main__':
    main()
