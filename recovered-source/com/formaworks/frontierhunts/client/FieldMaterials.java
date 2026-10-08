package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;

final class FieldMaterials implements VertexConsumer {
   static final ResourceLocation ATLAS = FrontierHunts.id("textures/material/field_materials.png");
   static final int STEEL = 0;
   static final int WALNUT = 1;
   static final int RUBBER = 2;
   static final int SILVER = 3;
   static final int PINE = 4;
   static final int ENDGRAIN = 5;
   static final int CANVAS = 6;
   static final int LEATHER = 7;
   static final int FOX = 8;
   static final int WOLF = 9;
   static final int ELK = 10;
   static final int MOOSE = 11;
   static final int CAT = 12;
   static final int RABBIT = 13;
   static final int FEATHER = 14;
   static final int SCALE = 15;
   private final VertexConsumer out;
   private final float x;
   private final float y;
   private final boolean neutral;
   private boolean flat;

   private FieldMaterials(VertexConsumer var1, int var2, boolean var3) {
      this.out = var1;
      this.x = (float)(var2 % 4) * 0.25F + 0.008F;
      this.y = (float)(var2 / 4) * 0.25F + 0.008F;
      this.neutral = var3;
   }

   static VertexConsumer flat(VertexConsumer var0) {
      FieldMaterials var1 = new FieldMaterials(var0, 3, false);
      var1.flat = true;
      return var1;
   }

   static VertexConsumer tile(VertexConsumer var0, int var1) {
      return new FieldMaterials(var0, var1, false);
   }

   static VertexConsumer neutral(VertexConsumer var0, int var1) {
      return new FieldMaterials(var0, var1, true);
   }

   public VertexConsumer addVertex(float var1, float var2, float var3) {
      this.out.addVertex(var1, var2, var3);
      return this;
   }

   public VertexConsumer setColor(int var1, int var2, int var3, int var4) {
      this.out.setColor(this.neutral ? 255 : var1, this.neutral ? 255 : var2, this.neutral ? 255 : var3, var4);
      return this;
   }

   public VertexConsumer setUv(float var1, float var2) {
      this.out.setUv(this.x + (this.flat ? 0.5F : Math.clamp(var1, 0.0F, 1.0F)) * 0.234F, this.y + (this.flat ? 0.5F : Math.clamp(var2, 0.0F, 1.0F)) * 0.234F);
      return this;
   }

   public VertexConsumer setUv1(int var1, int var2) {
      this.out.setUv1(var1, var2);
      return this;
   }

   public VertexConsumer setUv2(int var1, int var2) {
      this.out.setUv2(var1, var2);
      return this;
   }

   public VertexConsumer setNormal(float var1, float var2, float var3) {
      this.out.setNormal(var1, var2, var3);
      return this;
   }
}
