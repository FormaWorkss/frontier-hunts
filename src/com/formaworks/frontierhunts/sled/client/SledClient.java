package com.formaworks.frontierhunts.sled.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.sled.SledEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * [1.1.9] The ride, from the seat: the view widens as the sled gathers speed, leans into the turns and hums over the
 * snow, and the wind roars in your ears. Follows the Camera shake setting (none of the shake with Reduced motion).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SledClient {
   private SledClient() {
   }

   /** 0 standing still .. 1 at top speed, eased; and the lean into the turn */
   private static float rush, rushO, lean, leanO;
   private static Wind wind;

   static SledEntity sled(LocalPlayer p) {
      return p != null && p.getVehicle() instanceof SledEntity s ? s : null;
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isPaused()) {
         return;
      }
      rushO = rush;
      leanO = lean;
      SledEntity s = sled(mc.player);
      float target = s == null ? 0.0F : (float)Mth.clamp(Math.abs(s.speed()) / 1.6, 0.0, 1.0);
      rush += (target - rush) * (target > rush ? 0.12F : 0.2F);
      float lt = s == null ? 0.0F : s.steer * rush;
      lean += (lt - lean) * 0.15F;
      if (s != null && (wind == null || wind.isStopped()) && mc.getSoundManager() != null) {
         wind = new Wind();
         mc.getSoundManager().play(wind);
      }
      hint(mc);
      // [1.2.0] every sled moving nearby hisses over the snow (and sprays in a hard turn): runners, not footsteps
      if (mc.level != null && mc.player != null && mc.getSoundManager() != null) {
         for (SledEntity sled : mc.level.getEntitiesOfClass(SledEntity.class, mc.player.getBoundingBox().inflate(40.0))) {
            if (!RUNNERS.containsKey(sled) && speedOf(sled) > 0.06) {
               Runner glide = new Runner(sled, GLIDE, false), carve = new Runner(sled, CARVE, true);
               RUNNERS.put(sled, glide);
               mc.getSoundManager().play(glide);
               mc.getSoundManager().play(carve);
            }
         }
      }
   }

   private static int hintCooldown;

   /** [1.2.0] say how to use a toboggan you look at */
   static void hint(Minecraft mc) {
      if (mc.player == null || mc.level == null || mc.player.isPassenger() || --hintCooldown > 0) {
         return;
      }
      if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit && hit.getEntity() instanceof SledEntity se) {
         mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable(se instanceof com.formaworks.frontierhunts.sled.SnowmobileEntity
            ? "hint.frontierhunts.snowmobile.ride" : "hint.frontierhunts.sled.ride"), false);
         hintCooldown = 30;
      }
   }

   static final net.minecraft.sounds.SoundEvent GLIDE = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(FrontierHunts.id("sled_glide")),
      CARVE = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(FrontierHunts.id("sled_carve"));
   private static final java.util.Map<SledEntity, Runner> RUNNERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   /** blocks per tick: the sled's own speed when this game drives it, else how far it moved */
   static double speedOf(SledEntity s) {
      if (s.isControlledByLocalInstance()) {
         return Math.abs(s.speed());
      }
      return Math.hypot(s.getX() - s.xo, s.getZ() - s.zo);
   }

   /** the runners on the snow (or the spray of a carve), following one sled */
   static final class Runner extends AbstractTickableSoundInstance {
      private final SledEntity sled;
      private final boolean carve;
      private float smooth;

      Runner(SledEntity sled, net.minecraft.sounds.SoundEvent ev, boolean carve) {
         super(ev, SoundSource.NEUTRAL, RandomSource.create());
         this.sled = sled;
         this.carve = carve;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.0F;
         this.x = sled.getX();
         this.y = sled.getY();
         this.z = sled.getZ();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         if (this.sled.isRemoved()) {
            RUNNERS.remove(this.sled);
            this.stop();
            return;
         }
         this.x = this.sled.getX();
         this.y = this.sled.getY();
         this.z = this.sled.getZ();
         double sp = speedOf(this.sled);
         boolean snow = this.sled.grip() < 0.2;
         float target = (float)Math.min(1.0, sp / 1.2);
         if (this.carve) {
            target *= Math.min(1.0F, Math.abs(this.sled.steer) * 1.4F) * (sp > 0.3 ? 1.0F : 0.0F);
         }
         if (!snow) {
            target *= 0.35F;
         }
         this.smooth += (target - this.smooth) * 0.25F;
         this.volume = this.carve ? this.smooth * 0.8F : 0.08F + this.smooth * 0.9F;
         if (!this.carve && sp < 0.03) {
            this.volume = 0.0F;
         }
         this.pitch = (this.carve ? 0.85F : 0.7F) + this.smooth * 0.55F;
         if (sp < 0.01 && this.smooth < 0.01 && this.sled.tickCount % 40 == 0) {
            RUNNERS.remove(this.sled);
            this.stop();
         }
      }
   }

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      if (!e.usedConfiguredFov()) {
         return;
      }
      float k = Mth.lerp((float)e.getPartialTick(), rushO, rush);
      if (k > 0.001F) {
         // up to about a fifth wider at full tilt: the sense of speed
         e.setFOV(e.getFOV() * (1.0 + 0.2 * k * k * Minecraft.getInstance().options.fovEffectScale().get()));
      }
   }

   @SubscribeEvent
   public static void camera(ViewportEvent.ComputeCameraAngles e) {
      Minecraft mc = Minecraft.getInstance();
      SledEntity s = sled(mc.player);
      if (s == null || !mc.options.getCameraType().isFirstPerson()) {
         return;
      }
      float pt = (float)e.getPartialTick();
      float k = Mth.lerp(pt, rushO, rush);
      float shake = HuntConfig.shake();
      // lean into the turn, and take a little of the slope into the view (down the fall line the nose dips)
      float l = Mth.lerp(pt, leanO, lean);
      float pitch = Mth.lerp(pt, s.visPitchO, s.visPitch);
      double t = (s.tickCount + pt) * 0.05;
      // the runners chatter over the snow, faster and harder with speed
      float chatter = (float)(Math.sin(t * 37.0) * 0.6 + Math.sin(t * 59.0 + 1.3) * 0.4) * k * k * 0.22F * shake; // [1.2.2] a hum, not a shake
      e.setRoll(e.getRoll() - l * 5.0F * Math.min(1.0F, shake) + chatter * 0.6F);
      e.setPitch(e.getPitch() - pitch * 0.22F * Math.min(1.0F, shake) + chatter);
   }

   /** wind past your ears: silent at a walk, a roar at full speed */
   static final class Wind extends AbstractTickableSoundInstance {
      Wind() {
         super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.volume = 0.0F;
         this.relative = true;
         this.attenuation = SoundInstance.Attenuation.NONE;
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         Minecraft mc = Minecraft.getInstance();
         if (sled(mc.player) == null && rush < 0.02F) {
            this.stop();
            return;
         }
         float k = Mth.clamp((rush - 0.15F) / 0.85F, 0.0F, 1.0F);
         this.volume = (float)Math.pow(k, 1.4) * 0.75F;
         this.pitch = 0.6F + rush * 0.55F;
      }
   }
}
