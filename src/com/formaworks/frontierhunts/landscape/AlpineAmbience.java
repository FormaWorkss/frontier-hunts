package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
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
public final class AlpineAmbience {
   private static AlpineAmbience.Bed windHigh;
   private static AlpineAmbience.Bed windTrees;
   private static AlpineAmbience.Bed creek;
   private static AlpineAmbience.Bed meadow;
   private static AlpineAmbience.Bed night;
   private static double forest;
   private static double water;
   private static double flowing;
   private static double open;
   private static double rock;
   private static double stillWater;
   private static double nearWater = 99.0;
   private static final double[] next = new double[12];
   private static final RandomSource RANDOM = RandomSource.create();
   private static ClientLevel lastLevel;
   private static int ticks;
   private static final int SONG = 0;
   private static final int CHIRP = 1;
   private static final int WOODPECKER = 2;
   private static final int RAVEN = 3;
   private static final int HAWK = 4;
   private static final int PIKA = 5;
   private static final int OWL = 6;
   private static final int COYOTE = 7;
   private static final int LOON = 8;
   private static final int FROG = 9;
   private static final AlpineAmbience.Singer[] SINGERS = new AlpineAmbience.Singer[]{
      new AlpineAmbience.Singer(), new AlpineAmbience.Singer(), new AlpineAmbience.Singer()
   };
   private static final String[] SONGS = new String[]{"amb_song_thrush", "amb_song_warbler", "amb_song_chickadee"};
   private static double chipsNext;
   private static double chipsLeft;
   private static Vec3 chipsAt;
   static final double SONG_RANGE = 56.0;
   static final double CHIP_RANGE = 32.0;

   private static SoundEvent sound(String var0) {
      return (SoundEvent)BuiltInRegistries.SOUND_EVENT.get(FrontierHunts.id(var0));
   }

   private static boolean enabled() {
      try {
         return (Boolean)HuntConfig.AMBIENT_SOUNDS.get();
      } catch (IllegalStateException var1) {
         return true;
      }
   }

   private static float master() {
      try {
         return (float)((Integer)HuntConfig.AMBIENT_VOLUME.get()).intValue() / 100.0F;
      } catch (IllegalStateException var1) {
         return 1.0F;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      ClientLevel var2 = var1.level;
      LocalPlayer var3 = var1.player;
      if (var2 != null && var3 != null && !var1.isPaused()) {
         if (var2 != lastLevel) {
            lastLevel = var2;
            stopAll();
            Arrays.fill(next, 0.0);

            for (AlpineAmbience.Singer var7 : SINGERS) {
               var7.until = 0.0;
               var7.rest = 0.0;
            }

            chipsNext = 0.0;
            chipsLeft = 0.0;
         }

         if (enabled() && var2.dimensionType().hasSkyLight()) {
            if (++ticks % 10 == 0) {
               survey(var2, var3.blockPosition());
               mix(var1, var2, var3.position());
               calls(var1, var2, var3.position());
            }
         } else {
            stopAll();
         }
      }
   }

   private static void stopAll() {
      for (AlpineAmbience.Bed var3 : new AlpineAmbience.Bed[]{windHigh, windTrees, creek, meadow, night}) {
         if (var3 != null) {
            var3.target = 0.0F;
         }
      }
   }

   private static void survey(ClientLevel var0, BlockPos var1) {
      int var2 = 0;
      int var3 = 0;
      int var4 = 0;
      int var5 = 0;
      int var6 = 0;
      int var7 = 0;
      byte var8 = 28;
      double var9 = 99.0;

      for (int var11 = 0; var11 < var8; var11++) {
         double var12 = RANDOM.nextDouble() * Math.PI * 2.0;
         double var14 = 3.0 + RANDOM.nextDouble() * 26.0;
         int var16 = var1.getX() + (int)Math.round(Math.cos(var12) * var14);
         int var17 = var1.getZ() + (int)Math.round(Math.sin(var12) * var14);
         if (var0.hasChunk(var16 >> 4, var17 >> 4)) {
            int var18 = var0.getHeight(Types.MOTION_BLOCKING, var16, var17);
            int var19 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var16, var17);
            if (var18 > var19 + 2) {
               var2++;
            }

            BlockPos var20 = new BlockPos(var16, var19 - 1, var17);
            BlockState var21 = var0.getBlockState(var20);
            FluidState var22 = var21.getFluidState();
            if (!var22.isEmpty()) {
               var3++;
               var9 = Math.min(var9, var14);
               if (var22.isSource() && (!var0.getBlockState(var20.below()).getFluidState().isEmpty() || var0.getFluidState(var20.north()).isSource())) {
                  var7++;
               } else {
                  var4++;
               }
            } else if (var21.is(BlockTags.DIRT) || var21.is(Blocks.MOSS_BLOCK)) {
               var5++;
            } else if (var21.is(BlockTags.BASE_STONE_OVERWORLD) || var21.is(Blocks.GRAVEL) || var21.is(Blocks.COBBLESTONE) || var21.is(Blocks.SNOW_BLOCK)) {
               var6++;
            }
         }
      }

      double var23 = 0.35;
      forest = forest + ((double)var2 / (double)var8 - forest) * var23;
      water = water + ((double)var3 / (double)var8 - water) * var23;
      flowing = flowing + ((double)var4 / (double)var8 - flowing) * var23;
      stillWater = stillWater + ((double)var7 / (double)var8 - stillWater) * var23;
      open = open + ((double)var5 / (double)var8 - open) * var23;
      rock = rock + ((double)var6 / (double)var8 - rock) * var23;
      nearWater = nearWater + (var9 - nearWater) * 0.5;
   }

