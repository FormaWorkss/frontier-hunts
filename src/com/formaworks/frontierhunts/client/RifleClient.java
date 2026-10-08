package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.expedition.ExpeditionNetwork;
import com.formaworks.frontierhunts.expedition.AttachmentSpec;
import com.formaworks.frontierhunts.expedition.ExpeditionWeapon;
import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.rifle.RifleActions;
import com.formaworks.frontierhunts.rifle.RifleBullet;
import com.formaworks.frontierhunts.rifle.RifleContent;
import com.formaworks.frontierhunts.rifle.RifleItem;
import com.formaworks.frontierhunts.rifle.RifleMotion;
import com.formaworks.frontierhunts.rifle.RifleNetwork;
import com.formaworks.frontierhunts.rifle.RifleState;
import com.formaworks.frontierhunts.workshop.OpticUpgrade;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

@Mod(
   value = "frontierhunts",
   dist = {Dist.CLIENT}
)
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class RifleClient {
   public static final KeyMapping RELOAD = new KeyMapping("key.frontierhunts.reload", 71, "key.categories.frontierhunts");
   public static final KeyMapping CYCLE = new KeyMapping("key.frontierhunts.cycle", 86, "key.categories.frontierhunts");
   public static final KeyMapping FIRE = new KeyMapping("key.frontierhunts.fire", KeyConflictContext.IN_GAME, Type.MOUSE, 0, "key.categories.frontierhunts");
   public static final KeyMapping AIM = new KeyMapping("key.frontierhunts.aim", KeyConflictContext.IN_GAME, Type.MOUSE, 1, "key.categories.frontierhunts");
   private static boolean attack;
   private static boolean aimSent;
   private static float aim;
   private static float oldAim;
   private static float equip = 1.0F;
   private static float oldEquip = 1.0F;
   private static int heldSlot = -1;
   private static boolean wasHolding;
   private static long shotAt = -1000L;
   private static ResourceLocation dimension;

   public RifleClient(IEventBus var1) {
      var1.addListener((net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent var0) -> var0.registerReloadListener((ResourceManagerReloadListener)var0x -> CamoRifleModel.clear()));
      var1.addListener((net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent var0) -> {
         var0.register(RELOAD);
         var0.register(CYCLE);
         var0.register(FIRE);
         var0.register(AIM);
      });
      ArrowSupply.SWITCH_KEYS = () -> RELOAD.getTranslatedKeyMessage().getString() + " / " + CYCLE.getTranslatedKeyMessage().getString();
      // [rifle] the loose Ridgeline Hunting Scope item renders through the same renderer (set stays disjoint from other clients)
      var1.addListener((net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent var0) -> var0.registerItem(
         new RifleRenderer.Extensions(), new Item[]{(Item)RifleContent.RIFLE.get(), (Item)RifleContent.AMMO.get(), (Item)RifleContent.SCOPE.get()}));
      var1.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers var1x) -> var1x.registerEntityRenderer(RifleContent.BULLET.get(), var1xx -> new EntityRenderer<RifleBullet>(var1xx) {
               public ResourceLocation getTextureLocation(RifleBullet var1) {
                  return WhitetailRenderer.MATERIAL;
               }
            }));
      var1.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent var0) -> var0.registerAboveAll(FrontierHunts.id("rifle"), (var0x, var1x) -> hud(var0x)));
      RifleNetwork.receiver = RifleClient::shot;
   }

   private static boolean holding(Minecraft var0) {
      return var0.player != null && var0.player.getMainHandItem().getItem() instanceof RifleItem;
   }

   public static float aim(float var0) {
      return oldAim + (aim - oldAim) * var0;
   }

   public static float equip(float var0) {
      return oldEquip + (equip - oldEquip) * var0;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      oldAim = aim;
      oldEquip = equip;
      if (var1.level != null && var1.player != null) {
         if (!var1.level.dimension().location().equals(dimension)) {
            reset();
            dimension = var1.level.dimension().location();
         }

         if (!var1.isPaused()) {
            boolean var2 = holding(var1);
            boolean var3 = var2 && var1.screen == null;
            int var4 = var1.player.getInventory().selected;
            if (!var2) {
               oldEquip = 1.0F;
               equip = 1.0F;
            } else if (wasHolding && heldSlot == var4) {
               equip = Math.max(0.0F, equip - 0.2F);
            } else {
               oldEquip = 1.0F;
               equip = 1.0F;
            }

            wasHolding = var2;
            heldSlot = var4;
            RifleState var5 = var2 ? RifleState.read(var1.player.getMainHandItem()) : RifleState.EMPTY;
            boolean var6 = var3 && AIM.isDown() && var5.action() == 0 && RifleActions.stableMount(var1.player) && !var1.player.isUnderWater();
            if (var6 != aimSent || var6 && var1.player.tickCount % 20 == 0) {
               PacketDistributor.sendToServer(new RifleNetwork.Aim(var6), new CustomPacketPayload[0]);
               aimSent = var6;
            }

            aim = aim + Math.clamp((float)(var6 ? 1 : 0) - aim, -0.2F, 0.16F);
            boolean var7 = var3 && FIRE.isDown() && !ExpeditionClient.targetingGunRack(var1);
            if (var7 && !attack) {
               PacketDistributor.sendToServer(new RifleNetwork.Action(0), new CustomPacketPayload[0]);
            }

            attack = var7;
            boolean var8 = ArrowSupply.arrowBow(var1.player.getMainHandItem());
            if (var8) {
               boolean var9 = false;

               while (RELOAD.consumeClick()) {
                  var9 = true;
               }

               while (CYCLE.consumeClick()) {
                  var9 = true;
               }

               if (var9 && var1.screen == null) {
                  PacketDistributor.sendToServer(new ExpeditionNetwork.Request(20, ""), new CustomPacketPayload[0]);
               }
            }

            while (RELOAD.consumeClick()) {
               if (var3) {
                  PacketDistributor.sendToServer(new RifleNetwork.Action(1), new CustomPacketPayload[0]);
               } else if (var1.player.getMainHandItem().getItem() instanceof ExpeditionWeapon) {
                  PacketDistributor.sendToServer(new ExpeditionNetwork.Request(11, ""), new CustomPacketPayload[0]);
               }
            }

            while (CYCLE.consumeClick()) {
               if (var3) {
                  PacketDistributor.sendToServer(new RifleNetwork.Action(2), new CustomPacketPayload[0]);
               }
            }
         }
      } else {
         reset();
      }
   }

   @SubscribeEvent
   public static void input(InteractionKeyMappingTriggered var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (holding(var1) && (var0.isAttack() || var0.isUseItem() && !ExpeditionClient.targetingGunRack(var1))) {
         var0.setCanceled(true);
         var0.setSwingHand(false);
      }
   }

   @SubscribeEvent
   public static void fov(ComputeFovModifierEvent var0) {
      if (var0.getPlayer() == Minecraft.getInstance().player && holding(Minecraft.getInstance())) {
         var0.setNewFovModifier(var0.getNewFovModifier() * (1.0F - (OpticUpgrade.fitted(var0.getPlayer().getMainHandItem()) ? 0.715F : 0.62F) * aim));
      }
   }

   @SubscribeEvent
   public static void camera(ComputeCameraAngles var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && holding(var1) && !(Boolean)HuntConfig.REDUCED_MOTION.get()) {
         float var2 = RifleMotion.recoil((double)var1.level.getGameTime() + var0.getPartialTick() - (double)shotAt);
         // [rifle] prone rests the rifle on its bipod (or the shooter's elbows): much less kick
         boolean prone = com.formaworks.frontierhunts.prone.Prone.isProne(var1.player);
         if ((var1.player.isCrouching() || prone) && ExpeditionWeapon.attachment(var1.player.getMainHandItem(), "bipod")) {
            var2 *= prone ? 0.35F : 0.45F;
         } else if (prone) {
            var2 *= 0.6F;
         } else if (com.formaworks.frontierhunts.sticks.ShootingSticks.rested(var1.player)) {
            var2 *= com.formaworks.frontierhunts.sticks.ShootingSticks.RECOIL; // [sticks] the yoke takes the kick
         }

         var2 *= com.formaworks.frontierhunts.HuntConfig.shake(); // [1.1.5] comfort: camera shake setting
         var0.setPitch(var0.getPitch() - var2 * 1.15F);
         var0.setRoll(var0.getRoll() + var2 * 0.22F);
      }
   }

   @SubscribeEvent
   public static void hand(RenderHandEvent var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (holding(var1)) {
         if (var0.getHand() == InteractionHand.MAIN_HAND) {
            var0.setCanceled(true);
            float var2 = aim(var0.getPartialTick());
            // [rifle] the fitted sight decides the aim line and whether full aim shows an eyepiece (gun hidden)
            String sight = RidgelineSights.sight(var1.player.getMainHandItem());
            if (!(var2 > 0.985F) || !RidgelineSights.overlay(sight)) {
               RifleState var3 = RifleState.read(var1.player.getMainHandItem());
               double var4 = (double)((float)var1.level.getGameTime() + var0.getPartialTick());
               float var6 = HuntConfig.REDUCED_MOTION.get() ? 0.0F : RifleMotion.recoil(var4 - (double)shotAt);
               float var7 = RifleMotion.progress(var3, var4);
               float var8 = var3.action() == 0 ? 0.0F : (float)Math.sin((double)var7 * Math.PI);
               float var9 = var1.player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;
               PoseStack var10 = var0.getPoseStack();
               var10.pushPose();
               float axis = (float)RidgelineSights.axisY(sight) * 1.35F;
               float eye = (float)RidgelineSights.eyeZ(sight) * 1.35F;
               // [sticks] rested on shooting sticks: the rifle lies in the yoke (see SticksView)
               float sticksRest = com.formaworks.frontierhunts.sticks.client.SticksView.rest(var0.getPartialTick());
               float sdx = 0.0F, sdy = 0.0F, sdz = 0.0F, sticksTilt = 0.0F, sticksYaw = 0.0F, sticksRoll = 0.0F;
               if (sticksRest > 0.0F) {
                  float bx = var9 * 0.3F * (1.0F - var2);
                  float by = -0.315F * (1.0F - var2) - axis * var2;
                  float bz = -0.42F * (1.0F - var2) - eye * var2;
                  com.formaworks.frontierhunts.sticks.client.SticksView.pose(sticksRest, var2, var9, bx, by, bz, axis, eye);
                  sdx = (float)com.formaworks.frontierhunts.sticks.client.SticksView.dx;
                  sdy = (float)com.formaworks.frontierhunts.sticks.client.SticksView.dy;
                  sdz = (float)com.formaworks.frontierhunts.sticks.client.SticksView.dz;
                  sticksTilt = com.formaworks.frontierhunts.sticks.client.SticksView.tilt;
                  sticksYaw = com.formaworks.frontierhunts.sticks.client.SticksView.yaw;
                  sticksRoll = com.formaworks.frontierhunts.sticks.client.SticksView.roll;
                  com.formaworks.frontierhunts.sticks.client.SticksView.anchor(bx + sdx, by + sdy, bz + sdz, 0.04, -0.3, 1.35, var9, -3.0F * (1.0F - var2));
               }
               var10.translate(
                  var9 * 0.3F * (1.0F - var2) + sdx,
                  -0.315F * (1.0F - var2) - axis * var2 - equip(var0.getPartialTick()) * 0.3F - var8 * 0.04F + sdy,
                  -0.42F * (1.0F - var2) - eye * var2 + var6 * 0.038F + sdz
               );
               var10.mulPose(Axis.XP.rotationDegrees(var6 * 3.1F + var8 * 5.0F + sticksTilt));
               if (sticksYaw != 0.0F) {
                  var10.mulPose(Axis.YP.rotationDegrees(var9 * sticksYaw));
               }
               var10.mulPose(Axis.ZP.rotationDegrees(var9 * (-3.0F * (1.0F - var2) + sticksRoll - var8 * 12.0F)));
               var10.pushPose();
               if (var9 < 0.0F) {
                  var10.scale(-1.0F, 1.0F, 1.0F);
               }

               var10.scale(1.35F, 1.35F, 1.35F);
               ItemStack var11 = var1.player.getMainHandItem();
               boolean var12 = ExpeditionWeapon.attachment(var11, "bipod");
               RidgelineModel.draw(
                  var10,
                  var0.getMultiBufferSource(),
                  var0.getPackedLight(),
                  var3,
                  var4,
                  var12,
                  var12 ? FieldAttachmentHardware.bipodDeploy(var1.player, var1.player.isCrouching(), var4) : 0.0F,
                  sight
               );
               var10.popPose();
               var10.popPose();
            }
         }
      }
   }

   private static void shot(RifleNetwork.Shot var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && var1.level.dimension().location().toString().equals(var0.dimension()) && Double.isFinite(var0.x() + var0.y() + var0.z())) {
         if (var1.player != null && var1.player.getId() == var0.shooter()) {
            shotAt = var1.level.getGameTime();
         }

         Vec3 var2 = new Vec3(var0.x(), var0.y(), var0.z());
         if (!(var1.gameRenderer.getMainCamera().getPosition().distanceToSqr(var2) > 4096.0)) {
            byte var3 = switch ((HuntConfig.Quality)HuntConfig.QUALITY.get()) {
               case PERFORMANCE -> 3;
               case BALANCED -> 6;
               case CINEMATIC -> 9;
            };
            Vec3 var4 = Vec3.directionFromRotation(var0.pitch(), var0.yaw());

            for (int var5 = 0; var5 < var3; var5++) {
               var1.level
                  .addParticle(
                     ParticleTypes.SMOKE,
                     var2.x,
                     var2.y,
                     var2.z,
                     var4.x * 0.09 + (var1.level.random.nextDouble() - 0.5) * 0.025,
                     0.014 + var4.y * 0.09,
                     var4.z * 0.09 + (var1.level.random.nextDouble() - 0.5) * 0.025
                  );
            }

            // [1.1.6] no firework FLASH particle: it showed as a bright white burst at the muzzle on every shot
         }
      }
   }

   private static void hud(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (holding(var1) && var1.screen == null && !var1.options.hideGui) {
         int var2 = var1.getWindow().getGuiScaledWidth();
         int var3 = var1.getWindow().getGuiScaledHeight();
         if (aim > 0.985F) {
            // [rifle] eyepiece for magnified optics; the thermal (when powered), red dots and prism are drawn by
            // ExpeditionOptics, the iron sights are the rifle itself
            ItemStack held = var1.player.getMainHandItem();
            String sight = RidgelineSights.sight(held);
            boolean thermalLive = sight.equals("thermal_scope") && com.formaworks.frontierhunts.expedition.FieldElectronics.available(held);
            if (RidgelineSights.overlay(sight) && !thermalLive && !sight.equals("two_power_prism")) {
               FieldScopeView.draw(var0, (double)AttachmentSpec.magnification(held, com.formaworks.frontierhunts.expedition.Weapon.LEVER_RIFLE), false);
            }
         }

         RifleState var4 = RifleState.read(var1.player.getMainHandItem());
         String var5 = var4.action() == 2
            ? "RELOADING"
            : (var4.action() == 1 ? "CYCLING" : (var4.chamber() ? "READY" : (var4.spent() ? "CYCLE BOLT" : "CHAMBER EMPTY")));
         String var6 = String.format(Locale.ROOT, "%d + %d   /   .308   |   %s", var4.magazine(), var4.chamber() ? 1 : 0, var5);
         int var7 = var2 - 16 - var1.font.width(var6);
         int var8 = var3 - 52;
         var0.fill(var7 - 7, var8 - 5, var2 - 9, var8 + 15, -1072094175);
         var0.drawString(var1.font, var6, var7, var8, -1383477, false);
         if (var4.action() != 0) {
            float var9 = RifleMotion.progress(var4, (double)var1.level.getGameTime());
            var0.fill(var7 - 7, var8 + 16, var7 - 7 + (int)((float)(var2 - var7 - 2) * var9), var8 + 18, -3955353);
         }

         var0.drawString(
            var1.font, RELOAD.getTranslatedKeyMessage().getString() + " Reload · " + var4.limit() + " rounds per load", var7, var8 + 23, -3617346, false
         );
      }
   }

   private static void reset() {
      oldAim = 0.0F;
      aim = 0.0F;
      oldEquip = 1.0F;
      equip = 1.0F;
      heldSlot = -1;
      wasHolding = false;
      aimSent = false;
      attack = false;
      shotAt = -1000L;
      dimension = null;
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      reset();
   }
}
