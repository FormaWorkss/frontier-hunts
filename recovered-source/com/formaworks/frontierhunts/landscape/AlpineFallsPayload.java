package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public record AlpineFallsPayload(String dimension, List<AlpineFallsPayload.Fall> falls) implements CustomPacketPayload {
   public static final Type<AlpineFallsPayload> TYPE = new Type(FrontierHunts.id("falls_sites"));
   public static final StreamCodec<FriendlyByteBuf, AlpineFallsPayload> CODEC = StreamCodec.of(AlpineFallsPayload::write, AlpineFallsPayload::read);

   private static void write(FriendlyByteBuf var0, AlpineFallsPayload var1) {
      var0.writeUtf(var1.dimension);
      var0.writeVarInt(var1.falls.size());

      for (AlpineFallsPayload.Fall var3 : var1.falls) {
         var0.writeInt(var3.lipX);
         var0.writeVarInt(var3.lipY + 128);
         var0.writeInt(var3.lipZ);
         var0.writeVarInt(var3.poolY + 128);
         var0.writeVarInt(var3.width);
         var0.writeFloat(var3.towardX);
         var0.writeFloat(var3.towardZ);
         var0.writeFloat(var3.run);
         var0.writeByte(var3.type);
      }
   }

   private static AlpineFallsPayload read(FriendlyByteBuf var0) {
      String var1 = var0.readUtf();
      int var2 = Math.min(var0.readVarInt(), 512);
      ArrayList var3 = new ArrayList(var2);

      for (int var4 = 0; var4 < var2; var4++) {
         var3.add(
            new AlpineFallsPayload.Fall(
               var0.readInt(),
               var0.readVarInt() - 128,
               var0.readInt(),
               var0.readVarInt() - 128,
               var0.readVarInt(),
               var0.readFloat(),
               var0.readFloat(),
               var0.readFloat(),
               var0.readByte()
            )
         );
      }

      return new AlpineFallsPayload(var1, List.copyOf(var3));
   }

   public Type<? extends CustomPacketPayload> type() {
      return TYPE;
   }

   public static record Fall(int lipX, int lipY, int lipZ, int poolY, int width, float towardX, float towardZ, float run, byte type) {
      static AlpineFallsPayload.Fall of(AlpineFalls.Site var0) {
         double var1 = var0.originX - ((double)var0.lipX + 0.5);
         double var3 = var0.originZ - ((double)var0.lipZ + 0.5);
         double var5 = Math.hypot(var1, var3);
         if (var5 < 0.001) {
            var1 = var0.dirX;
            var3 = var0.dirZ;
            var5 = Math.max(0.001, Math.hypot(var1, var3));
         }
         double var7 = switch (var0.type) {
            case SLIDE, TIERED -> Math.min(Math.max(2.0, var5 - 3.0), (double)var0.drop() * 1.3);
            default -> 1.2;
         };
         return new AlpineFallsPayload.Fall(
            var0.lipX,
            var0.lipLevel,
            var0.lipZ,
            var0.poolLevel,
            Math.max(1, var0.lipWidth),
            (float)(var1 / var5),
            (float)(var3 / var5),
            (float)var7,
            (byte)var0.type.ordinal()
         );
      }

      int drop() {
         return this.lipY - this.poolY;
      }
   }
}