   private static double smooth(double var0, double var2, double var4) {
      return AlpineLayout.smooth(var0, var2, var4);
   }

   private static double[] hours(ClientLevel var0) {
      double var1 = (double)(var0.getDayTime() % 24000L) / 24000.0;
      double var3 = Math.max(bump(var1, 0.97, 0.07), bump(var1 + 1.0, 0.97, 0.07));
      double var5 = bump(var1, 0.52, 0.05);
      double var7 = smooth(0.03, 0.08, var1) * (1.0 - smooth(0.46, 0.52, var1));
      double var9 = smooth(0.53, 0.58, var1) * (1.0 - smooth(0.93, 0.97, var1));
      return new double[]{var3, var7, var5, var9};
   }

   private static double bump(double var0, double var2, double var4) {
      double var6 = (var0 - var2) / var4;
      return Math.exp(-var6 * var6);
   }

   private static void mix(Minecraft var0, ClientLevel var1, Vec3 var2) {
      double[] var3 = hours(var1);
      double var4 = var3[0];
      double var6 = var3[1];
      double var8 = var3[2];
      double var10 = var3[3];
      BlockPos var12 = BlockPos.containing(var2);
      double var13 = (double)var1.getBrightness(LightLayer.SKY, var12.above()) / 15.0;
      double var15 = smooth(0.35, 0.95, var13);
      double var17 = smooth(260.0, 560.0, var2.y);
      double var19 = (double)var1.getRainLevel(1.0F);
      double var21 = (double)var1.getThunderLevel(1.0F);
      float var23 = ((Biome)var1.getBiome(var12).value()).getBaseTemperature();
      double var24 = smooth(0.15, 0.55, (double)var23) * (1.0 - var17 * 0.85);
      double var26 = var0.player.isUnderWater() ? 0.0 : 1.0;
      float var28 = master() * (float)var26;
      // [shelter] inside a tent, a cabin or a cave the outdoor ambience is heard through the walls: wind beds much
      // quieter, birds/creek/meadow quieter (eased with the shared shelter value, ~1-2 s)
      float sh = com.formaworks.frontierhunts.shelter.client.ShelterClient.enclosure();
      float windIn = 1.0F - 0.8F * sh;
      float lifeIn = 1.0F - 0.55F * sh;
      double var29 = (double)var1.getGameTime() / 20.0;
      double var31 = gust(var29, 0.0);
      double var33 = gust(var29, 17.3);
      windHigh = bed(
         var0,
         windHigh,
         "amb_wind_high",
         var28 * windIn * (float)((0.1 + 0.8 * var17 + 0.25 * var21) * (0.45 + 0.55 * (1.0 - forest)) * var15 * (1.0 + 0.5 * var19) * 0.62 * var31)
      );
      windTrees = bed(
         var0,
         windTrees,
         "amb_wind_trees",
         var28 * windIn * (float)(smooth(0.12, 0.6, forest) * (0.55 + 0.45 * var15) * (1.0 + 0.6 * var19) * (1.0 - 0.4 * var17) * 0.6 * var33)
      );
      double var35 = smooth(22.0, 3.0, nearWater) * (smooth(0.0, 0.1, flowing) * 0.9 + smooth(0.0, 0.2, stillWater) * 0.3);
      creek = bed(var0, creek, "amb_creek", var28 * lifeIn * (float)(var35 * 0.65));
      meadow = bed(
         var0, meadow, "amb_meadow", var28 * lifeIn * (float)((var6 + 0.5 * var8) * var24 * smooth(0.1, 0.5, open + 0.3 * forest) * (1.0 - var19) * var15 * 0.24)
      );
      night = bed(var0, night, "amb_night", var28 * lifeIn * (float)(var10 * var24 * (1.0 - var19) * smooth(0.05, 0.4, open + forest) * (0.6 + 0.4 * var15) * 0.22));
   }

