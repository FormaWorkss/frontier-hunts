package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.archery.BowBallistics;
import com.formaworks.frontierhunts.archery.BowConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [bows] The screen half of the bow sight. At full draw it replaces the crosshair (the crosshair marks the arrow's
 * launch line, never where it lands):
 * <ul>
 *   <li>compound: the peep ring around the pin housing (the pins themselves are on the 3D sight), each pin's
 *       distance beside it;</li>
 *   <li>recurve / field recurve: a faint mark on the arrow tip (the point-on distance) and optional gap marks for the
 *       other distances;</li>
 *   <li>crossbow: one small dot per distance;</li>
 *   <li>with a rangefinder in the inventory: the distance to the animal on the sight line.</li>
 * </ul>
 * Everything is placed from the arrow's real flight (BowBallistics) and the world FOV, so a mark held on a target
 * at its distance is a hit. Pixel-exact (drawn in real pixels, not GUI units).
 */
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class BowSightHud {
   private static final ResourceLocation PEEP = FrontierHunts.id("textures/gui/bow_peep.png");
   private static boolean hasRangefinder;
   private static int range = -1;
   private static long rangeAt = Long.MIN_VALUE;

   private BowSightHud() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      bus = EventBusSubscriber.Bus.MOD,
      value = {Dist.CLIENT}
   )
   public static final class Layers {
      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent event) {
         event.registerAbove(VanillaGuiLayers.CROSSHAIR, FrontierHunts.id("bow_sight"), BowSightHud::draw);
      }
   }

   /**
    * How far the full-draw aid is in (0 = none). [archery2] Only once the draw is complete (the client has held the full
    * draw time; the server treats 95 % and up as full, so a release the moment the aid shows is a full-power shot): the
    * aid is calibrated for full draw, so it never shows while a release would still fly short and slow.
    */
   static float shown(Minecraft mc, float pt) {
      LocalPlayer p = mc.player;
      if (p == null || !mc.options.getCameraType().isFirstPerson()) {
         return 0.0F;
      }
      ItemStack stack = p.getMainHandItem();
      BowSight.Style style = BowSight.style(stack);
      if (style == BowSight.Style.NONE || !BowSight.enabled(style) || !FieldBowAim.drawn(stack)) {
         return 0.0F;
      }
      int held = FieldBowAim.heldTicks(p, stack);
      if (held <= 0) {
         return 0.0F;
      }
      float past = (float)held + pt - (float)FieldBowAim.interval(stack);
      return Math.clamp(past / 3.0F, 0.0F, 1.0F);
   }

   @SubscribeEvent
   public static void crosshair(RenderGuiLayerEvent.Pre event) {
      if (event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
         Minecraft mc = Minecraft.getInstance();
         // [1.2.5] a recurve (and every traditional bow) keeps the ordinary crosshair: you aim with it, the arrow
         // leaves along it and drops with the distance, as a bow does - no separate dot somewhere else on the screen
         if (shown(mc, event.getPartialTick().getGameTimeDeltaPartialTick(false)) > 0.5F && mc.player != null
            && BowSight.style(mc.player.getMainHandItem()) != BowSight.Style.TRADITIONAL) {
            event.setCanceled(true);
         }
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         hasRangefinder = false;
         range = -1;
         return;
      }
      long now = mc.level.getGameTime();
      if (now % 20L == 0L || rangeAt == Long.MIN_VALUE) {
         Item finder = ExpeditionContent.item("rangefinder");
         hasRangefinder = finder != null && p.getInventory().hasAnyMatching(s -> s.is(finder));
      }
      rangeAt = now;
   }

   private static void draw(GuiGraphics g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.options.hideGui) {
         return;
      }
      float pt = delta.getGameTimeDeltaPartialTick(false);
      float aim = shown(mc, pt);
      float alpha = aim;
      if (alpha <= 0.0F) {
         return;
      }
      alpha = alpha * alpha * (3.0F - 2.0F * alpha);
      ItemStack stack = p.getMainHandItem();
      BowSight.Style style = BowSight.style(stack);
      BowBallistics.Profile profile = BowSight.profile(stack);
      if (profile == null) {
         return;
      }
      double s = mc.getWindow().getGuiScale();
      // [archery2] the real framebuffer size: the GUI ortho is exactly window / guiScale, while guiWidth/Height are rounded
      // up and put the centre up to (scale - 1) pixels off the world's centre
      double h = mc.getWindow().getHeight();
      double cx = mc.getWindow().getWidth() * 0.5;
      double cy = h * 0.5;
      double pitch = BowSight.viewPitch(p, pt);
      double[] a = BowSight.angles(profile, pitch);
      int[] dist = BowConfig.pins();
      g.pose().pushPose();
      g.pose().scale((float)(1.0 / s), (float)(1.0 / s), 1.0F);
      // label size: as big as the GUI scale allows, never so big that neighbouring labels touch
      double spacing = Double.MAX_VALUE;
      for (int i = 1; i < a.length; i++) {
         if (Double.isFinite(a[i]) && Double.isFinite(a[i - 1])) {
            spacing = Math.min(spacing, Math.abs(BowSight.guiDrop(a[i], h) - BowSight.guiDrop(a[i - 1], h)));
         }
      }
      float label = (float)Math.max(1.0, Math.min(Math.round(s * 0.5), Math.floor(spacing / 10.5)));
      double refAngle;
      double readoutX;
      double readoutY;
      switch (style) {
         case PINS: {
            double[] hs = BowSight.housing(a);
            double yc = cy + BowSight.guiOfHand(hs[0], BowSight.SIGHT_DEPTH, h);
            double r = BowSight.guiOfHand(hs[1], BowSight.SIGHT_DEPTH, h);
            if (BowConfig.on(BowConfig.PEEP, true)) {
               peep(g, cx, yc, r * 1.32 + 2.0 * s, alpha);
            }
            for (int i = 0; i < a.length; i++) {
               if (Double.isFinite(hs[2 + i])) {
                  double y = cy + BowSight.guiOfHand(hs[2 + i], BowSight.SIGHT_DEPTH, h);
                  text(g, mc, Integer.toString(dist[i]), cx + r + 3.0 * s, y - 3.5 * label, label, BowSight.COLORS[i % BowSight.COLORS.length], 0.85F * alpha);
               }
            }
            refAngle = Math.atan(hs[0] / BowSight.SIGHT_DEPTH / ratio()); // the housing centre, in world angle
            readoutX = cx + r + 3.0 * s;
            readoutY = yc + r + 2.0 * s;
            break;
         }
         case DOTS: {
            int dot = (int)Math.max(2, Math.round(s * 0.9));
            for (int i = 0; i < a.length; i++) {
               if (Double.isFinite(a[i])) {
                  double y = cy + BowSight.guiDrop(a[i], h);
                  int c = BowSight.COLORS[i % BowSight.COLORS.length];
                  box(g, cx - dot * 0.5 - 1, y - dot * 0.5 - 1, dot + 2, dot + 2, 0x000000, 0.55F * alpha);
                  box(g, cx - dot * 0.5, y - dot * 0.5, dot, dot, c, 0.95F * alpha);
                  text(g, mc, Integer.toString(dist[i]), cx + dot + 3.0 * s, y - 3.5 * label, label, c, 0.7F * alpha);
               }
            }
            refAngle = a[BowSight.pointOnIndex()];
            readoutX = cx + 6.0 * s;
            readoutY = cy + BowSight.guiDrop(Double.isFinite(refAngle) ? refAngle : 0.0, h) + 7.0 * s;
            break;
         }
         case TRADITIONAL: {
            // [archery2] one dot exactly where the arrow will strike (its real flight through the world, from the eye and
            // rotation the server launches with). Hold it on the spot you want to hit, at any distance.
            refAngle = 0.0;
            readoutX = cx + 8.0 * s;
            readoutY = cy + 7.0 * s;
            aimRange = -1;
            if (false) { // [1.2.5] no impact dot: the crosshair is the aim (it read as a second reticle in the wrong place)
               Vec3 hit = BowAim.impact(p, profile, mc.level.getGameTime());
               double[] at = hit == null ? null : project(mc, hit, cx, cy, h);
               if (at != null) {
                  impactDot(g, at[0], at[1], s, alpha);
                  readoutX = at[0] + 8.0 * s;
                  readoutY = at[1] + 4.0 * s;
                  Entity e = BowAim.impactEntity();
                  aimRange = e instanceof LivingEntity ? (int)Math.round(p.getEyePosition(pt).distanceTo(hit)) : -1;
               } else {
                  aimRange = -1;
               }
            }
            break;
         }
         default:
            g.pose().popPose();
            return;
      }
      if (hasRangefinder && BowConfig.on(BowConfig.RANGE_READOUT, true) && aim > 0.98F) {
         int m = rangeTo(mc, p, pt, Double.isFinite(refAngle) ? refAngle : 0.0);
         if (m > 0) {
            float big = (float)Math.max(1.0, Math.round(s * 0.6));
            text(g, mc, m + " m", readoutX, readoutY, big, 0xBFE6C0, 0.9F * alpha);
         }
      }
      g.pose().popPose();
   }

   private static int aimRange = -1;

   /**
    * [archery2] Real-pixel screen position of world point {@code w} through this frame's camera and world FOV (the same
    * projection the world was drawn with), or null when it is behind the view.
    */
   static double[] project(Minecraft mc, Vec3 w, double cx, double cy, double h) {
      net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
      Vec3 rel = w.subtract(cam.getPosition());
      double[] p = com.formaworks.frontierhunts.archery.SightOptics.project(rel.x, rel.y, rel.z, cam.getYRot(), cam.getXRot(), BowSight.worldFov(), cx * 2.0, h);
      return p;
   }

   /** [archery2] The impact dot: a small bright point with a dark rim and a faint ring, readable on snow and in shade. */
   private static void impactDot(GuiGraphics g, double x, double y, double s, float alpha) {
      double u = Math.max(1.0, Math.round(s * 0.5));
      double px = Math.round(x);
      double py = Math.round(y);
      // faint ring (4 short arcs as boxes) to find the dot quickly against busy backgrounds
      double r = 5.0 * u;
      box(g, px - r, py - 0.5 * u, 2.0 * u, u, 0x000000, 0.28F * alpha);
      box(g, px + r - 2.0 * u, py - 0.5 * u, 2.0 * u, u, 0x000000, 0.28F * alpha);
      box(g, px - 0.5 * u, py - r, u, 2.0 * u, 0x000000, 0.28F * alpha);
      box(g, px - 0.5 * u, py + r - 2.0 * u, u, 2.0 * u, 0x000000, 0.28F * alpha);
      box(g, px - r + 0.5 * u, py - 0.5 * u, 1.2 * u, u, 0xF2EEDC, 0.45F * alpha);
      box(g, px + r - 1.7 * u, py - 0.5 * u, 1.2 * u, u, 0xF2EEDC, 0.45F * alpha);
      box(g, px - 0.5 * u, py - r + 0.5 * u, u, 1.2 * u, 0xF2EEDC, 0.45F * alpha);
      box(g, px - 0.5 * u, py + r - 1.7 * u, u, 1.2 * u, 0xF2EEDC, 0.45F * alpha);
      // the dot itself, centred on the exact impact pixel
      box(g, px - 1.5 * u, py - 1.5 * u, 3.0 * u, 3.0 * u, 0x000000, 0.6F * alpha);
      box(g, px - u, py - u, 2.0 * u, 2.0 * u, 0xFFE9A8, 0.95F * alpha);
   }

   private static double ratio() {
      return com.formaworks.frontierhunts.archery.SightOptics.ratio(BowSight.worldFov(), BowSight.handFov());
   }

   private static long rangeFrame = Long.MIN_VALUE;

   /** Laser range (m) to the living thing on the sight line, refreshed every other tick; -1 if none within 150 m. */
   private static int rangeTo(Minecraft mc, LocalPlayer p, float pt, double below) {
      long now = mc.level.getGameTime();
      if (rangeFrame != Long.MIN_VALUE && now - rangeFrame < 2L && now >= rangeFrame) {
         return range;
      }
      rangeFrame = now;
      Vec3 eye = p.getEyePosition(pt);
      Vec3 dir = Vec3.directionFromRotation((float)(p.getViewXRot(pt) + Math.toDegrees(below)), p.getViewYRot(pt));
      double reach = 150.0;
      Vec3 end = eye.add(dir.scale(reach));
      BlockHitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
      if (block.getType() != HitResult.Type.MISS) {
         end = block.getLocation();
      }
      AABB box = p.getBoundingBox().expandTowards(dir.scale(eye.distanceTo(end))).inflate(1.0);
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(
         p, eye, end, box, (Entity e) -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable(), reach * reach
      );
      range = hit == null ? -1 : (int)Math.round(eye.distanceTo(hit.getLocation()));
      return range;
   }

   private static void peep(GuiGraphics g, double cx, double cy, double innerRadius, float alpha) {
      double half = innerRadius / 0.5; // the texture's aperture edge sits at half its radius
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor(1.0F, 1.0F, 1.0F, 0.92F * alpha);
      g.pose().pushPose();
      g.pose().translate((float)(cx - half), (float)(cy - half), 0.0F);
      g.pose().scale((float)(half * 2.0 / 256.0), (float)(half * 2.0 / 256.0), 1.0F);
      g.blit(PEEP, 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
      g.pose().popPose();
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
   }

   private static void box(GuiGraphics g, double x, double y, double w, double hgt, int rgb, float alpha) {
      int a = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
      if (a == 0) {
         return;
      }
      g.pose().pushPose();
      g.pose().translate((float)x, (float)y, 0.0F);
      g.fill(0, 0, (int)Math.max(1, Math.round(w)), (int)Math.max(1, Math.round(hgt)), a << 24 | rgb & 16777215);
      g.pose().popPose();
   }

   private static void text(GuiGraphics g, Minecraft mc, String s, double x, double y, float scale, int rgb, float alpha) {
      int a = Math.clamp(Math.round(alpha * 255.0F), 0, 255);
      if (a < 8) {
         return;
      }
      g.pose().pushPose();
      g.pose().translate((float)Math.round(x), (float)Math.round(y), 0.0F);
      g.pose().scale(scale, scale, 1.0F);
      g.drawString(mc.font, s, 0, 0, a << 24 | rgb & 16777215, true);
      g.pose().popPose();
   }

   /** GUI units below the screen centre to keep the archery steadiness bar clear of the sight. */
   static int steadinessOffset(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null) {
         return 14;
      }
      BowSight.Style style = BowSight.style(p.getMainHandItem());
      return style != BowSight.Style.NONE && BowSight.enabled(style) ? 58 : 14;
   }
}
