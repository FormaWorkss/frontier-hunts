#!/usr/bin/env python3
"""[outfitter] Worn-gear figure rendering on top of mcr.py: humanoid layers (armour-style models that copy the player's
part poses), contact sheets of views x poses."""
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFont
import mcr

PARTS = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')
SLOT_PARTS = {
    'head': ('head',),
    'chest': ('body', 'right_arm', 'left_arm'),
    'legs': ('body', 'right_leg', 'left_leg'),
    'feet': ('right_leg', 'left_leg'),
    'all': PARTS,
}
PIVOTS = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
          'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}


class Layer:
    """A humanoid-shaped model: parts with cubes (+children), one texture."""
    def __init__(self, tex, tw, th, parts, visible=PARTS, tint=(1, 1, 1), name=''):
        self.tex = mcr.tex_array(tex) if not isinstance(tex, np.ndarray) else tex
        self.tw, self.th = tw, th
        self.parts = parts  # dict name -> mcr.Part
        self.visible = visible
        self.tint = tint
        self.name = name


def empty_parts():
    return {n: mcr.Part(n, [], PIVOTS[n]) for n in PARTS}


def figure(pose, slim=False, layers=(), skin=True, extras=None):
    """Scene with the posed player + layers. extras(scene, skin_parts, M_entity) adds attached meshes."""
    sc = mcr.Scene()
    P = mcr.posed_humanoid(pose, slim)
    M = mcr.entity_matrix(pose)
    if skin:
        st = mcr.tex_array(mcr.mc_image('assets/minecraft/textures/entity/player/%s/%s.png' % (('slim', 'alex') if slim else ('wide', 'steve'))))
        for n in PARTS:
            sc.add_part(P[n], st, 64, 64, M)
    for L in layers:
        for n in PARTS:
            if n not in L.visible or n not in L.parts:
                continue
            part = L.parts[n]
            part.copy_pose(P[n])
            sc.add_part(part, L.tex, L.tw, L.th, M, L.tint)
    if extras:
        extras(sc, P, M)
    return sc


VIEWS = {'front': (0, 6), 'front34': (35, 10), 'side': (90, 4), 'back': (180, 6), 'back34': (215, 12)}


def frame_for(pose):
    if pose.entity in ('swim', 'crawl', 'sleep'):
        return dict(size=(360, 360), scale=7.5, center=(0, 2))
    if pose.entity == 'ride':
        return dict(size=(300, 340), scale=9.0, center=(0, 13))
    return dict(size=(260, 360), scale=10.0, center=(0, 16))


def shot(pose, view, slim=False, layers=(), extras=None, skin=True, frame=None):
    sc = figure(pose, slim, layers, skin, extras)
    yaw, pitch = VIEWS[view] if isinstance(view, str) else view
    if pose.entity in ('swim', 'crawl', 'sleep') and isinstance(view, str):
        pitch = 35 if view != 'side' else 10
    V = mcr.camera(yaw, pitch)
    fr = frame or frame_for(pose)
    return mcr.render(sc, V, **fr)


def label(img, text):
    d = ImageDraw.Draw(img)
    try:
        f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 13)
    except Exception:
        f = None
    d.rectangle([0, 0, img.width, 17], fill=(30, 32, 34))
    d.text((4, 2), text, fill=(230, 230, 230), font=f)
    return img


def grid(images, cols, pad=4, bg=(24, 26, 28)):
    w = max(i.width for i in images); h = max(i.height for i in images)
    rows = (len(images) + cols - 1) // cols
    out = Image.new('RGB', (cols * (w + pad) + pad, rows * (h + pad) + pad), bg)
    for k, im in enumerate(images):
        out.paste(im, (pad + (k % cols) * (w + pad), pad + (k // cols) * (h + pad)))
    return out


def sheet(title, layers_fn, poses, views=('front', 'side', 'back'), slim=False, extras=None):
    """layers_fn(slim) -> layers. One row of views (idle) + poses (front34)."""
    ims = []
    L = layers_fn(slim)
    for v in views:
        ims.append(label(shot(mcr.POSES['idle'], v, slim, L, extras), '%s idle %s' % (title, v)))
    for p in poses:
        pose = mcr.POSES[p]
        v = 'side' if p in ('sprint', 'sprint_b', 'walk_a', 'sneak', 'sneak_walk', 'riding') else 'front34'
        if p in ('swim', 'prone', 'sleeping'):
            v = 'front34'
        ims.append(label(shot(pose, v, slim, L, extras), '%s %s' % (title, p)))
    return ims
