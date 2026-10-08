package com.formaworks.frontierhunts.phone;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [phone] Registers the Field Phone (self-registering on the mod bus, nothing in FrontierHunts.java), lists it in the
 * gear tab where the Camera Base Station was, and takes the retired base station out of creative.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class PhoneContent {
   public static final String ID = "field_phone";
   public static final DeferredHolder<Item, FieldPhoneItem> PHONE = DeferredHolder.create(Registries.ITEM, FrontierHunts.id(ID));

   private PhoneContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.ITEM, FrontierHunts.id(ID), FieldPhoneItem::new);
   }

   @SubscribeEvent(priority = EventPriority.LOW)
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      try {
         OldBaseStation.hideFromCreative(e);
         if (e.getTabKey() == HuntContent.GEAR_TAB) {
            com.formaworks.frontierhunts.items.TabPlacement.after(e, PHONE.get(), "trail_camera", "field_camera");
         }
      } catch (RuntimeException ex) {
         // a tab layout is cosmetic: never let it break the creative screen
      }
   }
}
