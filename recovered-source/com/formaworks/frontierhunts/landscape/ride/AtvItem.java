package com.formaworks.frontierhunts.landscape.ride;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class AtvItem extends Item {
   public AtvItem(Properties var1) {
      super(var1);
   }

   public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
      ItemStack var4 = var2.getItemInHand(var3);
      BlockHitResult var5 = getPlayerPOVHitResult(var1, var2, Fluid.NONE);
      if (var5.getType() != Type.BLOCK) {
         return InteractionResultHolder.pass(var4);
      } else {
         Vec3 var6 = var5.getLocation();
         Atv var7 = new Atv((EntityType<? extends Atv>)RideContent.ATV.get(), var1);
         var7.setPos(var6.x, var6.y, var6.z);
         var7.setYRot(var2.getYRot());
         var7.xo = var6.x;
         var7.yo = var6.y;
         var7.zo = var6.z;
         if (!var1.noCollision(var7, var7.getBoundingBox())) {
            return InteractionResultHolder.fail(var4);
         } else {
            if (!var1.isClientSide) {
               com.formaworks.frontierhunts.landscape.ride.rig.AtvFuel.fromItem(var7, var4); // [atvfuel] tank level travels with the item
               var1.addFreshEntity(var7);
               var1.gameEvent(var2, GameEvent.ENTITY_PLACE, var5.getLocation());
               var4.consume(1, var2);
            }

            return InteractionResultHolder.sidedSuccess(var4, var1.isClientSide);
         }
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.translatable("item.frontierhunts.atv.tip1").withStyle(ChatFormatting.GRAY));
      var3.add(Component.translatable("item.frontierhunts.atv.tip2").withStyle(ChatFormatting.DARK_GRAY));
      com.formaworks.frontierhunts.landscape.ride.rig.AtvFuelTooltip.append(var1, var3); // [atvfuel]
   }
}