   private static double gust(double var0, double var2) {
      double var4 = 0.5 + 0.5 * Math.sin((Math.PI * 2) * (var0 + var2) / 47.0 + 0.3);
      double var6 = 0.5 + 0.5 * Math.sin((Math.PI * 2) * (var0 + var2) / 17.3 + 1.1);
      double var8 = 0.5 + 0.5 * Math.sin((Math.PI * 2) * (var0 + var2) / 6.1 + 2.3);
      return 0.25 + 0.75 * Math.min(1.0, var4 * (0.45 + 0.55 * var6) * (0.8 + 0.2 * var8) * 1.25);
   }

   private static AlpineAmbience.Bed bed(Minecraft var0, AlpineAmbience.Bed var1, String var2, float var3) {
      var3 = Mth.clamp(var3, 0.0F, 1.0F);
      if (var1 != null && !var1.isStopped() && var0.getSoundManager().isActive(var1)) {
         var1.target = var3;
         return var1;
      } else if ((double)var3 < 0.02) {
         return null;
      } else {
         SoundEvent var4 = sound(var2);
         if (var4 == null) {
            return null;
         } else {
            AlpineAmbience.Bed var5 = new AlpineAmbience.Bed(var4, var3);
            var0.getSoundManager().play(var5);
            return var5;
         }
      }
   }

   private static void calls(Minecraft var0, ClientLevel var1, Vec3 var2) {
      double var3 = (double)var1.getGameTime() / 20.0;
      double[] var5 = hours(var1);
      double var6 = var5[0];
      double var8 = var5[1];
      double var10 = var5[2];
      double var12 = var5[3];
      BlockPos var14 = BlockPos.containing(var2);
      double var15 = (double)var1.getRainLevel(1.0F);
      if (!(var15 > 0.6)) {
         double var17 = smooth(260.0, 560.0, var2.y);
         float var19 = ((Biome)var1.getBiome(var14).value()).getBaseTemperature();
         if ((double)var19 > 0.25 && var17 < 0.6) {
            boolean var27 = true;
         } else {
            boolean var10000 = false;
         }

         double var21 = (var6 * 1.0 + var8 * 0.35 + var10 * 0.25) * (1.0 - var15) * (1.0 - var17 * 0.8);
         if (var21 > 0.05 && forest > 0.3) {
            singers(var0, var1, var2, var3, var21, var6);
         } else {
            for (AlpineAmbience.Singer var26 : SINGERS) {
               var26.until = 0.0;
            }
         }

         if (var21 > 0.05 && forest > 0.22) {
            chips(var0, var1, var2, var3, var21);
         }
      }
   }

   static double forest() {
      return forest;
   }

   private static void singers(Minecraft var0, ClientLevel var1, Vec3 var2, double var3, double var5, double var7) {
      int var9 = var7 > 0.5 ? 2 : 1;

      for (int var10 = 0; var10 < SINGERS.length; var10++) {
         AlpineAmbience.Singer var11 = SINGERS[var10];
         if (var10 >= var9) {
            var11.until = 0.0;
         } else if (var11.until > var3) {
            if (var3 >= var11.next) {
               var11.next = var3 + 7.0 + RANDOM.nextDouble() * 9.0;
               if (var11.at.distanceTo(var2) > 70.0) {
                  var11.until = 0.0;
               } else {
                  play(var0, var11.song, var11.at, var2, 56.0, var11.pitch + (RANDOM.nextFloat() - 0.5F) * 0.04F);
               }
            }
         } else if (var11.rest == 0.0) {
            var11.rest = var3 + 10.0 + RANDOM.nextDouble() * 40.0;
         } else if (!(var3 < var11.rest)) {
            Vec3 var12 = spot(var1, var2, 14.0, 42.0, true);
            if (var12 == null) {
               var11.rest = var3 + 5.0;
            } else {
               var11.at = var12;
               var11.song = SONGS[RANDOM.nextInt(SONGS.length)];
               var11.pitch = 0.9F + RANDOM.nextFloat() * 0.25F;
               var11.next = var3;
               var11.until = var3 + 30.0 + RANDOM.nextDouble() * 60.0;
               var11.rest = var11.until + (60.0 + RANDOM.nextDouble() * 120.0) / Math.max(0.35, var5);
            }
         }
      }
   }

   private static void chips(Minecraft var0, ClientLevel var1, Vec3 var2, double var3, double var5) {
      if (chipsNext == 0.0) {
         chipsNext = var3 + 5.0 + RANDOM.nextDouble() * 25.0;
      } else if (!(var3 < chipsNext)) {
         if (chipsLeft <= 0.0) {
            chipsAt = spot(var1, var2, 8.0, 32.0, true);
            if (chipsAt == null) {
               chipsNext = var3 + 4.0;
               return;
            }

            chipsLeft = (double)(2 + RANDOM.nextInt(2));
         }

         play(var0, "amb_bird_chirp", chipsAt, var2, 32.0, 0.95F + RANDOM.nextFloat() * 0.2F);
         chipsLeft--;
         chipsNext = chipsLeft > 0.0 ? var3 + 0.3 + RANDOM.nextDouble() * 1.1 : var3 + (30.0 + RANDOM.nextDouble() * 60.0) / Math.max(0.35, var5);
      }
   }

