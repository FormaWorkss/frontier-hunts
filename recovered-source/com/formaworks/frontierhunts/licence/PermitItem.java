package com.formaworks.frontierhunts.licence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * [licence] A Hunting Licence, a big-game tag or a bird stamp. Valid when issued ({@link PermitData}) to the carrier for
 * the current licence season; the tooltip says what it covers and whether it is still valid.
 */
public class PermitItem extends Item {
   public enum Kind {
      LICENCE, TAG, STAMP
   }

   public final Kind kind;
   public final Regulations.TagKind tag;
   public final Regulations.Stamp stamp;

   public PermitItem(Kind kind, Regulations.TagKind tag, Regulations.Stamp stamp, Item.Properties p) {
      super(p);
      this.kind = kind;
      this.tag = tag;
      this.stamp = stamp;
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      PermitData d = stack.get(LicenceContent.PERMIT.get());
      Level level = ctx.level();
      if (d == null) {
         tip.add(Component.translatable("item.frontierhunts.permit.blank").withStyle(ChatFormatting.RED));
         tip.add(Component.translatable("item.frontierhunts.permit.where").withStyle(ChatFormatting.GRAY));
      } else {
         tip.add(Component.translatable("item.frontierhunts.permit.issued", d.name(), Regulations.periodTitle(d.period())).withStyle(ChatFormatting.GRAY));
         if (level != null) {
            int now = LicenceTime.period(level);
            if (d.period() == now) {
               tip.add(Component.translatable("item.frontierhunts.permit.valid", LicenceTime.daysLeft(level)).withStyle(ChatFormatting.DARK_GREEN));
            } else if (d.period() < now) {
               tip.add(Component.translatable("item.frontierhunts.permit.expired").withStyle(ChatFormatting.RED));
            } else {
               tip.add(Component.translatable("item.frontierhunts.permit.future").withStyle(ChatFormatting.GOLD));
            }
         }
      }
      switch (this.kind) {
         case LICENCE -> tip.add(Component.translatable("item.frontierhunts.hunting_licence.tip").withStyle(ChatFormatting.DARK_GRAY));
         case TAG -> {
            tip.add(Component.translatable("item.frontierhunts.permit.covers", titles(Regulations.covered(this.tag))).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("item.frontierhunts.permit.open", Regulations.months(Regulations.months(this.tag))).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("item.frontierhunts.permit.tag_tip", this.tag.bag).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.translatable("item.frontierhunts.permit.tag_use").withStyle(ChatFormatting.GOLD));
         }
         case STAMP -> {
            tip.add(Component.translatable("item.frontierhunts.permit.covers", titles(Regulations.covered(this.stamp))).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("item.frontierhunts.permit.open", Regulations.months(Regulations.months(this.stamp))).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("item.frontierhunts.permit.stamp_tip", this.stamp.daily).withStyle(ChatFormatting.DARK_GRAY));
         }
      }
      if (LicenceConfig.mode() == LicenceConfig.Mode.OFF) {
         tip.add(Component.translatable("item.frontierhunts.permit.off").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
      }
   }

   private static String titles(List<Regulations.Rule> rules) {
      List<String> t = new ArrayList<>();
      for (Regulations.Rule r : rules) {
         t.add(r.title());
      }
      return String.join(", ", t);
   }
}
