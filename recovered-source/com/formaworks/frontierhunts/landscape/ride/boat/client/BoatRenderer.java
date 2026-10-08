package com.formaworks.frontierhunts.landscape.ride.boat.client;

import com.formaworks.frontierhunts.client.BoatMeshes;
import com.formaworks.frontierhunts.landscape.ride.boat.BoatSim;
import com.formaworks.frontierhunts.landscape.ride.boat.FrontierBoat;
import com.formaworks.frontierhunts.landscape.ride.boat.JonBoat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;

/**
 * [1.1.0] Draws the rowboat and the jon boat from their equipment meshes (see tools/gunsmith/boats.py): the hull
 * floating at the waterline with a slow bob and roll, the oars swinging, dipping and feathering through each stroke,
 * the outboard turning with the tiller, its propeller spinning and the whole motor tilting up in the shallows or on
 * land. A depth-only water mask keeps the water surface out of the hull.
 */
public class BoatRenderer extends EntityRenderer<Boat> {
   // the oarlock pivot (port side; the starboard one is mirrored) and the outboard's swivel / tilt axes, in mesh units
   static final float OAR_X = 0.6268F, OAR_Y = 0.5683F, OAR_Z = 0.3F;
   static final float SWIVEL_Z = -2.11F, TILT_Y = 0.47F, TILT_Z = -2.09F, PROP_Y = -0.42F;

   public BoatRenderer(EntityRendererProvider.Context ctx) {
      super(ctx);
      this.shadowRadius = 0.0F;
   }

