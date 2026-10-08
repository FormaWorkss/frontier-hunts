"""Frontier Hunts release build.

python3 tools/build.py <version-suffix> [repo_root]
  - compiles <repo>/src against orig62 + libs into <repo>/.build/classes
  - compiles <repo>/fs/java (Frontier Structures, bundled as a second mod in the same jar)
  - base = orig62.jar; every class family that src compiles replaces the jar's family
  - overlays <repo>/patch/** (files replace jar entries)
  - deep-merges JSON fragments: <repo>/patch/_merge/<jar path>/<any>.json into that jar path
    (dicts merge recursively, lists are unioned in order, scalars from fragments win; a null value deletes that key)
  - drops base-jar entries listed in <repo>/patch/_remove/<any>.txt (one jar path per line, '#' comments)
  - adds fs/resources/** and a [[mods]] frontierstructures entry to META-INF/neoforge.mods.toml
"""
import zipfile, os, sys, json, hashlib, subprocess, shutil, glob

SUFFIX = sys.argv[1] if len(sys.argv) > 1 else 'dev'
R = os.path.abspath(sys.argv[2] if len(sys.argv) > 2 else '/home/claude/fh')
BASE = '/home/claude/fh/orig62.jar'
VER_OLD = '0.1.0-dev.62-landscape.62-gear.3'
RELEASE = SUFFIX[:1].isdigit()  # [1.1.0] a plain release number names the release: FrontierHunts-1.1.0.jar
VER_NEW = SUFFIX if RELEASE else '0.1.0-dev.62-landscape.62-' + SUFFIX
OUT = os.path.join(R, '.build', f'FrontierHunts-{VER_NEW}.jar' if RELEASE else f'frontier-hunts-neoforge-1.21.1-{VER_NEW}.jar')
CP = open('/home/claude/fh/cp62.txt').read().strip()
FS_VERSION = '1.2.0'


def javac(srcdir, outdir, cp):
    shutil.rmtree(outdir, ignore_errors=True); os.makedirs(outdir)
    files = [p for p in glob.glob(srcdir + '/**/*.java', recursive=True)]
    if not files: return
    argf = outdir + '.args'
    open(argf, 'w').write('\n'.join(files))
    r = subprocess.run(['javac', '-proc:none', '--release', '21', '-nowarn', '-encoding', 'UTF-8', '-cp', cp, '-d', outdir, '@' + argf],
                       capture_output=True, text=True)
    err = '\n'.join(l for l in (r.stdout + r.stderr).splitlines() if 'JAVA_TOOL' not in l and not l.startswith('Note:'))
    if r.returncode != 0:
        print(err); raise SystemExit('javac failed for ' + srcdir)


def merge(a, b):
    if isinstance(a, dict) and isinstance(b, dict):
        o = dict(a)
        for k, v in b.items():
            if v is None: o.pop(k, None); continue  # [items] null in a fragment removes the key
            o[k] = merge(a[k], v) if k in a else v
        return o
    if isinstance(a, list) and isinstance(b, list):
        o = list(a)
        for v in b:
            if v not in o: o.append(v)
        return o
    return b


cls = os.path.join(R, '.build', 'classes'); fscls = os.path.join(R, '.build', 'fsclasses')
javac(os.path.join(R, 'src'), cls, CP)
javac(os.path.join(R, 'fs', 'java'), fscls, cls + os.pathsep + CP)  # [1.1.0] fs may call into the hunts mod (same jar)

entries = {}      # jar path -> bytes (overrides)
families = set()
for base, isclass in ((cls, True), (fscls, False), (os.path.join(R, 'patch'), False), (os.path.join(R, 'fs', 'resources'), False)):
    if not os.path.isdir(base): continue
    for root, _, files in os.walk(base):
        for f in files:
            p = os.path.join(root, f); rel = os.path.relpath(p, base).replace(os.sep, '/')
            if rel.startswith('_merge/') or rel.startswith('_remove/') or rel.startswith('META-INF/neoforge.mods.toml'): continue
            entries[rel] = open(p, 'rb').read()
            if isclass: families.add(rel.split('$')[0].replace('.class', ''))

