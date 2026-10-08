package com.formaworks.frontierhunts.guns;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [guns2] Bullets and shot hitting water. The server finds where a projectile's flight segment enters water from the
 * air, tells nearby clients to draw a splash scaled by the round's energy (pistol: a sharp spout; .30 rifle: a crown
 * and a tall plume; magnum / heavy rifle: a big column with spray and mist) and plays a layered splash sound. Under
 * water a bullet only goes on for a metre or three ({@link #penetration}); shot pellets each throw a spout and the
 * whole charge reads as one blast (the biggest splash of all, see {@link #splash}). [guns3] Own sounds and art.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class WaterImpact {
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("water_spray"));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MIST = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("water_mist"));
   /** [guns3] white-water clumps (crown, plume, surface foam) and the foam ring laid flat on the water */
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FOAM = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("water_foam"));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RING = DeferredHolder.create(Registries.PARTICLE_TYPE, FrontierHunts.id("water_ring"));
   /** [guns3] our own impact sounds (tools/gen_water_impact_audio.py): pistol/5.56, .30 rifles, a shotgun charge */
   public static final SoundEvent HIT_SMALL = SoundEvent.createVariableRangeEvent(FrontierHunts.id("guns.water_hit_small"));
   public static final SoundEvent HIT_LARGE = SoundEvent.createVariableRangeEvent(FrontierHunts.id("guns.water_hit_large"));
   public static final SoundEvent HIT_SHOT = SoundEvent.createVariableRangeEvent(FrontierHunts.id("guns.water_hit_shot"));
   /** the shotgun charge being drawn this tick: pellets landing near it are one blast (one sound, one big splash) */
   private static long blastTick = Long.MIN_VALUE;
   private static Vec3 blastAt = Vec3.ZERO;
   private static Object blastLevel;

   /** Set by the client wiring; a no-op on a dedicated server. */
   public static Consumer<Splash> receiver = s -> {
   };

   private WaterImpact() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.PARTICLE_TYPE, FrontierHunts.id("water_spray"), () -> new SimpleParticleType(true));
      e.register(Registries.PARTICLE_TYPE, FrontierHunts.id("water_mist"), () -> new SimpleParticleType(true));
      e.register(Registries.PARTICLE_TYPE, FrontierHunts.id("water_foam"), () -> new SimpleParticleType(true));
      e.register(Registries.PARTICLE_TYPE, FrontierHunts.id("water_ring"), () -> new SimpleParticleType(true));
      e.register(Registries.SOUND_EVENT, HIT_SMALL.getLocation(), () -> HIT_SMALL);
      e.register(Registries.SOUND_EVENT, HIT_LARGE.getLocation(), () -> HIT_LARGE);
      e.register(Registries.SOUND_EVENT, HIT_SHOT.getLocation(), () -> HIT_SHOT);
   }

   @SubscribeEvent
   public static void payloads(RegisterPayloadHandlersEvent event) {
      event.registrar("1").optional().playToClient(Splash.TYPE, Splash.CODEC, (p, ctx) -> ctx.enqueueWork(() -> receiver.accept(p)));
   }

   /**
    * Where the segment from {@code from} to {@code to} first enters water from the air, or null. Starting in water, or
    * never touching water, is not an entry.
    */
   public static Vec3 entry(ServerLevel level, Vec3 from, Vec3 to, Entity projectile) {
      if (level.getFluidState(BlockPos.containing(from)).is(FluidTags.WATER)) {
         return null;
      }
      BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, projectile));
      if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
         return null;
      }
      return hit.getLocation();
   }

   /** Distance a round keeps going under water before it has shed its energy (blocks). */
   public static double penetration(float energy, boolean pellet) {
      return pellet ? 0.35 + 0.4 * energy : 0.6 + 2.2 * Math.min(1.2F, energy);
   }

   /**
    * Splash at {@code at}. {@code energy}: about 0.25 for a pistol, 1 for a .30 rifle, up to 2 for a magnum;
    * {@code pellet}: one pellet of a shot charge (small spout, quieter).
    */
   public static void splash(ServerLevel level, Vec3 at, Vec3 dir, float energy, boolean pellet) {
      float e = Math.max(0.05F, Math.min(2.0F, energy));
      Vec3 d = dir.lengthSqr() > 1.0E-8 ? dir.normalize() : new Vec3(0.0, -1.0, 0.0);
      boolean blastStart = false;
      if (pellet) {
         // the first pellet of a charge to reach the water plays the blast; the rest only add their spouts
         long now = level.getGameTime();
         synchronized (WaterImpact.class) {
            if (blastLevel != level || blastTick != now || blastAt.distanceToSqr(at) > 10.0 * 10.0) {
               blastLevel = level;
               blastTick = now;
               blastAt = at;
               blastStart = true;
            }
         }
      }
      Splash s = new Splash(at.x, at.y, at.z, (float)d.x, (float)d.y, (float)d.z, e, pellet);
      for (ServerPlayer p : level.players()) {
         if (p.distanceToSqr(at) < 160.0 * 160.0 && p.connection != null && p.connection.hasChannel(Splash.TYPE)) {
            PacketDistributor.sendToPlayer(p, s);
         }
      }
      float pitch = 0.92F + level.random.nextFloat() * 0.16F;
      if (pellet) {
         if (blastStart) {
            level.playSound(null, at.x, at.y, at.z, HIT_SHOT, SoundSource.PLAYERS, 1.6F, pitch);
         }
      } else if (e >= 0.9F) {
         // .30-30 and up: heavier the harder it hits
         level.playSound(null, at.x, at.y, at.z, HIT_LARGE, SoundSource.PLAYERS, 1.1F + 0.5F * Math.min(1.4F, e), pitch * (1.08F - 0.1F * Math.min(1.4F, e)));
      } else {
         level.playSound(null, at.x, at.y, at.z, HIT_SMALL, SoundSource.PLAYERS, 0.8F + 0.6F * e, pitch * (1.1F - 0.2F * e));
      }
   }

   public record Splash(double x, double y, double z, float dx, float dy, float dz, float energy, boolean pellet) implements CustomPacketPayload {
      public static final Type<Splash> TYPE = new Type<>(FrontierHunts.id("water_splash"));
      public static final StreamCodec<FriendlyByteBuf, Splash> CODEC = StreamCodec.of((b, s) -> {
         b.writeDouble(s.x);
         b.writeDouble(s.y);
         b.writeDouble(s.z);
         b.writeFloat(s.dx);
         b.writeFloat(s.dy);
         b.writeFloat(s.dz);
         b.writeFloat(s.energy);
         b.writeBoolean(s.pellet);
      }, b -> new Splash(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(),
         Math.max(0.0F, Math.min(2.0F, b.readFloat())), b.readBoolean()));

      @Override
      public Type<? extends CustomPacketPayload> type() {
         return TYPE;
      }
   }
}
