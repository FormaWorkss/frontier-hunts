package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.client.rack.RackDraw;
import com.formaworks.frontierhunts.client.rutfight.RutFightClient;
import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.DeerMeshData;
import com.formaworks.frontierhunts.hunting.DeerSkeleton;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.ImpactMarks;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.perf.client.AnimationLod;
import com.formaworks.frontierhunts.perf.client.PerfStats;
import com.formaworks.frontierhunts.sign.client.SignWorkClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WhitetailRenderer extends EntityRenderer<Whitetail> {
   public static final ResourceLocation FUR = DeerDraw.COAT_SUMMER;
   public static final ResourceLocation MATERIAL = DeerDraw.MATERIAL;
   public static final ResourceLocation ALBEDO = DeerDraw.COAT_SUMMER;
   private static final Map<Whitetail, WhitetailRenderer.State> STATES = new WeakHashMap<>();
   private static long frame;
   private static final float[] SCORES = new float[256];
   private static int scoreCount;
   private static float lod0Cut;
   private static float lod1Cut;
   private static float lod2Cut;
   static final int LOD0_BUDGET = 3;
   static final int LOD1_BUDGET = 6;
   static final int LOD2_BUDGET = 14;
   private static final float WHITETAIL_REAL_XZ = 0.829932F;
   private static final float WHITETAIL_REAL_Y = 0.8297214F;
   private static long lastGlint;

   static void beginFrame() {
      frame++;
      int var0 = scoreCount;
      if (var0 > 3) {
         float[] var1 = Arrays.copyOf(SCORES, var0);
         Arrays.sort(var1);
         lod0Cut = var1[var0 - 3];
         lod1Cut = var0 > 6 ? var1[var0 - 6] : 0.0F;
         lod2Cut = var0 > 14 ? var1[var0 - 14] : 0.0F;
      } else {
         lod0Cut = 0.0F;
         lod1Cut = 0.0F;
         lod2Cut = 0.0F;
      }

      scoreCount = 0;
   }

   private static void realScale(PoseStack var0, Whitetail var1) {
      realScale(var0, var1.species());
   }

   static void realScale(PoseStack var0, GameSpecies var1) {
      if (var1 == GameSpecies.WHITETAIL) {
         var0.scale(0.829932F, 0.8297214F, 0.829932F);
      }
   }

   public WhitetailRenderer(Context var1) {
      this(var1, GameSpecies.WHITETAIL);
   }

   public WhitetailRenderer(Context var1, GameSpecies var2) {
      super(var1);
      this.shadowRadius = var2 == GameSpecies.WHITETAIL ? 0.48F : var2.width * 0.52F;
      DeerDraw.ensureTextures();
   }

   static void clear() {
      STATES.clear();
      DeerDraw.reset();
      RealisticCoats.reset();
      StylizedAnimal.clear();
   }

   public ResourceLocation getTextureLocation(Whitetail var1) {
      return DeerDraw.coat(var1.traits());
   }

   static DeerAnimator pose(Whitetail var0, float var1) {
      WhitetailRenderer.State var2 = STATES.computeIfAbsent(var0, WhitetailRenderer.State::new);
      float var3 = ((float)var0.tickCount + var1) / 20.0F;
      if (var3 == var2.lastTime) {
         return var2.animator;
      } else if (!Float.isNaN(var2.lastTime)
         && var3 > var2.lastTime
         && !var0.downed()
         && var0.hurtTime == 0
         && !KillCamClient.isDouble(var0)
         && !AnimationLod.due(var3 - var2.lastTime, animationDistance(var0))) {
         return var2.animator;
      } else {
         float var4 = Float.isNaN(var2.lastTime) ? 0.0F : Math.max(0.0F, Math.min(0.25F, var3 - var2.lastTime));
         var2.lastTime = var3;
         PerfStats.inc(11);
         boolean var5 = !var0.onGround() && !var0.isInWaterOrBubble() && !var0.downed();
         var2.airTime = var5 ? var2.airTime + var4 : 0.0F;
         DeerAnimator.Input var6 = var2.input;
         var0.animatorInput(var6, var1, var2.airTime);
         RutFightClient.prepare(var0, var6, var1);
         SignWorkClient.prepare(var0, var6, var1);
         var2.animator.update(var6);
         return var2.animator;
      }
   }

   private static double animationDistance(Whitetail var0) {
      Minecraft var1 = Minecraft.getInstance();
      double var2 = Math.sqrt(var1.getEntityRenderDispatcher().distanceToSqr(var0));
      return AnimationLod.effective(
         var2 * ViewProjection.scale() / Math.tan(Math.toRadians(35.0)) * Math.tan(Math.toRadians(30.0)), (double)var0.getBbHeight(), 1.0
      );
   }

   static McAnimalPose classicPose(Whitetail var0, float var1) {
      pose(var0, var1);
      WhitetailRenderer.State var2 = STATES.get(var0);
      if (var2.classic == null) {
         var2.classic = new McAnimalPose(var0.species());
      }

      var2.classic.update(var2.input, var0.traits().frameLength(), Float.NaN);
      return var2.classic;
   }

   static float[] boxRackOffset(GameSpecies var0) {
      return switch (var0) {
         case ELK -> new float[]{0.0F, 0.0F, 0.57F};
         case MOOSE -> new float[]{0.0F, -0.06F, 0.6F};
         default -> new float[]{0.0F, -0.09F, 0.02F};
      };
   }

   static void boxRack(PoseStack var0, MultiBufferSource var1, int var2, DeerTraits var3, Matrix4f[] var4, Matrix4f var5) {
      if (var3.buck() && var3.species().antlers != GameSpecies.Antlers.NONE) {
         float[] var6 = boxRackOffset(var3.species());
         Vector3f var7 = var5.transformDirection(new Vector3f(var6[0], var6[1], var6[2]));
         var0.pushPose();
         var0.translate(var7.x, var7.y, var7.z);
         if (!SculptRack.draw(var0, var1, null, var2, var3, var4, 1, null)) {
            RackDraw.draw(var0, var1.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.antler())), var2, var3, var5, 8, null);
         }

         var0.popPose();
      }
   }

   static float[] legendCoat(GameSpecies var0) {
      return switch (var0) {
         case ELK -> new float[]{1.32F, 1.3F, 1.28F};
         case MOOSE -> new float[]{0.5F, 0.47F, 0.45F};
         default -> new float[]{0.78F, 0.76F, 0.75F};
      };
   }

   static void legendGlint(Whitetail var0) {
      if (!var0.downed() && var0.tickCount % 9 == 0) {
         long var1 = (long)var0.getId() << 32 | (long)var0.tickCount;
         if (var1 != lastGlint) {
            lastGlint = var1;
            RandomSource var3 = var0.getRandom();
            double var4 = (double)var0.getBbHeight() * (var0.isCrouching() ? 0.6 : 0.95);
            var0.level()
               .addParticle(
                  ParticleTypes.WAX_ON,
                  var0.getX() + (var3.nextDouble() - 0.5) * (double)var0.getBbWidth() * 1.4,
                  var0.getY() + var4 + var3.nextDouble() * 0.6,
                  var0.getZ() + (var3.nextDouble() - 0.5) * (double)var0.getBbWidth() * 1.4,
                  0.0,
                  0.02,
                  0.0
               );
         }
      }
   }

   static void stylizedAntlers(PoseStack var0, MultiBufferSource var1, int var2, DeerTraits var3, Matrix4f var4, Matrix4f[] var5) {
      if (var3.species().antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
         DeerDraw.antlers(var0, var1.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.antler())), var2, var3, var4, 8, null);
      } else if (!SculptRack.draw(var0, var1, null, var2, var3, var5, 2, null)) {
         if (MeshAntlers.has(var3.species())) {
            MeshAntlers.draw(var0, var1, var2, var3, var5, null);
         } else {
            CubeAntlers.draw(var0, var1.getBuffer(HuntRenderTypes.sculpt(DeerDraw.ANTLER)), var2, var3, var4, 8, null);
         }
      }
   }

   static void thermal(Whitetail var0, float var1, PoseStack var2, VertexConsumer var3) {
      DeerTraits var4 = var0.traits();
      DeerAnimator var5 = pose(var0, var1);
      var2.pushPose();
      var2.scale(var4.frameWidth(), var4.frameHeight(), var4.frameLength());
      if (FrontierGraphics.realisticAnimals()) {
         realScale(var2, var0);
         float[][] var6 = DeerDraw.skin(var5, var4, 2);
         float[] var7 = new float[]{1.0F, 1.0F, 1.0F};
         DeerDraw.body(var2, var3, 15728880, var4, 2, var6, var7);
         if (var4.buck() && !SculptRack.draw(var2, null, var3, 15728880, var4, var5, 2, var7)) {
            RackDraw.draw(var2, var3, 15728880, var4, var5.model[DeerSkeleton.of(var0.species()).head], 4, var7);
         }

         var2.popPose();
      } else {
         StylizedAnimal var11 = StylizedAnimal.active(var0.species());
         if (var11 != null) {
            float[] var12 = new float[]{1.0F, 1.0F, 1.0F};
            var11.draw(var2.last(), var3, 15728880, CubeAnimal.OVERLAY, var5.skin, 1.0F, 1.0F, 1.0F);
            if (var4.buck()) {
               if (var0.species().antlers == GameSpecies.Antlers.PROCEDURAL_WHITETAIL) {
                  DeerDraw.antlers(var2, var3, 15728880, var4, var5.model[DeerSkeleton.of(var0.species()).head], 4, var12);
               } else if (!SculptRack.draw(var2, null, var3, 15728880, var4, var5.skin, 3, var12)) {
                  if (MeshAntlers.has(var0.species())) {
                     MeshAntlers.drawInto(var2, var3, 15728880, var4, var5.skin, var12);
                  } else {
                     CubeAntlers.draw(var2, var3, 15728880, var4, var5.model[DeerSkeleton.of(var0.species()).head], 4, var12);
                  }
               }
            }

            var2.popPose();
         } else {
            CubeAnimalData var13 = CubeAnimalData.of(var0.species());
            Matrix4f[] var8 = var5.skin;
            Matrix4f var9 = var5.model[DeerSkeleton.of(var0.species()).head];
            if (FrontierGraphics.vanillaAnimals()) {
               McAnimalPose var10 = classicPose(var0, var1);
               var8 = var10.skin;
               var9 = var10.head;
            }

            CubeAnimal.draw(
               var2.last(),
               var3,
               15728880,
               CubeAnimal.OVERLAY,
               var13.bones,
               var13.data,
               var13.stride,
               var8,
               1.0F,
               1.0F,
               1.0F,
               1.0F,
               ClassicAntlers.antlerBits(var0.species())
            );
            ClassicAntlers.draw(var2.last(), var3, 15728880, CubeAnimal.OVERLAY, var4, var8, 1.0F, 1.0F, 1.0F);
            var2.popPose();
         }
      }
   }

   public void render(Whitetail var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      if (!var1.isInvisible() && FrontierGraphics.realisticAnimals()) {
         this.renderRealistic(var1, var2, var3, var4, var5, var6);
      } else if (!var1.isInvisible()) {
         DeerDraw.ensureTextures(var1.species());
         var6 = FieldEntityLight.apply(var1, var6);
         DeerTraits var7 = var1.traits();
         DeerAnimator var8 = pose(var1, var3);
         boolean var9 = FrontierGraphics.vanillaAnimals() || StylizedAnimal.active(var1.species()) == null;
         CubeAnimalData var10 = CubeAnimalData.of(var1.species(), var9);
         boolean var11 = var9 && var7.buck() && SculptRack.legend(var7) >= 0.5F;
         Boolean var12 = ClassicAntlers.force;
         if (var9) {
            ClassicAntlers.force = Boolean.TRUE;
         }

         Matrix4f[] var13 = var8.skin;
         Matrix4f var14 = var8.model[DeerSkeleton.of(var1.species()).head];
         int var15 = CubeAnimal.OVERLAY;
         if (var9) {
            McAnimalPose var16 = classicPose(var1, var3);
            var13 = var16.skin;
            var14 = var16.head;
            var15 = OverlayTexture.pack(0, var1.hurtTime > 0 && !var1.downed() ? 3 : 10);
         }

         StylizedAnimal var27 = var9 ? null : StylizedAnimal.active(var1.species());
         if (var27 != null) {
            var15 = OverlayTexture.pack(0, var1.hurtTime > 0 && !var1.downed() ? 3 : 10);
         }

         var4.pushPose();
         var4.mulPose(Axis.YP.rotationDegrees(180.0F - RutFightClient.bodyYaw(var1, var3, Mth.rotLerp(var3, var1.yBodyRotO, var1.yBodyRot))));
         SignWorkClient.slide(var1, var4, var14);
         var4.scale(var7.frameWidth(), var7.frameHeight(), var7.frameLength());
         RutFightClient.slide(var1, var4, var9 ? null : var8, var7.frameHeight());
         float var17 = var7.coatWarmth();
         float var18 = var7.coatShade();
         float var19 = Math.min(1.0F, var18 * (1.0F + var17 * 0.06F));
         float var20 = Math.min(1.0F, var18);
         float var21 = Math.min(1.0F, var18 * (1.0F - var17 * 0.08F));
         if (var11) {
            float[] var22 = legendCoat(var7.species());
            var19 *= var22[0];
            var20 *= var22[1];
            var21 *= var22[2];
            legendGlint(var1);
         }

         float var28 = var1.downed() ? var1.skinningProgress(var3) : 0.0F;
         ResourceLocation var23 = BlockyCoats.coat(var7);
         if (var27 != null) {
            ResourceLocation var24 = StylizedAnimal.coat(var7);
            int var25 = var28 > 0.0F ? (int)(Math.min(1.0F, var28) * (float)var27.triangles) : 0;
            if (var25 > 0) {
               FilteredFieldTexture.ensure(CarcassSurface.MUSCLE);
               var27.draw(var4.last(), var5.getBuffer(HuntRenderTypes.sculpt(CarcassSurface.MUSCLE)), var6, var15, var13, 0.74F, 0.3F, 0.28F, 0, var25);
            }

            var27.draw(var4.last(), var5.getBuffer(HuntRenderTypes.sculpt(var24)), var6, var15, var13, var19, var20, var21, var25, var27.triangles);
            if (var7.buck()) {
               stylizedAntlers(var4, var5, var6, var7, var14, var13);
            }
         } else if (var28 > 0.0F) {
            FilteredFieldTexture.ensure(CarcassSurface.MUSCLE);
            CubeAnimal.draw(
               var4.last(),
               var5.getBuffer(HuntRenderTypes.sculpt(CarcassSurface.MUSCLE)),
               var6,
               var15,
               var10.bones,
               var10.data,
               var10.stride,
               var13,
               0.74F,
               0.3F,
               0.28F,
               1.0F,
               ClassicAntlers.antlerBits(var1.species())
            );
            long var29 = var10.hideMask(var28) | ClassicAntlers.antlerBits(var1.species());
            CubeAnimal.draw(
               var4.last(),
               var5.getBuffer(HuntRenderTypes.sculpt(var23)),
               var6,
               var15,
               var10.bones,
               var10.data,
               var10.stride,
               var13,
               var19,
               var20,
               var21,
               1.0F,
               var29
            );
         } else {
            CubeAnimal.draw(
               var4.last(),
               var5.getBuffer(HuntRenderTypes.sculpt(var23)),
               var6,
               var15,
               var10.bones,
               var10.data,
               var10.stride,
               var13,
               var19,
               var20,
               var21,
               1.0F,
               ClassicAntlers.antlerBits(var1.species())
            );
         }

         if (var9) {
            boxRack(var4, var5, var6, var7, var13, var14);
         } else {
            ClassicAntlers.draw(var4.last(), var5.getBuffer(HuntRenderTypes.sculpt(var23)), var6, var15, var7, var13, 1.0F, 1.0F, 1.0F);
         }

         if (!HuntShaderCompat.shadowPass()) {
            CompoundTag var30 = var1.impactMarks();
            if (ImpactMarks.skeletal(var30)) {
               ImpactMarkRenderer.draw(var30, var4, var5, var6, var13);
            }
         }

         ClassicAntlers.force = var12;
         var4.popPose();
         super.render(var1, var2, var3, var4, var5, var6);
      }
   }

   private void renderRealistic(Whitetail var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      var6 = FieldEntityLight.apply(var1, var6);
      DeerTraits var7 = var1.traits();
      WhitetailRenderer.State var8 = STATES.computeIfAbsent(var1, WhitetailRenderer.State::new);
      DeerAnimator var9 = pose(var1, var3);
      boolean var10 = HuntShaderCompat.shadowPass();
      double var11 = Math.sqrt(this.entityRenderDispatcher.distanceToSqr(var1))
         * ViewProjection.scale()
         / (double)Math.max(0.5F, var7.frameHeight() * DeerMeshData.of(var1.species()).heightScale);
      int var13 = FrontierGraphics.animalDetail();

      double var14 = switch (var13) {
         case 0 -> 5.0;
         case 1 -> 8.0;
         case 2 -> 13.0;
         default -> 32.0;
      };

      double var16 = switch (var13) {
         case 0 -> 13.0;
         case 1 -> 21.0;
         case 2 -> 32.0;
         default -> 72.0;
      };

      double var18 = switch (var13) {
         case 0 -> 32.0;
         case 1 -> 48.0;
         case 2 -> 72.0;
         default -> 110.0;
      };
      DeerMeshData var20 = DeerMeshData.of(var1.species());
      if (var20.lods[0].length > 60000 && var13 < 3) {
         var14 *= 0.75;
      }

      double var21 = var8.lod <= 0 ? 1.1 : 0.92;
      int var23;
      if (var11 < var14 * var21) {
         var23 = 0;
      } else if (var11 < var16 * (var8.lod <= 1 ? 1.1 : 0.92)) {
         var23 = 1;
      } else if (var11 < var18 * (var8.lod <= 2 ? 1.1 : 0.92)) {
         var23 = 2;
      } else {
         var23 = 3;
      }

      if (!var10) {
         float var24 = (float)(1.0 / Math.max(0.01, var11));
         if (scoreCount < SCORES.length) {
            SCORES[scoreCount++] = var24;
         }

         if (var13 < 3) {
            if (var23 == 0 && var24 < lod0Cut) {
               var23 = 1;
            }

            if (var23 == 1 && var24 < lod1Cut) {
               var23 = 2;
            }

            if (var23 == 2 && var24 < lod2Cut) {
               var23 = 3;
            }
         }
      }

      if (var10) {
         var23 = var11 < 12.0 ? 2 : 3;
      }

      boolean var30 = var1.downed() && var1.skinningProgress(var3) > 0.0F;
      if (var30) {
         var23 = 0;
      }

      if (!var10) {
         var8.lod = var23;
      }

      var4.pushPose();
      var4.mulPose(Axis.YP.rotationDegrees(180.0F - RutFightClient.bodyYaw(var1, var3, Mth.rotLerp(var3, var1.yBodyRotO, var1.yBodyRot))));
      SignWorkClient.slide(var1, var4, var9.model[DeerSkeleton.of(var1.species()).head]);
      var4.scale(var7.frameWidth(), var7.frameHeight(), var7.frameLength());
      realScale(var4, var1);
      RutFightClient.slide(var1, var4, var9, var7.frameHeight() * (var1.species() == GameSpecies.WHITETAIL ? 0.8297214F : 1.0F));
      var8.notePose(var9.skin);
      var8.lastUsed = frame;
      var8.trim(frame);
      float[][] var25;
      if (var30) {
         var25 = DeerDraw.skin(var9, var7, DeerDraw.carcassLod(var20));
      } else {
         int var26 = var23 == 0 ? 1 : 2;
         boolean var27 = var8.cachedPose[var23] == var8.poseId;
         if (var8.cached[var23] == null || !var27 && frame - var8.cachedFrame[var23] >= (long)var26) {
            var8.cached[var23] = DeerDraw.skinCompact(var9, var7, var23, var8.cached[var23]);
            var8.cachedFrame[var23] = frame;
            var8.cachedPose[var23] = var8.poseId;
         }

         var25 = var8.cached[var23];
      }

      ResourceLocation var31 = RealisticCoats.coat(var7);
      float var32 = var1.downed() ? var1.skinningProgress(var3) : 0.0F;
      if (var32 > 0.0F) {
         FilteredFieldTexture.ensure(CarcassSurface.MUSCLE);
         VertexRecorder var28 = VertexRecorder.shared();
         var28.clear();
         DeerDraw.carcass(var4, var5.getBuffer(HuntRenderTypes.sculpt(var31)), var28, var6, var7, var25, var32);
         var28.replay(var5.getBuffer(HuntRenderTypes.sculpt(CarcassSurface.MUSCLE)));
      } else {
         boolean var33 = var20.lodMode[var23] == 1;
         DeerDraw.bodyFast(var4, var5.getBuffer(HuntRenderTypes.sculpt(var33 ? DeerDraw.MATERIAL : var31)), var6, var7, var23, var25, null);
      }

      if (var7.buck()) {
         int var34 = var23 == 0 ? 8 : (var23 == 1 ? 6 : (var23 == 2 ? 5 : 4));
         if (var13 >= 3) {
            var34 = Math.max(var34, 7);
         }

         if (!SculptRack.draw(var4, var5, null, var6, var7, var9, var23, null)) {
            RackDraw.draw(
               var4, var5.getBuffer(HuntRenderTypes.sculpt(RealisticCoats.antler())), var6, var7, var9.model[DeerSkeleton.of(var1.species()).head], var34, null
            );
         }
      }

      if (!var10) {
         CompoundTag var35 = var1.impactMarks();
         if (ImpactMarks.skeletal(var35)) {
            ImpactMarkRenderer.draw(var35, var4, var5, var6, var9.skin);
         }
      }

      var4.popPose();
      super.render(var1, var2, var3, var4, var5, var6);
   }

   static final class State {
      final DeerAnimator animator;
      final DeerAnimator.Input input = new DeerAnimator.Input();
      McAnimalPose classic;
      float lastTime = Float.NaN;
      float airTime;
      int lod = 1;
      final float[][][] cached = new float[4][][];
      final long[] cachedFrame = new long[]{0L, 0L, 0L, 0L};
      final long[] cachedPose = new long[]{-1L, -1L, -1L, -1L};
      final float[] bones = new float[768];
      boolean bonesValid;
      long poseId;
      long lastUsed;

      State(Whitetail var1) {
         this.animator = new DeerAnimator(var1.species());
      }

      void notePose(Matrix4f[] var1) {
         int var2 = Math.min(var1.length, 64);
         boolean var3 = this.bonesValid;
         if (var3) {
            for (int var4 = 0; var4 < var2 && var3; var4++) {
               Matrix4f var5 = var1[var4];
               int var6 = var4 * 12;
               var3 = this.bones[var6] == var5.m00()
                  && this.bones[var6 + 1] == var5.m01()
                  && this.bones[var6 + 2] == var5.m02()
                  && this.bones[var6 + 3] == var5.m10()
                  && this.bones[var6 + 4] == var5.m11()
                  && this.bones[var6 + 5] == var5.m12()
                  && this.bones[var6 + 6] == var5.m20()
                  && this.bones[var6 + 7] == var5.m21()
                  && this.bones[var6 + 8] == var5.m22()
                  && this.bones[var6 + 9] == var5.m30()
                  && this.bones[var6 + 10] == var5.m31()
                  && this.bones[var6 + 11] == var5.m32();
            }
         }

         if (!var3) {
            for (int var7 = 0; var7 < var2; var7++) {
               Matrix4f var8 = var1[var7];
               int var9 = var7 * 12;
               this.bones[var9] = var8.m00();
               this.bones[var9 + 1] = var8.m01();
               this.bones[var9 + 2] = var8.m02();
               this.bones[var9 + 3] = var8.m10();
               this.bones[var9 + 4] = var8.m11();
               this.bones[var9 + 5] = var8.m12();
               this.bones[var9 + 6] = var8.m20();
               this.bones[var9 + 7] = var8.m21();
               this.bones[var9 + 8] = var8.m22();
               this.bones[var9 + 9] = var8.m30();
               this.bones[var9 + 10] = var8.m31();
               this.bones[var9 + 11] = var8.m32();
            }

            this.bonesValid = true;
            this.poseId++;
         }
      }

      void trim(long var1) {
         for (int var3 = 0; var3 < 2; var3++) {
            if (this.cached[var3] != null && var1 - this.cachedFrame[var3] > 400L) {
               this.cached[var3] = null;
               this.cachedPose[var3] = -1L;
            }
         }
      }
   }
}
