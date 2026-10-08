#!/usr/bin/env python3
"""[outfitter] 'After' contact sheets: the generated outfits (same box tables as OutfitGeometry.java) on the vanilla
player, wide and slim, idle views + walk/sprint/sneak/swim/prone/riding/sleeping/bow/rifle.
usage: python3 tools/outfitter/preview.py <repo> <out_dir> [hd|vanilla] [only,...]"""
import os, sys
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import mcr, wear
import outfit as OF

REPO = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/claude-0/ofx/after'
LOOK = sys.argv[3] if len(sys.argv) > 3 else 'hd'
ONLY = sys.argv[4].split(',') if len(sys.argv) > 4 else None

O, GROUPS, UV = OF.build()
_TEX = {}


def tex(grp):
    if grp not in _TEX:
        p = os.path.join(REPO, 'patch/assets/frontierhunts/textures/entity/outfitter', grp + ('_hd.png' if LOOK == 'hd' else '.png'))
        _TEX[grp] = mcr.tex_array(Image.open(p))
    return _TEX[grp]


def layer(name, slim, visible=None, hide_under_hem=False, hide_under_boot=False, hide_under_mitten=False, hide_under_long=False):
    boxes, grp = O[name]
    P = wear.empty_parts()
    def add(pname, b, mirror):
        x, w = b.x, b.w
        pose = b.pose
        mirror = b.left
        if mirror:
            x = -(b.x + b.w)
            if pose:
                pose = (-pose[0], pose[1], pose[2], pose[3], -pose[4], -pose[5])
        c = mcr.Cube(b.uv[0], b.uv[1], x, b.y, b.z, w, b.h, b.d, b.g, mirror=mirror)
        if pose:
            P[pname].child('c%d' % len(P[pname].children), [c], pose[:3], pose[3:])
        else:
            P[pname].cubes.append(c)
    for b in boxes:
        t = b.tag or ''
        if hide_under_hem and 'hem' in t or hide_under_boot and 'boot' in t or hide_under_mitten and 'mitten' in t or hide_under_long and 'long' in t:
            continue  # [clothing] the same give-way rules as OutfitModel
        if b.part == 'head':
            add('head', b, False)
        elif b.part == 'body':
            add('body', b, False)
        elif b.part == ('arm_slim' if slim else 'arm'):
            add('right_arm', b, False)
        elif b.part == ('arm_slim_l' if slim else 'arm_l'):
            add('left_arm', b, True)
        elif b.part == 'leg':
            add('right_leg', b, False)
        elif b.part == 'leg_l':
            add('left_leg', b, True)
    uvw, uvh = UV[grp]
    if visible is None:
        visible = wear.PARTS
    return wear.Layer(tex(grp), uvw, uvh, P, visible, name=name)


def chest(name, slim):
    vis = wear.SLOT_PARTS['chest'] + (('right_leg', 'left_leg') if name in OF.HEM_ON_LEGS else ())
    return layer(name, slim, vis)


SETS = {
    'trapper': lambda s: [layer('garment/buckskin_leggings', s, wear.SLOT_PARTS['legs'], hide_under_hem=True, hide_under_boot=True),  # [clothing]
                          layer('garment/fur_mukluks', s, wear.SLOT_PARTS['feet']),
                          chest('garment/buckskin_coat', s), layer('garment/fur_hat', s, wear.SLOT_PARTS['head'])],
    'buckskin_coat': lambda s: [chest('garment/buckskin_coat', s)],
    'leggings_mukluks': lambda s: [layer('garment/buckskin_leggings', s, wear.SLOT_PARTS['legs'], hide_under_boot=True), layer('garment/fur_mukluks', s, wear.SLOT_PARTS['feet']),
                                   layer('garment/fur_hat', s, wear.SLOT_PARTS['head'])],
    'bear_fur_coat': lambda s: [chest('garment/bear_fur_coat', s), layer('extra/mittens', s, ('right_arm', 'left_arm'))],
    'hide_robe': lambda s: [chest('garment/hide_robe', s), layer('garment/fur_mukluks', s, wear.SLOT_PARTS['feet'])],
    'carbon': lambda s: [layer('carbon/trousers', s, wear.SLOT_PARTS['legs']), layer('carbon/jacket', s, wear.SLOT_PARTS['chest']),
                         layer('carbon/hood', s, wear.SLOT_PARTS['head'])],
    'scent_suit': lambda s: [layer('coverall/scent_suit', s)],
    'lined_coverall': lambda s: [layer('coverall/timber_camo_coveralls', s), layer('extra/lining', s, ('body',))],
}
for cid in ['timber_camo_coveralls', 'autumn_camo_coveralls', 'marsh_camo_coveralls', 'prairie_camo_coveralls', 'snow_camo_coveralls',
            'digital_camo_coveralls', 'blaze_camo_coveralls']:
    SETS[cid] = (lambda c: (lambda s: [layer('coverall/' + c, s)]))(cid)
