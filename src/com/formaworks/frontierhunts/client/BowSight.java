package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.archery.BowConfig;
import com.formaworks.frontierhunts.archery.SightOptics;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [bows] The sight picture of a drawn bow, shared by the first-person model ({@link FieldBowPresentation},
 * {@link FieldBows}) and the HUD ({@link BowSightHud}) so both always agree.
 *
 * <p>Pin angles come from {@link BowBallistics} (the server's own arrow flight) for the configured distances at the
 * bow's current angle. The world is drawn with the player's FOV and the hand with its own, so anything modelled in
 * the hand pass is placed through {@link SightOptics} to cover the exact world spot. Both FOVs are read from the
 * final ComputeFov result each frame (after every other mod's zoom).
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class BowSight {
   enum Style {
      NONE,
      /** compound: pins in a housing, peep */
      PINS,
      /** recurve / field recurve: [archery2] the impact dot (where the arrow will strike) */
      TRADITIONAL,
      /** crossbow: range dots */
      DOTS
   }

   /** Compound sight in bow-local model coordinates (first person only). */
   record Pins(double x, double y, double r, double[] pins, int[] colors) {
   }

   /** Fibre colours, top pin first: green, yellow, red, blue (the usual multi-pin order). */
   static final int[] COLORS = new int[]{6476910, 14999626, 14704700, 8374527};
   /** View depth of the sight housing with the bow at anchor (bow 0.62 out, housing 0.205 ahead of the riser). */
   static final double SIGHT_DEPTH = 0.825;
   static final double ANCHOR_Z = -0.62;

   private static double worldFov = 70.0;
   private static double handFov = 70.0;
   private static Pins modelPins;
   /** [archery2] Crossbow reflex-sight window for the first-person model {centreDrop, radius} (hand space); null elsewhere. */
   private static double[] crossbowSight;

   private BowSight() {
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void fov(ViewportEvent.ComputeFov event) {
      double f = event.getFOV();
      if (!Double.isFinite(f) || f <= 0.0) {
         return;
      }
      if (event.usedConfiguredFov()) {
         worldFov = f;
      } else {
         handFov = f;
      }
   }

   static double worldFov() {
      return worldFov;
   }

   static double handFov() {
      return handFov;
   }

   static Pins modelPins() {
      return modelPins;
   }

   static void modelPins(Pins pins) {
      modelPins = pins;
   }

   static double[] crossbowSight() {
      return crossbowSight;
   }

   static void crossbowSight(double[] sight) {
      crossbowSight = sight;
   }

   static BowBallistics.Profile profile(ItemStack stack) {
      if (stack.is((Item)HuntContent.FIELD_BOW.get())) {
         return BowBallistics.FIELD_RECURVE;
      }
      if (stack.getItem() instanceof ExpeditionWeapon w) {
         return switch (w.weapon) {
            case RECURVE_BOW -> BowBallistics.RECURVE;
            case COMPOUND_BOW -> BowBallistics.COMPOUND;
            case CROSSBOW -> BowBallistics.CROSSBOW;
            default -> null;
         };
      }
      return null;
   }

   static Style style(ItemStack stack) {
      if (stack.is((Item)HuntContent.FIELD_BOW.get())) {
         return Style.TRADITIONAL;
      }
      if (stack.getItem() instanceof ExpeditionWeapon w) {
         return switch (w.weapon) {
            case RECURVE_BOW -> Style.TRADITIONAL;
            case COMPOUND_BOW -> Style.PINS;
            case CROSSBOW -> Style.DOTS;
            default -> Style.NONE;
         };
      }
      return Style.NONE;
   }

   /** Whether the aid for this style is switched on (else the vanilla crosshair stays). */
   static boolean enabled(Style s) {
      return switch (s) {
         case PINS, DOTS -> BowConfig.on(BowConfig.PIN_SIGHT, true);
         case TRADITIONAL -> BowConfig.on(BowConfig.TIP_REFERENCE, true); // [archery2] the impact dot
         default -> false;
      };
   }

   /** The bow's angle (radians, up positive) this frame. */
   static double viewPitch(Player p, float partialTick) {
      return -Math.toRadians(p.getViewXRot(partialTick));
   }

   /** Pin angles (radians below the view centre) for the configured distances; NaN for any out of reach. */
   static double[] angles(BowBallistics.Profile p, double viewPitch) {
      int[] d = BowConfig.pins();
      double[] a = new double[d.length];
      for (int i = 0; i < d.length; i++) {
         a[i] = BowBallistics.pinAngleAt(p, d[i], viewPitch);
      }
      return a;
   }

   /** The traditional point-on angle: the middle mark (the arrow tip sits on it). */
   static double pointOn(BowBallistics.Profile p, double viewPitch) {
      int[] d = BowConfig.pins();
      double a = BowBallistics.pinAngleAt(p, d[(d.length - 1) / 2], viewPitch);
      return Double.isFinite(a) ? a : Math.toRadians(3.0);
   }

   static int pointOnIndex() {
      return (BowConfig.pins().length - 1) / 2;
   }

   /** Camera-space drop (down positive) at depth {@code depth} for a hand-pass vertex covering world angle {@code angle}. */
   static double handDrop(double angle, double depth) {
      return SightOptics.handDrop(angle, depth, worldFov, handFov);
   }

   /** GUI units below the screen centre for world angle {@code angle}. */
   static double guiDrop(double angle, double guiHeight) {
      return SightOptics.screenDrop(angle, worldFov, guiHeight);
   }

   /** GUI units for a hand-space length {@code y} at view depth {@code depth}. */
   static double guiOfHand(double y, double depth, double guiHeight) {
      return y / depth / Math.tan(Math.toRadians(Math.max(1.0, Math.min(170.0, handFov))) * 0.5) * guiHeight * 0.5;
   }

   /**
    * Compound housing in camera space at {@link #SIGHT_DEPTH}: {centreDrop, radius, pinDrop...} (drops positive
    * down). Out-of-reach pins are left out of the housing size and drawn nowhere.
    */
   static double[] housing(double[] angles) {
      double top = Double.NaN;
      double bottom = Double.NaN;
      double[] out = new double[2 + angles.length];
      for (int i = 0; i < angles.length; i++) {
         double y = Double.isFinite(angles[i]) ? handDrop(angles[i], SIGHT_DEPTH) : Double.NaN;
         out[2 + i] = y;
         if (Double.isFinite(y)) {
            top = Double.isNaN(top) ? y : Math.min(top, y);
            bottom = Double.isNaN(bottom) ? y : Math.max(bottom, y);
         }
      }
      if (Double.isNaN(top)) {
         top = bottom = handDrop(Math.toRadians(2.0), SIGHT_DEPTH);
      }
      out[0] = 0.5 * (top + bottom);
      out[1] = Math.max(0.026, 0.5 * (bottom - top) + 0.012);
      return out;
   }

   /** Distance (m) the compound pins are set for, by pin index. */
   static int pinDistance(int i) {
      int[] d = BowConfig.pins();
      return d[Math.clamp(i, 0, d.length - 1)];
   }

   static boolean firstPerson() {
      Minecraft mc = Minecraft.getInstance();
      return mc.options.getCameraType().isFirstPerson();
   }

   static boolean isSighted(Weapon w) {
      return w == Weapon.COMPOUND_BOW;
   }
}
