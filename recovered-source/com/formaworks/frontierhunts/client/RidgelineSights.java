package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;

/**
 * [rifle] Swappable sights on the Ridgeline bolt rifle (model space of {@link CamoRifleModel}; muzzle towards -z).
 *
 * <ul>
 *   <li>Factory scope: part of the supplied rifle mesh, drawn by {@link CamoRifleModel} (unchanged).</li>
 *   <li>Any other optic: the field optic meshes (same parts, rings and glass as on the field guns, scaled to the
 *       slimmer rifle) with their rings clamped on the rifle's two Weaver bases; red dots / prism on the rear base.</li>
 *   <li>No optic: flip-up iron sights - a hooded-ramp front post with a brass bead near the muzzle and a U-notch
 *       leaf on the barrel ahead of the receiver. They fold away (are not drawn) while an optic is fitted.</li>
 * </ul>
 * {@link #axisY} / {@link #eyeZ} give the sight line and the eye position the first-person aim pose aligns to.
 */
final class RidgelineSights {
   /** Top of the supplied Weaver bases (front base z -0.04..0.01, rear base 0.09..0.14). Rings clamp straight on. */
   private static final double BASE_TOP = 0.1245;
   private static final double REAR_BASE_Z = 0.115;
   /**
    * The field optics are modelled for the chunkier field guns; on the slim Ridgeline they are drawn at this scale so
    * tube and bells match the factory scope's proportions (tube ~0.015 vs 0.0135).
    */
   private static final double SCOPE_SCALE = 0.78;
   /**
    * [gunsmith] The rebuilt field scopes have a true 30 mm tube (25.4 mm on the 4-12x), so on the Ridgeline they keep the
    * 0.78 length scale (rings on the bases, same eye relief) but only shrink radially to 0.9: a 27 mm tube like the
    * factory scope instead of a pencil-thin 23 mm one. Sight line heights below use the radial scale.
    */
   private static final double SCOPE_SR = 0.9;
   private static final double TACTICAL_SCALE = 0.86;
   /** Field scope meshes: ring feet at local y 0.096, axis at 0.146, rings at z -0.098 / +0.081, ocular end 0.181. */
   private static final double SCOPE_Y = BASE_TOP - 0.096 * SCOPE_SR + 5.0E-4;
   private static final double SCOPE_Z = REAR_BASE_Z - 0.081 * SCOPE_SCALE;
   /** The 4-12x Field Optic mesh: feet at 0.1, axis at 0.14, rings at -0.105 / +0.073, ocular glass at 0.184. */
   private static final double FIXED_Y = BASE_TOP - 0.1 * SCOPE_SR + 5.0E-4;
   private static final double FIXED_Z = REAR_BASE_Z - 0.073 * SCOPE_SCALE;
   /** Red dots / prism sit on the rear base: feet at local -0.0035. */
   private static final double TACTICAL_Y = BASE_TOP + 0.0035 * TACTICAL_SCALE + 5.0E-4;
   private static final double TACTICAL_Z = REAR_BASE_Z;
   /** Iron sight line: top of the front post = shoulders of the rear notch. */
   static final double IRON_Y = 0.134;
   private static final double FRONT_Z = -0.616;
   private static final double REAR_Z = -0.165;

   private static final int STEEL = 0x1D2124;
   private static final int STEEL_EDGE = 0x2C3236;
   private static final int BRASS = 0xD2AE55;

   private RidgelineSights() {
   }

   static String sight(ItemStack rifle) {
      return RidgelineOptics.sight(rifle);
   }

   /** Model-space height of the sight line. The factory scope keeps the rifle's original 0.151 aim height. */
   static double axisY(String sight) {
      return switch (sight) {
         case RidgelineOptics.STOCK -> CamoRifleModel.OPTIC_Y;
         case RidgelineOptics.IRONS -> IRON_Y;
         case "four_power_optic" -> FIXED_Y + 0.14 * SCOPE_SR;
         case "reflex_sight" -> TACTICAL_Y + 0.027 * TACTICAL_SCALE;
         case "two_power_prism" -> TACTICAL_Y + 0.044 * TACTICAL_SCALE;
         case "micro_red_dot" -> TACTICAL_Y + 0.036 * TACTICAL_SCALE;
         case "holographic_sight" -> TACTICAL_Y + 0.035 * TACTICAL_SCALE;
         default -> SCOPE_Y + 0.146 * SCOPE_SR;
      };
   }

   /** Model-space z of the shooter's eye at full aim (eye relief behind the ocular / window). */
   static double eyeZ(String sight) {
      return switch (sight) {
         case RidgelineOptics.STOCK -> 0.42 / 1.35;
         case RidgelineOptics.IRONS -> 0.27;
         case "two_power_prism" -> TACTICAL_Z + 0.138 * TACTICAL_SCALE;
         case "reflex_sight", "micro_red_dot", "holographic_sight" -> TACTICAL_Z + 0.16;
         case "four_power_optic" -> FIXED_Z + 0.184 * SCOPE_SCALE + 0.086;
         default -> SCOPE_Z + 0.181 * SCOPE_SCALE + 0.086; // same eye relief as the factory scope
      };
   }

