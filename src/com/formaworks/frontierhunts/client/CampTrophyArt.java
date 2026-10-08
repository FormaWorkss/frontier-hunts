package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

/**
 * [camps] Public bridge to the package-private trophy mount art, so the Big-Buck Board renderer draws exactly the same
 * plaque + head + rack as a wall-hung trophy (all graphics presets, cached meshes).
 */
public final class CampTrophyArt {
   private CampTrophyArt() {
   }

   /** Draws a trophy mount centred on the origin, plaque back at z = +0.025, head facing -z. */
   public static void drawMount(ItemStack trophy, PoseStack pose, MultiBufferSource buffers, int light) {
      TrophyDisplay.draw(trophy, pose, buffers, light, false);
   }
}
