package com.formaworks.frontierhunts.landscape.ride.rig;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * [atvfuel] 10 L steel jerry can. Gasoline is the {@link RigContent#FUEL} component (litres). Filled by drawing a lava
 * source into it (the heat cracks it to fuel - the lava source is used up, like a bucket) or by crafting it with a lava
 * bucket. Poured into an ATV by using it on the ATV (hold to keep pouring); stowed in the ATV can carrier as reserve.
 */
public class JerryCanItem extends Item {
   public static final float CAPACITY = 10.0F;

   public JerryCanItem(Item.Properties props) {
      super(props);
   }

   public static ItemStack filled(float litres) {
      ItemStack s = new ItemStack(RigContent.JERRY_CAN.get());
      s.set(RigContent.FUEL.get(), Mth.clamp(litres, 0.0F, CAPACITY));
      return s;
   }

   public static float litres(ItemStack stack) {
      return Math.min(CAPACITY, RigContent.fuelOf(stack));
   }

   public static void setLitres(ItemStack stack, float litres) {
      float l = Mth.clamp(Math.round(litres * 100.0F) / 100.0F, 0.0F, CAPACITY);
      if (l <= 0.0F) {
         stack.remove(RigContent.FUEL.get());
      } else {
         stack.set(RigContent.FUEL.get(), l);
      }
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
      if (hit.getType() != HitResult.Type.BLOCK) {
         return InteractionResultHolder.pass(stack);
      }
      BlockPos pos = hit.getBlockPos();
      FluidState fluid = level.getFluidState(pos);
      if (!fluid.is(FluidTags.LAVA) || !fluid.isSource()) {
         return InteractionResultHolder.pass(stack);
      }
      if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
         return InteractionResultHolder.fail(stack);
      }
      if (litres(stack) >= CAPACITY - 0.01F) {
         if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.frontierhunts.jerry_can_full").withStyle(ChatFormatting.GRAY), true);
         }
         return InteractionResultHolder.fail(stack);
      }
      BlockState state = level.getBlockState(pos);
      if (!(state.getBlock() instanceof BucketPickup pickup)) {
         return InteractionResultHolder.pass(stack);
      }
      if (!level.isClientSide) {
         if (pickup.pickupBlock(player, level, pos, state).isEmpty()) {
            return InteractionResultHolder.fail(stack);
         }
         setLitres(stack, CAPACITY);
         level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.8F + level.random.nextFloat() * 0.3F);
         level.playSound(null, player.getX(), player.getY(), player.getZ(), RigContent.SND_CAN_FILL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
         if (level instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.25, 0.1, 0.25, 0.02);
            sl.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.0);
         }
         level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
         player.awardStat(Stats.ITEM_USED.get(this));
         player.displayClientMessage(Component.translatable("message.frontierhunts.jerry_can_filled", String.format("%.1f", CAPACITY))
            .withStyle(ChatFormatting.GOLD), true);
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
   }

   @Override
   public Component getName(ItemStack stack) {
      return Component.translatable(litres(stack) > 0.0F ? this.getDescriptionId() : this.getDescriptionId() + ".empty");
   }

   @Override
   public boolean isBarVisible(ItemStack stack) {
      return litres(stack) > 0.0F;
   }

   @Override
   public int getBarWidth(ItemStack stack) {
      return Math.max(1, Math.round(13.0F * litres(stack) / CAPACITY));
   }

   @Override
   public int getBarColor(ItemStack stack) {
      float f = litres(stack) / CAPACITY;
      // amber gasoline, reddening as the can runs low
      int r = 0xF2;
      int g = (int)Mth.lerp(f, 0x55, 0xB8);
      int b = (int)Mth.lerp(f, 0x20, 0x30);
      return r << 16 | g << 8 | b;
   }

   @Override
   public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      float l = litres(stack);
      if (l > 0.0F) {
         lines.add(Component.translatable("item.frontierhunts.jerry_can.level", String.format("%.1f", l), String.format("%.0f", CAPACITY))
            .withStyle(ChatFormatting.GOLD));
      } else {
         lines.add(Component.translatable("item.frontierhunts.jerry_can.tip_empty").withStyle(ChatFormatting.GRAY));
      }
      lines.add(Component.translatable("item.frontierhunts.jerry_can.tip").withStyle(ChatFormatting.DARK_GRAY));
   }
}
