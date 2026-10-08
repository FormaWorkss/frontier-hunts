#!/usr/bin/env python3
"""Static checks over a built Frontier Hunts jar (integration gate).

usage: python3 tools/check_jar.py <jar> [repo_root] [--base <git rev>]

  (a) asset references in the new/changed assets resolve inside the jar (or are vanilla minecraft: resources):
      blockstates -> models, models -> parents/textures, particles -> textures, block atlas 'single' sources,
      shader programs -> .vsh/.fsh, literal "textures/..."/"sounds/..." ids in changed Java sources,
      registered blocks/items -> blockstate / item model, registered particle types -> particle json
  (b) every block / item / entity registered in the changed Java code has a lang key in the merged en_us.json,
      and every subtitle in sounds.json has one
  (c) sounds.json entries (frontierhunts namespace, type file) point to existing .ogg files; every sound event the
      changed code registers has a sounds.json entry
  (d) every mixin listed in frontierhunts.client.mixins.json exists in the jar (and "mixins" entries do not touch
      client classes)
  (e) no class outside a client package (and no common mixin) references net.minecraft.client.* / blaze3d classes
      (dedicated-server crash risk); classes only loaded through Dist.CLIENT subscribers are listed separately

"changed" = files under src/ and patch/ that differ from --base (default: the merge base of the workstreams,
8d16fb7), plus the deep-merged JSON files. Exit status 1 when a real problem (ERROR) is found.
"""
import json, os, re, struct, subprocess, sys, zipfile
from collections import defaultdict

args = [a for a in sys.argv[1:] if not a.startswith('--')]
if not args:
    sys.exit(__doc__)
JAR = args[0]
ROOT = os.path.abspath(args[1] if len(args) > 1 else os.path.join(os.path.dirname(__file__), '..'))
BASE = '8d16fb7'
if '--base' in sys.argv:
    BASE = sys.argv[sys.argv.index('--base') + 1]
NS = 'frontierhunts'

z = zipfile.ZipFile(JAR)
NAMES = set(z.namelist())
errors, warnings, notes = [], [], []


def err(kind, msg):
    errors.append(f'[{kind}] {msg}')


def warn(kind, msg):
    warnings.append(f'[{kind}] {msg}')


def jload(path):
    try:
        return json.loads(z.read(path).decode('utf-8'))
    except KeyError:
        return None
    except Exception as e:  # malformed json is an error in its own right
        err('json', f'{path}: cannot parse ({e})')
        return None


def changed_files():
    out = subprocess.run(['git', '-C', ROOT, 'diff', '--name-only', BASE, 'HEAD', '--', 'src', 'patch'],
                         capture_output=True, text=True).stdout.split()
    # also uncommitted work
    out += subprocess.run(['git', '-C', ROOT, 'diff', '--name-only', '--', 'src', 'patch'], capture_output=True, text=True).stdout.split()
    return sorted(set(out))


CHANGED = changed_files()
CHANGED_JAVA = [f for f in CHANGED if f.endswith('.java') and os.path.exists(os.path.join(ROOT, f))]
# jar paths of changed assets (patch/<jar path>, and patch/_merge/<jar path>/<frag>.json -> <jar path>)
CHANGED_ASSETS = set()
for f in CHANGED:
    if not f.startswith('patch/'):
        continue
    rel = f[len('patch/'):]
    if rel.startswith('_remove/'):
        continue
    if rel.startswith('_merge/'):
        CHANGED_ASSETS.add(os.path.dirname(rel[len('_merge/'):]))
    else:
        CHANGED_ASSETS.add(rel)

# [integ3] files removed on purpose: jar paths listed in patch/_remove/*.txt (dropped by build.py), plus patch/ files deleted
# since --base. They are expected to be absent from the jar; a removed path still in the jar is reported, and so is any
# changed asset that still references one (via the normal "missing" checks below).
REMOVED = set()
_rroot = os.path.join(ROOT, 'patch', '_remove')
if os.path.isdir(_rroot):
    for _f in sorted(os.listdir(_rroot)):
        if _f.endswith('.txt'):
            for _line in open(os.path.join(_rroot, _f), encoding='utf-8'):
                _line = _line.split('#', 1)[0].strip()
                if _line:
                    REMOVED.add(_line)
DELETED = {f[len('patch/'):] for f in CHANGED
           if f.startswith('patch/') and not f.startswith('patch/_') and not os.path.exists(os.path.join(ROOT, f))}


