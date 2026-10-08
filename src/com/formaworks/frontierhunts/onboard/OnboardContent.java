package com.formaworks.frontierhunts.onboard;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** [onboard] Registers the Frontier Handbook item and lists it next to the Hunter's Journal in the Field Equipment tab. */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class OnboardContent {
   public static Item HANDBOOK;

   private OnboardContent() {
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.ITEM, FrontierHunts.id("frontier_handbook"), () -> {
         HANDBOOK = new HandbookItem(new Item.Properties().stacksTo(1));
         return HANDBOOK;
      });
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() == HuntContent.GEAR_TAB && HANDBOOK != null) {
         TabPlacement.after(e, HANDBOOK, "hunter_journal", "expedition_guide"); // [onebook] HuntContent now lists it first (no-op)
      }
   }
}
