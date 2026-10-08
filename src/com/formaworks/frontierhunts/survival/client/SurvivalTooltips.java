package com.formaworks.frontierhunts.survival.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.FoodValues;
import com.formaworks.frontierhunts.survival.Freshness;
import com.formaworks.frontierhunts.survival.NutritionTable;
import com.formaworks.frontierhunts.survival.Perishable;
import com.formaworks.frontierhunts.survival.SurvivalMath;
import com.formaworks.frontierhunts.survival.SurvivalSync;
import com.formaworks.frontierhunts.survival.item.GarmentItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * [survival] Tooltips: nutrition of every food (what it gives you at the server's difficulty), where it comes from, and
 * for perishables how fresh it is ("Fresh, keeps 3 days" ... "Spoiling" ... "Spoiled") plus smoked / salt-cured / kept
 * cold. Worn pieces of any mod get their warmth line (the hide garments add their own).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, value = Dist.CLIENT)
public final class SurvivalTooltips {
   private static final TextColor PROTEIN = TextColor.fromRgb(0xE07060);
   private static final TextColor FAT = TextColor.fromRgb(0xF0DDB0);
   private static final TextColor ENERGY = TextColor.fromRgb(0xF0C050);

   private SurvivalTooltips() {
   }

   @SubscribeEvent
   public static void tooltip(ItemTooltipEvent e) {
      ItemStack s = e.getItemStack();
      if (s.isEmpty() || !SurvivalSync.mode(true).on()) {
         return;
      }
      List<Component> add = new ArrayList<>();
      FoodValues v = NutritionTable.get(s, true);
      if (v != null) {
         food(s, v, add);
      }
      if (!(s.getItem() instanceof GarmentItem)) {
         Equipable eq = Equipable.get(s);
         if (eq != null && eq.getEquipmentSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            Clothing.describe(s, eq.getEquipmentSlot(), add);
         }
      }
      if (!add.isEmpty()) {
         List<Component> tip = e.getToolTip();
         tip.addAll(Math.min(1, tip.size()), add);
      }
   }

   private static void food(ItemStack s, FoodValues v, List<Component> out) {
      SurvivalMath.Mode mode = SurvivalSync.mode(true);
      float pf = 1F, ff = 1F, ef = 1F;
      switch (v.source()) {
         case FARM -> {
            pf = mode.farm;
            ff = mode.farm;
         }
         case CROP -> {
            pf = ff = ef = mode.crop;
         }
         case FISH -> {
            pf = ff = ef = mode.fish;
         }
         default -> {
         }
      }
      float raw = v.raw() ? 0.85F : 1F;
      if (v.total() > 0F) {
         MutableComponent line = Component.empty();
         line.append(Component.translatable("survival.frontierhunts.tooltip.protein", Math.round(v.protein() * pf * raw)).withStyle(st -> st.withColor(PROTEIN)));
         line.append(Component.literal("  "));
         line.append(Component.translatable("survival.frontierhunts.tooltip.fat", Math.round(v.fat() * ff * raw)).withStyle(st -> st.withColor(FAT)));
         line.append(Component.literal("  "));
         line.append(Component.translatable("survival.frontierhunts.tooltip.energy", Math.round(v.energy() * ef * raw)).withStyle(st -> st.withColor(ENERGY)));
         out.add(line);
      }
      if (!v.spoiled()) {
         ChatFormatting c = switch (v.source()) {
            case GAME -> ChatFormatting.DARK_GREEN;
            case FISH -> ChatFormatting.DARK_AQUA;
            case FARM -> ChatFormatting.GRAY;
            default -> ChatFormatting.DARK_GRAY;
         };
         out.add(Component.translatable("survival.frontierhunts.source." + v.source().key()).withStyle(c));
      }
      if (v.spoiled()) {
         out.add(Component.translatable("survival.frontierhunts.fresh.sick").withStyle(ChatFormatting.DARK_RED));
         return;
      }
      if (!v.perishable() || !SurvivalSync.spoilage(true) || Minecraft.getInstance().level == null) {
         return;
      }
      long now = Minecraft.getInstance().level.getGameTime();
      Freshness f = Perishable.get(s);
      if (f == null) {
         out.add(Component.translatable("survival.frontierhunts.fresh.fresh", SurvivalText.duration((long) (v.shelfDays() * SurvivalMath.DAY)))
            .withStyle(ChatFormatting.GREEN));
         return;
      }
      float shelf = Perishable.shelfTicks(v, f);
      float spoil = SurvivalMath.spoil(f.made(), now, f.store(), shelf);
      long left = SurvivalMath.ticksLeft(f.made(), now, f.store(), shelf);
      MutableComponent line;
      if (spoil >= 1F) {
         line = Component.translatable("survival.frontierhunts.fresh.spoiled").withStyle(ChatFormatting.DARK_RED);
      } else if (spoil >= 0.85F) {
         line = Component.translatable("survival.frontierhunts.fresh.spoiling", SurvivalText.duration(left)).withStyle(ChatFormatting.RED);
      } else if (spoil >= 0.6F) {
         line = Component.translatable("survival.frontierhunts.fresh.old", SurvivalText.duration(left)).withStyle(ChatFormatting.GOLD);
      } else if (spoil >= 0.25F) {
         line = Component.translatable("survival.frontierhunts.fresh.good", SurvivalText.duration(left)).withStyle(ChatFormatting.YELLOW);
      } else {
         line = Component.translatable("survival.frontierhunts.fresh.fresh", SurvivalText.duration(left)).withStyle(ChatFormatting.GREEN);
      }
      if (f.cure() == SurvivalMath.SMOKED) {
         line.append(Component.translatable("survival.frontierhunts.cure.smoked").withStyle(ChatFormatting.GRAY));
      } else if (f.cure() == SurvivalMath.SALTED) {
         line.append(Component.translatable("survival.frontierhunts.cure.salted").withStyle(ChatFormatting.GRAY));
      }
      if (f.store() != SurvivalMath.AMBIENT) {
         line.append(Component.translatable("survival.frontierhunts.store." + f.store()).withStyle(ChatFormatting.DARK_AQUA));
      }
      out.add(line);
   }
}
