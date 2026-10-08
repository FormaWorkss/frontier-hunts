package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ImpactMarks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class ImpactMarkRenderer {
   static void draw(CompoundTag var0, PoseStack var1, MultiBufferSource var2, int var3, Matrix4f[] var4) {
      VertexConsumer var5 = var2.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS));
      VertexConsumer var6 = FieldMaterials.flat(var5);
      int var7 = Math.clamp((long)var0.getInt("count"), 0, 5);

      for (int var8 = 0; var8 < var7; var8++) {
         ImpactMarks.Mark var9 = ImpactMarks.get(var0, var8);
         if (var9 != null) {
            Vec3 var10 = var9.point();
            Vec3 var11 = var9.direction();
            if (var4 != null && var9.bone() >= 0 && var9.bone() < var4.length) {
               Vector3f var12 = new Vector3f((float)var10.x, (float)var10.y, (float)var10.z);
               Vector3f var13 = new Vector3f((float)var11.x, (float)var11.y, (float)var11.z);
               var4[var9.bone()].transformPosition(var12);
               var4[var9.bone()].transformDirection(var13).normalize();
               var10 = new Vec3((double)var12.x, (double)var12.y, (double)var12.z);
               var11 = new Vec3((double)var13.x, (double)var13.y, (double)var13.z);
            }

            Vec3 var27 = var10.subtract(var11.scale(0.006));
            int var28 = var9.exit() ? 6231316 : 5574927;
            int var14 = var9.exit() ? 10958126 : 9970975;
            HuntMesh.ellipsoid(var1, var6, var3, var28, 1, var27.x, var27.y, var27.z, 0.046, 0.04, 0.014);
            HuntMesh.ellipsoid(var1, var6, var3, var14, 1, var27.x - var11.x * 0.005, var27.y - var11.y * 0.005, var27.z - var11.z * 0.005, 0.025, 0.023, 0.009);
            Vec3 var15 = var27.add(0.0, -0.03, 0.0);
            HuntMesh.ellipsoid(var1, var6, var3, 7477012, 1, var15.x, var15.y, var15.z, 0.014, 0.031, 0.008);
            if (var9.arrow()) {
               // [archery2] the arrow itself (head, shaft, fletching of the arrow that was shot), its head buried along the
               // line it flew, exactly where it struck - the same arrow and depth the kill cam shows going in
               arrow(var0, var8, var1, var5, var3, var10, var11, var9.tracer());
            }
         }
      }
   }

   /** Head penetration past the entry point, as in the kill cam (KillCamReplay.PENETRATION). */
   static final double PENETRATION = 0.2;

   private static void arrow(CompoundTag marks, int i, PoseStack pose, VertexConsumer vc, int light, Vec3 entry, Vec3 dir, boolean tracer) {
      ArrowTip tip = marks.contains("tip" + i) ? ArrowTip.byOrdinal(marks.getByte("tip" + i)) : null;
      if (tip == null) {
         tip = tracer ? ArrowTip.TRACER_BROADHEAD : ArrowTip.FIXED_BROADHEAD;
      }
      boolean bolt = marks.getBoolean("bolt" + i);
      boolean primitive = marks.getBoolean("prim" + i);
      double off = FieldArrowRenderer.pointReach(tip, bolt);
      Vec3 c = entry.add(dir.scale(PENETRATION - off));
      pose.pushPose();
      pose.translate(c.x, c.y, c.z);
      FieldArrowRenderer.orient(pose, dir);
      FieldArrowModel.draw(pose, vc, light, false, bolt, new ArrowSupply.Shot(tip, primitive), true);
      pose.popPose();
   }

   private ImpactMarkRenderer() {
   }
}
