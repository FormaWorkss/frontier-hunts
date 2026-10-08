package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [licence] Registration of the licence system: the permit / filled-tag item components, the Hunting Licence, the eight
 * big-game tags, the two bird stamps, the filled tag and the counter sounds. Self-registering (RegisterEvent).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class LicenceContent {
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<PermitData>> PERMIT = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("permit")
   );
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<FilledTag>> FILLED = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("filled_tag")
   );

   private static final Map<String, Supplier<Item>> FACTORIES = new LinkedHashMap<>();
   private static final Map<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

   public static final DeferredHolder<Item, Item> LICENCE = reg(Regulations.LICENCE_ITEM,
      () -> new PermitItem(PermitItem.Kind.LICENCE, null, null, new Item.Properties().stacksTo(1)));
   public static final Map<Regulations.TagKind, DeferredHolder<Item, Item>> TAGS = new LinkedHashMap<>();
   public static final Map<Regulations.Stamp, DeferredHolder<Item, Item>> STAMPS = new LinkedHashMap<>();

   static {
      for (Regulations.TagKind k : Regulations.TagKind.values()) {
         TAGS.put(k, reg(k.item, () -> new PermitItem(PermitItem.Kind.TAG, k, null, new Item.Properties().stacksTo(16))));
      }
      for (Regulations.Stamp s : Regulations.Stamp.values()) {
         STAMPS.put(s, reg(s.item, () -> new PermitItem(PermitItem.Kind.STAMP, null, s, new Item.Properties().stacksTo(1))));
      }
   }

   public static final DeferredHolder<Item, Item> FILLED_TAG = reg("filled_tag",
      () -> new FilledTagItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

   public static final DeferredHolder<SoundEvent, SoundEvent> SND_STAMP = sound("ui.licence_stamp");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_PUNCH = sound("ui.tag_punch");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_WARDEN = sound("ui.warden_notice");
   private static final String[] SOUNDS = {"ui.licence_stamp", "ui.tag_punch", "ui.warden_notice"};

   private LicenceContent() {
   }

   private static DeferredHolder<Item, Item> reg(String name, Supplier<Item> f) {
      FACTORIES.put(name, f);
      DeferredHolder<Item, Item> h = DeferredHolder.create(Registries.ITEM, FrontierHunts.id(name));
      ITEMS.put(name, h);
      return h;
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
   }

   public static Item tag(Regulations.TagKind k) {
      return TAGS.get(k).get();
   }

   public static Item stamp(Regulations.Stamp s) {
      return STAMPS.get(s).get();
   }

   public static List<Item> items() {
      List<Item> out = new ArrayList<>();
      for (DeferredHolder<Item, Item> h : ITEMS.values()) {
         if (h.isBound()) {
            out.add(h.get());
         }
      }
      return out;
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("permit"), () -> DataComponentType.<PermitData>builder()
         .persistent(PermitData.CODEC).networkSynchronized(PermitData.STREAM_CODEC).build());
      e.register(Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("filled_tag"), () -> DataComponentType.<FilledTag>builder()
         .persistent(FilledTag.CODEC).networkSynchronized(FilledTag.STREAM_CODEC).build());
      for (Map.Entry<String, Supplier<Item>> f : FACTORIES.entrySet()) {
         e.register(Registries.ITEM, FrontierHunts.id(f.getKey()), f.getValue());
      }
      for (String s : SOUNDS) {
         ResourceLocation id = FrontierHunts.id(s);
         e.register(Registries.SOUND_EVENT, id, () -> SoundEvent.createVariableRangeEvent(id));
      }
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() != HuntContent.GEAR_TAB) {
         return;
      }
      String anchor = "frontier_handbook"; // [onebook] was expedition_guide (no longer in the tab: the Handbook is the only book)
      for (Map.Entry<String, DeferredHolder<Item, Item>> h : ITEMS.entrySet()) {
         if (h.getValue().isBound()) {
            TabPlacement.after(e, h.getValue().get(), anchor, "hunter_journal");
            anchor = h.getKey();
         }
      }
   }
}
