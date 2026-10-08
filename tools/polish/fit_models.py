#!/usr/bin/env python3
"""[polish] Inventory-slot fit for block items whose models had no (or a poor) GUI transform.

    python3 tools/polish/fit_models.py [jar]     (repo root; jar = a built Frontier Hunts jar)

* Lookout stair rail / deck rail / brace / cross brace (frontierhunts: and frontierstructures: copies): the block models
  carried no display transforms at all, so Minecraft drew them at 1:1 straight from the side - a thin dark bar running
  off the top and bottom of the slot. They now get a 3/4 view across the rails, centred and sized like the timber
  braces next to them, and hand / ground / frame transforms scaled to their size.
* Game pole, trail camera, cabin lantern, deadfall log: re-fitted (the game pole showed one 5x8 px piece of its frame,
  the trail camera sat off-centre at half size, the lantern was a 6 px speck, the log lay in the bottom of the slot).
* River pebbles / stones / boulders (OBJ models): sat in the bottom third of the slot at half size; now centred and
  sized like the neighbouring blocks (pebbles a little smaller than stones, stones smaller than boulders).
"""
import glob, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import icon_preview as ip  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, '..', '..'))
FH_ITEMS = os.path.join(ROOT, 'patch', 'assets', 'frontierhunts', 'models', 'item')
FS_ITEMS = os.path.join(ROOT, 'fs', 'resources', 'assets', 'frontierstructures', 'models', 'item')
VIEW = [25, 110, 0]


def r(v):
    return round(float(v), 3)


def other_views(k):
    """Vanilla block-item transforms for the other perspectives, scaled by k (model size relative to a block)."""
    return {
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [r(0.25 * k)] * 3},
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [r(0.5 * k)] * 3},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [r(0.375 * k)] * 3},
        'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [r(0.375 * k)] * 3},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [r(0.4 * k)] * 3},
        'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0], 'scale': [r(0.4 * k)] * 3},
        'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [r(k)] * 3},
    }


def write(path, data):
    with open(path, 'w') as f:
        json.dump(data, f, indent=1)
        f.write('\n')


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.join(ROOT, '.build', '*.jar')))[-1]
    src = ip.Src(os.path.join(ROOT, 'fs', 'resources'), jar)
    os.makedirs(FH_ITEMS, exist_ok=True)

    # --- lookout timbers: a low 3/4 view across the rails (the angle the neighbouring timber braces use, turned so
    # the rails and diagonals show broadside), each centred and filling the slot like the braces around them
    for n in ['lookout_stair_rail', 'lookout_deck_rail', 'lookout_brace', 'lookout_cross_brace']:
        g = ip.fit(src, 'frontierhunts:block/' + n, VIEW, 14.0)
        m = ip.resolve(src, 'frontierhunts:block/' + n)
        pts = [p for q in ip.model_quads(src, m) for p in q[0]]
        span = max(max(p[i] for p in pts) - min(p[i] for p in pts) for i in range(3))
        disp = dict(other_views(min(1.0, 16.0 / span)))
        disp['gui'] = g
        write(os.path.join(FH_ITEMS, n + '.json'), {'parent': 'frontierhunts:block/' + n, 'gui_light': 'side', 'display': disp})
        write(os.path.join(FS_ITEMS, n + '.json'), {'parent': 'frontierstructures:block/' + n, 'gui_light': 'side', 'display': disp})
        print(n, g, 'k', round(16.0 / span, 3))

    # --- river stones (neoforge:obj): keep their other transforms, re-fit the slot view
    for kind, target in (('pebbles', 12.0), ('stone', 13.5), ('boulder', 15.0)):
        for prefix in ('', 'mossy_'):
            n = f'{prefix}river_{kind}'
            g = ip.fit(src, f'frontierhunts:block/{n}_0', [25, -35, 0], target)
            write(os.path.join(FH_ITEMS, n + '.json'), {'parent': f'frontierhunts:block/{n}_0', 'display': {'gui': g}})
            print(n, g)

    refit(src, 'game_pole', 14.5)
    refit(src, 'trail_camera', 13.0)
    refit(src, 'cabin_lantern', 11.0)
    refit(src, 'deadfall_log', 14.0)


def refit(src, name, target, rotation=None):
    """Keeps an item model as it is and only re-fits its display.gui (same view angle unless given)."""
    data = src.read(f'assets/frontierhunts/models/item/{name}.json')
    j = json.loads(data)
    rot = rotation or ((j.get('display') or {}).get('gui') or {}).get('rotation') or [30, 225, 0]
    parent = j['parent']
    g = ip.fit(src, parent, rot, target)
    j.setdefault('display', {})['gui'] = g
    write(os.path.join(FH_ITEMS, name + '.json'), j)
    print(name, g)


if __name__ == '__main__':
    main()
