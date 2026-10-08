package com.formaworks.frontierhunts.killcam;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Kill cam payloads. The server is the only authority: it tells the shooter's client that a shot is (predicted to be,
 * then confirmed as) lethal, with everything needed to replay it. The client never reports hits.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class KillCamNetwork {
   /** Set by the client setup; stays a no-op on a dedicated server. */
   public static Consumer<Shot> shotReceiver = s -> {
   };
   public static Consumer<Cancel> cancelReceiver = c -> {
   };
   /** [killcam2] Far-target appearance (see {@link Appearance}). */
   public static Consumer<Appearance> appearanceReceiver = a -> {
   };

   public static final int MAX_PATH = 96;

   public static final byte RIFLE = 0;
   public static final byte ARROW = 1;
   public static final byte BOLT = 2;
   public static final byte FIREARM = 3;

   private KillCamNetwork() {
   }

   @SubscribeEvent
   public static void register(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToClient(Shot.TYPE, Shot.CODEC, (p, ctx) -> ctx.enqueueWork(() -> shotReceiver.accept(p)));
      r.playToClient(Cancel.TYPE, Cancel.CODEC, (p, ctx) -> ctx.enqueueWork(() -> cancelReceiver.accept(p)));
      r.playToClient(Appearance.TYPE, Appearance.CODEC, (p, ctx) -> ctx.enqueueWork(() -> appearanceReceiver.accept(p)));
      r.playToServer(Prefs.TYPE, Prefs.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) {
            KillCamServer.prefs(sp, p.mode());
         }
      }));
   }

   static void send(ServerPlayer player, CustomPacketPayload payload) {
      if (player.connection != null && player.connection.hasChannel(payload.type())) {
         PacketDistributor.sendToPlayer(player, payload);
      }
   }

   private static void vec(FriendlyByteBuf b, Vec3 v) {
      b.writeDouble(v.x);
      b.writeDouble(v.y);
      b.writeDouble(v.z);
   }

   private static Vec3 vec(FriendlyByteBuf b) {
      double x = b.readDouble();
      double y = b.readDouble();
      double z = b.readDouble();
      return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) ? new Vec3(x, y, z) : Vec3.ZERO;
   }

   private static Vec3 unit(FriendlyByteBuf b) {
      Vec3 v = new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
      return Double.isFinite(v.lengthSqr()) && v.lengthSqr() > 1.0E-8 ? v.normalize() : new Vec3(0.0, 0.0, 1.0);
   }

   /**
    * One shot. {@code confirmed=false} is the instant, predicted start (sent the tick the projectile spawns);
    * {@code confirmed=true} carries the real server hit result and is the only thing that lets a replay finish.
    */
   public record Shot(
      int shotId,
      boolean confirmed,
      byte weapon,
      byte tip,
      boolean primitive,
      int target,
      boolean trophy,
      byte region,
      byte organ,
      float distance,
      int flightTicks,
      Vec3 origin,
      float[] path,
      Vec3 impact,
      Vec3 dir,
      Vec3 exit,
      Vec3 pose,
      Vec3 poseVel,
      float bodyYaw,
      float headYaw,
      float pitch
   ) implements CustomPacketPayload {
      public static final Type<Shot> TYPE = new Type<>(FrontierHunts.id("killcam_shot"));
      public static final StreamCodec<FriendlyByteBuf, Shot> CODEC = StreamCodec.of(Shot::write, Shot::read);

      private static void write(FriendlyByteBuf b, Shot s) {
         b.writeVarInt(s.shotId);
         b.writeBoolean(s.confirmed);
         b.writeByte(s.weapon);
         b.writeByte(s.tip);
         b.writeBoolean(s.primitive);
         b.writeVarInt(s.target);
         b.writeBoolean(s.trophy);
         b.writeByte(s.region);
         b.writeByte(s.organ);
         b.writeFloat(s.distance);
         b.writeVarInt(s.flightTicks);
         vec(b, s.origin);
         int n = Math.min(MAX_PATH, s.path.length / 3);
         b.writeVarInt(n);
         for (int i = 0; i < n * 3; i++) {
            b.writeFloat(s.path[i]);
         }
         vec(b, s.impact);
         b.writeFloat((float)s.dir.x);
         b.writeFloat((float)s.dir.y);
         b.writeFloat((float)s.dir.z);
         b.writeBoolean(s.exit != null);
         if (s.exit != null) {
            vec(b, s.exit);
         }
         vec(b, s.pose);
         b.writeFloat((float)s.poseVel.x);
         b.writeFloat((float)s.poseVel.y);
         b.writeFloat((float)s.poseVel.z);
         b.writeFloat(s.bodyYaw);
         b.writeFloat(s.headYaw);
         b.writeFloat(s.pitch);
      }

      private static Shot read(FriendlyByteBuf b) {
         int id = b.readVarInt();
         boolean confirmed = b.readBoolean();
         byte weapon = b.readByte();
         byte tip = b.readByte();
         boolean primitive = b.readBoolean();
         int target = b.readVarInt();
         boolean trophy = b.readBoolean();
         byte region = b.readByte();
         byte organ = b.readByte();
         float distance = b.readFloat();
         int ticks = b.readVarInt();
         Vec3 origin = vec(b);
         int n = b.readVarInt();
         if (n < 0 || n > MAX_PATH) {
            throw new IllegalArgumentException("kill cam path too long: " + n);
         }
         float[] path = new float[n * 3];
         for (int i = 0; i < path.length; i++) {
            float f = b.readFloat();
            path[i] = Float.isFinite(f) ? Math.clamp(f, -2048.0F, 2048.0F) : 0.0F;
         }
         Vec3 impact = vec(b);
         Vec3 dir = unit(b);
         Vec3 exit = b.readBoolean() ? vec(b) : null;
         Vec3 pose = vec(b);
         Vec3 vel = new Vec3(b.readFloat(), b.readFloat(), b.readFloat());
         if (!Double.isFinite(vel.lengthSqr()) || vel.lengthSqr() > 16.0) {
            vel = Vec3.ZERO;
         }
         float by = b.readFloat();
         float hy = b.readFloat();
         float pt = b.readFloat();
         return new Shot(
            id, confirmed, weapon, tip, primitive, target, trophy, region, organ,
            Float.isFinite(distance) ? Math.clamp(distance, 0.0F, 4096.0F) : 0.0F,
            Math.clamp(ticks, 1, 400), origin, path, impact, dir, exit, pose, vel,
            Float.isFinite(by) ? by : 0.0F, Float.isFinite(hy) ? hy : 0.0F, Float.isFinite(pt) ? pt : 0.0F
         );
      }

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** The predicted shot did not turn out lethal (miss, graze, animal moved): the client backs out gracefully. */
   public record Cancel(int shotId) implements CustomPacketPayload {
      public static final Type<Cancel> TYPE = new Type<>(FrontierHunts.id("killcam_cancel"));
      public static final StreamCodec<FriendlyByteBuf, Cancel> CODEC = StreamCodec.of((b, c) -> b.writeVarInt(c.shotId), b -> new Cancel(b.readVarInt()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /**
    * [killcam2] What a far target looks like. A shot beyond the client's entity tracking range hits an animal the
    * client never received, so the server sends its type and saved data (sent before the Shot) and the client builds a
    * stand-in to film.
    */
   public record Appearance(int target, ResourceLocation entityType, CompoundTag data) implements CustomPacketPayload {
      public static final Type<Appearance> TYPE = new Type<>(FrontierHunts.id("killcam_appearance"));
      public static final StreamCodec<FriendlyByteBuf, Appearance> CODEC = StreamCodec.of((b, a) -> {
         b.writeVarInt(a.target);
         b.writeResourceLocation(a.entityType);
         b.writeNbt(a.data);
      }, b -> {
         int target = b.readVarInt();
         ResourceLocation type = b.readResourceLocation();
         CompoundTag data = b.readNbt();
         return new Appearance(target, type, data == null ? new CompoundTag() : data);
      });

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }

   /** Client kill cam setting, so the server skips prediction work for players who turned it off. */
   public record Prefs(int mode) implements CustomPacketPayload {
      public static final Type<Prefs> TYPE = new Type<>(FrontierHunts.id("killcam_prefs"));
      public static final StreamCodec<FriendlyByteBuf, Prefs> CODEC = StreamCodec.of((b, p) -> b.writeByte(p.mode), b -> new Prefs(b.readByte()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
