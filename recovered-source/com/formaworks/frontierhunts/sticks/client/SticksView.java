package com.formaworks.frontierhunts.sticks.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sticks.ShootingSticks;
import com.formaworks.frontierhunts.sticks.ShootingSticksEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * [sticks] First person on the sticks.
 *
 * <p>The gun's viewmodel is drawn in the hand pass, with its own projection, while the world (and the sticks in it) has
 * the player's field of view and the scope zoom: drawn in the world, the yoke could never stay under the forend. So for
 * the shooter resting on them the set is drawn in the hand pass too: as the shooter settles in it glides from exactly
 * where the world would show it (the world projection re-expressed in hand space: same screen position, so nothing
 * jumps) to the forend of the gun, and stays there (recoil lifts the gun out of the yoke and it settles back). The
 * world copy is hidden meanwhile; stepping off runs the same glide back before the world copy returns.
 *
 * <p>The weapons' first-person code asks {@link #rest(float)} how far the gun has settled onto the sticks, moves the
 * gun by {@link #dx}/{@link #dy}/{@link #dz} into its rested hold (centred, just under the eye line until the sight
 * comes up; a hair of cant) and reports where its forend is ({@link #anchor}).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SticksView {
   private static final float HAND_TAN = (float)Math.tan(Math.toRadians(35.0));
   /** 0..1 eased rested amount, per tick */
   private static float rest;
   private static float restO;
   /** the sticks drawn in the hand pass (the local rider's, or the ones just stepped off) */
   static ShootingSticksEntity drawn;
   private static int frame;
   private static int anchorFrame = -2;
   private static float ax, ay, az;
   private static boolean anchored;
   private static double fov = 70.0;

   /** the rested-hold offsets computed by {@link #pose} (hand space, handedness applied) and the extra tilt / yaw / cant (degrees, before handedness) */
   public static double dx, dy, dz;
   public static float tilt, yaw, roll;

   /** the rested "ready" hold: head lifted off the stock, the gun lower right, canted a little, muzzle on the line */
   private static final double READY_X = 0.075, READY_DOWN = 0.095, READY_FWD = 0.01;
   private static final float READY_TILT = 1.2F, READY_YAW = 2.5F, READY_CANT = -1.0F;

   private static final Matrix4f BASE = new Matrix4f();
   private static final Matrix3f BASE_N = new Matrix3f();
   private static final Quaternionf VIEW = new Quaternionf();
   private static final Vector3f V = new Vector3f();

   private SticksView() {
   }

   // ------------------------------------------------------------------------------------------- weapon hooks

   /** How far the held gun has settled onto the sticks (0 free .. 1 rested), smoothed. */
   public static float rest(float partial) {
      return ShootingSticks.ease(Mth.lerp(partial, restO, rest));
   }

   /**
    * The rested hold for a gun whose free hold puts it at {@code (baseX, baseY, baseZ)} (hand space, aim already mixed
    * in) and whose sight line sits {@code axisY} below / eye {@code eyeZ} behind the origin when aimed (both already
    * scaled); {@code side} 1 right-handed, -1 left. Sets {@link #dx}/{@link #dy}/{@link #dz} (add them to the gun's
    * translation) and {@link #tilt} / {@link #yaw} / {@link #roll} (add them to its XP / YP / ZP angles, the last two
    * inside the handedness factor). Aimed, the sight line is exactly where the free hold has it.
    */
   public static void pose(float r, float aim, float side, double baseX, double baseY, double baseZ, double axisY, double eyeZ) {
      if (r <= 0.0F) {
         dx = dy = dz = 0.0;
         tilt = yaw = roll = 0.0F;
         return;
      }
      float free = 1.0F - aim;
      double rx = side * READY_X * free;
      double ry = -axisY - READY_DOWN * free;
      double rz = -eyeZ - READY_FWD * free;
      dx = (rx - baseX) * r;
      dy = (ry - baseY) * r;
      dz = (rz - baseZ) * r;
      tilt = READY_TILT * free * r;
      yaw = READY_YAW * free * r;
      roll = READY_CANT * free * r;
   }

   /**
    * The gun's forend underside this frame, where the yoke goes: the gun's (recoil-free) translation {@code g*} plus the
    * gun-space point {@code (0, cy, cz)} at {@code scale}, turned as the gun is (tilt, yaw, cant incl. the hold's own
    * {@code baseRoll}).
    */
   public static void anchor(double gx, double gy, double gz, double cy, double cz, double scale, float side, float baseRoll) {
      double x = 0.0, y = cy * scale, z = cz * scale;
      double a = Math.toRadians(side * (baseRoll + roll));
      double x1 = x * Math.cos(a) - y * Math.sin(a), y1 = x * Math.sin(a) + y * Math.cos(a);
      double b = Math.toRadians(side * yaw);
      double x2 = x1 * Math.cos(b) + z * Math.sin(b), z2 = -x1 * Math.sin(b) + z * Math.cos(b);
      double c = Math.toRadians(tilt);
      double y3 = y1 * Math.cos(c) - z2 * Math.sin(c), z3 = y1 * Math.sin(c) + z2 * Math.cos(c);
      ax = (float)(gx + x2);
      ay = (float)(gy + y3);
      az = (float)(gz + z3);
      anchorFrame = frame;
      anchored = true;
   }

   /** The world renderer skips these sticks: they are being drawn in the hand pass. */
   public static boolean hides(Entity e) {
      Minecraft mc = Minecraft.getInstance();
      return e == drawn && mc.options.getCameraType().isFirstPerson() && !mc.options.hideGui && mc.getCameraEntity() == mc.player;
   }

   // ------------------------------------------------------------------------------------------- state

   static void tick(Minecraft mc) {
      restO = rest;
      LocalPlayer p = mc.player;
      ShootingSticksEntity s = ShootingSticks.sticks(p);
      if (s != null) {
         drawn = s;
         rest = Math.min(1.0F, rest + 1.0F / ShootingSticks.SETTLE_TICKS);
      } else {
         rest = Math.max(0.0F, rest - 1.0F / 6.0F);
         if (drawn != null && (rest <= 0.0F || drawn.isRemoved() || p == null || drawn.level() != p.level())) {
            drawn = null;
            rest = restO = 0.0F;
            anchored = false;
         }
      }
   }

   static void reset() {
      rest = restO = 0.0F;
      drawn = null;
      anchored = false;
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre e) {
      frame++;
   }

   /** the world field of view as finally rendered (after the scope zoom) */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void fov(ViewportEvent.ComputeFov e) {
      if (e.usedConfiguredFov()) {
         fov = Math.clamp(e.getFOV(), 1.0, 170.0);
      }
   }

   // ------------------------------------------------------------------------------------------- the hand pass

   @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
   public static void hand(RenderHandEvent e) {
      ShootingSticksEntity s = drawn;
      Minecraft mc = Minecraft.getInstance();
      if (s == null || e.getHand() != InteractionHand.MAIN_HAND || mc.player == null || !mc.options.getCameraType().isFirstPerson() || s.isRemoved()
         || mc.getCameraEntity() != mc.player) {
         return;
      }
      float pt = e.getPartialTick();
      float r = rest(pt);
      boolean fresh = anchorFrame == frame;
      if (ShootingSticks.rested(mc.player) && ShootingSticks.supports(mc.player.getMainHandItem()) && !fresh) {
         return; // the gun is behind the scope's eyepiece: so is the yoke
      }
      Camera cam = mc.gameRenderer.getMainCamera();
      float legLen = ShootingSticksRenderer.legLength(s, pt);
      float splay = ShootingSticksRenderer.splay(s, pt);
      float contact = SticksMesh.contact(legLen, splay);
      double cx = Mth.lerp(pt, s.xo, s.getX()) - cam.getPosition().x;
      double cy = Mth.lerp(pt, s.yo, s.getY()) + contact - cam.getPosition().y;
      double cz = Mth.lerp(pt, s.zo, s.getZ()) - cam.getPosition().z;
      VIEW.set(cam.rotation()).conjugate();
      V.set((float)cx, (float)cy, (float)cz);
      VIEW.transform(V);
      // the world projection, expressed in hand space: same depth, x / y scaled so it lands on the same pixel
      float k = HAND_TAN / (float)Math.tan(Math.toRadians(fov) * 0.5);
      float wx = V.x * k, wy = V.y * k, wz = V.z;
      float t = anchored ? r : 0.0F;
      float tx = Mth.lerp(t, wx, ax), ty = Mth.lerp(t, wy, ay), tz = Mth.lerp(t, wz, az);
      float sxy = Mth.lerp(t, k, 1.0F);
      PoseStack.Pose p = e.getPoseStack().last();
      BASE.set(p.pose()).translate(tx, ty, tz).scale(sxy, sxy, 1.0F).rotate(VIEW).translate(0.0F, -contact, 0.0F);
      BASE_N.set(p.normal()).rotate(VIEW);
      SticksMesh.drawSet(BASE, BASE_N, e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(SticksMesh.TEX)), e.getPackedLight(), s.getYRot(),
         splay, legLen, ShootingSticksRenderer.yokeYaw(s, pt), ShootingSticksRenderer.yokeTilt(s, pt), false);
   }
}
