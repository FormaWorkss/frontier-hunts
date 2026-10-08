package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.DeerAnimator;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import org.joml.Matrix4f;

public final class McAnimalPose {
   static final int BODY = 0;
   static final int HEAD = 1;
   static final int TAIL = 6;
   private final McAnimalPose.Rig rig;
   public final Matrix4f[] skin;
   public final Matrix4f head = new Matrix4f();
   private final Matrix4f[] part = new Matrix4f[7];
   private final Matrix4f work = new Matrix4f();
   private float limbPos;
   private float limbAmt;
   private float bed;
   private float sleep;
   private float graze;
   private float tail;
   private float lastTime = Float.NaN;
   private final com.formaworks.frontierhunts.hunting.GameSpecies species; // [rutfight]
   private float fight; // [rutfight] locked-head posture weight

   public McAnimalPose(GameSpecies var1) {
      this.rig = McAnimalPose.Rig.of(var1);
      this.species = var1; // [rutfight]
      this.skin = new Matrix4f[this.rig.partOfBone.length];

      for (int var2 = 0; var2 < this.skin.length; var2++) {
         this.skin[var2] = new Matrix4f();
      }

      for (int var3 = 0; var3 < this.part.length; var3++) {
         this.part[var3] = new Matrix4f();
      }
   }

   public static McAnimalPose still(GameSpecies var0, DeerAnimator.Input var1, float var2) {
      McAnimalPose var3 = new McAnimalPose(var0);
      var3.update(var1, var2, -1.0F);
      return var3;
   }

   public void update(DeerAnimator.Input var1, float var2, float var3) {
      boolean var4 = var3 < 0.0F || Float.isNaN(this.lastTime);
      float var5 = var3 >= 0.0F ? var3 : (var4 ? 0.0F : Math.max(0.0F, Math.min(0.25F, var1.time - this.lastTime)));
      this.lastTime = var1.time;
      float var6 = var5 * 20.0F;
      float var7 = var1.speed * var2 / 20.0F;
      if (var1.bedded || var1.downed || var1.sedated) {
         var7 = 0.0F;
      }

      float var8 = Math.min(1.0F, var7 * 4.0F);
      if (var4) {
         this.limbAmt = var8;
         this.limbPos = var1.time * 20.0F * var8 * this.rig.cadence;
      } else {
         this.limbAmt = this.limbAmt + (var8 - this.limbAmt) * (1.0F - (float)Math.pow(0.6, (double)var6));
         float var9 = Math.min(2.0F, 1.0F + Math.max(0.0F, var7 - 0.25F) * 2.0F);
         this.limbPos = this.limbPos + this.limbAmt * var6 * var9 * this.rig.cadence;
      }

      boolean var11 = (var1.bedded || var1.sedated) && !var1.downed;
      this.bed = approach(this.bed, var11 ? 1.0F : 0.0F, var11 ? 1.6F : 2.2F, var5, var4);
      this.sleep = approach(this.sleep, (var1.sleeping || var1.sedated) && var11 ? 1.0F : 0.0F, 1.2F, var5, var4);
      this.graze = approach(this.graze, !var11 && !var1.downed ? Math.max(0.0F, Math.min(1.0F, var1.graze)) : 0.0F, 3.0F, var5, var4);
      this.tail = approach(this.tail, Math.max(0.0F, Math.min(1.0F, var1.tailRaise)), var1.tailRaise > this.tail ? 9.0F : 2.5F, var5, var4);
      this.fight = approach(this.fight, var11 || var1.downed ? 0.0F : var1.fightLower, 4.0F, var5, var4); // [rutfight]
      float var10 = var1.downed ? Math.min(1.0F, (float)Math.sqrt((double)(Math.max(0.0F, var1.downAge / 1.8F) * 1.6F))) : 0.0F;
      if (var1.downed && var4) {
         var10 = 1.0F;
      }

      this.build(var1, var10);
   }

   private static float approach(float var0, float var1, float var2, float var3, boolean var4) {
      if (var4) {
         return var1;
      } else {
         float var5 = 1.0F - (float)Math.exp((double)(-var2 * var3 * 2.2F));
         return var0 + (var1 - var0) * var5;
      }
   }

