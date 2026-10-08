"""[gunsmith] Refines the supplied .fheq firearm / attachment / optic meshes into patch/ (all three LODs).

Steps per mesh (geometry topology, UVs and part ids are never changed; animation pivots and sockets stay valid):
  1. per-mesh fixes (FIXES): material re-tints, proportion corrections (radial scope-tube scaling about the optical
     axis, which keeps the sight line - checked by GunBench ads);
  2. ambient-occlusion bake into the vertex tint (bake.py).

usage: python3 refine.py <base equipment dir> <out dir> [name ...]
"""
import os
import sys
import numpy as np
import subprocess
import tempfile
import fheq
import bevel

HERE = os.path.dirname(os.path.abspath(__file__))
BAKE_CLASSES = '/tmp/claude-0/fheqbake'
STRENGTH = 0.62
FLOOR = 0.5
BEVEL = True


def baker():
    if not os.path.exists(os.path.join(BAKE_CLASSES, 'FheqBake.class')):
        os.makedirs(BAKE_CLASSES, exist_ok=True)
        subprocess.run(['javac', '-nowarn', '-d', BAKE_CLASSES, os.path.join(HERE, 'FheqBake.java')], check=True)

GUNS = ['lever_rifle', 'semi_auto_rifle', 'pump_shotgun', 'double_barrel', 'semi_auto_shotgun', 'revolver', 'tranquilizer_rifle',
        'flare_gun', 'bait_launcher']
ATTS = ['att_bipod_body', 'att_bipod_leg', 'att_cheek_riser', 'att_foregrip', 'att_muzzle_brake', 'att_optic_rail_long',
        'att_optic_rail_short', 'att_pistol_mag_extended', 'att_rifle_mag_extended', 'att_suppressor']
OPTICS = ['optic_six_power_scope', 'optic_eight_power_scope', 'optic_twelve_power_scope', 'optic_thermal_scope', 'optic_fixed_scope',
          'optic_holographic_sight', 'optic_micro_red_dot', 'optic_reflex_sight', 'optic_two_power_prism']
LODS = ['close', 'field', 'distant']


def tint_rgb(c):
    return np.stack([(c >> 16) & 255, (c >> 8) & 255, c & 255], -1).astype(np.float64)


def pack(rgb):
    rgb = np.clip(np.round(rgb), 0, 255).astype(np.int64)
    return (rgb[..., 0] << 16) | (rgb[..., 1] << 8) | rgb[..., 2]


def cell_of(p):
    """Atlas cell (col, row) per vertex."""
    f = p['f']
    return np.clip((f[:, 6] * 4).astype(int), 0, 3), np.clip((f[:, 7] * 4).astype(int), 0, 3)


def bipod_leg(parts, lod):
    # the telescoping lower legs used the bright-steel cell with black bands, which read as a toy candy stripe:
    # hard-anodized grey-black aluminium instead (same cell layout, darker tint), adjuster rings stay black
    for p in parts:
        cu, cv = cell_of(p)
        m = (cu == 1) & (cv == 1)
        if m.any():
            rgb = tint_rgb(p['c'])
            rgb[m] = rgb[m] * np.array([0.40, 0.41, 0.43])
            p['c'] = pack(rgb)
    return parts


def semi_auto(parts, lod):
    import rifles, magazines
    return magazines.replace_part3(rifles.semi_auto_stock(parts, lod), lod)


def semi_auto_shotgun(parts, lod):
    import shotguns
    return shotguns.semi_auto_action(parts, lod)


def _bores(name):
    import shotguns
    return shotguns.break_bores(name)


FIXES = {
    'double_barrel': _bores('double_barrel'),
    'flare_gun': _bores('flare_gun'),
    'att_bipod_leg': bipod_leg,
    'semi_auto_rifle': semi_auto,
    'semi_auto_shotgun': semi_auto_shotgun,
}


REBUILT = {}


def rebuilt(stem, lod):
    import scopes
    if stem in ('optic_six_power_scope', 'optic_eight_power_scope', 'optic_twelve_power_scope', 'optic_thermal_scope', 'optic_fixed_scope'):
        return [p.to_fheq() for p in scopes.build(stem[6:], lod)]
    if stem in ('att_suppressor', 'att_muzzle_brake'):
        import attachments
        return [p.to_fheq() for p in attachments.build(stem, lod)]
    if stem == 'att_foregrip':
        import attachments
        return [p.to_fheq() for p in attachments.foregrip(lod)]
    if stem == 'att_rifle_mag_extended':
        import magazines
        return [p.to_fheq() for p in magazines.build(lod, True)]
    if stem == 'revolver':
        import revolver
        return [p.to_fheq() for p in revolver.build(lod)]
    return None


def process(src, dst, name):
    stem, lod = name.rsplit('_', 1)
    new = rebuilt(stem, lod)
    if new is not None:
        with tempfile.NamedTemporaryFile(suffix='.fheq', delete=False) as tmp:
            fheq.write(tmp.name, new)
        baker()
        subprocess.run(['java', '-cp', BAKE_CLASSES, 'FheqBake', tmp.name, dst, str(STRENGTH * 0.8), str(FLOOR)], check=True, stderr=subprocess.DEVNULL)
        os.unlink(tmp.name)
        return
    parts = fheq.read(src)
    fix = FIXES.get(stem)
    if fix:
        parts = fix(parts, lod)
    if lod == 'close' and BEVEL:
        parts = [bevel.bevel_part(p)[0] for p in parts]
    strength = STRENGTH if lod != 'distant' else STRENGTH * 0.7
    with tempfile.NamedTemporaryFile(suffix='.fheq', delete=False) as tmp:
        fheq.write(tmp.name, parts)
    baker()
    subprocess.run(['java', '-cp', BAKE_CLASSES, 'FheqBake', tmp.name, dst, str(strength), str(FLOOR)], check=True,
                   stderr=subprocess.DEVNULL)
    os.unlink(tmp.name)


def main():
    base, out = sys.argv[1], sys.argv[2]
    names = sys.argv[3:] or [n + '_' + l for n in GUNS + ATTS + OPTICS for l in LODS]
    os.makedirs(out, exist_ok=True)
    for n in names:
        process(os.path.join(base, n + '.fheq'), os.path.join(out, n + '.fheq'), n)
        print('refined', n, flush=True)


if __name__ == '__main__':
    main()
