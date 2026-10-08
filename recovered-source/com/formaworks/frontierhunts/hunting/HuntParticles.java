package com.formaworks.frontierhunts.hunting;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class HuntParticles {
   public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, "frontierhunts");
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD = TYPES.register("blood_drop", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WATER_BLOOD = TYPES.register("water_blood", () -> new SimpleParticleType(false));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLARE_EMBER = TYPES.register("flare_ember", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLARE_SMOKE = TYPES.register("flare_smoke", () -> new SimpleParticleType(true));
   public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_PUFF = TYPES.register("wind_puff", () -> new SimpleParticleType(false));

   private HuntParticles() {
   }
}
