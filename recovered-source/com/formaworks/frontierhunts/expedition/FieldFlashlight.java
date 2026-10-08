package com.formaworks.frontierhunts.expedition;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent.Unload;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class FieldFlashlight {
   private static final int LEASE_TICKS = 12;
   private static final int MAX_NODES = 2048;
   private static final Map<ServerLevel, Map<BlockPos, Long>> LEASES = new WeakHashMap<>();
   private static final double[] DISTANCES = new double[]{1.3, 3.0, 5.5, 8.5, 12.0, 16.0, 20.0};

   public static boolean enabled(ItemStack var0) {
      if (var0.getItem() instanceof ExpeditionGear var1
         && var1.id.equals("field_flashlight")
         && ExpeditionWeapon.data(var0).getBoolean("light_on")
         && FieldElectronics.available(var0)) {
         return true;
      }

      return false;
   }

   public static void toggle(ServerPlayer var0, ItemStack var1) {
      if (var0.isShiftKeyDown()) {
         FieldElectronics.cycle(var0, var1);
      } else if (!FieldElectronics.available(var1)) {
         ExpeditionService.message(var0, "Battery empty · hold the light offhand and use a Field Battery Pack.");
      } else {
         CompoundTag var2 = ExpeditionWeapon.data(var1);
         var2.putBoolean("light_on", !enabled(var1));
         ExpeditionWeapon.save(var1, var2);
         var0.getCooldowns().addCooldown(var1.getItem(), 5);
         ExpeditionService.message(var0, "Field light: " + (enabled(var1) ? "ON" : "OFF"));
      }
   }

   public static Set<BlockPos> project(ServerLevel var0, Vec3 var1, Vec3 var2) {
      if (Double.isFinite(var1.lengthSqr()) && Double.isFinite(var2.lengthSqr()) && !(var2.lengthSqr() < 0.01)) {
         var2 = var2.normalize();
         double var3 = 20.0;

         for (double var5 = 0.0; var5 <= 20.0; var5 += 0.5) {
            BlockPos var7 = BlockPos.containing(var1.add(var2.scale(var5)));
            if (var0.isOutsideBuildHeight(var7) || !var0.getWorldBorder().isWithinBounds(var7) || !var0.hasChunkAt(var7)) {
               var3 = Math.max(0.0, var5 - 0.5);
               break;
            }
         }

         if (var3 < 0.3) {
            return Set.of();
         } else {
            BlockHitResult var21 = var0.clip(new ClipContext(var1, var1.add(var2.scale(var3)), Block.COLLIDER, Fluid.ANY, CollisionContext.empty()));
            if (var21.getType() != Type.MISS) {
               var3 = Math.max(0.0, var1.distanceTo(var21.getLocation()) - 0.12);
            }

            Map var6 = LEASES.computeIfAbsent(var0, var0x -> new HashMap<>());
            long var22 = var0.getGameTime();
            var6.entrySet().removeIf(var2x -> (Long)var2x.getValue() <= var22);
            HashSet var9 = new HashSet();

            for (double var13 : DISTANCES) {
               double var15 = Math.min(var13, var3);
               if (var15 < 0.3) {
                  break;
               }

               BlockPos var17 = BlockPos.containing(var1.add(var2.scale(var15)));
               if (!var9.add(var17)) {
                  if (var13 >= var3) {
                     break;
                  }
               } else {
                  BlockState var18 = var0.getBlockState(var17);
                  if ((var18.isAir() || var18.is((net.minecraft.world.level.block.Block)ExpeditionContent.BEAM.get()))
                     && var18.getFluidState().isEmpty()
                     && (var6.containsKey(var17) || var6.size() < 2048)) {
                     var6.put(var17, var22 + 12L);
                     BlockState var19 = (BlockState)((FieldSpotlight.Beam)ExpeditionContent.BEAM.get())
                        .defaultBlockState()
                        .setValue(FieldSpotlight.Beam.POWER, 15);
                     if (!var18.equals(var19)) {
                        var0.setBlock(var17, var19, 3);
                     }

                     var0.scheduleTick(var17, (net.minecraft.world.level.block.Block)ExpeditionContent.BEAM.get(), 12);
                  }

                  if (var13 >= var3) {
                     break;
                  }
               }
            }

            return Set.copyOf(var9);
         }
      } else {
         return Set.of();
      }
   }

   public static boolean holds(ServerLevel var0, BlockPos var1) {
      Map var2 = LEASES.get(var0);
      if (var2 == null) {
         return false;
      } else {
         Long var3 = (Long)var2.get(var1);
         if (var3 == null) {
            return false;
         } else if (var3 <= var0.getGameTime()) {
            var2.remove(var1);
            return false;
         } else {
            return true;
         }
      }
   }

   @SubscribeEvent
   public static void unload(Unload var0) {
      LEASES.remove(var0.getLevel());
   }

   @SubscribeEvent
   public static void stop(ServerStoppedEvent var0) {
      LEASES.clear();
   }

   private FieldFlashlight() {
   }
}
