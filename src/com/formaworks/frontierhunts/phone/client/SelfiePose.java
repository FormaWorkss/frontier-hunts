package com.formaworks.frontierhunts.phone.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * [1.4.0] Selfie poses. With the phone's front camera up, the wheel picks a pose and the hunter strikes it (and keeps
 * moving a little: a wave waves, a dance dances); hunters nearby see it too while it's held. Applied to the player
 * model after vanilla's animation ({@code client.mixin.PhonePoseMixin}); the sleeves, trouser legs, jacket and hat follow
 * their parts as vanilla's do. Pose 0 is none.
 *
 * <p>Model space: arms hang along +Y from the shoulder; a negative xRot swings an arm forward and up (-PI: straight up),
 * zRot swings it out to the side (the right arm out is positive, the left arm out negative).
 */
public final class SelfiePose {
   public static final String[] NAMES = {"No pose", "Classic", "Peace", "Antlers", "Flex", "Dab", "Wave", "Trophy", "T-pose", "Moose call", "Shocked",
      "Sneaky", "Dance", "Facepalm", "Shrug"};
   private static final float PI = (float)Math.PI;
   /** other hunters' poses: entity id -> {pose, until (game ms)} */
   private static final Map<Integer, long[]> REMOTE = new HashMap<>();

   private SelfiePose() {
   }

   /** A hunter nearby struck (or dropped, 0) a pose. */
   static void remote(int entity, int pose) {
      if (pose <= 0 || pose >= NAMES.length) {
         REMOTE.remove(entity);
      } else {
         REMOTE.put(entity, new long[]{pose, System.currentTimeMillis() + 4500L});
      }
   }

   static void clear() {
      REMOTE.clear();
   }

   private static int poseOf(LivingEntity e) {
      if (!(e instanceof Player)) {
         return 0;
      }
      Minecraft mc = Minecraft.getInstance();
      if (e == mc.player) {
         return PhoneCamera.selfie() ? PhoneCamera.pose : 0;
      }
      if (REMOTE.isEmpty()) {
         return 0;
      }
      long[] p = REMOTE.get(e.getId());
      if (p == null) {
         return 0;
      }
      if (System.currentTimeMillis() > p[1]) {
         REMOTE.remove(e.getId());
         return 0;
      }
      return (int)p[0];
   }

   public static void apply(PlayerModel<?> m, LivingEntity e, float age) {
      int pose = poseOf(e);
      if (pose <= 0) {
         return;
      }
      float t = age * 0.15F;
      ModelPart ra = m.rightArm, la = m.leftArm, hd = m.head;
      switch (pose) {
         case 1 -> { // classic: the phone held out at arm's length, a little head tilt
            set(ra, -1.5F, -0.3F, 0.0F);
            set(la, 0.05F, 0.0F, -0.08F);
            hd.zRot = 0.12F;
         }
         case 2 -> { // peace: phone out, the other hand up by the face
            set(ra, -1.5F, -0.3F, 0.0F);
            set(la, -2.55F, 0.2F, -0.45F);
            hd.zRot = -0.18F;
         }
         case 3 -> { // antlers: both hands at the head, fingers spread like a rack
            set(ra, -2.85F, 0.0F, 0.62F + 0.04F * Mth.sin(t * 2.0F));
            set(la, -2.85F, 0.0F, -0.62F - 0.04F * Mth.sin(t * 2.0F));
            hd.xRot = 0.08F * Mth.sin(t * 1.4F);
         }
         case 4 -> { // flex: arms up and out
            float f = 0.06F * Mth.sin(t * 3.0F);
            set(ra, -0.25F, 0.0F, 2.25F + f);
            set(la, -0.25F, 0.0F, -2.25F - f);
            hd.yRot = 0.25F;
         }
         case 5 -> { // dab
            set(la, -1.65F, 0.95F, 0.0F);
            set(ra, -2.25F, 0.0F, 1.05F);
            hd.xRot = 0.6F;
            hd.yRot = 0.45F;
         }
         case 6 -> { // wave
            set(ra, -2.75F, 0.0F, 0.3F + 0.38F * Mth.sin(t * 4.0F));
            set(la, 0.05F, 0.0F, -0.08F);
            hd.zRot = 0.1F;
         }
         case 7 -> { // trophy: both arms straight up, a little bounce
            float b = 0.07F * Mth.sin(t * 3.0F);
            set(ra, -3.0F + b, 0.0F, 0.12F);
            set(la, -3.0F + b, 0.0F, -0.12F);
            hd.xRot = -0.25F;
         }
         case 8 -> { // T-pose
            set(ra, 0.0F, 0.0F, PI / 2.0F);
            set(la, 0.0F, 0.0F, -PI / 2.0F);
            hd.xRot = 0.0F;
            hd.yRot = 0.0F;
            m.rightLeg.xRot = 0.0F;
            m.leftLeg.xRot = 0.0F;
         }
         case 9 -> { // moose call: hands cupped at the mouth, head up
            set(ra, -1.95F, -0.5F, 0.0F);
            set(la, -1.95F, 0.5F, 0.0F);
            hd.xRot = -0.4F;
         }
         case 10 -> { // shocked: hands to the cheeks
            set(ra, -2.25F, -0.55F, 0.1F);
            set(la, -2.25F, 0.55F, -0.1F);
            hd.xRot = -0.15F;
         }
         case 11 -> { // sneaky: low and stalking, looking about
            set(ra, -0.75F, -0.2F, 0.0F);
            set(la, -0.75F, 0.2F, 0.0F);
            hd.xRot = 0.25F;
            hd.yRot = 0.45F * Mth.sin(t);
            m.rightLeg.xRot = -0.35F;
            m.leftLeg.xRot = 0.3F;
         }
         case 12 -> { // dance
            float s = Mth.sin(t * 3.0F);
            set(ra, 0.35F * s, 0.0F, 0.45F + 0.55F * s);
            set(la, -0.35F * s, 0.0F, -0.45F + 0.55F * s);
            hd.yRot = 0.3F * s;
            hd.zRot = 0.12F * s;
            m.rightLeg.xRot = 0.25F * s;
            m.leftLeg.xRot = -0.25F * s;
         }
         case 13 -> { // facepalm
            set(ra, -2.3F, -0.55F, 0.0F);
            set(la, 0.05F, 0.0F, -0.08F);
            hd.xRot = 0.4F;
         }
         case 14 -> { // shrug
            set(ra, -0.45F, 0.0F, 0.75F);
            set(la, -0.45F, 0.0F, -0.75F);
            hd.zRot = 0.28F;
         }
         default -> {
         }
      }
      m.hat.copyFrom(hd);
      m.leftSleeve.copyFrom(la);
      m.rightSleeve.copyFrom(ra);
      m.leftPants.copyFrom(m.leftLeg);
      m.rightPants.copyFrom(m.rightLeg);
      m.jacket.copyFrom(m.body);
   }

   private static void set(ModelPart p, float x, float y, float z) {
      p.xRot = x;
      p.yRot = y;
      p.zRot = z;
   }
}