# JSON fragment merges
fragments = {}
mroot = os.path.join(R, 'patch', '_merge')
if os.path.isdir(mroot):
    for root, _, files in os.walk(mroot):
        for f in sorted(files):
            if not f.endswith('.json'): continue
            target = os.path.relpath(root, mroot).replace(os.sep, '/')
            fragments.setdefault(target, []).append(json.load(open(os.path.join(root, f), encoding='utf-8')))

# [items] base-jar entries to drop (removed features)
REMOVE = set()
rroot = os.path.join(R, 'patch', '_remove')
if os.path.isdir(rroot):
    for f in sorted(os.listdir(rroot)):
        if f.endswith('.txt'):
            for line in open(os.path.join(rroot, f), encoding='utf-8'):
                line = line.split('#', 1)[0].strip()
                if line: REMOVE.add(line)

os.makedirs(os.path.dirname(OUT), exist_ok=True)
if os.path.exists(OUT): os.remove(OUT)
removed = 0
with zipfile.ZipFile(BASE) as zi, zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as zo:
    names = set(zi.namelist())
    for info in zi.infolist():
        n = info.filename
        if n.endswith('.class') and n.split('$')[0].replace('.class', '') in families: removed += 1; continue
        if n in entries or n in fragments: continue
        if n in REMOVE: removed += 1; continue
        data = zi.read(n)
        if n == 'META-INF/neoforge.mods.toml':
            t = data.decode().replace(f'version="{VER_OLD}"', f'version="{VER_NEW}"')
            if RELEASE:
                t = t.replace('displayName="Frontier Hunts"', 'displayName="FrontierHunts"')
            if 'modId="frontierstructures"' not in t:
                t = t.replace('\n[[mixins]]', f'''
[[mods]]
modId="frontierstructures"
version="{FS_VERSION}"
displayName="Frontier Structures"
authors="FormaWorks"
description=\'\'\'Furnished frontier settlements, lookout towers and a persistent structure editing workshop, bundled with Frontier Hunts.\'\'\'

[[dependencies.frontierstructures]]
modId="neoforge"
type="required"
versionRange="[21.1.248]"
ordering="NONE"
side="BOTH"

[[dependencies.frontierstructures]]
modId="frontierhunts"
type="required"
versionRange="[0,)"
ordering="AFTER"
side="BOTH"

[[mixins]]''', 1)
            t = t.replace('versionRange="[21.1.248]"', 'versionRange="[21.1.248,)"')  # [1.1.5] any NeoForge 21.1 from 248 on
            # [1.1.5] one audio credits file
            t = t.replace('audio: asset-specific licenses in FRONTIER_AUDIO_CREDITS.txt and WILDLIFE_AUDIO_LICENSES.md', 'audio: asset-specific licences in AUDIO_CREDITS.txt')
            data = t.encode()
        if n == 'META-INF/MANIFEST.MF':
            data = data.decode().replace(f'Implementation-Version: {VER_OLD}', f'Implementation-Version: {VER_NEW}').encode()
        zo.writestr(info, data)
    for n, data in sorted(entries.items()):
        if n in fragments: continue
        zo.writestr(n, data)
    for n, frs in sorted(fragments.items()):
        if n in entries: base = json.loads(entries[n].decode('utf-8'))
        elif n in names:
            with zipfile.ZipFile(BASE) as z2: base = json.loads(z2.read(n).decode('utf-8'))
        else: base = {}
        for fr in frs: base = merge(base, fr)
        zo.writestr(n, json.dumps(base, indent=1, ensure_ascii=False))
print('removed', removed, 'overrides', len(entries), 'merged', len(fragments))
# [gear20] lossless size pass (smaller PNGs, compact JSON, max deflate)
import subprocess
subprocess.run([sys.executable, os.path.join(R, 'tools', 'shrink_jar.py'), OUT], check=True, stdout=subprocess.DEVNULL)
print(OUT)
print(hashlib.sha256(open(OUT, 'rb').read()).hexdigest().upper())
