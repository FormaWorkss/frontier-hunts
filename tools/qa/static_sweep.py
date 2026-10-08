#!/usr/bin/env python3
"""Frontier Hunts static resource sweep (QA workstream).

  python3 tools/qa/static_sweep.py <mod jar> [fhqa_ids.json]

Checks every JSON in the jar parses (and has no duplicate keys), blockstate -> model -> parent/texture
references, item models / lang names for every registered item, block and entity (ids come from the
fhqa_ids.json the QA harness writes on the smoke-test server; without it only file-level checks run),
sounds.json entries and .ogg files, particle definitions, atlas sources and core shader programs.
Vanilla references are resolved against the MDG client-extra resources jar.
Prints ERROR / WARN / INFO lines and a summary; exit code 1 if there is any ERROR.
"""
import json, sys, zipfile, re, collections, os

VANILLA = '/home/claude/qaserver/libs/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar'
OURS = ('frontierhunts', 'frontierstructures')
jar = zipfile.ZipFile(sys.argv[1])
names = set(jar.namelist())
van = set(zipfile.ZipFile(VANILLA).namelist()) if os.path.exists(VANILLA) else set()
ids = json.load(open(sys.argv[2])) if len(sys.argv) > 2 and os.path.exists(sys.argv[2]) else None
out = collections.defaultdict(list)


def report(level, msg):
    out[level].append(msg)


def dup_hook(pairs):
    d = {}
    for k, v in pairs:
        if k in d and k != '//' and not k.startswith('_comment'):
            dup_hook.dups.append(k)
        d[k] = v
    return d


dup_hook.dups = []
data = {}
for n in sorted(names):
    if not n.endswith('.json') and not n.endswith('.mcmeta'):
        continue
    try:
        dup_hook.dups = []
        data[n] = json.loads(jar.read(n).decode('utf-8-sig'), object_pairs_hook=dup_hook)
        if dup_hook.dups:
            report('WARN', f'{n}: duplicate keys {sorted(set(dup_hook.dups))[:8]}')
    except Exception as e:
        report('ERROR', f'{n}: does not parse: {e}')


def rl(s, default_ns='minecraft'):
    s = s.split('#')[0]
    return s.split(':', 1) if ':' in s else (default_ns, s)


def has(path):
    return path in names or path in van


# ---- atlas sprites declared by sources (unstitch / single / custom generated)
atlas_sprites = set()
atlas_dirs = []
for n, d in data.items():
    if '/atlases/' not in n or not isinstance(d, dict):
        continue
    for s in d.get('sources', []):
        t = s.get('type', '').replace('minecraft:', '')
        if t == 'single':
            atlas_sprites.add(s.get('sprite', s.get('resource')))
            ns, p = rl(s['resource'])
            if not has(f'assets/{ns}/textures/{p}.png'):
                report('ERROR', f'{n}: single source texture missing {s["resource"]}')
        elif t == 'unstitch':
            ns, p = rl(s['resource'])
            if not has(f'assets/{ns}/textures/{p}.png'):
                report('ERROR', f'{n}: unstitch source texture missing {s["resource"]}')
            for r in s.get('regions', []):
                atlas_sprites.add(r['sprite'])
        elif t == 'directory':
            atlas_dirs.append((s.get('source'), s.get('prefix')))
        elif t == 'paletted_permutations':
            pass
        else:
            report('INFO', f'{n}: custom atlas source type {s.get("type")} (sprites generated in code)')
atlas_sprites = {x if ':' in x else 'minecraft:' + x for x in atlas_sprites if x}


def texture_ok(ref, ctx):
    if ref.startswith('#'):
        return True
    ns, p = rl(ref)
    if has(f'assets/{ns}/textures/{p}.png') or f'{ns}:{p}' in atlas_sprites:
        return True
    if ns == 'frontierhunts' and p.startswith('block/original/'):
        return True  # BlendSpriteSource: generated from the vanilla texture in code
    if ns not in OURS and ns != 'minecraft':
        return True  # another mod's namespace (soft compat)
    report('ERROR', f'{ctx}: texture {ref} not found (renders as missing texture)')
    return False


# ---- models
model_files = {n for n in names if re.match(r'assets/[^/]+/models/.+\.json$', n)}


def model_exists(ref):
    ns, p = rl(ref)
    if p.startswith('builtin/'):
        return True
    return has(f'assets/{ns}/models/{p}.json')


