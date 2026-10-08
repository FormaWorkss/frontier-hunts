#!/usr/bin/env python3
"""[gear20] Makes the built jar lighter without changing anything the game sees:
 - PNGs re-encoded losslessly (fully opaque RGBA stored as RGB, best zlib filter search); kept only when smaller,
 - JSON written compactly (no indentation),
 - every entry deflated at the highest level.
Results are cached by content hash in .build/shrink-cache, so rebuilds stay quick.
usage: shrink_jar.py <jar>"""
import hashlib, io, json, os, sys, zipfile
from PIL import Image

jar = sys.argv[1]
cache = os.path.join(os.path.dirname(os.path.abspath(jar)), 'shrink-cache')
os.makedirs(cache, exist_ok=True)


def png(data):
    key = hashlib.sha1(data).hexdigest()
    cp = os.path.join(cache, key + '.png')
    if os.path.exists(cp):
        return open(cp, 'rb').read()
    out = data
    try:
        im = Image.open(io.BytesIO(data))
        im.load()
        if im.mode == 'RGBA' and im.getchannel('A').getextrema() == (255, 255):
            im = im.convert('RGB')
        if im.mode in ('RGB', 'RGBA', 'L', 'LA', 'P'):
            b = io.BytesIO()
            im.save(b, 'PNG', optimize=True)
            if len(b.getvalue()) < len(data):
                out = b.getvalue()
    except Exception:
        out = data
    open(cp, 'wb').write(out)
    return out


def js(name, data):
    try:
        return json.dumps(json.loads(data.decode('utf-8')), separators=(',', ':'), ensure_ascii=False).encode('utf-8')
    except Exception:
        return data


tmp = jar + '.tmp'
before = os.path.getsize(jar)
with zipfile.ZipFile(jar) as zi, zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zo:
    for info in zi.infolist():
        data = zi.read(info.filename)
        n = info.filename
        if n.endswith('.png') and len(data) > 1024:
            data = png(data)
        elif n.endswith('.json') and not n.startswith('META-INF/'):
            data = js(n, data)
        zi2 = zipfile.ZipInfo(n, date_time=info.date_time)
        zi2.external_attr = info.external_attr
        zi2.compress_type = zipfile.ZIP_STORED if n.endswith(('.ogg',)) else zipfile.ZIP_DEFLATED
        zo.writestr(zi2, data, compresslevel=9)
os.replace(tmp, jar)
after = os.path.getsize(jar)
print(f'shrink: {before / 1e6:.1f} MB -> {after / 1e6:.1f} MB')
print(hashlib.sha256(open(jar, 'rb').read()).hexdigest().upper())
