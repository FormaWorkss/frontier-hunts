package com.formaworks.frontierhunts;

import com.formaworks.frontierhunts.camp.CampContent;
import com.formaworks.frontierhunts.environment.FoliageDensity;
import com.formaworks.frontierhunts.environment.HuntingForest;
import com.formaworks.frontierhunts.expedition.EquipmentSounds;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.GhillieSuit;
import com.formaworks.frontierhunts.expedition.ScoutingNetwork;
import com.formaworks.frontierhunts.hunting.HuntEntities;
import com.formaworks.frontierhunts.hunting.HuntParticles;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import com.formaworks.frontierhunts.progression.AssignmentNetwork;
import com.formaworks.frontierhunts.rifle.RifleContent;
import com.formaworks.frontierhunts.tracking.TrailNetwork;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;

@Mod("frontierhunts")
public final class FrontierHunts {
   public static final String ID = "frontierhunts";

   public FrontierHunts(IEventBus var1, ModContainer var2) {
      HuntRules.bootstrap();
      GhillieSuit.MATERIALS.register(var1);
      EquipmentSounds.register(var1);
      ExpeditionContent.register(var1);
      HuntingForest.register(var1);
      FoliageDensity.register(var1);
      WorkshopContent.register(var1);
      HuntContent.register(var1);
      RifleContent.register(var1);
      CampContent.register(var1);
      HuntEntities.register(var1);
      HuntSounds.SOUNDS.register(var1);
      HuntParticles.TYPES.register(var1);
      var1.addListener(HuntDefinitions::register);
      var1.addListener(HuntNetwork::register);
      var1.addListener(TrailNetwork::register);
      var1.addListener(AssignmentNetwork::register);
      var1.addListener(ScoutingNetwork::register);
      var2.registerConfig(Type.SERVER, HuntConfig.SERVER);
      var2.registerConfig(Type.CLIENT, HuntConfig.CLIENT);
      var2.registerConfig(Type.COMMON, HuntConfig.WORLDGEN);
   }

   public static ResourceLocation id(String var0) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", var0);
   }
}
