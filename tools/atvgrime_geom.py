"""ATV geometry for the atvgrime art generator: texel -> model-space position/normal maps for the blocky AtvModel
(128x128 atv.png layout) and the realistic FHVM mesh (atv_real.png / atv_wheel.png atlases), plus a tiny numpy
software rasteriser used for preview renders.

Model space is Minecraft model space: pixels, +y DOWN (ground at y=24), front of the ATV is -z.
"""
import math, struct, zipfile
import numpy as np

JAR = '/home/claude/fh/merged62g8.jar'

# ------------------------------------------------------------------ blocky model (transcribed from AtvModel.createLayer)
BODY = [
    (0, 0, -5.0, 16.0, -12.0, 10.0, 3.0, 24.0), (69, 0, -6.0, 11.0, -8.0, 12.0, 5.0, 17.0), (0, 89, -4.5, 9.5, -8.0, 9.0, 2.0, 7.0),
    (53, 28, -3.5, 8.5, -1.0, 7.0, 3.0, 11.0), (33, 89, -6.0, 12.0, -18.0, 12.0, 3.0, 6.0), (51, 99, -5.0, 15.0, -18.2, 10.0, 2.0, 1.0),
    (74, 99, -5.5, 12.4, -18.4, 3.0, 1.6, 1.0), (83, 99, 2.5, 12.4, -18.4, 3.0, 1.6, 1.0), (90, 28, -11.0, 13.0, -16.0, 6.0, 1.5, 12.0),
    (0, 48, 5.0, 13.0, -16.0, 6.0, 1.5, 12.0), (37, 48, -11.0, 13.0, 4.0, 6.0, 1.5, 12.0), (74, 48, 5.0, 13.0, 4.0, 6.0, 1.5, 12.0),
    (92, 99, -11.0, 14.5, -16.0, 6.0, 2.0, 1.0), (107, 99, 5.0, 14.5, -16.0, 6.0, 2.0, 1.0), (0, 108, -11.0, 14.5, 15.0, 6.0, 2.0, 1.0),
    (15, 108, 5.0, 14.5, 15.0, 6.0, 2.0, 1.0), (51, 63, -6.0, 11.5, 9.0, 12.0, 3.5, 7.0), (80, 108, -5.5, 12.2, 16.1, 2.0, 1.2, 0.6),
    (87, 108, 3.5, 12.2, 16.1, 2.0, 1.2, 0.6), (70, 89, -8.0, 11.0, -17.0, 16.0, 1.0, 8.0), (0, 77, -8.0, 10.5, 7.0, 16.0, 1.0, 10.0),
    (0, 112, -8.0, 10.0, -17.0, 16.0, 1.0, 1.0), (35, 112, -8.0, 9.5, 16.0, 16.0, 1.0, 1.0), (53, 77, -10.0, 17.0, -5.0, 4.0, 1.0, 10.0),
    (82, 77, 6.0, 17.0, -5.0, 4.0, 1.0, 10.0), (19, 99, 4.0, 13.5, 14.0, 2.0, 2.0, 4.0), (0, 28, -4.0, 19.0, -10.0, 8.0, 0.5, 18.0),
]
BARS = [
    (32, 99, -0.5, -3.0, -0.5, 1.0, 3.5, 1.0), (30, 108, -7.0, -3.6, -0.6, 14.0, 1.1, 1.1), (62, 108, -7.6, -3.8, -0.8, 2.4, 1.5, 1.5),
    (71, 108, 5.2, -3.8, -0.8, 2.4, 1.5, 1.5), (37, 99, -2.2, -4.2, -1.9, 4.4, 1.6, 1.8), (70, 112, -1.4, -4.0, -2.1, 2.8, 1.1, 0.3),
]
HUB = (0, 99, -2.3, -2.0, -2.0, 4.6, 4.0, 4.0)
SLABS = [((111, 48, -2.0, -4.5, -1.889, 4.0, 9.0, 3.778), 0.0), ((0, 63, -1.96, -4.5, -1.889, 3.92, 9.0, 3.778), 0.785),
         ((17, 63, -1.92, -4.5, -1.889, 3.84, 9.0, 3.778), 1.571), ((34, 63, -1.88, -4.5, -1.889, 3.76, 9.0, 3.778), 2.356)]
