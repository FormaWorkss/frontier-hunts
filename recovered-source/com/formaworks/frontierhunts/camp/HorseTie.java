package com.formaworks.frontierhunts.camp;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class HorseTie {
   private static final String TAG = "frontierhunts_pole_tie";
   private static final ResourceLocation ROOTED = FrontierHunts.id("tied_at_pole");

   static void tie(AbstractHorse var0, Player var1) {
      var0.getPersistentData().putUUID("frontierhunts_pole_tie", var1.getUUID());
      AttributeInstance var2 = var0.getAttribute(Attributes.MOVEMENT_SPEED);
      if (var2 != null && !var2.hasModifier(ROOTED)) {
         var2.addPermanentModifier(new AttributeModifier(ROOTED, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
      }

      AttributeInstance var3 = var0.getAttribute(Attributes.JUMP_STRENGTH);
      if (var3 != null && !var3.hasModifier(ROOTED)) {
         var3.addPermanentModifier(new AttributeModifier(ROOTED, -1.0, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   static void untie(AbstractHorse var0) {
      var0.getPersistentData().remove("frontierhunts_pole_tie");
      AttributeInstance var1 = var0.getAttribute(Attributes.MOVEMENT_SPEED);
      if (var1 != null) {
         var1.removeModifier(ROOTED);
      }

      AttributeInstance var2 = var0.getAttribute(Attributes.JUMP_STRENGTH);
      if (var2 != null) {
         var2.removeModifier(ROOTED);
      }
   }

   public static boolean isTied(AbstractHorse var0) {
      return var0.getPersistentData().hasUUID("frontierhunts_pole_tie");
   }

   static boolean owner(AbstractHorse var0, Player var1) {
      return !isTied(var0)
         ? false
         : var0.getPersistentData().getUUID("frontierhunts_pole_tie").equals(var1.getUUID())
            || var1.hasPermissions(2)
            || var0.getOwnerUUID() != null && var0.getOwnerUUID().equals(var1.getUUID());
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      if (!(var0.getEntity() instanceof AbstractHorse var1) || var1.level().isClientSide || var1.tickCount % 20 != 0) {
         return;
      }

      if (isTied(var1)) {
         if (var1.isLeashed() && var1.getLeashHolder() instanceof LeashFenceKnotEntity) {
            if (var1.isVehicle() && var1.getControllingPassenger() instanceof Player) {
               var1.getNavigation().stop();
            }
         } else {
            untie(var1);
         }
      }
   }

   private HorseTie() {
   }
}