def split_id(rid, default_ns='minecraft'):
    rid = rid.strip()
    if ':' in rid:
        ns, p = rid.split(':', 1)
    else:
        ns, p = default_ns, rid
    return ns, p


CHECKED = defaultdict(int)


def need(kind, src, rid, jarpath):
    """A reference from src: vanilla (minecraft:) resources are assumed present; others must be in the jar."""
    CHECKED[kind] += 1
    ns = jarpath.split('/')[1] if jarpath.startswith('assets/') else NS
    if jarpath in NAMES:
        return True
    if ns == 'minecraft':
        notes.append(f'vanilla {kind} {rid} (from {src})')
        return True
    err(kind, f'{src}: {rid} -> {jarpath} missing')
    return False


# ------------------------------------------------------------------ (a) asset references
def texture_path(rid, folder=None):
    ns, p = split_id(rid)
    if folder and '/' not in p:
        p = folder + '/' + p
    return f'assets/{ns}/textures/{p}.png'


def check_model(path, seen=set()):
    if path in seen:
        return
    seen.add(path)
    m = jload(path)
    if m is None:
        return
    par = m.get('parent')
    if par and not par.startswith('builtin/'):
        ns, p = split_id(par)
        need('model', path, par, f'assets/{ns}/models/{p}.json')
        if ns == NS:
            check_model(f'assets/{ns}/models/{p}.json')
    for k, v in (m.get('textures') or {}).items():
        if isinstance(v, str) and not v.startswith('#'):
            need('texture', path, v, texture_path(v))
    # neoforge composite / loader children
    for child in (m.get('children') or {}).values() if isinstance(m.get('children'), dict) else []:
        if isinstance(child, dict) and 'parent' in child:
            ns, p = split_id(child['parent'])
            need('model', path, child['parent'], f'assets/{ns}/models/{p}.json')


def models_in_blockstate(bs):
    out = []
    def take(v):
        if isinstance(v, dict) and 'model' in v:
            out.append(v['model'])
        elif isinstance(v, list):
            for x in v:
                take(x)
    for v in (bs.get('variants') or {}).values():
        take(v)
    for part in bs.get('multipart') or []:
        take(part.get('apply'))
    return out


for path in sorted(REMOVED):
    if path in NAMES and not os.path.exists(os.path.join(ROOT, 'patch', path)):
        warn('remove', f'{path} is listed in patch/_remove but is still in the jar')

for path in sorted(CHANGED_ASSETS):
    if not path.startswith(f'assets/'):
        continue
    if path not in NAMES:
        if path in REMOVED or path in DELETED:
            notes.append(f'removed on purpose (patch/_remove or deleted from patch/): {path}')
        else:
            err('asset', f'{path} (changed in patch/) is not in the jar')
        continue
    parts = path.split('/')
    ns = parts[1]
    if len(parts) > 2 and parts[2] == 'blockstates' and path.endswith('.json'):
        bs = jload(path) or {}
        for mid in models_in_blockstate(bs):
            mns, mp = split_id(mid)
            if need('model', path, mid, f'assets/{mns}/models/{mp}.json') and mns == NS:
                check_model(f'assets/{mns}/models/{mp}.json')
    elif len(parts) > 2 and parts[2] == 'models' and path.endswith('.json'):
        check_model(path)
    elif len(parts) > 2 and parts[2] == 'particles' and path.endswith('.json'):
        for t in (jload(path) or {}).get('textures', []):
            need('texture', path, t, texture_path(t, 'particle'))
    elif len(parts) > 3 and parts[2] == 'shaders' and parts[3] == 'core' and path.endswith('.json'):
        sh = jload(path) or {}
        for key, ext in (('vertex', 'vsh'), ('fragment', 'fsh')):
            if key in sh:
                sns, sp = split_id(sh[key])
                need('shader', path, sh[key], f'assets/{sns}/shaders/core/{sp}.{ext}')
    elif len(parts) > 2 and parts[2] == 'atlases' and path.endswith('.json'):
        for src in (jload(path) or {}).get('sources', []):
            if src.get('type') in ('single', 'minecraft:single'):
                need('texture', path, src['resource'], texture_path(src['resource']))
    elif path.endswith('.png.mcmeta'):
        if path[:-len('.mcmeta')] not in NAMES:
            err('texture', f'{path}: mcmeta without its png')

