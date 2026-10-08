#!/usr/bin/env python3
"""[structures2] Offline door sanity check: for every door-like blockstate in the built jar (facing+half+hinge+open,
e.g. frontierhunts:cabin_door) the drawn leaf (largest model element, rotated by the blockstate) must coincide with
vanilla DoorBlock's collision/outline box for that state, and the handle must sit at the swinging end, away from the
hinge (hinge corner = overlap of the closed and open boxes). Vanilla spruce_door is checked as a control.
usage: door_shapes_check.py <mod jar> [vanilla client jar]"""
import json, sys, zipfile
MOD = sys.argv[1]
V = sys.argv[2] if len(sys.argv) > 2 else '/mnt/user-data/uploads/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
SOUTH = (0, 0, 0, 16, 16, 3); NORTH = (0, 0, 13, 16, 16, 16); WEST = (13, 0, 0, 16, 16, 16); EAST = (0, 0, 0, 3, 16, 16)


def shape(f, closed, right):  # net.minecraft.world.level.block.DoorBlock#getShape (1.21.1)
    if f == 'south': return SOUTH if closed else (EAST if right else WEST)
    if f == 'west': return WEST if closed else (SOUTH if right else NORTH)
    if f == 'north': return NORTH if closed else (WEST if right else EAST)
    return EAST if closed else (NORTH if right else SOUTH)


def rot(b, y):
    x0, y0, z0, x1, y1, z1 = b
    for _ in range((y // 90) % 4):  # blockstate y rotation = clockwise seen from above
        x0, z0, x1, z1 = 16 - z1, x0, 16 - z0, x1
    return (x0, y0, z0, x1, y1, z1)


zs = [zipfile.ZipFile(p) for p in (MOD, V)]
names = [set(z.namelist()) for z in zs]


def rd(p):
    for z, n in zip(zs, names):
        if p in n: return json.loads(z.read(p))


def getm(ref):
    ns, p = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
    m = rd(f'assets/{ns}/models/{p}.json')
    while m is not None and 'elements' not in m and m.get('parent'):
        m = getm(m['parent'])
    return m


def vol(e):
    f, t = e['from'], e['to']
    return (t[0] - f[0]) * (t[1] - f[1]) * (t[2] - f[2])


def check(ns, block, handles=True):
    bs = rd(f'assets/{ns}/blockstates/{block}.json')['variants']
    bad = 0
    for k, v in bs.items():
        p = dict(kv.split('=') for kv in k.split(','))
        m = getm(v['model'])
        els = sorted(m['elements'], key=vol, reverse=True)
        leaf = rot(tuple(round(x) for x in els[0]['from'] + els[0]['to']), v.get('y', 0))
        want = shape(p['facing'], p['open'] == 'false', p['hinge'] == 'right')
        err = []
        if leaf != want:
            err.append(f'leaf {leaf} != shape {want}')
        if handles and len(els) > 1:
            closed = shape(p['facing'], True, p['hinge'] == 'right')
            opened = shape(p['facing'], False, p['hinge'] == 'right')
            hx = (max(closed[0], opened[0]) + min(closed[3], opened[3])) / 2
            hz = (max(closed[2], opened[2]) + min(closed[5], opened[5])) / 2
            small = [e for e in els[1:] if vol(e) <= 8]
            for e in small:
                b = rot(tuple(e['from'] + e['to']), v.get('y', 0))
                cx, cz = (b[0] + b[3]) / 2, (b[2] + b[5]) / 2
                if max(abs(cx - hx), abs(cz - hz)) < 8:
                    err.append(f'handle at ({cx:.1f},{cz:.1f}) next to the hinge ({hx},{hz})')
        if err:
            bad += 1
            print(f'  {ns}:{block} {k}: ' + '; '.join(err))
    print(f'{ns}:{block}: {len(bs)} states, {bad} bad')
    return bad


doors = []
for n in sorted(names[0]):
    if n.startswith('assets/') and '/blockstates/' in n and n.endswith('.json'):
        try:
            vs = json.loads(zs[0].read(n)).get('variants', {})
        except Exception:
            continue
        k = next(iter(vs), '')
        if all(s in k for s in ('facing=', 'half=', 'hinge=', 'open=')):
            doors.append((n.split('/')[1], n.rsplit('/', 1)[1][:-5]))
total = check('minecraft', 'spruce_door', handles=False)
if total: print('control failed: shape table wrong'); sys.exit(2)
for ns, b in doors:
    total += check(ns, b)
print('DOOR SHAPES OK' if total == 0 else f'DOOR SHAPES: {total} bad states')
sys.exit(1 if total else 0)
