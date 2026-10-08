package com.formaworks.frontierhunts.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.client.renderer.RenderType.CompositeState;
import net.minecraft.resources.ResourceLocation;

final class RifleRenderType extends RenderType {
   private RifleRenderType() {
      super("frontier_rifle", DefaultVertexFormat.NEW_ENTITY, Mode.TRIANGLES, 262144, true, false, () -> {
      }, () -> {
      });
   }

   static RenderType createFor(ResourceLocation var0) {
      return create(
         "frontier_rifle",
         DefaultVertexFormat.NEW_ENTITY,
         Mode.TRIANGLES,
         262144,
         true,
         false,
         CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
            .setTextureState(new TextureStateShard(var0, true, true))
            .setCullState(NO_CULL)
            .setTransparencyState(NO_TRANSPARENCY)
            .setLightmapState(LIGHTMAP)
            .setOverlayState(OVERLAY)
            .createCompositeState(false)
      );
   }
}