for n in sorted(model_files):
    d = data.get(n)
    if not isinstance(d, dict):
        continue
    par = d.get('parent')
    if par and not model_exists(par):
        report('ERROR', f'{n}: parent {par} not found')
    for k, v in (d.get('textures') or {}).items():
        if isinstance(v, str):
            texture_ok(v, n)
    if 'loader' in d:
        continue
    # unresolved texture variables used by elements
    tex = dict(d.get('textures') or {})
    for el in d.get('elements', []) or []:
        for f, face in (el.get('faces') or {}).items():
            t = face.get('texture', '')
            if t.startswith('#') and t[1:] not in tex and not par:
                report('WARN', f'{n}: face texture {t} has no definition and no parent')

# ---- blockstates
for n in sorted(x for x in names if re.match(r'assets/[^/]+/blockstates/.+\.json$', x)):
    d = data.get(n)
    if not isinstance(d, dict):
        continue
    ns_here = n.split('/')[1]
    refs = []
    for v in (d.get('variants') or {}).values():
        refs += [m.get('model') for m in (v if isinstance(v, list) else [v]) if isinstance(m, dict)]
    for part in d.get('multipart') or []:
        a = part.get('apply')
        refs += [m.get('model') for m in (a if isinstance(a, list) else [a]) if isinstance(m, dict)]
    for r in refs:
        if r and not model_exists(r):
            report('ERROR', f'{n}: model {r} not found (block renders as missing model)')

# ---- registry completeness (needs fhqa_ids.json)
lang = {}
for ns in OURS:
    p = f'assets/{ns}/lang/en_us.json'
    if p in data:
        lang.update(data[p])
if ids:
    for item in ids.get('item', []):
        ns, p = item.split(':')
        if not has(f'assets/{ns}/models/item/{p}.json'):
            report('WARN', f'item {item}: no assets/{ns}/models/item/{p}.json (missing model in inventory unless rendered in code)')
        if f'item.{ns}.{p}' not in lang and f'block.{ns}.{p}' not in lang:
            report('WARN', f'item {item}: no lang name (item.{ns}.{p})')
    for b in ids.get('block', []):
        ns, p = b.split(':')
        if not has(f'assets/{ns}/blockstates/{p}.json'):
            report('INFO', f'block {b}: no blockstate file (fine only if it never renders as a model)')
        if f'block.{ns}.{p}' not in lang:
            report('INFO', f'block {b}: no lang name block.{ns}.{p}')
    for e in ids.get('entity_type', []):
        ns, p = e.split(':')
        if f'entity.{ns}.{p}' not in lang:
            report('WARN', f'entity {e}: no lang name entity.{ns}.{p} (shows raw key in death messages / spawn eggs)')
    sounds = {}
    for ns in OURS:
        sp = f'assets/{ns}/sounds.json'
        for k, v in (data.get(sp) or {}).items():
            sounds[f'{ns}:{k}'] = v
    for s in ids.get('sound_event', []):
        if s not in sounds:
            report('WARN', f'sound event {s}: registered but not in sounds.json (plays nothing, logs "Missing sound for event")')
    for k in sounds:
        if ids and k not in ids.get('sound_event', []):
            report('INFO', f'sounds.json entry {k} is not a registered SoundEvent (fine if only played by id)')
    for pt in ids.get('particle_type', []):
        ns, p = pt.split(':')
        if not has(f'assets/{ns}/particles/{p}.json'):
            report('INFO', f'particle {pt}: no particles/{p}.json (fine only if its provider does not use a sprite set)')
    for t in ids.get('creative_tab', []):
        pass

# ---- sounds.json files
for ns in OURS:
    sp = f'assets/{ns}/sounds.json'
    for k, v in (data.get(sp) or {}).items():
        for s in v.get('sounds', []):
            name = s if isinstance(s, str) else s.get('name')
            typ = 'file' if isinstance(s, str) else s.get('type', 'file')
            if typ == 'event':
                continue
            sns, sp2 = rl(name, ns)
            if not has(f'assets/{sns}/sounds/{sp2}.ogg'):
                report('ERROR', f'{sp}: {k} -> sounds/{sp2}.ogg missing')
        if 'subtitle' in v and v['subtitle'] not in lang:
            report('INFO', f'{sp}: {k} subtitle key {v["subtitle"]} not in lang')

# ---- particle definitions
for n in sorted(x for x in names if re.match(r'assets/[^/]+/particles/.+\.json$', x)):
    for t in (data.get(n) or {}).get('textures', []):
        ns, p = rl(t)
        if not has(f'assets/{ns}/textures/particle/{p}.png'):
            report('ERROR', f'{n}: particle texture particle/{p}.png missing')

