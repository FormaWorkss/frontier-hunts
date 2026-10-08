package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;

final class FieldReflexSight {
   static void draw(PoseStack var0, MultiBufferSource var1, int var2) {
      FieldWeaponMesh.part("optic_reflex_sight", var0, var1, var2);
      // [gunsmith] coated window (was a single faint quad): same rectangle, depth and facing
      OpticGlass.window(var0, var1, var2, -0.0172, 0.0172, 0.0088, 0.0422, -0.0218);
   }

   private FieldReflexSight() {
   }
}
