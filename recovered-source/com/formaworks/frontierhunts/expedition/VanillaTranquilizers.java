package com.formaworks.frontierhunts.expedition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Pre;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class VanillaTranquilizers {
   private static final String KEY = "frontier_vanilla_sedation";

   public static int required(Mob var0) {
      return Math.clamp((long)((int)Math.ceil((double)(var0.getMaxHealth() / 65.0F))), 1, 10);
   }

   public static boolean dart(Mob var0, ServerPlayer var1) {
      if (!var0.level().isClientSide && var0.isAlive() && BuiltInRegistries.ENTITY_TYPE.getKey(var0.getType()).getNamespace().equals("minecraft")) {
         CompoundTag var2 = var0.getPersistentData().getCompound("frontier_vanilla_sedation");
         long var3 = var0.level().getGameTime();
         if (var2.getLong("until") > var3) {
            var1.displayClientMessage(Component.literal("Sedated · wakes in " + (var2.getLong("until") - var3 + 19L) / 20L + "s"), true);
            return false;
         } else {
            int var5 = var3 - var2.getLong("last") > 1200L ? 1 : var2.getInt("doses") + 1;
            var2.putInt("doses", var5);
            var2.putLong("last", var3);
            if (var5 >= required(var0)) {
               var2.putBoolean("no_ai", var0.isNoAi());
               var2.putString("pose", var0.getPose().name());
               var2.putLong("until", var3 + 3600L);
               var0.setNoAi(true);
               var0.setTarget(null);
               var0.getNavigation().stop();
               var0.setDeltaMovement(var0.getDeltaMovement().multiply(0.15, 1.0, 0.15));
               var0.setPose(Pose.SLEEPING);
               var1.displayClientMessage(Component.literal("Sedated · wakes in 3 minutes"), true);
            } else {
               var1.displayClientMessage(Component.literal("Tranquilizer hit · " + var5 + " / " + required(var0) + " darts"), true);
            }

            var0.getPersistentData().put("frontier_vanilla_sedation", var2);
            return true;
         }
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void tick(Pre var0) {
      if (var0.getEntity() instanceof Mob var1 && !var1.level().isClientSide && var1.getPersistentData().contains("frontier_vanilla_sedation")) {
         CompoundTag var8 = var1.getPersistentData().getCompound("frontier_vanilla_sedation");
         long var3 = var8.getLong("until");
         if (var3 == 0L) {
            return;
         }

         if (var1.isAlive() && var1.level().getGameTime() < var3) {
            var1.setNoAi(true);
            var1.setTarget(null);
            var1.getNavigation().stop();
            var1.setPose(Pose.SLEEPING);
            var1.setDeltaMovement(var1.getDeltaMovement().multiply(0.5, 1.0, 0.5));
            if (var1 instanceof EnderDragon) {
               var0.setCanceled(true);
               Vec3 var9 = var1.getDeltaMovement();
               var1.move(MoverType.SELF, var9);
               var1.setDeltaMovement(0.0, var1.onGround() ? 0.0 : Math.max(-0.45, var9.y - 0.04), 0.0);
            }

            return;
         }

         var1.setNoAi(var8.getBoolean("no_ai"));

         Pose var5;
         try {
            var5 = Pose.valueOf(var8.getString("pose"));
         } catch (IllegalArgumentException var7) {
            var5 = Pose.STANDING;
         }

         var1.setPose(var5);
         var1.getPersistentData().remove("frontier_vanilla_sedation");
         return;
      }
   }

   private VanillaTranquilizers() {
   }
}
