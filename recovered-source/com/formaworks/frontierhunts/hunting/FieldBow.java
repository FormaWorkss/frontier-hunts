package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.expedition.BowHold;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;

public final class FieldBow extends FieldItem {
   public FieldBow(Properties var1) {
      super(var1);
   }

   public static float draw(int var0) {
      return Math.clamp((float)var0 / 26.0F, 0.0F, 1.0F);
   }

   /** @deprecated */
   public static ItemStack ammunition(Player var0) {
      return ArrowSupply.peekStack(var0);
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      if (!ArrowSupply.has(var2)) {
         if (!var1.isClientSide) {
            var2.displayClientMessage(Component.literal("No arrows: carry some, or fill a quiver"), true);
         }

         return InteractionResultHolder.fail(var4);
      } else {
         var2.startUsingItem(var3);
         return InteractionResultHolder.consume(var4);
      }
   }

   public void releaseUsing(ItemStack var1, Level var2, LivingEntity var3, int var4) {
      if (var2 instanceof ServerLevel var5 && var3 instanceof Player var6 && var6.isAlive() && !var6.isSpectator()) {
         float var7 = draw(this.getUseDuration(var1, var3) - var4);
         if (var7 >= 0.95F) {
            var7 = 1.0F; // [archery2] a tick of network jitter at release is still a full draw (the client sight shows only at 100 %)
         }
         if (!(var7 < 0.18F) && ArrowSupply.has(var6) && !var6.getCooldowns().isOnCooldown(this)) {
            ArrowSupply.Shot var8 = ArrowSupply.peek(var6);
            FieldArrow var9 = new FieldArrow((EntityType<? extends FieldArrow>)HuntEntities.ARROW.get(), var5);
            var9.setOwner(var6);
            var9.setPos(var6.getX(), var6.getEyeY() - 0.1, var6.getZ());
            var9.setRecoverable(!var6.hasInfiniteMaterials());
            var9.setShot(var8);
            var9.setLaunch(var9.position());
            float var10 = BowHold.strain(var6, this.getUseDuration(var1, var3) - var4);
            var9.shootFromRotation(
               var6,
               // [1.2.5] raised by the holdover that puts the arc on whatever is under the crosshair
               com.formaworks.frontierhunts.archery.PointOfAim.launchXRot(var6, com.formaworks.frontierhunts.archery.BowBallistics.FIELD_RECURVE,
                  2.75 * Math.sqrt((double)var7)),
               var6.getYRot(),
               0.0F,
               2.75F * (float)Math.sqrt((double)var7),
               // [bows] a full, steady draw flies exactly where the sight says; short draw / fatigue tremor / moving add spread
               com.formaworks.frontierhunts.archery.BowBallistics.spread(var7, var10, var6.getKnownMovement().horizontalDistance(), !var6.onGround())
            );
            if (!var5.addFreshEntity(var9)) {
               return;
            }

            ArrowSupply.take(var6);
            if (!var6.hasInfiniteMaterials()) {
               var1.hurtAndBreak(1, var6, LivingEntity.getSlotForHand(var6.getUsedItemHand()));
            }

            var6.getCooldowns().addCooldown(this, 10);
            var5.playSound(
               null, var6.blockPosition(), (SoundEvent)HuntSounds.BOW_RELEASE.get(), SoundSource.PLAYERS, 0.75F, 0.96F + var5.random.nextFloat() * 0.08F
            );
            var6.awardStat(Stats.ITEM_USED.get(this));
            return;
         }

         return;
      }
   }

   public int getUseDuration(ItemStack var1, LivingEntity var2) {
      return 72000;
   }

   public UseAnim getUseAnimation(ItemStack var1) {
      return UseAnim.BOW;
   }
}
