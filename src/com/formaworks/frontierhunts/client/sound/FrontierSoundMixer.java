package com.formaworks.frontierhunts.client.sound;

import com.formaworks.frontierhunts.HuntConfig;
import com.formaworks.frontierhunts.sound.FrontierSoundCategory;
import com.formaworks.frontierhunts.sound.SoundMixConfig;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * [gui] The Frontier sound mixer: the gain of every sound the mod plays, by {@link FrontierSoundCategory}.
 *
 * <p>Applied inside Minecraft's SoundEngine (FrontierSoundEngineMixin) where it computes a sound's final volume - when the
 * sound starts, on every tick of a tickable/looping sound (weather beds, ATV engine, waterfalls, glider wind...) and when
 * {@link #refreshPlaying()} re-applies the volumes to everything already playing. So a slider change is heard at once,
 * also on loops that are not tickable, and no sound instance has to be wrapped or replaced (other code can still stop or
 * query its own instances). The hot path is a namespace check and one map lookup, no allocation.</p>
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = Bus.MOD)
public final class FrontierSoundMixer {
   private static final FrontierSoundCategory[] CATS = FrontierSoundCategory.values();
   private static final float[] GAIN = new float[CATS.length];
   private static boolean loaded;
   /** Sound event path -> group (null group cached as MASTER = "only the master slider applies"). */
   private static final Map<ResourceLocation, FrontierSoundCategory> CACHE = new ConcurrentHashMap<>();
   /** [polish] Sound event path -> designed level (FrontierSoundCategory.trim). */
   private static final Map<ResourceLocation, Float> TRIMS = new ConcurrentHashMap<>();
   /**
    * Vanilla events the mod plays as interface sounds (SimpleSoundInstance.forUI = MASTER source, which Minecraft only
    * scales by its master slider): field-gear tab, low-fuel chime, glider variometer.
    */
   private static final Set<ResourceLocation> UI_EVENTS = Set.of(
      ResourceLocation.withDefaultNamespace("block.note_block.chime"),
      ResourceLocation.withDefaultNamespace("block.note_block.bit"),
      ResourceLocation.withDefaultNamespace("item.armor.equip_leather")
   );
   private static SoundInstance preview;
   private static FrontierSoundCategory previewGroup;
   private static int previewTicks;

   static {
      java.util.Arrays.fill(GAIN, 1.0F);
   }

   private FrontierSoundMixer() {
   }

   @SubscribeEvent
   public static void configLoaded(ModConfigEvent.Loading event) {
      if (event.getConfig().getSpec() == HuntConfig.CLIENT) {
         reload();
      }
   }

   @SubscribeEvent
   public static void configReloaded(ModConfigEvent.Reloading event) {
      if (event.getConfig().getSpec() == HuntConfig.CLIENT) {
         reload();
         Minecraft.getInstance().execute(FrontierSoundMixer::refreshPlaying); // may arrive on the config watcher thread
      }
   }

   /** Reads the group volumes from the client config (cheap; call after a slider changed). */
   public static void reload() {
      for (FrontierSoundCategory c : CATS) {
         GAIN[c.ordinal()] = FrontierSoundCategory.gain(SoundMixConfig.percent(c)); // [polish] audio taper, 0% = silent
      }
      loaded = true;
   }

   /** The group of a sound, or null when it is not one of Frontier's (then the mixer leaves it alone). */
   public static FrontierSoundCategory category(SoundInstance sound) {
      ResourceLocation id = sound.getLocation();
      if (id == null) {
         return null;
      }
      if ("frontierhunts".equals(id.getNamespace())) {
         FrontierSoundCategory c = CACHE.get(id);
         if (c == null) {
            c = FrontierSoundCategory.classify(id.getPath());
            if (c == null) {
               c = FrontierSoundCategory.MASTER;
            }
            CACHE.put(id, c);
         }
         return c;
      }
      if (sound.getSource() == SoundSource.MASTER && UI_EVENTS.contains(id)) {
         return FrontierSoundCategory.UI;
      }
      return null;
   }

   /** Volume multiplier for a sound: 1 for anything that is not Frontier's, master x group otherwise. */
   public static float factor(SoundInstance sound) {
      if (sound == null) {
         return 1.0F;
      }
      // a slider preview is heard at the volume of the slider being tested (Frontier master alone for the master slider)
      FrontierSoundCategory c = sound == preview ? previewGroup : category(sound);
      if (c == null) {
         return 1.0F;
      }
      if (!loaded) {
         reload();
      }
      float master = GAIN[FrontierSoundCategory.MASTER.ordinal()];
      float g = c == FrontierSoundCategory.MASTER ? master : master * GAIN[c.ordinal()];
      return g <= 0.0F ? 0.0F : g * trim(sound); // [polish] designed default level of the event
   }

   /** [polish] Designed level of a Frontier sound event (1 for anything else). */
   private static float trim(SoundInstance sound) {
      ResourceLocation id = sound.getLocation();
      if (id == null || !"frontierhunts".equals(id.getNamespace())) {
         return 1.0F;
      }
      Float t = TRIMS.get(id);
      if (t == null) {
         t = FrontierSoundCategory.trim(id.getPath());
         TRIMS.put(id, t);
      }
      return t;
   }

   /** Re-applies the volumes to every sound that is playing now (loops keep running, also at 0%). */
   public static void refreshPlaying() {
      try {
         SoundManager sm = Minecraft.getInstance().getSoundManager();
         if (sm instanceof FrontierSoundVolumes v) {
            v.frontierhunts$refreshVolumes();
         }
      } catch (RuntimeException e) {
      }
   }

   /** Plays the group's short preview sound (stopping the previous preview). */
   public static void preview(FrontierSoundCategory c) {
      try {
         Minecraft mc = Minecraft.getInstance();
         stopPreview();
         ResourceLocation id = c.preview.indexOf(':') >= 0
            ? ResourceLocation.parse(c.preview)
            : ResourceLocation.fromNamespaceAndPath("frontierhunts", c.preview);
         SoundInstance s = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(id), 1.0F, 0.9F);
         preview = s;
         previewGroup = c;
         previewTicks = 70;
         mc.getSoundManager().play(s);
      } catch (RuntimeException e) {
      }
   }

   /** Called every client tick while the settings screen is open: previews stop after 3.5 seconds at most. */
   public static void tickPreview() {
      if (preview != null && --previewTicks <= 0) {
         stopPreview();
      }
   }

   public static void stopPreview() {
      if (preview != null) {
         try {
            Minecraft.getInstance().getSoundManager().stop(preview);
         } catch (RuntimeException e) {
         }
         preview = null;
      }
   }
}
