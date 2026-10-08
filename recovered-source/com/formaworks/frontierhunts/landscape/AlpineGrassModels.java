package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.HuntConfig.GrassStyle;
import com.formaworks.frontierhunts.HuntConfig.GrassThickness;
import com.formaworks.frontierhunts.client.FrontierSettingsScreen;
import com.google.common.collect.UnmodifiableIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent.Loading;
import net.neoforged.fml.event.config.ModConfigEvent.Reloading;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT},
   bus = Bus.MOD
)
/** Pasture grass models (style, height, width, thickness). [shaderperf] copied from the base jar. */
public final class AlpineGrassModels {
   private static volatile int stamp;
   private static volatile GrassStyle style = GrassStyle.FRONTIER;
   private static volatile int heightPercent = 100;
   private static volatile int widthPercent = 100;
   private static volatile GrassThickness thickness = GrassThickness.NORMAL;

   public static GrassStyle style() {
      return style;
   }

   @SubscribeEvent
   public static void bake(ModifyBakingResult var0) {
      Map var1 = var0.getModels();
      UnmodifiableIterator var2 = AlpineRegistration.PASTURE.get().getStateDefinition().getPossibleStates().iterator();

      while (var2.hasNext()) {
         BlockState var3 = (BlockState)var2.next();
         ModelResourceLocation var4 = BlockModelShaper.stateToModelLocation(var3);
         BakedModel var5 = (BakedModel)var1.get(var4);
         if (var5 != null && !(var5 instanceof AlpineGrassModels.Sward)) {
            var1.put(var4, new AlpineGrassModels.Sward(var5, var3));
         }
      }

      stamp++;
   }

   @SubscribeEvent
   public static void loaded(Loading var0) {
      refresh(var0.getConfig());
   }

   @SubscribeEvent
   public static void reloaded(Reloading var0) {
      refresh(var0.getConfig());
   }