for p in OF.GHILLIE:
    SETS['ghillie_' + p] = (lambda p: (lambda s: [layer('ghillie/%s_trousers' % p, s, wear.SLOT_PARTS['legs']),
                                                  layer('ghillie/%s_jacket' % p, s, wear.SLOT_PARTS['chest']),
                                                  layer('ghillie/%s_hood' % p, s, wear.SLOT_PARTS['head'])]))(p)

POSES = ['walk_a', 'sprint', 'sneak', 'swim', 'prone', 'riding', 'sleeping', 'bow', 'rifle']


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SETS.items():
        if ONLY and name not in ONLY:
            continue
        ims = wear.sheet(name, fn, POSES)
        ims.append(wear.label(wear.shot(mcr.POSES['idle'], 'front34', True, fn(True)), name + ' slim'))
        ims.append(wear.label(wear.shot(mcr.POSES['sneak'], 'back34', True, fn(True)), name + ' slim sneak'))
        ims.append(wear.label(wear.shot(mcr.POSES['sprint_b'], 'back34', False, fn(False)), name + ' sprint back'))
        wear.grid(ims, 5).save(os.path.join(OUT, 'after_%s%s.png' % (name, '' if LOOK == 'hd' else '_vanilla')))
        print(name)


if __name__ == '__main__':
    main()


# ------------------------------------------------------------------------------------------------ pack + harness
def pack_extras(chest_outfit):
    import zipfile
    z = zipfile.ZipFile(OF.REF_JAR)
    parts = mcr.load_fheq(z.read('assets/frontierhunts/models/equipment/hunter_pack_field.fheq'))
    import io
    tex = mcr.tex_array(Image.open(io.BytesIO(z.read('assets/frontierhunts/textures/equipment/packs/material.png'))))
    depth = OF.chest_depth(O[chest_outfit][0]) if chest_outfit else 0.25
    depth = max(0.25, depth)
    off = max(0.0, (depth - 0.1) / 16.0)
    Mloc = mcr.mat_scale(16, 16, 16) @ mcr.mat_translate(0, 0.335, 0.2723 + off) @ mcr.mat_rot('x', 180) @ mcr.mat_scale(1.16, 1.17, 0.82)
    i = 0
    while i < len(OF.HARNESS_DEPTHS) - 1 and OF.HARNESS_DEPTHS[i] < depth - 0.001:
        i += 1
    H = layer('extra/harness_%d' % i, False, ('body',))
    def fn(sc, P, M):
        mcr.mesh_emitter(parts, tex, Mloc)(sc, M @ P['body'].local())
        H.parts['body'].copy_pose(P['body'])
        sc.add_part(H.parts['body'], H.tex, H.tw, H.th, M)
    return fn


def pack_sheet(out):
    ims = []
    for chest in (None, 'coverall/timber_camo_coveralls', 'garment/bear_fur_coat', 'garment/hide_robe'):
        L = [chest_layer for chest_layer in ([chest and (chest('x', False) if False else None)] if False else [])]
        Ls = [] if chest is None else ([layer(chest, False)] if chest.startswith('coverall') else [globals()['chest'](chest, False)])
        ex = pack_extras(chest)
        for pose, view in (('idle', 'back34'), ('idle', 'side'), ('idle', 'front34'), ('sneak', 'side'), ('walk_a', 'back34')):
            ims.append(wear.label(wear.shot(mcr.POSES[pose], view, False, Ls, ex), '%s %s %s' % (chest or 'bare', pose, view)))
    wear.grid(ims, 5).save(out)
