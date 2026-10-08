#!/usr/bin/env python3
"""[clothing] Clipping / z-fighting audit of every pair of worn garments, with the exact visibility rules the game uses:

  * armour slots: head, chest (coats, coveralls, ghillie jackets), legs, feet - each item's outfit model, the slot's parts
    (chest: body + arms, + thighs for long coats; legs: body + legs; feet: legs; coverall: everything, hood only with the
    head slot empty, legs only with the legs slot empty), a legs item's waistband hidden while a chest garment covers the
    torso, leggings fringe / tufts tagged under_hem hidden under a long coat;
  * the carbon base layer (clothing.client.BaseLayerRender): each piece only where no outer garment covers it;
  * sewn extras (mittens, fur collar) on the chest piece; the player's own skin overlay (hat 0.5, jacket/sleeves/pants 0.25).

Two checks per pair of boxes on the same model part (wide AND slim arms):
  Z   coplanar faces: two outward faces on the same side within 0.06 px over more than 0.25 px^2 (z-fighting);
  P   poke-through: a box of the INNER garment pushes out through a side face (x or z) of the OUTER garment's box where the
      two overlap (limb ends - y - are open: a leg leaving a coat hem is fine). Cards (tufts, fringe) use their posed corners.
Layer order per part: skin < base layer < legs slot (waist) < chest < extras (body); skin < base < legs/coverall legs <
feet < long-coat hem (legs); skin < base < chest < mittens (arms); skin < base < head slot / coverall hood (head).

usage: python3 tools/clothing/audit.py [repo]      (exit 1 when anything is flagged; prints one line per finding)
"""
import itertools, math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'outfitter'))
sys.argv = [sys.argv[0], sys.argv[1] if len(sys.argv) > 1 else '.']
import outfit as OF

O, _G, _UV = OF.build()
EPS_Z, AREA_Z, EPS_P = 0.06, 0.25, 0.02
PARTS = ('head', 'body', 'arm', 'arm_slim', 'leg')

# -------------------------------------------------------------------------------------------- garments
HEAD = ['garment/fur_hat'] + ['ghillie/%s_hood' % p for p in OF.GHILLIE]
CHEST = [k for k in O if k.startswith('coverall/') and k != 'coverall/scent_suit'] + \
        ['garment/buckskin_coat', 'garment/bear_fur_coat', 'garment/hide_robe'] + ['ghillie/%s_jacket' % p for p in OF.GHILLIE]
LEGS = ['garment/buckskin_leggings'] + ['ghillie/%s_trousers' % p for p in OF.GHILLIE]
FEET = ['garment/fur_mukluks']
BASE = {'hood': 'carbon/hood', 'top': 'carbon/jacket', 'trousers': 'carbon/trousers', 'suit': 'coverall/scent_suit'}


class Box:
    __slots__ = ('lo', 'hi', 'card', 'tag', 'src', 'pts')

    def __init__(self, lo, hi, card, tag, src, pts=None):
        self.lo, self.hi, self.card, self.tag, self.src, self.pts = lo, hi, card, tag, src, pts


def rot(v, rx, ry, rz):
    x, y, z = v
    # ModelPart: rotationZYX(z, y, x) -> R = Rz Ry Rx
    c, s = math.cos(rx), math.sin(rx); y, z = y * c - z * s, y * s + z * c
    c, s = math.cos(ry), math.sin(ry); x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(rz), math.sin(rz); x, y = x * c - y * s, x * s + y * c
    return x, y, z


def boxes_of(name):
    out = {p: [] for p in PARTS}
    for b in O[name][0]:
        if b.part not in out:
            continue  # left limbs mirror the right ones
        g = b.g
        lo = (b.x - g, b.y - g, b.z - g)
        hi = (b.x + b.w + g, b.y + b.h + g, b.z + b.d + g)
        card = min(b.w, b.h, b.d) == 0
        pts = None
        if card or b.pose:
            # sample the card / posed box surface (7 x 7 x 7 lattice of its corners' span), posed like ModelPart does
            px, py, pz, rx, ry, rz = b.pose or (0, 0, 0, 0, 0, 0)
            n = 7
            lat = [(lo[0] + (hi[0] - lo[0]) * i / (n - 1), lo[1] + (hi[1] - lo[1]) * j / (n - 1), lo[2] + (hi[2] - lo[2]) * k / (n - 1))
                   for i in range(n) for j in range(n) for k in range(n)]
            pts = []
            for q in lat:
                r = rot(q, rx, ry, rz)
                pts.append((r[0] + px, r[1] + py, r[2] + pz))
            lo = tuple(min(p[i] for p in pts) for i in range(3))
            hi = tuple(max(p[i] for p in pts) for i in range(3))
            card = True
        out[b.part].append(Box(lo, hi, card, b.tag, b, pts))
    return out


CACHE = {}


