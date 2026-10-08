#!/usr/bin/env python3
"""[onboard2] Seated-player clearance check + previews for the seats.

python3 tools/onboard2/seat_check.py <repo> <out dir> [--no-images]

For every seat (and variant) the vanilla riding pose is placed exactly where the game puts it (seat entity y, Player
vehicle attachment 0.6, PlayerRenderer scale 0.9375, outer skin layer +0.25 px) and sampled point by point against
the seat's model elements (with their rotations). Reports the deepest penetration per seat, the gap between the
thighs and the seat right at the hip, the gap from the back to the backrest, the eye level above the floor, and
renders Vanilla + Ultra previews with and without the sitter."""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

R = os.path.abspath(sys.argv[1])
OUT = sys.argv[2]
IMAGES = '--no-images' not in sys.argv
VERBOSE = '-v' in sys.argv
sys.path.insert(0, os.path.join(R, 'tools/onboard2'))
sys.path.insert(0, os.path.join(R, 'tools/outfitter'))
import mcr  # noqa: E402
import seat_models as SM  # noqa: E402
from seat_geom import ATTACH, EYE, SCALE, SEATS, SUNK, TOWER_RAISE  # noqa: E402

CORNERS = {
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],
}


def rotmat(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == 'y':
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def el_matrix(e):
    if 'rotation' not in e:
        return None, None
    r = e['rotation']
    return rotmat(r['axis'], r['angle']), np.array(r['origin'], float)


_TEX = {}


def texture(rid, ultra):
    key = (rid, ultra)
    if key not in _TEX:
        path = rid.split(':', 1)[1]
        base = os.path.join(R, 'patch/resourcepacks/realistic_world/assets/frontierhunts/textures' if ultra else 'patch/assets/frontierhunts/textures')
        _TEX[key] = mcr.tex_array(Image.open(os.path.join(base, path + '.png')))
    return _TEX[key]


def add_model(scene, model, ultra, M=np.eye(4), tint=(1, 1, 1)):
    tx = model['textures']
    for e in model['elements']:
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        Rm, O = el_matrix(e)
        for fc, spec in e['faces'].items():
            pts = []
            for c in CORNERS[fc]:
                p = f + (t - f) * np.array(c, float)
                if Rm is not None:
                    p = Rm @ (p - O) + O
                pts.append(list(p) + [1])
            P = (np.array(pts) @ M.T)[:, :3]
            u0, v0, u1, v1 = [c / 16.0 for c in spec['uv']]
            U = np.array([(u0, v0), (u1, v0), (u1, v1), (u0, v1)])
            tex = texture(tx[spec['texture'][1:]], ultra)
            scene._quad(P, U, tex, tint)


def player_parts():
    P = mcr.humanoid_skin_parts(False)
    mcr.setup_anim(P, mcr.Pose('sit', riding=True, entity='stand'))
    return P


def player_matrix(feet, yaw_deg=0.0):
    """World px <- player model px. yaw: body rotation relative to facing north (+ = turned right/clockwise from above)."""
    M = mcr.mat_translate(*feet) @ mcr.mat_rot('y', -yaw_deg) @ mcr.mat_scale(SCALE, SCALE, SCALE) @ mcr.mat_translate(0, 24.0, 0) @ mcr.mat_scale(-1, -1, 1)
    return M


def player_points(feet, yaw=0.0, grow=0.25, stepp=0.5):
    """(points Nx3 world px, part names) sampled inside every cube of the posed player (outer layer size)."""
    P = player_parts()
    M = player_matrix(feet, yaw)
    pts, names = [], []
    for name, part in P.items():
        L = M @ part.local()
        for c in part.cubes[:1]:  # the base cube; inflate by the outer layer
            x0, y0, z0 = c.x - grow, c.y - grow, c.z - grow
            x1, y1, z1 = c.x + c.w + grow, c.y + c.h + grow, c.z + c.d + grow
            xs = np.arange(x0, x1 + 1e-6, stepp)
            ys = np.arange(y0, y1 + 1e-6, stepp)
            zs = np.arange(z0, z1 + 1e-6, stepp)
            G = np.stack(np.meshgrid(xs, ys, zs, indexing='ij'), -1).reshape(-1, 3)
            W = (np.c_[G, np.ones(len(G))] @ L.T)[:, :3]
            pts.append(W)
            names += [name] * len(W)
    return np.vstack(pts), np.array(names)


def penetration(model, pts, names, report=None):
    worst = (0.0, None, None)
    for i, e in enumerate(model['elements']):
        f, t = np.array(e['from'], float), np.array(e['to'], float)
        Rm, O = el_matrix(e)
        Q = pts if Rm is None else (pts - O) @ Rm + O  # inverse rotation (R orthonormal: R^T)
        inside = np.all((Q > f) & (Q < t), axis=1)
        if inside.any():
            d = np.minimum(Q[inside] - f, t - Q[inside]).min(axis=1)
            k = d.argmax()
            if report is not None and d[k] > 0.05:
                report.append((round(float(d[k]), 3), str(names[inside][k]), i, e['from'], e['to']))
            if d[k] > worst[0]:
                worst = (float(d[k]), names[inside][k], i)
    return worst


def top_under(model, x, z, below):
    best = -99.0
    for e in model['elements']:
        if 'rotation' in e:
            continue
        f, t = e['from'], e['to']
        if f[0] <= x <= t[0] and f[2] <= z <= t[2] and t[1] <= below + 1e-6:
            best = max(best, t[1])
    return best


def back_gap(model, feet, hip_z, yaw=0.0):
    """Distance from the torso's back (outer layer) to the nearest element face behind it, at chest height."""
    torso_back = hip_z + 2.25 * SCALE
    best = 99.0
    y_lo, y_hi = feet[1] + 13 * SCALE, feet[1] + 22 * SCALE
    for e in model['elements']:
        if 'rotation' in e:
            continue
        f, t = e['from'], e['to']
        if t[1] > y_lo and f[1] < y_hi and f[0] < 11 and t[0] > 5 and f[2] >= torso_back - 0.5:
            best = min(best, f[2] - torso_back)
    return best


def check(name, model, seat_y, hip, floor=0.0, yaws=(0.0,)):
    feet = (8.0, seat_y - ATTACH, 8.0 + hip)
    res = []
    for yaw in yaws:
        pts, names = player_points(feet, yaw)
        rep = []
        res.append(penetration(model, pts, names, rep))
        if VERBOSE:
            for r in sorted(rep, reverse=True)[:6]:
                print('   ', yaw, r)
    worst = max(res, key=lambda r: r[0])
    # thighs at the hip: underside points of the legs within 1 px of the pivot
    pts, names = player_points(feet, 0.0, grow=0.25, stepp=0.25)
    leg = pts[(names == 'right_leg') | (names == 'left_leg')]
    near = leg[(leg[:, 2] > 8 + hip - 1.0) & (leg[:, 2] < 8 + hip + 0.6)]
    gap = 99.0
    for p in near[np.argsort(near[:, 1])][:40]:
        tu = top_under(model, p[0], p[2], p[1] + 0.3)
        if tu > -99:
            gap = min(gap, p[1] - tu)
    eye = (feet[1] + EYE - floor) / 16.0
    bg = back_gap(model, feet, 8.0 + hip)
    return {'seat': name, 'worst_px': round(worst[0], 3), 'part': worst[1], 'element': worst[2], 'hip_gap_px': round(gap, 3),
            'back_gap_px': round(bg, 3) if bg < 90 else None, 'eye_blocks': round(eye, 4)}


def render_set(name, model, sitter=None, hip=0.0, yaw_body=0.0, floor=0.0):
    if not IMAGES:
        return
    os.makedirs(OUT, exist_ok=True)
    tiles = []
    for ultra in (False, True):
        for (yaw, pitch, who) in ((35, 22, False), (215, 20, False), (300, 18, False), (90, 4, True), (35, 18, True)):
            sc = mcr.Scene()
            add_model(sc, model, ultra)
            if who and sitter is not None:
                P = player_parts()
                skin = mcr.tex_array(mcr.mc_image('assets/minecraft/textures/entity/player/wide/steve.png'))
                feet = (8.0, sitter - ATTACH, 8.0 + hip)
                Mp = player_matrix(feet, yaw_body)
                for part in P.values():
                    sc.add_part(part, skin, 64, 64, Mp)
            if who and sitter is None:
                continue
            V = mcr.camera(yaw, pitch, target=(8, 10 if who else 7, 8))
            img = mcr.render(sc, V, size=(230, 260), scale=9.0 if who else 12.0, ss=2, bg=(150, 160, 150) if ultra else (120, 132, 140))
            tiles.append(img)
    W = sum(t.width for t in tiles[:len(tiles) // 2]) if tiles else 0
    sheet = Image.new('RGB', (max(1, W), 2 * 260 + 18), (30, 30, 30))
    half = len(tiles) // 2
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % half) * 230, (i // half) * 278 + 18))
    ImageDraw.Draw(sheet).text((4, 2), name + '  (top: Vanilla · bottom: Ultra)', fill=(240, 240, 240))
    sheet.save(os.path.join(OUT, name + '.png'))


def cases():
    s = SEATS
    out = []
    out.append(('log_stump_seat', SM.stump(), s['log_stump_seat']['y'], 0.0, 0.0, (0, 22.5, 45, 90, 135, 180)))
    out.append(('camp_chair', SM.camp_chair(), s['camp_chair']['y'], s['camp_chair']['hip'], 0.0, (0,)))
    for l, r in ((False, False), (True, False), (False, True), (True, True)):
        out.append(('trail_bench_%s' % {(0, 0): 'single', (1, 0): 'left', (0, 1): 'right', (1, 1): 'middle'}[(int(l), int(r))],
                    SM.trail_bench(l, r), s['trail_bench']['y'], s['trail_bench']['hip'], 0.0, (0,)))
    out.append(('blind_chair', SM.blind_chair('full'), s['blind_chair']['y'], 0.0, 0.0, (0,)))
    ty = s['tower_chair']['y']
    out.append(('tower_chair', SM.tower_chair('full'), ty, 0.0, 0.0, (0,)))
    out.append(('tower_chair_raised', SM.tower_chair('full', raised=True), ty + TOWER_RAISE, 0.0, 0.0, (0,)))
    out.append(('tower_chair_sunk', SM.tower_chair('full', sunk=True), ty + SUNK, 0.0, SUNK, (0,)))
    out.append(('tower_chair_sunk_raised', SM.tower_chair('full', raised=True, sunk=True), ty + SUNK + TOWER_RAISE, 0.0, SUNK, (0,)))
    return out


if __name__ == '__main__':
    rows = []
    for name, model, y, hip, floor, yaws in cases():
        r = check(name, model, y, hip, floor, yaws)
        rows.append(r)
        print(json.dumps(r))
        render_set(name, model, y, hip, 0.0, floor)
    os.makedirs(OUT, exist_ok=True)
    json.dump(rows, open(os.path.join(OUT, 'clearance.json'), 'w'), indent=1)
