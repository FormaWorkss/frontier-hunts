package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import net.minecraft.resources.ResourceLocation;

/**
 * Coats of the box-model deer, elk and moose. [presets] Only the Vanilla preset draws box models now: the "enhanced"
 * coats of the retired Balanced (Minecraft+) look were removed from the jar, so every call resolves to the vanilla coat.
 */
public final class BlockyCoats {
   private static final ResourceLocation[] IDS = new ResourceLocation[GameSpecies.values().length * 2];

   private BlockyCoats() {
   }

   public static ResourceLocation coat(DeerTraits traits) {
      return coat(traits.species(), true, traits.greyCoat());
   }

   /** @param vanilla ignored ([presets] the enhanced coats are gone); kept for callers */
   public static ResourceLocation coat(GameSpecies species, boolean vanilla, boolean winter) {
      int i = species.ordinal() * 2 + (winter ? 1 : 0);
      ResourceLocation id = IDS[i];
      if (id == null) {
         id = FrontierHunts.id("textures/entity/blocky/" + species.id + "_vanilla_" + (winter ? "winter" : "summer") + ".png");
         IDS[i] = id;
      }
      return id;
   }
}
