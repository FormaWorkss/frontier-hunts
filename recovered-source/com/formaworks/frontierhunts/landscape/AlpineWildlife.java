package com.formaworks.frontierhunts.landscape;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(
   modid = "frontierhunts"
)
public final class AlpineWildlife {
   private static final String PENDING = "frontier_natural_population_v8";
   private static final Map<Level, List<AlpineWildlife.Call>> CALLS = new WeakHashMap<>();

   @SubscribeEvent
   public static void spawn(FinalizeSpawnEvent var0) {
      if (var0.getEntity() instanceof Whitetail var1) {
         if (var0.getSpawnType() == MobSpawnType.NATURAL || var0.getSpawnType() == MobSpawnType.CHUNK_GENERATION) {
            GameSpecies var9 = var1.traits().species();
            double var3 = var9 == GameSpecies.WHITETAIL ? 0.48 : (var9 == GameSpecies.ELK ? 0.16 : 0.22);
            List var5 = var0.getLevel()
               .getLevel()
               .getEntitiesOfClass(Whitetail.class, var1.getBoundingBox().inflate(96.0), var0x -> var0x.isAlive() && !var0x.downed());
            long var6 = var5.stream().filter(var1x -> var1x.traits().species() == var9).count();
            int var8 = var9 == GameSpecies.WHITETAIL ? 6 : (var9 == GameSpecies.ELK ? 3 : 2);
            if (!(var1.getRandom().nextDouble() > var3) && var5.size() < 9 && var6 < (long)var8) {
               var1.getPersistentData().putBoolean("frontier_natural_population_v8", true);
            } else {
               var0.setSpawnCancelled(true);
            }
         }
      }
   }

   @SubscribeEvent
   public static void joined(EntityJoinLevelEvent var0) {
      if (!var0.getLevel().isClientSide() && var0.getEntity() instanceof Whitetail var1) {
         if (var1.getPersistentData().getBoolean("frontier_natural_population_v8")) {
            var1.getPersistentData().remove("frontier_natural_population_v8");
            var1.setTraits(naturalTraits(var1.traits().species(), var1.getRandom()));
         }
      }
   }

   public static DeerTraits naturalTraits(GameSpecies var0, RandomSource var1) {
      boolean var2 = var1.nextFloat() < 0.25F;
      DeerTraits var3 = DeerTraits.random(var0, var1, var2);
      if (var2 && var1.nextFloat() > 0.12F) {
         int var4 = var0 == GameSpecies.WHITETAIL ? 18 + var1.nextInt(24) : 26 + var1.nextInt(16);
         var3 = new DeerTraits(
            var0,
            true,
            var4,
            Math.min(var3.frame(), 65),
            var3.condition(),
            Math.min(var3.rackGenes(), 75),
            var3.seed(),
            Math.min(var3.abnormal(), 2),
            var3.coat()
         );
      }

      return var3;
   }

   @SubscribeEvent
   public static void sound(AtPosition var0) {
      if (!var0.getLevel().isClientSide() && var0.getSound() != null) {
         ResourceLocation var1 = ((SoundEvent)var0.getSound().value()).getLocation();
         if (var1.getNamespace().equals("frontierhunts") && (var1.getPath().equals("elk_bugle") || var1.getPath().equals("elk_mew"))) {
            if (var0.getSource() == SoundSource.NEUTRAL) {
               Level var2 = var0.getLevel();
               long var3 = var2.getGameTime();
               List var5 = CALLS.computeIfAbsent(var2, var0x -> new ArrayList<>());
               var5.removeIf(var2x -> var2x.until <= var3);
               if (var5.stream().anyMatch(var1x -> var1x.position.distanceToSqr(var0.getPosition()) < 16384.0)) {
                  var0.setCanceled(true);
               } else {
                  if (var5.size() >= 256) {
                     var5.removeFirst();
                  }

                  var5.add(new AlpineWildlife.Call(var0.getPosition(), var3 + 1200L + (long)var2.random.nextInt(2401)));
               }
            }
         }
      }
   }

   private static record Call(Vec3 position, long until) {
   }
}
