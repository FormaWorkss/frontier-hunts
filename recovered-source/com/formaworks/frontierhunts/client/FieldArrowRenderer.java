package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Field Recurve arrows in the world.
 *
 * <p>[archery2] Oriented along the arrow's flight: the arrow model points its head down -z, and the old transform
 * ({@code YP(180 - yRot)}, {@code XP(-xRot)}) mirrored the heading east-west and the pitch, so arrows flew tail-first
 * east or west and SIDEWAYS on every diagonal (and in the kill cam, whose arrow uses this renderer). In flight the
 * heading now comes from the velocity itself (yRot lags the velocity by design: Projectile.updateRotation eases 20 %
 * per tick); stuck arrows keep the heading they hit with, through {@link #orient}.
 */
public final class FieldArrowRenderer extends EntityRenderer<FieldArrow> {
   public FieldArrowRenderer(Context var1) {
      super(var1);
   }

   public ResourceLocation getTextureLocation(FieldArrow var1) {
      return FieldMaterials.ATLAS;
   }

   public boolean shouldRender(FieldArrow var1, Frustum var2, double var3, double var5, double var7) {
      return var1.tracer() || super.shouldRender(var1, var2, var3, var5, var7);
   }

   /** Unit flight direction to draw: velocity while flying, else the projectile rotation (vanilla convention). */
   static Vec3 heading(FieldArrow a, float pt) {
      Vec3 v = a.getDeltaMovement();
      if (!a.stuck() && v.lengthSqr() > 1.0E-6) {
         return v.normalize();
      }
      return fromRotation(Mth.rotLerp(pt, a.yRotO, a.getYRot()), Mth.lerp(pt, a.xRotO, a.getXRot()));
   }

   /** Flight direction of a projectile rotation: yRot = atan2(vx, vz), xRot = atan2(vy, horizontal), degrees, up positive. */
   static Vec3 fromRotation(double yRotDeg, double xRotDeg) {
      double yaw = Math.toRadians(yRotDeg);
      double pitch = Math.toRadians(xRotDeg);
      return new Vec3(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
   }

   /**
    * [archery2] Distance from the arrow model's centre to the point of its head (FieldArrowModel geometry: shaft half
    * length 0.38, bolt 0.26, plus the head's reach), so the kill cam and impact marks put the point exactly at the hit.
    */
   static double pointReach(ArrowTip tip, boolean bolt) {
      double head = switch (tip == null ? ArrowTip.FIXED_BROADHEAD.shape() : tip.shape()) {
         case FIELD_POINT -> 0.034;
         case MECHANICAL_BROADHEAD -> 0.066;
         case CUT_ON_CONTACT -> 0.092;
         case JUDO_POINT -> 0.0346;
         case FLINT_POINT -> 0.058;
         case OBSIDIAN_POINT -> 0.066;
         case BONE_POINT -> 0.058;
         default -> 0.074;
      };
      return (bolt ? 0.26 : 0.38) + head;
   }

   /** Turn the arrow model (head toward -z) to point along unit direction {@code d}. */
   static void orient(PoseStack pose, Vec3 d) {
      pose.mulPose(Axis.YP.rotation((float)Math.atan2(-d.x, -d.z)));
      pose.mulPose(Axis.XP.rotation((float)Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
   }

   public void render(FieldArrow var1, float var2, float var3, PoseStack var4, MultiBufferSource var5, int var6) {
      Vec3 dir = heading(var1, var3);
      boolean bolt = KillCamClient.boltDouble(var1);
      var4.pushPose();
      orient(var4, dir);
      ArrowTip var9 = var1.tip();
      FieldArrowModel.draw(
         var4,
         var5.getBuffer(RenderType.entityCutoutNoCull(this.getTextureLocation(var1))),
         var6,
         false,
         bolt,
         new ArrowSupply.Shot(var9, var1.primitive()),
         var1.stuck()
      );
      var4.popPose();
      if (var9.tracer()) {
         Vec3 var15 = var1.getPosition(var3);
         boolean var16 = var1.stuck();
         double half = bolt ? 0.26 : 0.38;
         ArrowTrails.track(var1.getId(), var9.glow, var16 ? var15.subtract(dir.scale(half)) : var15.add(dir.scale(half - 0.02)), dir, var16, var3);
      }

      super.render(var1, var2, var3, var4, var5, var6);
   }
}
