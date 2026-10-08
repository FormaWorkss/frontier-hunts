package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionGear;
import com.formaworks.frontierhunts.expedition.ExpeditionNetwork;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.formaworks.frontierhunts.expedition.ExpeditionStation;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.expedition.FieldCamera;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.ScentControl;
import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(
   value = "frontierhunts",
   dist = {Dist.CLIENT}
)
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class ExpeditionClient {
   public static JsonObject state = new JsonObject();
   private static final KeyMapping GUIDE = new KeyMapping("key.frontierhunts.expedition", 78, "key.categories.frontierhunts");
   public static final KeyMapping PACK = new KeyMapping("key.frontierhunts.pack", 79, "key.categories.frontierhunts");
   private static boolean fired;
   private static boolean aimed;
   private static int cinematicTicks;
   private static String cinematic = "";
   private static boolean intro;
   private static int aimSlot = -1;
   private static long automaticNext;
   private static int fireSlot = -1;

   public ExpeditionClient(IEventBus var1) {
      FieldCamera.open = () -> Minecraft.getInstance().setScreen(new FieldPhotoMode());
      ScoutingNetwork.hubReceiver = CameraHubScreen::open;
      ScoutingNetwork.rollReceiver = var0 -> TrailCameraScreen.open(var0, CameraConsole.console(var0));
      var1.addListener(var0 -> {
         var0.register(CampingTentRenderer.setupModel(false));
         var0.register(CampingTentRenderer.setupModel(true));
         var0.register(StationStorageRenderer.LID);
      });
      var1.addListener(var0 -> var0.registerBlockEntityRenderer((BlockEntityType)ExpeditionContent.TENT_ANCHOR.get(), CampingTentRenderer::new));
      var1.addListener(var0 -> {
         var0.register(FrontierHunts.id("reserve_roof"), ReserveRoofGeometry.LOADER);
         var0.register(FrontierHunts.id("field_shelter"), FieldShelterGeometry.LOADER);
      });
      var1.addListener(var0 -> var0.registerBlockEntityRenderer((BlockEntityType)ExpeditionContent.STORAGE.get(), StationStorageRenderer::new));
      var1.addListener(var0 -> var0.registerBlockEntityRenderer((BlockEntityType)ExpeditionContent.TARGET_FACE.get(), TargetRenderer::new));
      var1.addListener(var0 -> {
         var0.register(GUIDE);
         var0.register(PACK);
      });
      ExpeditionGear.PACK_KEY = () -> PACK.getTranslatedKeyMessage().getString();
      var1.addListener(var0 -> {
         var0.registerEntityRenderer((EntityType)ExpeditionContent.PROJECTILE.get(), HuntProjectileRenderer::new);
         var0.registerEntityRenderer((EntityType)ExpeditionContent.STAND_SEAT.get(), NoopRenderer::new);
         var0.registerEntityRenderer((EntityType)ExpeditionContent.LENS_VIEW.get(), NoopRenderer::new);
         var0.registerEntityRenderer((EntityType)ExpeditionContent.TROPHY_MOUNT.get(), TrophyMountRenderer::new);
      });
      var1.addListener(
         var0 -> {
            Item[] var1x = ExpeditionContent.ITEMS
               .values()
               .stream()
               .map(var0x -> (Item)var0x.get())
               .filter(var0x -> !(var0x instanceof SpawnEggItem) && !(var0x instanceof GhillieSuit) && !(var0x instanceof ScentControl))
               .toArray(Item[]::new);
            var0.registerItem(new ExpeditionItemRenderer.Extensions(), var1x);
         }
      );
      var1.addListener(var0 -> var0.registerAboveAll(FrontierHunts.id("expedition"), (var0x, var1x) -> hud(var0x)));
      var1.addListener(var0 -> var0.registerReloadListener((ResourceManagerReloadListener)var0x -> {
            FieldWeaponMesh.clear();
            TrophyDisplay.clear();
         }));
      ExpeditionService.clientTracker = var0 -> state.has("skills") ? state.getAsJsonArray("skills").get(1).getAsInt() : 0;
      ExpeditionNetwork.receiver = var0 -> {
         Minecraft var1x = Minecraft.getInstance();
         if (var1x.level != null && var1x.level.dimension().location().toString().equals(var0.dimension())) {
            JsonObject var2 = JsonParser.parseString(var0.json()).getAsJsonObject();
            if (var2.has("cinematic")) {
               cinematic = var2.get("cinematic").getAsString();
               intro = var2.get("intro").getAsBoolean();
               cinematicTicks = 80;
               if (var2.has("entity")) {
                  HuntCinematics.start(var2.get("entity").getAsInt(), intro);
               }
            } else {
               state = var2;
               if (var2.get("open").getAsBoolean()) {
                  var1x.setScreen(new ExpeditionScreen());
               } else if (var1x.screen instanceof ExpeditionScreen var3) {
                  var3.refresh();
               }
            }
         }
      };
   }

   public static void send(int var0, String var1) {
      PacketDistributor.sendToServer(new ExpeditionNetwork.Request(var0, var1), new CustomPacketPayload[0]);
   }

   public static boolean aiming() {
      Minecraft var0 = Minecraft.getInstance();
      LocalPlayer var1 = var0.player;
      return var1 != null
         && var0.level != null
         && var0.screen == null
         && !var1.isSpectator()
         && var1.isAlive()
         && !var1.isUnderWater()
         && var1.getMainHandItem().getItem() instanceof ExpeditionWeapon var2
         && !var2.weapon.bow
         && RifleClient.AIM.isDown()
         && ExpeditionWeapon.data(var1.getMainHandItem()).getLong("reload_until") == 0L;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.player != null) {
         if (cinematicTicks > 0 && !var1.isPaused()) {
            cinematicTicks--;
         }

         while (GUIDE.consumeClick()) {
            if (var1.screen == null) {
               send(0, "");
            }
         }

         while (PACK.consumeClick()) {
            if (var1.screen == null) {
               if (var1.player.isShiftKeyDown()) {
                  var1.setScreen(new FieldGearScreen());
               } else {
                  send(21, "");
               }
            }
         }

         boolean var10000;
         label122: {
            if (var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon var3 && !var3.weapon.bow) {
               var10000 = true;
               break label122;
            }

            var10000 = false;
         }

         boolean var2 = var10000;
         boolean var11 = var2 && var1.screen == null;
         boolean var12 = aiming();
         int var5 = var1.player.getInventory().selected;
         if (var12 && !aimed && var1.player.isShiftKeyDown() && !var1.player.getOffhandItem().isEmpty()) {
            send(16, "");
         }

         if (var12 != aimed || var12 && (var5 != aimSlot || var1.player.tickCount % 20 == 0)) {
            send(15, var12 ? "1" : "0");
         }

         aimed = var12;
         aimSlot = var5;
         boolean var6 = var11 && RifleClient.FIRE.isDown() && !targetingGunRack(var1);
         boolean var7 = var2 && ((ExpeditionWeapon)var1.player.getMainHandItem().getItem()).weapon.automatic();
         if (!var6 || fireSlot != var5) {
            automaticNext = 0L;
            if (fireSlot != var5) {
               fired = false;
            }
         }

         fireSlot = var5;
         long var8 = var1.level.getGameTime();
         if (var6 && (!fired || var7 && var8 >= automaticNext && ExpeditionWeapon.data(var1.player.getMainHandItem()).getInt("rounds") > 0)) {
            ItemStack var10 = var1.player.getMainHandItem();
            if (!fired || var7) {
               FieldGunEffects.tryPredict(var1.player, var10, ((ExpeditionWeapon)var10.getItem()).weapon, var8);
            }

            send(10, "");
            automaticNext = var8 + 1L;
         }

         fired = var6;
      } else {
         state = new JsonObject();
         cinematicTicks = 0;
         aimed = false;
         fired = false;
         aimSlot = -1;
      }
   }

   public static boolean targetingGunRack(Minecraft var0) {
      if (var0.level != null && var0.hitResult instanceof BlockHitResult var1) {
         if (var0.level.getBlockState(var1.getBlockPos()).getBlock() instanceof ExpeditionStation var3 && var3.id.equals("gun_rack")) {
            return true;
         }

         return false;
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void input(InteractionKeyMappingTriggered var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null
         && var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon var2
         && !var2.weapon.bow
         && (var0.isAttack() || var0.isUseItem() && !targetingGunRack(var1))) {
         var0.setCanceled(true);
         var0.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFovModifierEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      Player var2 = var0.getPlayer();
      if (var2 == var1.player && var1.options.getCameraType().isFirstPerson()) {
         ItemStack var3 = var2.getMainHandItem();
         if (var2.isUsingItem()
            && var2.getUseItem().getItem() instanceof ExpeditionGear var4
            && (var4.id.contains("binoculars") || var4.id.equals("rangefinder"))) {
            float var10 = FieldOpticPresentation.progress(1.0F);
            float var6 = var4.id.equals("rangefinder") ? 0.2F : 0.16F;
            var0.setNewFovModifier(1.0F + (var6 - 1.0F) * var10);
         }

         if (var3.getItem() instanceof ExpeditionWeapon var9 && !var9.weapon.bow) {
            float var12 = AttachmentSpec.magnification(var3, var9.weapon);
            double var13 = Math.toRadians((double)((Integer)var1.options.fov().get()).intValue());
            float var8 = (float)(2.0 * Math.atan(Math.tan(var13 / 2.0) / (double)var12) / var13);
            var0.setNewFovModifier(var0.getNewFovModifier() * (1.0F + (var8 - 1.0F) * FieldWeaponFirstPerson.aim(1.0F)));
         }
      }
   }

   private static void hud(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.level != null && var1.screen == null && !var1.options.hideGui) {
         int var2 = var0.guiWidth();
         int var3 = var0.guiHeight();
         if (cinematicTicks > 0 && HuntCinematics.active()) {
            if (!(Boolean)HuntConfig.REDUCED_MOTION.get()) {
               var0.fill(0, 0, var2, Math.min(30, var3 / 10), -402653184);
               var0.fill(0, var3 - Math.min(30, var3 / 10), var2, var3, -402653184);
            }

            var0.drawCenteredString(var1.font, cinematic.toUpperCase(Locale.ROOT), var2 / 2, var3 / 4, -1123922);
            var0.drawCenteredString(
               var1.font, intro ? "THE HUNT BEGINS · MOVE TO SKIP" : "HUNT RESOLVED · SKINNING KNIFE TO RECOVER", var2 / 2, var3 / 4 + 17, -2172472
            );
         }

         if (var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon var4) {
            CompoundTag var10 = ExpeditionWeapon.data(var1.player.getMainHandItem());
            String var6 = var4.weapon == Weapon.BOWFISHING_BOW && BowfishingClient.active(var1.player) != null
               ? "Hold use to reel · ease off under strain"
               : (
                  var4.weapon == Weapon.BOWFISHING_BOW && var1.player.isUsingItem() && var10.getBoolean("bow_retrieving")
                     ? "Catch landed · release use"
                     : (
                        var4.weapon.bow
                           ? "Hold use to draw · release to fire"
                           : (
                              var10.getLong("reload_until") > var1.level.getGameTime()
                                 ? "Reloading…"
                                 : var10.getInt("rounds")
                                    + " / "
                                    + var4.capacity(var1.player.getMainHandItem())
                                    + "   ·   "
                                    + RifleClient.RELOAD.getTranslatedKeyMessage().getString()
                                    + " reload"
                           )
                     )
               );
            int var7 = var2 - 12 - var1.font.width(var6);
            var0.fill(var7 - 7, var3 - 58, var2 - 7, var3 - 39, -803921636);
            var0.drawString(var1.font, var6, var7, var3 - 53, -1515056, false);
         }

         ArcheryHud.draw(var0, var1, var2, var3);
         FishFinderHud.draw(var0, var1, var2, var3);
         if (var1.player.getMainHandItem().is(ExpeditionContent.item("bowfishing_bow"))) {
            for (Entity var11 : var1.level.entitiesForRendering()) {
               if (var11 instanceof HuntProjectile var12 && var12.tethered() && var12.getOwner() == var1.player) {
                  int var13 = var2 / 2 - 60;
                  int var8 = var3 - 92;
                  var0.fill(var13, var8, var13 + 120, var8 + 7, -15261154);
                  var0.fill(var13, var8, var13 + (int)(var12.strain() * 120.0F), var8 + 7, (double)var12.strain() > 0.9 ? -4959169 : -4735626);
                  var0.drawCenteredString(var1.font, "LINE TENSION · fish energy " + (int)(var12.stamina() * 100.0F) + "%", var2 / 2, var8 - 12, -2239050);
                  break;
               }
            }
         }

         ExpeditionOptics.hud(var0);
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      state = new JsonObject();
      cinematicTicks = 0;
      aimed = false;
      fired = false;
   }
}
