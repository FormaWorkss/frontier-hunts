package com.formaworks.frontierhunts.landscape;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

final class AlpineImpactWater {
   private static final List<AlpineImpactWater.Pool> pools = new ArrayList<>();
   private static ClientLevel world;
   private static long tick = Long.MIN_VALUE;

   static void discover(ClientLevel var0, AlpineCascadeSurface var1, Vec3 var2) {
      if (world != var0) {
         world = var0;
         pools.clear();
         tick = Long.MIN_VALUE;
      }

      pools.removeIf(var2x -> var0.getGameTime() - var2x.touched > 40L || var2.distanceToSqr(var2x.x, var2x.y, var2x.z) > 8100.0);
      ArrayList var3 = new ArrayList<>(var1.strips);
      var3.sort(Comparator.<AlpineCascadeSurface.Strip>comparingDouble(var0x -> var0x.top - var0x.bottom).reversed());

      for (AlpineCascadeSurface.Strip var5 : var3) {
         if (!(var5.top - var5.bottom < 3.0)) {
            Direction var6 = var5.key.face();
            boolean var7 = var6.getAxis() == Axis.X;
            double var8 = var5.normal(var5.bottom) + 1.5;
            double var10 = var7 ? var8 * (double)var6.getStepX() : (double)var5.key.lateral() + 0.5;
            double var12 = var7 ? (double)var5.key.lateral() + 0.5 : var8 * (double)var6.getStepZ();
            AlpineImpactWater.Pool var14 = null;

            for (AlpineImpactWater.Pool var16 : pools) {
               if (Math.hypot(var16.x - var10, var16.z - var12) < 9.0 && Math.abs(var16.y - var5.bottom) < 5.0) {
                  var14 = var16;
                  break;
               }
            }

            if (var14 != null) {
               var14.touched = var0.getGameTime();
            } else if (pools.size() < 3) {
               for (int var24 = (int)var5.bottom + 2; var24 >= (int)var5.bottom - 6; var24--) {
                  BlockPos var25 = BlockPos.containing(var10, (double)var24, var12);
                  if (!var0.hasChunkAt(var25)) {
                     break;
                  }

                  FluidState var17 = var0.getFluidState(var25);
                  if (var17.is(FluidTags.WATER) && !var0.getFluidState(var25.above()).is(FluidTags.WATER)) {
                     AlpineImpactWater.Pool var18 = new AlpineImpactWater.Pool(
                        var0, var10, (double)((float)var24 + var17.getHeight(var0, var25)), var12, Math.clamp((var5.top - var5.bottom) / 16.0, 0.25, 1.0)
                     );
                     int var19 = 0;

                     for (float var23 : var18.sim.depth) {
                        if (var23 > 0.0F) {
                           var19++;
                        }
                     }

                     if (var19 >= Math.max(48, (int)(var18.power * 150.0))) {
                        pools.add(var18);
                     }
                     break;
                  }
               }
            }
         }
      }
   }

   static void reset() {
      world = null;
      pools.clear();
      tick = Long.MIN_VALUE;
   }

   static boolean covers(double var0, double var2, double var4) {
      for (AlpineImpactWater.Pool var7 : pools) {
         if (Math.abs(var2 - var7.y) < 1.0 && Math.abs(var0 - var7.x) < 9.0 && Math.abs(var4 - var7.z) < 9.0) {
            return true;
         }
      }

      return false;
   }

   static String diagnostics() {
      return " impact_pools="
         + pools.stream()
            .map(
               var0 -> String.format(
                     Locale.ROOT,
                     "(%.1f,%.1f,%.1f;wet=%d)",
                     var0.x,
                     var0.y,
                     var0.z,
                     IntStream.range(0, var0.sim.depth.length).filter(var1 -> var0.sim.depth[var1] > 0.0F).count()
                  )
            )
            .toList();
   }

   static void tick(ClientLevel var0) {
      if (var0 == world && tick != var0.getGameTime()) {
         tick = var0.getGameTime();
         RandomSource var1 = var0.random;

         int var2 = AlpineWaterfallOptions.budget(switch ((ParticleStatus)Minecraft.getInstance().options.particles().get()) {
            case MINIMAL -> 1;
            case DECREASED -> 2;
            default -> 4;
         });

         for (AlpineImpactWater.Pool var4 : pools) {
            var4.sim.tick(var4.power);

            for (int var5 = 0; var5 < var2; var5++) {
               if (!(var1.nextDouble() > var4.power * var4.power * 0.55)) {
                  double var6 = var1.nextDouble() * Math.PI * 2.0;
                  double var8 = var1.nextDouble() * 1.7;
                  double var10 = Math.cos(var6);
                  double var12 = Math.sin(var6);
                  double var14 = var4.x + var10 * var8;
                  double var16 = var4.z + var12 * var8;
                  if (var0.getFluidState(BlockPos.containing(var14, var4.y - 0.4, var16)).is(FluidTags.WATER)) {
                     var0.addParticle(
                        (ParticleOptions)AlpineRegistration.BUBBLE_PARTICLE.get(),
                        var14,
                        var4.y - 0.15,
                        var16,
                        var10 * 0.035,
                        -0.12 - var4.power * 0.12,
                        var12 * 0.035
                     );
                     if (var5 % 2 == 0) {
                        var0.addParticle(
                           (ParticleOptions)AlpineRegistration.SPRAY_PARTICLE.get(),
                           var14,
                           var4.y + 0.1,
                           var16,
                           var10 * (0.12 + var4.power * 0.16),
                           0.22 + var4.power * 0.22,
                           var12 * (0.12 + var4.power * 0.16)
                        );
                     }
                  }
               }
            }
         }
      }
   }

