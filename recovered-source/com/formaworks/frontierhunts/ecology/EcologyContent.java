package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.ecology.bones.BoneSiteFeature;
import com.formaworks.frontierhunts.ecology.bones.BoneSpecies;
import com.formaworks.frontierhunts.ecology.bones.ScatteredBonesBlock;
import com.formaworks.frontierhunts.ecology.bones.ShedAntlerBlock;
import com.formaworks.frontierhunts.ecology.bones.SkullBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [ecology] Bone blocks and items, the kill-carcass entity, the bone-site feature and the howl sounds (self-registering). */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class EcologyContent {
   public static final Map<BoneSpecies, DeferredHolder<Block, SkullBlock>> SKULLS = new EnumMap<>(BoneSpecies.class);
   public static final Map<BoneSpecies, DeferredHolder<Item, Item>> SKULL_ITEMS = new EnumMap<>(BoneSpecies.class);
   public static final DeferredHolder<Block, ScatteredBonesBlock> SCATTERED_BONES = DeferredHolder.create(Registries.BLOCK, id("scattered_bones"));
   public static final DeferredHolder<Item, Item> SCATTERED_BONES_ITEM = DeferredHolder.create(Registries.ITEM, id("scattered_bones"));
   public static final DeferredHolder<Block, ShedAntlerBlock> SHED_ANTLER = DeferredHolder.create(Registries.BLOCK, id("shed_antler"));
   public static final DeferredHolder<Item, Item> SHED_ANTLER_ITEM = DeferredHolder.create(Registries.ITEM, id("shed_antler"));
   public static final DeferredHolder<EntityType<?>, EntityType<KillCarcass>> KILL_CARCASS = DeferredHolder.create(
      Registries.ENTITY_TYPE, id("kill_carcass")
   );
   public static final DeferredHolder<Feature<?>, BoneSiteFeature> BONE_SITE = DeferredHolder.create(Registries.FEATURE, id("bone_site"));
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HOWL = DeferredHolder.create(Registries.SOUND_EVENT, id("wolf_howl"));
   public static final DeferredHolder<SoundEvent, SoundEvent> WOLF_CHORUS = DeferredHolder.create(Registries.SOUND_EVENT, id("wolf_chorus"));

   static {
      for (BoneSpecies s : BoneSpecies.values()) {
         SKULLS.put(s, DeferredHolder.create(Registries.BLOCK, id(s.id + "_skull")));
         SKULL_ITEMS.put(s, DeferredHolder.create(Registries.ITEM, id(s.id + "_skull")));
      }
   }

   private EcologyContent() {
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   public static SkullBlock skull(BoneSpecies s) {
      return SKULLS.get(s).get();
   }

   private static BlockBehaviour.Properties bone() {
      return BlockBehaviour.Properties.of()
         .mapColor(MapColor.TERRACOTTA_WHITE)
         .strength(0.35F)
         .sound(SoundType.BONE_BLOCK)
         .noCollission()
         .noOcclusion()
         .randomTicks()
         .pushReaction(PushReaction.DESTROY);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      for (BoneSpecies s : BoneSpecies.values()) {
         e.register(Registries.BLOCK, id(s.id + "_skull"), () -> new SkullBlock(s, bone()));
         e.register(Registries.ITEM, id(s.id + "_skull"), () -> new BlockItem(SKULLS.get(s).get(), new Item.Properties().stacksTo(16)));
      }
      e.register(Registries.BLOCK, id("scattered_bones"), () -> new ScatteredBonesBlock(bone()));
      e.register(Registries.ITEM, id("scattered_bones"), () -> new BlockItem(SCATTERED_BONES.get(), new Item.Properties()));
      e.register(Registries.BLOCK, id("shed_antler"), () -> new ShedAntlerBlock(bone()));
      e.register(Registries.ITEM, id("shed_antler"), () -> new BlockItem(SHED_ANTLER.get(), new Item.Properties().stacksTo(16)));
      e.register(
         Registries.ENTITY_TYPE,
         id("kill_carcass"),
         () -> EntityType.Builder.<KillCarcass>of(KillCarcass::new, MobCategory.MISC)
               .sized(1.2F, 0.55F)
               .clientTrackingRange(8)
               .updateInterval(20)
               .fireImmune()
               .build("frontierhunts:kill_carcass")
      );
      e.register(Registries.FEATURE, id("bone_site"), BoneSiteFeature::new);
      e.register(Registries.SOUND_EVENT, id("wolf_howl"), () -> SoundEvent.createVariableRangeEvent(id("wolf_howl")));
      e.register(Registries.SOUND_EVENT, id("wolf_chorus"), () -> SoundEvent.createVariableRangeEvent(id("wolf_chorus")));
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().location().equals(id("world"))) {
         // with the other things found lying on the forest floor
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SHED_ANTLER_ITEM.get(), "deadfall_log", "fallen_branch", "forest_litter");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SKULL_ITEMS.get(BoneSpecies.WHITETAIL).get(), "shed_antler");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SKULL_ITEMS.get(BoneSpecies.ELK).get(), "whitetail_skull");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SKULL_ITEMS.get(BoneSpecies.MOOSE).get(), "elk_skull");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SKULL_ITEMS.get(BoneSpecies.BISON).get(), "moose_skull");
         com.formaworks.frontierhunts.items.TabPlacement.after(e, (ItemLike)SCATTERED_BONES_ITEM.get(), "bison_skull");
      }
   }
}
