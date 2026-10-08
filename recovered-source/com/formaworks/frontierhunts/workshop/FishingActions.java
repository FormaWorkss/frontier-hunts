package com.formaworks.frontierhunts.workshop;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class FishingActions {
   private static final Map<UUID, FishingActions.Cast> CHARGING = new HashMap<>();
   private static final Map<UUID, FieldFloat> FLOATS = new HashMap<>();

   public static FieldFloat active(ServerPlayer var0) {
      FieldFloat var1 = FLOATS.get(var0.getUUID());
      if (var1 == null || !var1.isRemoved() && var1.level() == var0.level()) {
         return var1;
      } else {
         var1.discard();
         FLOATS.remove(var0.getUUID());
         return null;
      }
   }

   public static void begin(ServerPlayer var0, ItemStack var1) {
      if (valid(var0, var1)) {
         if (active(var0) == null) {
            CHARGING.put(var0.getUUID(), new FishingActions.Cast(var1, var0.level().dimension().location().toString(), var0.level().getGameTime()));
         } else {
            CHARGING.remove(var0.getUUID());
         }
      }
   }

   private static boolean valid(ServerPlayer var0, ItemStack var1) {
      return var0.isAlive()
         && !var0.isSpectator()
         && !var0.isUnderWater()
         && var0.getMainHandItem() == var1
         && var1.getItem() instanceof FieldRodItem
         && var0.containerMenu == var0.inventoryMenu;
   }

   public static void release(ServerPlayer var0, ItemStack var1) {
      FishingActions.Cast var2 = CHARGING.remove(var0.getUUID());
      if (var2 != null
         && var2.rod() == var1
         && valid(var0, var1)
         && active(var0) == null
         && var2.dimension().equals(var0.level().dimension().location().toString())) {
         long var3 = var0.level().getGameTime() - var2.started();
         if (var3 >= 5L && var3 <= 1200L) {
            double var5 = Math.clamp((double)var3 / 30.0, 0.15, 1.0);
            double var7 = 0.3 + 0.75 * Math.sqrt(var5);
            Vec3 var9 = var0.getEyePosition().add(var0.getLookAngle().scale(0.28));
            FieldFloat var10 = new FieldFloat((EntityType<? extends FieldFloat>)WorkshopContent.FLOAT.get(), var0.level());
            var10.assign(var0, var1);
            var10.setPos(var9);
            Vec3 var11 = var0.getLookAngle().add(0.0, 0.15, 0.0).normalize();
            var10.setDeltaMovement(var11.scale(var7).add(var0.getDeltaMovement().scale(0.35)));
            BlockHitResult var12 = var0.level().clip(new ClipContext(var0.getEyePosition(), var9, Block.COLLIDER, Fluid.NONE, var0));
            if (var12.getType() == Type.MISS) {
               if (var0.serverLevel().addFreshEntity(var10)) {
                  FLOATS.put(var0.getUUID(), var10);
                  var0.awardStat(Stats.ITEM_USED.get(var1.getItem()));
               }
            }
         }
      }
   }

   public static void hook(ServerPlayer var0) {
      FieldFloat var1 = active(var0);
      if (var1 != null && valid(var0, var0.getMainHandItem())) {
         var1.strike(var0);
      }
   }

   static void forget(FieldFloat var0) {
      FLOATS.values().removeIf(var1 -> var1 == var0);
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      CHARGING.remove(var0.getEntity().getUUID());
      FieldFloat var1 = FLOATS.remove(var0.getEntity().getUUID());
      if (var1 != null) {
         var1.discard();
      }
   }

   @SubscribeEvent
   public static void stop(ServerStoppedEvent var0) {
      CHARGING.clear();
      FLOATS.clear();
   }

   private FishingActions() {
   }

   private static record Cast(ItemStack rod, String dimension, long started) {
   }
}