# literal resource ids in the changed Java sources
LIT = re.compile(r'(?:FrontierHunts\.id|\bid|fromNamespaceAndPath\(\s*"frontierhunts"\s*,)\s*\(?\s*"((?:textures|sounds|shaders|models)/[a-z0-9_./-]+\.(?:png|ogg|json|vsh|fsh))"')
for f in CHANGED_JAVA:
    src = open(os.path.join(ROOT, f), encoding='utf-8').read()
    for m in LIT.finditer(src):
        need('code-ref', f, f'{NS}:{m.group(1)}', f'assets/{NS}/{m.group(1)}')

# ------------------------------------------------------------------ registrations in changed code
REG = re.compile(r'Registries\.(BLOCK|ITEM|ENTITY_TYPE|PARTICLE_TYPE|SOUND_EVENT|BLOCK_ENTITY_TYPE)\s*,\s*'
                 r'(?:[\w.]*\bid\(\s*"([a-z0-9_./]+)"\s*\)|ResourceLocation\.fromNamespaceAndPath\(\s*"frontierhunts"\s*,\s*"([a-z0-9_./]+)"\s*\))', re.S)
SOUND_HELPER = re.compile(r'\b(?:sound|event)\(\s*"([a-z0-9_./]+)"\s*\)')
PARTICLE_HELPER = re.compile(r'\bparticle\(\s*"([a-z0-9_./]+)"\s*\)')
registered = defaultdict(set)
for f in CHANGED_JAVA:
    src = open(os.path.join(ROOT, f), encoding='utf-8').read()
    for m in REG.finditer(src):
        registered[m.group(1)].add((m.group(2) or m.group(3), f))
    if 'Registries.SOUND_EVENT' in src or 'createVariableRangeEvent' in src:
        for m in SOUND_HELPER.finditer(src):
            registered['SOUND_EVENT'].add((m.group(1), f))
    if 'Registries.PARTICLE_TYPE' in src:
        for m in PARTICLE_HELPER.finditer(src):
            registered['PARTICLE_TYPE'].add((m.group(1), f))

lang = jload(f'assets/{NS}/lang/en_us.json') or {}
blockstates_needed = {i for i, _ in registered['BLOCK']}
for kind, prefix in (('BLOCK', 'block'), ('ITEM', 'item'), ('ENTITY_TYPE', 'entity')):
    for rid, f in sorted(registered[kind]):
        key = f'{prefix}.{NS}.{rid}'
        if kind == 'ITEM' and rid in blockstates_needed:
            ok = key in lang or f'block.{NS}.{rid}' in lang  # BlockItem uses the block's key
        else:
            ok = key in lang
        if not ok:
            err('lang', f'{key} missing (registered in {f})')
        if kind == 'BLOCK':
            if need('blockstate', f, f'{NS}:{rid}', f'assets/{NS}/blockstates/{rid}.json'):
                for mid in models_in_blockstate(jload(f'assets/{NS}/blockstates/{rid}.json') or {}):
                    mns, mp = split_id(mid)
                    if need('model', f'blockstates/{rid}.json', mid, f'assets/{mns}/models/{mp}.json') and mns == NS:
                        check_model(f'assets/{mns}/models/{mp}.json')
        if kind == 'ITEM':
            ip = f'assets/{NS}/models/item/{rid}.json'
            if need('item-model', f, f'{NS}:item/{rid}', ip):
                check_model(ip)
for rid, f in sorted(registered['PARTICLE_TYPE']):
    need('particle', f, f'{NS}:{rid}', f'assets/{NS}/particles/{rid}.json')

# ------------------------------------------------------------------ (c) sounds.json
sounds = jload(f'assets/{NS}/sounds.json') or {}
for ev, spec in sorted(sounds.items()):
    for s in spec.get('sounds', []):
        name, typ = (s, 'file') if isinstance(s, str) else (s.get('name'), s.get('type', 'file'))
        if typ == 'event':
            ens, ep = split_id(name, NS)
            if ens == NS and ep not in sounds:
                err('sound', f'{ev}: event reference {name} not in sounds.json')
            continue
        sns, sp = split_id(name, 'minecraft')
        path = f'assets/{sns}/sounds/{sp}.ogg'
        CHECKED['sound-file'] += 1
        if sns == 'minecraft':
            notes.append(f'vanilla sound file {name} (event {ev})')
        elif path not in NAMES:
            err('sound', f'{ev}: {name} -> {path} missing')
    sub = spec.get('subtitle')
    if sub and sub not in lang:
        # vanilla subtitle keys are fine
        if sub.startswith('subtitles.') and not sub.startswith(f'subtitles.{NS}') and NS not in sub:
            notes.append(f'vanilla subtitle {sub} (event {ev})')
        else:
            err('lang', f'subtitle key {sub} (sound event {ev}) missing')