BLOCKY_WHEELS = [(-10.0, 19.5, -10.0), (10.0, 19.5, -10.0), (-10.0, 19.5, 10.0), (10.0, 19.5, 10.0)]
WHEEL_R_BLOCKY = 4.5


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def box_faces(box):
    """Yields (name, verts[4] (model coords), u1, v1, u2, v2) exactly like ModelPart.Cube/Polygon."""
    tu, tv, x0, y0, z0, dx, dy, dz = box
    x1, y1, z1 = x0 + dx, y0 + dy, z0 + dz
    V = [np.array(p, float) for p in [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]]
    f4 = tu; f5 = tu + dz; f6 = tu + dz + dx; f7 = tu + dz + dx + dx; f8 = tu + dz + dx + dz; f9 = tu + dz + dx + dz + dx
    f10 = tv; f11 = tv + dz; f12 = tv + dz + dy
    yield 'down', [V[5], V[4], V[0], V[1]], f5, f10, f6, f11
    yield 'up', [V[2], V[3], V[7], V[6]], f6, f11, f7, f10
    yield 'west', [V[0], V[4], V[7], V[3]], f4, f11, f5, f12
    yield 'north', [V[1], V[0], V[3], V[2]], f5, f11, f6, f12
    yield 'east', [V[5], V[1], V[2], V[6]], f6, f11, f8, f12
    yield 'south', [V[4], V[5], V[6], V[7]], f8, f11, f9, f12


def blocky_texel_map(size=128):
    """Per texel of the 128x128 layout: model position (rest pose), normal, kind (0 none, 1 body, 2 bars, 3 hub, 4 tire slab),
    wheel-local position (for kinds 3/4)."""
    pos = np.zeros((size, size, 3)); nrm = np.zeros((size, size, 3)); kind = np.zeros((size, size), int)
    loc = np.zeros((size, size, 3))
    quads = []  # for preview rendering: (verts[4] model, uvs[4] px, kind)

    def emit(box, xf, k, local_xf=None):
        for name, verts, u1, v1, u2, v2 in box_faces(box):
            W = [xf(v) for v in verts]
            L = [local_xf(v) for v in verts] if local_xf else W
            uvs = [(u2, v1), (u1, v1), (u1, v2), (u2, v2)]
            quads.append((np.array(W), np.array(uvs), k))
            n = np.cross(W[1] - W[0], W[2] - W[0]) if False else None
            # outward normal from the face direction
            e1 = W[0] - W[1]; e2 = W[2] - W[1]
            n = np.cross(e2, e1)
            ln = np.linalg.norm(n)
            if ln < 1e-9:
                continue
            n = n / ln
            ua, ub = sorted((u1, u2)); va, vb = sorted((v1, v2))
            for ty in range(int(math.floor(va)), int(math.ceil(vb))):
                for tx in range(int(math.floor(ua)), int(math.ceil(ub))):
                    cu, cv = tx + 0.5, ty + 0.5
                    if not (ua <= cu < ub and va <= cv < vb) or not (0 <= tx < size and 0 <= ty < size):
                        continue
                    s = (cu - u1) / (u2 - u1) if u2 != u1 else 0.0
                    t = (cv - v1) / (v2 - v1) if v2 != v1 else 0.0
                    p = W[1] + s * (W[0] - W[1]) + t * (W[2] - W[1])
                    pl = L[1] + s * (L[0] - L[1]) + t * (L[2] - L[1])
                    pos[ty, tx] = p; nrm[ty, tx] = n; kind[ty, tx] = k; loc[ty, tx] = pl

    for b in BODY:
        emit(b, lambda v: v, 1)
    R = rot_x(-0.35); off = np.array([0.0, 10.0, -7.0])
    for b in BARS:
        emit(b, lambda v, R=R: R @ v + off, 2)
    wc = np.array(BLOCKY_WHEELS[0])  # one representative wheel for the shared UVs (front left)
    emit(HUB, lambda v: v + wc, 3, lambda v: v)
    for b, a in SLABS:
        Rs = rot_x(a)
        emit(b, lambda v, Rs=Rs: Rs @ v + wc, 4, lambda v, Rs=Rs: Rs @ v)
    return pos, nrm, kind, loc, quads


