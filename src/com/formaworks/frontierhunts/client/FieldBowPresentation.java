package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov;

/**
 * First-person bows and the crossbow.
 *
 * <p>[archery2] Every hand-held bow is now set up for a right-handed archer: the bow in the left fist, the arrow on the
 * shelf against the LEFT side of the riser, the riser and upper limb to the right of the sight line. At anchor:
 * <ul>
 *   <li>traditional bows (Field Recurve, Recurve): canted 11 degrees with the top limb tipped RIGHT (out of the sight
 *       line), the arrow running straight out under the eye; the aiming reference is the impact dot on the HUD
 *       ({@link BowAim}, {@link BowSightHud}) - the arrow's real landing point at any distance;</li>
 *   <li>compound: held level, the housing and pins placed where the arrow really lands (see {@link BowSight});</li>
 *   <li>crossbow: shouldered - stock to the cheek, the eye behind a reflex sight whose window frames the range dots,
 *       rail, limbs and both hands below the sight line, nothing over the centre.</li>
 * </ul>
 * The model is mirrored from the shared bow meshes (which are built for the other hand) with a single negative scale,
 * so third-person and display models are unchanged. The bow is locked to the view at anchor (hand-lag cancelled) and
 * reaches the anchor exactly at full draw. All placement is pure ({@link #anchor}, {@link #place}, {@link #draw}) so
 * the offline view harness (tools/archery2) renders exactly what the game does.
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FieldBowPresentation {
   private static final double READY_X = 0.28;
   private static final double READY_Y = -0.24;
   private static final double READY_Z = -1.02;
   /** [archery2] Cant of a traditional bow at anchor, degrees about the view axis: negative = top limb tipped right. */
   static final double TRAD_CANT = -11.0;
   /** Broadhead length ahead of the shaft (average of the heads). */
   private static final double HEAD = 0.06;
   /** [archery2] How far below the view centre the traditional arrow's tip sits at anchor (cosmetic: the dot aims). */
   static final double TRAD_TIP_ANGLE = Math.toRadians(7.5);

   /** [archery2] Crossbow reflex sight: optic axis height above the bow origin and its station along the rail (model). */
   static final double XB_SIGHT_Y = 0.075;
   static final double XB_SIGHT_Z = 0.1;
   /** [archery2] Crossbow bow-origin depth at the shoulder (eye relief to the sight = XB_DEPTH - XB_SIGHT_Z). */
   static final double XB_DEPTH = 0.47;
   /** [archery2] Bolt / string height on the crossbow rail (the rail's top is at 0.014). */
   static final double XB_BOLT_Y = 0.0185;

   private FieldBowPresentation() {
   }

   /** [archery2] Mirrored for the right-handed hold (everything but the symmetric crossbow and the spear). */
   static boolean mirrored(Weapon w) {
      return w != Weapon.CROSSBOW && w != Weapon.HUNTING_SPEAR && w != Weapon.BOWFISHING_BOW;
   }

   /** [archery2] Arrow line, model coordinates before the mirror: x out from the riser centre, y above the bow origin. */
   static double arrowX(Weapon w, boolean fieldBow) {
      if (fieldBow) {
         return FieldPrimitiveBow.ARROW_X;
      }
      return w == Weapon.CROSSBOW ? 0.0 : 0.015;
   }

   static double arrowY(Weapon w, boolean fieldBow) {
      if (fieldBow) {
         return FieldPrimitiveBow.ARROW_Y;
      }
      return w == Weapon.CROSSBOW ? XB_BOLT_Y : 0.05;
   }

   static double nockZ(Weapon w, boolean fieldBow, float draw) {
      if (fieldBow) {
         return FieldPrimitiveBow.nockZ(draw);
      }
      return w == Weapon.CROSSBOW ? crossbowLatchZ(draw) : FieldBows.nockZ(draw);
   }

   /** Crossbow string / bolt nock position along the rail for a cocking progress. */
   static double crossbowLatchZ(float draw) {
      return -0.1 + (double)draw * 0.37;
   }

   /** [archery2] Crossbow sight window in model coordinates for this view: {centreY, radius}. */
   static double[] crossbowSight(double pitch) {
      double depth = XB_DEPTH - XB_SIGHT_Z;
      double[] a = BowSight.angles(BowBallistics.CROSSBOW, pitch);
      double top = Double.NaN;
      double bottom = Double.NaN;
      for (double v : a) {
         if (Double.isFinite(v)) {
            double y = BowSight.handDrop(v, depth);
            top = Double.isNaN(top) ? y : Math.min(top, y);
            bottom = Double.isNaN(bottom) ? y : Math.max(bottom, y);
         }
      }
      if (Double.isNaN(top)) {
         top = bottom = BowSight.handDrop(Math.toRadians(1.5), depth);
      }
      // the window includes the view centre (the launch line) as well as every dot
      top = Math.min(top, 0.0);
      double centre = 0.5 * (top + bottom);
      double radius = Math.max(0.017, 0.5 * (bottom - top) + 0.0085);
      return new double[]{centre, radius};
   }

   /**
    * Anchor {x, y, z, roll} of the bow origin in view space for a fully drawn bow. Traditional bows put the (mirrored)
    * arrow straight below the view centre with its tip {@link #TRAD_TIP_ANGLE} down; the compound puts the arrow just
    * under its pin housing; the crossbow puts its sight window around the range dots.
    */
   static double[] anchor(Weapon weapon, boolean fieldBow, float draw, double pitch) {
      if (weapon == Weapon.CROSSBOW) {
         double[] sight = crossbowSight(pitch);
         return new double[]{0.0, -XB_SIGHT_Y - sight[0], -XB_DEPTH, 0.0};
      }
      if (weapon == Weapon.HUNTING_SPEAR || weapon == Weapon.BOWFISHING_BOW) {
         return weapon == Weapon.BOWFISHING_BOW ? new double[]{-0.015, -0.119, -0.62, -2.0} : new double[]{-0.015, -0.05, -0.62, -2.0};
      }
      double ax = -arrowX(weapon, fieldBow); // mirrored: the arrow on the left of the riser
      double ay = arrowY(weapon, fieldBow);
      if (weapon == Weapon.COMPOUND_BOW) {
         double[] a = BowSight.angles(BowBallistics.COMPOUND, pitch);
         double last = Math.toRadians(3.0);
         for (double v : a) {
            if (Double.isFinite(v)) {
               last = Math.max(last, v);
            }
         }
         // [archery2] the arrow sits ~9 cm under the eye at anchor (it used to ride just under the pins, with the bow hand
         // right where the 30-40 m pins look)
         double arrow = Math.max(last + Math.toRadians(1.4), TRAD_TIP_ANGLE);
         double depth = -(BowSight.ANCHOR_Z + FieldBows.nockZ(draw) - 0.394 - 0.38 - HEAD);
         double yA = -BowSight.handDrop(arrow, Math.max(0.3, depth));
         return new double[]{-ax, yA - ay, BowSight.ANCHOR_Z, 0.0};
      }
      double nock = nockZ(weapon, fieldBow, draw);
      double depth = -(BowSight.ANCHOR_Z + nock - 0.394 - 0.38 - HEAD);
      double yA = -BowSight.handDrop(TRAD_TIP_ANGLE, Math.max(0.3, depth));
      double r = Math.toRadians(TRAD_CANT);
      double rx = ax * Math.cos(r) - ay * Math.sin(r);
      double ry = ax * Math.sin(r) + ay * Math.cos(r);
      return new double[]{-rx, yA - ry, BowSight.ANCHOR_Z, TRAD_CANT};
   }

   /**
    * Ready pose blended into the anchor by {@code aim}. {@code lagX/lagY} is vanilla's hand-lag rotation, cancelled at
    * anchor so the bow (and its sight) stays locked to the view like a real anchored bow.
    */
   static void place(PoseStack pose, double[] a, float aim, float equip, float lagX, float lagY) {
      if (aim > 0.0F) {
         pose.mulPose(Axis.YP.rotationDegrees(-lagY * aim));
         pose.mulPose(Axis.XP.rotationDegrees(-lagX * aim));
      }
      pose.translate(
         Mth.lerp((double)aim, READY_X, a[0]),
         Mth.lerp((double)aim, READY_Y, a[1]) - (double)equip * 0.5,
         Mth.lerp((double)aim, READY_Z, a[2])
      );
      pose.mulPose(Axis.ZP.rotationDegrees((float)Mth.lerp((double)aim, -5.0, a[3])));
   }

   /** [bows] Compound pins in bow-local model coordinates for this anchor (first person). */
   private static BowSight.Pins pins(double[] anchor, double pitch) {
      double[] a = BowSight.angles(BowBallistics.COMPOUND, pitch);
      double[] h = BowSight.housing(a);
      int n = 0;
      for (int i = 0; i < a.length; i++) {
         if (Double.isFinite(h[2 + i])) {
            n++;
         }
      }
      double[] ys = new double[n];
      int[] colors = new int[n];
      int k = 0;
      for (int i = 0; i < a.length; i++) {
         if (Double.isFinite(h[2 + i])) {
            ys[k] = -h[2 + i] - anchor[1];
            colors[k] = BowSight.COLORS[i % BowSight.COLORS.length];
            k++;
         }
      }
      // [archery2] model x of the arrow line (the housing is centred on it; the mirror puts it on the view axis)
      return new BowSight.Pins(arrowX(Weapon.COMPOUND_BOW, false), -h[0] - anchor[1], h[1], ys, colors);
   }

   /**
    * [archery2] Draw a first-person bow (model, nocked arrow, hands) in the frame {@link #place} set up. Pure: no
    * Minecraft instance needed except for the hands (skinned player arms). {@code recoil} is the release kick (0 = none).
    */
   static void draw(
      PoseStack pose, MultiBufferSource buffers, VertexConsumer vc, int light, Weapon weapon, boolean fieldBow, float draw, float aim, double pitch,
      double[] anchor, ArrowSupply.Shot shot, boolean arrowShown, double recoil
   ) {
      pose.pushPose();
      pose.translate(0.0, recoil * 0.004, recoil * 0.012);
      pose.mulPose(Axis.ZP.rotationDegrees((float)(recoil * 0.8)));
      if (weapon == Weapon.HUNTING_SPEAR) {
         pose.mulPose(Axis.XP.rotationDegrees(-12.0F - draw * 60.0F));
      }
      boolean mirror = mirrored(weapon);
      pose.pushPose();
      if (mirror) {
         pose.scale(-1.0F, 1.0F, 1.0F);
      }
      if (fieldBow) {
         FieldPrimitiveBow.draw(pose, vc, light, draw, aim);
      } else if (weapon == Weapon.CROSSBOW) {
         BowSight.crossbowSight(crossbowSight(pitch));
         try {
            FieldEquipmentModel.bow(weapon, pose, vc, light, draw, aim);
         } finally {
            BowSight.crossbowSight(null);
         }
      } else {
         BowSight.modelPins(weapon == Weapon.COMPOUND_BOW ? pins(anchor, pitch) : null);
         try {
            FieldEquipmentModel.bow(weapon, pose, vc, light, draw, aim);
         } finally {
            BowSight.modelPins(null);
         }
      }
      if (arrowShown && weapon != Weapon.HUNTING_SPEAR) {
         boolean bolt = weapon == Weapon.CROSSBOW;
         FieldArrowModel.nocked(
            pose, vc, light, arrowX(weapon, fieldBow), arrowY(weapon, fieldBow), nockZ(weapon, fieldBow, draw), bolt, shot,
            bolt ? 9.0 : FieldBows.cut(aim)
         );
      }
      pose.popPose();
      FieldBowHands.draw(weapon, fieldBow, draw, aim, pose, buffers, light);
      pose.popPose();
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || var0.getHand() != InteractionHand.MAIN_HAND) {
         return;
      }
      LocalPlayer player = mc.player;
      ItemStack stack = player.getMainHandItem();
      boolean fieldBow = stack.is((Item)HuntContent.FIELD_BOW.get());
      Weapon weapon;
      if (fieldBow) {
         weapon = Weapon.RECURVE_BOW;
      } else if (stack.getItem() instanceof ExpeditionWeapon w && w.weapon.bow && w.weapon != Weapon.BOWFISHING_BOW) {
         weapon = w.weapon;
      } else {
         return;
      }
      var0.setCanceled(true);
      float pt = var0.getPartialTick();
      double pitch = BowSight.viewPitch(player, pt);
      float draw = FieldBowAim.drawOf(player, stack, pt);
      float aim = weapon == Weapon.HUNTING_SPEAR ? 0.0F : FieldBowAim.aim(draw);
      float xb = Mth.lerp(pt, player.xBobO, player.xBob);
      float yb = Mth.lerp(pt, player.yBobO, player.yBob);
      float lagX = (player.getViewXRot(pt) - xb) * 0.1F;
      float lagY = (player.getViewYRot(pt) - yb) * 0.1F;
      double[] anchor = anchor(weapon, fieldBow, draw, pitch);
      boolean arrowShown;
      double recoil = 0.0;
      if (fieldBow) {
         arrowShown = !player.getCooldowns().isOnCooldown(stack.getItem());
      } else {
         double now = (double)((float)mc.level.getGameTime() + pt);
         long shotAt = ExpeditionWeapon.data(stack).getLong("shot_at");
         double since = shotAt == 0L ? 100.0 : Math.max(0.0, now - (double)shotAt);
         recoil = shotAt == 0L ? 0.0 : Math.exp(-since * 0.6) * Math.sin(since * 2.8);
         arrowShown = since >= 5.0;
      }
      PoseStack pose = var0.getPoseStack();
      pose.pushPose();
      place(pose, anchor, aim, var0.getEquipProgress(), lagX, lagY);
      FilteredFieldTexture.ensure(FieldMaterials.ATLAS);
      MultiBufferSource buffers = var0.getMultiBufferSource();
      draw(pose, buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(FieldMaterials.ATLAS)), var0.getPackedLight(), weapon, fieldBow, draw, aim, pitch, anchor, FieldArrowModel.shotFor(player), arrowShown, recoil);
      pose.popPose();
   }

   @SubscribeEvent
   public static void fov(ComputeFov var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.options.getCameraType().isFirstPerson()) {
         float var2 = FieldBowAim.aimFactor(var1.player, (float)var0.getPartialTick());
         if (var2 > 0.0F) {
            var0.setFOV(var0.getFOV() / (1.0 + 0.17 * (double)var2));
         }
      }
   }
}