   private void build(DeerAnimator.Input var1, float var2) {
      McAnimalPose.Rig var3 = this.rig;
      float var4 = Math.min(1.4F * this.limbAmt, 0.95F);
      float var5 = this.limbPos * 0.6662F;
      float var6 = this.bed;
      float var7 = 1.0F - var6;
      float var8 = var1.airborne && !var1.downed ? Math.min(1.0F, var1.airTime * 6.0F) : 0.0F;
      float[] var9 = new float[]{
         (float)Math.cos((double)var5) * var4,
         (float)Math.cos((double)var5 + Math.PI) * var4,
         (float)Math.cos((double)var5 + Math.PI) * var4,
         (float)Math.cos((double)var5) * var4
      };

      for (int var10 = 0; var10 < 4; var10++) {
         float[] var11 = var3.pivot[2 + var10];
         boolean var12 = var10 < 2;
         float var13 = var6 * (float)(var12 ? -Math.PI / 2 : Math.PI / 2);
         float var14 = var8 * (var12 ? 0.55F : -0.55F) * var7;
         float var15 = var9[var10] * var7 * (1.0F - var8) + var13 + var14;
         var15 += com.formaworks.frontierhunts.sign.work.SignPose.boxLeg(var1, var10); // [deersign] pawing / rub-urination
         float var16 = var12 ? 0.0F : (var10 % 2 == 0 ? -0.5F : 0.5F) * var6;
         this.part[2 + var10].identity().translate(var11[0] + var16, var11[1], var11[2]).rotateX(var15).translate(-var11[0], -var11[1], -var11[2]);
      }

      float[] var21 = var3.pivot[1];
      // Head look is damped while lying so the head stays settled over the chest.
      float var22 = (1.0F - this.graze) * (1.0F - this.sleep) * (1.0F - var2) * (1.0F - this.bed * 0.45F) * (1.0F - this.fight); // [rutfight]
      float var23 = clamp(var1.headYaw, -1.1F, 1.1F) * var22 + this.sleep * 0.9F;
      float var24 = clamp(var1.headPitch, -0.6F, 0.6F) * var22;
      float var26 = this.graze > 0.8F ? (float)Math.sin((double)(var1.time * 9.0F)) * 0.06F * this.graze : 0.0F;
      var24 -= this.graze * var3.graze + var26 + this.sleep * 0.55F + var2 * 0.25F;
      float var27 = this.graze * var3.grazeDrop + this.sleep * 1.5F;
      // [rutfight] locked antlers: head and neck swing low, forehead and rack to the rival
      var24 -= this.fight * com.formaworks.frontierhunts.hunting.rutfight.FightPose.classicPitch(this.species);
      var27 += this.fight * com.formaworks.frontierhunts.hunting.rutfight.FightPose.classicDrop(this.species);
      var24 -= var1.signPitch; // [deersign] rubbing strokes / licking branch (SignWorkClient)
      var23 += var1.signYaw; // [deersign]
      var27 += var1.signDrop; // [deersign]
      this.part[1]
         .identity()
         .translate(0.0F, -var27, 0.0F)
         .translate(var21[0], var21[1], var21[2])
         .rotateY(var23)
         .rotateX(var24)
         .translate(-var21[0], -var21[1], -var21[2]);
      float[] var28 = var3.pivot[6];
      float var17 = (float)Math.sin((double)(var1.time * 2.3F + (float)(var1.seed & 15))) * 0.08F * (1.0F - this.tail) * (1.0F - var2);
      this.part[6]
         .identity()
         .translate(var28[0], var28[1], var28[2])
         .rotateX(-this.tail * var3.tailFlag)
         .rotateY(var17)
         .translate(-var28[0], -var28[1], -var28[2]);
      this.part[0].identity();
      Matrix4f var18 = this.work.identity();
      if (var2 > 0.0F) {
         float var19 = var1.fallLeft ? -1.0F : 1.0F;
         float var20 = var3.bodyW / 2.0F;
         var18.translate(-var19 * var2 * (var3.bodyCy - var20), 0.0F, 0.0F);
         var18.translate(var19 * var20, 0.0F, 0.0F).rotateZ(-var19 * (float) (Math.PI / 2) * var2).translate(-var19 * var20, 0.0F, 0.0F);
      }

      var18.translate(0.0F, -var6 * (var3.leg - 2.0F), 0.0F);

      for (int var29 = 0; var29 < 7; var29++) {
         Matrix4f var31 = new Matrix4f()
            .scale(1.0F / var3.sx, 1.0F / var3.sy, 1.0F / var3.sz)
            .mul(var18)
            .mul(this.part[var29])
            .scale(var3.sx, var3.sy, var3.sz);
         this.part[var29].set(var31);
      }

      if (var1.fightHead != null && var1.fightReach > 0.001F && var2 <= 0.0F) {
         // [rutfight] the head/neck part goes exactly where the client's contact solve puts the rack
         com.formaworks.frontierhunts.client.rutfight.RutFightClient.classicHead(this.part[1], var3.standHead, var1.fightHead, var1.fightReach);
      }

      for (int var30 = 0; var30 < this.skin.length; var30++) {
         this.part[var3.partOfBone[var30]].mul(var3.standSkin[var30], this.skin[var30]);
      }

      this.part[1].mul(var3.standHead, this.head);
   }