def blocky_all_quads():
    """All quads of the blocky model in rest pose (every wheel), for previews."""
    quads = []
    def emit(box, xf, k):
        for name, verts, u1, v1, u2, v2 in box_faces(box):
            quads.append((np.array([xf(v) for v in verts]), np.array([(u2, v1), (u1, v1), (u1, v2), (u2, v2)]), k))
    for b in BODY:
        emit(b, lambda v: v, 1)
    R = rot_x(-0.35); off = np.array([0.0, 10.0, -7.0])
    for b in BARS:
        emit(b, lambda v, R=R: R @ v + off, 2)
    for wc in BLOCKY_WHEELS:
        wc = np.array(wc)
        emit(HUB, lambda v, wc=wc: v + wc, 3)
        for b, a in SLABS:
            Rs = rot_x(a)
            emit(b, lambda v, Rs=Rs, wc=wc: Rs @ v + wc, 4)
    return quads


# ------------------------------------------------------------------ realistic mesh
def load_fhvm():
    d = zipfile.ZipFile(JAR).read('assets/frontierhunts/models/entity/atv_real.fhvm')
    ver, n = struct.unpack('>ii', d[4:12]); o = 12
    parts = []
    for p in range(n):
        piv = np.array(struct.unpack('>3f', d[o:o + 12])); o += 12
        tex = struct.unpack('>i', d[o:o + 4])[0] if ver >= 2 else 0; o += 4 if ver >= 2 else 0
        nt = struct.unpack('>i', d[o:o + 4])[0]; o += 4
        a = np.frombuffer(d[o:o + nt * 96], dtype='>f4').astype(np.float64).reshape(nt, 3, 8); o += nt * 96
        parts.append(dict(pivot=piv, tex=tex, tris=a))
    return parts


def raster_uv(tris_uv, size, attrs):
    """Rasterise triangles (n,3,2 in 0..1 uv) into a size x size grid. attrs: list of (n,3,k) per-vertex attributes.
    Returns tri index map (-1 = empty) and interpolated attribute maps. Texel centres; v grows downwards (image rows)."""
    idx = -np.ones((size, size), np.int32)
    outs = [np.zeros((size, size, a.shape[2])) for a in attrs]
    P = tris_uv * size
    for i in range(len(P)):
        (x0, y0), (x1, y1), (x2, y2) = P[i]
        minx = max(int(math.floor(min(x0, x1, x2))), 0); maxx = min(int(math.ceil(max(x0, x1, x2))), size - 1)
        miny = max(int(math.floor(min(y0, y1, y2))), 0); maxy = min(int(math.ceil(max(y0, y1, y2))), size - 1)
        if maxx < minx or maxy < miny:
            continue
        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-12:
            continue
        xs = np.arange(minx, maxx + 1) + 0.5; ys = np.arange(miny, maxy + 1) + 0.5
        X, Y = np.meshgrid(xs, ys)
        l0 = ((y1 - y2) * (X - x2) + (x2 - x1) * (Y - y2)) / den
        l1 = ((y2 - y0) * (X - x2) + (x0 - x2) * (Y - y2)) / den
        l2 = 1 - l0 - l1
        m = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
        if not m.any():
            # sub-texel triangle: claim the texel under its centroid
            cx = int(min(max((x0 + x1 + x2) / 3, 0), size - 1)); cy = int(min(max((y0 + y1 + y2) / 3, 0), size - 1))
            if idx[cy, cx] < 0:
                idx[cy, cx] = i
                for o, a in zip(outs, attrs):
                    o[cy, cx] = a[i].mean(0)
            continue
        yy, xx = np.nonzero(m)
        ty = yy + miny; tx = xx + minx
        idx[ty, tx] = i
        L = np.stack([l0[m], l1[m], l2[m]], 1)
        for o, a in zip(outs, attrs):
            o[ty, tx] = L @ a[i]
    return idx, outs


