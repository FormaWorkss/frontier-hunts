package com.formaworks.frontierhunts.hunting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import org.joml.Vector3f;

public final class DeerGrounding {
   private final Vector3f point = new Vector3f();

   public float sample(Whitetail var1, WhitetailRig var2, int var3) {
      if (var1.onGround() && !var1.isInWaterOrBubble() && !var1.downed()) {
         int var4 = 9 + var3 * 4;
         float[] var5 = WhitetailBindPose.POINTS[var4];
         var2.bones[var4].transformPosition(this.point.set(var5));
         DeerTraits var6 = var1.traits();
         double var7 = Math.toRadians((double)(180.0F - var1.yBodyRot));
         double var9 = (double)(this.point.x * var6.widthScale());
         double var11 = (double)(this.point.z * var6.lengthScale());
         double var13 = var1.getX() + var9 * Math.cos(var7) + var11 * Math.sin(var7);
         double var15 = var1.getZ() - var9 * Math.sin(var7) + var11 * Math.cos(var7);
         Vec3 var17 = new Vec3(var13, var1.getY() + 0.62, var15);
         Vec3 var18 = new Vec3(var13, var1.getY() - 0.72, var15);
         if (!var1.level().hasChunkAt(BlockPos.containing(var17))) {
            return 0.0F;
         } else {
            BlockHitResult var19 = var1.level().clip(new ClipContext(var17, var18, Block.COLLIDER, Fluid.NONE, var1));
            if (var19.getType() != Type.BLOCK || var19.getDirection() != Direction.UP) {
               return 0.0F;
            } else if (!var1.level().getFluidState(var19.getBlockPos()).isEmpty()) {
               return 0.0F;
            } else {
               double var20 = (var19.getLocation().y - var1.getY()) / (double)var6.heightScale();
               return (float)Math.round(Math.clamp(var20, -0.7, 0.6) * 200.0) / 200.0F;
            }
         }
      } else {
         return 0.0F;
      }
   }
}
