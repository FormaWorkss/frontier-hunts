"""[hound4] In-game-like views of the Ultra hound: the REAL HoundRig (rigview.sh) poses the REAL .fhsk with the real
coat, drawn the way vanilla Minecraft lights an entity (shaders off):
  lightAccum = min(1, 0.4 + 0.6 * (max(0, n.L0) + max(0, n.L1))),  L0 = (0.2, 1, -0.7), L1 = (-0.2, 1, 0.7) (world space)
  colour = texture * lightAccum * lightmap (full daylight = 1)
through a perspective camera with Minecraft's default 70 deg vertical FOV, from a player's eye (y = 1.62) at 2-6 blocks
(first person, looking at the dog) and from the default third-person camera (4 blocks behind the player), over a
grass-block ground with the vanilla round entity shadow. Textures are sampled bilinear from a box-filtered mip level
chosen per triangle (like the game's trilinear HoundTexture), at 2x supersampling.

  python3 ingame.py [ultra|bal] [redbone|bluetick] [out.png] [poses]
"""
import sys, os, math
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rigview

L0 = np.array([0.2, 1.0, -0.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-0.2, 1.0, 0.7]); L1 /= np.linalg.norm(L1)


def mips(tex):
    out = [tex]
    while out[-1].shape[0] > 8:
        t = out[-1]
        out.append(0.25 * (t[0::2, 0::2] + t[1::2, 0::2] + t[0::2, 1::2] + t[1::2, 1::2]))
    return out


def bilinear(t, u, v):
    h, w = t.shape[:2]
    x = u * w - 0.5; y = v * h - 0.5
    x0 = np.floor(x).astype(int); y0 = np.floor(y).astype(int); fx = (x - x0)[..., None]; fy = (y - y0)[..., None]
    x0c = np.clip(x0, 0, w - 1); x1c = np.clip(x0 + 1, 0, w - 1); y0c = np.clip(y0, 0, h - 1); y1c = np.clip(y0 + 1, 0, h - 1)
    return (t[y0c, x0c] * (1 - fx) * (1 - fy) + t[y0c, x1c] * fx * (1 - fy) + t[y1c, x0c] * (1 - fx) * fy + t[y1c, x1c] * fx * fy)


def grass(gp):
    """grass block top: 16 px per block, per-pixel value noise, biome-tinted green"""
    px = np.floor(gp[..., 0] * 16).astype(np.int64); pz = np.floor(gp[..., 2] * 16).astype(np.int64)
    h = (px * 73856093 ^ pz * 19349663) & 0xffff
    n = (h / 65535.0)
    base = np.array([0.40, 0.62, 0.27])
    return base * (0.80 + 0.28 * n)[..., None]


