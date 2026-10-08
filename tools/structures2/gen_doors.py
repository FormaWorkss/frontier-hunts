#!/usr/bin/env python3
"""[structures2] Regenerates the cabin door models + blockstate so the drawn door matches vanilla DoorBlock's collision /
outline shape in every state (the old blockstate drew the leaf on the opposite edge of the block from its hitbox).

Canonical model = door facing north, closed: leaf on the south edge (z 13..16), hinge on the west for *_left, handle at
the free (east) end, a handle on both faces. Rotation per state is derived from the vanilla shape; open states use the
opposite-handed model so the handle stays at the swinging end (exactly how vanilla pairs *_open models)."""
import json, os, sys, zipfile
JAR = sys.argv[1]
OUT = sys.argv[2]  # repo patch dir
z = zipfile.ZipFile(JAR)
src = {h: json.loads(z.read(f'assets/frontierhunts/models/block/cabin_door_{h}.json')) for h in ('lower', 'upper')}
SOUTH = (0, 0, 0, 16, 16, 3); NORTH = (0, 0, 13, 16, 16, 16); WEST = (13, 0, 0, 16, 16, 16); EAST = (0, 0, 0, 3, 16, 16)
ROT = {NORTH: 0, EAST: 90, SOUTH: 180, WEST: 270}


def shape(f, closed, right):
    if f == 'south': return SOUTH if closed else (EAST if right else WEST)
    if f == 'west': return WEST if closed else (SOUTH if right else NORTH)
    if f == 'north': return NORTH if closed else (WEST if right else EAST)
    return EAST if closed else (NORTH if right else SOUTH)


def model(half, hand):
    m = json.loads(json.dumps(src[half]))
    els = []
    handle = None
    for e in m['elements']:
        f, t = e['from'], e['to']
        if f[2] < 0 and t[2] <= 0.01:  # the old handle (outside the north face)
            handle = e
            continue
        # leaf on the south edge: z 0..3 -> 13..16 (rails keep their 0.1 overhang)
        e['from'] = [f[0], f[1], round(f[2] + 13, 3)]
        e['to'] = [t[0], t[1], round(t[2] + 13, 3)]
        els.append(e)
    if handle is not None:
        for zf, zt in ((12, 13), (16, 17)):
            h = json.loads(json.dumps(handle))
            h['from'] = [handle['from'][0], handle['from'][1], zf]
            h['to'] = [handle['to'][0], handle['to'][1], zt]
            els.append(h)
    if hand == 'right':  # mirror across x
        for e in els:
            x0, x1 = 16 - e['to'][0], 16 - e['from'][0]
            e['from'][0], e['to'][0] = x0, x1
            fc = e['faces']
            if 'east' in fc and 'west' in fc:
                fc['east'], fc['west'] = fc['west'], fc['east']
    m['elements'] = els
    m['ambientocclusion'] = False
    return m


mdir = os.path.join(OUT, 'assets/frontierhunts/models/block')
os.makedirs(mdir, exist_ok=True)
for half in ('lower', 'upper'):
    for hand in ('left', 'right'):
        json.dump(model(half, hand), open(os.path.join(mdir, f'cabin_door_{half}_{hand}.json'), 'w'), separators=(',', ':'))
variants = {}
for f in ('north', 'east', 'south', 'west'):
    for half in ('lower', 'upper'):
        for hinge in ('left', 'right'):
            for op in ('false', 'true'):
                closed = op == 'false'
                hand = hinge if closed else ('right' if hinge == 'left' else 'left')
                v = {'model': f'frontierhunts:block/cabin_door_{half}_{hand}'}
                r = ROT[shape(f, closed, hinge == 'right')]
                if r: v['y'] = r
                variants[f'facing={f},half={half},hinge={hinge},open={op}'] = v
bdir = os.path.join(OUT, 'assets/frontierhunts/blockstates')
os.makedirs(bdir, exist_ok=True)
json.dump({'variants': variants}, open(os.path.join(bdir, 'cabin_door.json'), 'w'), indent=1)
print('wrote', len(variants), 'variants')
