package com.formaworks.frontierhunts.landscape.ride;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

@EventBusSubscriber(
   modid = "frontierhunts",
   bus = Bus.MOD
)
public final class RideContent {
   static final Logger LOG = LogUtils.getLogger();
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WING_OPEN = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, FrontierHunts.id("wing_open")
   );
   public static final DeferredHolder<Item, Paraglider> PARAGLIDER = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("paraglider"));
   public static final DeferredHolder<Item, Wingsuit> WINGSUIT = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("wingsuit"));
   public static final DeferredHolder<Item, HorseWhistle> HORSE_WHISTLE = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("horse_whistle"));
   public static final DeferredHolder<Item, AtvItem> ATV_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("atv"));
   public static final DeferredHolder<EntityType<?>, EntityType<Atv>> ATV = DeferredHolder.create(Registries.ENTITY_TYPE, FrontierHunts.id("atv"));
   public static final String[] SOUNDS = new String[]{
      "atv_engine",
      "atv_start",
      "atv_stop",
      "horse_whistle",
      "glider_open",
      "glider_fold",
      "glider_wind",
      "wingsuit_flutter",
      "canopy_open",
      "atv_engine_high",
      "atv_whine"
   };
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATV_ENGINE = sound("atv_engine");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATV_START = sound("atv_start");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATV_STOP = sound("atv_stop");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_WHISTLE = sound("horse_whistle");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_GLIDER_OPEN = sound("glider_open");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_GLIDER_FOLD = sound("glider_fold");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_GLIDER_WIND = sound("glider_wind");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_SUIT_FLUTTER = sound("wingsuit_flutter");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_CANOPY_OPEN = sound("canopy_open");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATV_ENGINE_HIGH = sound("atv_engine_high");
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_ATV_WHINE = sound("atv_whine");

   private RideContent() {
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String id) {
      return DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id(id));
   }

   @SubscribeEvent
   public static void register(RegisterEvent event) {
      event.register(
         Registries.DATA_COMPONENT_TYPE,
         FrontierHunts.id("wing_open"),
         () -> DataComponentType.builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build()
      );
      event.register(Registries.ITEM, FrontierHunts.id("paraglider"), () -> new Paraglider(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
      event.register(Registries.ITEM, FrontierHunts.id("wingsuit"), () -> new Wingsuit(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
      event.register(Registries.ITEM, FrontierHunts.id("horse_whistle"), () -> new HorseWhistle(new Properties().stacksTo(1)));
      event.register(Registries.ITEM, FrontierHunts.id("atv"), () -> new AtvItem(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
      event.register(
         Registries.ENTITY_TYPE,
         FrontierHunts.id("atv"),
         () -> Builder.of(Atv::new, MobCategory.MISC).sized(1.55F, 1.3F).eyeHeight(1.05F).clientTrackingRange(10).updateInterval(2).build("frontierhunts:atv")
      );

      for (String id : SOUNDS) {
         event.register(Registries.SOUND_EVENT, FrontierHunts.id(id), () -> SoundEvent.createVariableRangeEvent(FrontierHunts.id(id)));
      }
   }

   @SubscribeEvent
   public static void payloads(RegisterPayloadHandlersEvent event) {
      PayloadRegistrar r = event.registrar("1").optional();
      r.playToServer(RidePayloads.Gait.TYPE, RidePayloads.Gait.CODEC, Horses::gait);
      r.playToClient(RidePayloads.HorseState.TYPE, RidePayloads.HorseState.CODEC, (p, c) -> c.enqueueWork(() -> RideHooks.horseState.accept(p)));
      r.playToServer(RidePayloads.Glider.TYPE, RidePayloads.Glider.CODEC, GliderServer::handle);
      r.playToServer(RidePayloads.Suit.TYPE, RidePayloads.Suit.CODEC, WingsuitServer::handle);
      r.playToServer(RidePayloads.AtvCrash.TYPE, RidePayloads.AtvCrash.CODEC, Atv::crashed);
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent event) {
      ResourceLocation key = event.getTabKey().location();
      if (key.equals(FrontierHunts.id("field_equipment"))) {
         event.accept((ItemLike)WINGSUIT.get());
         event.accept((ItemLike)ATV_ITEM.get());
         event.accept((ItemLike)HORSE_WHISTLE.get());
      } else if (key.equals(FrontierHunts.id("wildlife"))) {
         event.accept(Items.HORSE_SPAWN_EGG);
         event.accept(Items.DONKEY_SPAWN_EGG);
         event.accept(Items.MULE_SPAWN_EGG);
      }
   }
}
