package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * [1.1.5] Comfort: the Blood effects setting. The blood spray and mist particles of a hit go through this filter -
 * FULL draws them all, REDUCED one in three, OFF none. Blood trail sign on the ground is drawn elsewhere and always
 * stays, because it is how a wounded animal is tracked and recovered.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BloodComfort {
   private BloodComfort() {
   }

   private static int count;

   /** registered after the mod's own providers, so these wrappers replace them */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void particles(RegisterParticleProvidersEvent e) {
      e.registerSpriteSet(HuntParticles.BLOOD.get(), sprites -> filtered(new BloodParticle.Provider(sprites)));
      e.registerSpriteSet(HuntParticles.WATER_BLOOD.get(), sprites -> filtered(new WaterBloodParticle.Provider(sprites)));
   }

   static ParticleProvider<SimpleParticleType> filtered(ParticleProvider<SimpleParticleType> inner) {
      return new ParticleProvider<>() {
         @Override
         public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return keep() ? inner.createParticle(type, level, x, y, z, dx, dy, dz) : null;
         }
      };
   }

   static boolean keep() {
      HuntConfig.BloodEffects mode;
      try {
         mode = HuntConfig.BLOOD_EFFECTS.get();
      } catch (RuntimeException e) {
         return true;
      }
      return switch (mode) {
         case FULL -> true;
         case REDUCED -> ++count % 3 == 0;
         case OFF -> false;
      };
   }
}
