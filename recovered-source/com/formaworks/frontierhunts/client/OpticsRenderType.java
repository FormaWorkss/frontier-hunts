package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;

final class OpticsRenderType extends RenderType {
   static final RenderType ORGANS = create(false);
   static final RenderType THERMAL = create(true);
   static final RenderType CAVITY = create(false);
   static final RenderType ANIMAL_ORGANS = create(
      "frontier_animal_cutaway",
      DefaultVertexFormat.NEW_ENTITY,
      Mode.QUADS,
      262144,
      false,
      false,
      CompositeState.builder()
         .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
         .setTextureState(new TextureStateShard(WhitetailRenderer.MATERIAL, false, false))
         .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
         .setCullState(NO_CULL)
         .setDepthTestState(NO_DEPTH_TEST)
         .setWriteMaskState(COLOR_WRITE)
         .setLightmapState(NO_LIGHTMAP)
         .setOverlayState(OVERLAY)
         .createCompositeState(false)
   );

   private OpticsRenderType() {
      super("frontier_anatomy", DefaultVertexFormat.NEW_ENTITY, Mode.QUADS, 65536, false, true, () -> {
      }, () -> {
      });
   }

   private static RenderType create(boolean var0) {
      return create(
         var0 ? "frontier_thermal" : "frontier_anatomy",
         DefaultVertexFormat.NEW_ENTITY,
         var0 ? Mode.TRIANGLES : Mode.QUADS,
         262144,
         false,
         true,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
            .setTextureState(new TextureStateShard(WhitetailRenderer.MATERIAL, false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setCullState(NO_CULL)
            .setDepthTestState(NO_DEPTH_TEST)
            .setWriteMaskState(COLOR_WRITE)
            .setLightmapState(NO_LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(false)
      );
   }
}
