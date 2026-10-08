#!/usr/bin/env python3
"""[clothing] Contact sheets of worn-gear COMBINATIONS on the vanilla player (wide and slim), with the in-game layering:
carbon base layer under outer clothing, coverall legs under leggings, thigh tufts / fringe under a long coat's hem, shin
tufts in boots, cuffs under sewn-on mittens (the same rules as OutfitClient.Armour, CoverallLayer, BaseLayerRender and
tools/clothing/audit.py).

usage: python3 tools/clothing/preview_combos.py <repo> <out dir> [hd|vanilla]
"""
import os, sys
REPO = sys.argv[1] if len(sys.argv) > 1 else '.'
OUT = sys.argv[2] if len(sys.argv) > 2 else '/tmp/claude-0/clothing_previews'
LOOK = sys.argv[3] if len(sys.argv) > 3 else 'hd'
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'outfitter'))
sys.argv = ['preview.py', REPO, OUT, LOOK]
import preview as PV
import wear, mcr
import outfit as OF

BASE = {'hood': 'carbon/hood', 'top': 'carbon/jacket', 'trousers': 'carbon/trousers', 'suit': 'coverall/scent_suit'}


def outfit(head=None, chest=None, legs=None, feet=None, base=(), mittens=False):
    def fn(slim):
        L = []
        coverall = chest is not None and chest.startswith('coverall/')
        long_coat = chest in OF.HEM_ON_LEGS
        robe = chest == 'garment/hide_robe'
        head_cov, torso_cov, legs_cov = head is not None or coverall, chest is not None, legs is not None or coverall
        flags = dict(hide_under_hem=long_coat, hide_under_boot=feet is not None, hide_under_mitten=mittens, hide_under_long=robe)
        if 'suit' in base:
            vis = (('head',) if not head_cov else ()) + (('body', 'right_arm', 'left_arm') if not torso_cov else ()) + \
                  (('right_leg', 'left_leg') if not legs_cov else ())
            if vis:
                L.append(PV.layer(BASE['suit'], slim, vis, hide_under_hem=long_coat))
        else:
            if 'hood' in base and not head_cov:
                L.append(PV.layer(BASE['hood'], slim, ('head',)))
            if 'top' in base and not torso_cov:
                L.append(PV.layer(BASE['top'], slim, ('body', 'right_arm', 'left_arm')))
            if 'trousers' in base and not legs_cov:
                L.append(PV.layer(BASE['trousers'], slim, (('body',) if not torso_cov else ()) + ('right_leg', 'left_leg'), hide_under_hem=long_coat))
        if legs:
            L.append(PV.layer(legs, slim, (('body',) if not torso_cov else ()) + ('right_leg', 'left_leg'), **flags))
        if feet:
            L.append(PV.layer(feet, slim, ('right_leg', 'left_leg')))
        if chest:
            if coverall:
                vis = (('head',) if head is None else ()) + ('body', 'right_arm', 'left_arm') + (('right_leg', 'left_leg') if legs is None else ())
                L.append(PV.layer(chest, slim, vis))
            else:
                vis = ('body', 'right_arm', 'left_arm') + (('right_leg', 'left_leg') if long_coat else ())
                L.append(PV.layer(chest, slim, vis, hide_under_mitten=mittens))
            if mittens:
                L.append(PV.layer('extra/mittens', slim, ('right_arm', 'left_arm')))
        if head:
            L.append(PV.layer(head, slim, ('head',)))
        return L
    return fn


SETS = [
    ('carbon base layer alone', outfit(base=('hood', 'top', 'trousers'))),
    ('carbon base under ghillie jacket', outfit(chest='ghillie/woodland_jacket', base=('hood', 'top', 'trousers'))),
    ('fur hat + ghillie jacket + buckskin leggings', outfit(head='garment/fur_hat', chest='ghillie/woodland_jacket', legs='garment/buckskin_leggings',
                                                         base=('hood', 'top', 'trousers'))),
    ('scent suit under bear coat, mukluks, mittens', outfit(chest='garment/bear_fur_coat', feet='garment/fur_mukluks', base=('suit',), mittens=True)),
    ('timber coveralls + buckskin leggings + mukluks', outfit(chest='coverall/timber_camo_coveralls', legs='garment/buckskin_leggings',
                                                            feet='garment/fur_mukluks')),
    ('hide robe + leggings + mukluks + mittens', outfit(chest='garment/hide_robe', legs='garment/buckskin_leggings', feet='garment/fur_mukluks',
                                                      mittens=True, head='garment/fur_hat')),
    ('ghillie trousers + mukluks + buckskin coat', outfit(chest='garment/buckskin_coat', legs='ghillie/snow_trousers', feet='garment/fur_mukluks',
                                                         head='ghillie/snow_hood')),
    ('scent suit under ghillie hood + trousers', outfit(head='ghillie/grassland_hood', legs='ghillie/grassland_trousers', base=('suit',))),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, (title, fn) in enumerate(SETS):
        ims = []
        for v in ('front', 'side', 'back'):
            ims.append(wear.label(wear.shot(mcr.POSES['idle'], v, False, fn(False)), '%s - %s' % (title, v)))
        for p, v in (('walk_a', 'side'), ('sneak', 'side'), ('riding', 'side'), ('swim', 'front34'), ('sprint_b', 'back34')):
            ims.append(wear.label(wear.shot(mcr.POSES[p], v, False, fn(False)), p))
        ims.append(wear.label(wear.shot(mcr.POSES['idle'], 'front34', True, fn(True)), 'slim front'))
        ims.append(wear.label(wear.shot(mcr.POSES['walk_a'], 'back34', True, fn(True)), 'slim walk'))
        path = os.path.join(OUT, 'combo_%d_%s%s.png' % (i + 1, title.split(' ')[0].replace('+', ''), '' if LOOK == 'hd' else '_vanilla'))
        wear.grid(ims, 5).save(path)
        print(path)


if __name__ == '__main__':
    main()
