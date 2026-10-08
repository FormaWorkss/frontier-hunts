package com.formaworks.frontierhunts.progression;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public final class AssignmentProgress {
   private AssignmentProgress.State state = AssignmentProgress.State.NONE;
   private ResourceLocation id;
   private Assignment terms;
   private long accepted;
   private long deadline;
   private int count;
   private int experience;
   private int skills;
   private int completed;
   private UUID animal;
   private final Set<UUID> evidence = new LinkedHashSet<>();
   private final Map<ResourceLocation, Long> cooldowns = new HashMap<>();

   public AssignmentProgress.State state() {
      return this.state;
   }

   public ResourceLocation id() {
      return this.id;
   }

   public Assignment terms() {
      return this.terms;
   }

   public long accepted() {
      return this.accepted;
   }

   public long deadline() {
      return this.deadline;
   }

   public int count() {
      return this.count;
   }

   public int experience() {
      return this.experience;
   }

   public int skills() {
      return this.skills;
   }

   public int completed() {
      return this.completed;
   }

   public int points() {
      return this.experience / 100 - Integer.bitCount(this.skills);
   }

   /** [academy] Ranger XP from a passed academy course. */
   public void reward(int var1) {
      if (var1 > 0) {
         this.experience = Math.min(1000000, this.experience + var1);
      }
   }

   /**
    * [academy] Certification (the old training node) earned by passing its academy course. No point is spent; returns
    * true when it was new.
    */
   public boolean certify(int var1) {
      if (var1 >= 0 && var1 < 3 && !this.trained(var1)) {
         this.skills |= 1 << var1;
         return true;
      } else {
         return false;
      }
   }

   public boolean trained(int var1) {
      return var1 >= 0 && var1 < 3 && (this.skills & 1 << var1) != 0;
   }

   public long cooldown(ResourceLocation var1, long var2) {
      return Math.max(0L, this.cooldowns.getOrDefault(var1, 0L) - var2);
   }

   public boolean expire(long var1) {
      if (this.state == AssignmentProgress.State.ACTIVE && this.deadline > 0L && var1 >= this.deadline) {
         this.state = AssignmentProgress.State.FAILED;
         return true;
      } else {
         return false;
      }
   }

   public boolean accept(ResourceLocation var1, Assignment var2, long var3) {
      this.expire(var3);
      if (this.state == AssignmentProgress.State.ACTIVE || this.state == AssignmentProgress.State.READY || this.cooldown(var1, var3) > 0L) {
         return false;
      } else if (!this.cooldowns.containsKey(var1) && this.cooldowns.size() >= 64) {
         return false;
      } else {
         this.id = var1;
         this.terms = var2;
         this.accepted = var3;
         this.deadline = var2.duration() == 0 ? 0L : var3 + (long)var2.duration();
         this.count = 0;
         this.state = AssignmentProgress.State.ACTIVE;
         this.animal = null;
         this.evidence.clear();
         return true;
      }
   }

   public boolean cancel(long var1) {
      if (this.state != AssignmentProgress.State.NONE && this.state != AssignmentProgress.State.READY) {
         this.cooldowns.put(this.id, var1 + 200L);
         this.state = AssignmentProgress.State.NONE;
         this.id = null;
         this.terms = null;
         this.evidence.clear();
         this.animal = null;
         this.count = 0;
         return true;
      } else {
         return false;
      }
   }

   public boolean record(Assignment.Objective var1, UUID var2, UUID var3, long var4, int var6, boolean var7, long var8) {
      this.expire(var8);
      if (this.state == AssignmentProgress.State.ACTIVE
         && this.terms.objective() == var1
         && var4 >= this.accepted
         && var4 <= var8
         && !this.evidence.contains(var2)) {
         if (var1 != Assignment.Objective.HARVEST || var6 >= this.terms.minimumMass() && (!this.terms.chestOnly() || var7)) {
            if (var1 == Assignment.Objective.TRACK) {
               if (this.animal != null && !this.animal.equals(var3)) {
                  return false;
               }

               this.animal = var3;
            }

            this.evidence.add(var2);
            this.count++;
            if (this.count >= this.terms.target()) {
               this.state = AssignmentProgress.State.READY;
            }

            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public boolean finish(long var1) {
      if (this.state == AssignmentProgress.State.READY && this.completed != Integer.MAX_VALUE) {
         this.experience = Math.min(1000000, this.experience + this.terms.experience());
         this.completed++;
         this.cooldowns.put(this.id, var1 + (long)this.terms.cooldown());
         this.state = AssignmentProgress.State.NONE;
         this.id = null;
         this.terms = null;
         this.count = 0;
         this.animal = null;
         this.evidence.clear();
         return true;
      } else {
         return false;
      }
   }

   public boolean train(int var1) {
      if (var1 >= 0 && var1 < 3 && !this.trained(var1) && this.points() >= 1) {
         this.skills |= 1 << var1;
         return true;
      } else {
         return false;
      }
   }

   public boolean respec() {
      if (this.skills == 0) {
         return false;
      } else {
         this.skills = 0;
         return true;
      }
   }

   public CompoundTag save() {
      CompoundTag var1 = new CompoundTag();
      var1.putInt("state", this.state.ordinal());
      var1.putInt("xp", this.experience);
      var1.putInt("skills", this.skills);
      var1.putInt("completed", this.completed);
      if (this.id != null) {
         var1.putString("id", this.id.toString());
         var1.put("terms", (Tag)Assignment.CODEC.encodeStart(NbtOps.INSTANCE, this.terms).getOrThrow());
         var1.putLong("accepted", this.accepted);
         var1.putLong("deadline", this.deadline);
         var1.putInt("count", this.count);
      }

      if (this.animal != null) {
         var1.putUUID("animal", this.animal);
      }

      ListTag var2 = new ListTag();
      this.evidence.forEach(var1x -> {
         CompoundTag var2x = new CompoundTag();
         var2x.putUUID("id", var1x);
         var2.add(var2x);
      });
      var1.put("evidence", var2);
      ListTag var3 = new ListTag();
      this.cooldowns.entrySet().stream().sorted(Entry.comparingByKey()).forEach(var1x -> {
         CompoundTag var2x = new CompoundTag();
         var2x.putString("id", var1x.getKey().toString());
         var2x.putLong("until", var1x.getValue());
         var3.add(var2x);
      });
      var1.put("cooldowns", var3);
      return var1;
   }

   public static AssignmentProgress load(CompoundTag var0) {
      AssignmentProgress var1 = new AssignmentProgress();
      int var2 = var0.getInt("state");
      if (var2 >= 0 && var2 < AssignmentProgress.State.values().length) {
         var1.state = AssignmentProgress.State.values()[var2];
         var1.experience = var0.getInt("xp");
         var1.skills = var0.getInt("skills");
         var1.completed = var0.getInt("completed");
         if (var1.experience >= 0 && var1.experience <= 1000000 && var1.skills >= 0 && var1.skills <= 7 && var1.completed >= 0) { // [academy] certified nodes may exceed points
            if (var1.state != AssignmentProgress.State.NONE) {
               var1.id = ResourceLocation.parse(var0.getString("id"));
               var1.terms = (Assignment)Assignment.CODEC.parse(NbtOps.INSTANCE, var0.get("terms")).getOrThrow();
               var1.accepted = var0.getLong("accepted");
               var1.deadline = var0.getLong("deadline");
               var1.count = var0.getInt("count");
               if (var1.accepted < 0L
                  || var1.deadline < 0L
                  || var1.count < 0
                  || var1.count > var1.terms.target()
                  || var1.state == AssignmentProgress.State.READY && var1.count != var1.terms.target()
                  || var1.state != AssignmentProgress.State.READY && var1.count >= var1.terms.target()) {
                  throw new IllegalStateException("Invalid saved assignment terms");
               }
            }

            if (var0.hasUUID("animal")) {
               var1.animal = var0.getUUID("animal");
            }

            ListTag var3 = var0.getList("evidence", 10);
            ListTag var4 = var0.getList("cooldowns", 10);
            if (var3.size() <= 10 && var4.size() <= 64) {
               for (int var5 = 0; var5 < var3.size(); var5++) {
                  if (!var1.evidence.add(var3.getCompound(var5).getUUID("id"))) {
                     throw new IllegalStateException("Duplicate assignment proof");
                  }
               }

               for (int var10 = 0; var10 < var4.size(); var10++) {
                  CompoundTag var6 = var4.getCompound(var10);
                  ResourceLocation var7 = ResourceLocation.parse(var6.getString("id"));
                  long var8 = var6.getLong("until");
                  if (var8 < 0L || var1.cooldowns.put(var7, var8) != null) {
                     throw new IllegalStateException("Invalid assignment cooldown");
                  }
               }

               if (var1.state != AssignmentProgress.State.NONE && var1.evidence.size() != var1.count) {
                  throw new IllegalStateException("Assignment evidence/count mismatch");
               } else {
                  return var1;
               }
            } else {
               throw new IllegalStateException("Assignment record exceeds capacity");
            }
         } else {
            throw new IllegalStateException("Invalid training record");
         }
      } else {
         throw new IllegalStateException("Invalid assignment state");
      }
   }

   public static enum State {
      NONE,
      ACTIVE,
      READY,
      FAILED;
   }
}
