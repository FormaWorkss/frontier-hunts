#!/usr/bin/env python3
"""[1.1.8] Toboggan: entity texture (32x32: deck boards, end grain, runners, rope), item sprite, item model, recipe."""
import json, os, random, sys
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import range_art as R

A, D = R.A, R.D


def entity():
    r = random.Random(5)
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(32):
            board = x // 8
            base = [(176, 128, 78), (166, 120, 72), (182, 134, 84), (170, 124, 76)][board]
            g = 0.92 + 0.08 * ((y * 7 + board * 3) % 5 == 0) + r.uniform(-0.05, 0.05)
            if x % 8 == 0:
                g = 0.62  # gap between boards
            px[x, y] = R.shade(base + (255,), g)
    for y in range(16, 20):
        for x in range(32):
            px[x, y] = R.shade((128, 88, 52, 255), 0.95 + r.uniform(-0.06, 0.06))
    for y in range(20, 24):
        for x in range(32):
            px[x, y] = R.shade((86, 58, 36, 255), 0.95 + r.uniform(-0.05, 0.05))
    for y in range(24, 26):
        for x in range(32):
            px[x, y] = (206, 182, 132, 255) if (x + y) % 3 else (160, 136, 92, 255)
    p = os.path.join(A, 'textures', 'entity', 'sled.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p, optimize=True)


def sprite(d, px):
    wood, hi, dk = (172, 124, 76, 255), (204, 158, 102, 255), (110, 74, 44, 255)
    d.polygon([(1, 12), (11, 12), (14, 9), (14, 6), (12, 6), (12, 8), (10, 10), (1, 10)], fill=wood)
    d.line([(1, 10), (10, 10)], fill=hi)
    d.line([(1, 13), (11, 13)], fill=dk)
    for x in (3, 7):
        px[x, 11] = dk
    d.line([(2, 9), (10, 9)], fill=(214, 190, 140, 255))  # rope


def rope_tex():
    """block-atlas rope texture (twisted manila)"""
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            twist = (x + y * 2) % 6
            px[x, y] = (214, 188, 136, 255) if twist < 3 else (176, 148, 98, 255)
            if twist == 5:
                px[x, y] = (140, 112, 70, 255)
    R.save_png(im, 'block', 'sled_rope.png')


def model():
    """[1.1.9] the toboggan as a JSON model (item, GUI and the entity itself): slatted deck, a front that curls up and
    back in 22.5-degree steps, crossbars, side rails on posts and a rope run along the rails"""
    import math
    t = {'deck': 'minecraft:block/stripped_birch_log', 'rail': 'minecraft:block/stripped_spruce_log', 'bar': 'minecraft:block/spruce_planks',
         'rope': 'frontierhunts:block/sled_rope', 'particle': 'minecraft:block/stripped_birch_log'}
    els = []

    def box(fr, to, tex, rot=None, along_z=True):
        faces = {}
        for f in ('north', 'south', 'east', 'west', 'up', 'down'):
            face = {'texture': '#' + tex}
            if along_z and f in ('up', 'down'):
                face['rotation'] = 90  # grain runs along the boards
            faces[f] = face
        e = {'from': [round(v, 3) for v in fr], 'to': [round(v, 3) for v in to], 'faces': faces}
        if rot:
            e['rotation'] = rot
        els.append(e)

    Z0, Z1 = 3.5, 30.5  # deck from the curl (front, low z) to the tail
    slats = [(1.5, 4.5), (4.75, 7.75), (8.25, 11.25), (11.5, 14.5)]
    for x0, x1 in slats:
        box([x0, 1.0, Z0], [x1, 2.0, Z1], 'deck')
    # crossbars across the top of the slats (what the rails stand on); a runner board under each outer slat
    for z in (6.0, 16.0, 26.0):
        box([0.5, 2.0, z], [15.5, 2.7, z + 1.6], 'bar', along_z=False)
    for x0 in (1.75, 12.25):
        box([x0, 0.4, Z0 + 1.0], [x0 + 2.0, 1.0, Z1 - 0.5], 'rail')
    # the curl: boards end to end, each turned 22.5 degrees more than the last, curling up and back over the front
    y, z = 1.0, Z0
    seg = 1.25
    tip = None
    for k in range(9):
        phi = 22.5 * (k + 1)
        rad = math.radians(phi)
        L = seg + 0.45  # a little overlap so the outside of the bend stays closed
        if phi <= 45.0:
            fr, to, ang = [1.5, y, z - L], [14.5, y + 1.0, z], phi
        elif phi <= 135.0:
            fr, to, ang = [1.5, y, z - 1.0], [14.5, y + L, z], phi - 90.0
        else:
            fr, to, ang = [1.5, y, z], [14.5, y + 1.0, z + L], -(180.0 - phi)
        box(fr, to, 'deck', rot={'angle': ang, 'axis': 'x', 'origin': [8.0, y, z]})
        y += math.sin(rad) * seg
        z -= math.cos(rad) * seg
        tip = (y, z)
    # the bar that closes the curl, lashed back to the first crossbar
    box([0.75, tip[0] + 0.2, tip[1] - 0.2], [15.25, tip[0] + 1.3, tip[1] + 1.3], 'bar', along_z=False)
    for x0 in (2.0, 13.25):
        box([x0, 2.0, tip[1] + 0.5], [x0 + 0.75, 2.6, 7.0], 'rope')
    # rails on posts, rope along the rails
    for x0 in (0.5, 14.5):
        for zp in (7.0, 17.0, 27.0):
            box([x0 + 0.25, 2.7, zp], [x0 + 0.75, 3.5, zp + 0.6], 'rail', along_z=False)
        box([x0, 3.5, 6.0], [x0 + 1.0, 4.4, Z1 - 1.0], 'rail')
        box([x0 + 0.2, 4.4, 6.0], [x0 + 0.8, 4.8, Z1 - 1.0], 'rope')
    box([1.0, 2.0, Z1 - 1.2], [15.0, 3.0, Z1], 'bar', along_z=False)
    # centre the 2-block-long toboggan on the block so the GUI, the hands and the entity all turn about its middle
    for e in els:
        e['from'][2] = round(e['from'][2] - 7.0, 3)
        e['to'][2] = round(e['to'][2] - 7.0, 3)
        if 'rotation' in e:
            e['rotation']['origin'][2] = round(e['rotation']['origin'][2] - 7.0, 3)
    m = {'parent': 'block/block', 'textures': t, 'elements': els, 'display': {
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.44, 0.44, 0.44]},
        'ground': {'translation': [0, 2, 0], 'scale': [0.3, 0.3, 0.3]},
        'fixed': {'rotation': [0, 90, 0], 'scale': [0.5, 0.5, 0.5]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.3, 0.3, 0.3]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 1, 0], 'scale': [0.32, 0.32, 0.32]},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 1, 0], 'scale': [0.32, 0.32, 0.32]}}}
    R.save_json(m, A, 'models', 'item', 'sled.json')


def main():
    entity()
    rope_tex()
    R.save_png(R.sprite(sprite), 'item', 'sled.png')
    model()
    R.save_json({"type": "minecraft:crafting_shaped", "category": "transportation", "pattern": ["S  ", "PPP", "PPP"],
                 "key": {"S": {"item": "minecraft:string"}, "P": {"tag": "minecraft:planks"}}, "result": {"id": "frontierhunts:sled", "count": 1}},
                D, 'recipe', 'sled.json')
    R.save_json({"parent": "minecraft:recipes/root", "criteria": {
        "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": "frontierhunts:sled"}},
        "has_material_0": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": ["minecraft:string"]}]}}},
        "requirements": [["has_material_0", "has_the_recipe"]], "rewards": {"recipes": ["frontierhunts:sled"]}},
        D, 'advancement', 'recipes', 'unlock', 'sled.json')


if __name__ == '__main__':
    main()
