package com.formaworks.frontierhunts.landscape.mapsync;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class MapSyncClient {
   private static final long FRAME_NANOS = 900000L;
   private static final long SCREEN_NANOS = 4000000L;
   private static Boolean xaero;
   private static boolean failed;
   private static String sessionKey;
   private static ClientLevel sessionLevel;
   private static String sessionDim;
   private static final Map<Long, Integer> index = new HashMap<>();
   private static boolean indexDirty;
   private static long indexSavedAt;
   private static final LinkedHashMap<Long, MapSyncClient.RegionWork> work = new LinkedHashMap<>();
   private static int acks;
   private static int nonce;
   private static long lastHelloAttempt;
   private static int drawn;

   private MapSyncClient() {
   }

   private static boolean enabled() {
      if (xaero == null) {
         xaero = ModList.get().isLoaded("xaeroworldmap");
      }

      return xaero && !failed;
   }

   static void receive(MapSyncPayloads.Batch var0, IPayloadContext var1) {
      var1.enqueueWork(
         () -> {
            Minecraft var1x = Minecraft.getInstance();
            if (var0.nonce() == nonce) {
               if (enabled() && var1x.level != null && sessionKey != null && var0.dimension().equals(sessionDim)) {
                  ArrayList var2 = new ArrayList(var0.chunks());

                  try {
                     byte[] var3 = MapColumns.inflate(var0.data(), 16777216);
                     MapColumns.In var4 = new MapColumns.In(var3, 0, var3.length);
                     int var5 = var1x.level.getMinBuildHeight();

                     while (var4.more()) {
                        int var6 = var4.varint();
                        int var7 = var4.varint();
                        int var8 = var4.position();
                        int var9 = (var0.regionX() << 5) + (var6 & 31);
                        int var10 = (var0.regionZ() << 5) + (var6 >> 5 & 31);

                        try {
                           var2.add(MapColumns.decode(var9, var10, var3, var8, var7, var5));
                        } catch (RuntimeException var12) {
                        }

                        var4.skip(var7);
                     }
                  } catch (RuntimeException var13) {
                     MapSyncPayloads.LOG.debug("Frontier map preload: unreadable batch", var13);
                  }

                  long var14 = MapSyncPayloads.key(var0.regionX(), var0.regionZ());
                  work.computeIfAbsent(Long.valueOf(var14), var1xx -> new MapSyncClient.RegionWork(var0.regionX(), var0.regionZ()))
                     .queue
                     .add(new MapSyncClient.Pending(var2, var0.last(), var0.version()));
               } else {
                  acks++;
               }
            }
         }
      );
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (failed && acks > 0) {
         sendAcks();
      }

      if (enabled()) {
         Minecraft var1 = Minecraft.getInstance();
         ClientLevel var2 = var1.level;
         if (var2 != null && var1.player != null) {
            try {
               String var3 = XaeroFeed.sessionKey(var2);
               if (var3 == null) {
                  if (sessionLevel != var2 && sessionKey != null) {
                     reset();
                  }

                  return;
               }

               if (!var3.equals(sessionKey) || sessionLevel != var2) {
                  long var4 = System.currentTimeMillis();
                  if (var4 - lastHelloAttempt < 2000L) {
                     return;
                  }

                  lastHelloAttempt = var4;
                  if (!hasChannel(var1)) {
                     return;
                  }

                  reset();
                  sessionKey = var3;
                  sessionLevel = var2;
                  sessionDim = var2.dimension().location().toString();
                  loadIndex();
                  hello();
                  return;
               }

               if (acks > 0) {
                  sendAcks();
               }

               if (indexDirty && System.currentTimeMillis() - indexSavedAt > 10000L) {
                  saveIndex();
               }
            } catch (Throwable var6) {
               failed = true;
               MapSyncPayloads.LOG
                  .warn("Frontier map preload stopped: Xaero's World Map could not take the data ({}). Your map is unaffected.", var6.toString());
               MapSyncPayloads.LOG.debug("Frontier map preload failure", var6);
            }
         } else {
            reset();
         }
      }
   }

   @SubscribeEvent
   public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post var0) {
      if (enabled() && sessionKey != null && !work.isEmpty()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null && var1.player != null && var1.level == sessionLevel) {
            try {
               pump(var1.level, var1, System.nanoTime() + (var1.screen == null && !var1.isPaused() ? 900000L : 4000000L));
            } catch (Throwable var3) {
               failed = true;
               MapSyncPayloads.LOG
                  .warn("Frontier map preload stopped: Xaero's World Map could not take the data ({}). Your map is unaffected.", var3.toString());
               MapSyncPayloads.LOG.debug("Frontier map preload failure", var3);
            }
         }
      }
   }

   private static void sendAcks() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.getConnection() != null && hasChannel(var0)) {
         PacketDistributor.sendToServer(new MapSyncPayloads.Ack(nonce, acks), new CustomPacketPayload[0]);
      }

      acks = 0;
   }

   private static boolean hasChannel(Minecraft var0) {
      ClientPacketListener var1 = var0.getConnection();

      try {
         return var1 != null && var1.hasChannel(MapSyncPayloads.Hello.TYPE);
      } catch (RuntimeException var3) {
         return false;
      }
   }

   private static void hello() {
      ArrayList var0 = new ArrayList();
      ArrayList var1 = new ArrayList();

      for (Entry var3 : index.entrySet()) {
         int var4 = MapSyncPayloads.keyX((Long)var3.getKey());
         int var5 = MapSyncPayloads.keyZ((Long)var3.getKey());
         if (XaeroFeed.regionKnown(var4, var5)) {
            var0.add((Long)var3.getKey());
            var1.add((Integer)var3.getValue());
            if (var0.size() >= 65536) {
               break;
            }
         }
      }

      long[] var6 = new long[var0.size()];
      int[] var7 = new int[var0.size()];

      for (int var8 = 0; var8 < var6.length; var8++) {
         var6[var8] = (Long)var0.get(var8);
         var7[var8] = (Integer)var1.get(var8);
      }

      nonce = new Random().nextInt();
      PacketDistributor.sendToServer(new MapSyncPayloads.Hello(sessionDim, nonce, var6, var7), new CustomPacketPayload[0]);
   }

   private static void pump(ClientLevel param0, Minecraft param1, long param2) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.IllegalStateException: No successor exists for {If}:45
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.Statement.getFirstSuccessor(Statement.java:834)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfNode.build(IfNode.java:90)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeIfs(IfHelper.java:75)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:38)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfsRec(IfHelper.java:35)
      //   at org.jetbrains.java.decompiler.modules.decompiler.IfHelper.mergeAllIfs(IfHelper.java:20)
      //   at org.jetbrains.java.decompiler.main.rels.MethodProcessor.codeToJava(MethodProcessor.java:280)
      //
      // Bytecode:
      // 000: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.work Ljava/util/LinkedHashMap;
      // 003: invokevirtual java/util/LinkedHashMap.isEmpty ()Z
      // 006: ifeq 00a
      // 009: return
      // 00a: aload 1
      // 00b: getfield net/minecraft/client/Minecraft.player Lnet/minecraft/client/player/LocalPlayer;
      // 00e: invokevirtual net/minecraft/client/player/LocalPlayer.getBlockX ()I
      // 011: bipush 9
      // 013: ishr
      // 014: istore 4
      // 016: aload 1
      // 017: getfield net/minecraft/client/Minecraft.player Lnet/minecraft/client/player/LocalPlayer;
      // 01a: invokevirtual net/minecraft/client/player/LocalPlayer.getBlockZ ()I
      // 01d: bipush 9
      // 01f: ishr
      // 020: istore 5
      // 022: new java/util/ArrayList
      // 025: dup
      // 026: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.work Ljava/util/LinkedHashMap;
      // 029: invokevirtual java/util/LinkedHashMap.values ()Ljava/util/Collection;
      // 02c: invokespecial java/util/ArrayList.<init> (Ljava/util/Collection;)V
      // 02f: astore 6
      // 031: aload 6
      // 033: iload 4
      // 035: iload 5
      // 037: invokedynamic applyAsLong (II)Ljava/util/function/ToLongFunction; bsm=java/lang/invoke/LambdaMetafactory.metafactory (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite; args=[ (Ljava/lang/Object;)J, com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.lambda$pump$2 (IILcom/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork;)J, (Lcom/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork;)J ]
      // 03c: invokestatic java/util/Comparator.comparingLong (Ljava/util/function/ToLongFunction;)Ljava/util/Comparator;
      // 03f: invokeinterface java/util/List.sort (Ljava/util/Comparator;)V 2
      // 044: invokestatic java/lang/System.currentTimeMillis ()J
      // 047: lstore 7
      // 049: bipush 0
      // 04a: istore 9
      // 04c: aload 6
      // 04e: invokeinterface java/util/List.iterator ()Ljava/util/Iterator; 1
      // 053: astore 10
      // 055: aload 10
      // 057: invokeinterface java/util/Iterator.hasNext ()Z 1
      // 05c: ifeq 21f
      // 05f: aload 10
      // 061: invokeinterface java/util/Iterator.next ()Ljava/lang/Object; 1
      // 066: checkcast com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork
      // 069: astore 11
      // 06b: invokestatic java/lang/System.nanoTime ()J
      // 06e: lload 2
      // 06f: lcmp
      // 070: iflt 076
      // 073: goto 21f
      // 076: aload 11
      // 078: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.retryAt J
      // 07b: lload 7
      // 07d: lcmp
      // 07e: ifle 084
      // 081: goto 055
      // 084: aload 11
      // 086: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 089: invokevirtual java/util/ArrayDeque.isEmpty ()Z
      // 08c: ifne 1fa
      // 08f: invokestatic java/lang/System.nanoTime ()J
      // 092: lload 2
      // 093: lcmp
      // 094: ifge 1fa
      // 097: aload 11
      // 099: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 09c: invokevirtual java/util/ArrayDeque.peek ()Ljava/lang/Object;
      // 09f: checkcast com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending
      // 0a2: astore 12
      // 0a4: aload 12
      // 0a6: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.chunks Ljava/util/List;
      // 0a9: invokeinterface java/util/List.isEmpty ()Z 1
      // 0ae: ifne 1ac
      // 0b1: iload 9
      // 0b3: bipush 2
      // 0b4: if_icmplt 0ba
      // 0b7: goto 1fa
      // 0ba: aload 12
      // 0bc: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.chunks Ljava/util/List;
      // 0bf: invokeinterface java/util/List.size ()I 1
      // 0c4: istore 13
      // 0c6: aload 0
      // 0c7: aload 11
      // 0c9: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rx I
      // 0cc: aload 11
      // 0ce: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rz I
      // 0d1: aload 12
      // 0d3: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.chunks Ljava/util/List;
      // 0d6: lload 2
      // 0d7: invokestatic com/formaworks/frontierhunts/landscape/mapsync/XaeroFeed.write (Lnet/minecraft/client/multiplayer/ClientLevel;IILjava/util/List;J)I
      // 0da: istore 14
      // 0dc: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.drawn I
      // 0df: iload 13
      // 0e1: aload 12
      // 0e3: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.chunks Ljava/util/List;
      // 0e6: invokeinterface java/util/List.size ()I 1
      // 0eb: isub
      // 0ec: iadd
      // 0ed: putstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.drawn I
      // 0f0: iload 14
      // 0f2: bipush 1
      // 0f3: if_icmpne 13e
      // 0f6: iinc 9 1
      // 0f9: aload 11
      // 0fb: lload 7
      // 0fd: ldc2_w 250
      // 100: ladd
      // 101: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.retryAt J
      // 104: aload 11
      // 106: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.loadingSince J
      // 109: lconst_0
      // 10a: lcmp
      // 10b: ifne 115
      // 10e: aload 11
      // 110: lload 7
      // 112: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.loadingSince J
      // 115: lload 7
      // 117: aload 11
      // 119: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.loadingSince J
      // 11c: lsub
      // 11d: ldc2_w 60000
      // 120: lcmp
      // 121: ifle 1fa
      // 124: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 127: aload 11
      // 129: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 12c: invokevirtual java/util/ArrayDeque.size ()I
      // 12f: iadd
      // 130: putstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 133: aload 11
      // 135: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 138: invokevirtual java/util/ArrayDeque.clear ()V
      // 13b: goto 1fa
      // 13e: aload 11
      // 140: lconst_0
      // 141: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.loadingSince J
      // 144: iload 14
      // 146: bipush 3
      // 147: if_icmpne 14b
      // 14a: return
      // 14b: iload 14
      // 14d: bipush 2
      // 14e: if_icmpne 196
      // 151: aload 11
      // 153: lload 7
      // 155: ldc2_w 100
      // 158: ladd
      // 159: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.retryAt J
      // 15c: aload 11
      // 15e: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.stuckSince J
      // 161: lconst_0
      // 162: lcmp
      // 163: ifne 16d
      // 166: aload 11
      // 168: lload 7
      // 16a: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.stuckSince J
      // 16d: lload 7
      // 16f: aload 11
      // 171: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.stuckSince J
      // 174: lsub
      // 175: ldc2_w 60000
      // 178: lcmp
      // 179: ifle 1fa
      // 17c: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 17f: aload 11
      // 181: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 184: invokevirtual java/util/ArrayDeque.size ()I
      // 187: iadd
      // 188: putstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 18b: aload 11
      // 18d: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 190: invokevirtual java/util/ArrayDeque.clear ()V
      // 193: goto 1fa
      // 196: aload 11
      // 198: lconst_0
      // 199: putfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.stuckSince J
      // 19c: aload 12
      // 19e: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.chunks Ljava/util/List;
      // 1a1: invokeinterface java/util/List.isEmpty ()Z 1
      // 1a6: ifne 1ac
      // 1a9: goto 1fa
      // 1ac: aload 11
      // 1ae: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 1b1: invokevirtual java/util/ArrayDeque.poll ()Ljava/lang/Object;
      // 1b4: pop
      // 1b5: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 1b8: bipush 1
      // 1b9: iadd
      // 1ba: putstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.acks I
      // 1bd: aload 12
      // 1bf: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.last Z
      // 1c2: ifeq 1f7
      // 1c5: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.index Ljava/util/Map;
      // 1c8: aload 11
      // 1ca: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rx I
      // 1cd: aload 11
      // 1cf: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rz I
      // 1d2: invokestatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncPayloads.key (II)J
      // 1d5: invokestatic java/lang/Long.valueOf (J)Ljava/lang/Long;
      // 1d8: aload 12
      // 1da: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$Pending.version I
      // 1dd: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
      // 1e0: invokeinterface java/util/Map.put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; 3
      // 1e5: pop
      // 1e6: bipush 1
      // 1e7: putstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.indexDirty Z
      // 1ea: aload 11
      // 1ec: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rx I
      // 1ef: aload 11
      // 1f1: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rz I
      // 1f4: invokestatic com/formaworks/frontierhunts/landscape/mapsync/XaeroFeed.flush (II)V
      // 1f7: goto 084
      // 1fa: aload 11
      // 1fc: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.queue Ljava/util/ArrayDeque;
      // 1ff: invokevirtual java/util/ArrayDeque.isEmpty ()Z
      // 202: ifeq 21c
      // 205: getstatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient.work Ljava/util/LinkedHashMap;
      // 208: aload 11
      // 20a: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rx I
      // 20d: aload 11
      // 20f: getfield com/formaworks/frontierhunts/landscape/mapsync/MapSyncClient$RegionWork.rz I
      // 212: invokestatic com/formaworks/frontierhunts/landscape/mapsync/MapSyncPayloads.key (II)J
      // 215: invokestatic java/lang/Long.valueOf (J)Ljava/lang/Long;
      // 218: invokevirtual java/util/LinkedHashMap.remove (Ljava/lang/Object;)Ljava/lang/Object;
      // 21b: pop
      // 21c: goto 055
      // 21f: return
   }

   @SubscribeEvent
   public static void loggingOut(LoggingOut var0) {
      reset();
   }

   private static void reset() {
      if (indexDirty) {
         saveIndex();
      }

      work.clear();
      acks = 0;
      sessionKey = null;
      sessionLevel = null;
      sessionDim = null;
      index.clear();
      indexDirty = false;
   }

   private static Path indexFile() {
      String var0 = Integer.toHexString(sessionKey.hashCode()) + "-" + sessionKey.replaceAll("[^A-Za-z0-9._-]", "_");
      if (var0.length() > 120) {
         var0 = var0.substring(0, 120);
      }

      return Minecraft.getInstance().gameDirectory.toPath().resolve("frontierhunts").resolve("mapsync").resolve(var0 + ".dat");
   }

   private static void loadIndex() {
      index.clear();
      Path var0 = indexFile();
      if (Files.exists(var0)) {
         try {
            try (DataInputStream var1 = new DataInputStream(new BufferedInputStream(Files.newInputStream(var0)))) {
               if (var1.readInt() != 1179143473) {
                  return;
               }

               String var2 = var1.readUTF();
               if (var2.equals(sessionKey)) {
                  int var3 = var1.readInt();

                  for (int var4 = 0; var4 < var3 && var4 < 1000000; var4++) {
                     index.put(var1.readLong(), var1.readInt());
                  }

                  return;
               }
            }
         } catch (IOException var7) {
            index.clear();
         }
      }
   }

   private static void saveIndex() {
      indexSavedAt = System.currentTimeMillis();
      if (sessionKey == null) {
         indexDirty = false;
      } else {
         Path var0 = indexFile();

         try {
            Files.createDirectories(var0.getParent());
            Path var1 = var0.resolveSibling(var0.getFileName() + ".tmp");

            try (DataOutputStream var2 = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(var1)))) {
               var2.writeInt(1179143473);
               var2.writeUTF(sessionKey);
               var2.writeInt(index.size());

               for (Entry var4 : index.entrySet()) {
                  var2.writeLong((Long)var4.getKey());
                  var2.writeInt((Integer)var4.getValue());
               }
            }

            Files.move(var1, var0, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            indexDirty = false;
         } catch (IOException var7) {
            MapSyncPayloads.LOG.debug("Frontier map preload: cannot save index", var7);
         }
      }
   }

   public static int drawn() {
      return drawn;
   }

   public static int queued() {
      int var0 = 0;

      for (MapSyncClient.RegionWork var2 : work.values()) {
         for (MapSyncClient.Pending var4 : var2.queue) {
            var0 += var4.chunks.size();
         }
      }

      return var0;
   }

   private static final class Pending {
      final List<MapColumns.Decoded> chunks;
      final boolean last;
      final int version;

      Pending(List<MapColumns.Decoded> var1, boolean var2, int var3) {
         this.chunks = var1;
         this.last = var2;
         this.version = var3;
      }
   }

   private static final class RegionWork {
      final int rx;
      final int rz;
      final ArrayDeque<MapSyncClient.Pending> queue = new ArrayDeque<>();
      long retryAt;
      long loadingSince;
      long stuckSince;

      RegionWork(int var1, int var2) {
         this.rx = var1;
         this.rz = var2;
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent var0) {
         MapSyncBridge.handler = MapSyncClient::receive;
      }
   }
}
