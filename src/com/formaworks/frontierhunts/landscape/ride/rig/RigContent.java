package com.formaworks.frontierhunts.landscape.ride.rig;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [atvfuel] Registration for the ATV fuel system and rear-rack rig: fuel data component, jerry can, cargo box,
 * can carrier, the rig menu and the fuel / rig sounds. Self-registering (MOD bus), no edits to FrontierHunts.java.
 */
@EventBusSubscriber(modid = "frontierhunts", bus = EventBusSubscriber.Bus.MOD)
public final class RigContent {
   /** Litres of gasoline: on a jerry can (contents) and on an ATV item (tank level when it was picked up). */
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> FUEL = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("fuel_liters")
   );
   public static final DeferredHolder<Item, JerryCanItem> JERRY_CAN = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("jerry_can"));
   public static final DeferredHolder<Item, CargoBoxItem> CARGO_BOX = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("atv_cargo_box"));
   public static final DeferredHolder<Item, CanCarrierItem> CAN_CARRIER = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("atv_can_carrier"));
   public static final DeferredHolder<MenuType<?>, MenuType<RigMenu>> MENU = DeferredHolder.create(Registries.MENU, FrontierHunts.id("atv_rig"));

   static final String[] SOUNDS = {"atv_sputter", "atv_stall", "atv_crank", "fuel_pour", "rig_attach", "jerry_can_fill"};
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_SPUTTER = sound("atv_sputter");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_STALL = sound("atv_stall");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_CRANK = sound("atv_crank");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_POUR = sound("fuel_pour");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATTACH = sound("rig_attach");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_CAN_FILL = sound("jerry_can_fill");

   private RigContent() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(
         Registries.DATA_COMPONENT_TYPE,
         FrontierHunts.id("fuel_liters"),
         () -> DataComponentType.<Float>builder().persistent(Codec.floatRange(0.0F, 1000.0F)).networkSynchronized(ByteBufCodecs.FLOAT).build()
      );
      event.register(Registries.ITEM, FrontierHunts.id("jerry_can"), () -> new JerryCanItem(new Item.Properties().stacksTo(1)));
      event.register(Registries.ITEM, FrontierHunts.id("atv_cargo_box"), () -> new CargoBoxItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
      event.register(Registries.ITEM, FrontierHunts.id("atv_can_carrier"), () -> new CanCarrierItem(new Item.Properties().stacksTo(1)));
      event.register(Registries.MENU, FrontierHunts.id("atv_rig"), () -> IMenuTypeExtension.create(RigMenu::client));
      for (String id : SOUNDS) {
         event.register(Registries.SOUND_EVENT, FrontierHunts.id(id), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(id)));
      }
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent event) {
      if (event.getTabKey().location().equals(FrontierHunts.id("field_equipment"))) {
         event.accept(JERRY_CAN.get());
         event.accept(JerryCanItem.filled(JerryCanItem.CAPACITY));
         event.accept(CARGO_BOX.get());
         event.accept(CAN_CARRIER.get());
      }
   }

   /** Litres stored on a stack (0 when absent). */
   public static float fuelOf(ItemStack stack) {
      Float f = stack.get(FUEL.get());
      return f == null || !Float.isFinite(f) ? 0.0F : Math.max(0.0F, f);
   }
}
