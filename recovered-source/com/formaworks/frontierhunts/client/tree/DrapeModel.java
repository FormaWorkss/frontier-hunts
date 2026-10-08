package com.formaworks.frontierhunts.client.tree;

import com.formaworks.frontierhunts.client.RealisticWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.common.util.TriState;

public final class DrapeModel implements IDynamicBakedModel {
   static final ModelProperty<Integer> AXIS = new ModelProperty();
   private static final float R = 0.475F;
   private final BakedModel base;

   public DrapeModel(BakedModel var1) {
      this.base = var1;
   }

   public ModelData getModelData(BlockAndTintGetter var1, BlockPos var2, BlockState var3, ModelData var4) {
      var4 = this.base.getModelData(var1, var2, var3, var4);
      if (!RealisticWorld.round) {
         return var4;
      } else {
         BlockState var5 = var1.getBlockState(var2.below());
         if (RealisticWorld.isRound(var5.getBlock()) && var5.hasProperty(BlockStateProperties.AXIS)) {
            Axis var6 = (Axis)var5.getValue(BlockStateProperties.AXIS);
            return var6 == Axis.Y ? var4 : var4.derive().with(AXIS, var6 == Axis.X ? 0 : 2).build();
         } else {
            return var4;
         }
      }
   }

   public List<BakedQuad> getQuads(BlockState var1, Direction var2, RandomSource var3, ModelData var4, RenderType var5) {
      Integer var6 = (Integer)var4.get(AXIS);
      if (var6 == null) {
         return this.base.getQuads(var1, var2, var3, var4, var5);
      } else if (var2 != null) {
         return List.of();
      } else {
         List var7 = this.base.getQuads(var1, Direction.UP, var3, var4, var5);
         if (var7.isEmpty()) {
            for (BakedQuad var9 : this.base.getQuads(var1, null, var3, var4, var5)) {
               if (var9.getDirection() == Direction.UP) {
                  var7 = List.of(var9);
                  break;
               }
            }
         }

         if (var7.isEmpty()) {
            return List.of();
         } else {
            BakedQuad var34 = (BakedQuad)var7.get(0);
            TextureAtlasSprite var35 = var34.getSprite();
            ArrayList var10 = new ArrayList();
            byte var11 = 6;
            double var12 = Math.toRadians(-72.0);
            double var14 = Math.toRadians(72.0);

            for (int var16 = 0; var16 < var11; var16++) {
               double var17 = var12 + (var14 - var12) * (double)var16 / (double)var11;
               double var19 = var12 + (var14 - var12) * (double)(var16 + 1) / (double)var11;
               float var21 = 0.5F + 0.475F * (float)Math.sin(var17);
               float var22 = -0.5F + 0.475F * (float)Math.cos(var17);
               float var23 = 0.5F + 0.475F * (float)Math.sin(var19);
               float var24 = -0.5F + 0.475F * (float)Math.cos(var19);
               float var25 = (float)var16 / (float)var11;
               float var26 = (float)(var16 + 1) / (float)var11;
               float[][] var27 = var6 == 0
                  ? new float[][]{{0.0F, var22, var21}, {0.0F, var24, var23}, {1.0F, var24, var23}, {1.0F, var22, var21}}
                  : new float[][]{{var21, var22, 0.0F}, {var21, var22, 1.0F}, {var23, var24, 1.0F}, {var23, var24, 0.0F}};
               float[][] var28 = var6 == 0
                  ? new float[][]{{0.0F, var25}, {0.0F, var26}, {1.0F, var26}, {1.0F, var25}}
                  : new float[][]{{var25, 0.0F}, {var25, 1.0F}, {var26, 1.0F}, {var26, 0.0F}};
               float[][] var29 = new float[4][];

               for (int var30 = 0; var30 < 4; var30++) {
                  float var31 = (var6 == 0 ? var27[var30][2] : var27[var30][0]) - 0.5F;
                  float var32 = var27[var30][1] + 0.5F;
                  float var33 = (float)Math.sqrt((double)(var31 * var31 + var32 * var32));
                  var29[var30] = var6 == 0 ? new float[]{0.0F, var32 / var33, var31 / var33} : new float[]{var31 / var33, var32 / var33, 0.0F};
               }

               QuadBakingVertexConsumer var36 = new QuadBakingVertexConsumer();
               var36.setSprite(var35);
               var36.setDirection(Direction.UP);
               var36.setShade(true);
               var36.setHasAmbientOcclusion(false);
               var36.setTintIndex(var34.getTintIndex());

               for (int var37 = 0; var37 < 4; var37++) {
                  var36.addVertex(var27[var37][0], var27[var37][1] + 0.01F, var27[var37][2]);
                  var36.setColor(1.0F, 1.0F, 1.0F, 1.0F);
                  var36.setUv(var35.getU(var28[var37][0]), var35.getV(var28[var37][1]));
                  var36.setNormal(var29[var37][0], var29[var37][1], var29[var37][2]);
               }

               var10.add(var36.bakeQuad());
            }

            return var10;
         }
      }
   }

   public TriState useAmbientOcclusion(BlockState var1, ModelData var2, RenderType var3) {
      return var2.get(AXIS) != null ? TriState.FALSE : this.base.useAmbientOcclusion(var1, var2, var3);
   }

   public ChunkRenderTypeSet getRenderTypes(BlockState var1, RandomSource var2, ModelData var3) {
      return this.base.getRenderTypes(var1, var2, var3);
   }

   public boolean useAmbientOcclusion() {
      return this.base.useAmbientOcclusion();
   }

   public boolean isGui3d() {
      return this.base.isGui3d();
   }

   public boolean usesBlockLight() {
      return this.base.usesBlockLight();
   }

   public boolean isCustomRenderer() {
      return this.base.isCustomRenderer();
   }

   public TextureAtlasSprite getParticleIcon() {
      return this.base.getParticleIcon();
   }

   public TextureAtlasSprite getParticleIcon(ModelData var1) {
      return this.base.getParticleIcon(var1);
   }

   public ItemTransforms getTransforms() {
      return this.base.getTransforms();
   }

   public ItemOverrides getOverrides() {
      return this.base.getOverrides();
   }

   public List<RenderType> getRenderTypes(ItemStack var1, boolean var2) {
      return this.base.getRenderTypes(var1, var2);
   }

   public List<BakedModel> getRenderPasses(ItemStack var1, boolean var2) {
      return this.base.getRenderPasses(var1, var2);
   }
}