def geo(name):
    if name not in CACHE:
        CACHE[name] = boxes_of(name)
    return CACHE[name]


SKIN = {
    'head': [Box((-4.5, -8.5, -4.5), (4.5, 0.5, 4.5), False, None, 'hat')],
    'body': [Box((-4.25, -0.25, -2.25), (4.25, 12.25, 2.25), False, None, 'jacket')],
    'arm': [Box((-3.25, -2.25, -2.25), (1.25, 10.25, 2.25), False, None, 'sleeve')],
    'arm_slim': [Box((-2.25, -2.25, -2.25), (1.25, 10.25, 2.25), False, None, 'sleeve')],
    'leg': [Box((-2.25, -0.25, -2.25), (2.25, 12.25, 2.25), False, None, 'pants')],
}


# -------------------------------------------------------------------------------------------- what the game draws
def worn_layers(head=None, chest=None, legs=None, feet=None, base=(), extras=False):
    """[(garment name, part, rank, boxes)] for one outfit, with the in-game visibility rules."""
    L = []
    coverall = chest is not None and chest.startswith('coverall/')
    long_coat = chest in OF.HEM_ON_LEGS
    boots = feet is not None
    mitts = extras and chest is not None
    robe = chest == 'garment/hide_robe'
    def give_way(b):
        t = b.tag or ''
        return not (long_coat and 'hem' in t or boots and 'boot' in t or mitts and 'mitten' in t or robe and 'long' in t)
    head_cov = head is not None or coverall
    torso_cov = chest is not None
    legs_cov = legs is not None or coverall
    def add(name, part, rank, keep=lambda b: True):
        bs = [b for b in geo(name)[part] if keep(b)]
        if bs:
            L.append((name, part, rank, bs))
    for part in PARTS:
        L.append(('skin', part, -1, SKIN[part]))
    if head:
        add(head, 'head', 3)
    if chest:
        if coverall:
            if head is None:
                add(chest, 'head', 3)
            if legs is None:
                add(chest, 'leg', 2)
        for part in ('body', 'arm', 'arm_slim'):
            add(chest, part, 3, give_way)
        if long_coat:
            add(chest, 'leg', 5)
    if legs:
        if not torso_cov:
            add(legs, 'body', 2)
        add(legs, 'leg', 2, give_way)
    if feet:
        add(feet, 'leg', 4)
    suit = 'suit' in base
    if suit:
        n = BASE['suit']
        if not head_cov:
            add(n, 'head', 1)
        if not torso_cov:
            add(n, 'body', 2.5)  # a one-piece suit is worn over trousers' waistbands
            add(n, 'arm', 1)
            add(n, 'arm_slim', 1)
        if not legs_cov:
            add(n, 'leg', 1, give_way)
    else:
        if 'hood' in base and not head_cov:
            add(BASE['hood'], 'head', 1)
        if 'top' in base and not torso_cov:
            add(BASE['top'], 'body', 2.5)  # a jacket hangs over the trousers' waistband
            add(BASE['top'], 'arm', 1)
            add(BASE['top'], 'arm_slim', 1)
        if 'trousers' in base and not legs_cov:
            if not torso_cov:
                add(BASE['trousers'], 'body', 1)
            add(BASE['trousers'], 'leg', 1, give_way)
    if extras and chest:
        if chest not in ('garment/bear_fur_coat', 'garment/hide_robe'):  # their own fur ruff: no sewn collar drawn
            add('extra/lining', 'body', 6)
        add('extra/mittens', 'arm', 6)
        add('extra/mittens', 'arm_slim', 6)
    return L


def overlap(a0, a1, b0, b1):
    return max(0.0, min(a1, b1) - max(a0, b0))


def coplanar(A, B):
    if A.card or B.card:
        return None
    for k in range(3):
        o = [i for i in range(3) if i != k]
        area = overlap(A.lo[o[0]], A.hi[o[0]], B.lo[o[0]], B.hi[o[0]]) * overlap(A.lo[o[1]], A.hi[o[1]], B.lo[o[1]], B.hi[o[1]])
        if area <= AREA_Z:
            continue
        if abs(A.lo[k] - B.lo[k]) < EPS_Z or abs(A.hi[k] - B.hi[k]) < EPS_Z:
            return 'xyz'[k], area
    return None


SECTION = {'head': ((-4, 4), (-4, 4)), 'body': ((-4, 4), (-2, 2)), 'arm': ((-3, 1), (-2, 2)), 'arm_slim': ((-2, 1), (-2, 2)),
           'leg': ((-2, 2), (-2, 2))}


def shell(b, part):
    (x0, x1), (z0, z1) = SECTION[part]
    return not b.card and b.lo[0] <= x0 + 0.5 and b.hi[0] >= x1 - 0.5 and b.lo[2] <= z0 and b.hi[2] >= z1


