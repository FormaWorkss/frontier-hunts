#!/usr/bin/env python3
"""[outfitter] 'Before' contact sheets: the worn gear exactly as master (1650414) builds it.
usage: python3 tools/outfitter/before.py <out_dir>"""
import os, re, sys, math, zipfile, io
import numpy as np
from PIL import Image
sys.path.insert(0, os.path.dirname(__file__))
import mcr, wear

MASTER = '/home/claude/fh'
JAR = '/home/claude/fh/merged62g8.jar'
_z = zipfile.ZipFile(JAR)


def jar_img(p):
    return Image.open(io.BytesIO(_z.read(p))).convert('RGBA')


def humanoid_mesh(defo, tw=64, th=32, head_def=None):
    """HumanoidModel.createMesh(def, 0) parts (no hat)."""
    P = wear.empty_parts()
    P['head'].cubes = [mcr.Cube(0, 0, -4, -8, -4, 8, 8, 8, head_def if head_def is not None else defo)]
    P['body'].cubes = [mcr.Cube(16, 16, -4, 0, -2, 8, 12, 4, defo)]
    P['right_arm'].cubes = [mcr.Cube(40, 16, -3, -2, -2, 4, 12, 4, defo)]
    P['left_arm'].cubes = [mcr.Cube(40, 16, -1, -2, -2, 4, 12, 4, defo, mirror=True)]
    P['right_leg'].cubes = [mcr.Cube(0, 16, -2, 0, -2, 4, 12, 4, defo)]
    P['left_leg'].cubes = [mcr.Cube(0, 16, -2, 0, -2, 4, 12, 4, defo, mirror=True)]
    return P


def garment_layer(kind, slot):
    src = open(os.path.join(MASTER, 'src/com/formaworks/frontierhunts/survival/client/GarmentGeometry.java')).read()
    m = re.search(r'case "%s" -> new float\[\]\[\]\{(.*?)\};' % kind, src, re.S)
    rows = re.findall(r'\{([^{}]*)\}', m.group(1))
    P = wear.empty_parts()
    for r in rows:
        b = [float(x.strip().rstrip('F')) for x in r.split(',')]
        part, u, v, x, y, z, w, h, d, inf = b
        part = int(part)
        if part == 0:
            P['head'].cubes.append(mcr.Cube(u, v, x, y, z, w, h, d, inf))
        elif part == 1:
            P['body'].cubes.append(mcr.Cube(u, v, x, y, z, w, h, d, inf))
        elif part == 2:
            P['right_arm'].cubes.append(mcr.Cube(u, v, x, y, z, w, h, d, inf))
            P['left_arm'].cubes.append(mcr.Cube(u, v, -(x + w), y, z, w, h, d, inf, mirror=True))
        else:
            P['right_leg'].cubes.append(mcr.Cube(u, v, x, y, z, w, h, d, inf))
            P['left_leg'].cubes.append(mcr.Cube(u, v, -(x + w), y, z, w, h, d, inf, mirror=True))
    tex = Image.open(os.path.join(MASTER, 'patch/assets/frontierhunts/textures/models/armor/survival/%s.png' % kind))
    return wear.Layer(tex, 64, 64, P, wear.SLOT_PARTS[slot], name=kind)


def coverall_layer(style):
    P = humanoid_mesh(0.32, head_def=0.55)
    return wear.Layer(jar_img('assets/frontierhunts/textures/entity/coverall/%s.png' % style), 64, 32, P, wear.PARTS, name=style)


def carbon_layers():
    outer = humanoid_mesh(1.0)
    inner = humanoid_mesh(0.5)
    t1 = jar_img('assets/frontierhunts/textures/models/armor/carbon_layer_1.png')
    t2 = jar_img('assets/frontierhunts/textures/models/armor/carbon_layer_2.png')
    return [wear.Layer(t2, 64, 32, inner, ('body', 'right_leg', 'left_leg')),
            wear.Layer(t1, 64, 32, outer, ('head', 'body', 'right_arm', 'left_arm'))]


