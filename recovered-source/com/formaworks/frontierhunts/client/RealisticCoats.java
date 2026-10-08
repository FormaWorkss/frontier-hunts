package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

public final class RealisticCoats {
   public static final ResourceLocation ANTLER = FrontierHunts.id("textures/entity/realistic/whitetail_antler.png");
   private static final Set<ResourceLocation> REGISTERED = new HashSet<>();

   private RealisticCoats() {
   }

   static boolean ultra() {
      return FrontierGraphics.animalDetail() >= 3;
   }

   public static ResourceLocation coat(GameSpecies var0, boolean var1) {
      String var2 = var0.coatTexture(var1).substring("textures/entity/".length());
      return FrontierHunts.id(ultra() ? "textures/entity/realistic/" + var2 : "textures/entity/realistic/1k/" + var2);
   }

   public static ResourceLocation coat(DeerTraits var0) {
      return ensure(coat(var0.species(), var0.greyCoat()));
   }

   public static ResourceLocation antler() {
      return ensure(ANTLER);
   }

   private static ResourceLocation ensure(ResourceLocation var0) {
      if (REGISTERED.add(var0)) {
         Minecraft var1 = Minecraft.getInstance();
         if (var1 != null) {
            var1.getTextureManager().register(var0, new MipmappedHuntTexture(var0));
         }
      }

      return var0;
   }

   static void reset() {
      REGISTERED.clear();
   }
}