def render(P, T, N, UV, texm, eye, target, W=960, H=540, fov=70.0, ss=2, shadow_r=0.36, dog_pos=(0, 0, 0), nearest=False):
    W2, H2 = W * ss, H * ss
    eye = np.asarray(eye, float); target = np.asarray(target, float)
    fwd = target - eye; fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = 1 / math.tan(math.radians(fov) / 2); asp = W / H
    gy, gx = np.mgrid[0:H2, 0:W2] + 0.5
    rx = (gx / W2 - 0.5) * 2 * asp / f; ry = (0.5 - gy / H2) * 2 / f
    dirs = rx[..., None] * right + ry[..., None] * up + fwd
    img = np.zeros((H2, W2, 3)); zb = np.full((H2, W2), 1e9)
    sky = np.array([0.47, 0.65, 1.0]) * 0.85 + 0.15 * np.array([0.75, 0.84, 1.0])
    img[:] = sky
    t = -eye[1] / np.where(np.abs(dirs[..., 1]) < 1e-9, -1e-9, dirs[..., 1])
    hit = t > 0
    gp = eye + dirs * t[..., None]
    g = grass(gp)
    # top face of a block: lightAccum with n = up
    g = g * min(1.0, 0.4 + 0.6 * (L0[1] + L1[1]))
    d2 = (gp[..., 0] - dog_pos[0]) ** 2 + (gp[..., 2] - dog_pos[2]) ** 2
    sh = np.clip(1 - np.sqrt(d2) / shadow_r, 0, 1) * 0.5
    g = g * (1 - sh[..., None])
    img[hit] = g[hit]
    zb[hit] = (t * (dirs @ fwd))[hit]
    # project
    rel = P - eye
    zc = rel @ fwd; xc = rel @ right; yc = rel @ up
    sx = (xc / zc * f / asp * 0.5 + 0.5) * W2; sy = (0.5 - yc / zc * f * 0.5) * H2
    lit_v = np.minimum(1.0, 0.4 + 0.6 * (np.clip(N @ L0, 0, 1) + np.clip(N @ L1, 0, 1)))
    th0 = texm[0].shape[0]
    for (a, b, c) in T:
        if zc[a] < 0.05 or zc[b] < 0.05 or zc[c] < 0.05:
            continue
        xs = np.array([sx[a], sx[b], sx[c]]); ys = np.array([sy[a], sy[b], sy[c]])
        x0 = max(int(xs.min()), 0); x1 = min(int(xs.max()) + 1, W2 - 1); y0 = max(int(ys.min()), 0); y1 = min(int(ys.max()) + 1, H2 - 1)
        if x0 > x1 or y0 > y1:
            continue
        den = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
        if abs(den) < 1e-9:
            continue
        # mip level: texels per (final, not supersampled) pixel
        uvA = abs((UV[b, 0] - UV[a, 0]) * (UV[c, 1] - UV[a, 1]) - (UV[c, 0] - UV[a, 0]) * (UV[b, 1] - UV[a, 1])) * th0 * th0
        lvl = int(np.clip(math.floor(0.5 * math.log2(max(uvA / (abs(den) / ss / ss), 1e-9))), 0, len(texm) - 1))
        tex = texm[lvl]
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        l1 = ((ys[1] - ys[2]) * (X - xs[2]) + (xs[2] - xs[1]) * (Y - ys[2])) / den
        l2 = ((ys[2] - ys[0]) * (X - xs[2]) + (xs[0] - xs[2]) * (Y - ys[2])) / den
        l3 = 1 - l1 - l2
        m = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
        if not m.any():
            continue
        iz = l1 / zc[a] + l2 / zc[b] + l3 / zc[c]; zz = 1 / iz
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        m &= zz < sub
        if not m.any():
            continue
        w1, w2, w3 = (l1 / zc[a] * zz)[m], (l2 / zc[b] * zz)[m], (l3 / zc[c] * zz)[m]
        u = w1 * UV[a, 0] + w2 * UV[b, 0] + w3 * UV[c, 0]; v = w1 * UV[a, 1] + w2 * UV[b, 1] + w3 * UV[c, 1]
        if nearest:  # vanilla entity textures: nearest, no mips
            t0 = texm[0]; th, tw = t0.shape[:2]
            col = t0[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
        else:
            col = bilinear(tex, u, v)
        # vanilla: lighting is computed per vertex and interpolated (Gouraud)
        lit = w1 * lit_v[a] + w2 * lit_v[b] + w3 * lit_v[c]
        sub[m] = zz[m]
        img[y0:y1 + 1, x0:x1 + 1][m] = col * lit[:, None]
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype('uint8'))
    return im.resize((W, H), Image.LANCZOS)


