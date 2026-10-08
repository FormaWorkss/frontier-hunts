"""[meshes] Repairs the baked grazing (feed) poses of the wildlife meshes, in place (header only, geometry untouched).

The feed pose is blended in linearly (0..1) by WildlifeRig, so every joint angle must be the short way round and the two
legs of a pair must bend the same way. The baked IK solutions had:
  * wrapped angles (grizzly fl_upper -4.86 rad, fr_foot -4.38): the leg spins most of a turn while the animal lowers its head,
  * mirrored knees (wolf / coyote / cougar / panther: fl_lower +1.70 but fr_lower -1.71): one elbow folds backwards,
  * a folded-flat elbow (grizzly fl_lower 3.14 = 180 deg): the skin collapses to a sheet (skin determinant ~0).
Fix: wrap to (-pi, pi]; per leg pair keep the side with the smaller total bend and mirror it (rotations are about the
lateral axis, so the same values); grizzly front legs get a shallow bend and a shallower stoop (audited: no collapse, muzzle above the soil).
usage: python3 feedfix.py [mesh files...]   (default: every patch/.../<species>_{ultra,mid,bal}.fhsk except the hound)
"""
import gzip, math, os, struct, sys, glob

HERE = os.path.dirname(os.path.abspath(__file__))
DIR = os.path.join(HERE, '..', '..', 'patch', 'assets', 'frontierhunts', 'models', 'wildlife')
OVERRIDE = {('grizzly', 'f'): {'upper': -0.3, 'lower': 0.4, 'foot': -0.1, 'toe': 0.0}}
# grizzly: a shallower forage stoop (the full one buried the muzzle 0.3-0.4 blocks into the ground)
MIRROR = False   # audited: mirroring the opposite-sign knees put a paw 0.2 blocks into the soil (they are the IK planting it)
SCALE = {'grizzly': {'pelvis': 0.5, 'chest': 0.5, 'neck': 0.5, 'head': 0.5}}


def wrap(a):
    return (a + math.pi) % (2 * math.pi) - math.pi if abs(a) > math.pi else a


def fix(path, log=print):
    raw = gzip.decompress(open(path, 'rb').read())
    b = bytearray(raw)
    o = 4 + 4 + 32
    nb, = struct.unpack_from('>h', b, o); o += 2
    v2 = struct.unpack_from('>i', b, 0)[0] == 0x46485332
    if not v2:
        return
    bones = {}
    for i in range(nb):
        ln, = struct.unpack_from('>h', b, o); o += 2
        name = b[o:o + ln].decode(); o += ln
        o += 2 + 24          # parent, joint, rest
        bones[name] = o      # feed x, y, z
        o += 12
    sp = os.path.basename(path).rsplit('_', 1)[0]
    changes = []
    feed = {n: list(struct.unpack_from('>3f', b, off)) for n, off in bones.items()}
    for n in feed:
        for k in range(3):
            w = wrap(feed[n][k])
            if w != feed[n][k]:
                changes.append('%s wrap %.2f->%.2f' % (n, feed[n][k], w)); feed[n][k] = w
    segs = ('upper', 'lower', 'foot', 'toe')
    for pair, (l, r) in (('f', ('fl', 'fr')), ('b', ('bl', 'br'))):
        if not all('%s_%s' % (s, g) in feed for s in (l, r) for g in segs[:3]):
            continue
        if (sp, pair) in OVERRIDE:
            for s in (l, r):
                for g, v in OVERRIDE[(sp, pair)].items():
                    if '%s_%s' % (s, g) in feed:
                        feed['%s_%s' % (s, g)][0] = v
            changes.append('%s legs re-posed (shallow bend)' % pair)
            continue
        cost = {s: sum(abs(feed['%s_%s' % (s, g)][0]) for g in segs if '%s_%s' % (s, g) in feed) for s in (l, r)}
        # only an inverted knee / elbow is repaired: the rest poses differ left / right (legs squared up from a
        # mid-stride sculpt), so small left / right differences are the IK keeping both paws planted
        a, c = feed['%s_lower' % l][0], feed['%s_lower' % r][0]
        if not (a * c < 0 and abs(a) > 0.8 and abs(c) > 0.8) or not MIRROR:
            continue
        keep = min(cost, key=cost.get)
        other = r if keep == l else l
        for g in segs:
            if '%s_%s' % (keep, g) in feed:
                feed['%s_%s' % (other, g)][0] = feed['%s_%s' % (keep, g)][0]
        changes.append('%s mirrored from %s' % (other, keep))
    if sp in SCALE:
        # scaled from master's value (so running the tool again changes nothing)
        import subprocess
        sys.path.insert(0, os.path.join(HERE, '..', 'wingshot'))
        import fhsk
        src = fhsk.parse(subprocess.run(['git', '-C', HERE, 'show', '644f37a:patch/assets/frontierhunts/models/wildlife/%s_%s.fhsk'
                                         % (sp, 'ultra' if path.endswith('_mid.fhsk') else os.path.basename(path).rsplit('_', 1)[1][:-5])],
                                        check=True, capture_output=True).stdout)
        for n, k in SCALE[sp].items():
            if n in feed and n in src.names:
                feed[n] = [float(x) * k for x in src.feed[src.names.index(n)]]
                changes.append('%s = master x%.1f' % (n, k))
    for n, off in bones.items():
        struct.pack_into('>3f', b, off, *feed[n])
    if changes:
        with gzip.open(path, 'wb', compresslevel=9) as f:
            f.write(bytes(b))
    log('%s: %s' % (os.path.basename(path), '; '.join(changes) or 'ok'))


if __name__ == '__main__':
    files = sys.argv[1:] or sorted(f for f in glob.glob(os.path.join(DIR, '*.fhsk')) if not os.path.basename(f).startswith('hound'))
    for f in files:
        fix(f)
