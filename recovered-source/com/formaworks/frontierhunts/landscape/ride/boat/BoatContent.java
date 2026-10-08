package com.formaworks.frontierhunts.landscape.ride.boat;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [1.1.0] Registration for the wooden rowboat and the jon boat (entities and the items that place them). */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class BoatContent {
   public static final DeferredHolder<EntityType<?>, EntityType<RowBoat>> ROWBOAT = DeferredHolder.create(Registries.ENTITY_TYPE, FrontierHunts.id("rowboat"));
   public static final DeferredHolder<EntityType<?>, EntityType<JonBoat>> JON_BOAT = DeferredHolder.create(Registries.ENTITY_TYPE, FrontierHunts.id("jon_boat"));
   public static final DeferredHolder<Item, FrontierBoatItem> ROWBOAT_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("rowboat"));
   public static final DeferredHolder<Item, FrontierBoatItem> JON_BOAT_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("jon_boat"));

   private BoatContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(Registries.ENTITY_TYPE, FrontierHunts.id("rowboat"), () -> EntityType.Builder.<RowBoat>of(RowBoat::new, MobCategory.MISC)
         .sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10).build("frontierhunts:rowboat"));
      event.register(Registries.ENTITY_TYPE, FrontierHunts.id("jon_boat"), () -> EntityType.Builder.<JonBoat>of(JonBoat::new, MobCategory.MISC)
         .sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10).build("frontierhunts:jon_boat"));
      event.register(Registries.ITEM, FrontierHunts.id("rowboat"), () -> new FrontierBoatItem(new Item.Properties().stacksTo(1), false));
      event.register(Registries.ITEM, FrontierHunts.id("jon_boat"), () -> new FrontierBoatItem(new Item.Properties().stacksTo(1), true));
   }

   /** [1.2.8] the vehicles together in the tab: ATV, rowboat, jon boat, then the snowmobile (added after these, at lowest) */
   @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOW)
   public static void creative(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().location().equals(FrontierHunts.id("field_equipment"))) {
         com.formaworks.frontierhunts.items.TabPlacement.after(event, ROWBOAT_ITEM.get(), "atv");
         com.formaworks.frontierhunts.items.TabPlacement.after(event, JON_BOAT_ITEM.get(), "rowboat");
      }
   }
}
