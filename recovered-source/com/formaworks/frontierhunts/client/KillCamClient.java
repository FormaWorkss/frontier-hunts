package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.hunting.TrackClue;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.killcam.KillCamMode;
import com.formaworks.frontierhunts.killcam.KillCamNetwork;
import com.formaworks.frontierhunts.killcam.KillCamSounds;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client half of the kill cam: starts/finishes replays from server payloads, owns the camera, input, HUD hiding, audio
 * ducking and the hooks the render mixins ask. Nothing here can change gameplay.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class KillCamClient {
   static KillCamReplay active;
   private static Marker camera;
   private static Entity previousCamera;
   private static CameraType previousView;
   private static ClientLevel startLevel;
   private static float startHealth;
   private static long lastNanos;
   private static long cooldownUntil;
   private static float fadeIn;
   private static float playerFov = 70.0F;
   private static KillCamReplay.Pose framePose;
   private static float standInPartial;
   private static boolean ownParticles;
   private static int lastPrefs = -1;
   private static int prefsClock;
   private static int nextDoubleId = -1_800_000_000;
   private static final List<SoundInstance> PLAYING = new ArrayList<>();
   /** [killcam2] Looks of far targets the client has no entity for (newest few). */
   private static final Map<Integer, KillCamNetwork.Appearance> APPEARANCES = new LinkedHashMap<>();

   private KillCamClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent event) {
         KillCamNetwork.shotReceiver = KillCamClient::onShot;
         KillCamNetwork.cancelReceiver = KillCamClient::onCancel;
         KillCamNetwork.appearanceReceiver = KillCamClient::onAppearance;
      }
   }

   static int nextDoubleId() {
      int id = nextDoubleId--;
      if (nextDoubleId > -1_000_000_000) {
         nextDoubleId = -1_800_000_000;
      }
      return id;
   }

   public static boolean active() {
      return active != null;
   }

   // ------------------------------------------------------------------ network

   private static void onShot(KillCamNetwork.Shot s) {
      Minecraft mc = Minecraft.getInstance();
      if (active != null) {
         if (active.shotId == s.shotId() && s.confirmed() && !active.confirmed && active.phase == KillCamReplay.Phase.FLIGHT) {
            if (s.target() == active.targetId) {
               active.apply(s, false);
            } else {
               // the shot killed a different animal than predicted: back out rather than show the wrong one
               active.abort();
            }
         }
         return;
      }
      if (mc.level == null || mc.player == null || mc.screen != null || !mc.player.isAlive() || mc.player.isSpectator() || mc.isPaused()) {
         return;
      }
      KillCamMode mode = HuntConfig.KILLCAM.get();
      if (mode == KillCamMode.OFF || HuntConfig.REDUCED_MOTION.get() || mode == KillCamMode.TROPHY && !s.trophy()) {
         return;
      }
      if (System.nanoTime() < cooldownUntil) {
         return;
      }
      // [killcam2] a target beyond entity tracking range is filmed as a stand-in built from its Appearance
      LivingEntity target = mc.level.getEntity(s.target()) instanceof LivingEntity loaded ? loaded : proxy(mc, s);
      if (!(target instanceof Whitetail || target instanceof WildlifeMob)) {
         return;
      }
      if (target.distanceToSqr(mc.player) > 700.0 * 700.0 || s.origin().distanceToSqr(mc.player.getEyePosition()) > 64.0) {
         return;
      }
      try {
         KillCamReplay r = KillCamReplay.start(s, mc.level, target);
         if (r != null) {
            begin(mc, r);
         }
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: replay not started", ex);
         end(false);
      }
   }

   private static void onAppearance(KillCamNetwork.Appearance a) {
      APPEARANCES.remove(a.target());
      APPEARANCES.put(a.target(), a);
      while (APPEARANCES.size() > 8) {
         APPEARANCES.remove(APPEARANCES.keySet().iterator().next());
      }
   }

   /** A client-only copy of a far animal, posed where the server says it was hit (never added to the level). */
   private static LivingEntity proxy(Minecraft mc, KillCamNetwork.Shot s) {
      KillCamNetwork.Appearance a = APPEARANCES.get(s.target());
      if (a == null || mc.level == null) {
         return null;
      }
      try {
         EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(a.entityType());
         if (!(type.create(mc.level) instanceof LivingEntity e)) {
            return null;
         }
         e.load(a.data());
         e.setId(nextDoubleId());
         Vec3 at = s.pose() != null ? s.pose() : s.impact();
         e.moveTo(at.x, at.y, at.z, s.bodyYaw(), s.pitch());
         e.setOldPosAndRot();
         e.yBodyRot = e.yBodyRotO = s.bodyYaw();
         e.yHeadRot = e.yHeadRotO = s.headYaw();
         return e;
      } catch (RuntimeException | LinkageError ex) {
         LogUtils.getLogger().debug("Frontier Hunts kill cam: no stand-in for a far animal", ex);
         return null;
      }
   }

   private static void onCancel(KillCamNetwork.Cancel c) {
      if (active != null && active.shotId == c.shotId() && !active.confirmed) {
         active.abort();
      }
   }

   private static void begin(Minecraft mc, KillCamReplay r) {
      if (HuntCinematics.active()) {
         HuntCinematics.stop();
      }
      active = r;
      startLevel = mc.level;
      startHealth = mc.player.getHealth();
      previousCamera = mc.getCameraEntity();
      previousView = mc.options.getCameraType();
      mc.options.setCameraType(CameraType.FIRST_PERSON);
      camera = new Marker(EntityType.MARKER, mc.level);
      Vec3 eye = mc.player.getEyePosition();
      camera.setPos(eye.x, eye.y, eye.z);
      camera.setOldPosAndRot();
      mc.setCameraEntity(camera);
      KeyMapping.releaseAll();
      lastNanos = System.nanoTime();
      framePose = null;
      KillCamFx.reset();
      KillCamFx.muzzle(r);
      play(KillCamSounds.SLOWMO_IN, 1.0F, 0.9F);
      play(KillCamSounds.FLIGHT, r.bow ? 0.82F : 1.0F, r.bow ? 0.55F : 0.75F);
   }

   static void end(boolean fade) {
      end(fade, true);
   }

   /**
    * Tear everything down. {@code fade} leaves a short fade-in from black (used after a graceful cancel);
    * {@code cutAudio} is false only for a replay that played out, so its last sound can ring out naturally.
    */
   static void end(boolean fade, boolean cutAudio) {
      Minecraft mc = Minecraft.getInstance();
      KillCamReplay r = active;
      active = null;
      framePose = null;
      if (r != null) {
         try {
            r.dispose();
         } catch (RuntimeException ex) {
            LogUtils.getLogger().debug("Frontier Hunts kill cam: dispose", ex);
         }
      }
      if (camera != null && mc.getCameraEntity() == camera) {
         Entity back = previousCamera != null && !previousCamera.isRemoved() && previousCamera.level() == mc.level ? previousCamera : mc.player;
         mc.setCameraEntity(back);
      }
      if (previousView != null) {
         mc.options.setCameraType(previousView);
      }
      camera = null;
      previousCamera = null;
      previousView = null;
      startLevel = null;
      if (cutAudio) {
         stopSounds();
      } else {
         PLAYING.clear();
      }
      KillCamFx.reset();
      KillCamOverlay.reset();
      fadeIn = fade ? 1.0F : 0.0F;
      cooldownUntil = System.nanoTime() + 1_200_000_000L;
   }

   // ------------------------------------------------------------------ per tick / per frame

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post event) {
      Minecraft mc = Minecraft.getInstance();
      syncPrefs(mc);
      if (active == null) {
         return;
      }
      if (mc.level == null || mc.level != startLevel || mc.player == null || !mc.player.isAlive() || mc.player.getHealth() < startHealth
         || mc.screen != null || HuntConfig.KILLCAM.get() == KillCamMode.OFF) {
         end(false);
         return;
      }
      if (HuntCinematics.active()) {
         HuntCinematics.stop();
      }
      KillCamReplay r = active;
      if (r.total > 12.0) {
         // safety net: no replay is ever this long; never leave the player without their camera
         end(false);
         return;
      }
      if (r.real != null && r.real.isRemoved() && !r.confirmed) {
         // the animal left before we had the real result: nothing trustworthy to show
         r.abort();
      }
      PLAYING.removeIf(s -> !mc.getSoundManager().isActive(s));
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre event) {
      KillCamReplay r = active;
      long now = System.nanoTime();
      double dt = Math.clamp((now - lastNanos) / 1.0E9, 0.0, 0.1);
      lastNanos = now;
      if (fadeIn > 0.0F) {
         fadeIn = Math.max(0.0F, fadeIn - (float)dt * 4.0F);
      }
      if (r == null) {
         return;
      }
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         end(false);
         return;
      }
      try {
         r.advance(dt);
         if (r.phase == KillCamReplay.Phase.DONE) {
            boolean aborted = r.vImpact < 0;
            end(aborted, aborted);
            return;
         }
         cues(r);
         r.driveDoubles();
         standInPartial = r.standInPartial();
         KillCamFx.step(r);
         float pt = event.getPartialTick().getGameTimeDeltaPartialTick(true);
         KillCamReplay.Pose eye = KillCamReplay.eye(mc, pt, playerFov);
         framePose = r.pose(eye, playerFov);
         if (camera != null) {
            Vec3 p = framePose.pos();
            camera.setPos(p.x, p.y, p.z);
            camera.xo = camera.xOld = p.x;
            camera.yo = camera.yOld = p.y;
            camera.zo = camera.zOld = p.z;
            camera.setYRot(framePose.yaw());
            camera.setXRot(framePose.pitch());
            camera.yRotO = framePose.yaw();
            camera.xRotO = framePose.pitch();
         }
      } catch (RuntimeException ex) {
         LogUtils.getLogger().warn("Frontier Hunts kill cam: replay stopped", ex);
         end(false);
      }
   }

   /** Timed audio cues driven by the replay's own clock. */
   private static void cues(KillCamReplay r) {
      if (r.once(0, r.phase == KillCamReplay.Phase.FLIGHT && r.total > 0.34)) {
         play(KillCamSounds.HEARTBEAT, 1.0F, 0.85F);
      }
      if (r.once(1, r.phase == KillCamReplay.Phase.FLIGHT && r.u > 0.72 && !r.confirmed)) {
         play(KillCamSounds.HEARTBEAT, 0.94F, 0.7F);
      }
      if (r.vImpact >= 0 && r.phase.ordinal() <= KillCamReplay.Phase.RETURN.ordinal()) {
         double since = r.v - r.vImpact;
         if (r.once(2, since >= (r.deer ? 11.0 : 9.0))) {
            float mass = Mth.clamp(r.bbHeight / 1.6F, 0.4F, 1.6F);
            play(KillCamSounds.THUD, Mth.clamp(1.12F - 0.18F * mass, 0.78F, 1.2F), 0.85F);
         }
      }
      if (r.once(3, r.phase == KillCamReplay.Phase.RETURN)) {
         play(KillCamSounds.RETURN, 1.0F, 0.55F);
      }
   }

   static void impact(KillCamReplay r) {
      play(r.bow ? KillCamSounds.IMPACT_ARROW : KillCamSounds.IMPACT, 1.0F, 1.0F);
      KillCamFx.impact(r);
      KillCamOverlay.card(r);
   }

   static void xrayStart(KillCamReplay r) {
      play(KillCamSounds.XRAY, 1.0F, 0.5F);
   }

   /** Vanilla death puff for a wildlife double, the way the real animal vanished. */
   static void poof(LivingEntity e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null) {
         return;
      }
      ownParticles = true;
      try {
         for (int i = 0; i < 20; i++) {
            double dx = e.getRandom().nextGaussian() * 0.02;
            double dy = e.getRandom().nextGaussian() * 0.02;
            double dz = e.getRandom().nextGaussian() * 0.02;
            mc.level.addParticle(ParticleTypes.POOF, e.getRandomX(1.0), e.getRandomY(), e.getRandomZ(1.0), dx, dy, dz);
         }
      } finally {
         ownParticles = false;
      }
   }

   // ------------------------------------------------------------------ hooks asked by the mixins

   /** Camera.setup TAIL: true while the kill cam owns this camera entity (then use the exact per-frame pose below). */
   public static boolean drivesCamera(Entity cameraEntity) {
      return active != null && camera != null && cameraEntity == camera && framePose != null;
   }

   public static Vec3 cameraPosition() {
      KillCamReplay.Pose p = framePose;
      return p == null ? Vec3.ZERO : p.pos();
   }

   public static float cameraYaw() {
      KillCamReplay.Pose p = framePose;
      return p == null ? 0.0F : p.yaw();
   }

   public static float cameraPitch() {
      KillCamReplay.Pose p = framePose;
      return p == null ? 0.0F : p.pitch();
   }

   /** LevelRenderer.renderEntity HEAD: hide the real target (a double stands in), the local player and fresh blood clues. */
   public static boolean hidden(Entity e) {
      KillCamReplay r = active;
      if (r == null) {
         return false;
      }
      Minecraft mc = Minecraft.getInstance();
      if (e == mc.player) {
         return true;
      }
      if (e.getId() == r.targetId) {
         return !r.showReal;
      }
      if (e instanceof Projectile p && r.phase.ordinal() < KillCamReplay.Phase.RETURN.ordinal() && p.getOwner() == mc.player) {
         // the real projectile is already downrange in real time; the replay shows its own
         return true;
      }
      return e instanceof TrackClue && r.phase.ordinal() < KillCamReplay.Phase.DROP.ordinal() && e.tickCount < r.total * 20.0 + 4.0
         && e.position().distanceToSqr(r.snapPos()) < 25.0;
   }

   /** LevelRenderer.renderEntity HEAD: the slow-motion partial tick for the doubles; NaN for everything else. */
   public static float partialFor(Entity e) {
      KillCamReplay r = active;
      if (r == null || e.getId() >= 0) {
         return Float.NaN;
      }
      if (r.standIn != null && e == r.standIn.entity) {
         return standInPartial;
      }
      return e == r.arrowDouble() ? 1.0F : Float.NaN;
   }

   /** LevelRenderer.addParticleInternal HEAD: hold back the server's own hit effects until the replay reaches the hit. */
   public static boolean suppressParticle(double x, double y, double z) {
      KillCamReplay r = active;
      if (r == null || ownParticles) {
         return false;
      }
      // deer: the server's blood and ground-hit dust may show once the double drops; wildlife: also hold the death puff
      if (r.phase.ordinal() >= (r.deer ? KillCamReplay.Phase.DROP : KillCamReplay.Phase.RETURN).ordinal()) {
         return false;
      }
      return r.snapPos().distanceToSqr(x, y, z) < 36.0;
   }

   /** [archery2] The partial tick the animal double is drawn with this frame (its slow-motion clock). */
   static float standInPartialNow() {
      return standInPartial;
   }

   /** [archery2] Whether this arrow entity is the kill cam's stand-in for a crossbow bolt (drawn as a bolt). */
   static boolean boltDouble(Entity e) {
      KillCamReplay r = active;
      return r != null && e.getId() < 0 && e == r.arrowDouble() && r.weapon == com.formaworks.frontierhunts.killcam.KillCamNetwork.BOLT;
   }

   public static boolean isDouble(Entity e) {
      KillCamReplay r = active;
      return r != null && e.getId() < 0 && (r.standIn != null && e == r.standIn.entity || e == r.arrowDouble());
   }

   // ------------------------------------------------------------------ camera angles, fov, hud, hands

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void angles(ViewportEvent.ComputeCameraAngles event) {
      KillCamReplay.Pose p = framePose;
      if (active != null && p != null && Minecraft.getInstance().getCameraEntity() == camera) {
         event.setYaw(p.yaw());
         event.setPitch(p.pitch());
         event.setRoll(p.roll());
      }
   }

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void fov(ViewportEvent.ComputeFov event) {
      if (!event.usedConfiguredFov()) {
         return;
      }
      if (active == null || framePose == null) {
         playerFov = (float)event.getFOV();
         return;
      }
      if (Minecraft.getInstance().player != null) {
         // what the player would see right now (options fov, sprint, zoom already applied upstream)
         playerFov = Minecraft.getInstance().options.fov().get() * Minecraft.getInstance().player.getFieldOfViewModifier();
         playerFov = (float)ScopeZoom.scopedFov(playerFov); // [scope] return into the raised scope's zoom
      }
      event.setFOV(framePose.fov());
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void hands(RenderHandEvent event) {
      if (active != null) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGH)
   public static void hud(RenderGuiLayerEvent.Pre event) {
      if (active != null) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void level(RenderLevelStageEvent event) {
      if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && !HuntShaderCompat.shadowPass()) {
         if (active != null) {
            KillCamOverlay.capture(event);
            KillCamFx.render(event, active);
         }
      }
   }

   static float fadeIn() {
      return fadeIn;
   }

   // ------------------------------------------------------------------ input: anything skips

   private static void skip() {
      KillCamReplay r = active;
      if (r != null && !r.skipped) {
         stopSounds();
         r.skip();
      }
   }

   @SubscribeEvent
   public static void key(InputEvent.Key event) {
      if (active == null || event.getAction() != GLFW.GLFW_PRESS) {
         return;
      }
      int k = event.getKey();
      boolean passive = k >= GLFW.GLFW_KEY_F1 && k <= GLFW.GLFW_KEY_F25 || k == GLFW.GLFW_KEY_LEFT_SHIFT || k == GLFW.GLFW_KEY_RIGHT_SHIFT
         || k == GLFW.GLFW_KEY_LEFT_CONTROL || k == GLFW.GLFW_KEY_RIGHT_CONTROL || k == GLFW.GLFW_KEY_LEFT_ALT || k == GLFW.GLFW_KEY_RIGHT_ALT
         || k == GLFW.GLFW_KEY_LEFT_SUPER || k == GLFW.GLFW_KEY_RIGHT_SUPER;
      if (!passive) {
         skip();
      }
   }

   @SubscribeEvent
   public static void mouse(InputEvent.MouseButton.Pre event) {
      if (active != null && event.getAction() == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
         skip();
         event.setCanceled(true);
      }
   }

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
      if (active != null) {
         skip();
         event.setCanceled(true);
         event.setSwingHand(false);
      }
   }

   // ------------------------------------------------------------------ doubles never tick on their own

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void entityTick(EntityTickEvent.Pre event) {
      Entity e = event.getEntity();
      if (e.getId() < 0 && e.level().isClientSide && (isDouble(e) || active == null && (e instanceof Whitetail || e instanceof WildlifeMob) && e.getId() <= -1_000_000_000)) {
         event.setCanceled(true);
         if (active == null && !isDouble(e) && e.level() instanceof ClientLevel cl) {
            // an orphaned double (should never happen): clean it up
            cl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
         }
      }
   }

   // ------------------------------------------------------------------ audio

   static void play(SoundEvent event, float pitch, float volume) {
      Minecraft mc = Minecraft.getInstance();
      SoundInstance s = SimpleSoundInstance.forUI(event, pitch, volume);
      mc.getSoundManager().play(s);
      PLAYING.add(s);
   }

   static void stopSounds() {
      SoundManager sm = Minecraft.getInstance().getSoundManager();
      for (SoundInstance s : PLAYING) {
         sm.stop(s);
      }
      PLAYING.clear();
   }

   /** World audio while the kill cam runs: premature hit sounds at the animal are held back, everything else is ducked and slowed. */
   @SubscribeEvent(priority = EventPriority.LOW)
   public static void sound(PlaySoundEvent event) {
      KillCamReplay r = active;
      SoundInstance s = event.getSound();
      if (r == null || s == null) {
         return;
      }
      ResourceLocation id = s.getLocation();
      if (id.getNamespace().equals("frontierhunts") && id.getPath().startsWith("killcam")) {
         return;
      }
      if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.RECORDS || s.getSource() == SoundSource.MASTER) {
         return;
      }
      boolean beforeDrop = r.phase.ordinal() < KillCamReplay.Phase.DROP.ordinal();
      if (beforeDrop && !s.isRelative() && r.snapPos().distanceToSqr(s.getX(), s.getY(), s.getZ()) < 100.0) {
         event.setSound(null);
         return;
      }
      if (s instanceof TickableSoundInstance || r.phase == KillCamReplay.Phase.RETURN) {
         return;
      }
      float volume = beforeDrop ? 0.35F : 0.6F;
      float pitch = beforeDrop ? 0.78F : 0.9F;
      event.setSound(new Ducked(s, volume, pitch));
   }

   /** Delegating sound: same sound, quieter and lower (slow-motion ear). */
   private record Ducked(SoundInstance base, float volumeScale, float pitchScale) implements SoundInstance {
      @Override
      public ResourceLocation getLocation() {
         return this.base.getLocation();
      }

      @Override
      public WeighedSoundEvents resolve(SoundManager manager) {
         return this.base.resolve(manager);
      }

      @Override
      public net.minecraft.client.resources.sounds.Sound getSound() {
         return this.base.getSound();
      }

      @Override
      public SoundSource getSource() {
         return this.base.getSource();
      }

      @Override
      public boolean isLooping() {
         return this.base.isLooping();
      }

      @Override
      public boolean isRelative() {
         return this.base.isRelative();
      }

      @Override
      public int getDelay() {
         return this.base.getDelay();
      }

      @Override
      public float getVolume() {
         return this.base.getVolume() * this.volumeScale;
      }

      @Override
      public float getPitch() {
         return this.base.getPitch() * this.pitchScale;
      }

      @Override
      public double getX() {
         return this.base.getX();
      }

      @Override
      public double getY() {
         return this.base.getY();
      }

      @Override
      public double getZ() {
         return this.base.getZ();
      }

      @Override
      public Attenuation getAttenuation() {
         return this.base.getAttenuation();
      }

      @Override
      public boolean canStartSilent() {
         return this.base.canStartSilent();
      }

      @Override
      public boolean canPlaySound() {
         return this.base.canPlaySound();
      }

      @Override
      public java.util.concurrent.CompletableFuture<net.minecraft.client.sounds.AudioStream> getStream(
         net.minecraft.client.sounds.SoundBufferLibrary library, net.minecraft.client.resources.sounds.Sound sound, boolean looping
      ) {
         return this.base.getStream(library, sound, looping);
      }
   }

   // ------------------------------------------------------------------ settings sync + lifecycle

   private static void syncPrefs(Minecraft mc) {
      if (mc.getConnection() == null || ++prefsClock % 20 != 0) {
         return;
      }
      int mode = HuntConfig.REDUCED_MOTION.get() ? KillCamMode.OFF.ordinal() : HuntConfig.KILLCAM.get().ordinal();
      if (mode != lastPrefs && mc.getConnection().hasChannel(KillCamNetwork.Prefs.TYPE)) {
         lastPrefs = mode;
         PacketDistributor.sendToServer(new KillCamNetwork.Prefs(mode));
      }
   }

   @SubscribeEvent
   public static void login(ClientPlayerNetworkEvent.LoggingIn event) {
      lastPrefs = -1;
   }

   @SubscribeEvent
   public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
      end(false);
      fadeIn = 0.0F;
      cooldownUntil = 0L;
      lastPrefs = -1;
      KillCamOverlay.close();
   }
}