def dilate_index(idx, iters=3):
    """Grow islands into empty texels (padding against seams); returns the filled index map."""
    out = idx.copy()
    for _ in range(iters):
        empty = out < 0
        if not empty.any():
            break
        cand = out.copy()
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            sh = np.roll(np.roll(out, dy, 0), dx, 1)
            fill = empty & (cand < 0) & (sh >= 0)
            cand[fill] = sh[fill]
        out = cand
    return out


# ------------------------------------------------------------------ preview rasteriser
def look_matrix(yaw_deg, pitch_deg):
    y = math.radians(yaw_deg); p = math.radians(pitch_deg)
    Ry = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    Rx = np.array([[1, 0, 0], [0, math.cos(p), -math.sin(p)], [0, math.sin(p), math.cos(p)]])
    return Rx @ Ry


def render(tris, W=640, H=480, yaw=35, pitch=22, scale=12.0, light=(-0.4, 0.8, -0.45), bg=(150, 165, 180), center=(0.0, -15.0, 0.0)):
    """tris: list of (P (3,3) model coords, sampler(l0,l1,l2)->(n,4) rgba, N (3,3) model normals).
    Model y is down; we flip to y-up. Orthographic. Alpha < 0.1 discarded (cutout); later layers drawn with equal-depth win."""
    img = np.zeros((H, W, 3)); img[:] = np.array(bg) / 255.0
    zb = np.full((H, W), -1e9)
    M = look_matrix(yaw, pitch)
    Lv = np.array(light, float); Lv /= np.linalg.norm(Lv)
    for P, sampler, N in tris:
        Q = (P * np.array([1, -1, 1]) - np.array(center)) @ M.T
        Nw = (N * np.array([1, -1, 1]))
        sx = Q[:, 0] * scale + W / 2; sy = -Q[:, 1] * scale + H * 0.5; z = Q[:, 2]
        minx = max(int(sx.min()), 0); maxx = min(int(sx.max()) + 1, W - 1)
        miny = max(int(sy.min()), 0); maxy = min(int(sy.max()) + 1, H - 1)
        if maxx < minx or maxy < miny:
            continue
        den = (sy[1] - sy[2]) * (sx[0] - sx[2]) + (sx[2] - sx[1]) * (sy[0] - sy[2])
        if abs(den) < 1e-9:
            continue
        xs = np.arange(minx, maxx + 1) + 0.5; ys = np.arange(miny, maxy + 1) + 0.5
        X, Y = np.meshgrid(xs, ys)
        l0 = ((sy[1] - sy[2]) * (X - sx[2]) + (sx[2] - sx[1]) * (Y - sy[2])) / den
        l1 = ((sy[2] - sy[0]) * (X - sx[2]) + (sx[0] - sx[2]) * (Y - sy[2])) / den
        l2 = 1 - l0 - l1
        m = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if not m.any():
            continue
        zz = l0 * z[0] + l1 * z[1] + l2 * z[2]
        yy, xx = np.nonzero(m)
        ty = yy + miny; tx = xx + minx
        zv = zz[m] - 1e-4  # camera looks down -z after transform? use larger z = closer
        rgba = sampler(l0[m], l1[m], l2[m])
        keep = (rgba[:, 3] >= 0.1) & (zv + 2e-3 >= zb[ty, tx])
        if not keep.any():
            continue
        n = Nw.mean(0); n /= max(np.linalg.norm(n), 1e-9)
        sh = 0.45 + 0.55 * max(abs(np.dot(n, Lv)), 0.0) if True else 1.0
        ty, tx = ty[keep], tx[keep]
        col = rgba[keep, :3]; a = rgba[keep, 3:4]
        a = np.where(a > 0.99, 1.0, a)
        img[ty, tx] = img[ty, tx] * (1 - a) + col * sh * a
        zb[ty, tx] = np.maximum(zb[ty, tx], zv[keep])
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)
