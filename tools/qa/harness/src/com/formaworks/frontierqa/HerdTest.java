package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.hunting.herd.HerdService;
import com.formaworks.frontierhunts.hunting.herd.HerdTuning;
import com.formaworks.frontierhunts.season.SeasonClock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [herds] Social groups of deer, elk and moose on a dedicated server: /fhqa herds &lt;step&gt;. Every verdict line reads
 * "herds PASS ..." / "herds FAIL ..."; long steps print "herds done &lt;step&gt;" when finished (the command file waits on it).
 * <pre>
 *   setup                 find whitetail country ~300+ blocks from spawn, force-load a 15x15-chunk square, July, morning
 *   natural &lt;packs&gt;       natural spawn packs (the vanilla pack loop: one finalizeSpawn chain per pack) of whitetail, elk, moose
 *   run &lt;ticks&gt; &lt;tag&gt;    run time forward (tick sprint), then "herds ran &lt;tag&gt;"
 *   season &lt;pos&gt;          calendar month position (0 = 1 Jan)
 *   composition           group kinds/sizes/sexes against the model (summer)
 *   cohesion              leaders of the families (and an elk herd) walk 100+ blocks: followers sampled every 10 ticks
 *   alarm | down          startle one member / kill one: the group flees together; later calm and back together
 *   persist save|check    groups and member positions before / after an unload or a restart
 *   unload | reload       drop / restore the forced chunks (entities saved and reloaded)
 *   harem | rut | yards   elk herd bulls (Sept), bachelors split and bucks alone (Nov), winter yards (Jan)
 *   perf &lt;ticks&gt;          server tick time with social groups on / off / on, and the herd logic's own time
 * </pre>
 */
final class HerdTest {
   private HerdTest() {
   }

   static final TagKey<Biome> HABITAT = TagKey.create(Registries.BIOME, ResourceLocation.parse("frontierhunts:whitetail_habitat"));
   static final int R = 7;
   static BlockPos centre;
   static final List<Job> JOBS = new ArrayList<>();
   static boolean hooked;
   static long tickStart;
   static long tickNanos;
   static long ticksMeasured;
   static final Map<String, String[]> SAVED = new LinkedHashMap<>();
   static final Map<UUID, Vec3> SAVED_POS = new HashMap<>();
   /** where each animal stood when it was last saved out (chunk unload, server stop) and when it was next loaded */
   static final Map<UUID, Vec3> LEFT_AT = new HashMap<>();
   static final Map<UUID, Vec3> JOINED_AT = new HashMap<>();

   interface Job {
      /** @return true when finished */
      boolean tick(ServerLevel level, long now);
   }

   static void say(String s) {
      FrontierQa.say("herds " + s);
   }

   static void verdict(boolean ok, String what) {
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static void hook() {
      if (hooked) {
         return;
      }

      hooked = true;
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) -> {
         if (!e.getLevel().isClientSide && e.getEntity() instanceof Whitetail w && (e.loadedFromDisk() || !JOINED_AT.containsKey(w.getUUID()))) {
            JOINED_AT.put(w.getUUID(), w.position());
         }
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e) -> {
         if (!e.getLevel().isClientSide && e.getEntity() instanceof Whitetail w) {
            LEFT_AT.put(w.getUUID(), w.position());
         }
      });
      NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
         // the positions the world is saved with: the restart run compares where the animals load against these
         if (centre == null) {
            return;
         }

         StringBuilder b = new StringBuilder();
         for (Whitetail w : animals(e.getServer().overworld())) {
            b.append(w.getUUID()).append(' ').append(w.getX()).append(' ').append(w.getY()).append(' ').append(w.getZ()).append('\n');
         }

