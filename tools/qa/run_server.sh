#!/bin/bash
# usage: run.sh <gamedir>   (stdin = server console)
S=/home/claude/qaserver
G=${1:-$S/run}
CPF=$S/classpath.txt
ls $S/libs/*.jar > $CPF
MP=$(ls $S/modules/*.jar | tr '\n' ':' | sed 's/:$//')
cd "$G"
exec java -Xmx3G ${QA_JVM_EXTRA} \
 -p "$MP" --add-modules ALL-MODULE-PATH \
 --add-opens java.base/java.util.jar=cpw.mods.securejarhandler \
 --add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler \
 --add-exports java.base/sun.security.util=cpw.mods.securejarhandler \
 --add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming \
 -Djava.net.preferIPv6Addresses=system \
 -DignoreList=mixinextras-neoforge-0.5.3.jar,client-extra,neoforge- \
 -DlegacyClassPath.file=$CPF \
 -cp "$(paste -sd: $CPF)" \
 cpw.mods.bootstraplauncher.BootstrapLauncher \
 --launchTarget forgeserverdev --gameDir . \
 --fml.fmlVersion 4.0.43 --fml.mcVersion 1.21.1 --fml.neoForgeVersion 21.1.248 --fml.neoFormVersion 20240808.144430 nogui
