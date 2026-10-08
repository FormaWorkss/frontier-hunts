package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** [1.1.0] Public access to the equipment meshes for the boat renderer (hull, oars, motor, propeller, water mask). */
public final class BoatMeshes {
   private BoatMeshes() {
   }

   public static void part(String mesh, int part, PoseStack pose, MultiBufferSource buffers, int light) {
      FieldWeaponMesh.drawPart(mesh, part, pose, buffers, light);
   }

   /** depth-only cover inside the hull, so the water surface does not show through the floor */
   public static void mask(String mesh, PoseStack pose, MultiBufferSource buffers) {
      FieldWeaponMesh.drawPositions(mesh, pose, buffers.getBuffer(RenderType.waterMask()));
   }
}