for rid, f in sorted(registered['SOUND_EVENT']):
    if rid not in sounds:
        err('sound', f'sound event {NS}:{rid} registered in {f} has no sounds.json entry')

# ------------------------------------------------------------------ class-file parsing
def parse_class(data):
    """Returns (utf8 constant pool entries by index, set of referenced class internal names)."""
    if data[:4] != b'\xca\xfe\xba\xbe':
        return {}, set()
    n = struct.unpack('>H', data[8:10])[0]
    i, idx = 10, 1
    utf8, classes, descs = {}, [], []
    while idx < n:
        tag = data[i]
        if tag == 1:
            ln = struct.unpack('>H', data[i + 1:i + 3])[0]
            utf8[idx] = data[i + 3:i + 3 + ln].decode('utf-8', 'replace')
            i += 3 + ln
        elif tag in (3, 4):
            i += 5
        elif tag in (5, 6):
            i += 9; idx += 1
        elif tag == 7:
            classes.append(struct.unpack('>H', data[i + 1:i + 3])[0]); i += 3
        elif tag == 8 or tag == 16 or tag == 19 or tag == 20:
            i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            if tag == 12:
                descs.append(struct.unpack('>H', data[i + 3:i + 5])[0])
            i += 5
        elif tag == 15:
            i += 4
        else:
            raise ValueError(f'bad constant tag {tag}')
        idx += 1
    names = {utf8.get(c, '') for c in classes}
    refs = set()
    for nm in names:
        nm = nm.lstrip('[')
        if nm.startswith('L') and nm.endswith(';'):
            nm = nm[1:-1]
        refs.add(nm)
    for s in list(utf8.values()):
        # descriptors / signatures anywhere in the pool (fields, methods, lambdas, annotations)
        for m in re.finditer(r'L([\w/$]+);', s):
            refs.add(m.group(1))
    return utf8, refs


CLIENT_PREFIX = ('net/minecraft/client/', 'com/mojang/blaze3d/', 'net/neoforged/neoforge/client/')


def is_client_pkg(name):
    return '/client/' in name


mixin_cfg = jload('frontierhunts.client.mixins.json') or {}
pkg = mixin_cfg.get('package', '').replace('.', '/')
client_mixins = {f'{pkg}/{m}' for m in mixin_cfg.get('client', [])}
common_mixins = {f'{pkg}/{m}' for m in mixin_cfg.get('mixins', [])}
# (d)
for m in sorted(client_mixins | common_mixins):
    if m + '.class' not in NAMES:
        err('mixin', f'{m.replace("/", ".")} listed in frontierhunts.client.mixins.json is not in the jar')
for other in ('server',):
    for m in mixin_cfg.get(other, []):
        if f'{pkg}/{m}.class' not in NAMES:
            err('mixin', f'{pkg}/{m} ({other}) not in the jar')

src_classes = set()
for f in subprocess.run(['git', '-C', ROOT, 'ls-files', 'src'], capture_output=True, text=True).stdout.split():
    if f.endswith('.java'):
        src_classes.add(f[len('src/'):-len('.java')])
changed_classes = {f[len('src/'):-len('.java')] for f in CHANGED_JAVA}

info = {}
for name in NAMES:
    if not (name.startswith('com/formaworks/') and name.endswith('.class')):
        continue
    cname = name[:-6]
    utf8, refs = parse_class(z.read(name))
    client_refs = sorted(r for r in refs if r.startswith(CLIENT_PREFIX))
    client_sub = any('Lnet/neoforged/api/distmarker/Dist;' in s for s in utf8.values()) and 'CLIENT' in utf8.values()
    info[cname] = (refs, client_refs, client_sub)

outer = lambda c: c.split('$', 1)[0]
offenders = []
for cname, (refs, client_refs, client_sub) in sorted(info.items()):
    if not client_refs:
        continue
    if cname in client_mixins or outer(cname) in client_mixins:
        continue
    common_mixin = cname in common_mixins or outer(cname) in common_mixins
    if is_client_pkg(cname) and not common_mixin:
        continue
    offenders.append((cname, client_refs, client_sub, common_mixin))

# who (outside client packages) references each offender: a class only reachable from client code is safe
referrers = defaultdict(set)
for cname, (refs, _, _) in info.items():
    for r in refs:
        if r in info and r != cname and outer(r) != outer(cname):
            referrers[outer(r)].add(cname)

