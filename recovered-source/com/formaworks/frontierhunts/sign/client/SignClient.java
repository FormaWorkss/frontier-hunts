package com.formaworks.frontierhunts.sign.client;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.hunting.DeerSign;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * [sign] Client wiring for rubs and scrapes: the block entity renderer that draws them, no block outline around their
 * (invisible) hit boxes, and a small "Use · Read the rub" prompt when you look at one up close. Seeing sign needs nothing:
 * the renderer draws every rub and scrape for every player.
 */
public final class SignClient {
   private SignClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD, value = Dist.CLIENT)
   public static final class ModEvents {
      @SubscribeEvent
      public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
         e.registerBlockEntityRenderer(ExpeditionContent.SIGN_MARK.get(), DeerSignRenderer::new);
      }

      @SubscribeEvent
      public static void layers(RegisterGuiLayersEvent e) {
         e.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath("frontierhunts", "deer_sign_prompt"), SignClient::prompt);
      }
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
   public static final class GameEvents {
      /** The selection box of a rub / scrape is an invisible stand-in for the decal: do not draw it. */
      @SubscribeEvent
      public static void outline(RenderHighlightEvent.Block e) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && kind(mc.level.getBlockState(e.getTarget().getBlockPos())) != null) {
            e.setCanceled(true);
         }
      }
   }

   private static DeerSign.Kind kind(BlockState s) {
      return s.getBlock() instanceof DeerSign d && d.kind != DeerSign.Kind.BED ? d.kind : null;
   }

   private static void prompt(GuiGraphics g, DeltaTracker delta) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.player == null || mc.screen != null || mc.options.hideGui || mc.player.isSpectator()) {
         return;
      }
      HitResult hit = mc.hitResult;
      if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
         return;
      }
      DeerSign.Kind kind = kind(mc.level.getBlockState(bh.getBlockPos()));
      if (kind == null || mc.player.isShiftKeyDown()) {
         return;
      }
      boolean bait = mc.player.getMainHandItem().is(ExpeditionContent.item("bait"));
      if (bait && kind != DeerSign.Kind.SCRAPE) {
         return;
      }
      String text = mc.options.keyUse.getTranslatedKeyMessage().getString()
         + (bait ? " · Freshen the scrape with bait" : " · Read the " + (kind == DeerSign.Kind.RUB ? "rub" : "scrape"));
      int w = mc.font.width(text) + 16;
      int x = (g.guiWidth() - w) / 2;
      int y = g.guiHeight() / 2 + 26;
      g.fill(x, y, x + w, y + 18, -1072291554);
      g.drawString(mc.font, text, x + 8, y + 5, -1448747, false);
   }
}
