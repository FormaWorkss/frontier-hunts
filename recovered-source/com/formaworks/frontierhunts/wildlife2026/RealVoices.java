package com.formaworks.frontierhunts.wildlife2026;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [calls] Real field recordings for the wildlife mobs' ambient and warning voices (bears, bison, cougars, pronghorn,
 * ducks, ruffed grouse, plus occasional real wolf howls and coyote yips). Previously these animals used pitched vanilla
 * voices (polar bear, cow, goat, parrot, chicken). Sources: U.S. National Park Service (public domain) and iNaturalist
 * (CC0 / CC BY 4.0) - see AUDIO_CREDITS.txt. Hurt and death sounds stay vanilla.
 *
 * <p>Only {@link WildlifeMob#playAmbientSound()} calls into this class; it returns false for species/moods it does not
 * cover so the mob falls back to its old vanilla voice.</p>
 */
@EventBusSubscriber(modid = "frontierhunts", bus = Bus.MOD)
public final class RealVoices {
   private static final String[] IDS = {"bear_huff", "bear_growl", "bison_bellow", "cougar_growl", "pronghorn_snort", "duck_quack", "grouse_drum"};

   public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_HUFF = holder("bear_huff");
   public static final DeferredHolder<SoundEvent, SoundEvent> BEAR_GROWL = holder("bear_growl");
   public static final DeferredHolder<SoundEvent, SoundEvent> BISON_BELLOW = holder("bison_bellow");
   public static final DeferredHolder<SoundEvent, SoundEvent> COUGAR_GROWL = holder("cougar_growl");
   public static final DeferredHolder<SoundEvent, SoundEvent> PRONGHORN_SNORT = holder("pronghorn_snort");
   public static final DeferredHolder<SoundEvent, SoundEvent> DUCK_QUACK = holder("duck_quack");
   public static final DeferredHolder<SoundEvent, SoundEvent> GROUSE_DRUM = holder("grouse_drum");
   // existing events (registered by ecology / the base mod), reused for the canids
   private static final DeferredHolder<SoundEvent, SoundEvent> WOLF_HOWL = holder("wolf_howl");
   private static final DeferredHolder<SoundEvent, SoundEvent> COYOTE = holder("amb_coyote");

   private RealVoices() {
   }

   private static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("frontierhunts", path);
   }

   private static DeferredHolder<SoundEvent, SoundEvent> holder(String path) {
      return DeferredHolder.create(Registries.SOUND_EVENT, id(path));
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      for (String s : IDS) {
         e.register(Registries.SOUND_EVENT, id(s), () -> SoundEvent.createVariableRangeEvent(id(s)));
      }
   }

   /** A real voice for this ambient tick: the event and volume, or null to keep the vanilla voice. */
   public record Voice(SoundEvent sound, float volume, boolean silent) {
   }

   private static final Voice SILENT = new Voice(null, 0.0F, true);

   static Voice pick(WildlifeSpecies species, boolean warn, RandomSource r) {
      try {
         return switch (species) {
            case GRIZZLY, BLACK_BEAR, POLAR_BEAR -> warn ? v(BEAR_GROWL, 1.4F) : (r.nextInt(3) == 0 ? v(BEAR_HUFF, 1.0F) : SILENT);
            case BISON -> r.nextInt(2) == 0 ? v(BISON_BELLOW, 1.8F) : SILENT;
            case COUGAR, PANTHER -> warn ? v(COUGAR_GROWL, 1.2F) : null;
            case PRONGHORN -> warn || r.nextInt(3) == 0 ? v(PRONGHORN_SNORT, 1.2F) : SILENT;
            case DUCK -> v(DUCK_QUACK, 1.0F);
            case GROUSE -> warn ? null : (r.nextInt(4) == 0 ? v(GROUSE_DRUM, 1.4F) : SILENT);
            case WOLF -> !warn && r.nextInt(6) == 0 ? v(WOLF_HOWL, 3.0F) : null;
            case COYOTE -> !warn && r.nextInt(5) == 0 ? v(COYOTE, 2.5F) : null;
            default -> null;
         };
      } catch (RuntimeException ex) {
         return null; // registry not bound (should not happen in game) -> vanilla voice
      }
   }

   private static Voice v(DeferredHolder<SoundEvent, SoundEvent> h, float vol) {
      return h.isBound() ? new Voice(h.get(), vol, false) : null;
   }
}