def ghillie_parts(pattern):
    """Port of client/GhillieModel.build (master)."""
    P = humanoid_mesh(0.38)
    P['head'].cubes = [mcr.Cube(0, 0, -4.6, -8.7, -4.5, 9.2, 1.0, 9.2), mcr.Cube(0, 0, -4.6, -7.7, 3.7, 9.2, 8.4, 1.0),
                       mcr.Cube(0, 0, -4.6, -7.7, -4.4, 1.0, 8.4, 8.1), mcr.Cube(0, 0, 3.6, -7.7, -4.4, 1.0, 8.4, 8.1)]
    grassy = pattern in ('grassland', 'wetland')

    def foliage(part, ox, rad, y0, span, n, head):
        for i in range(n):
            a = i * 2.399963
            mm = max(abs(math.cos(a)), abs(math.sin(a)))
            x = ox + math.cos(a) / mm * rad
            z = math.sin(a) / mm * (4.8 if head else 2.5)
            if head and z < -2.3:
                continue
            y = y0 + (i * 0.41421357) % 1.0 * span
            cubes = [mcr.Cube(i * 7 % 48, i * 3 % 20, -0.35 if grassy else -0.6, 0, -0.08, 0.7 if grassy else 1.2, 3.5 if grassy else 2.7, 0.16)]
            if not grassy:
                cubes.append(mcr.Cube(i * 7 % 48, i * 3 % 20, -1.15, 0.8, -0.1, 2.3, 0.65, 0.2))
            part.child('leaf_%d' % i, cubes, (x, y, z), (math.sin(a) * 0.25, -a, math.cos(a) * 0.25))
    foliage(P['head'], 0, 4.8, -8, 7, 36, True)
    foliage(P['body'], 0, 4.6, 0, 12, 80, False)
    foliage(P['left_arm'], 1, 2.5, -2, 9, 28, False)
    foliage(P['right_arm'], -1, 2.5, -2, 9, 28, False)
    foliage(P['left_leg'], 0, 2.5, 0, 10, 30, False)
    foliage(P['right_leg'], 0, 2.5, 0, 10, 30, False)
    snow = pattern == 'snow'
    u, v = (42, 20) if snow else (35, 18)
    P['body'].child('chest_lacing', [mcr.Cube(u, v, -3.55, 1.1, -2.48, 7.1, 0.32, 0.18), mcr.Cube(u, v, -3.55, 5.2, -2.48, 7.1, 0.28, 0.18),
                                     mcr.Cube(u, v, -0.16, 0.1, -2.5, 0.32, 10.7, 0.16)])
    P['body'].child('shoulder_mantle', [mcr.Cube(4, 17, -5.3, -0.45, -2.7, 10.6, 1.15, 5.4)])
    for n in ('left_arm', 'right_arm', 'left_leg', 'right_leg'):
        P[n].child('field_ties', [mcr.Cube(44, 10, -2.45, 3.0, -2.45, 4.9, 0.34, 4.9), mcr.Cube(44, 10, -2.42, 7.0, -2.42, 4.84, 0.28, 4.84)])
    return P


def ghillie_layers(pattern='woodland'):
    path = 'assets/frontierhunts/textures/equipment/shelters/camouflage_v1.png' if pattern == 'woodland' else 'assets/frontierhunts/textures/equipment/ghillie/%s.png' % pattern
    t = mcr.tex_array(jar_img(path))
    # vanilla armour layer: trousers = inner model piece (same GhillieModel instance, legs+body), jacket = body+arms, hood = head
    return [wear.Layer(t, 64, 32, ghillie_parts(pattern), wear.PARTS)]


def pack_extras(chest_worn=True):
    parts = mcr.load_fheq(_z.read('assets/frontierhunts/models/equipment/hunter_pack_field.fheq'))
    tex = mcr.tex_array(jar_img('assets/frontierhunts/textures/equipment/packs/material.png'))
    off = 0.06 if chest_worn else 0.0
    # PackLayer: body.translateAndRotate; translate(0, .335, .2723 + off); rotX 180; scale(1.16, 1.17, .82)  (block units)
    Mloc = mcr.mat_scale(16, 16, 16) @ mcr.mat_translate(0, 0.335, 0.2723 + off) @ mcr.mat_rot('x', 180) @ mcr.mat_scale(1.16, 1.17, 0.82)
    def fn(sc, P, M):
        body = P['body']
        Mb = M @ body.local()
        mcr.mesh_emitter(parts, tex, Mloc)(sc, Mb)
    return fn


POSE_SET = ['walk_a', 'sprint', 'sneak', 'swim', 'prone', 'riding', 'sleeping', 'bow', 'rifle']


def main(out):
    os.makedirs(out, exist_ok=True)
    jobs = [
        ('fur_hat+buckskin', lambda s: [garment_layer('buckskin_leggings', 'legs'), garment_layer('fur_mukluks', 'feet'),
                                        garment_layer('buckskin_coat', 'chest'), garment_layer('fur_hat', 'head')]),
        ('bear_fur_coat', lambda s: [garment_layer('bear_fur_coat', 'chest')]),
        ('hide_robe', lambda s: [garment_layer('hide_robe', 'chest')]),
        ('timber_coveralls', lambda s: [coverall_layer('timber_camo_coveralls')]),
        ('blaze_coveralls', lambda s: [coverall_layer('blaze_camo_coveralls')]),
        ('scent_suit', lambda s: [coverall_layer('scent_suit')]),
        ('carbon_layer', lambda s: carbon_layers()),
        ('ghillie_woodland', lambda s: ghillie_layers('woodland')),
        ('ghillie_grassland', lambda s: ghillie_layers('grassland')),
    ]
    for name, fn in jobs:
        ims = wear.sheet(name, fn, POSE_SET)
        wear.grid(ims, 6).save(os.path.join(out, 'before_%s.png' % name))
        print(name)
    ims = wear.sheet('pack+coat', lambda s: [garment_layer('bear_fur_coat', 'chest')], ['walk_a', 'sneak', 'riding'],
                     views=('back', 'side', 'back34'), extras=pack_extras(True))
    wear.grid(ims, 6).save(os.path.join(out, 'before_pack.png'))


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '/tmp/claude-0/ofx/before')
