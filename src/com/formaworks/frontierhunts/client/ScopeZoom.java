package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.client.trailcam.Darkroom;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.prone.Prone;
import com.formaworks.frontierhunts.rifle.RidgelineOptics;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.workshop.OpticUpgrade;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * [scope] Variable-power riflescopes, purely client-side presentation.
 *
 * <ul>
 *   <li>Every scoped gun (expedition / field guns and the Ridgeline) gets its optic's power from {@link ScopePower};
 *       the long-range scopes are variable and the mouse wheel turns the power ring while aiming (the wheel is
 *       consumed then, so the hotbar never changes under a raised scope). Fixed optics keep scrolling the hotbar.</li>
 *   <li>The zoom itself is applied in {@code ComputeFov} (HIGH priority), frame-smooth and without vanilla's 0.1
 *       FOV-modifier floor (25x needs a ~3 degree view). The legacy tick-smoothed FOV-modifier zoom of
 *       ExpeditionClient / RifleClient is divided back out at LOWEST priority, so it is never applied twice.
 *       Later absolute FOV owners (trail-cam darkroom NORMAL, kill cam LOWEST) still win, and the LOWEST
 *       recorders (WildlifeFov, ViewProjection, WeatherClient, KillCamClient's idle playerFov) see the scoped
 *       FOV, so the animal LOD keeps full detail through the glass.</li>
 *   <li>Power is remembered per weapon + optic in {@code config/frontierhunts-scope-power.properties}.</li>
 *   <li>Hold sway: a constant angular breathing / drift wobble while scoped, so on screen it grows in proportion
 *       to the power exactly as through a real scope. It moves the player's real look direction, so the reticle
 *       always shows where the shot goes (no hidden camera offset).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class ScopeZoom {
   private static final Logger LOGGER = LoggerFactory.getLogger("frontierhunts/scope");
   private static final ResourceLocation CLICK = FrontierHunts.id("optic.zoom_click");
   private static final ResourceLocation STOP = FrontierHunts.id("optic.zoom_stop");

   private static ScopePower.Profile profile;
   private static String key = "";
   private static float target = 1.0F;
   private static float shown = 1.0F;
   private static long lastNanos;
   private static double wheel;
   private static long shownAt = Long.MIN_VALUE;
   private static long clickAt;
   private static long stopAt;
   private static boolean engagedLast;
   private static long engagedAt;
   /** [rifle] The hold-sway curve and the part of it already applied to the look direction (degrees). */
   private static final HoldSway SWAY = new HoldSway(System.nanoTime());
   private static double swayPitch;
   private static double swayYaw;
   private static int frame;
   private static int readoutFrame = -1;
   private static int refreshFrame = -1;
   private static ItemStack refreshStack;
   private static Kind refreshKind = Kind.NONE;

   private static final Map<String, Float> MEMORY = new HashMap<>();
   private static boolean loaded;
   private static boolean dirty;
   private static long dirtyAt;

   private ScopeZoom() {
   }

   // ------------------------------------------------------------------------------------------- what is held

   private enum Kind { NONE, FIELD, RIDGELINE }

   private static Kind kind(Minecraft mc) {
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         return Kind.NONE;
      }
      ItemStack stack = p.getMainHandItem();
      if (stack.getItem() instanceof RifleItem) {
         return Kind.RIDGELINE;
      }
      if (stack.getItem() instanceof ExpeditionWeapon w && !w.weapon.bow) {
         return Kind.FIELD;
      }
      return Kind.NONE;
   }

   private static ScopePower.Profile resolve(Minecraft mc, Kind kind) {
      ItemStack stack = mc.player.getMainHandItem();
      if (kind == Kind.RIDGELINE) {
         // [rifle] the Ridgeline takes every field optic now; its factory scope keeps its own 3-9x profile
         String sight = RidgelineOptics.sight(stack);
         return sight.equals(RidgelineOptics.STOCK) ? ScopePower.RIDGELINE : ScopePower.field(sight, false);
      }
      ExpeditionWeapon w = (ExpeditionWeapon)stack.getItem();
      return ScopePower.field(AttachmentSpec.installedSight(stack), w.weapon == Weapon.TRANQUILIZER_RIFLE);
   }

   /** Picks up the held optic; a change of weapon or optic snaps to that optic's remembered power. */
   private static Kind refresh(Minecraft mc) {
      ItemStack held = mc.player == null ? null : mc.player.getMainHandItem();
      if (refreshFrame == frame && held == refreshStack) {
         return refreshKind; // already resolved this frame for this very stack (hot path: bob mixin, turn, overlay)
      }
      refreshFrame = frame;
      refreshStack = held;
      refreshKind = refreshNow(mc);
      return refreshKind;
   }

   private static Kind refreshNow(Minecraft mc) {
      Kind kind = kind(mc);
      if (kind == Kind.NONE) {
         profile = null;
         key = "";
         return kind;
      }
      ScopePower.Profile p = resolve(mc, kind);
      String k = BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()) + "|" + p.id();
      if (!k.equals(key) || profile == null) {
         load();
         profile = p;
         key = k;
         Float remembered = MEMORY.get(k);
         target = p.clamp(remembered == null ? p.initial() : remembered);
         shown = target;
         wheel = 0.0;
      }
      return kind;
   }

   /** Raise progress 0..1 of the held gun (eased), as the existing first-person code animates it. */
   private static float progress(Kind kind, float pt) {
      return switch (kind) {
         case FIELD -> FieldWeaponFirstPerson.aim(pt);
         case RIDGELINE -> {
            float a = Math.clamp(RifleClient.aim(pt), 0.0F, 1.0F);
            yield a * a * (3.0F - 2.0F * a);
         }
         case NONE -> 0.0F;
      };
   }

   private static boolean cinematicOwnsView() {
      return KillCamClient.active() || Darkroom.active();
   }

   /** Aim held with the gun (mostly) up, first person, no screen: the wheel belongs to the power ring. */
   private static boolean engaged(Minecraft mc, Kind kind) {
      if (kind == Kind.NONE || mc.screen != null || !mc.options.getCameraType().isFirstPerson() || cinematicOwnsView()) {
         return false;
      }
      LocalPlayer p = mc.player;
      if (p == null || !p.isAlive() || p.isSpectator()) {
         return false;
      }
      boolean held = kind == Kind.FIELD ? ExpeditionClient.aiming() : RifleClient.AIM.isDown();
      return held && progress(kind, 1.0F) > 0.2F;
   }

   // ------------------------------------------------------------------------------------------- public API

   /** The scope power the view currently shows for the held gun (eased), or 0 when no scoped gun is held. */
   public static double power() {
      Minecraft mc = Minecraft.getInstance();
      if (kind(mc) == Kind.NONE) {
         return 0.0;
      }
      refresh(mc);
      return shown;
   }

   /** The held optic, or null. */
   static ScopePower.Profile profile() {
      Minecraft mc = Minecraft.getInstance();
      return refresh(mc) == Kind.NONE ? null : profile;
   }

   /** Turn-rate multiplier for the held optic at its current power (proportional for variable optics). */
   static double turnScale() {
      return ScopePower.turnScale(profile(), power());
   }

   /** {@code fov} as seen through the raised scope right now (used by the kill cam to return into the scope). */
   public static double scopedFov(double fov) {
      return ScopePower.scopedFov(fov, effective(Minecraft.getInstance(), 1.0F));
   }

   private static double effective(Minecraft mc, float pt) {
      if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
         return 1.0;
      }
      Kind kind = refresh(mc);
      if (kind == Kind.NONE) {
         return 1.0;
      }
      return ScopePower.raised(shown, progress(kind, pt));
   }

   // ------------------------------------------------------------------------------------------- zoom (FOV)

   /**
    * Removes the legacy FOV-modifier zoom of ExpeditionClient.fov / RifleClient.fov (same inputs, same formula,
    * same tick), because the zoom is now applied in {@link #fov}. Third-person Ridgeline zoom is left alone.
    */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void stripLegacyZoom(ComputeFovModifierEvent event) {
      Minecraft mc = Minecraft.getInstance();
      if (event.getPlayer() != mc.player || mc.player == null || !mc.options.getCameraType().isFirstPerson()) {
         return;
      }
      ItemStack stack = mc.player.getMainHandItem();
      float legacy = 1.0F;
      if (stack.getItem() instanceof ExpeditionWeapon w && !w.weapon.bow) {
         float mag = AttachmentSpec.magnification(stack, w.weapon);
         double r = Math.toRadians((double)mc.options.fov().get().intValue());
         float f = (float)(2.0 * Math.atan(Math.tan(r / 2.0) / (double)mag) / r);
         legacy = 1.0F + (f - 1.0F) * FieldWeaponFirstPerson.aim(1.0F);
      } else if (stack.getItem() instanceof RifleItem) {
         legacy = 1.0F - (OpticUpgrade.fitted(stack) ? 0.715F : 0.62F) * RifleClient.aim(1.0F);
      }
      if (legacy > 1.0E-3F && legacy != 1.0F) {
         event.setNewFovModifier(event.getNewFovModifier() / legacy);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void fov(ViewportEvent.ComputeFov event) {
      if (!event.usedConfiguredFov()) {
         return;
      }
      double m = effective(Minecraft.getInstance(), (float)event.getPartialTick());
      if (m > 1.0001) {
         event.setFOV(ScopePower.scopedFov(event.getFOV(), m));
      }
   }

   // ------------------------------------------------------------------------------------------- power ring

   @SubscribeEvent
   public static void scroll(InputEvent.MouseScrollingEvent event) {
      Minecraft mc = Minecraft.getInstance();
      refreshFrame = -1;
      Kind kind = refresh(mc);
      if (!engaged(mc, kind) || profile == null || !profile.variable()) {
         wheel = 0.0;
         return;
      }
      event.setCanceled(true);
      double d = event.getScrollDeltaY();
      if (!Double.isFinite(d) || d == 0.0) {
         return;
      }
      if (Math.signum(d) != Math.signum(wheel)) {
         wheel = 0.0;
      }
      wheel += d;
      while (wheel >= 1.0) {
         wheel -= 1.0;
         turn(1);
      }
      while (wheel <= -1.0) {
         wheel += 1.0;
         turn(-1);
      }
   }

   private static void turn(int dir) {
      long now = System.nanoTime();
      float next = profile.step(target, dir);
      shownAt = now;
      if (Math.abs(next - target) < 1.0E-4F) {
         if (now - stopAt > 250_000_000L) {
            stopAt = now;
            play(STOP, 0.95F + 0.05F * dir, 0.28F);
         }
         return;
      }
      target = next;
      MEMORY.put(key, target);
      dirty = true;
      dirtyAt = now;
      if (now - clickAt > 40_000_000L) {
         clickAt = now;
         double along = (Math.log(target) - Math.log(profile.min())) / Math.max(1.0E-3, Math.log(profile.max()) - Math.log(profile.min()));
         play(CLICK, (float)(0.94 + 0.12 * along + (dir > 0 ? 0.02 : -0.02)), 0.32F);
      }
   }

   private static void play(ResourceLocation sound, float pitch, float volume) {
      Minecraft mc = Minecraft.getInstance();
      mc.getSoundManager().play(new SimpleSoundInstance(
         sound, SoundSource.PLAYERS, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
   }

   // ------------------------------------------------------------------------------------------- per frame

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre event) {
      frame++;
      long now = System.nanoTime();
      double dt = lastNanos == 0L ? 0.0 : Math.clamp((now - lastNanos) / 1.0E9, 0.0, 0.1);
      lastNanos = now;
      Minecraft mc = Minecraft.getInstance();
      Kind kind = refresh(mc);
      if (kind == Kind.NONE) {
         engagedLast = false;
         settleSway(mc, dt, 0.0F, 0.0F);
         saveIfDue(now, true);
         return;
      }
      shown = ScopePower.ease(shown, target, dt);
      float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
      boolean engaged = engaged(mc, kind);
      if (engaged && !engagedLast) {
         engagedAt = now;
         if (profile.variable()) {
            shownAt = now;
         }
      }
      engagedLast = engaged;
      boolean magnified = profile.max() >= 2.0F;
      float env = engaged && magnified && !HuntConfig.REDUCED_MOTION.get() ? progress(kind, pt) : 0.0F;
      settleSway(mc, dt, env, (now - engagedAt) / 1.0E9F);
      saveIfDue(now, !engaged);
   }

   /**
    * [rifle] How steady the hold is, as {drift, breathing} multiples of the standing hold: standing > crouched >
    * crouched on the bipod > prone (no drift at all, only a faint breathing bob); steady stock, moving.
    */
   private static double[] stance(Minecraft mc) {
      LocalPlayer p = mc.player;
      ItemStack stack = p.getMainHandItem();
      boolean bipod = ExpeditionWeapon.attachment(stack, "bipod");
      double drift;
      double breath;
      boolean sticks = com.formaworks.frontierhunts.sticks.ShootingSticks.rested(p); // [sticks] forend in the yoke
      if (sticks) {
         drift = com.formaworks.frontierhunts.sticks.ShootingSticks.DRIFT;
         breath = com.formaworks.frontierhunts.sticks.ShootingSticks.BREATH;
      } else if (Prone.isProne(p)) {
         drift = 0.0;
         breath = bipod ? 0.04 : 0.06;
      } else if (p.isCrouching()) {
         drift = bipod ? 0.18 : 0.55;
         breath = bipod ? 0.25 : 0.6;
      } else {
         drift = 1.0;
         breath = 1.0;
      }
      if (ExpeditionWeapon.attachment(stack, "steady_stock")) {
         drift *= 0.8;
      }
      if (p.getDeltaMovement().horizontalDistanceSqr() > 9.0E-4 && !Prone.isProne(p) && !sticks) {
         drift *= 1.8;
         breath *= 1.3;
      }
      return new double[]{drift, breath};
   }

   /**
    * [rifle] Advances the hold-sway curve ({@link HoldSway}) by this frame's real time and turns the look direction by
    * exactly the change since the last frame (current and previous-tick rotation alike, so interpolation is
    * untouched). What could not be applied (float rounding of a large yaw, the +-90 pitch clamp) is carried into the
    * next frame instead of being lost, so the motion stays one continuous curve at any frame rate. {@code env} is
    * the raise envelope (0 = gun down): the curve glides to rest instead of jumping.
    */
   private static void settleSway(Minecraft mc, double dt, float env, float sinceEngaged) {
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         SWAY.reset();
         swayPitch = 0.0;
         swayYaw = 0.0;
         return;
      }
      if (mc.isPaused()) {
         return;
      }
      double driftTarget = 0.0;
      double breathTarget = 0.0;
      double across = 0.0, along = 0.0;
      if (env > 1.0E-4F) {
         // [1.1.8] wind on the hold: crosswind leans and buffets the muzzle; less when crouched, little prone or on a bipod
         double we = com.formaworks.frontierhunts.weather.SeasonalWeather.windEast(mc.level);
         double ws = com.formaworks.frontierhunts.weather.SeasonalWeather.windSouth(mc.level);
         double yawR = Math.toRadians(p.getYRot());
         double fx = -Math.sin(yawR), fz = Math.cos(yawR); // facing
         double exposure = Prone.isProne(p) ? 0.15 : p.isCrouching() ? 0.55 : 1.0;
         if (ExpeditionWeapon.attachment(p.getMainHandItem(), "bipod") && (Prone.isProne(p) || p.isCrouching())) {
            exposure *= 0.5;
         }
         if (com.formaworks.frontierhunts.sticks.ShootingSticks.rested(p)) {
            exposure = 0.0; // [sticks] the yoke carries the gun: the wind no longer pushes the muzzle
         }
         across = (we * -fz + ws * fx) * exposure * env; // wind from the shooter's left pushes the muzzle right
         along = (we * fx + ws * fz) * exposure * env;
         double[] st = stance(mc);
         double settle = 1.0 + 0.9 * Math.exp(-Math.max(0.0, sinceEngaged) / 0.7);
         settle = 1.0 + (settle - 1.0) * com.formaworks.frontierhunts.journal.HunterSkills.settle(com.formaworks.frontierhunts.journal.HunterSkills.clientMask, com.formaworks.frontierhunts.journal.HunterSkills.clientStrength); // [journal] Quick Settle
         double cold = com.formaworks.frontierhunts.survival.SurvivalApi.clientSway(); // [survival] shivering / exhaustion shake the hold
         if (com.formaworks.frontierhunts.sticks.ShootingSticks.rested(p)) {
            cold = 1.0 + (cold - 1.0) * com.formaworks.frontierhunts.sticks.ShootingSticks.SHAKE; // [sticks] the rest takes most of the shake
         }
         driftTarget = st[0] * env * settle * cold;
         breathTarget = st[1] * env * (1.0 + (cold - 1.0) * 0.5);
         driftTarget *= com.formaworks.frontierhunts.journal.HunterSkills.swayDrift(com.formaworks.frontierhunts.journal.HunterSkills.clientMask, com.formaworks.frontierhunts.journal.HunterSkills.clientStrength); // [journal] Steady Hands / Controlled Breath
         breathTarget *= com.formaworks.frontierhunts.journal.HunterSkills.swayBreath(com.formaworks.frontierhunts.journal.HunterSkills.clientMask, com.formaworks.frontierhunts.journal.HunterSkills.clientStrength); // [journal] Controlled Breath
      }
      // [sticks] on the sticks everything else is so still that the pulse shows in the reticle at high power
      SWAY.pulse(env > 1.0E-4F && com.formaworks.frontierhunts.sticks.ShootingSticks.rested(p) ? com.formaworks.frontierhunts.sticks.ShootingSticks.PULSE * env : 0.0);
      SWAY.advance(dt, driftTarget, breathTarget, across, along);
      double wantPitch = SWAY.pitch();
      double wantYaw = SWAY.yaw();
      if (mc.screen != null || cinematicOwnsView() || !Double.isFinite(wantPitch + wantYaw)) {
         swayPitch = Double.isFinite(wantPitch) ? wantPitch : 0.0; // the view is not ours: accept, never catch up later
         swayYaw = Double.isFinite(wantYaw) ? wantYaw : 0.0;
         return;
      }
      double dp = wantPitch - swayPitch;
      double dy = wantYaw - swayYaw;
      if (dp != 0.0) {
         float before = p.getXRot();
         float after = Mth.clamp(before + (float)dp, -90.0F, 90.0F);
         float applied = after - before;
         p.setXRot(after);
         p.xRotO = Mth.clamp(p.xRotO + applied, -90.0F, 90.0F);
         swayPitch += applied;
      }
      if (dy != 0.0) {
         float before = p.getYRot();
         float after = before + (float)dy;
         float applied = after - before;
         p.setYRot(after);
         p.yRotO += applied;
         swayYaw += applied;
      }
   }

   // ------------------------------------------------------------------------------------------- readout

   /**
    * The power-ring readout drawn just outside the lens edge at about half past four: a log-scaled arc of the
    * ring's markings with a pointer at the current power, "12.0×", and the reticle's calibrated power (SFP: the
    * reticle keeps its apparent size, so mil holds are exact only at that power). Fades out ~2 s after the power
    * last changed or the scope came up. {@code cx, cy, radius} in GUI units. Drawn once per frame.
    */
   static void readout(GuiGraphics g, float cx, float cy, float radius) {
      ScopePower.Profile p = profile;
      if (p == null || !p.variable() || readoutFrame == frame) {
         return;
      }
      float alpha = ScopePower.readoutAlpha(shownAt == Long.MIN_VALUE ? -1.0 : (System.nanoTime() - shownAt) / 1.0E9);
      if (alpha < 0.03F) {
         return;
      }
      readoutFrame = frame;
      Minecraft mc = Minecraft.getInstance();
      double scale = mc.getWindow().getGuiScale();
      float px = (float)scale;
      double a0 = Math.toRadians(18.0);
      double a1 = Math.toRadians(58.0);
      double lnMin = Math.log(p.min());
      double lnSpan = Math.max(1.0E-3, Math.log(p.max()) - lnMin);
      float ringR = (radius + 5.0F) * px;
      float ox = cx * px;
      float oy = cy * px;
      int tickColor = argb(alpha * 0.55F, 0xA9B4A6);
      int majorColor = argb(alpha * 0.8F, 0xC9CFBF);
      g.pose().pushPose();
      g.pose().scale((float)(1.0 / scale), (float)(1.0 / scale), 1.0F);
      int lo = (int)Math.ceil(p.min() - 1.0E-3);
      int hi = (int)Math.floor(p.max() + 1.0E-3);
      for (int v = lo; v <= hi; v++) {
         boolean major = v == lo || v == hi || v % 5 == 0;
         double ang = a0 + (Math.log(v) - lnMin) / lnSpan * (a1 - a0);
         float len = (major ? 7.0F : 4.0F) * Math.max(1.0F, px * 0.5F);
         float w = Math.max(1.0F, px * 0.5F);
         line(g, ox, oy, ang, ringR, ringR + len, w, major ? majorColor : tickColor);
      }
      double cur = a0 + (Math.log(shown) - lnMin) / lnSpan * (a1 - a0);
      line(g, ox, oy, cur, ringR - 3.0F * px, ringR + 9.0F * Math.max(1.0F, px * 0.5F), Math.max(1.5F, px * 0.75F), argb(alpha, 0xE9D9A6));
      g.pose().popPose();
      double mid = a0 + (a1 - a0) * 0.5;
      int tx = Math.round(cx + (float)Math.cos(mid) * (radius + 17.0F));
      int ty = Math.round(cy + (float)Math.sin(mid) * (radius + 17.0F)) - 4;
      g.drawString(mc.font, ScopePower.power(shown), tx, ty, argb(alpha, 0xE2E4DA), false);
      if (!p.digital()) {
         boolean exact = Math.abs(shown - p.calibrated()) < 0.05F;
         g.drawString(mc.font, "MIL @ " + ScopePower.fmt(p.calibrated()) + "×", tx, ty + 10, argb(alpha * (exact ? 0.95F : 0.5F), exact ? 0xB7D9A2 : 0xA6ADA2), false);
      } else {
         g.drawString(mc.font, "E-ZOOM", tx, ty + 10, argb(alpha * 0.5F, 0xA6ADA2), false);
      }
   }

   /** Bottom caption of the scope view: "5-25×  /  LONG RANGE" for the held optic, or null. */
   static String caption() {
      ScopePower.Profile p = profile;
      return p == null ? null : p.range() + "  /  " + p.name();
   }

   /** Mil-mark scale power of the reticle: the calibrated power for an SFP variable optic, else {@code power}. */
   static double reticlePower(double power) {
      ScopePower.Profile p = profile;
      return p != null && p.variable() ? p.calibrated() : power;
   }

   private static void line(GuiGraphics g, float ox, float oy, double ang, float r0, float r1, float width, int color) {
      float cos = (float)Math.cos(ang);
      float sin = (float)Math.sin(ang);
      int steps = Math.max(2, (int)Math.ceil(r1 - r0));
      int half = Math.max(1, Math.round(width));
      for (int i = 0; i <= steps; i++) {
         float r = r0 + (r1 - r0) * i / steps;
         int x = Math.round(ox + cos * r);
         int y = Math.round(oy + sin * r);
         g.fill(x - half / 2, y - half / 2, x - half / 2 + half, y - half / 2 + half, color);
      }
   }

   private static int argb(float alpha, int rgb) {
      return Math.clamp(Math.round(alpha * 255.0F), 0, 255) << 24 | rgb & 0xFFFFFF;
   }

   // ------------------------------------------------------------------------------------------- memory

   private static Path file() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("frontierhunts-scope-power.properties");
   }

   private static void load() {
      if (loaded) {
         return;
      }
      loaded = true;
      try {
         Path f = file();
         if (Files.isRegularFile(f)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(f)) {
               props.load(in);
            }
            for (String k : props.stringPropertyNames()) {
               try {
                  float v = Float.parseFloat(props.getProperty(k));
                  if (Float.isFinite(v) && MEMORY.size() < 512) {
                     MEMORY.put(k, v);
                  }
               } catch (NumberFormatException ignored) {
               }
            }
         }
      } catch (IOException | RuntimeException e) {
         LOGGER.warn("Could not read scope power memory: {}", e.toString());
      }
   }

   private static void saveIfDue(long now, boolean idle) {
      if (!dirty || now - dirtyAt < (idle ? 300_000_000L : 2_000_000_000L)) {
         return;
      }
      dirty = false;
      Properties props = new Properties();
      MEMORY.forEach((k, v) -> props.setProperty(k, Float.toString(v)));
      Path f;
      try {
         f = file();
      } catch (RuntimeException e) {
         return;
      }
      CompletableFuture.runAsync(() -> {
         try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
               props.store(out, "Frontier Hunts - last scope power per weapon|optic (client only)");
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
         } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not save scope power memory: {}", e.toString());
         }
      });
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
      if (dirty) {
         dirtyAt = 0L;
         saveIfDue(System.nanoTime(), true);
      }
      profile = null;
      key = "";
      refreshFrame = -1;
      refreshStack = null;
      engagedLast = false;
      SWAY.reset();
      swayPitch = 0.0;
      swayYaw = 0.0;
      shownAt = Long.MIN_VALUE;
   }
}
