package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.workshop.FieldFloat;
import com.formaworks.frontierhunts.workshop.FieldRodItem;
import com.formaworks.frontierhunts.workshop.FishingNetwork;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.RenderLivingEvent.Pre;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@Mod(
   value = "frontierhunts",
   dist = {Dist.CLIENT}
)
@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class FishingClient {
   private static LivingEntity holder;
   private static final Map<UUID, Vec3> TIPS = new HashMap<>();
   private static final Map<UUID, Long> TIP_AT = new HashMap<>(); // [bows] when each third-person tip was captured
   private static Vec3 firstTip;
   private static long firstTipAt; // [bows] game time of the last first-person capture
   private static float flex;
   private static float oldFlex;

   public FishingClient(IEventBus var1) {
      var1.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers var0) -> var0.registerEntityRenderer((EntityType<FieldFloat>)WorkshopContent.FLOAT.get(), FieldFloatRenderer::new));
      var1.addListener((net.neoforged.neoforge.client.event.RegisterGuiLayersEvent var0) -> var0.registerAboveAll(FrontierHunts.id("fishing"), (var0x, var1x) -> hud(var0x)));
   }

   public static FieldFloat active() {
      Minecraft var0 = Minecraft.getInstance();
      if (var0.player != null && var0.level != null) {
         for (Entity var2 : var0.level.entitiesForRendering()) {
            if (var2 instanceof FieldFloat var3 && var3.owner() == var0.player) {
               return var3;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static float flex(float var0) {
      return oldFlex + (flex - oldFlex) * var0;
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      oldFlex = flex;
      if (var1.player != null && var1.level != null) {
         FieldFloat var2 = active();
         float var3 = var2 == null
            ? (var1.player.isUsingItem() ? Math.min(1.0F, (float)var1.player.getTicksUsingItem() / 30.0F) * 0.045F : 0.0F)
            : var2.tension() * 0.19F;
         flex = flex + (var3 - flex) * 0.25F;
      } else {
         TIPS.clear();
         TIP_AT.clear();
         firstTip = null;
         oldFlex = 0.0F;
         flex = 0.0F;
      }
   }

   @SubscribeEvent
   public static void before(Pre<?, ?> var0) {
      holder = var0.getEntity();
   }

   @SubscribeEvent
   public static void after(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> var0) {
      holder = null;
   }

   public static void capture(PoseStack var0, ItemDisplayContext var1, float var2) {
      Minecraft var3 = Minecraft.getInstance();
      if (HuntShaderCompat.shadowPass()) {
         return; // [bows] the shadow pass draws from the sun's view: its poses are not the player's
      }
      if (var1.firstPerson()) {
         // [bows] camera-local rod tip, right with or without shaders and at any FOV (see HandSpace)
         Vector3f var5 = HandSpace.camera(var0, 0.0F, 1.1F, var2);
         firstTip = new Vec3((double)var5.x, (double)var5.y, (double)var5.z);
         firstTipAt = var3.level == null ? 0L : var3.level.getGameTime();
         return;
      }
      Vector3f var4 = var0.last().pose().transformPosition(new Vector3f(0.0F, 1.1F, var2));
      if (holder != null && (var1 == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || var1 == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
         TIPS.put(holder.getUUID(), new Vec3((double)var4.x, (double)var4.y, (double)var4.z).add(var3.gameRenderer.getMainCamera().getPosition()));
         TIP_AT.put(holder.getUUID(), var3.level == null ? 0L : var3.level.getGameTime()); // [bows]
      }
   }

   public static Vec3 tip(Entity var0, float var1) {
      Minecraft var2 = Minecraft.getInstance();
      long now = var2.level == null ? 0L : var2.level.getGameTime();
      if (var0 == var2.player && var2.options.getCameraType().isFirstPerson()) {
         // [bows] fresh capture from the hand pass; else (rod just put away) a point out on the rod's side, never the middle
         Vec3 local = firstTip != null && now - firstTipAt <= 2L && now >= firstTipAt ? firstTip : new Vec3(side(var0) * 0.36, 0.08, -0.62);
         Vector3f var3 = var2.gameRenderer.getMainCamera().rotation().transform(new Vector3f((float)local.x, (float)local.y, (float)local.z));
         return var2.gameRenderer.getMainCamera().getPosition().add((double)var3.x, (double)var3.y, (double)var3.z);
      } else {
         Vec3 known = TIPS.get(var0.getUUID());
         Long at = TIP_AT.get(var0.getUUID());
         if (known != null && at != null && now - at <= 2L && now >= at) {
            return known;
         }
         // [bows] not drawn this frame (behind the camera): out over the rod hand, not the chest
         float yaw = var0 instanceof LivingEntity le ? le.yBodyRot * (float)(Math.PI / 180.0) : var0.getYRot() * (float)(Math.PI / 180.0);
         double s = side(var0) * 0.38;
         return var0.getEyePosition(var1).add(var0.getLookAngle().scale(0.9)).add(-Math.cos(yaw) * s, 0.25, -Math.sin(yaw) * s);
      }
   }

   /** [bows] +1 when the rod is in the right hand, -1 in the left (left-handed main arm, or the off hand). */
   static double side(Entity e) {
      if (!(e instanceof net.minecraft.world.entity.player.Player p)) {
         return 1.0;
      }
      boolean main = p.getMainHandItem().getItem() instanceof FieldRodItem;
      net.minecraft.world.entity.HumanoidArm arm = main ? p.getMainArm() : p.getMainArm().getOpposite();
      return arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;
   }

   @SubscribeEvent
   public static void attack(InteractionKeyMappingTriggered var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.player.getMainHandItem().getItem() instanceof FieldRodItem && var0.isAttack()) {
         var0.setCanceled(true);
         var0.setSwingHand(false);
         if (active() != null) {
            PacketDistributor.sendToServer(new FishingNetwork.Strike(), new CustomPacketPayload[0]);
         }
      }
   }

   private static void hud(GuiGraphics var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.player != null && var1.screen == null && !var1.options.hideGui && var1.player.getMainHandItem().getItem() instanceof FieldRodItem) {
         FieldFloat var2 = active();
         String var3 = var1.options.keyUse.getTranslatedKeyMessage().getString();
         String var4 = var1.options.keyAttack.getTranslatedKeyMessage().getString();
         String var10000;
         if (var2 == null) {
            var10000 = var1.player.isUsingItem() ? "Release to cast" : "Hold " + var3 + " to load cast";
         } else {
            switch (var2.state()) {
               case 2:
                  var10000 = var4 + " · STRIKE";
                  break;
               case 3:
                  var10000 = var2.stamina() < 0.12F ? var3 + " · Reel / approach the bank to land" : var3 + " · Reel — ease off when tension rises";
                  break;
               case 4:
                  var10000 = var3 + " · Recover tackle";
                  break;
               default:
                  var10000 = var3 + " · Reel in / Watch the float";
            }
         }

         String var5 = var10000;
         short var6 = 296;
         int var7 = (var0.guiWidth() - var6) / 2;
         int var8 = var0.guiHeight() - 84;
         var0.fill(var7, var8, var7 + var6, var8 + 37, -803722970);
         var0.drawCenteredString(var1.font, var5, var0.guiWidth() / 2, var8 + 6, -1449271);
         float var9 = var2 != null ? var2.tension() : (var1.player.isUsingItem() ? Math.min(1.0F, (float)var1.player.getTicksUsingItem() / 30.0F) : 0.0F);
         var0.fill(var7 + 12, var8 + 23, var7 + var6 - 12, var8 + 29, -13087421);
         var0.fill(var7 + 12, var8 + 23, var7 + 12 + (int)((float)(var6 - 24) * var9), var8 + 29, (double)var9 > 0.85 ? -5025483 : -3951251);
         if (var2 != null) {
            var0.drawString(
               var1.font,
               String.format(Locale.ROOT, "%.1f m line%s", var2.line(), var2.state() == 3 ? " · fish " + Math.round(var2.stamina() * 100.0F) + "%" : ""),
               var7 + 12,
               var8 + 41,
               -2171702,
               false
            );
         }
      }
   }

   @SubscribeEvent
   public static void logout(LoggingOut var0) {
      TIPS.clear();
      TIP_AT.clear();
      firstTip = null;
      holder = null;
      oldFlex = 0.0F;
      flex = 0.0F;
   }
}
