package com.formaworks.frontierhunts.hunting.herd;

import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;

/**
 * [herds] Natural spawns come as real groups. The first animal of a spawn pack rolls what the pack is (by species and
 * time of year: {@link HerdTuning#spawnMix}) and every further animal of the pack takes the next place in it: a lead
 * doe, her yearlings and grown daughters; a few bucks of mixed ages; a lone buck... A pack bigger than its group starts
 * a new group. Only the animal's own data is written here (this runs on world-generation threads too); the group
 * itself is registered when the first of its members is loaded on the server thread ({@link HerdService}).
 */
public final class HerdSpawn {
   private HerdSpawn() {
   }

   /** One place in a planned group. */
   record Slot(boolean male, int minAge, int maxAge, HerdRole role) {
   }

   /** The spawn pack's plan, passed from animal to animal through vanilla's spawn group data. */
   public static final class Pack implements SpawnGroupData {
      final GameSpecies species;
      final HerdKind kind;
      final List<Slot> slots;
      final UUID groupId;
      int filled;
      UUID mother;

      Pack(GameSpecies species, HerdKind kind, List<Slot> slots, UUID groupId) {
         this.species = species;
         this.kind = kind;
         this.slots = slots;
         this.groupId = groupId;
      }

      boolean full() {
         return this.filled >= this.slots.size();
      }

      public HerdKind kind() {
         return this.kind;
      }
   }

   /**
    * finalizeSpawn for natural and chunk-generation spawns. Returns the pack data to hand to the next animal of the pack,
    * or null when the old behaviour should run (social groups off, or not a natural spawn).
    */
   public static SpawnGroupData finalize(Whitetail deer, MobSpawnType type, SpawnGroupData previous) {
      if (!HerdService.enabled() || type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
         return null;
      }

      RandomSource random = deer.getRandom();
      Pack pack = previous instanceof Pack p && p.species == deer.species() && !p.full() ? p : plan(deer.species(), random, HerdService.yearPos, deer.getUUID());
      Slot slot = pack.slots.get(pack.filled++);
      deer.setTraits(traits(deer.species(), slot, random));
      CompoundTag data = deer.getPersistentData();
      data.putUUID(HerdService.KEY, pack.groupId);
      data.putByte(HerdService.KIND_KEY, (byte)pack.kind.ordinal());
      data.putByte(HerdService.ROLE_KEY, (byte)slot.role().ordinal());
      if (slot.role() == HerdRole.YOUNG && pack.mother != null) {
         data.putUUID(HerdService.MOTHER_KEY, pack.mother);
      } else if (pack.mother == null && !slot.male() && slot.role() != HerdRole.YOUNG) {
         pack.mother = deer.getUUID();
      }

      return pack;
   }

   /** Traits for a planned place: the usual random animal, aged into the place's range. */
   static DeerTraits traits(GameSpecies sp, Slot slot, RandomSource random) {
      DeerTraits t = DeerTraits.random(sp, random, slot.male());
      int age = slot.minAge() + random.nextInt(Math.max(1, slot.maxAge() - slot.minAge() + 1));
      if (sp.meshAntlers() && slot.male()) {
         age = Math.max(age, 26); // elk / moose bulls carry antlers from their second fall
      }

      int abnormal = age < 36 && t.abnormal() > 2 ? 0 : t.abnormal();
      return new DeerTraits(sp, slot.male(), age, t.frame(), t.condition(), t.rackGenes(), t.seed(), abnormal, t.coat());
   }

   /** Rolls what a new pack is. */
   static Pack plan(GameSpecies sp, RandomSource random, double pos, UUID first) {
      HerdTuning.Mix mix = HerdTuning.spawnMix(sp, pos);
      HerdTuning.Social s = HerdTuning.of(sp);
      float r = random.nextFloat() * (mix.family() + mix.bachelor() + mix.soloMale() + mix.soloFemale());
      List<Slot> slots = new ArrayList<>();
      HerdKind kind;
      if ((r -= mix.family()) < 0.0F) {
         kind = HerdKind.FAMILY;
         family(sp, s, random, pos, slots);
      } else if ((r -= mix.bachelor()) < 0.0F) {
         kind = HerdKind.BACHELOR;
         int n = s.bachelorMax();
         for (int i = 0; i < n; i++) {
            // a bachelor group: one older buck and younger ones (yearlings that have left their mothers)
            slots.add(i == 0 ? new Slot(true, 36, 84, HerdRole.LEADER) : new Slot(true, sp == GameSpecies.WHITETAIL ? 18 : 26, 60, HerdRole.ADULT));
         }
      } else if ((r -= mix.soloMale()) < 0.0F) {
         kind = HerdKind.SOLO;
         slots.add(new Slot(true, 30, 120, HerdRole.LEADER));
      } else {
         kind = HerdKind.SOLO;
         slots.add(new Slot(false, 30, 120, HerdRole.LEADER));
      }

      // the group id is derived from its first animal: no shared random source is touched on world-generation threads
      UUID id = new UUID(first.getMostSignificantBits() ^ 0x5DEECE66DL * 0x9E3779B97F4A7C15L, ~first.getLeastSignificantBits());
      return new Pack(sp, kind, slots, id);
   }

   private static void family(GameSpecies sp, HerdTuning.Social s, RandomSource random, double pos, List<Slot> slots) {
      switch (sp) {
         case MOOSE -> {
            slots.add(new Slot(false, 36, 120, HerdRole.LEADER));
            slots.add(new Slot(false, 12, 16, HerdRole.YOUNG));
         }
         case ELK -> {
            boolean harem = s.harem().contains(pos);
            slots.add(new Slot(false, 60, 130, HerdRole.LEADER));
            slots.add(new Slot(false, 12, 23, HerdRole.YOUNG));
            slots.add(new Slot(false, 30, 100, HerdRole.ADULT));
            if (harem) {
               slots.add(new Slot(true, 48, 110, HerdRole.HERD_BULL));
            }

            for (int i = slots.size(); i < 10; i++) {
               slots.add(i % 2 == 0 ? new Slot(false, 12, 23, HerdRole.YOUNG) : new Slot(false, 26, 110, HerdRole.ADULT));
            }
         }
         default -> {
            // a doe, her yearlings and her grown daughters (with their own young)
            boolean dispersal = s.youngDispersal().contains(pos);
            float buckYoung = dispersal ? 0.2F : 0.45F;
            slots.add(new Slot(false, 36, 108, HerdRole.LEADER));
            slots.add(new Slot(random.nextFloat() < buckYoung, 12, 23, HerdRole.YOUNG));
            slots.add(new Slot(false, 24, 60, HerdRole.ADULT));
            slots.add(new Slot(random.nextFloat() < buckYoung, 12, 23, HerdRole.YOUNG));
            slots.add(new Slot(random.nextFloat() < buckYoung, 12, 23, HerdRole.YOUNG));
            slots.add(new Slot(false, 24, 48, HerdRole.ADULT));
         }
      }
   }

   /** QA: what a pack would be (kind and the sexes/ages of its places), without spawning anything. */
   public static String[] describePlan(GameSpecies sp, RandomSource random, double pos) {
      Pack p = plan(sp, random, pos, new UUID(random.nextLong(), random.nextLong()));
      StringBuilder b = new StringBuilder();
      for (Slot sl : p.slots) {
         b.append(sl.male() ? 'M' : 'F').append(sl.role().name().charAt(0));
      }

      return new String[]{p.kind.name(), b.toString()};
   }
}
