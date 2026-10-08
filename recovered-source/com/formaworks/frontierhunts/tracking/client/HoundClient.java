package com.formaworks.frontierhunts.tracking.client;

import com.formaworks.frontierhunts.tracking.hound.HoundNet;
import com.formaworks.frontierhunts.tracking.hound.TrackingHound;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * [hound3] The hunter's side of the hound: the call key (default ` - tap: come, hold: command wheel), the command wheel
 * from right-clicking the hound or using the Hound Lead, and a one-line HUD readout of what he is doing while he is
 * within 96 blocks. Only asks the server; every order is validated there (HoundCommands).
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class HoundClient {
   public static final KeyMapping CALL = new KeyMapping(
      "key.frontierhunts.hound", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.frontierhunts");
   /** hold the key this long (ticks) to open the wheel; a shorter tap calls him in */
   static final int HOLD = 5;
   static HoundNet.Info info;
   private static int held = -1;
   private static long sentAt = Long.MIN_VALUE;
   private static TrackingHound cached;
   private static int scanAt;

   private HoundClient() {
   }

   @EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
   public static final class Setup {
      private Setup() {
      }

      @SubscribeEvent
      public static void keys(RegisterKeyMappingsEvent e) {
         e.register(CALL);
      }

      @SubscribeEvent
      public static void setup(FMLClientSetupEvent e) {
         HoundNet.infoReceiver = i -> info = i;
         HoundNet.openWheel = h -> open(false);
         HoundNet.leadWheel = HoundClient::leadWheel;
      }
   }

   /** the local player's own hound if it is loaded here and within {@code r} blocks */
   static TrackingHound mine(double r) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.level == null) {
         return null;
      }
      if (cached != null && (cached.isRemoved() || cached.level() != mc.level)) {
         cached = null;
      }
      if (cached == null && p.tickCount >= scanAt) {
         scanAt = p.tickCount + 20;
         for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof TrackingHound h && p.getUUID().equals(h.getOwnerUUID())) {
               cached = h;
               break;
            }
         }
      }
      return cached != null && cached.distanceToSqr(p) <= r * r ? cached : null;
   }

   private static boolean leadWheel(Player p) {
      if (p != Minecraft.getInstance().player || mine(48.0) == null) {
         return false;
      }
      Minecraft.getInstance().tell(() -> open(false));
      return true;
   }

   static void open(boolean hold) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.screen != null && !(mc.screen instanceof HoundWheelScreen)) {
         return;
      }
      send(HoundNet.INFO, null, true);
      mc.setScreen(new HoundWheelScreen(hold));
   }

   static void send(int order, java.util.UUID target, boolean force) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level == null || mc.getConnection() == null) {
         return;
      }
      long now = mc.level.getGameTime();
      if (!force && now >= sentAt && now - sentAt < 3L) {
         return;
      }
      sentAt = now;
      PacketDistributor.sendToServer(new HoundNet.Order(order, target));
   }

   @SubscribeEvent
   public static void tick(ClientTickEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || mc.level == null) {
         held = -1;
         while (CALL.consumeClick()) {
         }
         return;
      }
      while (CALL.consumeClick()) {
         if (mc.screen == null && held < 0) {
            held = 0;
         }
      }
      if (held >= 0) {
         if (mc.screen != null) {
            held = -1;
         } else if (CALL.isDown()) {
            if (++held >= HOLD) {
               held = -1;
               open(true);
            }
         } else {
            // a tap: call him in
            held = -1;
            if (mc.player.isAlive() && !mc.player.isSpectator()) {
               send(HoundNet.COME, null, false);
            }
         }
      }
   }

   @SubscribeEvent
   public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
      info = null;
      cached = null;
      held = -1;
   }

   // ------------------------------------------------------------------------------------------------ HUD

   /** e.g. "Belle · Trailing · whitetail deer · 42 m" with an arrow toward him */
   @SubscribeEvent
   public static void hud(RenderGuiEvent.Post e) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer p = mc.player;
      if (p == null || mc.options.hideGui || mc.screen instanceof HoundWheelScreen) {
         return;
      }
      TrackingHound h = mine(96.0);
      if (h == null) {
         return;
      }
      GuiGraphics g = e.getGuiGraphics();
      Font font = mc.font;
      double dx = h.getX() - p.getX(), dz = h.getZ() - p.getZ();
      int dist = (int)Math.round(Math.sqrt(dx * dx + dz * dz));
      Component state = HoundRenderer.modeText(h);
      String label = h.quarryLabel();
      Component line1 = Component.empty().append(h.getName()).append(Component.literal("  " + arrow(p, dx, dz) + " " + dist + " m"));
      Component line2 = label.isEmpty() || !h.working() ? state : Component.empty().append(state).append(" · " + label);
      // right of the hotbar (past the attack indicator when it sits there)
      int x = g.guiWidth() / 2 + (mc.options.attackIndicator().get() == net.minecraft.client.AttackIndicatorStatus.HOTBAR ? 118 : 98);
      int w = Math.max(font.width(line1), font.width(line2));
      if (x + w + 6 > g.guiWidth()) {
         return; // narrow window: the hotbar side has no room - the overhead label still shows
      }
      int y = g.guiHeight() - 23;
      g.fill(x - 3, y - 2, x + w + 3, y + 20, 0x66000000);
      int accent = h.flag(TrackingHound.F_HOT) || h.mode() == TrackingHound.BAY || h.mode() == TrackingHound.FOUND ? 0xFFF0B050
         : h.working() ? 0xFFE8D8B0 : 0xFFC8C8C8;
      g.fill(x - 3, y - 2, x - 2, y + 20, accent);
      g.drawString(font, line1, x, y, 0xFFFFFFFF, true);
      g.drawString(font, line2, x, y + 10, accent, true);
   }

   /** an arrow pointing toward (dx, dz) relative to where the player looks */
   static String arrow(LocalPlayer p, double dx, double dz) {
      if (dx * dx + dz * dz < 4.0) {
         return "•";
      }
      float to = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
      float rel = Mth.wrapDegrees(to - p.getYRot());
      int i = Math.floorMod(Math.round(rel / 45.0F), 8);
      return new String[]{"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"}[i];
   }
}
