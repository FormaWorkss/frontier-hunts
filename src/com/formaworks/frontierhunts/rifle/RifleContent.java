package com.formaworks.frontierhunts.rifle;

import com.formaworks.frontierhunts.FrontierHunts;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class RifleContent {
   public static final Items ITEMS = DeferredRegister.createItems("frontierhunts");
   public static final DeferredItem<Item> RIFLE = ITEMS.register("ridgeline_rifle", RifleItem::new);
   public static final DeferredItem<Item> AMMO = ITEMS.register("reserve_308", () -> new Item(new Properties()));
   /** [rifle] The Ridgeline's own 3-9x hunting scope as a loose part (removed at the Attachment Workbench to swap optics). */
   public static final DeferredItem<Item> SCOPE = ITEMS.register("ridgeline_scope", RidgelineScopeItem::new);
   private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, "frontierhunts");
   public static final DeferredHolder<EntityType<?>, EntityType<RifleBullet>> BULLET = ENTITIES.register(
      "rifle_bullet",
      () -> Builder.of(RifleBullet::new, MobCategory.MISC).sized(0.04F, 0.04F).clientTrackingRange(6).updateInterval(1).build("frontierhunts:rifle_bullet")
   );
   private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "frontierhunts");
   public static final DeferredHolder<SoundEvent, SoundEvent> SHOT = sound("rifle_shot");
   public static final DeferredHolder<SoundEvent, SoundEvent> UNLOCK = sound("rifle_unlock");
   public static final DeferredHolder<SoundEvent, SoundEvent> PULL = sound("rifle_pull");
   public static final DeferredHolder<SoundEvent, SoundEvent> PUSH = sound("rifle_push");
   public static final DeferredHolder<SoundEvent, SoundEvent> LOCK = sound("rifle_lock");
   public static final DeferredHolder<SoundEvent, SoundEvent> MAG_OUT = sound("rifle_mag_out");
   public static final DeferredHolder<SoundEvent, SoundEvent> MAG_IN = sound("rifle_mag_in");
   public static final DeferredHolder<SoundEvent, SoundEvent> DRY = sound("rifle_dry");
   public static final DeferredHolder<SoundEvent, SoundEvent> SHOT_FAR = sound("rifle_shot_far");

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String var0) {
      return SOUNDS.register(
         var0,
         () -> var0.equals("rifle_shot")
               ? SoundEvent.createFixedRangeEvent(FrontierHunts.id(var0), 96.0F)
               : SoundEvent.createVariableRangeEvent(FrontierHunts.id(var0))
      );
   }

   public static void register(IEventBus var0) {
      ITEMS.register(var0);
      ENTITIES.register(var0);
      SOUNDS.register(var0);
      var0.addListener(RifleNetwork::register);
   }

   private RifleContent() {
   }
}