def pokes(inner, outer, part='leg'):
    """inner pushes out through a side (x or z) face of outer, inside outer's extent on the other axes. Only shells
    (boxes wrapping the whole limb) can be poked through: a toe cap or a pocket is a detail on the surface."""
    if outer.card or not shell(outer, part):
        return None
    vol = 1.0
    for k in range(3):
        vol *= overlap(inner.lo[k], inner.hi[k], outer.lo[k], outer.hi[k])
    if vol <= 1e-6 and not inner.card:
        return None
    if inner.card:
        # a card pokes through when one of its points lies within the outer box's y span and its x (or z) span, but
        # outside it along the other side axis, while the card also has points inside the box (it passes through a face)
        inside = any(all(outer.lo[k] + EPS_P < q[k] < outer.hi[k] - EPS_P for k in range(3)) for q in inner.pts)
        if not inside:
            return None
        worst = None
        for k, o in ((0, 2), (2, 0)):
            for q in inner.pts:
                if not (outer.lo[1] < q[1] < outer.hi[1]) or not (outer.lo[o] < q[o] < outer.hi[o]):
                    continue
                out = max(q[k] - outer.hi[k], outer.lo[k] - q[k])
                if out > EPS_P and (worst is None or out > worst[1]):
                    worst = ('xyz'[k], out)
        return worst
    worst = None
    for k in (0, 2):
        o = [i for i in range(3) if i != k]
        if overlap(inner.lo[o[0]], inner.hi[o[0]], outer.lo[o[0]], outer.hi[o[0]]) <= 0.05 or \
           overlap(inner.lo[o[1]], inner.hi[o[1]], outer.lo[o[1]], outer.hi[o[1]]) <= 0.05:
            continue
        out = max(inner.hi[k] - outer.hi[k], outer.lo[k] - inner.lo[k])
        if out > EPS_P and (worst is None or out > worst[1]):
            worst = ('xyz'[k], out)
    return worst


def audit(layers, label):
    found = []
    by_part = {}
    for name, part, rank, bs in layers:
        by_part.setdefault(part, []).append((name, rank, bs))
    for part, gs in by_part.items():
        for (na, ra, ba), (nb, rb, bb) in itertools.combinations(gs, 2):
            if na == nb:
                continue
            if ra == rb:
                continue  # the same layer (e.g. left/right) never meets another garment of equal rank
            inner, outer = ((na, ba), (nb, bb)) if ra < rb else ((nb, bb), (na, ba))
            for A in inner[1]:
                for B in outer[1]:
                    c = coplanar(A, B)
                    if c:
                        found.append('Z %s | %s: %s ~ %s on %s (%.2f px2)' % (label, part, inner[0], outer[0], c[0], c[1]))
                    p = pokes(A, B, part)
                    if p and inner[0] != 'skin':
                        found.append('P %s | %s: %s through %s along %s by %.2f px (%s %s)' % (
                            label, part, inner[0], outer[0], p[0], p[1], 'card' if A.card else 'box', getattr(A.src, 'mat', A.src)))
    return found


def main():
    findings = []
    combos = []
    for h in HEAD:
        combos.append(dict(head=h))
        for c in CHEST:
            if c.startswith('coverall/'):
                combos.append(dict(head=h, chest=c))
    for c in CHEST:
        combos.append(dict(chest=c))
        combos.append(dict(chest=c, extras=True))
        for l in LEGS:
            combos.append(dict(chest=c, legs=l))
            combos.append(dict(chest=c, legs=l, feet=FEET[0]))
        combos.append(dict(chest=c, feet=FEET[0]))
    for l in LEGS:
        combos.append(dict(legs=l))
        combos.append(dict(legs=l, feet=FEET[0]))
    combos.append(dict(feet=FEET[0]))
    bases = [('hood',), ('top',), ('trousers',), ('hood', 'top', 'trousers'), ('suit',)]
    extra = []
    for b in bases:
        extra.append(dict(base=b))
        for h in HEAD:
            extra.append(dict(base=b, head=h))
        for c in CHEST:
            extra.append(dict(base=b, chest=c))
        for l in LEGS:
            extra.append(dict(base=b, legs=l))
            for c in CHEST:
                extra.append(dict(base=b, legs=l, chest=c))
        extra.append(dict(base=b, feet=FEET[0]))
    for cfg in combos + extra:
        label = ' + '.join('%s=%s' % (k, (v if isinstance(v, str) else ','.join(v) if isinstance(v, tuple) else v)) for k, v in cfg.items())
        findings += audit(worn_layers(**cfg), label)
    seen = set()
    uniq = []
    for f in findings:
        key = f.split(' | ', 1)[1]
        if key not in seen:
            seen.add(key)
            uniq.append(f)
    for f in uniq:
        print(f)
    print('%d combinations, %d findings' % (len(combos) + len(extra), len(uniq)), file=sys.stderr)
    sys.exit(1 if uniq else 0)


if __name__ == '__main__':
    main()
