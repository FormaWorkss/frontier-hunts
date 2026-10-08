package com.formaworks.frontierhunts.client.terrain;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Retired smooth-terrain model. Never installed; if constructed it is a plain pass-through. */
@Deprecated
public final class SmoothTerrainModel extends BakedModelWrapper<BakedModel> {
   public SmoothTerrainModel(BakedModel base, boolean grass, TextureAtlasSprite tuft) {
      super(base);
   }
}