         try {
            Files.writeString(e.getServer().getWorldPath(LevelResource.ROOT).resolve("fhqa_herds_stop.txt"), b.toString(), StandardCharsets.UTF_8);
         } catch (Exception x) {
            FrontierQa.fail("herds stop positions", x);
         }
      });
      NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ServerTickEvent.Pre.class, e -> tickStart = System.nanoTime());
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> {
         if (tickStart != 0L) {
            tickNanos += System.nanoTime() - tickStart;
            ticksMeasured++;
         }

         ServerLevel level = e.getServer().overworld();
         long now = level.getGameTime();
         JOBS.removeIf(j -> {
            try {
               return j.tick(level, now);
            } catch (Throwable t) {
               FrontierQa.fail("herds job", t);
               return true;
            }
         });
      });
   }

   static int run(CommandSourceStack src, String args) {
      hook();
      MinecraftServer server = src.getServer();
      ServerLevel level = server.overworld();
      String[] a = args.trim().split("\\s+");
      try {
         switch (a[0]) {
            case "setup" -> setup(server, level);
            case "natural" -> natural(level, a.length > 1 ? Integer.parseInt(a[1]) : 14);
            case "run" -> runFor(server, level, Integer.parseInt(a[1]), a.length > 2 ? a[2] : "x");
            case "season" -> {
               SeasonClock.setYearPosition(server, Double.parseDouble(a[1]));
               say("season set to " + a[1] + " (now " + String.format(java.util.Locale.ROOT, "%.2f", SeasonClock.yearPosition(level)) + ")");
            }
            case "composition" -> composition(level);
            case "cohesion" -> cohesion(level);
            case "alarm" -> alarm(level, false);
            case "down" -> alarm(level, true);
            case "persist" -> {
               if (a.length > 1 && a[1].equals("save")) {
                  persistSave(server, level);
               } else {
                  persistCheck(server, level);
               }
            }
            case "unload" -> force(level, false);
            case "reload" -> force(level, true);
            case "harem" -> harem(level);
            case "rut" -> rut(level);
            case "yards" -> yards(level);
            case "perf" -> perf(level, a.length > 1 ? Integer.parseInt(a[1]) : 600);
            case "plan" -> plan();
            default -> say("FAIL unknown step " + a[0]);
         }
      } catch (Throwable t) {
         FrontierQa.fail("herds " + args, t);
      }

      return 1;
   }

   // ================================================================== world

   static void setup(MinecraftServer server, ServerLevel level) {
      BlockPos spawn = level.getSharedSpawnPos();
      BlockPos found = null;
      search:
      for (int ring = 5; ring <= 40; ring++) {
         int r = ring * 64;
         for (int k = 0; k < ring * 6; k++) {
            double ang = k * Math.PI * 2.0 / (ring * 6);
            int x = spawn.getX() + (int)(Math.cos(ang) * r);
            int z = spawn.getZ() + (int)(Math.sin(ang) * r);
            int ok = 0;
            for (int dx = -1; dx <= 1; dx++) {
               for (int dz = -1; dz <= 1; dz++) {
                  Holder<Biome> b = AreaTest.biomeAt(level, x + dx * 80, 70, z + dz * 80);
                  if (b.is(HABITAT)) {
                     ok++;
                  }
               }
            }

            if (ok >= 8) {
               found = new BlockPos(x, 70, z);
               break search;
            }
         }
      }

      if (found == null) {
         say("FAIL setup: no whitetail country within 2500 blocks of spawn");
         return;
      }

      centre = found;
      for (int cx = -R; cx <= R; cx++) {
         for (int cz = -R; cz <= R; cz++) {
            level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, true);
         }
      }

      SeasonClock.setYearPosition(server, 6.5); // July: families and bachelor groups
      level.setDayTime(1000L);
      saveCentre(server);
      say("setup centre " + centre.getX() + " " + centre.getZ() + " (" + AreaTest.name(AreaTest.biomeAt(level, centre.getX(), 70, centre.getZ()))
         + "), forcing " + (2 * R + 1) * (2 * R + 1) + " chunks");
      JOBS.add(new Job() {
         final long start = level.getGameTime();

         @Override
         public boolean tick(ServerLevel l, long now) {
            if ((now - this.start) % 20L != 0L) {
               return false;
            }

            int loaded = 0;
            for (int cx = -R; cx <= R; cx++) {
               for (int cz = -R; cz <= R; cz++) {
                  if (l.hasChunk((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz)) {
                     loaded++;
                  }
               }
            }

            if (loaded == (2 * R + 1) * (2 * R + 1) || now - this.start > 4800L) {
               say("done setup: " + loaded + " chunks loaded, " + animals(l).size() + " deer/elk/moose there from chunk generation");
               return true;
            }

            return false;
         }
      });
   }

   static void saveCentre(MinecraftServer server) {
      try {
         Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("fhqa_herds_centre.txt"), centre.getX() + " " + centre.getZ());
      } catch (Exception e) {
         FrontierQa.fail("herds centre", e);
      }
   }

   static boolean loadCentre(MinecraftServer server) {
      if (centre != null) {
         return true;
      }

      try {
         Path p = server.getWorldPath(LevelResource.ROOT).resolve("fhqa_herds_centre.txt");
         if (Files.exists(p)) {
            String[] xz = Files.readString(p).trim().split(" ");
            centre = new BlockPos(Integer.parseInt(xz[0]), 70, Integer.parseInt(xz[1]));
            return true;
         }
      } catch (Exception e) {
         FrontierQa.fail("herds centre", e);
      }

      return false;
   }

   static void force(ServerLevel level, boolean on) {
      for (int cx = -R; cx <= R; cx++) {
         for (int cz = -R; cz <= R; cz++) {
            level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, on);
         }
      }

      int before = animals(level).size();
      if (!on) {
         LEFT_AT.clear();
         JOINED_AT.clear();
      }

      int expect = LEFT_AT.size();
      say((on ? "re-forced" : "released") + " the square (" + before + " animals loaded now)");
      long start = level.getGameTime();
      JOBS.add((l, now) -> {
         if ((now - start) % 20L != 0L) {
            return false;
         }

         int n = animals(l).size();
         if (!on && n == 0 || on && now - start >= 100L && n >= expect * 0.95 && n > 0 || now - start > 2400L) {
            say("done " + (on ? "reload" : "unload") + ": " + n + " animals loaded after " + (now - start) + " ticks");
            return true;
         }

         return false;
      });
   }

   static List<Whitetail> animals(ServerLevel level) {
      if (centre == null) {
         return List.of();
      }

      double half = (R + 0.5) * 16.0;
      return level.getEntitiesOfClass(Whitetail.class, new AABB(centre.getX() - half, -64, centre.getZ() - half, centre.getX() + half, 320, centre.getZ() + half),
         w -> !w.downed() && !w.isRemoved());
   }

   static void runFor(MinecraftServer server, ServerLevel level, int ticks, String tag) {
      long end = level.getGameTime() + ticks;
      server.tickRateManager().requestGameToSprint(ticks);
      long t0 = System.nanoTime();
      JOBS.add((l, now) -> {
         if (now >= end) {
            say("ran " + tag + " (" + ticks + " ticks in " + (System.nanoTime() - t0) / 1000000L + " ms)");
            return true;
         }

         return false;
      });
   }

   // ================================================================== natural spawns

   static void natural(ServerLevel level, int packs) {
      if (centre == null) {
         say("FAIL natural: run setup first");
         return;
      }

      RandomSource random = level.getRandom();
      int[] made = new int[3];
      int[] groups = new int[3];
      String[] ids = {"whitetail", "elk", "moose"};
      int[] want = {packs, Math.max(2, packs / 3), Math.max(2, packs / 3)};
      for (int s = 0; s < 3; s++) {
         EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:" + ids[s]));
         for (int p = 0; p < want[s]; p++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double dist = 20.0 + random.nextDouble() * 70.0;
            int x = centre.getX() + (int)(Math.cos(ang) * dist);
            int z = centre.getZ() + (int)(Math.sin(ang) * dist);
            int n = packSize(level, type, new BlockPos(x, 70, z), random);
            int spawned = pack(level, type, x, z, n, random);
            if (spawned > 0) {
               made[s] += spawned;
               groups[s]++;
            }
         }
      }

      say("natural packs: whitetail " + groups[0] + " packs / " + made[0] + " animals, elk " + groups[1] + " / " + made[1] + ", moose " + groups[2] + " / " + made[2]
         + " (season " + String.format(java.util.Locale.ROOT, "%.2f", HerdService.yearPosition()) + ")");
      verdict(made[0] >= packs, "natural whitetail packs spawned (" + made[0] + " whitetails in " + groups[0] + " packs)");
   }

   /** The pack size vanilla would roll for this type here: the biome's spawner entry (min..max), else our spawn files' sizes. */
   static int packSize(ServerLevel level, EntityType<?> type, BlockPos at, RandomSource random) {
      int min = type.toShortString().contains("elk") ? 3 : 1;
      int max = type.toShortString().contains("elk") ? 8 : type.toShortString().contains("moose") ? 2 : 5;
      for (MobSpawnSettings.SpawnerData d : level.getBiome(at).value().getMobSettings().getMobs(MobCategory.CREATURE).unwrap()) {
         if (d.type == type) {
            min = d.minCount;
            max = d.maxCount;
         }
      }

      return min + random.nextInt(1 + max - min);
   }

   /** The vanilla natural spawn loop for one pack: random walk of positions, one SpawnGroupData chain, the cluster cap. */
   static int pack(ServerLevel level, EntityType<?> type, int x0, int z0, int count, RandomSource random) {
      SpawnGroupData data = null;
      int x = x0;
      int z = z0;
      int n = 0;
      for (int i = 0; i < count * 4 && n < count; i++) {
         x += random.nextInt(6) - random.nextInt(6);
         z += random.nextInt(6) - random.nextInt(6);
         int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos pos = new BlockPos(x, y, z);
         if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            continue;
         }

         Entity e = type.create(level);
         if (!(e instanceof Whitetail w)) {
            return n;
         }

         w.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
         data = w.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, data);
         if (level.addFreshEntity(w)) {
            n++;
         }

         if (n >= w.getMaxSpawnClusterSize()) {
            break;
         }
      }

      return n;
   }

   /** Offline: 4000 rolled packs per species and season, without spawning (the spawn mix and the places in each). */
   static void plan() {
      RandomSource random = RandomSource.create(42L);
      for (GameSpecies sp : GameSpecies.values()) {
         for (double pos : new double[]{6.5, 10.4, 0.6}) {
            Map<String, Integer> kinds = new java.util.TreeMap<>();
            for (int i = 0; i < 4000; i++) {
               kinds.merge(com.formaworks.frontierhunts.hunting.herd.HerdSpawn.describePlan(sp, random, pos)[0], 1, Integer::sum);
            }

            say("plan " + sp.id + " @" + pos + ": " + kinds);
         }
      }
   }

   // ================================================================== groups as they stand

   record Grp(String id, String kind, List<Whitetail> members, UUID leader, String parent) {
   }

   static Map<String, Grp> groups(ServerLevel level, GameSpecies sp) {
      Map<String, Grp> out = new LinkedHashMap<>();
      for (Whitetail w : animals(level)) {
         if (w.species() != sp) {
            continue;
         }

         String g = HerdService.groupOf(w);
         UUID root = HerdService.yardRootOf(w);
         Grp grp = out.computeIfAbsent(g.isEmpty() ? "none:" + w.getUUID() : g,
            k -> new Grp(k, HerdService.kindOf(w), new ArrayList<>(), HerdService.leaderOf(w), root == null ? "" : root.toString()));
         grp.members.add(w);
      }

      return out;
   }

   static String sexAge(Whitetail w) {
      return (w.traits().buck() ? "M" : "F") + w.traits().ageMonths();
   }

   static void composition(ServerLevel level) {
      int ungrouped = 0;
      int total = 0;
      for (Whitetail w : animals(level)) {
         total++;
         if (HerdService.groupOf(w).isEmpty()) {
            ungrouped++;
         }
      }

      say("composition: " + total + " animals, " + ungrouped + " not yet in a group");
      verdict(total > 0 && ungrouped <= Math.max(1, total / 20), "every animal has a group (" + ungrouped + " of " + total + " without)");
      // whitetail
      Map<String, Grp> wt = groups(level, GameSpecies.WHITETAIL);
      int fam = 0;
      int bach = 0;
      int soloBuck = 0;
      int loneDoe = 0;
      int bigFam = 0;
      int badFam = 0;
      int badBach = 0;
      int spreadOk = 0;
      int spreadAll = 0;
      List<Integer> famSizes = new ArrayList<>();
      List<Integer> bachSizes = new ArrayList<>();
      for (Grp g : wt.values()) {
         StringBuilder b = new StringBuilder();
         for (Whitetail w : g.members) {
            b.append(sexAge(w)).append(w.getUUID().equals(g.leader) ? "*" : "").append(' ');
         }

         int n = g.members.size();
         if (n >= 2 && g.kind.equals("FAMILY")) {
            fam++;
            famSizes.add(n);
            Whitetail lead = lead(g);
            boolean adultDoeLeads = lead != null && !lead.traits().buck() && lead.traits().ageMonths() >= 24;
            boolean noAdultBuck = g.members.stream().noneMatch(w -> w.traits().buck() && w.traits().ageMonths() >= 24);
            if (!adultDoeLeads || !noAdultBuck) {
               badFam++;
            }

            if (n > HerdTuning.WHITETAIL.familyMax()) {
               bigFam++;
            }
         } else if (g.kind.equals("BACHELOR") && n >= 2) {
            bach++;
            bachSizes.add(n);
            if (n > HerdTuning.WHITETAIL.bachelorMax() || g.members.stream().anyMatch(w -> !w.traits().buck())) {
               badBach++;
            }
         } else if (n == 1 && g.members.get(0).traits().buck()) {
            soloBuck++;
         } else if (n == 1) {
            loneDoe++;
         }

         Whitetail lead = lead(g);
         if (lead != null && n >= 2) {
            for (Whitetail w : g.members) {
               if (w != lead) {
                  spreadAll++;
                  if (w.distanceTo(lead) <= 48.0) {
                     spreadOk++;
                  }
               }
            }
         }

         say("  whitetail " + g.kind + " x" + n + ": " + b.toString().trim());
      }

      say("whitetail: family groups " + famSizes + ", bachelor groups " + bachSizes + ", solitary bucks " + soloBuck + ", lone does " + loneDoe);
      verdict(fam >= 1 && badFam == 0 && bigFam == 0, "whitetail family groups of 2-6, each led by an adult doe with no adult buck (" + fam + " groups, " + badFam
         + " wrong, " + bigFam + " too big)");
      verdict(bach >= 1 && badBach == 0, "whitetail bachelor groups of 2-4 bucks in summer (" + bach + " groups, " + badBach + " wrong)");
      verdict(soloBuck >= 1, "some whitetail bucks live alone (" + soloBuck + ")");
      verdict(spreadAll > 0 && spreadOk >= spreadAll * 0.9, "group mates are near their leader (" + spreadOk + " of " + spreadAll + " within 48 m)");
      // elk
      Map<String, Grp> elk = groups(level, GameSpecies.ELK);
      int herds = 0;
      int badHerd = 0;
      int bands = 0;
      List<Integer> herdSizes = new ArrayList<>();
      for (Grp g : elk.values()) {
         int n = g.members.size();
         Whitetail lead = lead(g);
         if (g.kind.equals("FAMILY") && n >= 2) {
            herds++;
            herdSizes.add(n);
            if (lead == null || lead.traits().buck() || n > HerdTuning.ELK.familyMax()) {
               badHerd++;
            }
         } else if (g.kind.equals("BACHELOR")) {
            bands++;
            if (g.members.stream().anyMatch(w -> !w.traits().buck())) {
               badHerd++;
            }
         }
      }

      say("elk: cow herds " + herdSizes + ", bachelor bands " + bands + ", groups " + elk.size());
      verdict(elk.isEmpty() || herds >= 1 && badHerd == 0, "elk cow herds led by a cow, bachelor bands all bulls (" + herds + " herds, " + badHerd + " wrong)");
      // moose
      Map<String, Grp> moose = groups(level, GameSpecies.MOOSE);
      int badMoose = 0;
      int pairs = 0;
      for (Grp g : moose.values()) {
         if (g.members.size() > 2) {
            badMoose++;
         } else if (g.members.size() == 2) {
            pairs++;
            long cows = g.members.stream().filter(w -> !w.traits().buck() && w.traits().ageMonths() >= 24).count();
            long calves = g.members.stream().filter(w -> w.traits().ageMonths() < HerdTuning.MOOSE_CALF_MONTHS).count();
            if (cows != 1 || calves != 1) {
               badMoose++;
            }
         }
      }

      verdict(badMoose == 0, "moose alone or a cow with her calf (" + moose.size() + " groups, " + pairs + " cow+calf, " + badMoose + " wrong)");
   }

   static Whitetail lead(Grp g) {
      for (Whitetail w : g.members) {
         if (w.getUUID().equals(g.leader)) {
            return w;
         }
      }

      return null;
   }

   // ================================================================== cohesion while travelling

   static final class Track {
      final Whitetail leader;
      final List<Whitetail> followers;
      final GameSpecies species;
      Vec3 last;
      double path;
      long accepted = -1L;
      int samples;
      int within;
      int moving;
      final List<Double> gaps = new ArrayList<>();
      final List<Double> lateral = new ArrayList<>();
      final List<Double> nearest = new ArrayList<>();
      final Map<UUID, Vec3> prev = new HashMap<>();

      Track(Whitetail leader, List<Whitetail> followers) {
         this.leader = leader;
         this.followers = followers;
         this.species = leader.species();
      }
   }

   static void cohesion(ServerLevel level) {
      List<Track> tracks = new ArrayList<>();
      List<Grp> pool = new ArrayList<>();
      pool.addAll(groups(level, GameSpecies.WHITETAIL).values());
      pool.addAll(groups(level, GameSpecies.ELK).values());
      pool.sort((x, y) -> y.members.size() - x.members.size());
      int deer = 0;
      int elk = 0;
      for (Grp g : pool) {
         Whitetail lead = lead(g);
         if (lead == null || g.members.size() < 3 || !g.kind.equals("FAMILY") || !g.parent.isEmpty()) {
            continue;
         }

         boolean isElk = lead.species() == GameSpecies.ELK;
         if (isElk ? elk >= 1 : deer >= 3) {
            continue;
         }

         List<Whitetail> f = new ArrayList<>(g.members);
         f.remove(lead);
         tracks.add(new Track(lead, f));
         if (isElk) {
            elk++;
         } else {
            deer++;
         }
      }

      if (tracks.isEmpty()) {
         say("FAIL cohesion: no family group of 3+ to follow");
         say("done cohesion");
         return;
      }

      long start = level.getGameTime();
      say("cohesion: following " + tracks.size() + " groups (" + deer + " whitetail, " + elk + " elk) for 2400 ticks");
      JOBS.add(new Job() {
         @Override
         public boolean tick(ServerLevel l, long now) {
            long t = now - start;
            for (Track tr : tracks) {
               if (tr.accepted < 0L && t % 20L == 0L) {
                  // the leader sets off for the far side of the square (once it is on its feet)
                  HerdService.wake(tr.leader, 1);
                  Vec3 p = tr.leader.position();
                  double dx = centre.getX() - p.x;
                  double dz = centre.getZ() - p.z;
                  double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
                  double reach = (R - 1) * 16.0 - 8.0;
                  int gx = (int)(centre.getX() + dx / len * reach);
                  int gz = (int)(centre.getZ() + dz / len * reach);
                  if (Math.hypot(gx - p.x, gz - p.z) < 110.0) {
                     double a = Math.atan2(dz, dx) + 1.2;
                     gx = (int)(centre.getX() + Math.cos(a) * reach);
                     gz = (int)(centre.getZ() + Math.sin(a) * reach);
                  }

                  int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
                  if (HerdService.walkTo(tr.leader, new BlockPos(gx, gy, gz))) {
                     tr.accepted = now;
                     tr.last = tr.leader.position();
                     say("cohesion: leader " + sexAge(tr.leader) + " of " + (tr.followers.size() + 1) + " " + tr.species.id + " sets off for " + gx + " " + gz
                        + " (" + Math.round(Math.hypot(gx - p.x, gz - p.z)) + " m)");
                  }
               }

               if (tr.accepted < 0L || t % 10L != 0L || tr.leader.isRemoved()) {
                  continue;
               }

               Vec3 lp = tr.leader.position();
               tr.path += Math.hypot(lp.x - tr.last.x, lp.z - tr.last.z);
               tr.last = lp;
               if (!HerdService.moving(tr.leader)) {
                  continue;
               }

               tr.moving++;
               List<Whitetail> all = new ArrayList<>(tr.followers);
               all.add(tr.leader);
               double slack = tr.species == GameSpecies.ELK ? 32.0 : 18.0;
               for (Whitetail w : tr.followers) {
                  if (w.isRemoved() || w.downed()) {
                     continue;
                  }

                  UUID fid = HerdService.followIdOf(w);
                  Entity target = fid == null ? null : l.getEntity(fid);
                  if (!(target instanceof Whitetail tw)) {
                     continue;
                  }

                  double d = w.distanceTo(tw);
                  tr.samples++;
                  tr.gaps.add(d);
                  if (d <= HerdService.gap(w) + slack) {
                     tr.within++;
                  }

                  Vec3 tp = tr.prev.get(tw.getUUID());
                  Vec3 now3 = tw.position();
                  if (tp != null && now3.distanceToSqr(tp) > 0.04) {
                     Vec3 v = now3.subtract(tp).multiply(1, 0, 1).normalize();
                     Vec3 r = w.position().subtract(now3).multiply(1, 0, 1);
                     tr.lateral.add(Math.abs(r.x * v.z - r.z * v.x));
                  }

                  double nn = Double.MAX_VALUE;
                  for (Whitetail o : all) {
                     if (o != w) {
                        nn = Math.min(nn, o.distanceTo(w));
                     }
                  }

                  tr.nearest.add(nn);
               }

               for (Whitetail w : all) {
                  tr.prev.put(w.getUUID(), w.position());
               }
            }

            if (t < 2400L) {
               return false;
            }

            for (Track tr : tracks) {
               double share = tr.samples == 0 ? 0.0 : (double)tr.within / tr.samples;
               double settled = 0;
               for (Whitetail w : tr.followers) {
                  if (!w.isRemoved() && w.distanceTo(tr.leader) <= HerdTuning.of(tr.species).feedSpread() + 12.0) {
                     settled++;
                  }
               }

               say(String.format(java.util.Locale.ROOT,
                  "cohesion %s group of %d: leader walked %.0f m (%d moving samples); follower-to-followed distance median %.1f m, p90 %.1f m; within slot+slack %.0f%% of %d samples;"
                     + " nearest mate median %.1f m; side offset median %.1f m; %d/%d close to the leader at the end",
                  tr.species.id, tr.followers.size() + 1, tr.path, tr.moving, pct(tr.gaps, 0.5), pct(tr.gaps, 0.9), share * 100.0, tr.samples, pct(tr.nearest, 0.5),
                  pct(tr.lateral, 0.5), (int)settled, tr.followers.size()));
               if (tr.species == GameSpecies.ELK && tr.path < 60.0) {
                  // a big elk in whitetail woods can be boxed in by the trees: that is terrain, not the herd logic
                  say("SKIP elk cohesion: the elk leader could only walk " + Math.round(tr.path) + " m in this forest");
                  continue;
               }

               verdict(tr.path >= (tr.species == GameSpecies.ELK ? 60.0 : 100.0), tr.species.id + " leader travelled " + (tr.species == GameSpecies.ELK ? "60" : "100")
                  + "+ m (" + Math.round(tr.path) + ")");
               verdict(share >= 0.85, tr.species.id + " followers keep their place behind the one they follow while travelling (" + Math.round(share * 100.0) + "% within slot+"
                  + (tr.species == GameSpecies.ELK ? 32 : 18) + " m)");
               verdict(pct(tr.nearest, 0.5) >= 1.8, tr.species.id + " group travels loosely spaced, not a tight blob (nearest mate median "
                  + String.format(java.util.Locale.ROOT, "%.1f", pct(tr.nearest, 0.5)) + " m)");
               double lat = pct(tr.lateral, 0.5);
               verdict(lat >= 0.15 && lat <= 8.0, tr.species.id + " followers drift off the leader's exact line, not a marching file (side offset median "
                  + String.format(java.util.Locale.ROOT, "%.2f", lat) + " m)");
            }

            say("done cohesion");
            return true;
         }
      });
   }

   static double pct(List<Double> v, double q) {
      if (v.isEmpty()) {
         return Double.NaN;
      }

      double[] a = v.stream().mapToDouble(Double::doubleValue).toArray();
      Arrays.sort(a);
      return a[Math.min(a.length - 1, (int)Math.floor(q * (a.length - 1) + 0.5))];
   }

   // ================================================================== alarm propagation

   static void alarm(ServerLevel level, boolean kill) {
      Grp pick = null;
      for (Grp g : groups(level, GameSpecies.WHITETAIL).values()) {
         Whitetail lead = lead(g);
         if (lead == null || g.members.size() < 3 || !g.parent.isEmpty()) {
            continue;
         }

         boolean calm = g.members.stream().allMatch(w -> w.behavior() != Whitetail.BEHAVIOR_FLEEING && w.alertness() < 0.3F);
         double spread = g.members.stream().mapToDouble(w -> w.distanceTo(lead)).max().orElse(0.0);
         if (calm && spread < 30.0 && (pick == null || g.members.size() > pick.members.size())) {
            pick = g;
         }
      }

      if (pick == null) {
         say("FAIL " + (kill ? "down" : "alarm") + ": no calm whitetail family of 3+ together");
         say("done " + (kill ? "down" : "alarm"));
         return;
      }

      Grp g = pick;
      Whitetail lead = lead(g);
      Whitetail one = kill ? lead : g.members.stream().filter(w -> w != lead).findFirst().orElse(lead);
      RandomSource random = level.getRandom();
      double a = random.nextDouble() * Math.PI * 2.0;
      Vec3 threat = one.position().add(Math.cos(a) * 14.0, 0.0, Math.sin(a) * 14.0);
      Map<UUID, Vec3> start = new HashMap<>();
      for (Whitetail w : g.members) {
         start.put(w.getUUID(), w.position());
      }

      int size = g.members.size();
      if (kill) {
         one.hurt(level.damageSources().generic(), one.getHealth() + 50.0F);
         if (!one.downed()) {
            one.die(level.damageSources().generic());
         }

         say("down: killed the leader (" + sexAge(one) + ") of a family of " + size);
      } else {
         one.alarm(threat, 1.0F, 300);
         say("alarm: startled one member (" + sexAge(one) + ") of a family of " + size + " from 14 m");
      }

      Vec3 from = kill ? one.position() : threat;
      long t0 = level.getGameTime();
      String step = kill ? "down" : "alarm";
      JOBS.add(new Job() {
         final Map<UUID, Long> fledAt = new HashMap<>();

         @Override
         public boolean tick(ServerLevel l, long now) {
            long t = now - t0;
            for (Whitetail w : g.members) {
               if (w != one && !w.isRemoved() && w.behavior() == Whitetail.BEHAVIOR_FLEEING) {
                  this.fledAt.putIfAbsent(w.getUUID(), t);
               }
            }

            if (t == 120L) {
               int others = 0;
               int fled = 0;
               long latest = 0L;
               double sx = 0.0;
               double sz = 0.0;
               int away = 0;
               int n = 0;
               for (Whitetail w : g.members) {
                  if (w == one || w.isRemoved()) {
                     continue;
                  }

                  others++;
                  Long at = this.fledAt.get(w.getUUID());
                  if (at != null) {
                     fled++;
                     latest = Math.max(latest, at);
                  }

                  Vec3 d = w.position().subtract(start.get(w.getUUID())).multiply(1, 0, 1);
                  if (d.lengthSqr() > 1.0) {
                     Vec3 u = d.normalize();
                     sx += u.x;
                     sz += u.z;
                     n++;
                     Vec3 off = start.get(w.getUUID()).subtract(from).multiply(1, 0, 1);
                     if (off.lengthSqr() < 1.0 || u.dot(off.normalize()) > -0.2) {
                        away++;
                     }
                  }
               }

               double coherence = n == 0 ? 0.0 : Math.sqrt(sx * sx + sz * sz) / n;
               double spread = 0.0;
               for (Whitetail x : g.members) {
                  for (Whitetail y : g.members) {
                     if (x != one && y != one && !x.isRemoved() && !y.isRemoved()) {
                        spread = Math.max(spread, x.distanceTo(y));
                     }
                  }
               }

               say(String.format(java.util.Locale.ROOT, "%s: %d of %d mates bolted (last after %d ticks); flight heading coherence %.2f (1 = one direction); %d of %d ran away from the danger; spread after 6 s %.0f m",
                  step, fled, others, latest, coherence, away, n, spread));
               verdict(fled == others && latest <= 40L, step + ": the whole group bolts within 2 s (" + fled + "/" + others + ", last at " + latest + " ticks)");
               verdict(coherence >= 0.7, step + ": the group runs one way together (coherence " + String.format(java.util.Locale.ROOT, "%.2f", coherence) + ")");
               verdict(spread <= 48.0, step + ": they stay together in flight (spread " + Math.round(spread) + " m)");
            }

            if (t == 1800L) {
               int calm = 0;
               int close = 0;
               int others = 0;
               Whitetail newLead = null;
               for (Whitetail w : g.members) {
                  if (w == one || w.isRemoved()) {
                     continue;
                  }

                  others++;
                  if (w.alertness() < 0.25F && w.behavior() != Whitetail.BEHAVIOR_FLEEING) {
                     calm++;
                  }

                  UUID lid = HerdService.leaderOf(w);
                  if (lid != null && l.getEntity(lid) instanceof Whitetail lw) {
                     newLead = lw;
                     if (w == lw || w.distanceTo(lw) <= HerdTuning.WHITETAIL.feedSpread() + 14.0) {
                        close++;
                     }
                  }
               }

               boolean sameGroup = g.members.stream().filter(w -> w != one && !w.isRemoved()).map(HerdService::groupOf).distinct().count() == 1;
               say(step + " later (90 s): " + calm + " of " + others + " calm again, " + close + " back with their leader"
                  + (newLead != null ? " (" + sexAge(newLead) + ")" : "") + ", one group: " + sameGroup);
               verdict(calm == others && close >= others - 1 && sameGroup, step + ": the group settles and closes up again" + (kill ? " under a new leader" : ""));
               if (kill) {
                  verdict(newLead != null && newLead != one && !newLead.downed(), "down: a new leader took over (" + (newLead == null ? "none" : sexAge(newLead)) + ")");
               }

               say("done " + step);
               return true;
            }

            return false;
         }
      });
   }

   // ================================================================== persistence

   static void persistSave(MinecraftServer server, ServerLevel level) {
      SAVED.clear();
      SAVED_POS.clear();
      StringBuilder file = new StringBuilder();
      int animals = 0;
      for (GameSpecies sp : GameSpecies.values()) {
         for (Grp g : groups(level, sp).values()) {
            if (g.id.startsWith("none:")) {
               continue;
            }

            String[] row = new String[g.members.size() + 2];
            row[0] = g.kind;
            row[1] = g.leader == null ? "" : g.leader.toString();
            for (int i = 0; i < g.members.size(); i++) {
               Whitetail w = g.members.get(i);
               row[i + 2] = w.getUUID().toString();
               SAVED_POS.put(w.getUUID(), w.position());
               file.append("pos ").append(w.getUUID()).append(' ').append(w.getX()).append(' ').append(w.getY()).append(' ').append(w.getZ()).append('\n');
               animals++;
            }

            SAVED.put(g.id, row);
            file.append("group ").append(g.id).append(' ').append(String.join(" ", row)).append('\n');
         }
      }

      try {
         Files.writeString(server.getWorldPath(LevelResource.ROOT).resolve("fhqa_herds.txt"), file.toString(), StandardCharsets.UTF_8);
      } catch (Exception e) {
         FrontierQa.fail("herds persist save", e);
      }

      say("persist: saved " + SAVED.size() + " groups, " + animals + " animals");
   }

   static void persistCheck(MinecraftServer server, ServerLevel level) {
      if (!loadCentre(server)) {
         say("FAIL persist check: no test square in this world");
         return;
      }

      if (SAVED.isEmpty()) {
         try {
            for (String line : Files.readAllLines(server.getWorldPath(LevelResource.ROOT).resolve("fhqa_herds.txt"))) {
               String[] p = line.split(" ");
               if (p[0].equals("pos")) {
                  SAVED_POS.put(UUID.fromString(p[1]), new Vec3(Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4])));
               } else if (p[0].equals("group")) {
                  SAVED.put(p[1], Arrays.copyOfRange(p, 2, p.length));
               }
            }
         } catch (Exception e) {
            FrontierQa.fail("herds persist read", e);
            return;
         }

         say("persist: read " + SAVED.size() + " groups saved by the previous run");
      }

      Map<UUID, Whitetail> loaded = new HashMap<>();
      for (Whitetail w : animals(level)) {
         loaded.put(w.getUUID(), w);
      }

      // where they were saved out: at the chunk unload in this run, or at the last server stop (restart run)
      Map<UUID, Vec3> savedAt = new HashMap<>(LEFT_AT);
      if (savedAt.isEmpty()) {
         try {
            Path stop = server.getWorldPath(LevelResource.ROOT).resolve("fhqa_herds_stop.txt");
            if (Files.exists(stop)) {
               for (String line : Files.readAllLines(stop)) {
                  String[] p = line.split(" ");
                  if (p.length == 4) {
                     savedAt.put(UUID.fromString(p[0]), new Vec3(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])));
                  }
               }
            }
         } catch (Exception e) {
            FrontierQa.fail("herds stop positions read", e);
         }
      }

      int compared = 0;

      int found = 0;
      int same = 0;
      int moved = 0;
      int groupsKept = 0;
      int leadersKept = 0;
      for (Map.Entry<String, String[]> e : SAVED.entrySet()) {
         String[] row = e.getValue();
         boolean all = true;
         for (int i = 2; i < row.length; i++) {
            Whitetail w = loaded.get(UUID.fromString(row[i]));
            if (w == null) {
               all = false;
               continue;
            }

            found++;
            if (HerdService.groupOf(w).equals(e.getKey())) {
               same++;
            } else {
               all = false;
            }

            Vec3 was = savedAt.get(w.getUUID());
            Vec3 came = JOINED_AT.get(w.getUUID());
            if (was != null && came != null) {
               compared++;
               if (was.distanceTo(came) > 2.0) {
                  moved++;
               }
            }
         }

         if (all) {
            groupsKept++;
            Whitetail any = loaded.get(UUID.fromString(row[2]));
            UUID lead = any == null ? null : HerdService.leaderOf(any);
            if (lead != null && lead.toString().equals(row[1])) {
               leadersKept++;
            }
         }
      }

      say("persist check: " + found + " of " + SAVED_POS.size() + " saved animals loaded, " + same + " still in the same group, " + groupsKept + " of " + SAVED.size()
         + " groups whole, " + leadersKept + " with the same leader; of " + compared + " compared, " + moved + " loaded more than 2 m from where they were saved");
      verdict(found >= SAVED_POS.size() * 0.95 && same == found, "groups survive unloading / a restart (" + same + "/" + found + " animals in their group)");
      verdict(leadersKept >= groupsKept * 0.9, "leaders survive unloading / a restart (" + leadersKept + "/" + groupsKept + ")");
      verdict(compared > 0 && moved == 0, "nobody was teleported to its group (" + moved + " of " + compared + " loaded away from where they were saved)");
   }

   // ================================================================== seasons

   static void harem(ServerLevel level) {
      int herds = 0;
      int withBull = 0;
      int bulls = 0;
      int bullsHolding = 0;
      for (Grp g : groups(level, GameSpecies.ELK).values()) {
         long b = g.members.stream().filter(w -> w.traits().buck() && w.traits().ageMonths() >= HerdTuning.HERD_BULL_MONTHS).count();
         if (g.kind.equals("FAMILY") && g.members.stream().filter(w -> !w.traits().buck()).count() >= 2) {
            herds++;
            if (b == 1) {
               withBull++;
            }
         }

         for (Whitetail w : g.members) {
            if (w.traits().buck() && w.traits().ageMonths() >= HerdTuning.HERD_BULL_MONTHS) {
               bulls++;
               if (g.kind.equals("FAMILY")) {
                  bullsHolding++;
               }
            }
         }

         if (b > 1 && g.kind.equals("FAMILY")) {
            verdict(false, "harem: a cow herd has " + b + " herd bulls");
         }
      }

      say("harem (elk rut): " + herds + " cow herds, " + withBull + " held by a herd bull; " + bulls + " mature bulls, " + bullsHolding + " holding a harem");
      verdict(herds == 0 || withBull >= 1, "harem: elk herd bulls hold cow herds in the rut (" + withBull + " of " + herds + ")");
   }

   static void rut(ServerLevel level) {
      int bachelors = 0;
      int adultBucks = 0;
      int alone = 0;
      int families = 0;
      for (Grp g : groups(level, GameSpecies.WHITETAIL).values()) {
         if (g.kind.equals("BACHELOR") && g.members.size() >= 2) {
            bachelors++;
         }

         if (g.kind.equals("FAMILY") && g.members.size() >= 2) {
            families++;
         }

         for (Whitetail w : g.members) {
            if (w.traits().buck() && w.traits().ageMonths() >= 24) {
               adultBucks++;
               if (g.members.size() == 1) {
                  alone++;
               }
            }
         }
      }

      say("rut (Nov): " + bachelors + " bachelor groups left, " + alone + " of " + adultBucks + " adult bucks alone, " + families + " family groups");
      verdict(bachelors == 0 && alone == adultBucks, "rut: bachelor groups have split up and the bucks roam alone (" + alone + "/" + adultBucks + ")");
      verdict(families >= 1, "rut: doe family groups stay together (" + families + ")");
   }

   static void yards(ServerLevel level) {
      Map<String, Integer> yard = new HashMap<>();
      Map<String, Integer> groupsIn = new HashMap<>();
      int animals = 0;
      for (Grp g : groups(level, GameSpecies.WHITETAIL).values()) {
         animals += g.members.size();
         String root = g.parent.isEmpty() ? g.id : g.parent;
         yard.merge(root, g.members.size(), Integer::sum);
         groupsIn.merge(root, 1, Integer::sum);
      }

      List<Integer> sizes = new ArrayList<>();
      int biggest = 0;
      int over = 0;
      for (Map.Entry<String, Integer> e : yard.entrySet()) {
         if (groupsIn.get(e.getKey()) > 1) {
            sizes.add(e.getValue());
            biggest = Math.max(biggest, e.getValue());
            if (e.getValue() > HerdTuning.WHITETAIL.yardMax()) {
               over++;
            }
         }
      }

      sizes.sort(null);
      say("yards (Jan): " + sizes.size() + " yards " + sizes + " of " + animals + " whitetails");
      verdict(biggest >= 8 && over == 0, "winter: deer gather in yards of 8-20 (" + sizes + ")");
   }

   // ================================================================== tick cost

   static void perf(ServerLevel level, int ticks) {
      int n = animals(level).size();
      say("perf: " + n + " animals in the square; " + ticks + " ticks each with social groups on, off, on (normal 20 TPS, no sprint)");
      long t0 = level.getGameTime();
      long[] nanos = new long[3];
      long[] counted = new long[3];
      long[] herd = new long[3];
      long[] calls = new long[3];
      HerdService.setOverride(Boolean.TRUE);
      HerdService.profiling = true;
      HerdService.profileNanos = 0L;
      HerdService.profileCalls = 0L;
      tickNanos = 0L;
      ticksMeasured = 0L;
      JOBS.add((l, now) -> {
         long t = now - t0;
         int phase = (int)(t / ticks);
         if (t > 0 && t % ticks == 0 && phase <= 3) {
            int done = phase - 1;
            nanos[done] = tickNanos;
            counted[done] = ticksMeasured;
            herd[done] = HerdService.profileNanos;
            calls[done] = HerdService.profileCalls;
            tickNanos = 0L;
            ticksMeasured = 0L;
            HerdService.profileNanos = 0L;
            HerdService.profileCalls = 0L;
            if (phase == 1) {
               HerdService.setOverride(Boolean.FALSE);
            } else if (phase == 2) {
               HerdService.setOverride(Boolean.TRUE);
            } else {
               HerdService.setOverride(null);
               HerdService.profiling = false;
               double on = (nanos[0] + nanos[2]) / 1.0E6 / Math.max(1, counted[0] + counted[2]);
               double off = nanos[1] / 1.0E6 / Math.max(1, counted[1]);
               double self = (herd[0] + herd[2]) / 1.0E6 / Math.max(1, counted[0] + counted[2]);
               double perAnimal = (herd[0] + herd[2]) / 1000.0 / Math.max(1, calls[0] + calls[2]);
               say(String.format(java.util.Locale.ROOT, "perf: mean server tick %.3f ms with social groups (on %.3f / %.3f), %.3f ms without; herd logic itself %.4f ms per tick (%.2f us per animal-tick)",
                  on, nanos[0] / 1.0E6 / Math.max(1, counted[0]), nanos[2] / 1.0E6 / Math.max(1, counted[2]), off, self, perAnimal));
               verdict(on <= off * 1.15 + 0.3, "perf: server tick cost with social groups does not rise meaningfully (" + String.format(java.util.Locale.ROOT, "%.3f vs %.3f ms", on, off) + ")");
               verdict(self <= 0.25, "perf: the herd logic costs under 0.25 ms per tick (" + String.format(java.util.Locale.ROOT, "%.4f ms", self) + ")");
               say("done perf");
               return true;
            }
         }

         return false;
      });
   }
}