# ---- core shaders
for n in sorted(x for x in names if re.match(r'assets/[^/]+/shaders/core/.+\.json$', x)):
    d = data.get(n) or {}
    for key in ('vertex', 'fragment'):
        if key in d:
            ns, p = rl(d[key])
            ext = 'vsh' if key == 'vertex' else 'fsh'
            if not has(f'assets/{ns}/shaders/core/{p}.{ext}'):
                report('ERROR', f'{n}: {key} program {p}.{ext} missing')
            else:
                src = (jar.read(f'assets/{ns}/shaders/core/{p}.{ext}') if f'assets/{ns}/shaders/core/{p}.{ext}' in names else b'').decode('utf-8', 'replace')
                for u in d.get('uniforms', []):
                    pass
    # declared uniforms / samplers that no program uses get optimised out -> "could not find uniform" warnings
    progs = ''
    for key, ext in (('vertex', 'vsh'), ('fragment', 'fsh')):
        if key in d:
            ns, p = rl(d[key])
            f = f'assets/{ns}/shaders/core/{p}.{ext}'
            if f in names:
                progs += re.sub(r'^\s*uniform[^;]*;', '', jar.read(f).decode('utf-8', 'replace'), flags=re.M)
    if progs:
        for u in d.get('uniforms', []):
            if not re.search(r'\b' + re.escape(u['name']) + r'\b', progs):
                report('WARN', f'{n}: uniform {u["name"]} declared but unused (ShaderInstance logs "could not find uniform")')
        for s in d.get('samplers', []):
            if not re.search(r'\b' + re.escape(s['name']) + r'\b', progs):
                report('WARN', f'{n}: sampler {s["name"]} declared but unused (ShaderInstance logs "could not find sampler")')

# ---- built-in resource packs shipped inside the jar (resourcepacks/<pack>/assets/...)
packs = sorted({n.split('/')[1] for n in names if n.startswith('resourcepacks/') and n.count('/') > 2})
for pk in packs:
    root = f'resourcepacks/{pk}/'
    pnames = {n[len(root):] for n in names if n.startswith(root)}
    def phas(path):
        return path in pnames or has(path)
    for n in sorted(pnames):
        if not re.match(r'assets/[^/]+/(models|blockstates)/.+\.json$', n):
            continue
        d = data.get(root + n)
        if not isinstance(d, dict):
            continue
        if '/models/' in n:
            par = d.get('parent')
            if par:
                ns, pp = rl(par)
                if not pp.startswith('builtin/') and not phas(f'assets/{ns}/models/{pp}.json'):
                    report('ERROR', f'{root}{n}: parent {par} not found')
            for k, v in (d.get('textures') or {}).items():
                if isinstance(v, str) and not v.startswith('#'):
                    ns, pp = rl(v)
                    if not (phas(f'assets/{ns}/textures/{pp}.png') or f'{ns}:{pp}' in atlas_sprites or pp.startswith('block/original/')):
                        report('ERROR', f'{root}{n}: texture {v} not found')
        else:
            refs = []
            for v in (d.get('variants') or {}).values():
                refs += [m.get('model') for m in (v if isinstance(v, list) else [v]) if isinstance(m, dict)]
            for part in d.get('multipart') or []:
                a = part.get('apply')
                refs += [m.get('model') for m in (a if isinstance(a, list) else [a]) if isinstance(m, dict)]
            for r in refs:
                if r:
                    ns, pp = rl(r)
                    if not phas(f'assets/{ns}/models/{pp}.json'):
                        report('ERROR', f'{root}{n}: model {r} not found')
    report('INFO', f'checked built-in resource pack {pk} ({len(pnames)} files)')

# ---- pack.mcmeta
pm = data.get('pack.mcmeta', {}).get('pack', {})
fmt = pm.get('pack_format'); sup = pm.get('supported_formats')
lo, hi = (sup if isinstance(sup, list) else [sup.get('min_inclusive'), sup.get('max_inclusive')]) if sup else (fmt, fmt)
for need, what in ((34, 'resource pack (1.21.1 = 34)'), (48, 'data pack (1.21.1 = 48)')):
    if not (lo is not None and lo <= need <= hi):
        report('WARN', f'pack.mcmeta pack_format {fmt} supported {sup}: mod {what} is flagged incompatible')

for level in ('ERROR', 'WARN', 'INFO'):
    for m in out[level]:
        print(f'{level}: {m}')
print(f'SUMMARY json={len(data)} models={len(model_files)} errors={len(out["ERROR"])} warnings={len(out["WARN"])} info={len(out["INFO"])}')
sys.exit(1 if out['ERROR'] else 0)
