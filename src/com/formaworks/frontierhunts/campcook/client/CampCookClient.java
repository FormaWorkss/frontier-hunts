package com.formaworks.frontierhunts.campcook.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.campcook.CampCookContent;
import com.formaworks.frontierhunts.campcook.MealBuffs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** [licence] Client side of camp cooking: the Dutch oven screen and the local Steady Aim value for the sway code. */
public final class CampCookClient {
   private CampCookClient() {
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Mod {
      private Mod() {
      }

      @SubscribeEvent
      public static void screens(RegisterMenuScreensEvent e) {
         e.register(CampCookContent.MENU.get(), DutchOvenScreen::new);
      }
   }

   @EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
   public static final class Game {
      private Game() {
      }

      @SubscribeEvent
      public static void tick(ClientTickEvent.Post e) {
         LocalPlayer p = Minecraft.getInstance().player;
         MealBuffs.clientAim = p == null ? 1.0F : MealBuffs.aim(p);
      }
   }
}