   @SubscribeEvent
   public static void setup(FMLClientSetupEvent var0) {
      ModList.get()
         .getModContainerById("frontierhunts")
         .ifPresent(var0x -> var0x.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(var0xx, var1) -> new FrontierSettingsScreen(var1)));
   }

   private static void refresh(ModConfig var0) {
      if (var0.getSpec() == HuntConfig.CLIENT) {
         apply();
      }
   }

   public static void apply() {
      apply(true);
   }

   /**
    * [shaderperf] The shader pack was switched on or off: take the grass thickness to draw (Thick reads as Normal while
    * the lighter-with-shader-packs setting applies) without a re-mesh of our own; the caller re-meshes the world next.
    */
   public static void followShaderPack() {
      apply(false);
   }

   private static void apply(boolean remesh) { // [shaderperf] remesh=false: the caller re-meshes
      GrassStyle var0;
      int var1;
      int var2;
      GrassThickness var3;
      try {
         var0 = (GrassStyle)HuntConfig.GRASS_STYLE.get();
         var1 = HuntConfig.GRASS_HEIGHT_PERCENT.get();
         var2 = HuntConfig.GRASS_WIDTH_PERCENT.get();
         var3 = (GrassThickness)HuntConfig.GRASS_THICKNESS.get();
      } catch (RuntimeException var5) {
         return;
      }
      var3 = com.formaworks.frontierhunts.perf.client.ShaderPerf.grassThickness(var3); // [shaderperf] Thick draws Normal under a shader pack

      if (var0 != style || var1 != heightPercent || var2 != widthPercent || var3 != thickness) {
         style = var0;
         heightPercent = var1;
         widthPercent = var2;
         thickness = var3;
         stamp++;
         if (!remesh) { // [shaderperf]
            return;
         }
         Minecraft var4 = Minecraft.getInstance();
         var4.execute(() -> {
            if (var4.levelRenderer != null && var4.level != null) {
               var4.levelRenderer.allChanged();
            }
         });
      }
   }

   static BakedQuad transform(BakedQuad var0, double var1, double var3, boolean var5) {
      return transform(var0, var1, var3, var5, 1.0);
   }

   static BakedQuad transform(BakedQuad var0, double var1, double var3, boolean var5, double var6) {
      int[] var8 = (int[])var0.getVertices().clone();
      int var9 = var8.length / 4;
      double var10 = Double.MAX_VALUE;

      for (int var12 = 0; var12 < 4; var12++) {
         var10 = Math.min(var10, (double)Float.intBitsToFloat(var8[var12 * var9 + 1]));
      }

      for (int var23 = 0; var23 < 4; var23++) {
         int var13 = var23 * var9;
         float var14 = Float.intBitsToFloat(var8[var13]);
         float var15 = Float.intBitsToFloat(var8[var13 + 1]);
         float var16 = Float.intBitsToFloat(var8[var13 + 2]);
         var15 = (float)(var10 + ((double)var15 - var10) * var1 + var3);
         if (var6 != 1.0) {
            var14 = (float)(0.5 + ((double)var14 - 0.5) * var6);
            var16 = (float)(0.5 + ((double)var16 - 0.5) * var6);
         }

         if (var5) {
            float var17 = 0.5F + (0.5F - var16) * 0.88F;
            float var18 = 0.5F + (var14 - 0.5F) * 0.88F;
            var14 = var17;
            var16 = var18;
            int var19 = var8[var13 + 7];
            byte var20 = (byte)(var19 & 0xFF);
            byte var21 = (byte)(var19 >> 8 & 0xFF);
            byte var22 = (byte)(var19 >> 16 & 0xFF);
            var8[var13 + 7] = var19 & 0xFF000000 | (var21 & 255) << 8 | -var22 & 0xFF | (var20 & 255) << 16;
         }

         var8[var13] = Float.floatToRawIntBits(var14);
         var8[var13 + 1] = Float.floatToRawIntBits(var15);
         var8[var13 + 2] = Float.floatToRawIntBits(var16);
      }

      Direction var24 = var0.getDirection();
      if (var5 && var24.getAxis().isHorizontal()) {
         var24 = var24.getClockWise();
      }

      return new BakedQuad(var8, var0.getTintIndex(), var24, var0.getSprite(), var0.isShade(), var0.hasAmbientOcclusion());
   }

   private AlpineGrassModels() {
   }

   static final class Sward extends BakedModelWrapper<BakedModel> {
      private final int heightIndex;
      private final int sink;
      private final Map<List<BakedQuad>, List<BakedQuad>> cache = new ConcurrentHashMap<>();
      private volatile int seen = -1;

      Sward(BakedModel var1, BlockState var2) {
         super(var1);
         this.heightIndex = var2.getValue(AlpinePasture.HEIGHT);
         this.sink = var2.getValue(AlpinePasture.SINK);
      }

      @Override
      public List<BakedQuad> getQuads(BlockState var1, Direction var2, RandomSource var3, ModelData var4, RenderType var5) {
         GrassStyle var6 = AlpineGrassModels.style;
         if (var6 == GrassStyle.OFF) {
            return List.of();
         } else if (var6 == GrassStyle.FRONTIER
            && AlpineGrassModels.heightPercent == 100
            && AlpineGrassModels.widthPercent == 100
            && AlpineGrassModels.thickness == GrassThickness.NORMAL) {
            return this.originalModel.getQuads(var1, var2, var3, var4, var5);
         } else if (var2 != null) {
            return List.of();
         } else {
            if (this.seen != AlpineGrassModels.stamp) {
               this.cache.clear();
               this.seen = AlpineGrassModels.stamp;
            }

            List var7;
            if (var6 == GrassStyle.VANILLA) {
               BakedModel var8 = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.SHORT_GRASS.defaultBlockState());
               var7 = var8.getQuads(Blocks.SHORT_GRASS.defaultBlockState(), null, var3, ModelData.EMPTY, null);
            } else {
               var7 = this.originalModel.getQuads(var1, null, var3, var4, var5);
            }

            return this.cache.computeIfAbsent(var7, this::redraw);
         }
      }

      @Override
      public List<BakedQuad> getQuads(BlockState var1, Direction var2, RandomSource var3) {
         return this.getQuads(var1, var2, var3, ModelData.EMPTY, null);
      }

      private List<BakedQuad> redraw(List<BakedQuad> var1) {
         boolean var2 = AlpineGrassModels.style == GrassStyle.VANILLA;
         double var3 = (double)AlpineGrassModels.heightPercent / 100.0;
         double var5 = (double)AlpineGrassModels.widthPercent / 100.0;
         if (var2 && this.heightIndex == 2) {
            var3 *= 1.18;
         }

         double var7 = var2 ? (double)(-this.sink * 2) / 16.0 : 0.0;
         ArrayList var9 = new ArrayList();

         for (int var10 = 0; var10 < var1.size(); var10++) {
            if (var2 || AlpineGrassModels.thickness != GrassThickness.LIGHT || var10 / 2 % 2 != 1) {
               var9.add(AlpineGrassModels.transform(var1.get(var10), var3, var7, false, var5));
            }
         }

         if (AlpineGrassModels.thickness == GrassThickness.THICK) {
            for (BakedQuad var11 : var1) {
               var9.add(AlpineGrassModels.transform(var11, var3 * 0.92, var7, true, var5));
            }
         }

         return List.copyOf(var9);
      }
   }
}
