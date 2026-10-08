package com.formaworks.frontierhunts.ecology;

import net.minecraft.nbt.CompoundTag;

/**
 * [ecology] Who killed an animal, when, and how much of it has been eaten. Lives on the carcass (KillCarcass NBT, or
 * a downed Whitetail's persistent data) and in the per-dimension {@link KillSiteStore}.
 */
public final class KillRecord {
   /** a fresh, unknown record (never shared: records are mutable) */
   public static KillRecord empty() {
      return new KillRecord("", 0, "animal", "REMAINS", false, 0L);
   }

   /** {@link Predator} name, or "" when unknown */
   public final String predator;
   public final int pack;
   /** "whitetail doe", "old bison" ... */
   public final String prey;
   /** {@link Prey.Kind} name, used for the bones it leaves */
   public final String kind;
   public final boolean antlers;
   public final long killedAt;
   /** 0 untouched .. 1 picked clean */
   public float fed;
   public boolean salvaged;
   /** names of the scavengers that fed here since the kill (shown on inspection), comma separated */
   public String visitors = "";

   public KillRecord(String predator, int pack, String prey, String kind, boolean antlers, long killedAt) {
      this.predator = predator == null ? "" : predator;
      this.pack = Math.clamp(pack, 0, 16);
      this.prey = prey == null || prey.isEmpty() ? "animal" : prey.length() > 40 ? prey.substring(0, 40) : prey;
      this.kind = kind == null ? "REMAINS" : kind;
      this.antlers = antlers;
      this.killedAt = Math.max(0L, killedAt);
   }

   public Predator predatorKind() {
      for (Predator p : Predator.values()) {
         if (p.name().equals(this.predator)) {
            return p;
         }
      }
      return null;
   }

   public Prey.Kind preyKind() {
      for (Prey.Kind k : Prey.Kind.values()) {
         if (k.name().equals(this.kind)) {
            return k;
         }
      }
      return null;
   }

   /** the same record, killed {@code ticks} earlier (debug command) */
   public KillRecord aged(long ticks) {
      KillRecord r = new KillRecord(this.predator, this.pack, this.prey, this.kind, this.antlers, Math.max(0L, this.killedAt - Math.max(0L, ticks)));
      r.fed = this.fed;
      r.salvaged = this.salvaged;
      r.visitors = this.visitors;
      return r;
   }

   public void feed(float amount) {
      if (Float.isFinite(amount) && amount > 0.0F) {
         this.fed = Math.min(1.0F, this.fed + amount);
      }
   }

   public void visited(String who) {
      if (who == null || who.isEmpty() || this.visitors.contains(who) || this.visitors.length() > 60) {
         return;
      }
      this.visitors = this.visitors.isEmpty() ? who : this.visitors + ", " + who;
   }

   public CompoundTag save() {
      CompoundTag t = new CompoundTag();
      t.putString("predator", this.predator);
      t.putInt("pack", this.pack);
      t.putString("prey", this.prey);
      t.putString("kind", this.kind);
      t.putBoolean("antlers", this.antlers);
      t.putLong("at", this.killedAt);
      t.putFloat("fed", this.fed);
      t.putBoolean("salvaged", this.salvaged);
      t.putString("visitors", this.visitors);
      return t;
   }

   public static KillRecord load(CompoundTag t) {
      if (t == null || !t.contains("at")) {
         return empty();
      }
      KillRecord r = new KillRecord(t.getString("predator"), t.getInt("pack"), t.getString("prey"), t.getString("kind"), t.getBoolean("antlers"), t.getLong("at"));
      float f = t.getFloat("fed");
      r.fed = Float.isFinite(f) ? Math.clamp(f, 0.0F, 1.0F) : 0.0F;
      r.salvaged = t.getBoolean("salvaged");
      String v = t.getString("visitors");
      r.visitors = v.length() > 80 ? v.substring(0, 80) : v;
      return r;
   }
}
