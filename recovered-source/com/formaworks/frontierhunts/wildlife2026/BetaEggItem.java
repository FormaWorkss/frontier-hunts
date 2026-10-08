package com.formaworks.frontierhunts.wildlife2026;

import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

/**
 * [1.1.9] The spawn egg of an animal that isn't finished. It's no longer in the creative tab; one already in someone's
 * inventory only says the animal isn't ready yet. Server operators (permission level 2, as for /summon) can still use
 * it to try the animal.
 */
public class BetaEggItem extends DeferredSpawnEggItem {
   public BetaEggItem(Supplier<? extends EntityType<? extends Mob>> type, int back, int spots, Properties props) {
      super(type, back, spots, props);
   }

   static boolean allowed(Player p) {
      return p != null && p.hasPermissions(2);
   }

   private static void refuse(Player p) {
      if (p != null && !p.level().isClientSide) {
         p.displayClientMessage(Component.translatable("item.frontierhunts.beta_egg.not_ready").withStyle(ChatFormatting.GOLD), true);
      }
   }

   @Override
   public InteractionResult useOn(UseOnContext ctx) {
      if (!allowed(ctx.getPlayer())) {
         refuse(ctx.getPlayer());
         return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
      }
      return super.useOn(ctx);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
      if (!allowed(p)) {
         refuse(p);
         return InteractionResultHolder.fail(p.getItemInHand(hand));
      }
      return super.use(level, p, hand);
   }
}