# (e2) changed non-client classes that reference the mod's own client-package classes (indirect client loading:
# a method reference / call / field type pulls the client class - and through it net.minecraft.client - onto the server)
indirect = []
for cname, (refs, _, client_sub) in sorted(info.items()):
    fam = outer(cname)
    if fam not in changed_classes or is_client_pkg(cname) or cname in client_mixins or outer(cname) in client_mixins:
        continue
    own_client = sorted(r for r in refs if r in info and outer(r) != fam and info[r][1] and r not in common_mixins
                        and (is_client_pkg(r) or not info[r][2]))
    if own_client:
        indirect.append((cname, own_client, client_sub))

# (e3) classes in client packages that FML loads on every side: @EventBusSubscriber without value = Dist.CLIENT
both_sides = []
for cname, (refs, crefs, client_sub) in sorted(info.items()):
    if not is_client_pkg(cname) or cname in common_mixins or cname in client_mixins:
        continue
    utf8 = parse_class(z.read(cname + '.class'))[0]
    vals = set(utf8.values())
    if 'Lnet/neoforged/fml/common/EventBusSubscriber;' in vals and not ('Lnet/neoforged/api/distmarker/Dist;' in vals and 'CLIENT' in vals):
        both_sides.append((cname, crefs))

print(f'jar: {JAR}')
print(f'changed since {BASE}: {len(CHANGED_JAVA)} java files, {len(CHANGED_ASSETS)} asset paths')
print(f'registered in changed code: ' + ', '.join(f'{k.lower()} {len(v)}' for k, v in sorted(registered.items())))
print()
print('(e) classes outside client packages that reference client classes:')
real = 0
for cname, crefs, client_sub, common_mixin in offenders:
    fam = outer(cname)
    origin = 'CHANGED' if fam in changed_classes else ('src' if fam in src_classes else 'base jar')
    common_refs = sorted(c for c in referrers.get(fam, ()) if not is_client_pkg(c) and outer(c) != fam)
    tag = 'Dist.CLIENT subscriber' if client_sub else ('COMMON MIXIN' if common_mixin else '')
    line = f'  {cname.replace("/", ".")} [{origin}] {tag}\n      -> {", ".join(c.split("/")[-1] for c in crefs[:6])}{" ..." if len(crefs) > 6 else ""}'
    if common_refs:
        line += f'\n      referenced from non-client: {", ".join(c.split("/")[-1] for c in common_refs[:6])}{" ..." if len(common_refs) > 6 else ""}'
    if origin != 'base jar' or '-v' in sys.argv:
        print(line)
    if origin == 'CHANGED' and (common_mixin or not client_sub):
        real += 1
        warn('client-ref', f'{cname} ({origin}) references {len(crefs)} client classes: review (see javap evidence)')
base_count = sum(1 for c, *_ in offenders if outer(c) not in src_classes)
print(f'  {len(offenders)} offenders, {base_count} of them unchanged base-jar classes (listed with -v; they shipped in dev.62)')
print()
print('(e2) changed non-client classes referencing the mod\'s client-package classes (need a Dist guard / client-only path):')
for cname, own, client_sub in indirect:
    print(f'  {cname.replace("/", ".")}{" [Dist.CLIENT subscriber]" if client_sub else ""}\n      -> {", ".join(o.split("/")[-1] for o in own)}')
    if not client_sub:
        warn('client-ref', f'{cname} references client classes {", ".join(o.split("/")[-1] for o in own)}: check it only runs on the client')
print()
print('(e3) client-package classes auto-subscribed on BOTH sides (@EventBusSubscriber without Dist.CLIENT):')
for cname, crefs in both_sides:
    fam = outer(cname)
    origin = 'CHANGED' if fam in changed_classes else ('src' if fam in src_classes else 'base jar')
    print(f'  {cname.replace("/", ".")} [{origin}] client refs: {len(crefs)}')
    if crefs and origin == 'CHANGED':
        err('client-ref', f'{cname} is a both-sides @EventBusSubscriber in a client package and references {len(crefs)} client classes')
print()
for w in warnings:
    print('WARN ', w)
for e in errors:
    print('ERROR', e)
print('references checked: ' + ', '.join(f'{k} {v}' for k, v in sorted(CHECKED.items())))
print(f'\n{len(errors)} errors, {len(warnings)} warnings, {len(set(notes))} notes (vanilla references assumed present, intentional removals)')
if '-v' in sys.argv:
    for n in sorted(set(notes)):
        print('  note', n)
sys.exit(1 if errors else 0)
