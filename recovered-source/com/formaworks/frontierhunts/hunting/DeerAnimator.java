package com.formaworks.frontierhunts.hunting;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DeerAnimator {
   public static final float WALK_STRIDE = 0.95F;
   /** Rise clip speed-up when the animal bolts (flee/charge) straight out of its bed. Whitetail uses the same factor server-side. */
   public static final float RISE_HURRY = 1.7F;
   private final boolean[] restMask;
   private final float[] t3;
   private final float[] q3;
   private float sleepW;
   private final float sleepClipW;
   private final float sleepNeckYaw;
   private final float sleepNeckPitch;
   private final float sleepHeadPitch;
   private final DeerSkeleton sk;
   private final int n;
   private final float[] t;
   private final float[] q;
   private final float[] t2;
   private final float[] q2;
   public final Matrix4f[] model;
   public final Matrix4f[] skin;
   private final boolean[] upperMask;
   private final boolean[] holdMask; // [routines]
   private float last = Float.NaN;
   private float walkPhase;
   private float trotPhase;
   private float runPhase;
   private float moveW;
   private float trotW;
   private float runW;
   private float jumpW;
   private float grazeW;
   private float stanceW;
   private float bedW;
   private float cueW;
   private float downW;
   private float tailW;
   private float earPhase;
   private float aggW;
   private float fightW; // [rutfight] locked-head posture weight
   /** [rutfight] What the neck could not reach in the last contact solve (target - head joint, model space). */
   public final org.joml.Vector3f fightResidual = new org.joml.Vector3f();
   private final GameSpecies species;
   private int gaitState;
   private final Quaternionf rot = new Quaternionf();
   private final Vector3f axis = new Vector3f();
   private final Matrix4f tmp = new Matrix4f();

   public DeerAnimator() {
      this(GameSpecies.WHITETAIL);
   }

   public DeerAnimator(GameSpecies var1) {
      this.species = var1;
      this.sk = DeerSkeleton.of(var1);
      this.n = this.sk.count();
      this.t = new float[this.n * 3];
      this.q = new float[this.n * 4];
      this.t2 = new float[this.n * 3];
      this.q2 = new float[this.n * 4];
      this.model = new Matrix4f[this.n];
      this.skin = new Matrix4f[this.n];
      this.upperMask = new boolean[this.n];

      for (int var2 = 0; var2 < this.n; var2++) {
         this.model[var2] = new Matrix4f();
         this.skin[var2] = new Matrix4f();
      }

      for (int var5 : new int[]{this.sk.neck0, this.sk.neck1, this.sk.head, this.sk.earL, this.sk.earR}) {
         this.upperMask[var5] = true;
      }

      this.holdMask = new boolean[this.n]; // [routines] upper body for held rut cues while walking

      for (int var7 : new int[]{this.sk.neck0, this.sk.neck1, this.sk.neck2, this.sk.head, this.sk.jaw, this.sk.earL, this.sk.earR, this.sk.tail}) {
         if (var7 >= 0) {
            this.holdMask[var7] = true;
         }
      }

      this.t3 = new float[this.n * 3];
      this.q3 = new float[this.n * 4];
      this.restMask = new boolean[this.n];

      for (int var6 : new int[]{this.sk.neck0, this.sk.neck1, this.sk.neck2, this.sk.head, this.sk.jaw, this.sk.earL, this.sk.earR}) {
         if (var6 >= 0) {
            this.restMask[var6] = true;
         }
      }

      // The authored "sleep" clips fold the head into the brisket (whitetail) or into the shoulder (elk/moose).
      // The sleeping head pose is therefore built from the clean "bedded" clip: elk/moose take half of their sleep
      // clip's sideways head turn, whitetail rests its head low and forward. Values checked against the skinned
      // meshes so no head vertex ends up inside the torso, neck or folded legs.
      if (var1 == GameSpecies.ELK || var1 == GameSpecies.MOOSE) {
         this.sleepClipW = 0.5F;
         this.sleepNeckYaw = 0.3F;
         this.sleepNeckPitch = 0.0F;
         this.sleepHeadPitch = 0.0F;
      } else {
         this.sleepClipW = 0.0F;
         this.sleepNeckYaw = 0.0F;
         this.sleepNeckPitch = -1.2F;
         this.sleepHeadPitch = 0.4F;
      }
   }

   private static float approach(float var0, float var1, float var2, float var3) {
      float var4 = 1.0F - (float)Math.exp((double)(-var2 * var3));
      return var0 + (var1 - var0) * var4;
   }

   private static float smooth(float var0, float var1, float var2) {
      float var3 = Math.max(0.0F, Math.min(1.0F, (var2 - var0) / (var1 - var0)));
      return var3 * var3 * (3.0F - 2.0F * var3);
   }

   public void update(DeerAnimator.Input var1) {
      float var2 = Float.isNaN(this.last) ? 0.0F : Math.max(0.0F, Math.min(0.25F, var1.time - this.last));
      this.last = var1.time;
      float var3 = Math.max(0.0F, var1.speed);
      GameSpecies.Behavior var4 = this.species.behavior;
      DeerSkeleton.Clip var5 = this.sk.clip("walk");
      DeerSkeleton.Clip var6 = this.sk.clip("run");
      DeerSkeleton.Clip var7 = this.sk.clip("trot");
      DeerSkeleton.Clip var8 = this.sk.clip("stand");
      this.sk.sample(var8, var1.time + (float)(var1.seed & 63) * 0.13F, this.t, this.q);
      if (var4.walkStride() > 0.0F && var7 != null) {
         float var28 = var4.trotSpeed();
         this.moveW = approach(this.moveW, smooth(0.06F, 0.3F, var3), 10.0F, var2);
         int var31 = this.gaitState;
         if (this.gaitState == 0 && var3 > var28 * 0.75F) {
            var31 = 1;
         }

         if (this.gaitState == 1 && var3 < var28 * 0.55F) {
            var31 = 0;
         }

         if (this.gaitState == 1 && var3 > var28 * 2.0F) {
            var31 = 2;
         }

         if (this.gaitState == 2 && var3 < var28 * 1.6F) {
            var31 = 1;
         }

         if (var31 != this.gaitState) {
            float var33 = this.gaitState == 0 ? this.walkPhase : (this.gaitState == 1 ? this.trotPhase : this.runPhase);
            if (var31 == 0 && this.moveW > 0.001F && this.trotW + this.runW > 0.5F) {
               this.walkPhase = var33;
            }

            if (var31 == 1 && this.trotW < 0.5F) {
               this.trotPhase = var33;
            }

            if (var31 == 2 && this.runW < 0.5F) {
               this.runPhase = var33;
            }

            this.gaitState = var31;
         }

         this.trotW = approach(this.trotW, this.gaitState == 1 ? 1.0F : 0.0F, 12.0F, var2);
         this.runW = approach(this.runW, this.gaitState == 2 ? 1.0F : 0.0F, 12.0F, var2);
         float var34 = Math.max(0.0F, 1.0F - this.trotW - this.runW);
         this.walkPhase = (this.walkPhase + (var1.backing ? -1.0F : 1.0F) * var2 * Math.max(0.15F, Math.min(2.0F, var3 / var4.walkStride())) + 1.0F) % 1.0F; // [routines] backing up plays the walk in reverse
         this.trotPhase = (this.trotPhase + var2 * Math.max(0.55F, Math.min(2.8F, var3 / var4.trotStride()))) % 1.0F;
         this.runPhase = (this.runPhase + var2 * Math.max(0.9F, Math.min(3.4F, var3 / var4.runStride()))) % 1.0F;
         if (this.moveW > 0.001F) {
            float var37 = var34 + this.trotW + this.runW;
            this.sk.sample(var5, this.walkPhase * var5.duration(), this.t2, this.q2);
            if (var37 > 1.0E-4F && this.trotW > 0.001F) {
               float[] var13 = (float[])this.t2.clone();
               float[] var14 = (float[])this.q2.clone();
               this.sk.sample(var7, this.trotPhase * var7.duration(), this.t2, this.q2);
               DeerSkeleton.blend(var13, var14, this.t2, this.q2, this.trotW / Math.max(1.0E-4F, var34 + this.trotW), null);
               System.arraycopy(var13, 0, this.t2, 0, var13.length);
               System.arraycopy(var14, 0, this.q2, 0, var14.length);
            }

            if (this.runW > 0.001F) {
               float[] var40 = (float[])this.t2.clone();
               float[] var45 = (float[])this.q2.clone();
               this.sk.sample(var6, this.runPhase * var6.duration(), this.t2, this.q2);
               DeerSkeleton.blend(var40, var45, this.t2, this.q2, this.runW / Math.max(1.0E-4F, var37), null);
               System.arraycopy(var40, 0, this.t2, 0, var40.length);
               System.arraycopy(var45, 0, this.q2, 0, var45.length);
            }

            DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.moveW, null);
         }
      } else {
         this.moveW = approach(this.moveW, smooth(0.1F, 0.45F, var3), 10.0F, var2);
         this.runW = approach(this.runW, smooth(2.4F, 4.6F, var3), 6.0F, var2);
         float var9 = Math.max(0.35F, Math.min(2.4F, var3 / 0.95F));
         this.walkPhase = (this.walkPhase + (var1.backing ? -1.0F : 1.0F) * var2 * var9 + 1.0F) % 1.0F; // [routines] backing up plays the walk in reverse
         float var10 = Math.max(1.4F, Math.min(3.6F, 2.3F * (float)Math.sqrt((double)(Math.max(var3, 0.5F) / 6.0F))));
         this.runPhase = (this.runPhase + var2 * var10) % 1.0F;
         if (this.moveW > 0.001F) {
            this.sk.sample(var5, this.walkPhase * var5.duration(), this.t2, this.q2);
            if (this.runW > 0.001F) {
               float[] var11 = (float[])this.t2.clone();
               float[] var12 = (float[])this.q2.clone();
               this.sk.sample(var6, this.runPhase * var6.duration(), this.t2, this.q2);
               DeerSkeleton.blend(var11, var12, this.t2, this.q2, this.runW, null);
               System.arraycopy(var11, 0, this.t2, 0, var11.length);
               System.arraycopy(var12, 0, this.q2, 0, var12.length);
            }

            DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.moveW, null);
         }
      }

      this.jumpW = approach(this.jumpW, var1.airborne && var1.airTime > 0.12F ? 1.0F : 0.0F, 14.0F, var2);
      if (this.jumpW > 0.001F) {
         DeerSkeleton.Clip var29 = this.sk.clip("jump");
         this.sk.sample(var29, 0.3F + var1.airTime * 1.1F, this.t2, this.q2);
         DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.jumpW, null);
      }

      this.grazeW = approach(this.grazeW, var1.graze * (1.0F - this.moveW), 4.0F, var2);
      if (this.grazeW > 0.001F) {
         this.sk.sample(this.sk.clip("graze"), var1.time, this.t2, this.q2);
         DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.grazeW, null);
      }

      this.stanceW = approach(this.stanceW, var1.stance * (1.0F - this.moveW * 0.7F), 7.0F, var2);
      if (this.stanceW > 0.001F) {
         this.sk.sample(this.sk.clip("alert"), var1.time, this.t2, this.q2);
         DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.stanceW, null);
      }
      String var30 = switch (var1.cue) {
         case 1 -> "stomp";
         case 2 -> "headbob";
         case 3 -> "look_l";
         case 4 -> "look_r";
         case 5 -> "bugle";
         case 6 -> "rut_display";
         case 7 -> "spar";
         default -> null;
      };
      float var32 = 0.0F;
      if (var30 != null && this.sk.clip(var30) != null) {
         DeerSkeleton.Clip var35 = this.sk.clip(var30);
         float var38 = var35.loop() ? 2.2F : var35.duration();
         if (var1.cueHold) {
            // [routines] a held rut cue (display / spar) loops for as long as the pose lasts. Its spar clip starts and
            // ends on the same frame. While the animal walks (circling, shoving) the legs stay on the gait and only the
            // neck, head, ears and tail take the clip, so the feet never slide.
            var32 = smooth(0.0F, 0.15F, var1.cueAge);
            this.sk.sample(var35, var35.loop() ? var1.cueAge : var1.cueAge % var35.duration(), this.t2, this.q2);
            this.cueW = approach(this.cueW, var32, 12.0F, var2);
            float var60 = this.cueW * (1.0F - this.moveW);
            DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, var60, null);
            if (this.cueW > var60 + 1.0E-4F) {
               DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, (this.cueW - var60) / Math.max(1.0E-4F, 1.0F - var60), this.holdMask);
            }
         } else if (var1.cueAge < var38) {
            var32 = smooth(0.0F, 0.15F, var1.cueAge) * (1.0F - smooth(var38 - 0.25F, var38, var1.cueAge));
            this.sk.sample(var35, var1.cueAge, this.t2, this.q2);
            DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.cueW = approach(this.cueW, var32, 12.0F, var2), null);
         }
      }

      if (var32 == 0.0F) {
         this.cueW = approach(this.cueW, 0.0F, 12.0F, var2);
      }

      // [rutfight] locked-antlers head posture (held low spar frame), whole body standing, upper body while shoving
      this.fightW = approach(this.fightW, var1.fightLower, 9.0F, var2);
      com.formaworks.frontierhunts.hunting.rutfight.FightPose.blendLock(this.sk, this.t, this.q, this.fightW, this.moveW, this.holdMask);

      boolean var36 = var1.bedded || var1.sedated;
      float var39 = var1.bedded ? var1.bedAge : var1.sedatedAge;
      boolean var52 = var36 && (var1.sleeping || var1.sedated);
      // Sleeping is a slow crossfade on top of the bedded pose (no clip snap when bedded <-> sleeping or when rising).
      this.sleepW = approach(this.sleepW, var52 ? 1.0F : 0.0F, var52 ? 1.2F : 4.0F, var2);
      DeerSkeleton.Clip var53 = this.sk.clip("rise");
      float var54 = var53.duration();
      if (!var36 && !var1.rising && !var1.waking) {
         // Fade the last of the lying weight out instead of snapping straight to the standing pose.
         this.bedW = approach(this.bedW, 0.0F, 10.0F, var2);
         if (this.bedW > 0.001F) {
            this.sk.sample(var53, var54, this.t2, this.q2);
            DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.bedW, null);
         } else {
            this.bedW = 0.0F;
         }
      } else {
         float var55 = 1.0F;
         if (var36) {
            DeerSkeleton.Clip var42 = this.sk.clip("bed_down");
            if (var39 < var42.duration()) {
               this.sk.sample(var42, var39, this.t2, this.q2);
            } else {
               this.sk.sample(this.sk.clip("bedded"), var1.time, this.t2, this.q2);
            }
         } else {
            // Waking from sedation lasts 42 ticks: stretch the rise so it completes inside that window.
            float var41 = var1.rising ? var1.riseAge * (var1.riseHurry ? RISE_HURRY : 1.0F) : var1.wakingAge * var54 / 1.9F;
            this.sk.sample(var53, var41, this.t2, this.q2);
            // Hand the legs back to the gait as the rise completes (sooner when bolting) so the animal never moves on frozen legs.
            var55 = 1.0F - smooth(var54 * (var1.rising && var1.riseHurry ? 0.55F : 0.8F), var54, var41);
         }

         if (this.sleepW > 0.001F && this.sleepClipW > 0.0F) {
            this.sk.sample(this.sk.clip("sleep"), var1.time, this.t3, this.q3);
            DeerSkeleton.blend(this.t2, this.q2, this.t3, this.q3, this.sleepW * this.sleepClipW, this.restMask);
         }

         this.bedW = var55 < this.bedW ? var55 : approach(this.bedW, var55, 8.0F, var2);
         DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.bedW, null);
      }

      if (var1.downed) {
         this.downW = approach(this.downW, 1.0F, 18.0F, var2);
         DeerSkeleton.Clip var43 = this.sk.clip(var1.fallLeft ? "collapse_l" : "collapse_r");
         if (var1.downAge < var43.duration()) {
            this.sk.sample(var43, var1.downAge, this.t2, this.q2);
         } else {
            this.sk.sample(this.sk.clip(var1.fallLeft ? "dead_l" : "dead_r"), 0.0F, this.t2, this.q2);
         }

         DeerSkeleton.blend(this.t, this.q, this.t2, this.q2, this.downW, null);
      } else {
         this.downW = 0.0F;
      }

      boolean var44 = !var1.downed;
      com.formaworks.frontierhunts.sign.work.SignPose.legs(this.sk, this.t, this.q, this.model, var1); // [deersign] pawing / rub-urination legs
      this.sk.pose(this.t, this.q, null, this.model);
      if (var44) {
         float var46 = Math.max(this.bedW * (float)(var36 ? 1 : 0), 0.0F);
         float var56 = this.sleepW * this.bedW;
         if (var56 > 0.001F) {
            // Sleeping head rest: lift the head clear of the chest/shoulder (see constructor).
            int[] var57 = this.sk.neck2 >= 0 ? new int[]{this.sk.neck0, this.sk.neck1, this.sk.neck2} : new int[]{this.sk.neck0, this.sk.neck1};

            for (int var58 : var57) {
               this.rotateModel(var58, 0.0F, 1.0F, 0.0F, this.sleepNeckYaw * var56 / (float)var57.length);
            }

            for (int var59 : var57) {
               this.rotateModel(var59, 1.0F, 0.0F, 0.0F, this.sleepNeckPitch * var56 / (float)var57.length);
            }

            this.rotateModel(this.sk.head, 1.0F, 0.0F, 0.0F, this.sleepHeadPitch * var56);
         }

         // Head look: damped and clamped while lying so it can't swing the head into the body, and off while asleep.
         float var15 = (1.0F - this.grazeW * 0.85F) * (1.0F - this.cueW * 0.8F) * (1.0F - var46 * 0.55F) * (1.0F - this.sleepW)
            * (1.0F - this.fightW); // [rutfight] no free head look while the racks are engaged
         float var60 = 1.2F - 0.5F * var46;
         float var16 = Math.max(-var60, Math.min(var60, var1.headYaw)) * var15;
         float var17 = Math.max(-0.5F + 0.25F * var46, Math.min(0.5F - 0.15F * var46, var1.headPitch)) * var15;
         if (var1.hitAge < 0.6F) {
            float var18 = (float)Math.sin((double)Math.min(1.0F, var1.hitAge / 0.6F) * Math.PI);
            this.rotateModel(this.sk.bodyTop0, 1.0F, 0.0F, 0.0F, (var1.hitKind == 2 ? 0.12F : -0.06F) * var18);
            this.rotateModel(this.sk.bodyTop1, 1.0F, 0.0F, 0.0F, (var1.hitKind == 2 ? -0.08F : 0.05F) * var18);
         }

         this.aggW = approach(this.aggW, var1.aggression, 6.0F, var2);
         if (this.aggW > 0.001F) {
            this.rotateModel(this.sk.neck0, 1.0F, 0.0F, 0.0F, -0.1F * this.aggW);
            this.rotateModel(this.sk.head, 1.0F, 0.0F, 0.0F, -0.3F * this.aggW);
         }

         this.rotateModel(this.sk.neck0, 0.0F, 1.0F, 0.0F, var16 * 0.35F);
         this.rotateModel(this.sk.neck1, 0.0F, 1.0F, 0.0F, var16 * 0.35F);
         this.rotateModel(this.sk.head, 0.0F, 1.0F, 0.0F, var16 * 0.3F);
         this.rotateModel(this.sk.neck1, 1.0F, 0.0F, 0.0F, var17 * 0.45F);
         this.rotateModel(this.sk.head, 1.0F, 0.0F, 0.0F, var17 * 0.55F);
         float var48 = 1.4F + var1.fleeing * 2.6F;
         float var19 = (float)Math.sin((double)(var1.time * var48) * Math.PI * 2.0) * (0.006F + var1.fleeing * 0.012F);
         this.rotateModel(this.sk.bodyTop1, 1.0F, 0.0F, 0.0F, var19);
         this.earPhase += var2;

         for (int var20 = 0; var20 < 2; var20++) {
            int var21 = var20 == 0 ? this.sk.earL : this.sk.earR;
            float var22 = var20 == 0 ? 1.0F : -1.0F;
            double var23 = (double)var1.time * (0.37 + (double)var20 * 0.11) + (double)(var1.seed >> var20 & 15);
            float var25 = (float)Math.pow(Math.max(0.0, Math.sin(var23)), 24.0) * 0.45F * (1.0F - var1.alert);
            float var26 = var1.alert * 0.35F * (1.0F - this.aggW) - this.aggW * 0.95F;
            float var27 = Math.max(-0.8F, Math.min(0.8F, var1.earYaw)) * var1.alert;
            this.rotateModel(var21, 0.0F, 1.0F, 0.0F, var22 * (0.05F + var25) + var27 * 0.6F);
            this.rotateModel(var21, 1.0F, 0.0F, 0.0F, -var26 + var25 * 0.3F);
         }

         this.tailW = approach(this.tailW, var1.tailRaise, var1.tailRaise > this.tailW ? 9.0F : 2.5F, var2);
         float var49 = (float)Math.sin((double)var1.time * 2.3 + (double)var1.seed) * 0.12F * (1.0F - this.tailW);
         float var50 = (float)Math.pow(Math.max(0.0, Math.sin((double)var1.time * 0.9 + (double)var1.seed * 0.7)), 30.0) * 0.35F * (1.0F - this.tailW);
         float var51 = this.species.behavior.walkStride() > 0.0F ? -1.0F : 1.0F;
         this.rotateModel(this.sk.tail, 1.0F, 0.0F, 0.0F, var51 * (this.tailW * (this.species == GameSpecies.WHITETAIL ? 1.75F : 0.9F) + var50));
         this.rotateModel(this.sk.tail, 0.0F, 1.0F, 0.0F, var49 + (float)Math.sin((double)(var1.time * 9.0F)) * 0.08F * this.tailW);
      }

      if (var1.fightHead != null && !var1.downed) {
         // [rutfight] put the rack exactly where the client's contact solve wants it (neck bends, head turns)
         com.formaworks.frontierhunts.hunting.rutfight.FightPose.reach(this.sk, this.t, this.q, this.model, var1.fightHead, var1.fightReach, this.fightResidual);
      } else {
         this.fightResidual.zero();
      }

      for (int var47 = 0; var47 < this.n; var47++) {
         this.skin[var47].set(this.model[var47]).mul(this.sk.inverseBind[var47]);
      }
   }

   private void rotateModel(int var1, float var2, float var3, float var4, float var5) {
      if (!(Math.abs(var5) < 1.0E-5F)) {
         int var6 = this.sk.parents[var1];
         if (var6 >= 0) {
            this.tmp.set(this.model[var6]).invert();
            this.tmp.transformDirection(this.axis.set(var2, var3, var4)).normalize();
         } else {
            this.axis.set(var2, var3, var4);
         }

         DeerSkeleton.preRotateLocal(this.q, var1, this.axis.x, this.axis.y, this.axis.z, var5);
         this.sk.pose(this.t, this.q, null, this.model);
      }
   }

   public float[] locals() {
      return this.t;
   }

   public static final class Input {
      public float time;
      public float speed;
      public boolean airborne;
      public float airTime;
      public float graze;
      public float alert;
      public float stance;
      public boolean bedded;
      public float bedAge;
      public boolean sleeping;
      public boolean rising;
      public float riseAge;
      public boolean riseHurry;
      public int cue;
      public float cueAge;
      public boolean downed;
      public float downAge;
      public boolean fallLeft;
      public boolean sedated;
      public float sedatedAge;
      public boolean waking;
      public float wakingAge;
      public float headYaw;
      public float headPitch;
      public float tailRaise;
      public float earYaw;
      public float hitAge = 99.0F;
      public int hitKind;
      public float fleeing;
      public float aggression;
      public boolean buck;
      public int seed;
      public boolean cueHold; // [routines] hold/loop the current rut cue (display, spar)
      public boolean backing; // [routines] moving backwards (shoved in a fight)
      public float fightLower; // [rutfight] 0..1 locked-antlers head posture
      public org.joml.Matrix4f fightHead; // [rutfight] model-space head target from the contact solve (client), or null
      public float fightReach; // [rutfight] weight of fightHead
      public int signPhase; // [deersign] SignAct phase a buck is working sign in (0 none)
      public float signAge; // [deersign] seconds into that phase
      public float signDur; // [deersign] phase length, seconds
      public int signSeed; // [deersign] motion seed
      public float signW; // [deersign] weight of the sign posture
      public float signPitch; // [deersign] Vanilla box head: extra nose-down pitch (rad)
      public float signYaw; // [deersign] Vanilla box head: extra yaw (rad)
      public float signDrop; // [deersign] Vanilla box head: extra drop (model pixels)
   }
}
