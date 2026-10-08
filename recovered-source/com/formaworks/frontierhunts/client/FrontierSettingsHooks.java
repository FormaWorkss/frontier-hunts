package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.client.tree.TreeModels;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack.Position;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FrontierSettingsHooks {
   static final KeyMapping OPEN = new KeyMapping("key.frontierhunts.settings", Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F7, "key.categories.frontierhunts"); // [1.2.8] bound out of the box (F7: free in vanilla and the usual mods)

   private FrontierSettingsHooks() {
   }

   /** [1.2.8] the key that opens the Frontier visual settings, as the player has it bound ("F7"), for the Handbook and the welcome card */
   public static String keyName() {
      return OPEN.isUnbound() ? "-" : OPEN.getTranslatedKeyMessage().getString();
   }

   /** [1.2.8] opens the Frontier visual settings over another screen (the Handbook's settings row) */
   public static void open(net.minecraft.client.gui.screens.Screen parent) {
      Minecraft.getInstance().setScreen(new FrontierSettingsScreen(parent));
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      RealisticWorld.tick();
      ShaderState.tick();

      while (OPEN.consumeClick()) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1.screen == null) {
            var1.setScreen(new FrontierSettingsScreen(null));
         }
      }
   }

   @SubscribeEvent
   public static void screen(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post var0) {
      if (var0.getScreen() instanceof VideoSettingsScreen var1) {
         Button var3 = Button.builder(Component.literal("Frontier Hunts..."), var1x -> Minecraft.getInstance().setScreen(new FrontierSettingsScreen(var1)))
            .bounds(6, 6, 104, 20)
            .tooltip(Tooltip.create(Component.literal("Graphics presets, realistic wildlife, grass, waterfalls and texture/shader packs per preset.")))
            .build();
         var0.addListener(var3);
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Registration {
      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent var0) {
         var0.register(FrontierSettingsHooks.OPEN);
      }

      @SubscribeEvent
      public static void packs(AddPackFindersEvent var0) {
         if (var0.getPackType() == PackType.CLIENT_RESOURCES) {
            var0.addPackFinders(
               RealisticWorld.PACK_LOCATION, PackType.CLIENT_RESOURCES, Component.literal("Frontier Realistic World"), PackSource.BUILT_IN, false, Position.TOP
            );
         }
      }

      @SubscribeEvent(
         priority = EventPriority.LOWEST
      )
      public static void models(ModifyBakingResult var0) {
         TreeModels.wrap(var0);
      }

      @SubscribeEvent
      public static void reload(RegisterClientReloadListenersEvent var0) {
         var0.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) RealisticWorld::reloaded);
      }
   }
}
