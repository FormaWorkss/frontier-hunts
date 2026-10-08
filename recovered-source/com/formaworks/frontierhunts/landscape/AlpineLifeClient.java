package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineLifeClient {
   private static final RandomSource RANDOM = RandomSource.create();
   private static long nextHawk;
   private static long nextRaven;
   private static long nextFish;
   private static long nextFlock;
   private static long nextSong;
   private static ClientLevel lastLevel;
   private static int ticks;

   private static boolean option(Supplier<Boolean> var0) {
      try {
         return (Boolean)var0.get();
      } catch (IllegalStateException var2) {
         return true;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      ClientLevel var2 = var1.level;
      LocalPlayer var3 = var1.player;
      if (var2 != null && var3 != null && !var1.isPaused() && var2.dimensionType().hasSkyLight()) {
         if (var2 != lastLevel) {
            lastLevel = var2;
            nextSong = 0L;
            nextFlock = 0L;
            nextFish = 0L;
            nextRaven = 0L;
            nextHawk = 0L;
         }

         ticks++;
         if (option(HuntConfig.MOUNTAIN_SPINDRIFT::get) && ticks % 4 == 0) {
            spindrift(var2, var3);
         }

         if (ticks % 10 == 0) {
            if (option(HuntConfig.BREATH_VAPOR::get)) {
               breath(var2, var3);
            }

            if (ticks % 20 == 0 && option(HuntConfig.WILDLIFE_LIFE::get)) {
               life(var1, var2, var3.position());
            }
         }
      }
   }

   static float temperature(ClientLevel var0, BlockPos var1) {
      float var2 = ((Biome)var0.getBiome(var1).value()).getBaseTemperature();
      return var2 - (float)Math.max(0, var1.getY() - 80) * 0.0016F;
   }

   private static boolean cold(ClientLevel var0, BlockPos var1) {
      float var2 = temperature(var0, var1);
      long var3 = var0.getDayTime() % 24000L;
      boolean var5 = var3 > 12500L || var3 < 2500L;
      return (double)var2 < 0.3 || var5 && (double)var2 < 0.62 || var0.isRaining() && (double)var2 < 0.55;
   }

   private static void breath(ClientLevel var0, Player var1) {
      BlockPos var2 = var1.blockPosition();
      if (cold(var0, var2)) {
         int var3 = var1.isSprinting() ? 26 : 62;
         if (var1.tickCount % var3 < 10 && !var1.isUnderWater()) {
            puff(var0, var1, 0.42);
         }

         for (Whitetail var5 : var0.getEntitiesOfClass(Whitetail.class, var1.getBoundingBox().inflate(28.0), var0x -> var0x.isAlive())) {
            int var6 = var5.getDeltaMovement().horizontalDistanceSqr() > 0.02 ? 30 : 70;
            if ((var5.tickCount + var5.getId() * 13) % var6 < 10) {
               puff(var0, var5, 0.55);
            }
         }
      }
   }

   private static void puff(ClientLevel var0, LivingEntity var1, double var2) {
      Vec3 var4 = var1.getEyePosition();
      Vec3 var5 = var1.getViewVector(1.0F);
      Vec3 var6 = var4.add(var5.scale(var2)).add(0.0, -0.12, 0.0);

      for (int var7 = 0; var7 < 2; var7++) {
         var0.addParticle(
            (ParticleOptions)AlpineRegistration.BREATH.get(),
            var6.x,
            var6.y,
            var6.z,
            var5.x * 0.035 + (RANDOM.nextDouble() - 0.5) * 0.01,
            0.006 + RANDOM.nextDouble() * 0.006,
            var5.z * 0.035 + (RANDOM.nextDouble() - 0.5) * 0.01
         );
      }
   }

   private static void spindrift(ClientLevel var0, Player var1) {
      BlockPos var2 = var1.blockPosition();
      if (var2.getY() >= 360 && var0.getBrightness(LightLayer.SKY, var2.above()) >= 12 && !((double)temperature(var0, var2) > 0.2)) {
         double var3 = (double)var0.getGameTime() / 20.0;
         double var5 = Math.max(0.0, Math.sin(var3 * 0.37) + 0.6 * Math.sin(var3 * 0.91 + 1.3) - 0.2) * AlpineLayout.smooth(360.0, 620.0, (double)var2.getY());
         int var7 = (int)(var5 * 9.0);

         for (int var8 = 0; var8 < var7; var8++) {
            double var9 = var1.getX() + (RANDOM.nextDouble() - 0.5) * 24.0;
            double var11 = var1.getZ() + (RANDOM.nextDouble() - 0.5) * 24.0;
            int var13 = var0.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(var9), (int)Math.floor(var11));
            if (var0.getBlockState(new BlockPos((int)Math.floor(var9), var13 - 1, (int)Math.floor(var11))).is(Blocks.SNOW_BLOCK)
               || !(RANDOM.nextFloat() < 0.7F)) {
               var0.addParticle(
                  ParticleTypes.SNOWFLAKE,
                  var9,
                  (double)var13 + 0.2 + RANDOM.nextDouble() * 1.5,
                  var11,
                  0.18 + var5 * 0.25,
                  0.02 + RANDOM.nextDouble() * 0.04,
                  0.06
               );
            }
         }
      }
   }

   private static void life(Minecraft var0, ClientLevel var1, Vec3 var2) {
      long var3 = var1.getGameTime();
      long var5 = var1.getDayTime() % 24000L;
      boolean var7 = var5 > 500L && var5 < 11500L;
      boolean var8 = var5 > 11000L && var5 < 13500L || var5 > 22500L || var5 < 1500L;
      BlockPos var9 = BlockPos.containing(var2);
      boolean var10 = var1.getBrightness(LightLayer.SKY, var9.above()) >= 14;
      if (var1.isRaining()) {
         nextHawk = Math.max(nextHawk, var3 + 600L);
      } else {
         if (nextHawk == 0L) {
            nextHawk = var3 + 600L + (long)RANDOM.nextInt(1800);
         }

         if (nextRaven == 0L) {
            nextRaven = var3 + 400L + (long)RANDOM.nextInt(1600);
         }

         if (nextFish == 0L) {
            nextFish = var3 + 200L + (long)RANDOM.nextInt(400);
         }

         if (var7 && var10 && var3 >= nextHawk) {
            nextHawk = var3 + 1200L + (long)RANDOM.nextInt(1800);
            double var11 = RANDOM.nextDouble() * Math.PI * 2.0;
            double var13 = 22.0 + RANDOM.nextDouble() * 30.0;
            double var15 = var2.x + Math.cos(var11) * var13;
            double var17 = var2.z + Math.sin(var11) * var13;
            int var19 = var1.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(var15), (int)Math.floor(var17));
            double var20 = Math.max(var2.y, (double)var19) + 22.0 + RANDOM.nextDouble() * 22.0;
            var1.addParticle(
               (ParticleOptions)AlpineRegistration.SOARING_HAWK.get(),
               true,
               var15,
               var20,
               var17,
               12.0 + RANDOM.nextDouble() * 12.0,
               (double)(RANDOM.nextBoolean() ? 1 : -1) * (0.01 + RANDOM.nextDouble() * 0.006),
               0.0
            );
         }

         if ((var7 || var8) && var10 && var3 >= nextRaven) {
            nextRaven = var3 + 900L + (long)RANDOM.nextInt(1500);
            double var34 = RANDOM.nextDouble() * Math.PI * 2.0;
            double var37 = 48.0;
            double var39 = var2.x + Math.cos(var34) * var37;
            double var41 = var2.z + Math.sin(var34) * var37;
            double var44 = var34 + Math.PI + (RANDOM.nextDouble() - 0.5) * 0.8;
            double var21 = 0.22 + RANDOM.nextDouble() * 0.08;
            double var23 = var2.y + 16.0 + RANDOM.nextDouble() * 18.0;
            int var25 = RANDOM.nextFloat() < 0.45F ? 2 + RANDOM.nextInt(2) : 1;

            for (int var26 = 0; var26 < var25; var26++) {
               var1.addParticle(
                  (ParticleOptions)AlpineRegistration.RAVEN.get(),
                  true,
                  var39 + (double)var26 * 2.5,
                  var23 + (double)var26 * 1.2,
                  var41 - (double)var26 * 1.8,
                  Math.cos(var44) * var21,
                  0.0,
                  Math.sin(var44) * var21
               );
            }
         }

         if ((var7 || var8) && var3 >= nextFlock) {
            nextFlock = var3 + 500L + (long)RANDOM.nextInt(900);
            double var35 = RANDOM.nextDouble() * Math.PI * 2.0;
            double var38 = 26.0 + RANDOM.nextDouble() * 16.0;
            double var40 = var2.x + Math.cos(var35) * var38;
            double var42 = var2.z + Math.sin(var35) * var38;
            int var45 = var1.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(var40), (int)Math.floor(var42));
            double var47 = var35 + Math.PI + (RANDOM.nextDouble() - 0.5) * 1.1;
            double var22 = 0.38 + RANDOM.nextDouble() * 0.12;
            double var24 = Math.max((double)var45, var2.y) + 7.0 + RANDOM.nextDouble() * 14.0;
            int var52 = 5 + RANDOM.nextInt(9);

            for (int var27 = 0; var27 < var52; var27++) {
               double var28 = RANDOM.nextGaussian() * 2.2;
               double var30 = RANDOM.nextGaussian() * 0.8;
               double var32 = RANDOM.nextGaussian() * 2.2;
               var1.addParticle(
                  (ParticleOptions)AlpineRegistration.FLOCK_BIRD.get(),
                  true,
                  var40 + var28,
                  var24 + var30,
                  var42 + var32,
                  Math.cos(var47) * var22,
                  RANDOM.nextDouble() * 0.6,
                  Math.sin(var47) * var22
               );
            }

            if (AlpineAmbience.forest() > 0.15) {
               AlpineAmbience.play(var0, "amb_bird_chirp", new Vec3(var40, var24, var42), var2, 32.0, 1.15F + RANDOM.nextFloat() * 0.1F);
            }
         }

         if ((var7 || var8) && var3 >= nextSong) {
            nextSong = var3 + 40L + (long)RANDOM.nextInt(60);
            songbirds(var1, var2, var8 ? 0.6 : 1.0);
         }

         if (var3 >= nextFish) {
            nextFish = var3 + (long)(var8 ? 160 : 500) + (long)RANDOM.nextInt(var8 ? 340 : 900);

            for (int var36 = 0; var36 < 8; var36++) {
               double var12 = RANDOM.nextDouble() * Math.PI * 2.0;
               double var14 = 6.0 + RANDOM.nextDouble() * 22.0;
               int var16 = (int)Math.floor(var2.x + Math.cos(var12) * var14);
               int var43 = (int)Math.floor(var2.z + Math.sin(var12) * var14);
               if (var1.hasChunk(var16 >> 4, var43 >> 4)) {
                  int var18 = var1.getHeight(Types.MOTION_BLOCKING, var16, var43);
                  BlockPos var46 = new BlockPos(var16, var18 - 1, var43);
                  FluidState var48 = var1.getFluidState(var46);
                  if (var48.isSource() && var1.getFluidState(var46.below()).isSource() && var1.getFluidState(var46.below(2)).isSource()) {
                     double var49 = (double)var16 + 0.5;
                     double var50 = (double)var18 - 0.1;
                     double var51 = (double)var43 + 0.5;
                     double var53 = RANDOM.nextDouble() * Math.PI * 2.0;
                     var1.addParticle(
                        (ParticleOptions)AlpineRegistration.RISING_FISH.get(), true, var49, var50, var51, Math.cos(var53) * 0.04, 0.0, Math.sin(var53) * 0.04
                     );
                     var0.getSoundManager()
                        .play(
                           new SimpleSoundInstance(
                              SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.AMBIENT, 1.1F, 1.2F + RANDOM.nextFloat() * 0.3F, RANDOM, var49, var50, var51
                           )
                        );
                     break;
                  }
               }
            }
         }
      }
   }

   private static void songbirds(ClientLevel var0, Vec3 var1, double var2) {
      int var4 = (int)Math.round(9.0 * var2);
      if (AlpineLifeParticles.Songbird.alive() < var4) {
         Block var5 = (Block)BuiltInRegistries.BLOCK.get(FrontierHunts.id("sapling_pole"));

         for (int var6 = 0; var6 < 10; var6++) {
            double var7 = RANDOM.nextDouble() * Math.PI * 2.0;
            double var9 = 12.0 + RANDOM.nextDouble() * 20.0;
            int var11 = (int)Math.floor(var1.x + Math.cos(var7) * var9);
            int var12 = (int)Math.floor(var1.z + Math.sin(var7) * var9);
            if (var0.hasChunk(var11 >> 4, var12 >> 4)) {
               int var13 = var0.getHeight(Types.MOTION_BLOCKING, var11, var12);
               int var14 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var11, var12);
               if (!(Math.abs((double)var14 - var1.y) > 14.0)) {
                  BlockPos var15 = new BlockPos(var11, var13 - 1, var12);
                  BlockState var16 = var0.getBlockState(var15);
                  int var17 = RANDOM.nextInt(3);
                  if (!var16.is(var5) && (!var16.is(BlockTags.LOGS) || var13 != var14)) {
                     if (var13 <= var14 + 1) {
                        BlockState var31 = var0.getBlockState(new BlockPos(var11, var14 - 1, var12));
                        if (var31.is(BlockTags.DIRT) || var31.is(Blocks.MOSS_BLOCK)) {
                           BlockState var19 = var0.getBlockState(new BlockPos(var11, var14, var12));
                           if ((
                                 var19.isAir()
                                    || var19.getBlock() instanceof AlpineSticks
                                    || var19.is((Block)AlpineRegistration.PASTURE.get()) && (Integer)var19.getValue(AlpinePasture.HEIGHT) == 0
                                    || var19.is(Blocks.SHORT_GRASS)
                              )
                              && var0.getBrightness(LightLayer.SKY, new BlockPos(var11, var14, var12)) >= 10) {
                              int var20 = 1 + RANDOM.nextInt(RANDOM.nextFloat() < 0.4F ? 4 : 2);
                              double var21 = RANDOM.nextDouble() * Math.PI * 2.0;

                              for (int var23 = 0; var23 < var20; var23++) {
                                 double var24 = var23 == 0 ? 0.0 : RANDOM.nextGaussian() * 1.3;
                                 double var26 = var23 == 0 ? 0.0 : RANDOM.nextGaussian() * 1.3;
                                 int var28 = (int)Math.floor((double)var11 + 0.5 + var24);
                                 int var29 = (int)Math.floor((double)var12 + 0.5 + var26);
                                 int var30 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var28, var29);
                                 if (Math.abs(var30 - var14) <= 1) {
                                    var0.addParticle(
                                       (ParticleOptions)AlpineRegistration.SONGBIRD.get(),
                                       true,
                                       (double)var11 + 0.5 + var24,
                                       (double)var30 + 0.02,
                                       (double)var12 + 0.5 + var26,
                                       (double)var17,
                                       var21 + (RANDOM.nextDouble() - 0.5) * 0.6,
                                       (double)(500 + RANDOM.nextInt(900))
                                    );
                                 }
                              }

                              return;
                           }
                        }
                     }
                  } else {
                     double var18 = (double)(var13 - 1) + (var16.is(var5) ? (var16.getValue(AlpinePole.KIND) == 2 ? 0.85 : 1.0) : 1.0);
                     if (var0.getBlockState(var15.above()).isAir()) {
                        var0.addParticle(
                           (ParticleOptions)AlpineRegistration.SONGBIRD.get(),
                           true,
                           (double)var11 + 0.5,
                           var18,
                           (double)var12 + 0.5,
                           (double)(var17 + 10),
                           RANDOM.nextDouble() * Math.PI * 2.0,
                           (double)(600 + RANDOM.nextInt(900))
                        );
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   private AlpineLifeClient() {
   }
}
