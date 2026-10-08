package com.formaworks.frontierhunts.landscape;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class AlpineWaterfallClient {
   private static final Map<Long, List<AlpineWaterfallClient.Emitter>> EMITTERS = new ConcurrentHashMap<>();
   private static final Map<Long, AlpineWaterfallClient.Scan> PENDING = new ConcurrentHashMap<>();
   private static final List<AlpineWaterfallClient.Emitter> NEAR_IMPACTS = new ArrayList<>();
   private static final List<AlpineWaterfallClient.Emitter> NEAR_LIPS = new ArrayList<>();
   private static ClientLevel world;
   private static final AlpineWaterfallClient.Voice[][] VOICES = new AlpineWaterfallClient.Voice[2][3];
   private static AlpineWaterfallClient.Voice trickle;
   private static AlpineWaterfallClient.Voice under;
   private static int tick;
   private static int cursor;
   private static int refreshCursor;
   private static long emitted;

   public static String reviewSnapshot() {
      return "emitter_chunks=" + EMITTERS.size() + " near_impacts=" + NEAR_IMPACTS.size() + " near_lips=" + NEAR_LIPS.size() + " emitted=" + emitted;
   }

   public static int reviewSourceCount() {
      return EMITTERS.values().stream().mapToInt(List::size).sum();
   }

   static boolean falling(BlockState var0) {
      if (var0.getBlock() instanceof AlpineCascade) {
         return true;
      } else {
         FluidState var1 = var0.getFluidState();
         return var1.is(FluidTags.WATER) && var1.hasProperty(BlockStateProperties.FALLING) && (Boolean)var1.getValue(BlockStateProperties.FALLING);
      }
   }

   @SubscribeEvent
   public static void load(Load var0) {
      if (var0.getChunk() instanceof LevelChunk var1 && var1.getLevel().isClientSide) {
         PENDING.put(var1.getPos().toLong(), new AlpineWaterfallClient.Scan(var1));
      }
   }

   @SubscribeEvent
   public static void unload(Unload var0) {
      if (var0.getChunk() instanceof LevelChunk var1 && var1.getLevel().isClientSide) {
         PENDING.remove(var1.getPos().toLong());
         EMITTERS.remove(var1.getPos().toLong());
      }
   }

   @SubscribeEvent
   public static void update(Post var0) {
      ProfilerFiller var1 = Minecraft.getInstance().getProfiler();
      var1.push("frontierhunts_falls_audio");

      try {
         updateInner(var0);
      } finally {
         var1.pop();
      }
   }

   private static void updateInner(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != world) {
         EMITTERS.clear();
         NEAR_IMPACTS.clear();
         NEAR_LIPS.clear();
         PENDING.values().removeIf(var1x -> var1x.chunk.getLevel() != var1.level);
         stopAll(var1);
         world = var1.level;
      }

      if (world != null && var1.player != null && !var1.isPaused()) {
         int var2 = EMITTERS.isEmpty() ? 8 : (world.getGameTime() % 2L == 0L ? 2 : 1);

         for (int var3 = 0; var3 < var2; var3++) {
            int var4 = refreshCursor++ % 49;
            int var5 = var1.player.chunkPosition().x + var4 % 7 - 3;
            int var6 = var1.player.chunkPosition().z + var4 / 7 - 3;
            LevelChunk var7 = world.getChunkSource().getChunkNow(var5, var6);
            if (var7 != null) {
               PENDING.putIfAbsent(var7.getPos().toLong(), new AlpineWaterfallClient.Scan(var7));
            }
         }

         int var15 = PENDING.size() > 16 ? 12 : (PENDING.size() > 6 ? 6 : 2);
         int var16 = 0;

         for (int var17 = 0; var17 < 96 && !PENDING.isEmpty() && var16 < var15; var17++) {
            Entry var19 = null;
            int var20 = var1.player.chunkPosition().x;
            int var8 = var1.player.chunkPosition().z;

            label103:
            for (int var9 = 0; var9 <= 5; var9++) {
               for (int var10 = -var9; var10 <= var9; var10++) {
                  for (int var11 = -var9; var11 <= var9; var11++) {
                     if (Math.max(Math.abs(var11), Math.abs(var10)) == var9) {
                        long var12 = ChunkPos.asLong(var20 + var11, var8 + var10);
                        AlpineWaterfallClient.Scan var14 = PENDING.get(var12);
                        if (var14 != null) {
                           var19 = Map.entry(var12, var14);
                           break label103;
                        }
                     }
                  }
               }
            }

            if (var19 == null) {
               Iterator var21 = PENDING.entrySet().iterator();
               if (var21.hasNext()) {
                  var19 = (Entry)var21.next();
               }
            }

            if (var19 == null) {
               break;
            }

            AlpineWaterfallClient.Scan var22 = (AlpineWaterfallClient.Scan)var19.getValue();
            if (var22.chunk.getLevel() != world) {
               PENDING.remove(var19.getKey(), var22);
            } else {
               var16++;
               if (var22.step()) {
                  if (!var22.found.isEmpty()) {
                     EMITTERS.put((Long)var19.getKey(), List.copyOf(var22.found));
                  } else {
                     EMITTERS.remove(var19.getKey());
                  }

                  PENDING.remove(var19.getKey(), var22);
               }
            }
         }

         Vec3 var18 = var1.gameRenderer.getMainCamera().getPosition();
         if (++tick % 5 == 0) {
            mix(var1, var18);
         }

         particles(var1, var18);
      }
   }

   private static void mix(Minecraft var0, Vec3 var1) {
      NEAR_IMPACTS.clear();
      NEAR_LIPS.clear();
      HashMap var2 = new HashMap();

      for (Entry var4 : EMITTERS.entrySet()) {
         ChunkPos var5 = new ChunkPos((Long)var4.getKey());
         if (!(Math.abs((double)var5.getMiddleBlockX() - var1.x) > 300.0) && !(Math.abs((double)var5.getMiddleBlockZ() - var1.z) > 300.0)) {
            for (AlpineWaterfallClient.Emitter var7 : (List)var4.getValue()) {
               double var8 = (var7.x - var1.x) * (var7.x - var1.x) + (var7.z - var1.z) * (var7.z - var1.z);
               if (var8 < 9216.0) {
                  if (var7.impact) {
                     if (NEAR_IMPACTS.size() < 512) {
                        NEAR_IMPACTS.add(var7);
                     }
                  } else if (NEAR_LIPS.size() < 512) {
                     NEAR_LIPS.add(var7);
                  }
               }

               long var10 = (long)Math.floorDiv((int)var7.x, 10) << 32 ^ (long)Math.floorDiv((int)var7.z, 10) & 4294967295L;
               var2.computeIfAbsent(var10, var0x -> new AlpineWaterfallClient.Fall()).add(var7, var1);
            }
         }
      }

      ArrayList var18 = new ArrayList();
      HashSet var19 = new HashSet();

      for (Long var22 : var2.keySet()) {
         if (var19.add(var22)) {
            AlpineWaterfallClient.Fall var24 = new AlpineWaterfallClient.Fall();
            ArrayDeque var25 = new ArrayDeque();
            var25.add(var22);

            while (!var25.isEmpty()) {
               long var9 = (Long)var25.poll();
               AlpineWaterfallClient.Fall var11 = (AlpineWaterfallClient.Fall)var2.get(var9);
               if (var11.best < var24.best) {
                  var24.best = var11.best;
                  var24.px = var11.px;
                  var24.py = var11.py;
                  var24.pz = var11.pz;
               }

               var24.power = var24.power + var11.power;
               var24.columns = var24.columns + var11.columns;
               var24.tallest = Math.max(var24.tallest, var11.tallest);
               var24.ix = var24.ix + var11.ix;
               var24.iy = var24.iy + var11.iy;
               var24.iz = var24.iz + var11.iz;
               var24.iw = var24.iw + var11.iw;
               int var12 = (int)(var9 >> 32);
               int var13 = (int)var9;

               for (int var14 = -1; var14 <= 1; var14++) {
                  for (int var15 = -1; var15 <= 1; var15++) {
                     long var16 = (long)(var12 + var14) << 32 ^ (long)(var13 + var15) & 4294967295L;
                     if (var2.containsKey(var16) && var19.add(var16)) {
                        var25.add(var16);
                     }
                  }
               }
            }

            var18.add(var24);
         }
      }

      var18.sort(Comparator.comparingDouble(AlpineWaterfallClient.Fall::level).reversed());
      boolean var21 = var0.player.isEyeInFluid(FluidTags.WATER);
      double var23 = 0.0;
      int var26 = 0;
      AlpineWaterfallClient.Fall var27 = null;

      for (AlpineWaterfallClient.Fall var31 : var18) {
         if (var31.tallest < 5 && var31.power < 18.0) {
            if (var27 == null || var31.level() > var27.level()) {
               var27 = var31;
            }
         } else if (var26 < 2) {
            voice(var0, var26++, var31, var21);
            if (var21 && var31.best < 24.0) {
               var23 = Math.max(var23, gain(var31.level(), -38.0, 2.0));
            }
         }
      }

      for (int var29 = var26; var29 < 2; var29++) {
         for (int var32 = 0; var32 < 3; var32++) {
            aim(VOICES[var29][var32], null, 0.0F);
         }
      }

      if (var27 != null) {
         double var30 = gain(var27.level(), -30.0, 4.0) * (var21 ? 0.25 : 1.0);
         trickle = voice(var0, trickle, (SoundEvent)AlpineRegistration.FALLS_TRICKLE.get(), var27.px, var27.py, var27.pz, (float)(var30 * 0.85), 1.0F);
      } else {
         aim(trickle, null, 0.0F);
      }

      under = voice(var0, under, (SoundEvent)AlpineRegistration.FALLS_UNDERWATER.get(), var1.x, var1.y, var1.z, (float)(var23 * 0.9), 1.0F);
   }

   private static double gain(double var0, double var2, double var4) {
      double var6 = Math.clamp((var0 - var2) / (40.0 + var4), 0.0, 1.0);
      return var6 * var6;
   }

   private static void voice(Minecraft var0, int var1, AlpineWaterfallClient.Fall var2, boolean var3) {
      double var4 = Math.max(1.0, var2.best);
      double var6 = gain(var2.level(), -44.0, 0.0);
      double var8 = var6 * (1.0 - AlpineLayout.smooth(6.0, 42.0, var4)) * 1.05;
      double var10 = var6 * (0.95 - 0.55 * AlpineLayout.smooth(35.0, 140.0, var4));
      double var12 = var6 * (0.35 + 0.65 * AlpineLayout.smooth(8.0, 70.0, var4))
         + Math.min(0.35, var2.power / 900.0) * (1.0 - AlpineLayout.smooth(120.0, 320.0, var4));
      if (var3) {
         var8 *= 0.12;
         var10 *= 0.25;
         var12 *= 0.35;
      }

      float var14 = var1 == 0 ? 1.0F : 0.965F;
      double var15 = var2.iw > 0.0 ? var2.ix / var2.iw : var2.px;
      double var17 = var2.iw > 0.0 ? var2.iy / var2.iw : var2.py;
      double var19 = var2.iw > 0.0 ? var2.iz / var2.iw : var2.pz;
      VOICES[var1][0] = voice(
         var0, VOICES[var1][0], (SoundEvent)AlpineRegistration.FALLS_ROAR_FAR.get(), var15, var17, var19, (float)Math.min(1.0, var12), var14 * 0.98F
      );
      VOICES[var1][1] = voice(
         var0, VOICES[var1][1], (SoundEvent)AlpineRegistration.FALLS_ROAR.get(), var2.px, var2.py + 2.0, var2.pz, (float)Math.min(1.0, var10), var14
      );
      VOICES[var1][2] = voice(
         var0, VOICES[var1][2], (SoundEvent)AlpineRegistration.FALLS_CRASH.get(), var2.px, var2.py, var2.pz, (float)Math.min(1.0, var8), var14 * 1.02F
      );
   }

   private static void aim(AlpineWaterfallClient.Voice var0, Vec3 var1, float var2) {
      if (var0 != null) {
         var0.target = var2;
      }
   }

   private static AlpineWaterfallClient.Voice voice(
      Minecraft var0, AlpineWaterfallClient.Voice var1, SoundEvent var2, double var3, double var5, double var7, float var9, float var10
   ) {
      if (var1 != null && !var1.isStopped()) {
         var1.tx = var3;
         var1.ty = var5;
         var1.tz = var7;
         var1.target = var9;
      } else {
         if (var9 < 0.004F) {
            return null;
         }

         var1 = new AlpineWaterfallClient.Voice(var2, var3, var5, var7, var9, var10);
         var0.getSoundManager().play(var1);
      }

      return var1;
   }

   private static void stopAll(Minecraft var0) {
      for (AlpineWaterfallClient.Voice[] var4 : VOICES) {
         for (int var5 = 0; var5 < var4.length; var5++) {
            if (var4[var5] != null) {
               var0.getSoundManager().stop(var4[var5]);
            }

            var4[var5] = null;
         }
      }

      if (trickle != null) {
         var0.getSoundManager().stop(trickle);
      }

      if (under != null) {
         var0.getSoundManager().stop(under);
      }

      under = null;
      trickle = null;
   }

   private static void particles(Minecraft var0, Vec3 var1) {
      if (AlpineWaterfallOptions.visuals() && (!NEAR_IMPACTS.isEmpty() || !NEAR_LIPS.isEmpty())) {
         int var2 = AlpineWaterfallOptions.budget(switch ((ParticleStatus)var0.options.particles().get()) {
            case MINIMAL -> 6;
            case DECREASED -> 22;
            default -> 46;
         });
         RandomSource var3 = world.random;

         for (int var4 = 0; var4 < var2; var4++) {
            boolean var5 = !NEAR_LIPS.isEmpty() && (NEAR_IMPACTS.isEmpty() || var4 % 5 == 4);
            List var6 = var5 ? NEAR_LIPS : NEAR_IMPACTS;
            AlpineWaterfallClient.Emitter var7 = (AlpineWaterfallClient.Emitter)var6.get(Math.floorMod(cursor++, var6.size()));
            double var8 = (var7.x - var1.x) * (var7.x - var1.x) + (var7.y - var1.y) * (var7.y - var1.y) + (var7.z - var1.z) * (var7.z - var1.z);
            if (!(var8 > 8100.0)) {
               double var10 = Math.clamp((double)var7.height / 20.0, 0.15, 2.2);
               if (!(var3.nextDouble() > 0.25 + var10 * 0.4)) {
                  emitted++;
                  double var12 = var7.x + (var3.nextDouble() - 0.5) * 0.9;
                  double var14 = var7.z + (var3.nextDouble() - 0.5) * 0.9;
                  if (var5) {
                     world.addParticle(
                        (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                        var12,
                        var7.y - var3.nextDouble() * (double)Math.min(4, var7.height),
                        var14,
                        (var3.nextDouble() - 0.5) * 0.05,
                        -0.25 - var3.nextDouble() * 0.35,
                        (var3.nextDouble() - 0.5) * 0.05
                     );
                     if (var3.nextDouble() < 0.3) {
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.MIST_PARTICLE.get(),
                           var12 + (var3.nextDouble() - 0.5) * 2.4,
                           var7.y - var3.nextDouble() * (double)Math.min(6, var7.height),
                           var14 + (var3.nextDouble() - 0.5) * 2.4,
                           (var3.nextDouble() - 0.5) * 0.02,
                           -0.01 - var3.nextDouble() * 0.02,
                           (var3.nextDouble() - 0.5) * 0.02
                        );
                     }

                     if (var7.height > 8 && var3.nextDouble() < 0.45) {
                        double var16 = var3.nextDouble() * (double)var7.height;
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.MIST_PARTICLE.get(),
                           var12 + (var3.nextDouble() - 0.5) * 1.6,
                           var7.y - var16,
                           var14 + (var3.nextDouble() - 0.5) * 1.6,
                           (var3.nextDouble() - 0.5) * 0.03,
                           -0.03 - var3.nextDouble() * 0.04,
                           (var3.nextDouble() - 0.5) * 0.03
                        );
                        if (var3.nextDouble() < 0.5) {
                           world.addParticle(
                              (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                              var12,
                              var7.y - var16,
                              var14,
                              (var3.nextDouble() - 0.5) * 0.08,
                              -0.35 - var3.nextDouble() * 0.3,
                              (var3.nextDouble() - 0.5) * 0.08
                           );
                        }
                     }
                  } else {
                     double var24 = var7.y + var3.nextDouble() * 0.4;
                     double var18 = 0.08 + var10 * 0.07;
                     world.addParticle(
                        (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                        var12,
                        var24,
                        var14,
                        (var3.nextDouble() - 0.5) * var18 * 2.0,
                        0.18 + var10 * 0.12 + var3.nextDouble() * 0.14,
                        (var3.nextDouble() - 0.5) * var18 * 2.0
                     );
                     if (var3.nextDouble() < 0.55) {
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                           var12,
                           var24,
                           var14,
                           (var3.nextDouble() - 0.5) * var18,
                           0.04 + var3.nextDouble() * 0.05,
                           (var3.nextDouble() - 0.5) * var18
                        );
                     }

                     if (var10 > 0.35 && var3.nextDouble() < 0.22 + var10 * 0.12) {
                        double var20 = var3.nextDouble() * Math.PI * 2.0;
                        double var22 = 0.05 + var3.nextDouble() * 0.07 * var10;
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.MIST_PARTICLE.get(),
                           var12 + Math.cos(var20) * (1.2 + var3.nextDouble() * 2.4),
                           var24 + 0.05,
                           var14 + Math.sin(var20) * (1.2 + var3.nextDouble() * 2.4),
                           Math.cos(var20) * var22,
                           0.004 + var3.nextDouble() * 0.008,
                           Math.sin(var20) * var22
                        );
                     }

                     if (var10 > 0.25 && var3.nextDouble() < 0.3 + var10 * 0.18) {
                        double var25 = var3.nextDouble() * Math.PI * 2.0;
                        double var27 = 0.025 + var3.nextDouble() * 0.04 * var10;
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.MIST_PARTICLE.get(),
                           var12 + Math.cos(var25) * 0.6,
                           var24 + 0.2,
                           var14 + Math.sin(var25) * 0.6,
                           Math.cos(var25) * var27,
                           0.012 + var3.nextDouble() * 0.025 * var10,
                           Math.sin(var25) * var27
                        );
                     }

                     if (var10 > 0.5 && var3.nextDouble() < 0.35) {
                        double var26 = var3.nextDouble() * Math.PI * 2.0;
                        double var28 = 0.1 + var3.nextDouble() * 0.12 * var10;
                        world.addParticle(
                           (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                           var12,
                           var24 + 0.15,
                           var14,
                           Math.cos(var26) * var28,
                           0.1 + var3.nextDouble() * 0.1,
                           Math.sin(var26) * var28
                        );
                     }
                  }
               }
            }
         }
      }
   }

   private static record Emitter(double x, double y, double z, int height, boolean impact) {
   }

   private static final class Fall {
      double px;
      double py;
      double pz;
      double best = Double.MAX_VALUE;
      double power;
      int columns;
      int tallest;
      double ix;
      double iy;
      double iz;
      double iw;

      void add(AlpineWaterfallClient.Emitter var1, Vec3 var2) {
         double var3 = Math.sqrt(
            var1.x * 0.0 + (var1.x - var2.x) * (var1.x - var2.x) + (var1.y - var2.y) * (var1.y - var2.y) + (var1.z - var2.z) * (var1.z - var2.z)
         );
         if (var3 < this.best) {
            this.best = var3;
            this.px = var1.x;
            this.py = var1.y;
            this.pz = var1.z;
         }

         if (var1.impact) {
            double var5 = Math.pow((double)var1.height, 0.9);
            this.power += var5;
            this.columns++;
            this.ix = this.ix + var1.x * var5;
            this.iy = this.iy + var1.y * var5;
            this.iz = this.iz + var1.z * var5;
            this.iw += var5;
         } else {
            this.power = this.power + Math.pow((double)var1.height, 0.9) * 0.35;
         }

         this.tallest = Math.max(this.tallest, var1.height);
      }

      double level() {
         double var1 = Math.max(2.5, this.best);
         return 10.0 * Math.log10(Math.max(0.001, this.power)) - 20.0 * Math.log10(var1);
      }
   }

   private static final class Scan {
      final LevelChunk chunk;
      int section;
      final List<AlpineWaterfallClient.Emitter> found = new ArrayList<>();

      Scan(LevelChunk var1) {
         this.chunk = var1;
      }

      boolean step() {
         while (this.section < this.chunk.getSectionsCount()) {
            LevelChunkSection var1 = this.chunk.getSections()[this.section];
            if (var1.hasOnlyAir() || !var1.maybeHas(AlpineWaterfallClient::falling)) {
               this.section++;
               continue;
            }
            break;
         }

         if (this.section >= this.chunk.getSectionsCount()) {
            return true;
         } else {
            int var17 = this.section++;
            LevelChunkSection var2 = this.chunk.getSections()[var17];
            Level var3 = this.chunk.getLevel();
            MutableBlockPos var4 = new MutableBlockPos();
            int var5 = this.chunk.getSectionYFromSectionIndex(var17) << 4;

            for (int var6 = 0; var6 < 16; var6++) {
               for (int var7 = 0; var7 < 16; var7++) {
                  for (int var8 = 0; var8 < 16; var8++) {
                     if (AlpineWaterfallClient.falling(var2.getBlockState(var8, var6, var7))) {
                        int var9 = this.chunk.getPos().getMinBlockX() + var8;
                        int var10 = var5 + var6;
                        int var11 = this.chunk.getPos().getMinBlockZ() + var7;
                        var4.set(var9, var10 - 1, var11);
                        BlockState var12 = var3.getBlockState(var4);
                        var4.set(var9, var10 + 1, var11);
                        BlockState var13 = var3.getBlockState(var4);
                        boolean var14 = !AlpineWaterfallClient.falling(var12);
                        boolean var15 = !AlpineWaterfallClient.falling(var13);
                        if ((var14 || var15) && this.found.size() < 192) {
                           int var16 = 1;
                           if (var14) {
                              var4.set(var9, var10, var11);

                              while (var16 < 128 && AlpineWaterfallClient.falling(var3.getBlockState(var4.move(0, 1, 0)))) {
                                 var16++;
                              }
                           } else {
                              var4.set(var9, var10, var11);

                              while (var16 < 128 && AlpineWaterfallClient.falling(var3.getBlockState(var4.move(0, -1, 0)))) {
                                 var16++;
                              }
                           }

                           if (var16 >= 2) {
                              this.found
                                 .add(
                                    new AlpineWaterfallClient.Emitter(
                                       (double)var9 + 0.5, var14 ? (double)var10 + 0.1 : (double)var10 + 0.9, (double)var11 + 0.5, var16, var14
                                    )
                                 );
                           }
                        }
                     }
                  }
               }
            }

            return this.section >= this.chunk.getSectionsCount();
         }
      }
   }

   private static final class Voice extends AbstractTickableSoundInstance {
      double tx;
      double ty;
      double tz;
      float target;

      Voice(SoundEvent var1, double var2, double var4, double var6, float var8, float var9) {
         super(var1, SoundSource.AMBIENT, RandomSource.create());
         this.x = this.tx = var2;
         this.y = this.ty = var4;
         this.z = this.tz = var6;
         this.target = var8;
         this.pitch = var9;
         this.looping = true;
         this.delay = 0;
         this.attenuation = Attenuation.NONE;
         this.volume = Math.max(0.004F, var8 * 0.15F);
      }

      public void tick() {
         this.x = this.x + (this.tx - this.x) * 0.15;
         this.y = this.y + (this.ty - this.y) * 0.15;
         this.z = this.z + (this.tz - this.z) * 0.15;
         this.volume = this.volume + (this.target - this.volume) * 0.07F;
         if (this.target <= 0.0F && this.volume < 0.003F) {
            this.stop();
         }
      }
   }
}
