package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RifleNetwork {
   public static Consumer<RifleNetwork.Shot> receiver = var0 -> {
   };
   public static Consumer<RifleNetwork.Impact> impactReceiver = var0 -> {
   };

   public static void impact(ServerLevel var0, BlockHitResult var1) {
      Vec3 var2 = var1.getLocation();
      BlockState var3 = var0.getBlockState(var1.getBlockPos());
      if (!var3.isAir() && !var3.is(BlockTags.LEAVES)) {
         RifleNetwork.Impact var4 = new RifleNetwork.Impact(
            var0.dimension().location().toString(), var1.getBlockPos(), var1.getDirection(), var2.x, var2.y, var2.z, Block.getId(var3)
         );

         for (ServerPlayer var6 : var0.players()) {
            if (var6.connection != null && var6.connection.hasChannel(RifleNetwork.Impact.TYPE) && var6.distanceToSqr(var2) < 4096.0) {
               PacketDistributor.sendToPlayer(var6, var4, new CustomPacketPayload[0]);
            }
         }
      }
   }

   public static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("2");
      var1.playToServer(RifleNetwork.Action.TYPE, RifleNetwork.Action.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               RifleActions.request(var2, var0x.kind);
            }
         }));
      var1.playToServer(RifleNetwork.Aim.TYPE, RifleNetwork.Aim.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               RifleActions.aim(var2, var0x.down);
            }
         }));
      var1.playToClient(RifleNetwork.Shot.TYPE, RifleNetwork.Shot.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> receiver.accept(var0x)));
      var1.playToClient(RifleNetwork.Impact.TYPE, RifleNetwork.Impact.CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> impactReceiver.accept(var0x)));
   }

   public static record Action(int kind) implements CustomPacketPayload {
      public static final Type<RifleNetwork.Action> TYPE = new Type(FrontierHunts.id("rifle_action"));
      public static final StreamCodec<FriendlyByteBuf, RifleNetwork.Action> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeByte(var1.kind), var0 -> new RifleNetwork.Action(var0.readUnsignedByte())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Aim(boolean down) implements CustomPacketPayload {
      public static final Type<RifleNetwork.Aim> TYPE = new Type(FrontierHunts.id("rifle_aim"));
      public static final StreamCodec<FriendlyByteBuf, RifleNetwork.Aim> CODEC = StreamCodec.of(
         (var0, var1) -> var0.writeBoolean(var1.down), var0 -> new RifleNetwork.Aim(var0.readBoolean())
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Impact(String dimension, BlockPos block, Direction face, double x, double y, double z, int state) implements CustomPacketPayload {
      public static final Type<RifleNetwork.Impact> TYPE = new Type(FrontierHunts.id("bullet_impact"));
      public static final StreamCodec<FriendlyByteBuf, RifleNetwork.Impact> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeUtf(var1.dimension, 256);
            var0.writeBlockPos(var1.block);
            var0.writeEnum(var1.face);
            var0.writeDouble(var1.x);
            var0.writeDouble(var1.y);
            var0.writeDouble(var1.z);
            var0.writeVarInt(var1.state);
         },
         var0 -> new RifleNetwork.Impact(
               var0.readUtf(256),
               var0.readBlockPos(),
               (Direction)var0.readEnum(Direction.class),
               var0.readDouble(),
               var0.readDouble(),
               var0.readDouble(),
               var0.readVarInt()
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   public static record Shot(String dimension, int shooter, double x, double y, double z, float yaw, float pitch) implements CustomPacketPayload {
      public static final Type<RifleNetwork.Shot> TYPE = new Type(FrontierHunts.id("rifle_shot"));
      public static final StreamCodec<FriendlyByteBuf, RifleNetwork.Shot> CODEC = StreamCodec.of(
         (var0, var1) -> {
            var0.writeUtf(var1.dimension, 256);
            var0.writeVarInt(var1.shooter);
            var0.writeDouble(var1.x);
            var0.writeDouble(var1.y);
            var0.writeDouble(var1.z);
            var0.writeFloat(var1.yaw);
            var0.writeFloat(var1.pitch);
         },
         var0 -> new RifleNetwork.Shot(
               var0.readUtf(256), var0.readVarInt(), var0.readDouble(), var0.readDouble(), var0.readDouble(), var0.readFloat(), var0.readFloat()
            )
      );

      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
