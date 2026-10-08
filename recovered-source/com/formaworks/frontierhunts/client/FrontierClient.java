package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.HuntNetwork;
import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.camp.CampContent;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntEntities;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import com.formaworks.frontierhunts.progression.AssignmentNetwork;
import com.formaworks.frontierhunts.rifle.RifleNetwork;
import com.formaworks.frontierhunts.tracking.TrailNetwork;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(
   value = "frontierhunts",
   dist = {Dist.CLIENT}
)
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FrontierClient {
   public static final KeyMapping JOURNAL = new KeyMapping("key.frontierhunts.journal", 74, "key.categories.frontierhunts");
   public static HuntNetwork.Snapshot state;
   public static HuntNetwork.HuntProgress hunt;
   public static AssignmentNetwork.Snapshot assignments;
   private static int arrivalTicks;

   public FrontierClient(IEventBus var1) {
      RifleNetwork.impactReceiver = BulletImpacts::receive;
      var1.addListener(FieldBlockParticles::register);
      var1.addListener(
         var0 -> var0.registerItem(
               new GhillieModel.Extensions(),
               ExpeditionContent.ITEMS.values().stream().map(var0x -> (Item)var0x.get()).filter(var0x -> var0x instanceof GhillieSuit).toArray(Item[]::new)
            )
      );
      var1.addListener(
         var0 -> var0.register(
               (var0x, var1x, var2, var3) -> var1x != null && var2 != null ? BiomeColors.getAverageGrassColor(var1x, var2) : GrassColor.getDefaultColor(),
               new Block[]{(Block)ExpeditionContent.UNDERGROWTH.get()}
            )
      );
      var1.addListener(var0 -> var0.register((var0x, var1x) -> GrassColor.get(0.5, 0.8), new ItemLike[]{(ItemLike)ExpeditionContent.UNDERGROWTH.get()}));
      var1.addListener(this::keys);
      var1.addListener(this::layers);
      var1.addListener(var0 -> {
         var0.registerSpriteSet((ParticleType)HuntParticles.FLARE_EMBER.get(), var0x -> new FlareParticle.Provider(var0x, false));
         var0.registerSpriteSet((ParticleType)HuntParticles.FLARE_SMOKE.get(), var0x -> new FlareParticle.Provider(var0x, true));
      });
      var1.addListener(var0 -> var0.registerSpriteSet((ParticleType)HuntParticles.BLOOD.get(), BloodParticle.Provider::new));
      var1.addListener(var0 -> var0.registerSpriteSet((ParticleType)HuntParticles.WATER_BLOOD.get(), WaterBloodParticle.Provider::new));
      var1.addListener(var0 -> var0.registerSpriteSet((ParticleType)HuntParticles.WIND_PUFF.get(), WindPuffParticle.Provider::new));
      var1.addListener(var0 -> {
         var0.register((MenuType)CampContent.SMOKE_MENU.get(), StationScreen::new);
         var0.register((MenuType)CampContent.TAN_MENU.get(), StationScreen::new);
         var0.register((MenuType)CampContent.CONTRACT_MENU.get(), ContractScreen::new);
      });
      var1.addListener(var0 -> var0.register((MenuType)WorkshopContent.MENU.get(), WorkbenchScreen::new));
      var1.addListener(var0 -> var0.register((MenuType)WorkshopContent.ATTACHMENT_MENU.get(), WorkbenchScreen::new));
      var1.addListener(var0 -> var0.register((MenuType)WorkshopContent.FITTING_MENU.get(), AttachmentScreen::new));
      var1.addListener(var0 -> {
         var0.register((MenuType)WorkshopContent.AMMO_MENU.get(), WorkbenchScreen::new);
         var0.register((MenuType)WorkshopContent.BOW_MENU.get(), WorkbenchScreen::new);
         var0.register((MenuType)WorkshopContent.FISH_MENU.get(), WorkbenchScreen::new);
         var0.register((MenuType)WorkshopContent.CLOTHING_MENU.get(), WorkbenchScreen::new);
         var0.register((MenuType)WorkshopContent.TENT_MENU.get(), WorkbenchScreen::new);
      });
      var1.addListener(var0 -> {
         var0.registerRenderBuffer(HuntRenderTypes.sculpt(WhitetailRenderer.FUR));
         var0.registerRenderBuffer(HuntRenderTypes.sculpt(WhitetailRenderer.MATERIAL));

         for (GameSpecies var4 : GameSpecies.values()) {
            for (boolean var8 : new boolean[]{false, true}) {
               if (var4 != GameSpecies.WHITETAIL || var8) {
                  var0.registerRenderBuffer(HuntRenderTypes.sculpt(DeerDraw.coat(var4, var8)));
               }
            }
         }

         var0.registerRenderBuffer(HuntRenderTypes.sculpt(DeerDraw.ANTLER));
      });
      var1.addListener(var0 -> {
         for (Model var2 : var0.getSkins()) {
            if (var0.getSkin(var2) instanceof PlayerRenderer var3) {
               var3.addLayer(new QuiverLayer(var3));
               var3.addLayer(new PackLayer(var3));
            }
         }
      });
      var1.addListener(var0 -> var0.registerReloadListener((ResourceManagerReloadListener)var0x -> {
            CarcassSurface.clear();
            WhitetailRenderer.clear();
            DeerMount.clear();
            BloodParticle.clear();
            WaterBloodParticle.clear();
            FlareParticle.clear();
         }));
      var1.addListener(
         var0 -> {
            var0.registerItem(new HuntEquipmentRenderer.Extensions(), HuntContent.TIPS.values().stream().map(var0x -> (Item)var0x.get()).toArray(Item[]::new));
            var0.registerItem(
               new HuntEquipmentRenderer.Extensions(),
               new Item[]{
                  (Item)HuntContent.FIELD_BOW.get(),
                  (Item)HuntContent.FIELD_ARROW.get(),
                  (Item)HuntContent.PRIMITIVE_ARROW.get(),
                  (Item)HuntContent.QUIVER.get(),
                  (Item)HuntContent.TRACER_ARROW.get(),
                  (Item)HuntContent.FIELD_KNIFE.get(),
                  (Item)HuntContent.SKINNING_TOOL.get(),
                  (Item)HuntContent.JOURNAL.get(),
                  (Item)HuntContent.WHITETAIL_TROPHY.get()
               }
            );
            var0.registerItem(
               new HarvestItemRenderer.Extensions(),
               new Item[]{
                  (Item)HuntContent.DEER_HIDE.get(),
                  (Item)HuntContent.TANNED_HIDE.get(),
                  (Item)HuntContent.VENISON.get(),
                  (Item)HuntContent.COOKED_VENISON.get(),
                  (Item)HuntContent.VENISON_QUARTER.get(),
                  (Item)HuntContent.AGED_VENISON.get(),
                  (Item)HuntContent.BACKSTRAP.get(),
                  (Item)HuntContent.COOKED_BACKSTRAP.get()
               }
            );
            var0.registerItem(new WorkshopItemRenderer.Extensions(), new Item[]{(Item)WorkshopContent.ROD.get(), (Item)WorkshopContent.OPTIC.get()});
         }
      );
      HuntNetwork.clientReceiver = FrontierClient::receive;
      TrailNetwork.receiver = TrailClient::receive;
      TrailNetwork.confirmation = TrailClient::confirm;
      AssignmentNetwork.receiver = var0 -> {
         Minecraft var1x = Minecraft.getInstance();
         if (var1x.level != null && var0.dimension().equals(var1x.level.dimension().location().toString())) {
            assignments = var0;
            if (var0.open()) {
               var1x.setScreen(new AssignmentScreen());
            } else if (var1x.screen instanceof AssignmentScreen var2) {
               var2.refresh();
            }
         }
      };
      HuntNetwork.huntReceiver = var0 -> {
         Minecraft var1x = Minecraft.getInstance();
         if (var1x.level != null && var1x.level.dimension().location().toString().equals(var0.dimension())) {
            hunt = var0;
            if (var1x.screen instanceof JournalScreen var2) {
               var2.refresh();
            }
         }
      };
      var1.addListener(var0 -> {
         HuntEntities.GAME.forEach((var1x, var2) -> var0.registerEntityRenderer((EntityType)var2.get(), var1xx -> new WhitetailRenderer(var1xx, var1x)));
         var0.registerEntityRenderer((EntityType)HuntEntities.ARROW.get(), FieldArrowRenderer::new);
         var0.registerEntityRenderer((EntityType)HuntEntities.CLUE.get(), TrackClueRenderer::new);
      });
   }

   private void keys(RegisterKeyMappingsEvent var1) {
      var1.register(JOURNAL);
   }

   private void layers(RegisterGuiLayersEvent var1) {
      var1.registerAboveAll(FrontierHunts.id("wilderness"), (var0, var1x) -> hud(var0));
   }

   public static void receive(HuntNetwork.Snapshot var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.level.dimension().location().toString().equals(var0.dimension())) {
         if (var0.active() && (state == null || !state.active())) {
            arrivalTicks = 110;
         }

         state = var0;
         if (var0.open()) {
            var1.setScreen(new JournalScreen());
         } else if (var1.screen instanceof JournalScreen var2) {
            var2.refresh();
         }
      }
   }

   @SubscribeEvent
   public static void frame(Pre var0) {
      WhitetailRenderer.beginFrame();
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null) {
         state = null;
         hunt = null;
         assignments = null;
         arrivalTicks = 0;
      } else {
         if (assignments != null && !assignments.dimension().equals(var1.level.dimension().location().toString())) {
            assignments = null;
         }

         if (hunt != null && !hunt.dimension().equals(var1.level.dimension().location().toString())) {
            hunt = null;
         }

         if (state != null && !state.dimension().equals(var1.level.dimension().location().toString())) {
            state = null;
            arrivalTicks = 0;
         }

         if (arrivalTicks > 0 && !var1.isPaused()) {
            arrivalTicks--;
         }

         while (JOURNAL.consumeClick()) {
            if (var1.player != null && var1.screen == null) {
               PacketDistributor.sendToServer(new HuntNetwork.Request(0), new CustomPacketPayload[0]);
            }
         }
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      state = null;
      hunt = null;
      assignments = null;
      arrivalTicks = 0;
      BloodParticle.clear();
      FlareParticle.clear();
   }

   private static void hud(GuiGraphics var0) {
      TrailClient.hud(var0);
      Minecraft var1 = Minecraft.getInstance();
      HuntNetwork.Snapshot var2 = state;
      if (var2 != null && var2.active() && var1.player != null && !var1.options.hideGui && var1.screen == null) {
         int var3 = var1.getWindow().getGuiScaledWidth();
         int var4 = var1.getWindow().getGuiScaledHeight();
         Wilderness.Wind var5 = new Wilderness.Wind((double)var2.windEast(), (double)var2.windSouth());
         if ((Boolean)HuntConfig.SHOW_HUD.get() && var2.tracking() && arrivalTicks <= 0) {
            String var6 = var2.region()
               + "  /  "
               + var5.directionTo()
               + "  "
               + String.format(Locale.ROOT, "%.1f m/s", var5.speed())
               + ScentOverlay.hudSuffix(var1.player);
            int var7 = Math.min(var3 - 12, var1.font.width(var6) + 20);
            int var8 = (var3 - var7) / 2;
            var0.fill(var8, 7, var8 + var7, 26, -1072291554);
            var0.fill(var8, 7, var8 + 2, 26, -2508680);
            var0.drawString(var1.font, var1.font.plainSubstrByWidth(var6, var7 - 16), var8 + 9, 13, -1448747, false);
         }

         if (arrivalTicks > 0) {
            float var9 = Math.min(1.0F, Math.min((float)(110 - arrivalTicks) / 16.0F, (float)arrivalTicks / 22.0F));
            int var10 = (int)(Math.max(0.05F, var9) * 255.0F);
            if (!(Boolean)HuntConfig.REDUCED_MOTION.get()) {
               int var11 = (int)((float)var4 * 0.075F * var9);
               var0.fill(0, 0, var3, var11, -721420288);
               var0.fill(0, var4 - var11, var3, var4, -721420288);
            }

            var0.drawCenteredString(var1.font, "F R O N T I E R   H U N T S", var3 / 2, var4 / 3, var10 << 24 | 15328469);
            var0.drawCenteredString(var1.font, var2.region(), var3 / 2, var4 / 3 + 18, var10 << 24 | 14268536);
         }
      }
   }
}
