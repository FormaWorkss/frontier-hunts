package com.formaworks.frontierhunts.wildlife2026.client;

import com.formaworks.frontierhunts.wildlife2026.Beta;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** [gear20] Marks beta animals' spawn eggs and licence tags as a work in progress. */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT)
public final class BetaTooltip {
   private BetaTooltip() {
   }

   @SubscribeEvent
   public static void tooltip(ItemTooltipEvent e) {
      if (e.getItemStack().isEmpty()) {
         return;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem());
      if (!"frontierhunts".equals(id.getNamespace()) || !Beta.item(id.getPath())) {
         return;
      }
      e.getToolTip().add(1, Component.translatable("tooltip.frontierhunts.beta.title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
      e.getToolTip().add(2, Component.translatable(id.getPath().endsWith("_spawn_egg") ? "tooltip.frontierhunts.beta.egg" : "tooltip.frontierhunts.beta.tag")
         .withStyle(ChatFormatting.GRAY));
   }
}
