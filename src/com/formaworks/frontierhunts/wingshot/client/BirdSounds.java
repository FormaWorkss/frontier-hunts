package com.formaworks.frontierhunts.wingshot.client;

import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import com.formaworks.frontierhunts.wingshot.Flight;
import com.formaworks.frontierhunts.wingshot.WingshotContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * [wingshot] The sound of wings: a looping wingbeat that follows each of the (at most four) nearest flying birds -
 * the whistling beat of a mallard, the drum of a grouse's short wings - loud while flapping, a soft rush of air on set
 * wings, timed up for the take-off. Started and stopped from the client tick; never more than four at once.
 */
final class BirdSounds {
   private static final int MAX = 4;
   private static final Map<Integer, Loop> PLAYING = new HashMap<>();

   private BirdSounds() {
   }

   static void tick(List<WildlifeMob> flying) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null) {
         return;
      }
      flying.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
      List<Integer> want = new ArrayList<>();
      for (WildlifeMob m : flying) {
         if (want.size() >= MAX || m.distanceToSqr(mc.player) > 34.0 * 34.0) {
            break;
         }
         want.add(m.getId());
         if (!PLAYING.containsKey(m.getId())) {
            Loop l = new Loop(m);
            PLAYING.put(m.getId(), l);
            mc.getSoundManager().play(l);
         }
      }
      PLAYING.entrySet().removeIf(en -> {
         if (!want.contains(en.getKey()) || en.getValue().isStopped()) {
            en.getValue().end();
            return true;
         }
         return false;
      });
   }

   static void clear() {
      PLAYING.values().forEach(Loop::end);
      PLAYING.clear();
   }

   static final class Loop extends AbstractTickableSoundInstance {
      private final WildlifeMob bird;
      private float level;

      Loop(WildlifeMob bird) {
         super(bird.species == WildlifeSpecies.GROUSE ? WingshotContent.GROUSE_WINGS.get() : WingshotContent.DUCK_WINGS.get(), SoundSource.NEUTRAL,
            RandomSource.create(bird.getId()));
         this.bird = bird;
         this.looping = true;
         this.delay = 0;
         this.volume = 0.01F;
         this.x = bird.getX();
         this.y = bird.getY();
         this.z = bird.getZ();
         this.attenuation = SoundInstance.Attenuation.LINEAR;
      }

      void end() {
         this.stop();
      }

      @Override
      public boolean canStartSilent() {
         return true;
      }

      @Override
      public void tick() {
         if (this.bird.isRemoved() || !this.bird.isAlive()) {
            this.stop();
            return;
         }
         this.x = this.bird.getX();
         this.y = this.bird.getY();
         this.z = this.bird.getZ();
         byte p = this.bird.flightPhase();
         float target;
         float pitch = 1.0F;
         switch (p) {
            case Flight.TAKEOFF -> {
               target = 1.0F;
               pitch = this.bird.species == WildlifeSpecies.GROUSE ? 1.1F : 1.2F;
            }
            case Flight.FLAP -> target = 0.75F;
            case Flight.FLUSH -> {
               target = 1.0F;
               pitch = 1.1F;
            }
            case Flight.LAND -> {
               target = 0.6F;
               pitch = 0.85F;
            }
            case Flight.GLIDE, Flight.SET -> {
               target = 0.22F;
               pitch = 0.75F;
            }
            default -> target = 0.0F;
         }
         this.level += (target - this.level) * 0.25F;
         this.volume = Math.max(0.001F, this.level);
         this.pitch = pitch;
         if (target == 0.0F && this.level < 0.02F) {
            this.stop();
         }
      }
   }
}
