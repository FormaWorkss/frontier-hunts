package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.environment.CascadeMist;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi.Delayed;
import com.seibel.distanthorizons.api.interfaces.block.IDhApiBlockStateWrapper;
import com.seibel.distanthorizons.api.interfaces.factories.IDhApiWrapperFactory;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.DhApiEventRegister;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiChunkProcessingEvent.EventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiEventParam;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

final class AlpineDhBridge extends DhApiChunkProcessingEvent {
   private static final byte KEEP = 0;
   private static final byte AIR = 1;
   private static final byte WATER = 2;
   private static final Map<BlockState, Byte> DECISION = new ConcurrentHashMap<>();
   private static final Map<IDhApiLevelWrapper, IDhApiBlockStateWrapper> WATER_BY_LEVEL = new ConcurrentHashMap<>();
   private volatile IDhApiBlockStateWrapper air;
   private final AtomicBoolean observed = new AtomicBoolean();

   static void register() {
      DhApiResult var0 = DhApiEventRegister.on(DhApiChunkProcessingEvent.class, new AlpineDhBridge());
      LogUtils.getLogger().info("Frontier landscape LOD adapter registered with Distant Horizons: {}", var0.success);
   }

   public void blockOrBiomeChangedDuringChunkProcessing(DhApiEventParam<EventParam> var1) {
      if (var1 != null && var1.value != null) {
         EventParam var2 = (EventParam)var1.value;
         if (var2.currentBlock != null && var2.getBlockOverride() == null) {
            if (var2.currentBlock.getWrappedMcObject() instanceof BlockState var3) {
               Byte var6 = DECISION.get(var3);
               if (var6 == null) {
                  var6 = decide(var3);
                  DECISION.put(var3, var6);
               }

               if (var6 != 0) {
                  IDhApiBlockStateWrapper var5 = var6 == 2 ? water(var2.levelWrapper) : this.air();
                  if (var5 == null) {
                     var5 = this.air();
                  }

                  if (var5 != null) {
                     var2.setBlockOverride(var5);
                     if (this.observed.compareAndSet(false, true)) {
                        LogUtils.getLogger().info("Frontier landscape LOD adapter is replacing small and see-through landscape blocks in distant terrain");
                     }
                  }
               }
            }
         }
      }
   }

   private IDhApiBlockStateWrapper air() {
      IDhApiBlockStateWrapper var1 = this.air;
      if (var1 == null) {
         IDhApiWrapperFactory var2 = Delayed.wrapperFactory;
         if (var2 == null) {
            return null;
         }

         this.air = var1 = var2.getAirBlockStateWrapper();
      }

      return var1;
   }

   private static IDhApiBlockStateWrapper water(IDhApiLevelWrapper var0) {
      if (var0 == null) {
         return null;
      } else {
         IDhApiBlockStateWrapper var1 = WATER_BY_LEVEL.get(var0);
         if (var1 != null) {
            return var1;
         } else {
            IDhApiWrapperFactory var2 = Delayed.wrapperFactory;
            if (var2 == null) {
               return null;
            } else {
               try {
                  IDhApiBlockStateWrapper var3 = var2.getDefaultBlockStateWrapper("minecraft:water", var0);
                  if (var3 != null) {
                     if (WATER_BY_LEVEL.size() > 16) {
                        WATER_BY_LEVEL.clear();
                     }

                     WATER_BY_LEVEL.put(var0, var3);
                  }

                  return var3;
               } catch (Exception var4) {
                  return null;
               }
            }
         }
      }
   }

   private static byte decide(BlockState var0) {
      Block var1 = var0.getBlock();
      boolean var2 = var0.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean)var0.getValue(BlockStateProperties.WATERLOGGED);
      if (var1 instanceof AlpineFoam
         || var1 instanceof CascadeMist
         || var1 instanceof AlpineSpray
         || var1 instanceof AlpineFlow
         || var1 instanceof AlpineCascade
         || var1 instanceof AlpineTracks
         || var1 instanceof AlpineSticks
         || var1 instanceof AlpineBranchStub
         || var1 instanceof AlpinePasture
         || var1 instanceof AlpineOvergrowth) {
         return (byte)(var2 ? 2 : 1);
      } else if (var1 instanceof AlpinePole) {
         return (byte)(var2 ? 2 : 1);
      } else if (var1 instanceof AlpineTurf) {
         return (byte)(var0.getValue(AlpineTurf.LAYERS) < 4 ? 1 : 0);
      } else if (!(var1 instanceof AlpineBoulder) && !(var1 instanceof AlpineOutcrop) && !(var1 instanceof AlpineGiantBoulder)) {
         return 0;
      } else {
         double var3 = 0.0;

         try {
            for (AABB var6 : var0.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
               var3 += (var6.maxX - var6.minX) * (var6.maxY - var6.minY) * (var6.maxZ - var6.minZ);
            }
         } catch (RuntimeException var7) {
            return 0;
         }

         if (var3 < 0.3) {
            return (byte)(var2 ? 2 : 1);
         } else {
            return 0;
         }
      }
   }
}