   private static float clamp(float var0, float var1, float var2) {
      return var0 < var1 ? var1 : Math.min(var0, var2);
   }

   static final class Rig {
      private static final McAnimalPose.Rig[] BY = new McAnimalPose.Rig[GameSpecies.values().length];
      final int[] partOfBone;
      final float[][] pivot;
      final Matrix4f[] standSkin;
      final Matrix4f standHead;
      final float leg;
      final float graze;
      final float grazeDrop;
      final float bodyW;
      final float bodyCy;
      final float tailFlag;
      final float cadence;
      final float sx;
      final float sy;
      final float sz;

      private Rig(
         GameSpecies var1,
         int[] var2,
         float[][] var3,
         float[][] var4,
         float[] var5,
         float var6,
         float var7,
         float var8,
         float var9,
         float var10,
         float var11,
         float var12,
         float var13
      ) {
         this.partOfBone = var2;
         this.pivot = var3;
         this.standSkin = new Matrix4f[var4.length];

         for (int var14 = 0; var14 < var4.length; var14++) {
            this.standSkin[var14] = new Matrix4f().set(var4[var14]);
         }

         this.standHead = new Matrix4f().set(var5);
         this.leg = var6;
         this.graze = var7;
         this.grazeDrop = var8;
         this.bodyW = var9;
         this.bodyCy = var10;
         this.tailFlag = var1 == GameSpecies.WHITETAIL ? 2.3F : 1.1F;
         this.cadence = var1 == GameSpecies.MOOSE ? 0.8F : (var1 == GameSpecies.ELK ? 0.9F : 1.0F);
         this.sx = var11 * 16.0F;
         this.sy = var12 * 16.0F;
         this.sz = var13 * 16.0F;
      }

      static McAnimalPose.Rig of(GameSpecies var0) {
         McAnimalPose.Rig var1 = BY[var0.ordinal()];
         if (var1 == null) {
            McAnimalPose.Rig[] var10000 = BY;
            int var10001 = var0.ordinal();
            McAnimalPose.Rig var10002 = switch (var0) {
               case ELK -> new McAnimalPose.Rig(
               var0,
               ElkVanillaCubes.PART_OF_BONE,
               ElkVanillaCubes.PIVOT,
               ElkVanillaCubes.STAND_SKIN,
               ElkVanillaCubes.STAND_HEAD,
               15.0F,
               1.309F,
               6.54F,
               10.0F,
               19.5F,
               1.3F,
               1.36F,
               1.3F
            );
               case MOOSE -> new McAnimalPose.Rig(
               var0,
               MooseVanillaCubes.PART_OF_BONE,
               MooseVanillaCubes.PIVOT,
               MooseVanillaCubes.STAND_SKIN,
               MooseVanillaCubes.STAND_HEAD,
               18.0F,
               1.309F,
               8.08F,
               11.0F,
               23.0F,
               1.4F,
               1.45F,
               1.4F
            );
               default -> new McAnimalPose.Rig(
               var0,
               WhitetailVanillaCubes.PART_OF_BONE,
               WhitetailVanillaCubes.PIVOT,
               WhitetailVanillaCubes.STAND_SKIN,
               WhitetailVanillaCubes.STAND_HEAD,
               12.0F,
               1.309F,
               4.02F,
               8.0F,
               15.5F,
               1.47F,
               1.615F,
               1.47F
            );
            };
            var1 = var10002;
            var10000[var10001] = var10002;
         }

         return var1;
      }
   }
}
