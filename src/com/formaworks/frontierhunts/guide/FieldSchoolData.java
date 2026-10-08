package com.formaworks.frontierhunts.guide;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** [guide] Per-hunter Field School progress, saved with the overworld ({@code data/frontierhunts_fieldschool.dat}). */
public final class FieldSchoolData extends SavedData {
   private static final int SCHEMA = 1;
   private final Map<UUID, Hunter> hunters = new HashMap<>();

   public static final class Hunter {
      /** completed lessons (bit per {@link Lesson}) */
      public int done;
      /** field notes already shown (bit per {@link Tip}) */
      public int tips;
      public boolean skipped;
      public boolean welcomed;
      public boolean graduated;
      /** the starter kit has been handled for this hunter (given, or skipped as a veteran) */
      public boolean kit;
      /** lesson 6: blood sign read so far */
      public int blood;
      /** lesson 7: carcass dressed (the trophy still has to be picked up) */
      public boolean dressed;
      /** last season seen (-1 = not yet sampled), for the season-change note */
      public int season = -1;

      CompoundTag save() {
         CompoundTag t = new CompoundTag();
         t.putInt("done", this.done);
         t.putInt("tips", this.tips);
         t.putBoolean("skipped", this.skipped);
         t.putBoolean("welcomed", this.welcomed);
         t.putBoolean("graduated", this.graduated);
         t.putBoolean("kit", this.kit);
         t.putInt("blood", this.blood);
         t.putBoolean("dressed", this.dressed);
         t.putInt("season", this.season);
         return t;
      }

      static Hunter load(CompoundTag t) {
         Hunter h = new Hunter();
         h.done = t.getInt("done") & Lesson.ALL_MASK;
         h.tips = t.getInt("tips");
         h.skipped = t.getBoolean("skipped");
         h.welcomed = t.getBoolean("welcomed");
         h.graduated = t.getBoolean("graduated");
         h.kit = t.getBoolean("kit");
         h.blood = Math.clamp(t.getInt("blood"), 0, 64);
         h.dressed = t.getBoolean("dressed");
         h.season = t.contains("season", Tag.TAG_INT) ? t.getInt("season") : -1;
         return h;
      }

      /** Progress toward the lesson's goal, for the HUD card. */
      public int progress(Lesson l) {
         if (l.done(this.done)) {
            return l.goal;
         }
         return switch (l) {
            case TRAIL -> Math.min(this.blood, l.goal - 1);
            case HARVEST -> this.dressed ? 1 : 0;
            default -> 0;
         };
      }

      /** Back to a fresh hunter (the starter kit is never issued twice). */
      public void reset() {
         this.done = 0;
         this.tips = 0;
         this.skipped = false;
         this.welcomed = false;
         this.graduated = false;
         this.blood = 0;
         this.dressed = false;
         this.season = -1;
      }
   }

   public static FieldSchoolData get(MinecraftServer server) {
      return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(FieldSchoolData::new, FieldSchoolData::load, null), "frontierhunts_fieldschool");
   }

   /** The record for this hunter, or null for a hunter who has never joined with the Field School installed. */
   public Hunter find(UUID id) {
      return this.hunters.get(id);
   }

   public Hunter create(UUID id) {
      return this.hunters.computeIfAbsent(id, k -> {
         this.setDirty();
         return new Hunter();
      });
   }

   @Override
   public CompoundTag save(CompoundTag tag, Provider registries) {
      tag.putInt("schema", SCHEMA);
      CompoundTag all = new CompoundTag();
      this.hunters.forEach((id, h) -> all.put(id.toString(), h.save()));
      tag.put("hunters", all);
      return tag;
   }

   public static FieldSchoolData load(CompoundTag tag, Provider registries) {
      FieldSchoolData d = new FieldSchoolData();
      CompoundTag all = tag.getCompound("hunters");
      for (String key : all.getAllKeys()) {
         try {
            d.hunters.put(UUID.fromString(key), Hunter.load(all.getCompound(key)));
         } catch (IllegalArgumentException ignored) {
            // a damaged key: skip that hunter only
         }
      }
      return d;
   }
}
