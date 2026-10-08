#!/bin/bash
# Rebuilds the headless NeoForge 21.1.248 dedicated-server runtime in /home/claude/qaserver from files staged off the
# user's PC (read-only) into /mnt/user-data/uploads:
#   - Gradle module cache jars  C:\Users\austi\.gradle\caches\modules-2\files-2.1\...   (launcher, FML, libraries)
#   - MDG artifacts             C:\Users\austi\Documents\Minecraft-Mod-Builder\build\moddev\artifacts\
#                               neoforge-21.1.248.jar (Minecraft+NeoForge, Mojang names) and
#                               neoforge-21.1.248-client-extra-aka-minecraft-resources.jar (vanilla data/assets)
# The container has no internet, so nothing is downloaded. Only needed if /home/claude/qaserver is gone.
set -e
S=/home/claude/qaserver; U=/mnt/user-data/uploads
A=$U/Minecraft-Mod-Builder/build/moddev/artifacts
mkdir -p $S/libs $S/modules
for j in $(find $U/caches/modules-2 -name '*.jar'); do
   b=$(basename $j)
   case $b in
      bootstraplauncher*|securejarhandler*|asm-*|JarJarFileSystems*) cp $j $S/modules/;;
      neoforge-21.1.248-universal.jar) ;;
      *) cp $j $S/libs/;;
   esac
done
for j in authlib-6.0.54 brigadier-1.3.10 datafixerupper-8.0.16 fastutil-8.5.12 gson-2.10.1 guava-32.1.2-jre joml-1.10.5 \
         log4j-api-2.22.1 logging-1.2.7 slf4j-api-2.0.9 sponge-mixin-0.15.2+mixin.0.8.7; do
   cp /home/claude/fh/lib/$j.jar $S/libs/
done
cp /home/claude/fh/rtlib/*.jar $S/libs/
cp $A/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar $S/libs/
# Production servers have no client classes: strip them so anything our mod touches on the server fails like it would
# there (net.minecraft.client.server.* stays - the vanilla server jar ships LanServerPinger).
python3 - "$A/neoforge-21.1.248.jar" "$S/libs/neoforge-21.1.248-serveronly.jar" <<'P'
import sys, zipfile
src, out = sys.argv[1], sys.argv[2]
drop = ('net/minecraft/client/', 'com/mojang/blaze3d/', 'com/mojang/realmsclient/')
with zipfile.ZipFile(src) as zi, zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zo:
    for i in zi.infolist():
        if i.filename.startswith(drop) and not i.filename.startswith('net/minecraft/client/server/'):
            continue
        zo.writestr(i, zi.read(i.filename))
P
cp "$(dirname "$0")/run_server.sh" $S/run.sh; chmod +x $S/run.sh
echo "assembled $S: $(ls $S/libs | wc -l) libs, $(ls $S/modules | wc -l) modules"
