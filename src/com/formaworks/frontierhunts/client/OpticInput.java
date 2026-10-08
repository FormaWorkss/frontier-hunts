package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.OpticTuning;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import com.formaworks.frontierhunts.workshop.OpticUpgrade;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class OpticInput {
   private static final KeyMapping ILLUMINATION = new KeyMapping("key.frontierhunts.optic_illumination", 73, "key.categories.frontierhunts");
   private static int reticle = 1;

   @SubscribeEvent
   public static void tick(Post var0) {
      while (ILLUMINATION.consumeClick()) {
         if (Minecraft.getInstance().screen == null && ExpeditionOptics.active()) {
            reticle = (reticle + 1) % 3;
         }
      }
   }

   static int reticleColor() {
      return reticle == 0 ? -14538719 : (reticle == 1 ? -2195119 : -7157354);
   }

   static void instrument(GuiGraphics var0, String var1, int var2) {
      Minecraft var3 = Minecraft.getInstance();
      if (var3.player != null) {
         int var4 = var0.guiWidth() / 2;
         int var5 = var0.guiHeight() / 2;
         float var6 = (Mth.wrapDegrees(var3.player.getYRot()) + 360.0F) % 360.0F;
         int var7 = Math.round((var6 + 180.0F) % 360.0F);
         int var8 = Math.round(-var3.player.getXRot());
         String var9 = var7 < 315 && var7 >= 45 ? (var7 < 135 ? "E" : (var7 < 225 ? "S" : "W")) : "N";
         int var10 = -7427431;
         var0.drawCenteredString(var3.font, String.format(Locale.ROOT, "%03d° %s   /   %+d°", var7, var9, var8), var4, var5 + var2 - 23, var10);
         if (var1.equals("field_scope") || var1.equals("thermal_scope")) {
            // [scope] live power of the held optic (variable scopes / thermal e-zoom) + power-ring readout
            double var11 = ScopeZoom.power();
            String var12 = var11 > 0.0 ? ScopePower.power((float)var11) : (var1.equals("thermal_scope") ? "4×" : "3×");
            var0.drawCenteredString(
               var3.font, var12 + "   ·   " + ILLUMINATION.getTranslatedKeyMessage().getString() + "  RETICLE", var4, var0.guiHeight() - 19, -4667212
            );
            ScopeZoom.readout(var0, (float)var4, (float)var5, (float)var2);
            int var13 = var5 + var2 - 10;
            var0.fill(var4 - 30, var13, var4 + 31, var13 + 1, -11639206);
            var0.fill(var4, var13 - 2, var4 + 1, var13 + 3, -8282488);
            int var14 = var4 + (int)Math.clamp((double)var8 * 0.55, -29.0, 29.0);
            var0.fill(var14 - 1, var13 - 1, var14 + 2, var13 + 2, reticleColor());
         }
      }
   }

   @SubscribeEvent
   public static void turn(CalculatePlayerTurnEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.screen == null && var1.options.getCameraType().isFirstPerson()) {
         boolean var2 = var1.player.isUsingItem()
            && var1.player.getUseItem().getItem() instanceof ExpeditionGear var3
            && (var3.id.contains("binoculars") || var3.id.equals("rangefinder"));
         boolean var5 = var1.player.getMainHandItem().getItem() instanceof RifleItem && RifleClient.AIM.isDown();
         if (var2 || var5 || ExpeditionClient.aiming()) {
            // [scope] scoped guns: turn rate follows the live power (proportional on variable optics)
            if (!var2 && ScopeZoom.power() > 0.0) {
               var0.setMouseSensitivity(ScopePower.eventSensitivity(var0.getMouseSensitivity(), ScopeZoom.turnScale()));
            } else {
               var0.setMouseSensitivity(OpticTuning.eventSensitivity(var0.getMouseSensitivity(), magnification()));
            }
         }
      }
   }

   public static double magnification() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.player == null) {
         return 1.0;
      } else {
         ItemStack var1 = var0.player.getMainHandItem();
         if (var0.player.isUsingItem()
            && var0.player.getUseItem().getItem() instanceof ExpeditionGear var2
            && (var2.id.contains("binoculars") || var2.id.equals("rangefinder"))) {
            return var2.id.equals("rangefinder") ? 5.0 : 6.25;
         } else if (ScopeZoom.power() > 0.0) {
            return ScopeZoom.power(); // [scope] live (eased) power of the held scoped gun
         } else if (var1.getItem() instanceof RifleItem) {
            return OpticUpgrade.fitted(var1) ? 4.0 : 3.0;
         } else {
            if (var1.getItem() instanceof ExpeditionWeapon var4) {
               return (double)AttachmentSpec.magnification(var1, var4.weapon);
            }

            return 1.0;
         }
      }
   }

   public static float movementBobScale() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.player != null && var0.screen == null && var0.options.getCameraType().isFirstPerson()) {
         boolean var1 = var0.player.getMainHandItem().getItem() instanceof RifleItem && RifleClient.aim(1.0F) > 0.8F;
         if (!var1 && !ExpeditionClient.aiming()) {
            return 1.0F;
         } else {
            return HuntConfig.REDUCED_MOTION.get() ? 0.0F : OpticTuning.bobScale(magnification());
         }
      } else {
         return 1.0F;
      }
   }

   @SubscribeEvent
   public static void recipes(RecipesUpdatedEvent var0) {
      EquipmentCatalog.clear();
   }

   private OpticInput() {
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent var0) {
         var0.register(OpticInput.ILLUMINATION);
      }
   }
}