def views(m, Ms, tex, W=640, H=360, box=None):
    """(label, eye, target) for a hound standing at the origin facing -z, seen from a player"""
    if box is not None:
        P, N, T, UVb = box
        m = dict(T=T, UV=UVb)
    else:
        P, N = rigview.skin(m, Ms)
    out = []
    texm = mips(tex)
    for lab, dist, ang, third in (('1st person 2 blocks', 2.0, 60, False), ('1st person 3.5 blocks', 3.5, 120, False),
                                  ('1st person 6 blocks', 6.0, 35, False), ('3rd person (player 2.5 blocks away)', 2.5, 100, True)):
        a = math.radians(ang)
        pl = np.array([math.sin(a) * dist, 0, -math.cos(a) * dist])
        eye = pl + np.array([0, 1.62, 0])
        tgt = np.array([0, 0.38, 0])
        if third:
            d = tgt - eye; d /= np.linalg.norm(d)
            eye = eye - d * 4.0
        im = render(P, m['T'], N, m['UV'], texm, eye, tgt, 1280, 720, nearest=box is not None)
        # crop around the dog (what the eye sees of him on a 1280x720 screen), shown at 2x
        fwd = tgt - eye; fwd /= np.linalg.norm(fwd); right = np.cross(fwd, [0, 1, 0]); right /= np.linalg.norm(right); up = np.cross(right, fwd)
        rel = P - eye; zc = rel @ fwd; f = 1 / math.tan(math.radians(35)); asp = 1280 / 720
        sx = ((rel @ right) / zc * f / asp * 0.5 + 0.5) * 1280; sy = (0.5 - (rel @ up) / zc * f * 0.5) * 720
        cx, cy = (sx.min() + sx.max()) / 2, (sy.min() + sy.max()) / 2; hh = max(sy.max() - sy.min(), (sx.max() - sx.min()) * H / W) * 0.62
        box = (int(cx - hh * W / H), int(cy - hh), int(cx + hh * W / H), int(cy + hh))
        im = im.crop(box).resize((W, H), Image.LANCZOS)
        ImageDraw.Draw(im).text((6, 4), '%s  (%d px tall on a 720p screen)' % (lab, int(sy.max() - sy.min())), fill=(255, 255, 255))
        out.append(im)
    return out


def main():
    lod = sys.argv[1] if len(sys.argv) > 1 else 'ultra'
    if lod == 'vanilla':
        return vanilla(*sys.argv[2:])
    kind = sys.argv[2] if len(sys.argv) > 2 else 'redbone'
    outp = sys.argv[3] if len(sys.argv) > 3 else '/tmp/claude-0/h4/ingame_%s_%s.png' % (lod, kind)
    sel = sys.argv[4] if len(sys.argv) > 4 else 'stand'
    path = rigview.PATCH + 'models/wildlife/hound_%s.fhsk' % lod
    m = rigview.load(path)
    tex = np.asarray(Image.open(rigview.PATCH + 'textures/entity/wildlife/real/hound_%s%s.png' % (kind, '' if lod == 'ultra' else '_far')).convert('RGB')).astype(float) / 255
    tex = tex ** 2.2  # (linear filtering would be in sRGB in the game; keep sRGB) -> undone below
    tex = tex ** (1 / 2.2)
    poses = [p for p in rigview.POSES if p[0] in sel.split(',')]
    mats = rigview.dump(path, poses)
    rows = []
    for name, _ in poses:
        vs = views(m, mats[name], tex)
        rows.append(rigview.r3.sheet(vs, 4))
    W = rows[0].size[0]
    sheet = Image.new('RGB', (W, sum(r.size[1] for r in rows)))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y)); y += r.size[1]
    os.makedirs(os.path.dirname(outp), exist_ok=True)
    sheet.save(outp)
    print('wrote', outp)


def vanilla(kind='redbone', outp=None, sel='stand,trot'):
    """the Vanilla-preset box hound (classic3.py mirrors HoundModel layout + setupAnim) under the same camera / light"""
    import classic3
    tex = np.asarray(Image.open(classic3.OUT + 'hound_%s.png' % kind).convert('RGB')).astype(float) / 255
    rows = []
    for nm, kw in classic3.POSES:
        if nm not in sel.split(','):
            continue
        P, UVb = classic3.mesh(classic3.anim(**kw))
        P = np.asarray(P, float).reshape(-1, 3); UVb = np.asarray(UVb, float).reshape(-1, 2)
        Tr = P.reshape(-1, 3, 3)
        Nf = np.cross(Tr[:, 1] - Tr[:, 0], Tr[:, 2] - Tr[:, 0]); Nf /= np.linalg.norm(Nf, axis=1, keepdims=True) + 1e-12
        if (Nf[:, 1] @ np.ones(len(Nf))) < 0 and False:
            Nf = -Nf
        N = np.repeat(Nf, 3, 0)
        T = np.arange(len(P)).reshape(-1, 3)
        rows.append(rigview.r3.sheet(views(None, None, tex, box=(P, N, T, UVb)), 4))
    sheet = Image.new('RGB', (rows[0].size[0], sum(r.size[1] for r in rows)))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y)); y += r.size[1]
    outp = outp or '/tmp/claude-0/h4/ingame_vanilla_%s.png' % kind
    sheet.save(outp)
    print('wrote', outp)


if __name__ == '__main__':
    main()
