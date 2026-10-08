package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

public final class HuntRenderTypes extends RenderType {
   private static final Function<ResourceLocation, RenderType> SCULPT = Util.memoize(
      var0 -> create(
            "frontier_sculpt",
            DefaultVertexFormat.NEW_ENTITY,
            Mode.TRIANGLES,
            262144,
            true,
            false,
            CompositeState.builder()
               .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
               .setTextureState(new TextureStateShard(var0, false, false))
               .setTransparencyState(NO_TRANSPARENCY)
               .setLightmapState(LIGHTMAP)
               .setOverlayState(OVERLAY)
               .createCompositeState(false)
         )
   );
   private static final Function<ResourceLocation, RenderType> SUPPLIED = Util.memoize(
      var0 -> create(
            "frontier_supplied",
            DefaultVertexFormat.NEW_ENTITY,
            Mode.TRIANGLES,
            262144,
            true,
            false,
            CompositeState.builder()
               .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
               .setTextureState(new TextureStateShard(var0, false, false))
               .setCullState(NO_CULL)
               .setTransparencyState(NO_TRANSPARENCY)
               .setLightmapState(LIGHTMAP)
               .setOverlayState(OVERLAY)
               .createCompositeState(false)
         )
   );

   private HuntRenderTypes() {
      super("frontier_sculpt", DefaultVertexFormat.NEW_ENTITY, Mode.TRIANGLES, 262144, true, false, () -> {
      }, () -> {
      });
   }

   public static RenderType sculpt(ResourceLocation var0) {
      return SCULPT.apply(var0);
   }

   public static RenderType supplied(ResourceLocation var0) {
      return SUPPLIED.apply(var0);
   }
}
