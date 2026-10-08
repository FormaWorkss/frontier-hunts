package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.expedition.FieldFlashlight;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

final class FieldFlashlightModel {
   private static final ResourceLocation TEXTURE = FrontierHunts.id("textures/equipment/packs/material.png");

   static void draw(ItemStack var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4) {
      String var5 = var1 == ItemDisplayContext.GROUND
         ? "distant"
         : (!var1.firstPerson() && var1 != ItemDisplayContext.GUI && var1 != ItemDisplayContext.FIXED ? "field" : "close");
      if (FieldFlashlight.enabled(var0)) {
         var4 = LightTexture.pack(Math.max(7, LightTexture.block(var4)), LightTexture.sky(var4));
      }

      FieldWeaponMesh.drawStatic("field_flashlight_" + var5, TEXTURE, var2, var3, var4);
      if (FieldFlashlight.enabled(var0)) {
         FieldWeaponMesh.drawStatic("field_flashlight_emitter", TEXTURE, var2, var3, 15728880);
      }
   }

   private FieldFlashlightModel() {
   }
}
