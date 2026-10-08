package com.formaworks.frontierhunts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HuntNetwork {
   public static Consumer<HuntNetwork.Snapshot> clientReceiver = var0 -> {
   };
   public static Consumer<HuntNetwork.HuntProgress> huntReceiver = var0 -> {
   };

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("2");
      var1.playToServer(HuntNetwork.Request.TYPE, HuntNetwork.Request.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               HuntService.request(var2, var0x);
            }
         }));
      var1.playToClient(HuntNetwork.Snapshot.TYPE, HuntNetwork.Snapshot.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientReceiver.accept(var0x)));
      var1.playToClient(HuntNetwork.HuntProgress.TYPE, HuntNetwork.HuntProgress.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> huntReceiver.accept(var0x)));
   }

   private HuntNetwork() {
   }

   public static record HuntProgress(String dimension, boolean clue, boolean harvested, boolean claimed) implements CustomPacketPayload {
      public static final Type<HuntNetwork.HuntProgress> TYPE = new Type(FrontierHunts.id("hunt_progress"));
      public static final StreamCodec<FriendlyByteBuf, HuntNetwork.HuntProgress> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeUtf(var1.dimension, 256);
         var0.writeBoolean(var1.clue);
         var0.writeBoolean(var1.harvested);
         var0.writeBoolean(var1.claimed);
      }, var0 -> new HuntNetwork.HuntProgress(var0.readUtf(256), var0.readBoolean(), var0.readBoolean(), var0.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Request(int action) implements CustomPacketPayload {
      public static final int OPEN = 0;
      public static final int CLAIM_SURVEY = 1;
      public static final int CLAIM_FIRST_HUNT = 2;
      public static final Type<HuntNetwork.Request> TYPE = new Type(FrontierHunts.id("journal_request"));
      public static final StreamCodec<FriendlyByteBuf, HuntNetwork.Request> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeVarInt(var1.action), var0 -> new HuntNetwork.Request(var0.readVarInt())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Snapshot(
      boolean open,
      boolean active,
      boolean tracking,
      String dimension,
      String region,
      String biome,
      String description,
      int threat,
      int tokens,
      int discovered,
      boolean surveyReady,
      boolean surveyClaimed,
      int reward,
      float windEast,
      float windSouth,
      boolean rain,
      String assistance,
      List<String> recentBiomes
   ) implements CustomPacketPayload {
      public static final Type<HuntNetwork.Snapshot> TYPE = new Type(FrontierHunts.id("journal_snapshot"));
      public static final StreamCodec<FriendlyByteBuf, HuntNetwork.Snapshot> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeBoolean(var1.open);
            var0.writeBoolean(var1.active);
            var0.writeBoolean(var1.tracking);
            var0.writeUtf(var1.dimension, 256);
            var0.writeUtf(var1.region, 80);
            var0.writeUtf(var1.biome, 256);
            var0.writeUtf(var1.description, 1024);
            var0.writeVarInt(var1.threat);
            var0.writeVarInt(var1.tokens);
            var0.writeVarInt(var1.discovered);
            var0.writeBoolean(var1.surveyReady);
            var0.writeBoolean(var1.surveyClaimed);
            var0.writeVarInt(var1.reward);
            var0.writeFloat(var1.windEast);
            var0.writeFloat(var1.windSouth);
            var0.writeBoolean(var1.rain);
            var0.writeUtf(var1.assistance, 24);
            var0.writeVarInt(var1.recentBiomes.size());

            for (String var3 : var1.recentBiomes) {
               var0.writeUtf(var3, 256);
            }
         },
         var0 -> {
            boolean var1 = var0.readBoolean();
            boolean var2 = var0.readBoolean();
            boolean var3 = var0.readBoolean();
            String var4 = var0.readUtf(256);
            String var5 = var0.readUtf(80);
            String var6 = var0.readUtf(256);
            String var7 = var0.readUtf(1024);
            int var8 = var0.readVarInt();
            int var9 = var0.readVarInt();
            int var10 = var0.readVarInt();
            boolean var11 = var0.readBoolean();
            boolean var12 = var0.readBoolean();
            int var13 = var0.readVarInt();
            float var14 = var0.readFloat();
            float var15 = var0.readFloat();
            boolean var16 = var0.readBoolean();
            String var17 = var0.readUtf(24);
            int var18 = var0.readVarInt();
            if (var18 >= 0
               && var18 <= 16
               && var8 >= 1
               && var8 <= 5
               && var9 >= 0
               && var10 >= 0
               && var10 <= 2048
               && var13 >= 0
               && var13 <= 10000
               && Float.isFinite(var14)
               && Float.isFinite(var15)) {
               ArrayList var19 = new ArrayList();

               for (int var20 = 0; var20 < var18; var20++) {
                  var19.add(var0.readUtf(256));
               }

               return new HuntNetwork.Snapshot(
                  var1, var2, var3, var4, var5, var6, var7, var8, var9, var10, var11, var12, var13, var14, var15, var16, var17, var19
               );
            } else {
               throw new IllegalArgumentException("Invalid Frontier journal snapshot");
            }
         }
      );

      public Snapshot(
         boolean open,
         boolean active,
         boolean tracking,
         String dimension,
         String region,
         String biome,
         String description,
         int threat,
         int tokens,
         int discovered,
         boolean surveyReady,
         boolean surveyClaimed,
         int reward,
         float windEast,
         float windSouth,
         boolean rain,
         String assistance,
         List<String> recentBiomes
      ) {
         recentBiomes = List.copyOf(recentBiomes);
         this.open = open;
         this.active = active;
         this.tracking = tracking;
         this.dimension = dimension;
         this.region = region;
         this.biome = biome;
         this.description = description;
         this.threat = threat;
         this.tokens = tokens;
         this.discovered = discovered;
         this.surveyReady = surveyReady;
         this.surveyClaimed = surveyClaimed;
         this.reward = reward;
         this.windEast = windEast;
         this.windSouth = windSouth;
         this.rain = rain;
         this.assistance = assistance;
         this.recentBiomes = recentBiomes;
      }

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
