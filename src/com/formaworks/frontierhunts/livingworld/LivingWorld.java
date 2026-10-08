package com.formaworks.frontierhunts.livingworld;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * [livingworld] A lived-in world: hunting camps, outfitters' posts, ranger stations, trappers' cabins, meat sheds, elk
 * camps, abandoned camps, duck blinds, field-edge tree stands, ground blinds by food plots, glassing points, trailheads,
 * old fence lines and antler caches generated into new chunks (one structure type, "frontierhunts:living_site", with a
 * {@code kind} per structure json). Self-registering; no edits to FrontierHunts.java.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class LivingWorld {
   public static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
   public static final DeferredHolder<StructureType<?>, StructureType<LivingSite>> SITE = DeferredHolder.create(Registries.STRUCTURE_TYPE, id("living_site"));
   public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = DeferredHolder.create(Registries.STRUCTURE_PIECE, id("living_site"));

   private LivingWorld() {
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   @SubscribeEvent
   public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) {
      McTrees.install();
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.STRUCTURE_TYPE, id("living_site"), () -> (StructureType<LivingSite>)() -> LivingSite.CODEC);
      e.register(Registries.STRUCTURE_PIECE, id("living_site"), () -> (StructurePieceType.ContextlessType)LivingPiece::new);
   }
}
