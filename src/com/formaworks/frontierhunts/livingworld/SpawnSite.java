package com.formaworks.frontierhunts.livingworld;

import com.formaworks.frontierhunts.livingworld.plan.Executor;
import com.formaworks.frontierhunts.livingworld.plan.Kinds;
import com.formaworks.frontierhunts.livingworld.plan.Plan;
import com.formaworks.frontierhunts.livingworld.plan.Rnd;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * [livingworld] Guarantees a staffed-looking ranger check station (with a Frontier Handbook on the shelf) within walking
 * distance of world spawn in every NEW world, plus a sign at spawn pointing to it. Worlds that already existed before
 * this feature are left alone (new chunks only); the decision is stored so it runs once.
 */
@EventBusSubscriber(modid = "frontierhunts")
public final class SpawnSite {
   private SpawnSite() {
   }

   public static final class Data extends SavedData {
      public boolean done;
      public BlockPos pos;
      public String note = "";

      static Data load(CompoundTag t, HolderLookup.Provider p) {
         Data d = new Data();
         d.done = t.getBoolean("done");
         d.pos = t.contains("pos") ? NbtUtils.readBlockPos(t, "pos").orElse(null) : null;
         d.note = t.getString("note");
         return d;
      }

      @Override
      public CompoundTag save(CompoundTag t, HolderLookup.Provider p) {
         t.putBoolean("done", this.done);
         if (this.pos != null) {
            t.put("pos", NbtUtils.writeBlockPos(this.pos));
         }
         t.putString("note", this.note);
         return t;
      }

      public static Data get(ServerLevel level) {
         return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load), "frontierhunts_livingworld");
      }
   }

   @SubscribeEvent
   public static void started(ServerStartedEvent e) {
      ServerLevel level = e.getServer().overworld();
      Data d = Data.get(level);
      if (d.done) {
         return;
      }
      d.done = true;
      d.setDirty();
      if (!e.getServer().getWorldData().worldGenOptions().generateStructures()) {
         d.note = "structures are off for this world";
         return;
      }
      if (level.getGameTime() > 2400L) {
         d.note = "the world existed before the living-world sites were added";
         LivingWorld.LOG.info("[Frontier Hunts] existing world: no spawn hunting camp placed (new chunks get sites as they generate)");
         return;
      }
      try {
         place(level, d);
      } catch (RuntimeException ex) {
         d.note = "placement failed: " + ex;
         LivingWorld.LOG.error("[Frontier Hunts] could not place the spawn hunting camp", ex);
      }
   }

   static int redo(CommandSourceStack s) {
      ServerLevel level = s.getServer().overworld();
      Data d = Data.get(level);
      d.done = true;
      d.pos = null;
      place(level, d);
      d.setDirty();
      s.sendSuccess(() -> Component.literal(d.pos == null ? "No suitable spot near spawn: " + d.note : "Spawn hunting camp placed at " + d.pos.toShortString()), true);
      return d.pos == null ? 0 : 1;
   }

   static void place(ServerLevel level, Data d) {
      BlockPos spawn = level.getSharedSpawnPos();
      Rnd r = new Rnd(level.getSeed() ^ 0x5A17E5L);
      String[] kinds = {"hunting_camp"}; // [villages] the ranger station (a building) is retired; a tent camp greets new players
      for (String kind : kinds) {
         if (Kinds.get(kind) == null) {
            continue;
         }
         for (int attempt = 0; attempt < 48; attempt++) {
            double a = r.range(0.0, Math.PI * 2);
            double dist = 34 + attempt * 3.5;
            int ox = spawn.getX() + (int)Math.round(Math.cos(a) * dist);
            int oz = spawn.getZ() + (int)Math.round(Math.sin(a) * dist);
            McTerrain t = new McTerrain(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
            long seed = Rnd.mix(level.getSeed(), attempt * 7919L + kind.hashCode());
            Plan p = LivingSite.plan(kind, t, seed, ox, oz, r.nextInt(4), kind.equals("hunting_camp") ? 0 : Kinds.get(kind).pickVariant(r));
            if (p == null) {
               continue;
            }
            SitesCommand.placeNow(level, p);
            d.pos = new BlockPos(p.cx, p.cy, p.cz);
            d.note = kind;
            LivingWorld.LOG.info("[Frontier Hunts] spawn {} placed at {} ({} blocks from spawn)", kind, d.pos.toShortString(), (int)dist);
            sign(level, spawn, d.pos);
            return;
         }
      }
      d.note = "no dry, level ground within 200 blocks of spawn";
   }

   /** a sign post a few steps from spawn: "Ranger Station · 85 m north-east" */
   static void sign(ServerLevel level, BlockPos spawn, BlockPos target) {
      double dx = target.getX() - spawn.getX();
      double dz = target.getZ() - spawn.getZ();
      double len = Math.sqrt(dx * dx + dz * dz);
      if (len < 20) {
         return;
      }
      int sx = spawn.getX() + (int)Math.round(dx / len * 5);
      int sz = spawn.getZ() + (int)Math.round(dz / len * 5);
      String[] dirs = {"east", "south_east", "south", "south_west", "west", "north_west", "north", "north_east"};
      int oct = (int)Math.floorMod(Math.round(Math.atan2(dz, dx) / (Math.PI / 4)), 8);
      McTerrain t = new McTerrain(level.getChunkSource().getGenerator(), level, level.getChunkSource().randomState());
      com.formaworks.frontierhunts.livingworld.plan.Ctx c = new com.formaworks.frontierhunts.livingworld.plan.Ctx("spawn_sign", t, 1L, sx, sz, 0, 0);
      int y = c.floor(0, 0);
      if (c.wet(0, 0)) {
         return;
      }
      com.formaworks.frontierhunts.livingworld.plan.Dir face = Math.abs(dx) > Math.abs(dz)
         ? (dx > 0 ? com.formaworks.frontierhunts.livingworld.plan.Dir.WEST : com.formaworks.frontierhunts.livingworld.plan.Dir.EAST)
         : (dz > 0 ? com.formaworks.frontierhunts.livingworld.plan.Dir.NORTH : com.formaworks.frontierhunts.livingworld.plan.Dir.SOUTH);
      com.formaworks.frontierhunts.livingworld.plan.Kit.signPost(c, 0, y, 0, face, "spawn_camp", 3); // [villages] "HUNTERS' / %s m %s / CAMP"
      // fill the distance and direction into the second line
      for (Plan.Block b : c.plan.blocks()) {
         if (b.nbt != null && b.nbt.contains("spawn_camp.2")) {
            b.nbt = b.nbt.replace("{\"translate\":\"frontierhunts.lw.spawn_camp.2\"}",
               "{\"translate\":\"frontierhunts.lw.spawn_camp.2\",\"with\":[\"" + Math.round(len) + "\",{\"translate\":\"frontierhunts.lw.dir." + dirs[oct] + "\"}]}");
         }
      }
      c.plan.connect();
      SitesCommand.placeNow(level, c.plan);
   }
}
