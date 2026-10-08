package com.formaworks.frontierhunts.landscape.ride.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.landscape.AlpineRegistration;
import com.formaworks.frontierhunts.landscape.ride.Atv;
import com.formaworks.frontierhunts.landscape.ride.Paraglider;
import com.formaworks.frontierhunts.landscape.ride.RideContent;
import com.formaworks.frontierhunts.landscape.ride.RideHooks;
import com.formaworks.frontierhunts.landscape.ride.RidePayloads;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin.Model;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class RideClient {
   private static RidePayloads.HorseState horse;
   private static long horseAt;
   private static float wind = 1.0F;
   private static float windShown = 1.0F;
   private static Boolean sentGallop;
   private static final Map<Atv, AtvSound[]> SOUNDS = new WeakHashMap<>();

   private RideClient() {
   }

   static void horseState(RidePayloads.HorseState var0) {
      horse = var0;
      horseAt = System.currentTimeMillis();
      wind = var0.stamina();
   }

   private static boolean ridingHorse(LocalPlayer var0) {
      if (var0.getVehicle() instanceof AbstractHorse var1 && var1.getControllingPassenger() == var0) {
         return true;
      }

      return false;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      LocalPlayer var2 = var1.player;
      GliderRender.tick();
      if (var2 != null && var1.level != null) {
         if (ridingHorse(var2)) {
            boolean var3 = var1.options.keySprint.isDown();
            if (sentGallop == null || sentGallop != var3) {
               sentGallop = var3;
               if (hasChannel()) {
                  PacketDistributor.sendToServer(new RidePayloads.Gait(var3), new CustomPacketPayload[0]);
               }
            }

            if (horse != null
               && var2.getVehicle() instanceof AbstractHorse var4
               && (horse.stamina() < 0.35F || horse.gait() == 3)
               && var2.tickCount % (horse.gait() == 2 ? 6 : 12) == 0) {
               Vec3 var12 = Vec3.directionFromRotation(0.0F, var4.getYRot());
               Vec3 var6 = var4.position().add(var12.scale(1.25)).add(0.0, 1.65, 0.0);
               DeferredHolder var7 = AlpineRegistration.BREATH;
               if (var7.isBound()) {
                  var1.level.addParticle((ParticleOptions)var7.get(), var6.x, var6.y, var6.z, var12.x * 0.05, -0.01, var12.z * 0.05);
               }
            }
         } else if (sentGallop != null) {
            if (sentGallop && hasChannel()) {
               PacketDistributor.sendToServer(new RidePayloads.Gait(false), new CustomPacketPayload[0]);
            }

            sentGallop = null;
            horse = null;
         }

         windShown = windShown + (wind - windShown) * 0.2F;
         if (var2.tickCount % 10 == 0) {
            SOUNDS.entrySet().removeIf(var1x -> var1x.getValue()[0].isStopped() || var1x.getKey().isRemoved() || var1x.getKey().level() != var1.level);

            for (Atv var11 : var1.level.getEntitiesOfClass(Atv.class, var2.getBoundingBox().inflate(64.0), var0x -> var0x.isVehicle())) {
               AtvSound[] var13 = SOUNDS.get(var11);
               if (var13 == null || var13[0].isStopped()) {
                  var13 = AtvSound.engine(var11);
                  SOUNDS.put(var11, var13);

                  for (AtvSound var9 : var13) {
                     var1.getSoundManager().play(var9);
                  }
               }
            }
         }
      } else {
         horse = null;
         sentGallop = null;
         SOUNDS.clear();
      }
   }

   private static boolean hasChannel() {
      ClientPacketListener var0 = Minecraft.getInstance().getConnection();

      try {
         return var0 != null && var0.hasChannel(RidePayloads.Gait.TYPE);
      } catch (RuntimeException var2) {
         return false;
      }
   }

   static Atv.Controls atvInput(Player var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var0 == var1.player && (var1.screen == null || var1.screen.isPauseScreen())) {
         Input var2 = var1.player.input;
         float var3 = (var2.right ? 1.0F : 0.0F) - (var2.left ? 1.0F : 0.0F);
         return new Atv.Controls(var2.up ? 1.0F : 0.0F, var2.down ? 1.0F : 0.0F, var3, var2.jumping);
      } else {
         return new Atv.Controls(0.0F, 0.0F, 0.0F, false);
      }
   }

   static void gauges(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      LocalPlayer var2 = var1.player;
      if (var2 != null && !var1.options.hideGui && !LaunchCinematic.running()) {
         int var3 = var0.guiWidth();
         int var4 = var0.guiHeight();
         if (ridingHorse(var2) && horse != null && System.currentTimeMillis() - horseAt < 3000L) {
            byte var5 = 81;
            int var6 = var3 / 2 + 10;
            int var7 = var4 - 49;
            if (var2.isCreative()) {
               var7 = var4 - 32;
            }

            var0.fill(var6 - 1, var7 - 1, var6 + var5 + 1, var7 + 4, -1879048192);
            int var8 = (int)((float)var5 * Mth.clamp(windShown, 0.0F, 1.0F));
            int var9 = horse.gait() == 3 ? -3653329 : (windShown < 0.3F ? -2522581 : -1522070);
            var0.fill(var6, var7, var6 + var8, var7 + 3, var9);

            String var10 = switch (horse.gait()) {
               case 1 -> horse.pack() ? "pack pace" : "canter";
               case 2 -> "gallop";
               case 3 -> "blown";
               default -> "walk";
            };
            var0.drawString(var1.font, var10, var6 + var5 - var1.font.width(var10), var7 - 10, -520093697, true);
         }

         if (GliderClient.active) {
            Component var11 = GliderClient.instruments(var2);
            int var14 = var4 - (var2.isCreative() ? 44 : 58);
            var0.drawString(var1.font, var11, var3 / 2 - var1.font.width(var11) / 2, var14, GliderClient.climb > 0.005 ? -6298725 : -2039584, true);
         }

         if (WingsuitClient.active) {
            Component var12 = WingsuitClient.instruments(var2);
            int var15 = var4 - (var2.isCreative() ? 44 : 58);
            var0.drawString(var1.font, var12, var3 / 2 - var1.font.width(var12) / 2, var15, -2039584, true);
            int var18 = WingsuitClient.pullWarning();
            if (var18 > 0) {
               boolean var19 = var18 == 2 && var2.tickCount / 4 % 2 == 0;
               MutableComponent var21 = Component.translatable(
                  var18 == 2 ? "message.frontierhunts.wingsuit_pull_now" : "message.frontierhunts.wingsuit_pull",
                  new Object[]{var1.options.keyJump.getTranslatedKeyMessage()}
               );
               int var22 = var18 == 2 ? (var19 ? -46534 : -20320) : -11654;
               var0.pose().pushPose();
               var0.pose().translate((float)var3 / 2.0F, (float)var4 * 0.62F, 0.0F);
               var0.pose().scale(var18 == 2 ? 1.6F : 1.2F, var18 == 2 ? 1.6F : 1.2F, 1.0F);
               var0.drawString(var1.font, var21, -var1.font.width(var21) / 2, 0, var22, true);
               var0.pose().popPose();
            }
         }

         if (var2.getVehicle() instanceof Atv var13 && var13.getControllingPassenger() == var2) {
            double var17 = Math.abs(var13.shownSpeed) * 20.0 * 3.6;
            String var20 = var13.flooded ? "engine flooded" : String.format("%.0f km/h", var17);
            var0.drawString(
               var1.font, var20, var3 / 2 + 91 - var1.font.width(var20), var4 - 49 - (var2.isCreative() ? -17 : 0), var13.flooded ? -3121072 : -2039584, true
            );
         }
      }
   }

   @EventBusSubscriber(
      modid = "frontierhunts",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Setup {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent var0) {
         Paraglider.clientLaunch = LaunchCinematic::tryLaunch;
         RideHooks.horseState = RideClient::horseState;
         Atv.INPUT = RideClient::atvInput;
      }

      @SubscribeEvent
      public static void layers(RegisterLayerDefinitions var0) {
         var0.registerLayerDefinition(AtvModel.LAYER, AtvModel::createLayer);
         var0.registerLayerDefinition(WingsuitLayer.LAYER, WingsuitLayer::createLayer);
      }

      @SubscribeEvent
      public static void addLayers(AddLayers var0) {
         for (Model var2 : var0.getSkins()) {
            if (var0.getSkin(var2) instanceof PlayerRenderer var4) {
               WingsuitLayer.parentPosed = WingsuitLayer.insertPose(var4);
               var4.addLayer(new WingsuitLayer(var4, var0.getEntityModels()));
            }
         }
      }

      @SubscribeEvent
      public static void renderers(RegisterRenderers var0) {
         var0.registerEntityRenderer((EntityType)RideContent.ATV.get(), AtvRenderer::new);
      }

      @SubscribeEvent
      public static void hud(RegisterGuiLayersEvent var0) {
         var0.registerAbove(VanillaGuiLayers.HOTBAR, FrontierHunts.id("ride_gauges"), (var0x, var1) -> RideClient.gauges(var0x));
      }
   }
}
