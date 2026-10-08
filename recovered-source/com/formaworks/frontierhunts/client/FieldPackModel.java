package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

final class FieldPackModel {
   private static final ResourceLocation TEXTURE = FrontierHunts.id("textures/equipment/packs/material.png");

   static boolean draw(String var0, ItemDisplayContext var1, PoseStack var2, MultiBufferSource var3, int var4) {
      if (!var0.equals("hunter_pack") && !var0.equals("medkit")) {
         return false;
      } else {
         String var5 = var1 == ItemDisplayContext.GROUND
            ? "distant"
            : (!var1.firstPerson() && var1 != ItemDisplayContext.GUI && var1 != ItemDisplayContext.FIXED ? "field" : "close");
         FieldWeaponMesh.drawStatic(var0 + "_" + var5, TEXTURE, var2, var3, var4);
         return true;
      }
   }

   private FieldPackModel() {
   }
}