   static void render(RenderLevelStageEvent var0) {
      if (!pools.isEmpty()) {
         Vec3 var1 = var0.getCamera().getPosition();
         PoseStack var2 = var0.getPoseStack();
         BufferBuilder var3 = Tesselator.getInstance().begin(Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
         double var4 = (double)var0.getPartialTick().getGameTimeDeltaPartialTick(false);
         var2.pushPose();
         var2.translate(-var1.x, -var1.y, -var1.z);
         byte var6 = 41;

         for (AlpineImpactWater.Pool var8 : pools) {
            for (int var9 = 0; var9 < var6 - 1; var9++) {
               for (int var10 = 0; var10 < var6 - 1; var10++) {
                  int var11 = var9 * var6 + var10;
                  if (var8.sim.depth[var11] != 0.0F
                     && var8.sim.depth[var11 + 1] != 0.0F
                     && var8.sim.depth[var11 + var6] != 0.0F
                     && var8.sim.depth[var11 + var6 + 1] != 0.0F) {
                     for (int var12 = 0; var12 < 4; var12++) {
                        int var13 = var10 + (var12 >= 2 ? 1 : 0);
                        int var14 = var9 + (var12 != 1 && var12 != 2 ? 0 : 1);
                        int var15 = var14 * var6 + var13;
                        double var16 = (double)Math.min(Math.min(var13, var14), Math.min(var6 - 1 - var13, var6 - 1 - var14));
                        double var18 = Math.clamp(var16 / 4.0, 0.0, 1.0);
                        double var20 = (double)var8.sim.previous[var15] + (double)(var8.sim.height[var15] - var8.sim.previous[var15]) * var4;
                        double var22 = (double)var8.sim.foam[var15];
                        float var24 = -(var8.sim.height[var14 * var6 + Math.min(var6 - 1, var13 + 1)] - var8.sim.height[var14 * var6 + Math.max(0, var13 - 1)]);
                        float var25 = -(var8.sim.height[Math.min(var6 - 1, var14 + 1) * var6 + var13] - var8.sim.height[Math.max(0, var14 - 1) * var6 + var13]);
                        float var26 = (float)Math.sqrt((double)(1.0F + var24 * var24 + var25 * var25));
                        int var27 = (int)((double)(var8.color >> 16 & 0xFF) * (1.0 - var22) + 235.0 * var22);
                        int var28 = (int)((double)(var8.color >> 8 & 0xFF) * (1.0 - var22) + 246.0 * var22);
                        int var29 = (int)((double)(var8.color & 0xFF) * (1.0 - var22) + 247.0 * var22);
                        double var30 = var8.wx(var13) - var8.x;
                        double var32 = var8.wz(var14) - var8.z;
                        var3.addVertex(
                              var2.last().pose(), (float)var8.wx(var13), (float)(var8.y + 0.045 + Math.max(-0.035, var20) * var18), (float)var8.wz(var14)
                           )
                           .setColor(var27, var28, var29, (int)((84.0 + 104.0 * var22) * var18))
                           .setUv((float)(var30 * 0.22), (float)(var32 * 0.22))
                           .setOverlay(OverlayTexture.NO_OVERLAY)
                           .setLight(var8.light)
                           .setNormal(var2.last(), var24 / var26, 1.0F / var26, var25 / var26);
                     }
                  }
               }
            }
         }

         var2.popPose();
         AlpineWaterMaterial.draw(var3.build(), var0, ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/block/alpine_pool_surface.png"));
      }
   }

   private static final class Pool {
      final double x;
      final double y;
      final double z;
      final double power;
      final AlpinePoolSimulation sim = new AlpinePoolSimulation();
      final int light;
      final int color;
      long touched;

      Pool(ClientLevel var1, double var2, double var4, double var6, double var8) {
         this.x = var2;
         this.y = var4;
         this.z = var6;
         this.power = var8;
         this.touched = var1.getGameTime();
         BlockPos var10 = BlockPos.containing(var2, var4, var6);
         this.light = LevelRenderer.getLightColor(var1, var10.above());
         this.color = BiomeColors.getAverageWaterColor(var1, var10);

         for (int var11 = 0; var11 < 41; var11++) {
            for (int var12 = 0; var12 < 41; var12++) {
               BlockPos var13 = BlockPos.containing(this.wx(var12), var4 - 0.1, this.wz(var11));
               if (var1.hasChunkAt(var13) && var1.getFluidState(var13).is(FluidTags.WATER) && !var1.getFluidState(var13.above()).is(FluidTags.WATER)) {
                  int var14 = 0;

                  for (int var15 = 0; var15 < 6 && var1.getFluidState(var13.below(var15)).is(FluidTags.WATER); var15++) {
                     var14++;
                  }

                  this.sim.depth[var11 * 41 + var12] = (float)var14;
               }
            }
         }
      }

      double wx(int var1) {
         return this.x + (double)(var1 - 20) * 0.5;
      }

      double wz(int var1) {
         return this.z + (double)(var1 - 20) * 0.5;
      }
   }
}
