package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineFallsSync {
   private static final int RADIUS = 3;
   private static final Map<ServerLevel, AlpineFallsSync.Tracker> TRACKERS = new WeakHashMap<>();
   private static final Map<UUID, String> SENT = new HashMap<>();

   @SubscribeEvent
   public static void tick(Post var0) {
      MinecraftServer var1 = var0.getServer();
      if (var1.getTickCount() % 40 == 7) {
         for (ServerPlayer var3 : var1.getPlayerList().getPlayers()) {
            ServerLevel var4 = var3.serverLevel();
            ChunkGenerator var6 = var4.getChunkSource().getGenerator();
            if (var6 instanceof AlpineGenerator) {
               AlpineGenerator var5 = (AlpineGenerator)var6;
               AlpineLayout var20 = var5.layout();
               if (var20.version() >= 13 && var20.watershed() != null) {
                  AlpineFallsSync.Tracker var7 = TRACKERS.computeIfAbsent(var4, var1x -> {
                     AlpineFalls var2 = var20.watershed().designer();
                     return var2 == null ? null : new AlpineFallsSync.Tracker(var2);
                  });
                  if (var7 != null) {
                     int var8 = Math.floorDiv(var3.getBlockX(), 384);
                     int var9 = Math.floorDiv(var3.getBlockZ(), 384);
                     boolean var10 = true;
                     ArrayList var11 = new ArrayList();

                     for (int var12 = -3; var12 <= 3; var12++) {
                        for (int var13 = -3; var13 <= 3; var13++) {
                           int var14 = var8 + var12;
                           int var15 = var9 + var13;
                           long var16 = (long)var14 << 32 ^ (long)var15 & 4294967295L;
                           Optional var18 = var7.cells.get(var16);
                           if (var18 == null) {
                              var10 = false;
                              if (var7.pending.add(var16)) {
                                 Util.backgroundExecutor().execute(() -> {
                                    AlpineFallsPayload.Fall var5x = null;

                                    try {
                                       AlpineFalls.Site var6x = var7.designer.site(var14, var15);
                                       if (var6x != null) {
                                          var5x = AlpineFallsPayload.Fall.of(var6x);
                                       }
                                    } catch (RuntimeException var7x) {
                                    }

                                    var7.cells.put(var16, Optional.ofNullable(var5x));
                                    var7.pending.remove(var16);
                                 });
                              }
                           } else {
                              var18.ifPresent(var11::add);
                           }
                        }
                     }

                     if (var10) {
                        String var21 = var4.dimension().location() + "@" + var8 + "," + var9 + "#" + var11.size();
                        if (!var21.equals(SENT.get(var3.getUUID()))) {
                           SENT.put(var3.getUUID(), var21);

                           try {
                              if (var3.connection.hasChannel(AlpineFallsPayload.TYPE)) {
                                 PacketDistributor.sendToPlayer(
                                    var3, new AlpineFallsPayload(var4.dimension().location().toString(), var11), new CustomPacketPayload[0]
                                 );
                              }
                           } catch (RuntimeException var19) {
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      SENT.remove(var0.getEntity().getUUID());
   }

   @SubscribeEvent
   public static void changed(PlayerChangedDimensionEvent var0) {
      SENT.remove(var0.getEntity().getUUID());
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void payloads(RegisterPayloadHandlersEvent var0) {
         var0.registrar("1")
            .optional()
            .playToClient(AlpineFallsPayload.TYPE, AlpineFallsPayload.CODEC, (var0x, var1) -> var1.enqueueWork(() -> AlpineFallsClientHook.receive(var0x)));
      }
   }

   private static final class Tracker {
      final AlpineFalls designer;
      final ConcurrentHashMap<Long, Optional<AlpineFallsPayload.Fall>> cells = new ConcurrentHashMap<>();
      final Set<Long> pending = ConcurrentHashMap.newKeySet();

      Tracker(AlpineFalls var1) {
         this.designer = var1;
      }
   }
}
