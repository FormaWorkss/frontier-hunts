package com.formaworks.frontierhunts.client.terrain;

import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/** Retired smooth-terrain plant lift. Never installed; if constructed it is a plain pass-through. */
@Deprecated
public final class PlantOffsetModel extends BakedModelWrapper<BakedModel> {
   public PlantOffsetModel(BakedModel base) {
      super(base);
   }
}
