#!/usr/bin/env python3
"""[gear20] Stacked firewood, remade: a full block of split rounds in four staggered courses, running along the
block's facing axis so the cut ends show at the front and back. Bark on the outside of each piece, pale split wood
where it was cleaved, ring end grain on the cut ends - all vanilla log textures, so it matches the world (and any
resource pack) and lights like wood. Fills the whole block, so stacks of it tile into one tidy woodpile (side by side
and on top of each other), with slight variation in piece size and how far each end sits back."""
import json, random, os, sys

out = sys.argv[1] if len(sys.argv) > 1 else 'patch/assets/frontierhunts/models/block/stacked_firewood.json'
rnd = random.Random(20261003)
T = {
    'bark': 'minecraft:block/spruce_log', 'end': 'minecraft:block/spruce_log_top', 'split': 'minecraft:block/stripped_spruce_log',
    'bbark': 'minecraft:block/birch_log', 'bend': 'minecraft:block/birch_log_top', 'bsplit': 'minecraft:block/stripped_birch_log',
    'particle': 'minecraft:block/spruce_log'}
# four courses, widths per course (sum 16), staggered like a real stack
courses = [[4, 4.5, 3.5, 4], [3, 4, 4.5, 4.5], [4.5, 3.5, 4, 4], [3.5, 4.5, 4, 4]]
heights = [4, 4, 4, 4]
els = []
y0 = 0.0
birch = {(1, 2), (3, 0)}
for ci, widths in enumerate(courses):
    h = heights[ci]
    x0 = 0.0
    for pi, w in enumerate(widths):
        b = (ci, pi) in birch
        gap = 0.25
        x_a, x_b = x0 + gap, x0 + w - gap
        top = y0 + h - rnd.uniform(0.1, 0.45)
        zf = rnd.choice([0.0, 0.0, 0.25, 0.5, 0.75])
        zb = 16 - rnd.choice([0.0, 0.0, 0.25, 0.5])
        # which long faces show bark and which show the cleaved wood (a split quarter: two flat faces, one bark arc)
        quarter = rnd.randrange(4)
        faces = {}
        bark, end, split = ('bbark', 'bend', 'bsplit') if b else ('bark', 'end', 'split')
        u = rnd.uniform(0, 16 - (x_b - x_a))
        v = rnd.uniform(0, 16 - (top - y0))
        er = [round(u, 2), round(v, 2), round(u + (x_b - x_a), 2), round(v + (top - y0), 2)]
        # each cut end shows a whole round: the full log-top texture (rings inside a bark rim), turned at random
        for side, flush in (('north', zf == 0), ('south', zb == 16)):
            f = {'texture': '#' + end, 'uv': [0, 0, 16, 16], 'rotation': rnd.choice([0, 90, 180, 270])}
            if flush:
                f['cullface'] = side
            faces[side] = f
        long_uv = lambda span: [round(rnd.uniform(0, 12), 2), 0, round(rnd.uniform(0, 12), 2) + 4, 16]
        for k, side in enumerate(('up', 'east', 'down', 'west')):
            tex = split if (k == quarter or k == (quarter + 1) % 4) else bark
            uvx = rnd.uniform(0, 16 - min(4.5, (x_b - x_a) if side in ('up', 'down') else (top - y0)))
            wspan = (x_b - x_a) if side in ('up', 'down') else (top - y0)
            f = {'texture': '#' + tex, 'uv': [round(uvx, 2), 0, round(uvx + wspan, 2), 16], 'rotation': 90}
            if side == 'down' and ci == 0:
                f['cullface'] = 'down'
            faces[side] = f
        els.append({'from': [round(x_a, 2), round(y0, 2), zf], 'to': [round(x_b, 2), round(top, 2), zb], 'faces': faces})
        x0 += w
    y0 += h
model = {'parent': 'minecraft:block/block', 'ambientocclusion': True, 'textures': T, 'elements': els,
         'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.625, 0.625, 0.625]}}}
os.makedirs(os.path.dirname(out), exist_ok=True)
json.dump(model, open(out, 'w'), indent=1)
print(out, len(els), 'pieces')