   private static Vec3 spot(ClientLevel var0, Vec3 var1, double var2, double var4, boolean var6) {
      for (int var7 = 0; var7 < 6; var7++) {
         double var8 = RANDOM.nextDouble() * Math.PI * 2.0;
         double var10 = var2 + (var4 - var2) * RANDOM.nextDouble();
         int var12 = (int)Math.floor(var1.x + Math.cos(var8) * var10);
         int var13 = (int)Math.floor(var1.z + Math.sin(var8) * var10);
         if (var0.hasChunk(var12 >> 4, var13 >> 4)) {
            int var14 = var0.getHeight(Types.MOTION_BLOCKING, var12, var13);
            int var15 = var0.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var12, var13);
            if (!var6 || var14 > var15 + 2 || var7 >= 4) {
               return new Vec3((double)var12 + 0.5, var6 ? (double)var14 - 1.5 : (double)(var15 + 1), (double)var13 + 0.5);
            }
         }
      }

      return null;
   }

   static void play(Minecraft var0, String var1, Vec3 var2, Vec3 var3, double var4, float var6) {
      SoundEvent var7 = sound(var1);
      if (var7 != null) {
         double var8 = var4 * (double)Math.max(0.2F, master());
         if (!(var2.distanceTo(var3) >= var8)) {
            var0.getSoundManager().play(new SimpleSoundInstance(var7, SoundSource.AMBIENT, (float)(var8 / 16.0), var6, RANDOM, var2.x, var2.y, var2.z));
         }
      }
   }

   private static void due(
      Minecraft var0,
      ClientLevel var1,
      Vec3 var2,
      double var3,
      int var5,
      String var6,
      double var7,
      double var9,
      double var11,
      double var13,
      boolean var15,
      float var16,
      float var17,
      float var18
   ) {
      if (next[var5] == 0.0) {
         next[var5] = var3 + var7 * RANDOM.nextDouble();
      } else if (!(var3 < next[var5])) {
         next[var5] = var3 + var7 + (var9 - var7) * RANDOM.nextDouble();
         SoundEvent var19 = sound(var6);
         if (var19 != null) {
            Vec3 var20 = null;

            for (int var21 = 0; var21 < 6 && var20 == null; var21++) {
               double var22 = RANDOM.nextDouble() * Math.PI * 2.0;
               double var24 = var11 + (var13 - var11) * RANDOM.nextDouble();
               int var26 = (int)Math.floor(var2.x + Math.cos(var22) * var24);
               int var27 = (int)Math.floor(var2.z + Math.sin(var22) * var24);
               if (var1.hasChunk(var26 >> 4, var27 >> 4)) {
                  int var28 = var1.getHeight(Types.MOTION_BLOCKING, var26, var27);
                  int var29 = var1.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, var26, var27);
                  if (!var15 || var28 > var29 + 2 || var21 >= 4) {
                     double var30 = var15
                        ? (double)var28 - 1.5
                        : (var5 != 4 && var5 != 3 ? (double)(var29 + 1) : Math.max(var2.y, (double)var29) + 25.0 + 30.0 * RANDOM.nextDouble());
                     var20 = new Vec3((double)var26 + 0.5, var30, (double)var27 + 0.5);
                  }
               }
            }

            if (var20 != null) {
               double var32 = var20.distanceTo(var2);
               float var23 = (float)Math.max((double)var16, var32 / 10.0) * master();
               float var33 = var17 + (var18 - var17) * RANDOM.nextFloat();
               var0.getSoundManager().play(new SimpleSoundInstance(var19, SoundSource.AMBIENT, var23, var33, RANDOM, var20.x, var20.y, var20.z));
            }
         }
      }
   }

   private AlpineAmbience() {
   }

   static final class Bed extends AbstractTickableSoundInstance {
      float target;

      Bed(SoundEvent var1, float var2) {
         super(var1, SoundSource.AMBIENT, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.relative = true;
         this.attenuation = Attenuation.NONE;
         this.target = var2;
         this.volume = Math.max(0.01F, var2 * 0.2F);
         this.x = 0.0;
         this.y = 0.0;
         this.z = 0.0;
      }

      public void tick() {
         this.volume = this.volume + Mth.clamp(this.target - this.volume, -0.012F, 0.012F);
         if (this.target <= 0.005F && this.volume <= 0.01F) {
            this.stop();
         }
      }
   }

   private static final class Singer {
      Vec3 at;
      String song;
      float pitch;
      double until;
      double next;
      double rest;
   }
}
