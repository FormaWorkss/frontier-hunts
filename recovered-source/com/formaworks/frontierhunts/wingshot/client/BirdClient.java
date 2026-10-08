package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wingshot.BirdNet;
import com.formaworks.frontierhunts.wingshot.Flight;
import com.formaworks.frontierhunts.wingshot.WingshotContent;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * [wingshot] Client wiring: the hit / wing-shot payload handlers, the feather sprite set, the per-tick watch over nearby
 * birds (splashes when a duck jumps off or lands on water, the thud or splash of a shot bird coming down, the wing
 * sound loops), the slow-motion moment's clock, its FOV and vignette.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class BirdClient {
   private static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath("frontierhunts", "textures/gui/wingshot_vignette.png");
   private static final WeakHashMap<WildlifeMob, Watch> WATCH = new WeakHashMap<>();

   private BirdClient() {
   }

   static final class Watch {
      byte phase = -1;
      boolean dead, down;
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class ModEvents {
      private ModEvents() {
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         BirdNet.clientHit = BirdFx::onHit;
         BirdNet.clientWingShot = w -> WingShotMoment.start(w.entity(), w.distance());
      }

      @SubscribeEvent
      public static void particles(RegisterParticleProvidersEvent e) {
         e.registerSpriteSet(WingshotContent.FX.get(), sprites -> {
            BirdFx.spriteSet = sprites;
            BirdFx.resetSprites();
            return (type, level, x, y, z, dx, dy, dz) -> null;
         });
      }
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null) {
         return;
      }
      if (mc.level.getGameTime() % 600L == 0L) {
         BirdFx.resetSprites(); // cheap: picks the sprites up again after a resource reload
      }
      List<WildlifeMob> flying = new ArrayList<>();
      for (Entity en : mc.level.entitiesForRendering()) {
         if (!(en instanceof WildlifeMob m) || !m.species.bird || m.distanceToSqr(mc.player) > 96.0 * 96.0) {
            continue;
         }
         Watch w = WATCH.computeIfAbsent(m, k -> new Watch());
         byte p = m.flightPhase();
         boolean dead = m.isDeadOrDying();
         boolean water = m.isInWater();
         if (w.phase != -1 && p != w.phase && !dead) {
            if (p == Flight.TAKEOFF && water) {
               BirdFx.splash(mc.level, m.getX(), m.getY() + 0.1, m.getZ(), 16, 0.9F);
               mc.level.playLocalSound(m.getX(), m.getY(), m.getZ(), WingshotContent.SPLASH_SMALL.get(), SoundSource.NEUTRAL, 0.9F, 1.05F, false);
            } else if (w.phase == Flight.LAND && p == Flight.NONE && (water || mc.level.getFluidState(m.blockPosition().below()).isSource())) {
               BirdFx.splash(mc.level, m.getX(), m.getY() + 0.1, m.getZ(), 22, 0.7F);
               mc.level.playLocalSound(m.getX(), m.getY(), m.getZ(), WingshotContent.SPLASH_SMALL.get(), SoundSource.NEUTRAL, 1.0F, 0.85F, false);
            }
         }
         w.phase = p;
         if (dead) {
            BirdAnim.State st = BirdRender.current(m);
            // the replayed (slow-motion) body comes down later than the real one: follow what is drawn
            boolean down = st != null && WingShotMoment.position(m, 0.0F) != null ? st.down : (m.onGround() || water);
            if (!w.dead) {
               w.dead = true;
               w.down = down; // shot on the ground: no landing thud
            } else if (down && !w.down) {
               w.down = true;
               BirdFx.bodyDown(m, water);
            }
         } else if (BirdAnim.flying(p) && !m.isRemoved()) {
            flying.add(m);
         }
      }
      BirdSounds.tick(flying);
   }

   @SubscribeEvent
   public static void frame(RenderFrameEvent.Pre e) {
      WingShotMoment.frame(e.getPartialTick().getGameTimeDeltaPartialTick(true));
   }

   @SubscribeEvent
   public static void fov(ViewportEvent.ComputeFov e) {
      float w = WingShotMoment.weight();
      if (w > 0.0F) {
         e.setFOV(e.getFOV() * (1.0 - 0.1 * w));
      }
   }

   @SubscribeEvent
   public static void gui(RenderGuiEvent.Post e) {
      float w = WingShotMoment.weight();
      if (w <= 0.001F) {
         return;
      }
      var g = e.getGuiGraphics();
      int sw = g.guiWidth(), sh = g.guiHeight();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      g.setColor(1.0F, 1.0F, 1.0F, 0.75F * w);
      g.blit(VIGNETTE, 0, 0, 0.0F, 0.0F, sw, sh, sw, sh);
      // a faint warm wash over the frozen instant
      g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      g.fill(0, 0, sw, sh, ((int)(26 * w) << 24) | 0x3A2A1A);
      RenderSystem.disableBlend();
   }

   @SubscribeEvent
   public static void unload(LevelEvent.Unload e) {
      if (e.getLevel().isClientSide()) {
         BirdFx.clear();
         BirdSounds.clear();
         BirdRender.clear();
         BirdWings.clear();
         WATCH.clear();
         WingShotMoment.stop();
      }
   }
}
