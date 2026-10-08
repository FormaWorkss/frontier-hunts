package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.EquipmentSounds;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.expedition.WeaponAction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldGunEffects {
   static final ResourceLocation FLASH = FrontierHunts.id("textures/effect/muzzle_flash_v2.png");
   private static final Random RANDOM = new Random();
   private static long predictedAt = Long.MIN_VALUE;
   private static int predictedSlot = -1;
   private static float kick;
   private static float kickO;
   private static float yawKick;
   private static float yawKickO;
   private static float rollKick;
   private static float rollKickO;
   private static float fov;
   private static float fovO;
   private static float kickVel;
   private static float yawVel;
   private static float rollVel;
   private static long predictedNext;
   private static final List<FieldGunEffects.Pending> pending = new ArrayList<>();
   private static final List<FieldGunEffects.Casing> CASINGS = new ArrayList<>();
   private static final List<FieldGunEffects.ViewCasing> VIEW = new ArrayList<>();
   private static final Matrix4f GUN_TO_VIEW = new Matrix4f();
   private static boolean gunFrameValid;
   private static float lastYaw = Float.NaN;
   private static float lastPitch = Float.NaN;
   private static final Map<Integer, Long> SEEN = new HashMap<>();
   private static final Map<Integer, Long> SEEN_RELOAD = new HashMap<>();

   public static void tryPredict(Player var0, ItemStack var1, Weapon var2, long var3) {
      if (!var2.bow && var0.isAlive() && !var0.isSpectator() && !var0.isUnderWater()) {
         CompoundTag var5 = ExpeditionWeapon.data(var1);
         if (var5.getInt("rounds") > 0
            && var5.getLong("reload_until") <= 0L
            && var5.getLong("next_shot") <= var3
            && (predictedSlot != var0.getInventory().selected || var3 >= predictedNext)) {
            predictShot(var0, var1, var2);
         }
      }
   }

   static void predictShot(Player var0, ItemStack var1, Weapon var2) {
      Minecraft var3 = Minecraft.getInstance();
      if (var3.level != null) {
         predictedAt = var3.level.getGameTime();
         predictedSlot = var0.getInventory().selected;
         predictedNext = predictedAt + (long)Math.max(1, var2.interval);
         viewRecoil(var0, var1, var2);
         if (casingOnShot(var2)) {
            eject(var0, var2, true);
         } else if (var2 == Weapon.LEVER_RIFLE || var2 == Weapon.PUMP_SHOTGUN) {
            pending.add(new FieldGunEffects.Pending(var0.getId(), var2, predictedAt + (long)(var2 == Weapon.PUMP_SHOTGUN ? 5 : 4)));
         }
      }
   }

   private static void viewRecoil(Player var0, ItemStack var1, Weapon var2) {
      FieldWeaponFirstPerson.impulse(var2, var1);
      if (!(Boolean)HuntConfig.REDUCED_MOTION.get()) {
         boolean var3 = ExpeditionClient.aiming();
         boolean var4 = (var0.isCrouching() || com.formaworks.frontierhunts.prone.Prone.isProne(var0)) && ExpeditionWeapon.attachment(var1, "bipod"); // [rifle]
         float var5 = ExpeditionWeapon.attachment(var1, "muzzle_brake") ? 0.7F : 1.0F;
         var5 *= !var4 && com.formaworks.frontierhunts.prone.Prone.isProne(var0) ? 0.65F : 1.0F; // [rifle] prone, no bipod: body takes the kick
         boolean sticks = com.formaworks.frontierhunts.sticks.ShootingSticks.rested(var0); // [sticks] the yoke takes the kick
         var5 *= sticks && !var4 ? com.formaworks.frontierhunts.sticks.ShootingSticks.RECOIL : 1.0F;
         float var6 = (var3 ? 0.8F : 1.0F) * (var4 ? 0.45F : 1.0F) * (ExpeditionWeapon.attachment(var1, "steady_stock") ? 0.85F : 1.0F) * var5;
         float var7 = viewKick(var2) * var6;
         kickVel += var7 * 0.9F;
         yawVel = yawVel + (RANDOM.nextFloat() - 0.5F) * var7 * 0.45F;
         rollVel = rollVel + (RANDOM.nextFloat() - 0.5F) * var7 * 0.8F;
         fov = fov + (var2.pellets <= 0 && var2 != Weapon.DOUBLE_BARREL ? 1.3F : 2.4F);
         float var8 = var7 * 0.22F * (sticks ? com.formaworks.frontierhunts.sticks.ShootingSticks.CLIMB : 1.0F); // [sticks] settles back into the yoke
         var0.setXRot(Mth.clamp(var0.getXRot() - var8, -90.0F, 90.0F));
         var0.xRotO = var0.getXRot();
         var0.setYRot(var0.getYRot() + (RANDOM.nextFloat() - 0.5F) * var8 * 0.6F);
         var0.yRotO = var0.getYRot();
      }
   }

   static long shotTime(ItemStack var0) {
      long var1 = ExpeditionWeapon.data(var0).getLong("shot_at");
      Minecraft var3 = Minecraft.getInstance();
      return FieldWeaponMesh.localView
            && var3.player != null
            && predictedSlot == var3.player.getInventory().selected
            && predictedAt >= var1 - 6L
            && predictedAt > Long.MIN_VALUE
         ? Math.max(predictedAt, 1L)
         : var1;
   }

   private static float viewKick(Weapon var0) {
      return switch (var0) {
         case DOUBLE_BARREL -> 5.5F;
         case PUMP_SHOTGUN -> 4.6F;
         case SEMI_AUTO_SHOTGUN -> 3.6F;
         case LEVER_RIFLE -> 3.0F;
         case SEMI_AUTO_RIFLE -> 1.35F;
         case REVOLVER -> 3.8F;
         case FIELD_PISTOL -> 2.4F;
         case FLARE_GUN -> 2.6F;
         case TRANQUILIZER_RIFLE -> 0.7F;
         case BAIT_LAUNCHER -> 1.6F;
         default -> 1.0F;
      };
   }

   @SubscribeEvent
   public static void camera(ComputeCameraAngles var0) {
      float var1 = (float)var0.getPartialTick();
      float var2 = Mth.lerp(var1, kickO, kick);
      float var3 = Mth.lerp(var1, yawKickO, yawKick);
      float var4 = Mth.lerp(var1, rollKickO, rollKick);
      float shake = com.formaworks.frontierhunts.HuntConfig.shake(); // [1.1.5] comfort: camera shake setting
      var2 *= shake;
      var3 *= shake;
      var4 *= shake;
      if (var2 != 0.0F || var3 != 0.0F || var4 != 0.0F) {
         var0.setPitch(var0.getPitch() - var2);
         var0.setYaw(var0.getYaw() + var3);
         var0.setRoll(var0.getRoll() + var4);
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFov var0) {
      if (var0.usedConfiguredFov()) {
         float var1 = Mth.lerp((float)var0.getPartialTick(), fovO, fov);
         if (var1 > 0.0F) {
            var0.setFOV(var0.getFOV() + (double)var1 * var0.getFOV() / 70.0);
         }
      }
   }

   private static void springs() {
      kickO = kick;
      yawKickO = yawKick;
      rollKickO = rollKick;
      fovO = fov;
      kickVel = kickVel + -kick * 0.42F;
      kickVel *= 0.52F;
      kick = kick + kickVel;
      yawVel = yawVel + -yawKick * 0.42F;
      yawVel *= 0.52F;
      yawKick = yawKick + yawVel;
      rollVel = rollVel + -rollKick * 0.38F;
      rollVel *= 0.55F;
      rollKick = rollKick + rollVel;
      fov *= 0.62F;
      if (Math.abs(fov) < 0.01F) {
         fov = 0.0F;
      }

      if (Math.abs(kick) < 0.001F && Math.abs(kickVel) < 0.001F) {
         kickVel = 0.0F;
         kick = 0.0F;
      }

      if (Math.abs(yawKick) < 0.001F && Math.abs(yawVel) < 0.001F) {
         yawVel = 0.0F;
         yawKick = 0.0F;
      }

      if (Math.abs(rollKick) < 0.001F && Math.abs(rollVel) < 0.001F) {
         rollVel = 0.0F;
         rollKick = 0.0F;
      }
   }

   static boolean flashes(Weapon var0) {
      return var0 != Weapon.TRANQUILIZER_RIFLE && var0 != Weapon.BAIT_LAUNCHER && !var0.bow;
   }

   static void flash(Weapon var0, ItemStack var1, PoseStack var2, MultiBufferSource var3, double var4) {
      // [gear21] every shot: a short burst of orange fire at the muzzle (about one and a half ticks) and a spray of
      // sparks flying out ahead of it (three ticks). Warm colours, not the old white star; daylight only softens it.
      // gear.20 made it vanish by day, which left the pistol with no fire at all.
      if (!flashes(var0) || var4 < 0.0 || var4 >= 3.0) {
         return;
      }
      boolean suppressed = ExpeditionWeapon.attachment(var1, "suppressor") && WeaponAction.supports(var0, "suppressor");
      boolean brake = ExpeditionWeapon.attachment(var1, "muzzle_brake") && WeaponAction.supports(var0, "muzzle_brake");
      float day = 1.0F - 0.45F * ambientLight();
      float size = switch (var0) {
         case DOUBLE_BARREL -> 0.17F;
         case PUMP_SHOTGUN, SEMI_AUTO_SHOTGUN -> 0.15F;
         case LEVER_RIFLE -> 0.13F;
         case REVOLVER -> 0.12F;
         case FIELD_PISTOL -> 0.1F;
         case FLARE_GUN -> 0.14F;
         default -> 0.11F;
      };
      if (suppressed) {
         size *= 0.3F;
      }
      long shot = ExpeditionWeapon.data(var1).getLong("shot_at");
      int frame = Math.floorMod(shot, 2);
      double z = FieldWeaponSockets.muzzleZ(var0) - (suppressed ? 0.172 : (brake ? 0.056 : 0.0)) - 0.004;
      VertexConsumer vc = var3.getBuffer(RenderType.entityTranslucentEmissive(FLASH));
      int g = var0 == Weapon.FLARE_GUN ? 150 : 205;
      int b = var0 == Weapon.FLARE_GUN ? 90 : 120;
      float u0 = frame * 0.5F, u1 = u0 + 0.5F;
      var2.pushPose();
      var2.translate(0.0, FieldWeaponSockets.muzzleY(var0), z);
      if (var4 < 1.5) {
         float k = (float)Math.pow(1.0 - var4 / 1.5, 1.4);
         int alpha = (int)(255.0F * Math.min(1.0F, k * day));
         float grow = 0.8F + 0.4F * (float)(var4 / 1.5);
         float s = size * grow;
         var2.pushPose();
         var2.mulPose(Axis.ZP.rotationDegrees((float)Math.floorMod(shot * 37L, 360)));
         // the fireball, facing down the barrel
         quad(vc, var2, alpha, -s * 0.5F, -s * 0.5F, 0.0F, s * 0.5F, -s * 0.5F, 0.0F, s * 0.5F, s * 0.5F, 0.0F, -s * 0.5F, s * 0.5F, 0.0F,
            u0, 0.0F, u1, 0.5F, 255, g, b);
         // the flame tongues forward (a crossed pair, four petals with a muzzle brake)
         float len = s * (brake ? 1.0F : 1.5F), half = s * 0.4F;
         for (int i = 0; i < (brake ? 4 : 2); i++) {
            var2.pushPose();
            var2.mulPose(Axis.ZP.rotationDegrees(i * (brake ? 45.0F : 90.0F)));
            quad(vc, var2, alpha, 0.0F, -half, 0.0F, 0.0F, -half, -len, 0.0F, half, -len, 0.0F, half, 0.0F, u0, 0.5F, u1, 1.0F, 255, g, b);
            var2.popPose();
         }
         var2.popPose();
      }
      if (!suppressed) {
         // sparks: a few burning flecks of powder flying out in a cone, seeded by the shot so each shot differs
         java.util.Random r = new java.util.Random(shot * 6364136223846793005L + 1442695040888963407L);
         int n = var0 == Weapon.DOUBLE_BARREL || var0 == Weapon.PUMP_SHOTGUN || var0 == Weapon.SEMI_AUTO_SHOTGUN ? 9 : 6;
         float t = (float)var4;
         int alpha = (int)(255.0F * Math.max(0.0F, 1.0F - t / 3.0F) * Math.max(0.55F, day));
         for (int i = 0; i < n; i++) {
            double az = r.nextDouble() * Math.PI * 2.0, cone = Math.toRadians(4.0 + r.nextDouble() * 22.0);
            float dx = (float)(Math.sin(cone) * Math.cos(az)), dy = (float)(Math.sin(cone) * Math.sin(az)), dz = -(float)Math.cos(cone);
            float speed = 0.12F + r.nextFloat() * 0.16F;
            float d = 0.02F + speed * t;
            float cx = dx * d, cy = dy * d - 0.006F * t * t, cz = dz * d;
            float sl = 0.025F + 0.02F * r.nextFloat(), w = 0.0035F;
            // a thin streak along the flight direction, widened sideways in the gun's x-y plane
            float px = -dy, py = dx;
            float pl = (float)Math.sqrt(px * px + py * py);
            if (pl < 1.0E-4F) {
               px = 1.0F;
               py = 0.0F;
               pl = 1.0F;
            }
            px = px / pl * w;
            py = py / pl * w;
            float hx = dx * sl * 0.5F, hy = dy * sl * 0.5F, hz = dz * sl * 0.5F;
            quad(vc, var2, alpha, cx - hx - px, cy - hy - py, cz - hz, cx + hx - px, cy + hy - py, cz + hz, cx + hx + px, cy + hy + py, cz + hz,
               cx - hx + px, cy - hy + py, cz - hz, u0 + 0.04F, 0.73F, u0 + 0.16F, 0.77F, 255, 215, 140);
         }
      }
      var2.popPose();
   }

   /** 0 (night, or deep under cover) .. 1 (open sky at noon): how washed out a muzzle flash is. */
   private static float ambientLight() {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return 0.0F;
      }
      float sky = mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY, mc.player.blockPosition()) / 15.0F;
      float day = Math.max(0.0F, (mc.level.getSkyDarken(1.0F) - 0.2F) / 0.8F);
      return sky * day;
   }

   private static void quad(
      VertexConsumer var0,
      PoseStack var1,
      int var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      float var14,
      float var15,
      float var16,
      float var17,
      float var18,
      int var19,
      int var20,
      int var21
   ) {
      Pose var22 = var1.last();
      float[][] var23 = new float[][]{
         {var3, var4, var5, var15, var18}, {var6, var7, var8, var17, var18}, {var9, var10, var11, var17, var16}, {var12, var13, var14, var15, var16}
      };

      for (int var24 = 0; var24 < 2; var24++) {
         for (int var25 = 0; var25 < 4; var25++) {
            float[] var26 = var23[var24 == 0 ? var25 : 3 - var25];
            var0.addVertex(var22, var26[0], var26[1], var26[2])
               .setColor(var19, var20, var21, var2)
               .setUv(var26[3], var26[4])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(15728880)
               .setNormal(var22, 0.0F, 0.0F, var24 == 0 ? -1.0F : 1.0F);
         }
      }
   }

   private static void quad(
      VertexConsumer var0,
      PoseStack var1,
      float var2,
      float var3,
      float var4,
      float var5,
      float var6,
      float var7,
      float var8,
      float var9,
      float var10,
      float var11,
      float var12,
      float var13,
      float var14,
      float var15,
      float var16,
      float var17,
      int var18,
      int var19,
      int var20
   ) {
      Pose var21 = var1.last();
      float[][] var22 = new float[][]{
         {var2, var3, var4, var14, var17}, {var5, var6, var7, var16, var17}, {var8, var9, var10, var16, var15}, {var11, var12, var13, var14, var15}
      };

      for (int var23 = 0; var23 < 2; var23++) {
         for (int var24 = 0; var24 < 4; var24++) {
            float[] var25 = var22[var23 == 0 ? var24 : 3 - var24];
            var0.addVertex(var21, var25[0], var25[1], var25[2])
               .setColor(var18, var19, var20, 255)
               .setUv(var25[3], var25[4])
               .setOverlay(OverlayTexture.NO_OVERLAY)
               .setLight(15728880)
               .setNormal(var21, 0.0F, 0.0F, var23 == 0 ? -1.0F : 1.0F);
         }
      }
   }

   static void gunFrame(Matrix4f var0, Matrix4f var1) {
      GUN_TO_VIEW.set(var0).invert().mul(var1);
      gunFrameValid = true;
   }

   private static boolean viewEject(Weapon var0) {
      double[] var1 = FieldWeaponSockets.ejection(var0);
      if (var1 != null && gunFrameValid) {
         Minecraft var2 = Minecraft.getInstance();
         if (!var2.options.getCameraType().isFirstPerson()) {
            return false;
         } else {
            FieldGunEffects.ViewCasing var3 = new FieldGunEffects.ViewCasing();
            Vector3f var4 = GUN_TO_VIEW.transformPosition(new Vector3f((float)var1[0], (float)var1[1], (float)var1[2]));
            Vector3f var5 = GUN_TO_VIEW.transformDirection(
               new Vector3f(
                  (float)(var1[3] * (0.85 + (double)RANDOM.nextFloat() * 0.3)),
                  (float)(var1[4] * (0.85 + (double)RANDOM.nextFloat() * 0.3)),
                  (float)(var1[5] * (0.7 + (double)RANDOM.nextFloat() * 0.6))
               )
            );
            var3.x = var3.ox = (double)var4.x;
            var3.y = var3.oy = (double)var4.y;
            var3.z = var3.oz = (double)var4.z;
            var3.vx = (double)var5.x;
            var3.vy = (double)var5.y;
            var3.vz = (double)var5.z;
            var3.hull = var0.pellets > 0;
            var3.pistol = var0 == Weapon.FIELD_PISTOL;
            var3.a = var3.oa = RANDOM.nextFloat() * 360.0F;
            var3.b = var3.ob = 0.0F;
            var3.spinA = 40.0F + RANDOM.nextFloat() * 50.0F;
            var3.spinB = (RANDOM.nextFloat() - 0.5F) * 70.0F;
            if (VIEW.size() >= 16) {
               VIEW.remove(0);
            }

            VIEW.add(var3);
            return true;
         }
      } else {
         return false;
      }
   }

   private static void tickView(Player var0) {
      float var1 = var0.getYRot();
      float var2 = var0.getXRot();
      float var3 = Float.isNaN(lastYaw) ? 0.0F : Mth.wrapDegrees(var1 - lastYaw);
      float var4 = Float.isNaN(lastPitch) ? 0.0F : var2 - lastPitch;
      lastYaw = var1;
      lastPitch = var2;
      double var5 = Math.toRadians((double)var2);
      double var7 = -Math.cos(var5) * 0.045;
      double var9 = -Math.sin(var5) * 0.045;
      double var11 = Math.cos(Math.toRadians((double)var3));
      double var13 = Math.sin(Math.toRadians((double)var3));
      double var15 = Math.cos(Math.toRadians((double)var4));
      double var17 = Math.sin(Math.toRadians((double)var4));
      Iterator var19 = VIEW.iterator();

      while (var19.hasNext()) {
         FieldGunEffects.ViewCasing var20 = (FieldGunEffects.ViewCasing)var19.next();
         var20.ox = var20.x;
         var20.oy = var20.y;
         var20.oz = var20.z;
         var20.oa = var20.a;
         var20.ob = var20.b;
         double var21 = var20.x * var11 + var20.z * var13;
         double var23 = -var20.x * var13 + var20.z * var11;
         var20.x = var21;
         var20.z = var23;
         double var25 = var20.vx * var11 + var20.vz * var13;
         double var27 = -var20.vx * var13 + var20.vz * var11;
         var20.vx = var25;
         var20.vz = var27;
         double var29 = var20.y * var15 - var20.z * var17;
         var23 = var20.y * var17 + var20.z * var15;
         var20.y = var29;
         var20.z = var23;
         var20.vy += var7;
         var20.vz += var9;
         var20.x = var20.x + var20.vx;
         var20.y = var20.y + var20.vy;
         var20.z = var20.z + var20.vz;
         var20.a = var20.a + var20.spinA;
         var20.b = var20.b + var20.spinB;
         if (++var20.age >= 7 || var20.y < -0.9 || var20.z > 0.05) {
            toWorld(var0, var20);
            var19.remove();
         }
      }
   }

   private static void toWorld(Player var0, FieldGunEffects.ViewCasing var1) {
      Minecraft var2 = Minecraft.getInstance();
      if (var2.level != null) {
         Camera var3 = var2.gameRenderer.getMainCamera();
         Quaternionf var4 = new Quaternionf(var3.rotation());
         Vector3f var5 = var4.transform(new Vector3f((float)var1.x, (float)var1.y, (float)var1.z));
         Vector3f var6 = var4.transform(new Vector3f((float)var1.vx, (float)var1.vy, (float)var1.vz));
         Vec3 var7 = var3.getPosition().add((double)var5.x, (double)var5.y, (double)var5.z);
         FieldGunEffects.Casing var8 = new FieldGunEffects.Casing();
         var8.x = var8.ox = var7.x;
         var8.y = var8.oy = var7.y;
         var8.z = var8.oz = var7.z;
         Vec3 var9 = var0.getDeltaMovement();
         var8.vx = (double)var6.x + var9.x;
         var8.vy = (double)var6.y;
         var8.vz = (double)var6.z + var9.z;
         var8.hull = var1.hull;
         var8.pistol = var1.pistol;
         var8.yaw = var8.oyaw = var0.getYRot() + 90.0F + var1.a * 0.2F;
         var8.pitch = var8.opitch = var1.b;
         var8.spinYaw = var1.spinB * 0.6F;
         var8.spinPitch = var1.spinA * 0.5F;
         if (CASINGS.size() >= 64) {
            CASINGS.remove(0);
         }

         CASINGS.add(var8);
      }
   }

   static void drawViewCasings(PoseStack var0, MultiBufferSource var1, int var2, float var3) {
      if (!VIEW.isEmpty()) {
         FilteredFieldTexture.ensure(FieldWeaponMesh.guns());

         for (FieldGunEffects.ViewCasing var5 : VIEW) {
            var0.pushPose();
            var0.translate(Mth.lerp((double)var3, var5.ox, var5.x), Mth.lerp((double)var3, var5.oy, var5.y), Mth.lerp((double)var3, var5.oz, var5.z));
            var0.mulPose(Axis.YP.rotationDegrees(90.0F + Mth.lerp(var3, var5.oa, var5.a) * 0.25F));
            var0.mulPose(Axis.XP.rotationDegrees(Mth.lerp(var3, var5.oa, var5.a)));
            var0.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(var3, var5.ob, var5.b)));
            FieldWeaponMesh.drawStatic(
               var5.hull ? "casing_hull_close" : (var5.pistol ? "casing_pistol_close" : "casing_rifle_close"), FieldWeaponMesh.guns(), var0, var1, var2
            );
            var0.popPose();
         }
      }
   }

   static boolean casingOnShot(Weapon var0) {
      return var0 == Weapon.SEMI_AUTO_RIFLE || var0 == Weapon.SEMI_AUTO_SHOTGUN || var0 == Weapon.FIELD_PISTOL;
   }

   static void eject(Player var0, Weapon var1, boolean var2) {
      Minecraft var3 = Minecraft.getInstance();
      if (var3.level != null && (!var2 || var0 != var3.player || !viewEject(var1))) {
         boolean var4 = var2 && var3.options.getCameraType().isFirstPerson();
         double var5 = Math.toRadians((double)var0.getYRot());
         double var7 = Math.toRadians((double)var0.getXRot());
         Vec3 var9 = new Vec3(-Math.sin(var5) * Math.cos(var7), -Math.sin(var7), Math.cos(var5) * Math.cos(var7));
         float var10 = var0.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
         Vec3 var11 = new Vec3(-Math.cos(var5), 0.0, -Math.sin(var5)).scale((double)var10);
         Vec3 var12 = var11.cross(var9).normalize().scale((double)var10);
         boolean var13 = var2 && ExpeditionClient.aiming();
         Vec3 var14 = var0.getEyePosition()
            .add(var9.scale(var4 ? 0.42 : 0.36))
            .add(var11.scale(var4 ? (var13 ? 0.06 : 0.16) : 0.2))
            .add(var12.scale(var4 ? (var13 ? -0.05 : -0.1) : -0.24));
         Vec3 var15 = var11.scale(0.085 + (double)RANDOM.nextFloat() * 0.04)
            .add(var12.scale(0.07 + (double)RANDOM.nextFloat() * 0.04))
            .add(var9.scale(-0.02 + (double)RANDOM.nextFloat() * 0.02))
            .add(var0.getDeltaMovement());
         FieldGunEffects.Casing var16 = new FieldGunEffects.Casing();
         var16.x = var16.ox = var14.x;
         var16.y = var16.oy = var14.y;
         var16.z = var16.oz = var14.z;
         var16.vx = var15.x;
         var16.vy = var15.y;
         var16.vz = var15.z;
         var16.hull = var1.pellets > 0 || var1 == Weapon.DOUBLE_BARREL;
         var16.pistol = var1 == Weapon.FIELD_PISTOL || var1 == Weapon.REVOLVER;
         var16.yaw = var16.oyaw = var0.getYRot() + 90.0F;
         var16.spinYaw = (RANDOM.nextFloat() - 0.5F) * 60.0F;
         var16.spinPitch = 25.0F + RANDOM.nextFloat() * 35.0F;
         if (CASINGS.size() >= 64) {
            CASINGS.remove(0);
         }

         CASINGS.add(var16);
      }
   }

   private static void dump(Player var0, Weapon var1, int var2) {
      for (int var3 = 0; var3 < var2; var3++) {
         eject(var0, var1, var0 == Minecraft.getInstance().player);
         FieldGunEffects.Casing var4 = CASINGS.get(CASINGS.size() - 1);
         var4.vx *= 0.35;
         var4.vz *= 0.35;
         var4.vy = -0.02 - (double)RANDOM.nextFloat() * 0.03;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null || var1.player == null) {
         VIEW.clear();
         gunFrameValid = false;
         lastPitch = Float.NaN;
         lastYaw = Float.NaN;
         predictedAt = Long.MIN_VALUE;
         predictedSlot = -1;
         predictedNext = 0L;
         CASINGS.clear();
         pending.clear();
         SEEN.clear();
         SEEN_RELOAD.clear();
         fovO = 0.0F;
         fov = 0.0F;
         rollKickO = 0.0F;
         rollKick = 0.0F;
         yawKickO = 0.0F;
         yawKick = 0.0F;
         kickO = 0.0F;
         kick = 0.0F;
         rollVel = 0.0F;
         yawVel = 0.0F;
         kickVel = 0.0F;
      } else if (!var1.isPaused()) {
         springs();
         tickView(var1.player);
         if (!(var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon)) {
            gunFrameValid = false;
         }

         long var2 = var1.level.getGameTime();

         for (AbstractClientPlayer var5 : var1.level.players()) {
            if (!(var5.distanceToSqr(var1.player) > 2304.0)) {
               ItemStack var6 = var5.getMainHandItem();
               Item var7 = var6.getItem();
               if (var7 instanceof ExpeditionWeapon) {
                  ExpeditionWeapon var8 = (ExpeditionWeapon)var7;
                  if (!var8.weapon.bow) {
                     CompoundTag var9 = ExpeditionWeapon.data(var6);
                     long var10 = var9.getLong("shot_at");
                     long var12 = var9.getLong("reload_started");
                     Long var14 = SEEN.put(var5.getId(), var10);
                     if (var14 != null && var10 > var14 && var2 - var10 < 6L) {
                        boolean var15 = var5 == var1.player;
                        boolean var16 = var15 && Math.abs(var10 - predictedAt) <= 6L;
                        if (var15 && !var16) {
                           viewRecoil(var5, var6, var8.weapon);
                        }

                        if (!var16) {
                           if (casingOnShot(var8.weapon)) {
                              eject(var5, var8.weapon, var15);
                           } else if (var8.weapon == Weapon.LEVER_RIFLE || var8.weapon == Weapon.PUMP_SHOTGUN) {
                              pending.add(new FieldGunEffects.Pending(var5.getId(), var8.weapon, var10 + (long)(var8.weapon == Weapon.PUMP_SHOTGUN ? 5 : 4)));
                           }
                        }
                     }

                     Long var33 = SEEN_RELOAD.put(var5.getId(), var12);
                     if (var33 != null && var12 > var33 && var9.getBoolean("reload_empty")) {
                        if (var8.weapon == Weapon.DOUBLE_BARREL) {
                           pending.add(new FieldGunEffects.Pending(var5.getId(), var8.weapon, var12 + 10L));
                        }

                        if (var8.weapon == Weapon.REVOLVER) {
                           pending.add(new FieldGunEffects.Pending(var5.getId(), var8.weapon, var12 + 14L));
                        }
                     }
                  }
               }
            }
         }

         Iterator var22 = pending.iterator();

         while (var22.hasNext()) {
            FieldGunEffects.Pending var24 = (FieldGunEffects.Pending)var22.next();
            if (var2 >= var24.at()) {
               var22.remove();
               Entity var26 = var1.level.getEntity(var24.player());
               if (var26 instanceof Player) {
                  Player var28 = (Player)var26;
                  if (var24.weapon() == Weapon.DOUBLE_BARREL) {
                     dump(var28, var24.weapon(), 2);
                  } else if (var24.weapon() == Weapon.REVOLVER) {
                     dump(var28, var24.weapon(), 6);
                  } else {
                     eject(var28, var24.weapon(), var28 == var1.player);
                  }
               }
            }
         }

         var22 = CASINGS.iterator();

         while (var22.hasNext()) {
            FieldGunEffects.Casing var25 = (FieldGunEffects.Casing)var22.next();
            var25.ox = var25.x;
            var25.oy = var25.y;
            var25.oz = var25.z;
            var25.oyaw = var25.yaw;
            var25.opitch = var25.pitch;
            if (++var25.age > 240) {
               var22.remove();
            } else if (!var25.resting) {
               var25.vy -= 0.045;
               var25.vx *= 0.985;
               var25.vy *= 0.985;
               var25.vz *= 0.985;
               double var27 = var25.x + var25.vx;
               double var29 = var25.y + var25.vy;
               double var30 = var25.z + var25.vz;
               BlockPos var31 = BlockPos.containing(var27, var29, var30);
               BlockState var13 = var1.level.getBlockState(var31);
               VoxelShape var32 = var13.getCollisionShape(var1.level, var31);
               boolean var34 = !var32.isEmpty() && var32.bounds().move(var31).inflate(0.002).contains(var27, var29, var30);
               if (var34 && var25.vy < 0.0) {
                  double var35 = var32.max(net.minecraft.core.Direction.Axis.Y) + (double)var31.getY();
                  var29 = Math.max(var29, var35 + 0.004);
                  double var18 = Math.sqrt(var25.vx * var25.vx + var25.vy * var25.vy + var25.vz * var25.vz);
                  if (var18 > 0.05 && var25.bounces < 3) {
                     float var20 = (float)Math.min(0.55, var18 * 2.2) * (var25.bounces == 0 ? 1.0F : 0.6F);
                     SoundEvent var21 = var25.hull ? EquipmentSounds.casing(true) : EquipmentSounds.casing(false);
                     var1.level
                        .playLocalSound(
                           var27, var29, var30, var21, SoundSource.PLAYERS, var20, 0.9F + RANDOM.nextFloat() * 0.25F + (var25.pistol ? 0.15F : 0.0F), false
                        );
                  }

                  var25.bounces++;
                  var25.vy = -var25.vy * (var25.hull ? 0.22 : 0.38);
                  var25.vx *= 0.55;
                  var25.vz *= 0.55;
                  var25.spinPitch *= 0.5F;
                  var25.spinYaw *= 0.6F;
                  if (Math.abs(var25.vy) < 0.03 && var25.vx * var25.vx + var25.vz * var25.vz < 4.0E-4) {
                     var25.resting = true;
                     var25.vy = 0.0;
                     var25.pitch = var25.opitch = 0.0F;
                     var25.spinYaw = 0.0F;
                     var25.spinPitch = 0.0F;
                  }
               } else if (var34) {
                  var25.vx *= -0.3;
                  var25.vz *= -0.3;
                  var27 = var25.x;
                  var30 = var25.z;
               }

               var25.x = var27;
               var25.y = var29;
               var25.z = var30;
               var25.yaw = var25.yaw + var25.spinYaw;
               if (!var25.resting) {
                  var25.pitch = var25.pitch + var25.spinPitch;
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent var0) {
      if (var0.getStage() == Stage.AFTER_ENTITIES && !CASINGS.isEmpty()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.level != null) {
            BufferSource var2 = var1.renderBuffers().bufferSource();
            Vec3 var3 = var0.getCamera().getPosition();
            PoseStack var4 = var0.getPoseStack();
            float var5 = var0.getPartialTick().getGameTimeDeltaPartialTick(false);
            FilteredFieldTexture.ensure(FieldWeaponMesh.guns());

            for (FieldGunEffects.Casing var7 : CASINGS) {
               double var8 = Mth.lerp((double)var5, var7.ox, var7.x);
               double var10 = Mth.lerp((double)var5, var7.oy, var7.y);
               double var12 = Mth.lerp((double)var5, var7.oz, var7.z);
               if (!((var8 - var3.x) * (var8 - var3.x) + (var10 - var3.y) * (var10 - var3.y) + (var12 - var3.z) * (var12 - var3.z) > 1024.0)) {
                  int var14 = LevelRenderer.getLightColor(var1.level, BlockPos.containing(var8, var10, var12));
                  var4.pushPose();
                  var4.translate(var8 - var3.x, var10 - var3.y, var12 - var3.z);
                  var4.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(var5, var7.oyaw, var7.yaw)));
                  var4.mulPose(Axis.XP.rotationDegrees(Mth.lerp(var5, var7.opitch, var7.pitch)));
                  FieldWeaponMesh.drawStatic(
                     var7.hull ? "casing_hull_close" : (var7.pistol ? "casing_pistol_close" : "casing_rifle_close"), FieldWeaponMesh.guns(), var4, var2, var14
                  );
                  var4.popPose();
               }
            }

            var2.endBatch(HuntRenderTypes.supplied(FieldWeaponMesh.guns()));
         }
      }
   }

   private FieldGunEffects() {
   }

   private static final class Casing {
      double x;
      double y;
      double z;
      double ox;
      double oy;
      double oz;
      double vx;
      double vy;
      double vz;
      float yaw;
      float pitch;
      float oyaw;
      float opitch;
      float spinYaw;
      float spinPitch;
      int age;
      int bounces;
      boolean hull;
      boolean pistol;
      boolean resting;
   }

   private static record Pending(int player, Weapon weapon, long at) {
   }

   private static final class ViewCasing {
      double x;
      double y;
      double z;
      double ox;
      double oy;
      double oz;
      double vx;
      double vy;
      double vz;
      float spinA;
      float spinB;
      float a;
      float b;
      float oa;
      float ob;
      int age;
      boolean hull;
      boolean pistol;
   }
}
