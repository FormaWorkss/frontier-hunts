package com.formaworks.frontierhunts.survival.item;

import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** [survival] Plain survival item (foods, hides, salt, linings) with a one-line field note in its tooltip. */
public class SurvivalItem extends Item {
   private static final Set<String> NOTES = Set.of(
      "salt", "heavy_hide", "fur_pelt", "bear_pelt", "tanned_heavy_hide", "tanned_fur", "bear_fur", "fur_lining", "fur_mittens", "jerky", "pemmican",
      "tallow", "game_fat", "spoiled_meat", "organ_meat"
   );

   public SurvivalItem(Properties p) {
      super(p);
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      String id = BuiltInRegistries.ITEM.getKey(this).getPath();
      if (NOTES.contains(id)) {
         lines.add(Component.translatable("item.frontierhunts." + id + ".note").withStyle(ChatFormatting.GRAY));
      }
   }
}
