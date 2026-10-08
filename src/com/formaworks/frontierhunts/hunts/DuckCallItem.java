package com.formaworks.frontierhunts.hunts;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * [hunts] The Duck Call: use = a hail call that carries ~96 blocks; mallards that hear it swing to the decoy spread
 * nearby (or to open water near the caller) and land. A short cooldown like the deer calls; nearby players hear it.
 */
public class DuckCallItem extends Item {
   public static final int COOLDOWN = 60;
   /** [1.1.6] one quack per click: the call itself only rests a moment; the lure still counts once per COOLDOWN */
   public static final int CLICK = 5;
   private static final java.util.Map<java.util.UUID, Long> LAST_LURE = new java.util.HashMap<>();

   public DuckCallItem(Properties p) {
      super(p);
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack st = player.getItemInHand(hand);
      if (player.getCooldowns().isOnCooldown(this)) {
         return InteractionResultHolder.fail(st);
      }
      if (!level.isClientSide && player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
         float pitch = 0.94F + sl.random.nextFloat() * 0.12F;
         sl.playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(), HuntContent.DUCK_CALL_SOUND, SoundSource.PLAYERS, 1.0F, pitch);
         long now = sl.getGameTime();
         Long last = LAST_LURE.get(sp.getUUID());
         if (last == null || now - last >= COOLDOWN || now < last) {
            LAST_LURE.put(sp.getUUID(), now);
            if (HuntsConfig.lures()) {
               Lures.blow(Lures.Kind.DUCK, sp.getUUID(), sl.dimension().location().toString(), sp.position(), now, 96.0);
            }
            int decoys = HuntContent.liveDecoys(sl, sp.getX(), sp.getY(), sp.getZ(), 32.0, 32).size();
            sp.displayClientMessage(Component.translatable(decoys > 0 ? "hunts.frontierhunts.msg.duck_call" : "hunts.frontierhunts.msg.duck_call_nodecoys", decoys), true);
         }
      }
      player.getCooldowns().addCooldown(this, CLICK);
      return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
      tip.add(Component.translatable("item.frontierhunts.duck_call.tip1").withStyle(ChatFormatting.GRAY));
      tip.add(Component.translatable("item.frontierhunts.duck_call.tip2").withStyle(ChatFormatting.GRAY));
   }
}
