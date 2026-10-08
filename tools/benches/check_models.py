#!/usr/bin/env python3
"""[benchart] Sanity checks for the bench models: coordinate limits, rotation rules, element counts, uv range,
coplanar same-facing overlapping faces (z-fighting) across the joined left+right halves.

python3 tools/benches/check_models.py <repo> [bench ids]
"""
import json
import os
import sys

AX = {'north': (2, 0), 'south': (2, 1), 'west': (0, 0), 'east': (0, 1), 'down': (1, 0), 'up': (1, 1)}


def load(A, name, dx=0):
    m = json.load(open(os.path.join(A, 'models', 'block', name + '.json')))
    out = []
    for e in m['elements']:
        f = [e['from'][0] + dx, e['from'][1], e['from'][2]]
        t = [e['to'][0] + dx, e['to'][1], e['to'][2]]
        out.append((f, t, e))
    return m, out


def main():
    repo = os.path.abspath(sys.argv[1])
    A = os.path.join(repo, 'patch', 'assets', 'frontierhunts')
    ids = sys.argv[2:] or ['frontier_workbench', 'gunsmith_bench', 'reloading_bench']
    bad = 0
    for bid in ids:
        els = []
        for part, dx in (('left', 0), ('right', 16)):
            m, es = load(A, f'{bid}_{part}', dx)
            print(f'{bid}_{part}: {len(m["elements"])} elements, textures {m["textures"]}')
            for f, t, e in es:
                fl, tl = e['from'], e['to']
                if min(fl + tl) < -16 or max(fl + tl) > 32:
                    print('  OUT OF RANGE', fl, tl); bad += 1
                if min(fl[0], fl[2]) < 0 or max(tl[0], tl[2]) > 16 or fl[1] < 0 or tl[1] > 26:
                    print('  outside the half', fl, tl); bad += 1
                r = e.get('rotation')
                if r and r['angle'] not in (0, 22.5, -22.5, 45, -45):
                    print('  bad angle', r); bad += 1
                for fn, fd in e['faces'].items():
                    if min(fd['uv']) < 0 or max(fd['uv']) > 16:
                        print('  uv out of range', fd['uv']); bad += 1
            els += es
        # z-fighting: coplanar, same direction, overlapping faces of unrotated elements
        faces = []
        for f, t, e in els:
            if e.get('rotation'):
                continue
            for fn in e['faces']:
                ax, side = AX[fn]
                plane = t[ax] if side else f[ax]
                o = [i for i in range(3) if i != ax]
                faces.append((fn, round(plane, 4), (f[o[0]], t[o[0]], f[o[1]], t[o[1]])))
        n = 0
        for i in range(len(faces)):
            for j in range(i + 1, len(faces)):
                a, b = faces[i], faces[j]
                if a[0] != b[0] or a[1] != b[1]:
                    continue
                ra, rb = a[2], b[2]
                ox = min(ra[1], rb[1]) - max(ra[0], rb[0])
                oy = min(ra[3], rb[3]) - max(ra[2], rb[2])
                if ox > 1e-4 and oy > 1e-4:
                    n += 1
                    if n <= 12:
                        print('  z-fight', a, b)
        print(f'  {bid}: {n} coplanar overlaps')
        bad += n
    print('problems:', bad)


if __name__ == '__main__':
    main()
