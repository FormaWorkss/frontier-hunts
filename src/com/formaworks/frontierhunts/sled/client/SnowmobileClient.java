package com.formaworks.frontierhunts.sled.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.sled.SnowmobileEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [1.2.5] The snowmobile from the seat and from the trail: its engine (an idle burble that hands over to the
 * two-stroke on the pipe as the revs climb), the track, and the fuel gauge and speed on the HUD.
 */
public final class SnowmobileClient {
   static final SoundEvent IDLE = SoundEvent.createVariableRangeEvent(FrontierHunts.id("snowmobile_idle")),
      ENGINE = SoundEvent.createVariableRangeEvent(FrontierHunts.id("snowmobile_engine")),
      TRACK = SoundEvent.createVariableRangeEvent(FrontierHunts.id("snowmobile_track"));
   private static final java.util.Map<SnowmobileEntity, Boolean> PLAYING = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   private SnowmobileClient() {
   }

   private static int noSnowHint;

   /** hooked into every snowmobile's client tick */
   public static void tick(SnowmobileEntity e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && e.getControllingPassenger() == mc.player) {
         double sp = Math.abs(e.speed());
         boolean snow = e.grip() < 0.1;
         // powder off the skis on the view now and then (not overwhelming), more in deep snow and at speed
         if (snow && e.onGround()) {
            com.formaworks.frontierhunts.landscape.ride.grime.client.ScreenSplatter.feedSnowmobile(0.6F + 0.4F * e.snowCoat(), (float)sp);
         }
         // off the snow the track has nothing to bite: say so when the throttle does nothing
         if (!snow && mc.player.zza > 0.0F && sp < 0.05 && --noSnowHint <= 0) {
            mc.gui.setOverlayMessage(Component.translatable("hint.frontierhunts.snowmobile.no_snow"), false);
            noSnowHint = 60;
         }
      }
      if (mc.player == null || mc.getSoundManager() == null || PLAYING.containsKey(e) || e.distanceToSqr(mc.player) > 64 * 64) {
         return;
      }
      if (e.running() || e.speed() > 0.05) {
         PLAYING.put(e, Boolean.TRUE);
         mc.getSoundManager().play(new Loop(e, 0));
         mc.getSoundManager().play(new Loop(e, 1));
         mc.getSoundManager().play(new Loop(e, 2));
      }
   }

   static double speedOf(SnowmobileEntity s) {
      return s.isControlledByLocalInstance() ? Math.abs(s.speed()) : Math.hypot(s.getX() - s.xo, s.getZ() - s.zo);
   }

   /** 0 idle, 1 engine on the pipe, 2 the track */
   static final class Loop extends AbstractTickableSoundInstance {
      private final SnowmobileEntity e;
      private final int layer;
      private float gain;

      Loop(SnowmobileEntity e, int layer) {
         super(layer == 0 ? IDLE : layer == 1 ? ENGINE : TRACK, SoundSource.NEUTRAL, RandomSource.create());
         this.e = e;
         this.layer = layer;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.001F;
         this.x = e.getX();
         this.y = e.getY();
         this.z = e.getZ();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         if (this.e.isRemoved() || this.e.distanceToSqr(Minecraft.getInstance().player) > 72 * 72) {
            PLAYING.remove(this.e);
            this.stop();
            return;
         }
         boolean on = this.e.running();
         float rpm = Mth.clamp(this.e.rpm, 0.0F, 1.0F);
         double sp = speedOf(this.e);
         float target;
         float pitch;
         if (this.layer == 0) {
            target = on ? 0.55F * (1.0F - Mth.clamp((rpm - 0.2F) / 0.25F, 0.0F, 1.0F)) : 0.0F;
            pitch = 0.9F + rpm * 0.6F;
         } else if (this.layer == 1) {
            target = on ? Mth.clamp((rpm - 0.12F) / 0.25F, 0.0F, 1.0F) * (0.55F + 0.45F * this.e.throttleShown) : 0.0F;
            pitch = 0.6F + rpm * 1.4F;
         } else {
            float k = (float)Mth.clamp(sp / 1.4, 0.0, 1.0);
            target = sp < 0.02 ? 0.0F : 0.12F + 0.6F * k;
            pitch = 0.55F + k * 1.0F;
         }
         if (this.layer < 2 && this.e.misfire > 0) {
            target *= 0.3F;
         }
         this.gain += (target - this.gain) * (target > this.gain ? 0.3F : 0.12F);
         if (!on && sp < 0.02 && this.gain < 0.01F) {
            PLAYING.remove(this.e);
            this.stop();
            return;
         }
         this.volume = Math.max(0.001F, this.gain);
         this.pitch = Mth.clamp(pitch, 0.5F, 2.0F);
         this.x = this.e.getX();
         this.y = this.e.getY() + 0.5;
         this.z = this.e.getZ();
      }
   }

   // ============================================================================================ HUD

   private static float shown = -1.0F;
   private static boolean warned;

   /** fuel gauge and speed, right of the hotbar, while you drive */
   public static void hud(GuiGraphics g, DeltaTracker dt) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.options.hideGui || !(p.getVehicle() instanceof SnowmobileEntity s) || s.getControllingPassenger() != p) {
         shown = -1.0F;
         return;
      }
      float frac = s.fraction();
      shown = shown < 0.0F ? frac : shown + (frac - shown) * 0.12F;
      boolean exempt = s.exempt();
      if (!exempt) {
         if (!warned && frac < 0.15F && frac > 0.0F) {
            warned = true;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F, 0.35F));
         } else if (frac > 0.18F) {
            warned = false;
         }
      }
      int w = g.guiWidth(), h = g.guiHeight();
      int right = w / 2 + 91 + 8;
      int y = h - 22;
      int barW = 50;
      int bx = right;
      // speed
      String spd = String.format("%.0f km/h", Math.abs(s.speed()) * 72.0);
      g.drawString(mc.font, spd, bx, y - 12, 0xFFE8E8E8, true);
      // fuel bar
      g.fill(bx - 1, y - 1, bx + barW + 1, y + 6, 0xA0000000);
      int fw = Math.round(barW * Mth.clamp(shown, 0.0F, 1.0F));
      int col = shown < 0.15F ? 0xFFD2462A : 0xFFE0A22C;
      if (fw > 0) {
         g.fill(bx, y, bx + fw, y + 5, col);
         g.fill(bx, y, bx + fw, y + 1, 0x60FFFFFF);
      }
      for (int i = 1; i < 4; i++) {
         int tx = bx + barW * i / 4;
         g.fill(tx, y + 3, tx + 1, y + 5, 0xA0FFFFFF);
      }
      String txt;
      int tc;
      if (exempt) {
         txt = String.format("%.1f L", s.fuel());
         tc = 0xFFB8B8B8;
      } else if (frac <= 0.0F) {
         txt = Component.translatable("hud.frontierhunts.atv_out_of_gas").getString();
         tc = p.tickCount / 6 % 2 == 0 ? 0xFFFF5A40 : 0xFFB03020;
      } else {
         txt = String.format("%.1f L", s.fuel());
         tc = frac < 0.15F ? 0xFFF0A030 : 0xFFE0E0E0;
      }
      g.drawString(mc.font, txt, bx + barW + 4, y - 1, tc, true);
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void hud(RegisterGuiLayersEvent event) {
         event.registerAbove(VanillaGuiLayers.HOTBAR, FrontierHunts.id("snowmobile_gauge"), SnowmobileClient::hud);
         SnowmobileEntity.clientTick = SnowmobileClient::tick;
      }
   }
}
