package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.hunting.Whitetail;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

public final class CarcassCleanup {
   public static final int AUTO_TICKS = 1200;

   public static boolean harvested(Mob var0) {
      if (var0 instanceof Whitetail var1 && var1.downed() && var1.harvested()) {
         return true;
      }

      return false;
   }

   public static boolean automatic(Mob var0, long var1) {
      if (harvested(var0) && !var0.level().isClientSide && var0.level().getGameTime() - var1 >= 1200L) {
         clear(var0);
         return true;
      } else {
         return false;
      }
   }

   public static void interact(Mob var0, Player var1, InteractionHand var2) {
      if (harvested(var0)) {
         if (!var0.level().isClientSide && var2 == InteractionHand.MAIN_HAND) {
            boolean var3 = var1.getMainHandItem().isEmpty() || var1.getMainHandItem().is((Item)HuntContent.SKINNING_TOOL.get());
            if (var1.isShiftKeyDown()
               && var3
               && var1.isAlive()
               && !var1.isSpectator()
               && var1.level() == var0.level()
               && var1.distanceToSqr(var0) <= 16.0
               && var1.hasLineOfSight(var0)) {
               clear(var0);
               var1.displayClientMessage(Component.literal("Harvested remains cleared"), true);
            } else {
               var1.displayClientMessage(
                  Component.literal(
                     "Harvest recovered · crouch and use with the Contour Knife or an empty hand to clear remains · automatic cleanup after 1 minute"
                  ),
                  true
               );
            }
         }
      }
   }

   private static void clear(Mob var0) {
      var0.ejectPassengers();
      var0.stopRiding();
      var0.discard();
   }

   private CarcassCleanup() {
   }
}
