package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.hunting.AntlerDesign;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.slf4j.Logger;

/**
 * Entry points called from shared classes (marked {@code // [camps]} there). Kept tiny and exception-safe so a problem in
 * the record book can never break a harvest or a deer's call response.
 *
 * <p>Reconciling with the {@code trophies} workstream: if it adds a central harvest event, replace the Whitetail hook
 * line with a listener that calls {@link #deerHarvest}; the score is read from the trophy item's CUSTOM_DATA so any
 * better score it writes there (keys below) flows through automatically.
 */
public final class CampHooks {
   private static final Logger LOG = LogUtils.getLogger();
   /** persistent-data key stamped on a deer when it answers a call */
   public static final String CALLED_AT = "frontierhunts_called_at";
   /** a call counts for "called in" if the shot comes within 5 minutes of the deer answering */
   static final long CALL_WINDOW = 6000L;
   /** score keys a richer scoring system may write into the trophy tag, in order of preference */
   static final String[] SCORE_KEYS = new String[]{"bc_net", "net_score", "score_net", "bc_score", "bc_gross", "gross_score", "score_inches"};

   private CampHooks() {
   }

   /** Whitetail.approachCall succeeded: remember when this animal answered a call. */
   public static void called(Whitetail deer) {
      try {
         deer.getPersistentData().putLong(CALLED_AT, deer.level().getGameTime());
      } catch (Throwable ignored) {
      }
   }

   /**
    * Whitetail.harvest(): the trophy item has been created. {@code shooter} may be null (then the dresser gets the credit
    * only if they are a player); {@code shotAt} is the game time of the recorded shot.
    */
   public static void deerHarvest(ServerPlayer dresser, Whitetail deer, ItemStack trophy, UUID shooter, double shotMetres, long shotAt) {
      try {
         MinecraftServer server = dresser.server;
         CompoundTag tag = trophy.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
         UUID hunter = shooter != null ? shooter : dresser.getUUID();
         ServerPlayer online = server.getPlayerList().getPlayer(hunter);
         HarvestRecord r = new HarvestRecord();
         r.hunter = hunter;
         r.name = RecordService.nameOf(server, hunter);
         GameSpecies gs = deer.species();
         Quarry q = Quarry.find(gs.id);
         r.species = q == null ? gs.id : q.id;
         DeerTraits traits = deer.traits();
         r.male = traits.buck();
         r.weightKg = tag.contains("mass_kg") ? tag.getInt("mass_kg") : deer.massKg();
         r.shotM = Double.isFinite(shotMetres) ? Math.clamp(shotMetres, 0.0, 1000.0) : 0.0;
         r.score = score(tag, traits);
         if (traits.buck()) {
            r.pointsL = traits.points(false);
            r.pointsR = traits.points(true);
            if (tag.contains("deer_traits", 10)) {
               r.traits = tag.getCompound("deer_traits").copy();
            } else {
               r.traits = traits.save();
            }
         }
         r.legendary = tag.getBoolean("legendary") || tag.contains("legend_name") || tag.contains("legendary_name");
         r.legendName = tag.contains("legend_name") ? tag.getString("legend_name") : tag.getString("legendary_name");
         long calledAt = deer.getPersistentData().getLong(CALLED_AT);
         r.called = calledAt > 0L && shotAt >= calledAt && shotAt - calledAt <= CALL_WINDOW;
         r.animal = deer.getUUID();
         place(r, deer);
         RecordService.ingest(server, online, r);
      } catch (Throwable t) {
         LOG.error("Frontier Hunts camps: harvest hook failed (harvest itself is unaffected)", t);
      }
   }

   static double score(CompoundTag tag, DeerTraits traits) {
      for (String k : SCORE_KEYS) {
         if (tag.contains(k)) {
            double v = tag.getDouble(k);
            if (Double.isFinite(v) && v > 0.0) {
               return Math.min(v, 1000.0);
            }
         }
      }
      int whole = tag.getInt("trophy_score");
      if (whole <= 0 || !traits.buck()) {
         return 0.0;
      }
      if (traits.species() == GameSpecies.WHITETAIL) {
         // the trophy stores the rounded gross; recover the eighths from the same antler design when they agree
         try {
            float gross = AntlerDesign.of(traits).grossScoreInches();
            if (Math.round(gross) == whole) {
               return Math.round(gross * 8.0) / 8.0;
            }
         } catch (Throwable ignored) {
         }
      }
      return whole;
   }

   /** Wildlife (bears, boar, predators, birds...) killed by a player. */
   public static void wildlifeKilled(WildlifeMob mob, ServerPlayer killer) {
      try {
         Quarry q = Quarry.find(mob.species.id);
         if (q == null || q.rack()) {
            return;
         }
         HarvestRecord r = new HarvestRecord();
         r.hunter = killer.getUUID();
         r.name = killer.getGameProfile().getName();
         r.species = q.id;
         UUID id = mob.getUUID();
         r.male = (id.getLeastSignificantBits() & 1L) == 0L;
         r.weightKg = q.weightFor(id.getMostSignificantBits() ^ id.getLeastSignificantBits()) * (r.male ? 1.0 : 0.82);
         r.weightKg = q.kind == Quarry.Kind.BIRD ? Math.round(r.weightKg * 100.0) / 100.0 : Math.round(r.weightKg * 10.0) / 10.0;
         r.shotM = Math.sqrt(killer.distanceToSqr(mob));
         r.animal = id;
         place(r, mob);
         RecordService.ingest(killer.server, killer, r);
      } catch (Throwable t) {
         LOG.error("Frontier Hunts camps: wildlife record failed", t);
      }
   }

   private static void place(HarvestRecord r, Entity e) {
      r.dim = e.level().dimension().location().toString();
      r.x = e.getBlockX();
      r.y = e.getBlockY();
      r.z = e.getBlockZ();
   }

   static ServerLevel overworld(MinecraftServer s) {
      return s.overworld();
   }
}