   @Override
   public void render(Boat b, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
      if (!(b instanceof FrontierBoat fb)) {
         return;
      }
      BoatSim.State s = fb.sim();
      BoatSim.Spec sp = fb.spec();
      float afloat = Mth.lerp(pt, s.afloatO, s.afloat);
      float t = b.tickCount + pt;
      float bob = afloat * Mth.sin(t * 0.09F + b.getId()) * 0.012F;
      ps.pushPose();
      ps.translate(0.0, BoatSim.WATERLINE * afloat + sp.keel() * (1.0F - afloat) + bob, 0.0);
      ps.mulPose(Axis.YP.rotationDegrees(-yaw));
      float hurt = b.getHurtTime() - pt;
      float damage = Math.max(0.0F, b.getDamage() - pt);
      if (hurt > 0.0F) {
         ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * b.getHurtDir()));
      }
      float bubble = b.getBubbleAngle(pt);
      if (!Mth.equal(bubble, 0.0F)) {
         ps.mulPose(new Quaternionf().setAngleAxis(bubble * (float)(Math.PI / 180.0), 1.0F, 0.0F, 1.0F));
      }
      float pitch = Mth.lerp(pt, s.pitchO, s.pitch);
      float roll = Mth.lerp(pt, s.rollO, s.roll) + afloat * Mth.sin(t * 0.061F + b.getId() * 0.7F) * 0.8F;
      ps.mulPose(Axis.XP.rotationDegrees(-pitch));
      ps.mulPose(Axis.ZP.rotationDegrees(roll));
      String lod = this.entityRenderDispatcher.distanceToSqr(b) < 24.0 * 24.0 ? "close" : "field";
      String mesh = sp.mesh() + "_" + lod;
      BoatMeshes.part(mesh, 0, ps, buf, light);
      if (b instanceof JonBoat) {
         this.motor(s, mesh, pt, ps, buf, light);
      } else {
         this.oar(s, mesh, 0, pt, ps, buf, light);
         this.oar(s, mesh, 1, pt, ps, buf, light);
      }
      if (afloat > 0.5F && !b.isUnderWater()) {
         BoatMeshes.mask(sp.mesh() + "_mask", ps, buf);
      }
      ps.popPose();
      super.render(b, yaw, pt, ps, buf, light);
   }

   private void oar(BoatSim.State s, String mesh, int side, float pt, PoseStack ps, MultiBufferSource buf, int light) {
      float ph = lerpWrap(pt, s.phaseO[side], s.phase[side]);
      float act = Mth.lerp(pt, s.activeO[side], s.active[side]);
      float sweep, dip, feather;
      if (ph < 0.45F) {
         float u = smooth(ph / 0.45F);
         sweep = -40.0F + 75.0F * u;
      } else {
         float u = smooth((ph - 0.45F) / 0.55F);
         sweep = 35.0F - 75.0F * u;
      }
      // blade in the water through the drive, lifted and laid flat (feathered) for the recovery
      float inWater = window(ph, -0.02F, 0.05F, 0.4F, 0.48F) + window(ph, 0.97F, 1.05F, 2.0F, 2.0F);
      dip = 4.0F + 15.0F * inWater;
      feather = 90.0F * window(ph, -0.06F, 0.0F, 0.43F, 0.5F) + 90.0F * window(ph, 0.93F, 1.0F, 2.0F, 2.0F);
      // at rest the oars trail aft along the hull
      sweep = Mth.lerp(act, 72.0F, sweep);
      dip = Mth.lerp(act, 1.5F, dip);
      feather = Mth.lerp(act, 0.0F, feather);
      float sgn = side == 0 ? 1.0F : -1.0F;
      ps.pushPose();
      ps.translate(sgn * OAR_X, OAR_Y, OAR_Z);
      if (side == 1) {
         ps.mulPose(Axis.YP.rotationDegrees(180.0F));
      }
      ps.mulPose(Axis.YP.rotationDegrees(sgn * sweep));
      BoatMeshes.part(mesh, side == 0 ? 5 : 6, ps, buf, light);
      ps.mulPose(Axis.ZP.rotationDegrees(-dip));
      ps.mulPose(Axis.XP.rotationDegrees(feather));
      BoatMeshes.part(mesh, side == 0 ? 1 : 2, ps, buf, light);
      ps.popPose();
   }

   private void motor(BoatSim.State s, String mesh, float pt, PoseStack ps, MultiBufferSource buf, int light) {
      float tiller = Mth.lerp(pt, s.tillerO, s.tiller);
      float tilt = Mth.lerp(pt, s.tiltO, s.tilt);
      float prop = Mth.lerp(pt, s.propO, s.prop);
      ps.pushPose();
      ps.translate(0.0F, TILT_Y, TILT_Z);
      ps.mulPose(Axis.XP.rotationDegrees(65.0F * smooth(tilt)));
      ps.translate(0.0F, -TILT_Y, -TILT_Z);
      ps.translate(0.0F, 0.0F, SWIVEL_Z);
      ps.mulPose(Axis.YP.rotationDegrees(28.0F * tiller));
      ps.translate(0.0F, 0.0F, -SWIVEL_Z);
      BoatMeshes.part(mesh, 3, ps, buf, light);
      ps.translate(0.0F, PROP_Y, 0.0F);
      ps.mulPose(Axis.ZP.rotationDegrees(prop));
      ps.translate(0.0F, -PROP_Y, 0.0F);
      BoatMeshes.part(mesh, 4, ps, buf, light);
      ps.popPose();
   }

   static float lerpWrap(float pt, float a, float b) {
      float d = b - a;
      if (d > 0.5F) d -= 1.0F;
      if (d < -0.5F) d += 1.0F;
      float p = (a + d * pt) % 1.0F;
      return p < 0.0F ? p + 1.0F : p;
   }

   static float smooth(float x) {
      float c = Mth.clamp(x, 0.0F, 1.0F);
      return c * c * (3.0F - 2.0F * c);
   }

   /** 0 before a, rises to 1 by b, holds to c, falls to 0 by d */
   static float window(float x, float a, float b, float c, float d) {
      if (x <= a || x >= d) return 0.0F;
      if (x < b) return smooth((x - a) / (b - a));
      if (x <= c) return 1.0F;
      return 1.0F - smooth((x - c) / (d - c));
   }

   @Override
   public ResourceLocation getTextureLocation(Boat b) {
      return InventoryMenu.BLOCK_ATLAS;
   }
}
