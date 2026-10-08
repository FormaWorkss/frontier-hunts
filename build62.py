import zipfile, os, hashlib
src='orig62.jar'
VER_OLD='0.1.0-dev.62-landscape.62-gear.3'
VER_NEW='0.1.0-dev.62-landscape.62-gear.8'
out='merged62g8.jar'
families=set(); patch_files={}
for base in ('out','patch'):
    for root,_,files in os.walk(base):
        for f in files:
            p=os.path.join(root,f); rel=os.path.relpath(p,base)
            patch_files[rel]=open(p,'rb').read()
            if base=='out': families.add(rel.split('$')[0].replace('.class',''))
if os.path.exists(out): os.remove(out)
removed=0
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as zo:
    for info in zi.infolist():
        n=info.filename
        if n.endswith('.class') and n.split('$')[0].replace('.class','') in families: removed+=1; continue
        if n in patch_files: continue
        data=zi.read(n)
        if n=='META-INF/neoforge.mods.toml': data=data.decode().replace(f'version="{VER_OLD}"',f'version="{VER_NEW}"').encode()
        if n=='META-INF/MANIFEST.MF': data=data.decode().replace(f'Implementation-Version: {VER_OLD}',f'Implementation-Version: {VER_NEW}').encode()
        zo.writestr(info,data)
    for n,data in sorted(patch_files.items()): zo.writestr(n,data)
print('removed',removed,'added',len(patch_files))
print(hashlib.sha256(open(out,'rb').read()).hexdigest())
