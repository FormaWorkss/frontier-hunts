#!/usr/bin/env python3
"""[villages] top-down map of a REAL generated area (mca_dump.py output): top block per column, coloured with the
average colour of its top texture (vanilla client jar + mod jar), biome-ish tints, hill shading.
usage: topdown_real.py <dump.json> <out.png> [scale] [--mod JAR]"""
import json, sys, zipfile, io, os
import numpy as np
from PIL import Image, ImageDraw
args = [a for a in sys.argv[1:] if not a.startswith('--')]
mod = sys.argv[sys.argv.index('--mod') + 1] if '--mod' in sys.argv else None
if mod in args: args.remove(mod)
d = json.load(open(args[0])); S = int(args[2]) if len(args) > 2 else 3
jars = [zipfile.ZipFile('/mnt/user-data/uploads/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')]
if mod: jars.append(zipfile.ZipFile(mod))
cache = {}
TINT = {'grass_block': (124, 189, 107), 'short_grass': (124, 189, 107), 'tall_grass': (124, 189, 107), 'fern': (124, 189, 107), 'large_fern': (124, 189, 107),
        'oak_leaves': (89, 174, 48), 'spruce_leaves': (97, 153, 97), 'birch_leaves': (128, 167, 85), 'jungle_leaves': (89, 174, 48), 'dark_oak_leaves': (89, 174, 48),
        'acacia_leaves': (89, 174, 48), 'mangrove_leaves': (89, 174, 48), 'vine': (89, 174, 48), 'water': (63, 118, 228), 'lily_pad': (32, 128, 48)}
def colour(name):
    ns, _, path = name.partition(':'); path = path.split('[')[0]
    if path in cache: return cache[path]
    c = None
    cands = [path + '_top', path, path.replace('_wall', ''), path.replace('_slab', '').replace('_stairs', '') + 's', path.replace('_slab', '').replace('_stairs', '').replace('_fence', '_planks').replace('_wall', ''),
             path.replace('stripped_', 'stripped_') + '_top', path.replace('_log', '_log_top'), path + '_still', path.replace('wall_', '').replace('_wall_sign', '_planks'), path.replace('_sign', '_planks').replace('_trapdoor', '_trapdoor')]
    for cand in cands:
        for j in jars:
            for nsp in (ns, 'minecraft'):
                try:
                    im = Image.open(io.BytesIO(j.read('assets/%s/textures/block/%s.png' % (nsp, cand)))).convert('RGBA')
                    a = np.array(im)[:16, :16].reshape(-1, 4).astype(float)
                    a = a[a[:, 3] > 0] if (a[:, 3] > 0).any() else a
                    c = a[:, :3].mean(0); break
                except KeyError: pass
            if c is not None: break
        if c is not None: break
    if c is None: c = np.array([255, 0, 255.])
    if path in TINT: c = c * np.array(TINT[path]) / 255 * 1.6 if path != 'water' else np.array(TINT[path], float)
    cache[path] = c; return c
bl = d['blocks']
xs = [b[0] for b in bl]; zs = [b[2] for b in bl]
x0, z0 = min(xs), min(zs); w, h = max(xs) - x0 + 1, max(zs) - z0 + 1
top = {}
SKIP = ('air', 'cave_air', 'barrier', 'light', 'structure_void', 'structure_space')
for x, y, z, n in bl:
    if n.split(':')[1].split('[')[0] in SKIP: continue
    k = (x, z)
    if k not in top or y > top[k][0]: top[k] = (y, n)
H = np.zeros((h, w)); img = np.zeros((h, w, 3))
for (x, z), (y, n) in top.items():
    H[z - z0, x - x0] = y; img[z - z0, x - x0] = colour(n)
gx = np.zeros_like(H); gz = np.zeros_like(H)
gx[:, 1:] = H[:, 1:] - H[:, :-1]; gz[1:, :] = H[1:, :] - H[:-1, :]
shade = np.clip(1.0 + 0.10 * gx + 0.12 * gz, 0.6, 1.3)
img = np.clip(img * shade[:, :, None], 0, 255).astype(np.uint8)
im = Image.fromarray(img).resize((w * S, h * S), Image.NEAREST)
ImageDraw.Draw(im).text((4, 4), d.get('title', ''), fill=(255, 255, 255))
im.save(args[1]); print('saved', args[1], w, h)
