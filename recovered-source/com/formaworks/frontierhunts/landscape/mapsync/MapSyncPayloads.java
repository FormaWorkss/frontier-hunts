package com.formaworks.frontierhunts.landscape.mapsync;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import org.slf4j.Logger;

public final class MapSyncPayloads {
   public static final int MAX_REGIONS = 65536;
   static final Logger LOG = LogUtils.getLogger();

   private MapSyncPayloads() {
   }

   public static long key(int var0, int var1) {
      return (long)var0 << 32 ^ (long)var1 & 4294967295L;
   }

   public static int keyX(long var0) {
      return (int)(var0 >> 32);
   }

   public static int keyZ(long var0) {
      return (int)var0;
   }

   public static record Ack(int nonce, int batches) implements CustomPacketPayload {
      public static final Type<MapSyncPayloads.Ack> TYPE = new Type(FrontierHunts.id("map_ack"));
      public static final StreamCodec<FriendlyByteBuf, MapSyncPayloads.Ack> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeInt(var1.nonce);
         var0.writeVarInt(var1.batches);
      }, var0 -> new MapSyncPayloads.Ack(var0.readInt(), var0.readVarInt()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Batch(String dimension, int nonce, int regionX, int regionZ, int version, boolean last, int chunks, byte[] data)
      implements CustomPacketPayload {
      public static final Type<MapSyncPayloads.Batch> TYPE = new Type(FrontierHunts.id("map_batch"));
      public static final StreamCodec<FriendlyByteBuf, MapSyncPayloads.Batch> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeUtf(var1.dimension);
            var0.writeInt(var1.nonce);
            var0.writeInt(var1.regionX);
            var0.writeInt(var1.regionZ);
            var0.writeInt(var1.version);
            var0.writeBoolean(var1.last);
            var0.writeVarInt(var1.chunks);
            var0.writeByteArray(var1.data);
         },
         var0 -> new MapSyncPayloads.Batch(
               var0.readUtf(),
               var0.readInt(),
               var0.readInt(),
               var0.readInt(),
               var0.readInt(),
               var0.readBoolean(),
               var0.readVarInt(),
               var0.readByteArray(1048576)
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Hello(String dimension, int nonce, long[] regions, int[] versions) implements CustomPacketPayload {
      public static final Type<MapSyncPayloads.Hello> TYPE = new Type(FrontierHunts.id("map_hello"));
      public static final StreamCodec<FriendlyByteBuf, MapSyncPayloads.Hello> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeUtf(var1.dimension);
         var0.writeInt(var1.nonce);
         var0.writeVarInt(var1.regions.length);

         for (int var2 = 0; var2 < var1.regions.length; var2++) {
            var0.writeLong(var1.regions[var2]);
            var0.writeInt(var1.versions[var2]);
         }
      }, var0 -> {
         String var1 = var0.readUtf();
         int var2 = var0.readInt();
         int var3 = Math.min(var0.readVarInt(), 65536);
         long[] var4 = new long[var3];
         int[] var5 = new int[var3];

         for (int var6 = 0; var6 < var3; var6++) {
            var4[var6] = var0.readLong();
            var5[var6] = var0.readInt();
         }

         return new MapSyncPayloads.Hello(var1, var2, var4, var5);
      });

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
