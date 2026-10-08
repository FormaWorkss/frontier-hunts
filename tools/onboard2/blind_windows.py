#!/usr/bin/env python3
"""[onboard2] Measures the window openings of the mod's blinds from their real collision shapes and checks the seated
eye level of every blind seat against them.

  * Hub ground blind: HubGroundBlind.construction() - wall boxes over the height bands {0, 1.2, 1.8, 1.93, 2.04};
    the window band (no wall) is 1.2-1.8 above the floor (the ground the blind stands on).
  * Elevated tower blinds: TowerBlindShapeData (base jar), dumped through a tiny Java helper, then sampled along the
    cabin walls: floor top, sill, window top.
python3 tools/onboard2/blind_windows.py <repo>
"""
import json
import os
import subprocess
import sys
import tempfile

R = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else '.')
sys.path.insert(0, os.path.join(R, 'tools/onboard2'))
import seat_geom as G  # noqa: E402

JAVA = r'''package com.formaworks.frontierhunts.expedition;
public class DumpTower { public static void main(String[] a){ double[][][][] b=TowerBlindShapeData.boxes(); int[][] p=TowerBlindShapeData.PARTS;
 StringBuilder s=new StringBuilder("[");
 for(int i=0;i<p.length;i++){ if(i>0)s.append(","); s.append("{\"p\":["+p[i][0]+","+p[i][1]+","+p[i][2]+"],\"s\":[");
  for(int j=0;j<b[i][1].length;j++){ if(j>0)s.append(","); s.append(java.util.Arrays.toString(b[i][1][j]));}
  s.append("]}");}
 s.append("]"); System.out.println(s);}}
'''


def tower_data():
    d = tempfile.mkdtemp(prefix='fhwin', dir='/tmp/claude-0')
    pk = os.path.join(d, 'com/formaworks/frontierhunts/expedition')
    os.makedirs(pk)
    open(os.path.join(pk, 'DumpTower.java'), 'w').write(JAVA)
    jar = '/home/claude/fh/orig62.jar'
    subprocess.run(['javac', '-proc:none', '-nowarn', '-cp', jar, '-d', d, os.path.join(pk, 'DumpTower.java')], check=True, capture_output=True)
    out = subprocess.run(['java', '-cp', d + ':' + jar, 'com.formaworks.frontierhunts.expedition.DumpTower'], check=True, capture_output=True, text=True).stdout
    return json.loads(out)


def solid(B, x, y, z):
    return any(b[0] <= x <= b[3] and b[1] <= y <= b[4] and b[2] <= z <= b[5] for b in B)


def column(B, axis, c, h, lo, hi):
    """Transitions along y on a wall plane: [(y, solid)]."""
    out, prev, y = [], None, lo
    while y <= hi:
        s = solid(B, *((c, y, h) if axis == 'x' else (h, y, c)))
        if s != prev:
            out.append((round(y, 3), s))
            prev = s
        y += 0.005
    return out


def window(trans):
    """First open band above the floor: (floor top, sill, top)."""
    floor = trans[1][0] if trans and trans[0][1] else trans[0][0]
    opens = [(trans[i][0], trans[i + 1][0]) for i in range(len(trans) - 1) if not trans[i][1]]
    # skip the open gap right above the floor (door / interior) -> first band starting > 1 above the floor
    band = next((o for o in opens if o[0] - floor > 1.0), None)
    return floor, band


def main():
    rows = []
    rows.append(('ground blind (hub)', 0.0, (1.2, 1.8)))
    data = tower_data()
    for name, rng, plane, floor_hint in (('small tower blind', range(0, 68), ('x', -0.64), 3.6), ('big tower blind', range(68, len(data)), ('x', -1.2), 4.0)):
        B = []
        for i in rng:
            ox, oy, oz = data[i]['p']
            for b in data[i]['s']:
                B.append((b[0] + ox, b[1] + oy, b[2] + oz, b[3] + ox, b[4] + oy, b[5] + oz))
        t = column(B, plane[0], plane[1], 0.5, floor_hint, floor_hint + 3.0)
        floor = floor_hint
        opens = [(t[i][0], t[i + 1][0]) for i in range(len(t) - 1) if not t[i][1] and t[i][0] > floor + 0.5]
        band = opens[0]
        rows.append((name, floor, (round(band[0] - floor, 3), round(band[1] - floor, 3))))
    seats = [('Blind Swivel Chair', G.EYE_GROUND), ('Stump Seat / Camp Chair / Trail Bench', G.EYE_GROUND),
             ('Tower Swivel Chair (standard / sunk)', G.EYE_TOWER), ('Tower Swivel Chair (raised)', G.EYE_TOWER_HIGH)]
    print('%-22s %-8s %-18s %s' % ('blind', 'floor', 'window above floor', 'centre'))
    for n, f, (a, b) in rows:
        print('%-22s %-8s %-18s %.3f' % (n, f, '%.3f - %.3f' % (a, b), (a + b) / 2))
    print()
    for n, eye in seats:
        y = G.seat_y(eye)
        print('%-40s seat point %.2f px above its floor -> eye %.3f blocks' % (n, y, (y + G.EYE - G.ATTACH) / 16))
    want = {'ground blind (hub)': G.EYE_GROUND, 'small tower blind': G.EYE_TOWER, 'big tower blind': G.EYE_TOWER_HIGH}
    ok = True
    for n, f, (a, b) in rows:
        c = (a + b) / 2
        e = want[n]
        good = abs(e - c) < 0.01 and a + 0.15 < e < b - 0.15
        ok &= good
        print('%-22s chair eye %.3f vs window centre %.3f  %s' % (n, e, c, 'OK' if good else 'MISMATCH'))
    # Frontier Structures lookout towers: an open deck behind lookout_deck_rail (no window); the seated eye must clear
    # the rail's top rail (model elements, px) so the view over it is free
    rail = os.path.join(R, 'fs/resources/assets/frontierstructures/models/block/lookout_deck_rail.json')
    if os.path.exists(rail):
        top = max(e['to'][1] for e in json.load(open(rail))['elements']) / 16.0
        for n, e in (('lookout deck (any seat)', G.EYE_GROUND), ('lookout deck (tower chair)', G.EYE_TOWER)):
            good = e > top + 0.15
            ok &= good
            print('%-22s chair eye %.3f vs deck rail top %.3f  %s' % (n, e, top, 'OK (clear view over the rail)' if good else 'BLOCKED'))
    sys.exit(0 if ok else 1)


if __name__ == '__main__':
    main()
