package com.formaworks.frontierhunts.landscape.ride.boat.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.boat.BoatContent;
import com.formaworks.frontierhunts.landscape.ride.boat.BoatSim;
import com.formaworks.frontierhunts.landscape.ride.boat.JonBoat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** [1.1.0] Client side of the boats: renderers, the outboard's engine note, bow spray, wake and prop wash. */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BoatClient {
   private BoatClient() {
   }

   @SubscribeEvent
   public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(BoatContent.ROWBOAT.get(), BoatRenderer::new);
      e.registerEntityRenderer(BoatContent.JON_BOAT.get(), BoatRenderer::new);
      BoatSim.CLIENT_TICK = BoatClient::tick;
   }

   static void tick(Boat b) {
      BoatSim.State s = ((com.formaworks.frontierhunts.landscape.ride.boat.FrontierBoat)b).sim();
      Minecraft mc = Minecraft.getInstance();
      if (b instanceof JonBoat jb && s.running && !(s.sound instanceof Motor m && !m.isStopped())) {
         Motor m = new Motor(jb);
         s.sound = m;
         mc.getSoundManager().play(m);
      }
      if (s.afloat < 0.6F || mc.player == null || b.distanceToSqr(mc.player) > 64.0 * 64.0) {
         return;
      }
      Level level = b.level();
      RandomSource r = b.getRandom();
      float yaw = b.getYRot() * (float)(Math.PI / 180.0);
      double sp = Math.abs(s.speed);
      double half = ((com.formaworks.frontierhunts.landscape.ride.boat.FrontierBoat)b).spec().halfLength();
      double wy = b.getY() + BoatSim.WATERLINE + 0.02;
      if (sp > 0.06) {
         // bow spray off both sides, wake rolling off the quarters
         int n = sp > 0.25 ? 3 : 1;
         for (int i = 0; i < n; i++) {
            for (int side = -1; side <= 1; side += 2) {
               Vec3 p = b.position().add(new Vec3(side * 0.45, 0.0, half - 0.5).yRot(-yaw));
               Vec3 out = new Vec3(side * 0.12, 0.0, 0.0).yRot(-yaw);
               level.addParticle(ParticleTypes.SPLASH, p.x, wy, p.z, out.x + (r.nextDouble() - 0.5) * 0.05, 0.08 + sp * 0.4, out.z);
            }
         }
         if (b.tickCount % 2 == 0) {
            for (int side = -1; side <= 1; side += 2) {
               Vec3 p = b.position().add(new Vec3(side * 0.62, 0.0, -half + 0.3).yRot(-yaw));
               level.addParticle(ParticleTypes.FISHING, p.x, wy, p.z, 0.0, 0.0, 0.0);
            }
         }
      }
      if (b instanceof JonBoat && s.running && s.tilt < 0.3F) {
         Vec3 p = b.position().add(new Vec3(0.0, 0.0, -half - 0.45).yRot(-yaw));
         float thr = Math.abs(s.throttle);
         if (r.nextFloat() < 0.3F + thr) {
            level.addParticle(ParticleTypes.BUBBLE, p.x + (r.nextDouble() - 0.5) * 0.2, wy - 0.35, p.z + (r.nextDouble() - 0.5) * 0.2, 0.0, 0.05, 0.0);
         }
         if (thr > 0.2F) {
            Vec3 back = new Vec3(0.0, 0.0, -0.2 - thr * 0.3).yRot(-yaw);
            level.addParticle(ParticleTypes.SPLASH, p.x, wy, p.z, back.x, 0.1 + thr * 0.15, back.z);
            if (b.tickCount % 2 == 0) {
               level.addParticle(ParticleTypes.FISHING, p.x, wy, p.z, 0.0, 0.0, 0.0);
            }
         }
      }
   }

   /** the outboard's note: the engine sample pitched up into a two-stroke buzz, rising with the throttle */
   static final class Motor extends AbstractTickableSoundInstance {
      private final JonBoat boat;
      private float gain;

      Motor(JonBoat boat) {
         super((SoundEvent)RideContent.SND_ATV_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
         this.boat = boat;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = boat.getX();
         this.y = boat.getY();
         this.z = boat.getZ();
      }

      @Override
      public void tick() {
         if (this.boat.isRemoved()) {
            this.stop();
            return;
         }
         BoatSim.State s = this.boat.sim();
         float thr = Math.abs(s.throttle);
         float target = s.running ? 0.32F + 0.5F * thr : 0.0F;
         this.gain += (target - this.gain) * (target > this.gain ? 0.25F : 0.12F);
         if (!s.running && this.gain < 0.01F) {
            this.stop();
            return;
         }
         this.volume = Math.max(0.001F, this.gain);
         this.pitch = Mth.clamp(1.15F + 0.75F * thr + (float)Math.abs(s.speed) * 0.4F, 0.5F, 2.0F);
         this.x = this.boat.getX();
         this.y = this.boat.getY() + 0.6;
         this.z = this.boat.getZ();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }
   }
}