   /** Optics whose full-aim view is a drawn eyepiece overlay (the gun is not drawn then). */
   static boolean overlay(String sight) {
      return !sight.isEmpty() && !AttachmentSpec.unmagnified(sight);
   }

   /** Draws the fitted optic (rail + optic) or the iron sights. The factory scope is drawn by the rifle mesh. */
   static void draw(PoseStack pose, MultiBufferSource buffers, int light, String sight) {
      if (sight.equals(RidgelineOptics.STOCK)) {
         return;
      }
      if (sight.isEmpty()) {
         irons(pose, buffers, light);
         return;
      }
      String lod = FieldWeaponMesh.lod;
      FieldWeaponMesh.lod = HuntConfig.QUALITY.get() == HuntConfig.Quality.PERFORMANCE || HuntShaderCompat.shadowPass() ? "field" : "close";
      pose.pushPose();
      try {
         if (FieldWeaponSockets.tactical(sight)) {
            pose.translate(0.0, TACTICAL_Y, TACTICAL_Z);
            pose.scale((float)TACTICAL_SCALE, (float)TACTICAL_SCALE, (float)TACTICAL_SCALE);
            FieldTacticalSight.draw(sight, pose, buffers, light);
         } else if (sight.equals("four_power_optic")) {
            pose.translate(0.0, FIXED_Y, FIXED_Z);
            pose.scale((float)SCOPE_SR, (float)SCOPE_SR, (float)SCOPE_SCALE); // [gunsmith] radial 0.9, length 0.78
            FieldMountedOptic.draw(pose, buffers, light, sight);
         } else {
            pose.translate(0.0, SCOPE_Y, SCOPE_Z);
            pose.scale((float)SCOPE_SR, (float)SCOPE_SR, (float)SCOPE_SCALE); // [gunsmith] radial 0.9, length 0.78
            FieldMountedOptic.draw(pose, buffers, light, sight);
         }
      } finally {
         pose.popPose();
         FieldWeaponMesh.lod = lod;
      }
   }

   /** Hooded-ramp front post with a brass bead, U-notch rear leaf on a barrel band. */
   private static void irons(PoseStack pose, MultiBufferSource buffers, int light) {
      VertexConsumer atlas = buffers.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer steel = FieldMaterials.tile(atlas, FieldMaterials.STEEL);
      VertexConsumer flat = FieldMaterials.flat(atlas);
      // front: barrel band, ramp, post, protective ears, bead
      HuntMesh.tube(pose, steel, light, STEEL, 0.0, 0.1012, FRONT_Z - 0.013, 0.0, 0.1012, FRONT_Z + 0.014, 0.0091, 0.0091, 20);
      ArtMesh.profile(pose, steel, light, STEEL, 0.0042, 6.0E-4, new double[][]{
         {0.1065, FRONT_Z - 0.016}, {0.1065, FRONT_Z + 0.017}, {0.1112, FRONT_Z + 0.017}, {0.1238, FRONT_Z + 0.002}, {0.1238, FRONT_Z - 0.016}
      });
      ArtMesh.box(pose, flat, light, STEEL, 0.0, 0.1288, FRONT_Z - 0.004, 0.003, 0.0104, 0.0026);
      ArtMesh.box(pose, flat, light, BRASS, 0.0, IRON_Y - 0.0013, FRONT_Z - 0.0042, 0.0036, 0.0028, 0.0019);
      for (int side : new int[]{-1, 1}) {
         pose.pushPose();
         pose.translate(side * 0.0056, 0.0, 0.0);
         ArtMesh.box(pose, flat, light, STEEL_EDGE, 0.0, 0.1282, FRONT_Z - 0.006, 0.0011, 0.0115, 0.0085);
         pose.popPose();
      }
      // rear: band, base block, U-notch leaf (shoulders at the sight line, notch 0.005 deep)
      HuntMesh.tube(pose, steel, light, STEEL, 0.0, 0.1012, REAR_Z - 0.012, 0.0, 0.1012, REAR_Z + 0.012, 0.0108, 0.0108, 20);
      ArtMesh.profile(pose, steel, light, STEEL, 0.0062, 6.0E-4, new double[][]{
         {0.1075, REAR_Z - 0.014}, {0.1075, REAR_Z + 0.014}, {0.1168, REAR_Z + 0.011}, {0.1182, REAR_Z - 0.004}, {0.1168, REAR_Z - 0.014}
      });
      double bottom = 0.1175;
      for (int side : new int[]{-1, 1}) {
         pose.pushPose();
         pose.translate(side * 0.00515, 0.0, 0.0);
         ArtMesh.box(pose, flat, light, STEEL, 0.0, (bottom + IRON_Y) / 2.0, REAR_Z, 0.0055, IRON_Y - bottom, 0.0022);
         pose.popPose();
      }
      ArtMesh.box(pose, flat, light, STEEL, 0.0, (bottom + IRON_Y - 0.005) / 2.0, REAR_Z, 0.0049, IRON_Y - 0.005 - bottom, 0.0022);
   }
}
