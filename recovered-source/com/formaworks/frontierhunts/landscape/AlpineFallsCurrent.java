package com.formaworks.frontierhunts.landscape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineFallsCurrent {
   private static final Map<ServerLevel, Map<Long, AlpineFallsCurrent.Field>> FIELDS = new WeakHashMap<>();

   @SubscribeEvent
   public static void tick(Post var0) {
      Entity var1 = var0.getEntity();
      if (var1.level() instanceof ServerLevel var2) {
         if (!var1.noPhysics && !var1.isSpectator() && var1.isInWater()) {
            if (var1 instanceof Player var36 && var36.getAbilities().flying) {
               return;
            }

            boolean var37 = var1 instanceof Player;
            if (var37 || (var1.tickCount + var1.getId()) % 2 == 0) {
               double var4 = var37 ? 1.0 : 2.0;
               AlpineFallsCurrent.Field var6 = field(var2, var1.blockPosition());
               BlockPos var7 = var1.blockPosition();
               Vec3 var8 = var1.getDeltaMovement();
               double var9 = 0.0;
               double var11 = 0.0;
               double var13 = 0.0;
               RandomSource var15 = var1.getRandom();
               boolean var16 = falling(var2, var7)
                  || falling(var2, BlockPos.containing(var1.getX(), var1.getY() + (double)var1.getBbHeight() * 0.6, var1.getZ()));
               if (var16) {
                  double var17 = -1.05;
                  var11 += (var17 - var8.y) * 0.32;
                  var9 += (var15.nextDouble() - 0.5) * 0.035 - var8.x * 0.08;
                  var13 += (var15.nextDouble() - 0.5) * 0.035 - var8.z * 0.08;
               } else {
                  AlpineFallsCurrent.Lip var41 = null;
                  double var18 = 1.0E9;

                  for (AlpineFallsCurrent.Lip var21 : var6.lips) {
                     if (!(Math.abs(var1.getY() - var21.y) > 1.6)) {
                        double var22 = Math.hypot(var21.x - var1.getX(), var21.z - var1.getZ());
                        double var24 = 2.5 + Math.min(4.5, (double)var21.drop * 0.12);
                        if (var22 < var24 && var22 < var18) {
                           var18 = var22;
                           var41 = var21;
                        }
                     }
                  }

                  if (var41 != null) {
                     double var42 = 2.5 + Math.min(4.5, (double)var41.drop * 0.12);
                     double var45 = Math.pow(1.0 - var18 / var42, 1.4) * (0.05 + Math.min(0.07, (double)var41.drop * 0.004));
                     double var47 = (var41.x - var1.getX()) / Math.max(0.3, var18);
                     double var26 = (var41.z - var1.getZ()) / Math.max(0.3, var18);
                     double var28 = var45 * 3.2;
                     var9 += (var47 * var28 - var8.x) * 0.18 * (var45 / 0.12 + 0.2);
                     var13 += (var26 * var28 - var8.z) * 0.18 * (var45 / 0.12 + 0.2);
                     if (var18 < 1.2) {
                        var11 -= 0.01;
                     }
                  }

                  for (AlpineFallsCurrent.Impact var44 : var6.impacts) {
                     double var46 = var44.y - var1.getY();
                     if (!(var46 < -1.5) && !(var46 > 7.0)) {
                        double var48 = var1.getX() - var44.x;
                        double var49 = var1.getZ() - var44.z;
                        double var50 = Math.hypot(var48, var49);
                        double var30 = Math.clamp((double)var44.height / 18.0, 0.25, 1.6);
                        double var32 = 2.2 + var30 * 1.8;
                        if (!(var50 > var32 + 2.5)) {
                           if (var50 < 1.3) {
                              var11 += -0.055 * var30 - 0.02 * var15.nextDouble();
                              var9 += (var15.nextDouble() - 0.5) * 0.06 * var30;
                              var13 += (var15.nextDouble() - 0.5) * 0.06 * var30;
                           } else {
                              double var34 = 0.045 * var30 * (1.0 - Math.clamp((var50 - 1.3) / (var32 + 1.2), 0.0, 1.0));
                              var9 += var48 / var50 * var34;
                              var13 += var49 / var50 * var34;
                              var11 += (var15.nextDouble() - 0.45) * 0.03 * var30 * (1.0 - Math.clamp(var50 / var32, 0.0, 1.0));
                           }
                        }
                     }
                  }
               }

               if (var9 != 0.0 || var11 != 0.0 || var13 != 0.0) {
                  var9 *= var4;
                  var11 *= var4;
                  var13 *= var4;
                  var1.setDeltaMovement(var8.x + Math.clamp(var9, -0.25, 0.25), Math.clamp(var8.y + var11, -1.4, 0.6), var8.z + Math.clamp(var13, -0.25, 0.25));
                  if (var37) {
                     var1.hurtMarked = true;
                  }
               }
            }
         }
      }
   }

   private static boolean falling(ServerLevel var0, BlockPos var1) {
      if (!var0.isLoaded(var1)) {
         return false;
      } else if (var0.getBlockState(var1).getBlock() instanceof AlpineCascade) {
         return true;
      } else {
         FluidState var2 = var0.getFluidState(var1);
         return var2.is(FluidTags.WATER) && var2.hasProperty(BlockStateProperties.FALLING) && (Boolean)var2.getValue(BlockStateProperties.FALLING);
      }
   }

   private static AlpineFallsCurrent.Field field(ServerLevel var0, BlockPos var1) {
      long var2 = BlockPos.asLong(var1.getX() >> 2, var1.getY() >> 2, var1.getZ() >> 2);
      Map var4 = FIELDS.computeIfAbsent(var0, var0x -> new HashMap<>());
      AlpineFallsCurrent.Field var5 = (AlpineFallsCurrent.Field)var4.get(var2);
      long var6 = var0.getGameTime();
      if (var5 != null && var6 - var5.tick < 40L) {
         return var5;
      } else {
         if (var4.size() > 768) {
            var4.entrySet().removeIf(var2x -> var6 - ((AlpineFallsCurrent.Field)var2x.getValue()).tick > 200L);
         }

         int var8 = (var1.getX() & -4) + 2;
         int var9 = (var1.getY() & -4) + 2;
         int var10 = (var1.getZ() & -4) + 2;
         if (!anyFalling(var0, var8, var9, var10)) {
            AlpineFallsCurrent.Field var24 = new AlpineFallsCurrent.Field(var6, List.of(), List.of());
            var4.put(var2, var24);
            return var24;
         } else {
            ArrayList var11 = new ArrayList();
            ArrayList var12 = new ArrayList();
            MutableBlockPos var13 = new MutableBlockPos();

            for (int var14 = -9; var14 <= 4; var14++) {
               for (int var15 = -7; var15 <= 7; var15++) {
                  for (int var16 = -7; var16 <= 7; var16++) {
                     var13.set(var8 + var16, var9 + var14, var10 + var15);
                     if (var0.isLoaded(var13)) {
                        BlockState var17 = var0.getBlockState(var13);
                        if (var17.getBlock() instanceof AlpineCascade) {
                           AlpineCascade.Part var26 = (AlpineCascade.Part)var17.getValue(AlpineCascade.PART);
                           if (var26 == AlpineCascade.Part.FOOT || var26 == AlpineCascade.Part.SHORT) {
                              int var27 = 1;
                              MutableBlockPos var30 = var13.mutable();

                              while (var27 < 96 && falling(var0, var30.move(0, 1, 0))) {
                                 var27++;
                              }

                              if (var27 >= 2) {
                                 var12.add(
                                    new AlpineFallsCurrent.Impact((double)var13.getX() + 0.5, (double)var13.getY() - 0.5, (double)var13.getZ() + 0.5, var27)
                                 );
                              }
                           }

                           if (var26 == AlpineCascade.Part.LIP) {
                              BlockPos var28 = var13.relative(((Direction)var17.getValue(AlpineCascade.FACING)).getOpposite());
                              int var31 = 1;
                              MutableBlockPos var32 = var13.mutable();

                              while (var31 < 120 && falling(var0, var32.move(0, -1, 0))) {
                                 var31++;
                              }

                              if (var31 >= 3) {
                                 var11.add(
                                    new AlpineFallsCurrent.Lip((double)var28.getX() + 0.5, (double)var28.getY() + 0.5, (double)var28.getZ() + 0.5, var31)
                                 );
                              }
                           }
                        } else {
                           FluidState var18 = var0.getFluidState(var13);
                           if (var18.is(FluidTags.WATER)) {
                              boolean var19 = var18.hasProperty(BlockStateProperties.FALLING) && (Boolean)var18.getValue(BlockStateProperties.FALLING);
                              if (!var19) {
                                 if (falling(var0, var13.below())) {
                                    int var29 = dropBelow(var0, var13);
                                    if (var29 >= 3) {
                                       var11.add(
                                          new AlpineFallsCurrent.Lip((double)var13.getX() + 0.5, (double)var13.getY() + 0.5, (double)var13.getZ() + 0.5, var29)
                                       );
                                    }
                                 }
                              } else {
                                 BlockPos var20 = var13.below();
                                 FluidState var21 = var0.getFluidState(var20);
                                 if (var21.is(FluidTags.WATER)
                                    && (!var21.hasProperty(BlockStateProperties.FALLING) || !(Boolean)var21.getValue(BlockStateProperties.FALLING))) {
                                    int var22 = 1;
                                    MutableBlockPos var23 = var13.mutable();

                                    while (var22 < 96 && falling(var0, var23.move(0, 1, 0))) {
                                       var22++;
                                    }

                                    if (var22 >= 2) {
                                       var12.add(
                                          new AlpineFallsCurrent.Impact(
                                             (double)var13.getX() + 0.5, (double)var13.getY() + 0.5, (double)var13.getZ() + 0.5, var22
                                          )
                                       );
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }

            AlpineFallsCurrent.Field var25 = var11.isEmpty() && var12.isEmpty()
               ? new AlpineFallsCurrent.Field(var6, List.of(), List.of())
               : new AlpineFallsCurrent.Field(var6, List.copyOf(var11), List.copyOf(var12));
            var4.put(var2, var25);
            return var25;
         }
      }
   }

   private static boolean anyFalling(ServerLevel var0, int var1, int var2, int var3) {
      for (int var4 = var1 - 7 >> 4; var4 <= var1 + 7 >> 4; var4++) {
         for (int var5 = var3 - 7 >> 4; var5 <= var3 + 7 >> 4; var5++) {
            LevelChunk var6 = var0.getChunkSource().getChunkNow(var4, var5);
            if (var6 != null) {
               for (int var7 = var2 - 9 >> 4; var7 <= var2 + 4 >> 4; var7++) {
                  int var8 = var6.getSectionIndexFromSectionY(var7);
                  if (var8 >= 0 && var8 < var6.getSectionsCount()) {
                     LevelChunkSection var9 = var6.getSection(var8);
                     if (!var9.hasOnlyAir()
                        && var9.maybeHas(
                           var0x -> {
                              if (var0x.getBlock() instanceof AlpineCascade) {
                                 return true;
                              } else {
                                 FluidState var1x = var0x.getFluidState();
                                 return var1x.is(FluidTags.WATER)
                                    && var1x.hasProperty(BlockStateProperties.FALLING)
                                    && (Boolean)var1x.getValue(BlockStateProperties.FALLING);
                              }
                           }
                        )) {
                        return true;
                     }
                  }
               }
            }
         }
      }

      return false;
   }

   private static int dropBelow(ServerLevel var0, BlockPos var1) {
      int var2 = 0;
      MutableBlockPos var3 = var1.mutable();

      while (var2 < 120 && falling(var0, var3.move(0, -1, 0))) {
         var2++;
      }

      return var2;
   }

   private static record Field(long tick, List<AlpineFallsCurrent.Lip> lips, List<AlpineFallsCurrent.Impact> impacts) {
   }

   private static record Impact(double x, double y, double z, int height) {
   }

   private static record Lip(double x, double y, double z, int drop) {
   }
}
