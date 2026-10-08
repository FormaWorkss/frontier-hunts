package com.formaworks.frontierhunts.landscape.ride;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;

public final class RidePayloads {
   private RidePayloads() {
   }

   public static record AtvCrash(float speed) implements CustomPacketPayload {
      public static final Type<RidePayloads.AtvCrash> TYPE = new Type(FrontierHunts.id("ride_atv_crash"));
      public static final StreamCodec<FriendlyByteBuf, RidePayloads.AtvCrash> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeFloat(var1.speed), var0 -> new RidePayloads.AtvCrash(var0.readFloat())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Gait(boolean gallop) implements CustomPacketPayload {
      public static final Type<RidePayloads.Gait> TYPE = new Type(FrontierHunts.id("ride_gait"));
      public static final StreamCodec<FriendlyByteBuf, RidePayloads.Gait> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeBoolean(var1.gallop), var0 -> new RidePayloads.Gait(var0.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Glider(byte action, float impact) implements CustomPacketPayload {
      public static final byte OPEN = 0;
      public static final byte FOLD = 1;
      public static final byte CRASH = 2;
      public static final byte FLYING = 3;
      public static final Type<RidePayloads.Glider> TYPE = new Type(FrontierHunts.id("ride_glider"));
      public static final StreamCodec<FriendlyByteBuf, RidePayloads.Glider> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeByte(var1.action);
         var0.writeFloat(var1.impact);
      }, var0 -> new RidePayloads.Glider(var0.readByte(), var0.readFloat()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record HorseState(int horse, float stamina, byte gait, boolean pack) implements CustomPacketPayload {
      public static final Type<RidePayloads.HorseState> TYPE = new Type(FrontierHunts.id("ride_horse_state"));
      public static final StreamCodec<FriendlyByteBuf, RidePayloads.HorseState> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeVarInt(var1.horse);
         var0.writeFloat(var1.stamina);
         var0.writeByte(var1.gait);
         var0.writeBoolean(var1.pack);
      }, var0 -> new RidePayloads.HorseState(var0.readVarInt(), var0.readFloat(), var0.readByte(), var0.readBoolean()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Suit(byte action, float value) implements CustomPacketPayload {
      public static final byte DEPLOY = 0;
      public static final byte IMPACT = 1;
      public static final byte IMPACT_WATER = 2;
      public static final byte STOW = 3;
      public static final Type<RidePayloads.Suit> TYPE = new Type(FrontierHunts.id("ride_wingsuit"));
      public static final StreamCodec<FriendlyByteBuf, RidePayloads.Suit> CODEC = StreamCodec.of((var0, var1) -> {
         var0.writeByte(var1.action);
         var0.writeFloat(var1.value);
      }, var0 -> new RidePayloads.Suit(var0.readByte(), var0.readFloat()));

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
