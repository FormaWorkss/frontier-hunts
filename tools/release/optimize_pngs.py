#!/usr/bin/env python3
"""[1.1.5] Lossless PNG optimization done once at the source instead of in every build (the Java release builder does
not re-encode images). The same rule tools/shrink_jar.py used: fully opaque RGBA stored as RGB, best zlib filter
search, kept only when smaller.

  optimize_pngs.py tree <folder>...        optimizes PNG files in place (files > 1 KB)
  optimize_pngs.py jar <in.jar> <out.jar>  writes a copy of a jar with its PNGs optimized (everything else byte-identical)
"""
import io, sys, os, zipfile


def png(data):
    from PIL import Image
    try:
        im = Image.open(io.BytesIO(data))
        im.load()
        if im.mode == 'RGBA' and im.getchannel('A').getextrema() == (255, 255):
            im = im.convert('RGB')
        if im.mode in ('RGB', 'RGBA', 'L', 'LA', 'P'):
            b = io.BytesIO()
            im.save(b, 'PNG', optimize=True)
            if len(b.getvalue()) < len(data):
                return b.getvalue()
    except Exception:
        pass
    return data


if sys.argv[1] == 'tree':
    saved = n = 0
    for root in sys.argv[2:]:
        for dp, _, fs in os.walk(root):
            for f in fs:
                if f.endswith('.png'):
                    p = os.path.join(dp, f)
                    d = open(p, 'rb').read()
                    if len(d) > 1024:
                        o = png(d)
                        if len(o) < len(d):
                            open(p, 'wb').write(o); saved += len(d) - len(o); n += 1
    print(f'optimized {n} PNGs, saved {saved / 1e6:.1f} MB')
else:
    with zipfile.ZipFile(sys.argv[2]) as zi, zipfile.ZipFile(sys.argv[3], 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as zo:
        for info in zi.infolist():
            d = zi.read(info.filename)
            if info.filename.endswith('.png') and len(d) > 1024:
                d = png(d)
            zo.writestr(info, d)
    print(sys.argv[3])
