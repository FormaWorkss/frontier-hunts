package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.environment.CascadeMist;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.hunting.HuntSounds;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance.Attenuation;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.level.ChunkEvent.Unload;

@EventBusSubscriber(
   modid = "frontierhunts",
   value = {Dist.CLIENT}
)
public final class CascadeAmbience {
   private static final double RANGE = 170.0;
   private static final Map<Long, CascadeAmbience.Fall> FALLS = new ConcurrentHashMap<>();
   private static final Map<Long, CascadeAmbience.Scan> PENDING = new ConcurrentHashMap<>();
   private static ClientLevel currentLevel;
   private static CascadeAmbience.Voice far;
   private static CascadeAmbience.Voice body;
   private static CascadeAmbience.Voice foot;
   private static int sweep;

   private CascadeAmbience() {
   }

   @SubscribeEvent
   public static void chunkLoad(Load event) {
      if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel().isClientSide) {
         CascadeAmbience.Scan scan = new CascadeAmbience.Scan(chunk);
         if (scan.sections.length > 0) {
            PENDING.put(chunk.getPos().toLong(), scan);
         } else {
            PENDING.remove(chunk.getPos().toLong());
            FALLS.remove(chunk.getPos().toLong());
         }

         return;
      }
   }

   @SubscribeEvent
   public static void chunkUnload(Unload event) {
      if (event.getChunk() instanceof LevelChunk chunk && chunk.getLevel().isClientSide) {
         FALLS.remove(chunk.getPos().toLong());
         PENDING.remove(chunk.getPos().toLong());
      }
   }

   @SubscribeEvent
   public static void tick(Post event) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != currentLevel) {
         FALLS.clear();
         PENDING.values().removeIf(s -> s.chunk.getLevel() != mc.level);

         for (CascadeAmbience.Voice voice : new CascadeAmbience.Voice[]{far, body, foot}) {
            if (voice != null) {
               mc.getSoundManager().stop(voice);
            }
         }

         foot = null;
         body = null;
         far = null;
         currentLevel = mc.level;
      }

      if (mc.level != null && mc.player != null) {
         if (!mc.isPaused()) {
            for (int budget = 0; budget < 2 && !PENDING.isEmpty(); budget++) {
               Iterator<Entry<Long, CascadeAmbience.Scan>> it = PENDING.entrySet().iterator();
               if (!it.hasNext()) {
                  break;
               }

               Entry<Long, CascadeAmbience.Scan> entry = it.next();
               CascadeAmbience.Scan scan = entry.getValue();
               if (scan.chunk.getLevel() != mc.level) {
                  PENDING.remove(entry.getKey(), scan);
               } else if (scan.step()) {
                  scan.finish();
                  PENDING.remove(entry.getKey(), scan);
               }
            }

            if (++sweep % 4 == 0) {
               CascadeAmbience.Fall best = null;
               double bestSq = 28900.0;
               Vec3 eye = mc.player.position();

               for (CascadeAmbience.Fall fall : FALLS.values()) {
                  double d = fall.at().distToCenterSqr(eye.x, eye.y, eye.z);
                  if (d < bestSq) {
                     bestSq = d;
                     best = fall;
                  }
               }

               if (best == null) {
                  silence();
               } else {
                  double d = Math.sqrt(bestSq);
                  float scale = 0.52F + (float)best.size() * 0.2F;
                  far = aim(far, (SoundEvent)HuntSounds.CASCADE_FAR.get(), 0.96F, best.at(), ramp(d, 170.0, 120.0) * ramp(-d, -16.0, -34.0) * scale * 0.85F);
                  body = aim(body, (SoundEvent)HuntSounds.CASCADE.get(), 1.0F, best.at(), ramp(d, 74.0, 44.0) * ramp(-d, -2.0, -7.0) * scale);
                  foot = aim(foot, (SoundEvent)HuntSounds.CASCADE_FOOT.get(), 1.0F, best.at(), ramp(d, 17.0, 9.0) * scale * 0.9F);
               }
            }
         }
      } else {
         FALLS.clear();
         PENDING.clear();
         silence();
      }
   }

   private static float ramp(double d, double zero, double full) {
      return (float)Mth.clamp((zero - d) / (zero - full), 0.0, 1.0);
   }

   private static CascadeAmbience.Voice aim(CascadeAmbience.Voice voice, SoundEvent sound, float pitch, BlockPos at, float target) {
      if (voice != null && voice.isStopped()) {
         voice = null;
      }

      if (voice == null) {
         if (target <= 0.002F) {
            return null;
         } else {
            voice = new CascadeAmbience.Voice(sound, pitch, at, target);
            Minecraft.getInstance().getSoundManager().play(voice);
            return voice;
         }
      } else {
         voice.aim(at, target);
         return voice;
      }
   }

   private static void silence() {
      if (far != null) {
         far.aim(null, 0.0F);
      }

      if (body != null) {
         body.aim(null, 0.0F);
      }

      if (foot != null) {
         foot.aim(null, 0.0F);
      }
   }

   private static record Fall(BlockPos at, int size) {
   }

   private static final class Scan {
      final LevelChunk chunk;
      final int[] sections;
      int sectionIndex;
      long cx;
      long cy;
      long cz;
      long n;
      int size;

      Scan(LevelChunk chunk) {
         this.chunk = chunk;
         IntArrayList matching = new IntArrayList();
         LevelChunkSection[] all = chunk.getSections();
         CascadeMist mist = (CascadeMist)ExpeditionContent.CASCADE_MIST.get();

         for (int i = 0; i < all.length; i++) {
            if (all[i] != null && !all[i].hasOnlyAir() && all[i].maybeHas(s -> s.is(mist) && (Integer)s.getValue(CascadeMist.WHERE) == 2)) {
               matching.add(i);
            }
         }

         this.sections = matching.toIntArray();
      }

      boolean step() {
         CascadeMist mist = (CascadeMist)ExpeditionContent.CASCADE_MIST.get();
         if (this.sectionIndex >= this.sections.length) {
            return true;
         } else {
            int i = this.sections[this.sectionIndex++];
            LevelChunkSection section = this.chunk.getSections()[i];
            if (section != null && !section.hasOnlyAir()) {
               if (!section.maybeHas(s -> s.is(mist) && (Integer)s.getValue(CascadeMist.WHERE) == 2)) {
                  return this.sectionIndex >= this.sections.length;
               } else {
                  int baseY = this.chunk.getSectionYFromSectionIndex(i) << 4;

                  for (int y = 0; y < 16; y++) {
                     for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                           BlockState state = section.getBlockState(x, y, z);
                           if (state.is(mist) && (Integer)state.getValue(CascadeMist.WHERE) == 2) {
                              this.cx = this.cx + (long)(this.chunk.getPos().getMinBlockX() + x);
                              this.cy += (long)(baseY + y);
                              this.cz = this.cz + (long)(this.chunk.getPos().getMinBlockZ() + z);
                              this.size = Math.max(this.size, (Integer)state.getValue(CascadeMist.SIZE));
                              this.n++;
                           }
                        }
                     }
                  }

                  return this.sectionIndex >= this.sections.length;
               }
            } else {
               return this.sectionIndex >= this.sections.length;
            }
         }
      }

      void finish() {
         long key = this.chunk.getPos().toLong();
         if (this.n > 0L) {
            CascadeAmbience.FALLS
               .put(key, new CascadeAmbience.Fall(new BlockPos((int)(this.cx / this.n), (int)(this.cy / this.n), (int)(this.cz / this.n)), this.size));
         } else {
            CascadeAmbience.FALLS.remove(key);
         }
      }
   }

   private static final class Voice extends AbstractTickableSoundInstance {
      private BlockPos at;
      private float target;

      private Voice(SoundEvent sound, float pitch, BlockPos at, float target) {
         super(sound, SoundSource.AMBIENT, RandomSource.create());
         this.looping = true;
         this.delay = 0;
         this.attenuation = Attenuation.NONE;
         this.pitch = pitch;
         this.at = at;
         this.target = target;
         this.volume = Math.max(0.01F, target * 0.25F);
         this.place();
      }

      void aim(BlockPos to, float wanted) {
         if (to != null) {
            this.at = to;
         }

         this.target = wanted;
      }

      private void place() {
         if (this.at != null) {
            this.x = (double)this.at.getX() + 0.5;
            this.y = (double)this.at.getY() + 0.5;
            this.z = (double)this.at.getZ() + 0.5;
         }
      }

      public void tick() {
         this.place();
         this.volume = this.volume + Mth.clamp(this.target - this.volume, -0.018F, 0.018F);
         if (this.target <= 0.0F && this.volume <= 0.004F) {
            this.stop();
         }
      }
   }
}
