package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.hunting.ArrowSupply;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.QuiverItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class ArcheryHud {
   private static long tick = Long.MIN_VALUE;
   private static ItemStack stack = ItemStack.EMPTY;
   private static ItemStack quiver = ItemStack.EMPTY;
   private static ItemStack previous = ItemStack.EMPTY;
   private static int left;
   private static long switchedAt = Long.MIN_VALUE;
   private static final List<ItemStack> CHOICES = new ArrayList<>();

   private static void refresh(Minecraft var0, Player var1, long var2) {
      tick = var2;
      stack = ArrowSupply.peekStack(var1).copy();
      left = stack.isEmpty() ? 0 : ArrowSupply.available(var1);
      quiver = ArrowSupply.feedingQuiver(var1).copy();
      if (!ItemStack.isSameItemSameComponents(stack, previous)) {
         if (!previous.isEmpty() || !stack.isEmpty()) {
            switchedAt = var2;
         }

         previous = stack;
      }

      CHOICES.clear();
      if (switchedAt != Long.MIN_VALUE && var2 - switchedAt < 60L) {
         if (!var1.getOffhandItem().isEmpty() && ArrowTip.arrow(var1.getOffhandItem())) {
            add(var1.getOffhandItem());
         }

         if (!quiver.isEmpty()) {
            for (ItemStack var5 : QuiverItem.read(quiver)) {
               add(var5);
            }
         }

         for (ItemStack var7 : var1.getInventory().items) {
            add(var7);
         }
      }
   }

   private static void add(ItemStack var0) {
      if (!var0.isEmpty() && ArrowTip.arrow(var0) && CHOICES.size() < 6) {
         for (ItemStack var2 : CHOICES) {
            if (ItemStack.isSameItemSameComponents(var2, var0)) {
               var2.grow(var0.getCount());
               return;
            }
         }

         CHOICES.add(var0.copy());
      }
   }

   static void draw(GuiGraphics var0, Minecraft var1, int var2, int var3) {
      LocalPlayer var4 = var1.player;
      if (var4 != null && ArrowSupply.arrowBow(var4.getMainHandItem())) {
         long var5 = var1.level.getGameTime();
         if (var5 != tick) {
            refresh(var1, var4, var5);
         }

         steadiness(var0, var1, var4, var2, var3);
         String var7 = stack.isEmpty() ? (var4.hasInfiniteMaterials() ? "Fixed-Blade Broadhead" : "No arrows") : ArrowTip.of(stack).title;
         String var8 = ArrowTip.arrow(var4.getOffhandItem())
            ? "offhand"
            : (!quiver.isEmpty() && QuiverItem.drawSlot(quiver) >= 0 ? "quiver " + QuiverItem.count(quiver) : "inventory");
         String var9 = RifleClient.RELOAD.getTranslatedKeyMessage().getString() + "/" + RifleClient.CYCLE.getTranslatedKeyMessage().getString();
         String var10 = (stack.isEmpty() ? "" : left + " · ") + var8 + " · " + var9 + " switch arrow";
         int var11 = Math.max(var1.font.width(var7), var1.font.width(var10)) + 30;
         int var12 = var2 - 7 - var11;
         int var13 = var3 - 90;
         var0.fill(var12, var13, var2 - 7, var13 + 26, -803921636);
         int var14 = stack.isEmpty() ? -7714246 : (ArrowTip.of(stack).tracer() ? 0xFF000000 | ArrowTip.of(stack).glow : -4676502);
         var0.fill(var12, var13, var12 + 2, var13 + 26, var14);
         if (!stack.isEmpty()) {
            var0.renderItem(stack, var12 + 5, var13 + 5);
         }

         var0.drawString(var1.font, var7, var12 + 25, var13 + 4, stack.isEmpty() ? -3110280 : -1383473, false);
         var0.drawString(var1.font, var10, var12 + 25, var13 + 15, -5655652, false);
         if (CHOICES.size() > 1) {
            int var15 = CHOICES.size();
            int var16 = 0;

            for (ItemStack var18 : CHOICES) {
               var16 = Math.max(var16, var1.font.width(ArrowTip.of(var18).title + "  " + var18.getCount()) + 26);
            }

            int var24 = var2 - 7 - var16;
            int var25 = var13 - 4 - var15 * 18;
            var0.fill(var24, var25, var2 - 7, var13 - 4, -1072686570);

            for (int var19 = 0; var19 < var15; var19++) {
               ItemStack var20 = CHOICES.get(var19);
               boolean var21 = ItemStack.isSameItemSameComponents(var20, stack);
               int var22 = var25 + var19 * 18;
               if (var21) {
                  var0.fill(var24, var22, var2 - 7, var22 + 18, 1355331704);
               }

               var0.renderItem(var20, var24 + 3, var22 + 1);
               var0.drawString(var1.font, ArrowTip.of(var20).title, var24 + 22, var22 + 5, var21 ? -857651 : -5655652, false);
               String var23 = "×" + var20.getCount();
               var0.drawString(var1.font, var23, var2 - 11 - var1.font.width(var23), var22 + 5, -7363709, false);
            }
         }
      }
   }

   private static void steadiness(GuiGraphics var0, Minecraft var1, Player var2, int var3, int var4) {
      ItemStack var5 = var2.getMainHandItem();
      if (FieldBowAim.drawn(var5)) {
         float var6 = var1.getTimer().getGameTimeDeltaPartialTick(false);
         if (!(FieldBowAim.drawOf(var2, var5, var6) < 0.55F)) {
            float var7 = Mth.clamp(1.0F - FieldBowAim.strain(var2, var5) / 0.8F, 0.0F, 1.0F);
            byte var8 = 54;
            int var9 = var3 / 2 - var8 / 2;
            int var10 = var4 / 2 + BowSightHud.steadinessOffset(var1); // [bows] below the bow sight
            var0.fill(var9 - 1, var10 - 1, var9 + var8 + 1, var10 + 4, -1877993196);
            int var11 = var7 > 0.6F ? -7359622 : (var7 > 0.3F ? -2705556 : -3904942);
            var0.fill(var9, var10, var9 + Math.round((float)var8 * var7), var10 + 3, var11);
         }
      }
   }

   private ArcheryHud() {
   }
}
