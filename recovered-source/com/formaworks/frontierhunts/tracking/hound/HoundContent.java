package com.formaworks.frontierhunts.tracking.hound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [tracking] Registers the tracking hound and the Hound Lead. */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class HoundContent {
   public static final DeferredHolder<EntityType<?>, EntityType<TrackingHound>> HOUND = DeferredHolder.create(Registries.ENTITY_TYPE, id("tracking_hound"));
   public static final DeferredHolder<Item, Item> LEAD = DeferredHolder.create(Registries.ITEM, id("hound_lead"));

   private HoundContent() {
   }

   static ResourceLocation id(String name) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", name);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(
         Registries.ENTITY_TYPE,
         id("tracking_hound"),
         () -> EntityType.Builder.<TrackingHound>of(TrackingHound::new, MobCategory.CREATURE)
            .sized(0.62F, 0.85F)
            .eyeHeight(0.72F)
            .clientTrackingRange(10)
            .updateInterval(2)
            .build("frontierhunts:tracking_hound")
      );
      e.register(Registries.ITEM, id("hound_lead"), () -> new HoundLeadItem(new Item.Properties().stacksTo(1)));
   }

   @SubscribeEvent
   public static void attributes(EntityAttributeCreationEvent e) {
      e.put(HOUND.get(), TrackingHound.attributes().build());
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().location().equals(id("field_equipment"))) {
         // [items] next to the scouting / tracking gear instead of the end of the tab
         com.formaworks.frontierhunts.items.TabPlacement.after(e, LEAD.get(), "wind_checker", "rattling_antlers", "deer_call", "binoculars");
      }
   }
}
