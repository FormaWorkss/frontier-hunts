package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.Wilderness;
import com.formaworks.frontierhunts.camps.Tokens;
import com.formaworks.frontierhunts.firsthunt.BeginnerArea;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.licence.LicenceStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;

/**
 * [1.2.7] The first hunt, step by step, on a dedicated server: /fhqa flow begin | status | permit | area | another | empty |
 * quiet | wind | signs | hide | claim. Uses the fake hunter FHQA on the player list (see {@link Persist}); the shot, track
 * and harvest use the real arrow / tag / skinning path of {@code /fhqa persist harvest ...}.
 */
final class FlowTest {
   private FlowTest() {
   }

   static final List<java.util.UUID> herd = new ArrayList<>();

   static void say(String s) {
      FrontierQa.say("flow " + s);
   }

   static void verdict(boolean ok, String what) {
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static CompoundTag data(Player p) {
      return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("frontierhunts_firsthunt");
   }

   static FirstHunt.Step step(Player p) {
      return FirstHunt.current(data(p).getInt("done"));
   }

   static int run(CommandSourceStack src, String args) {
      MinecraftServer server = src.getServer();
      String[] a = args.trim().split("\\s+");
      try {
         var fp = FrontierQa.fake(server);
         ServerLevel level = fp.serverLevel();
         switch (a[0]) {
            case "begin" -> {
               Persist.join(server, true);
               CompoundTag d = data(fp);
               verdict(d.getBoolean("started") && d.getBoolean("intro") && step(fp) == FirstHunt.Step.PERMIT,
                  "a new hunter given the Handbook has one objective: 'Start your first hunt' (started " + d.getBoolean("started") + ", intro " + d.getBoolean("intro")
                     + ", step " + step(fp) + ")");
               status(fp);
            }
            case "status" -> status(fp);
            case "snowmobile" -> { // [1.2.8] get off, sneak + use the empty machine: it must stay in the world (and not land in the pack)
               fp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // a survival hunter (creative breaks it without a drop, by design)
               fp.stopRiding(); // on its own feet at spawn, whatever earlier tests (sled runs) left it doing
               BlockPos spawn = level.getSharedSpawnPos();
               level.getChunkAt(spawn);
               fp.moveTo(spawn.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ()), spawn.getZ() + 0.5, 0f, 0f);
               var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:snowmobile"));
               var e = type.create(level);
               e.moveTo(fp.getX() + 2, fp.getY(), fp.getZ(), 0f, 0f);
               level.addFreshEntity(e);
               fp.setShiftKeyDown(true); // just got off: still holding sneak (a fake player can't ride, so it starts beside it)
               String inv0 = Persist.inventory(fp);
               var r = fp.interactOn(e, net.minecraft.world.InteractionHand.MAIN_HAND);
               boolean alive = !e.isRemoved();
               verdict(alive && Persist.inventory(fp).equals(inv0), "snowmobile: sneak + use on the empty machine -> " + r
                  + ", still in the world " + alive + ", pack unchanged " + Persist.inventory(fp).equals(inv0));
               fp.setShiftKeyDown(false);
               boolean hurt = e.hurt(level.damageSources().playerAttack(fp), 1F);
               verdict(!e.isRemoved(), "snowmobile: one hit doesn't wreck it (still there " + !e.isRemoved() + ")");
               for (int i = 0; i < 4 && !e.isRemoved(); i++) {
                  e.hurt(level.damageSources().playerAttack(fp), 1F);
               }
               int dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, e.getBoundingBox().inflate(3), ie -> ie.getItem().is(
                  BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:snowmobile")))).size();
               verdict(e.isRemoved() && dropped == 1, "snowmobile: a few hits break it into its item (dropped " + dropped + ")");
            }
            case "ticktest" -> {
               int[] n = new int[2];
               NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, e -> {
                  if (e.getEntity() == fp) {
                     n[0]++;
                  }
               });
               int tc = fp.tickCount;
               try {
                  for (int i = 0; i < 40; i++) {
                     fp.doTick();
                     n[1]++;
                  }
               } catch (Throwable t) {
                  FrontierQa.fail("doTick", t);
               }
               say("doTick x" + n[1] + ": PlayerTickEvent.Post fired " + n[0] + " times, tickCount " + tc + " -> " + fp.tickCount + ", alive " + fp.isAlive()
                  + ", connection " + fp.connection + ", spectator " + fp.isSpectator() + ", health " + fp.getHealth());
            }
            case "permit" -> { // [1.2.9] no free permit: the licence is the whole Ranger Academy, a deer tag is bought
               String inv0 = Persist.inventory(fp);
               FirstHunt.action(fp, FirstHuntNetwork.A_PERMIT);
               var before = LicenceStatus.of(fp, "whitetail");
               verdict(Persist.inventory(fp).equals(inv0) && !before.licence() && !before.tag(),
                  "no free starter permit: asking for one gives nothing (licence " + before.licence() + ", tag " + before.tag() + ")");
               String rTok = com.formaworks.frontierhunts.licence.LicenceOffice.refusal(fp, com.formaworks.frontierhunts.licence.LicenceOffice.WHAT_LICENCE, 0,
                  com.formaworks.frontierhunts.licence.LicenceOffice.PAY_TOKENS, true);
               String rFree = com.formaworks.frontierhunts.licence.LicenceOffice.refusal(fp, com.formaworks.frontierhunts.licence.LicenceOffice.WHAT_LICENCE, 0,
                  com.formaworks.frontierhunts.licence.LicenceOffice.PAY_FREE, true);
               verdict("academy".equals(rTok) && "academy".equals(rFree), "the licence is never sold and not free before the whole Academy: tokens -> " + rTok
                  + ", free -> " + rFree);
               var store = com.formaworks.frontierhunts.academy.TrainingStore.get(fp.server);
               var all = com.formaworks.frontierhunts.academy.Course.curriculum();
               for (int i = 0; i < all.length - 1; i++) {
                  store.record(fp.getUUID(), all[i]).passes = 1;
               }
               boolean early = com.formaworks.frontierhunts.licence.LicenceOffice.graduate(fp);
               verdict(!early && !LicenceStatus.of(fp, "whitetail").licence(), (all.length - 1) + " of " + all.length
                  + " Academy courses passed (archery included): still no licence");
               store.record(fp.getUUID(), all[all.length - 1]).passes = 1;
               boolean issued = com.formaworks.frontierhunts.licence.LicenceOffice.graduate(fp);
               var mid = LicenceStatus.of(fp, "whitetail");
               verdict(issued && mid.licence() && !mid.tag(), "whole Academy passed: the Academy issues the licence (" + mid.licence() + "), no tag with it ("
                  + mid.tag() + ")");
               verdict(!com.formaworks.frontierhunts.licence.LicenceOffice.graduate(fp), "one licence a season (asked again: nothing more)");
               int deer = com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER.ordinal();
               String rTag = com.formaworks.frontierhunts.licence.LicenceOffice.refusal(fp, com.formaworks.frontierhunts.licence.LicenceOffice.WHAT_TAG, deer,
                  com.formaworks.frontierhunts.licence.LicenceOffice.PAY_FREE, true);
               verdict(rTag != null, "a deer tag is never free: " + rTag);
               Tokens.credit(level, fp.getUUID(), 50);
               com.formaworks.frontierhunts.licence.LicenceOffice.request(fp, com.formaworks.frontierhunts.licence.LicenceOffice.WHAT_TAG, deer,
                  com.formaworks.frontierhunts.licence.LicenceOffice.PAY_TOKENS, true);
               fp.getInventory().add(new net.minecraft.world.item.ItemStack(com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get())); // crafted
               var after = LicenceStatus.of(fp, "whitetail");
               verdict(after.licence() && after.tag(), "a deer tag bought for tokens at a counter: licence " + after.licence() + ", tag " + after.tag() + ", pack "
                  + Persist.inventory(fp));
               status(fp);
            }
            case "herd" -> herd(level, fp.blockPosition().offset(140, 0, 10), 3); // whitetails ~140 blocks east
            case "herd2" -> herd(level, fp.blockPosition().offset(-90, 0, -120), 2); // a second group far north-west
            // [fharea] the beginner area is an easy spot (AreaTest)
            case "easyherd" -> AreaTest.herd(fp, level, false);
            case "easyherd2" -> AreaTest.herd(fp, level, true);
            case "easy" -> AreaTest.easy(fp, level, a.length > 1 ? a[1] : "assigned");
            case "rough" -> AreaTest.rough(fp, level);
            case "legacy" -> AreaTest.legacy(fp, level);
            case "tiers" -> AreaTest.tiers(level);
            case "area" -> area(fp, false); // the area is assigned near them, as a broad circle, never on them
            case "another" -> { // the hunter asks for another area: somewhere else
               CompoundTag d = data(fp);
               int ox = d.getInt("areaX"), oz = d.getInt("areaZ");
               FirstHunt.action(fp, FirstHuntNetwork.A_NEW_AREA);
               d = data(fp);
               double moved = Math.hypot(d.getInt("areaX") - ox, d.getInt("areaZ") - oz);
               boolean onlyOne = !AreaTest.secondPlaced; // [1.3.0] no second easy patch in this world: it must stay on easy land, not wander off it
               verdict(d.getBoolean("area") && (moved >= 100 || onlyOne && BeginnerArea.judge(level, d.getInt("areaX"), d.getInt("areaZ"), 64).ok),
                  (onlyOne ? "(one easy patch in reach) " : "") + "'Find another area': new area " + d.getInt("areaX") + "," + d.getInt("areaZ") + " is " + (int)moved
                  + " blocks from the old one");
               FirstHunt.action(fp, FirstHuntNetwork.A_NEW_AREA);
               d = data(fp);
               verdict(Math.hypot(d.getInt("areaX") - ox, d.getInt("areaZ") - oz) == moved, "asking again at once is rate-limited (area unchanged)");
            }
            case "empty" -> { // every test deer gone: the hunter stands in the area, a minute passes, the area is called quiet
               // an area 2,000 blocks out where no whitetail has ever been seen (no home ranges): the assigned animals have all gone
               herd.clear();
               CompoundTag d = data(fp);
               d.putInt("areaX", fp.getBlockX() + 2000);
               d.putInt("areaZ", fp.getBlockZ() + 2000);
               d.putInt("areaV", 2); // [fharea] an area whose ground was checked: this test is about its deer leaving, not its ground
               level.getChunkAt(new BlockPos(d.getInt("areaX"), 0, d.getInt("areaZ")));
               int gone = 0;
               for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, new net.minecraft.world.phys.AABB(new BlockPos(d.getInt("areaX"), 100, d.getInt("areaZ"))).inflate(400, 200, 400))) {
                  w.discard();
                  gone++;
               }
               say("area moved to " + d.getInt("areaX") + "," + d.getInt("areaZ") + ", " + gone + " whitetails there removed");
               fp.moveTo(d.getInt("areaX") + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, d.getInt("areaX"), d.getInt("areaZ")),
                  d.getInt("areaZ") + 0.5, 0f, 0f);
               level.getChunkAt(fp.blockPosition());
               int liveDeer = level.getEntitiesOfClass(Whitetail.class, fp.getBoundingBox().inflate(130, 96, 130)).size();
               tick(fp, 1300);
               d = data(fp);
               verdict(d.getBoolean("areaQuiet") || liveDeer > 0, "an area that went empty is flagged quiet after a minute in it, so the hunter can ask for another (quiet "
                  + d.getBoolean("areaQuiet") + ", other whitetails within 130 blocks " + liveDeer + ")");
               if (liveDeer == 0) {
                  BlockPos keep = fp.blockPosition();
                  FirstHunt.action(fp, FirstHuntNetwork.A_SYNC);
                  var assign = FirstHunt.class.getDeclaredMethod("assignArea", ServerPlayer.class, CompoundTag.class, boolean.class);
                  assign.setAccessible(true);
                  boolean got = (boolean)assign.invoke(null, fp, data(fp), true);
                  verdict(got || data(fp).getBoolean("scouting"), "no live whitetails near: an area only from established home ranges, else 'scout' and no marker (assigned " + got + ", scouting "
                     + data(fp).getBoolean("scouting") + ") at " + keep.toShortString());
               }
            }
            case "home" -> {
               BlockPos sp = level.getSharedSpawnPos();
               fp.moveTo(sp.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ()), sp.getZ() + 0.5, 0f, 0f);
               say("FHQA back at " + fp.blockPosition().toShortString());
            }
            case "signs" -> {
               FirstHunt.signRead(fp, null); // what TrailService.inspect calls when the hunter reads a track, rub or scrape
               verdict((data(fp).getInt("done") & FirstHunt.Step.SIGNS.bit()) != 0, "reading a deer sign completes 'Find signs' (step now " + step(fp) + ")");
            }
            case "wind" -> windSpawn(fp, level, a.length > 1 && a[1].equals("up"));
            case "windcheck" -> windCheck(fp, level, a.length > 1 && a[1].equals("up"));
            case "hide" -> {
               FirstHunt.action(fp, FirstHuntNetwork.A_HIDE);
               boolean hidden = data(fp).getBoolean("hidden");
               FirstHunt.action(fp, FirstHuntNetwork.A_SHOW);
               verdict(hidden && !data(fp).getBoolean("hidden"), "the step card can be hidden and shown again");
            }
            case "track" -> {
               Whitetail w = Persist.deer(level);
               if (w != null) {
                  fp.moveTo(w.getX() + 2.5, w.getY(), w.getZ(), 0f, 0f);
                  tick(fp, 60);
                  verdict((data(fp).getInt("done") & FirstHunt.Step.TRACK.bit()) != 0, "walking up on the downed deer completes 'Track' (step now " + step(fp) + ")");
               }
            }
            case "claim" -> {
               int t0 = Tokens.balance(level, fp.getUUID());
               int arrows0 = arrows(fp);
               boolean atClaim = step(fp) == FirstHunt.Step.CLAIM;
               FirstHunt.action(fp, FirstHuntNetwork.A_CLAIM);
               int t1 = Tokens.balance(level, fp.getUUID());
               verdict(atClaim && t1 - t0 == FirstHunt.REWARD_TOKENS && arrows(fp) - arrows0 == 8 && step(fp) == FirstHunt.Step.DONE,
                  "claim the reward: +" + (t1 - t0) + " tokens, +" + (arrows(fp) - arrows0) + " field arrows, step now " + step(fp));
               FirstHunt.action(fp, FirstHuntNetwork.A_CLAIM);
               verdict(Tokens.balance(level, fp.getUUID()) == t1, "claiming twice pays nothing");
            }
            default -> say("FAIL unknown flow command " + a[0]);
         }
      } catch (Throwable t) {
         FrontierQa.fail("flow " + args, t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t);
      }
      return 1;
   }

   static int arrows(ServerPlayer p) {
      int n = 0;
      var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("frontierhunts:field_arrow"));
      for (var s : p.getInventory().items) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      return n;
   }

   /** the paperwork the first-hunt screen shows, from the same read-only check */
   static void status(ServerPlayer p) {
      var st = LicenceStatus.of(p, "whitetail");
      say("paperwork: regulated " + st.regulated() + ", licence " + st.licence() + ", tag " + st.tag() + ", season open " + st.open() + " (opens in "
         + st.daysToOpen() + " days), tags left " + st.tagsLeft() + ", legal now " + st.legal() + "; step " + step(p) + ", record " + data(p));
   }

   static void herd(ServerLevel level, BlockPos near, int n) {
      @SuppressWarnings("unchecked")
      EntityType<Whitetail> type = (EntityType<Whitetail>)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:whitetail"));
      int cx = near.getX() >> 4, cz = near.getZ() >> 4;
      for (int dx = -2; dx <= 2; dx++) {
         for (int dz = -2; dz <= 2; dz++) {
            level.setChunkForced(cx + dx, cz + dz, true);
         }
      }
      for (int i = 0; i < n; i++) {
         int x = near.getX() + i * 3, z = near.getZ() + i * 2;
         Whitetail w = type.spawn(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z), MobSpawnType.COMMAND);
         if (w != null) {
            herd.add(w.getUUID());
         }
      }
      say(herd.size() + " test whitetails near " + near.toShortString());
   }

   static void area(ServerPlayer p, boolean another) throws Exception {
      CompoundTag d = data(p);
      d.putLong("areaRetry", 0L);
      ServerLevel level = p.serverLevel();
      var seen = level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(320.0, 96.0, 320.0));
      StringBuilder sp = new StringBuilder();
      for (Whitetail w : seen) {
         sp.append(w.species()).append('@').append(w.blockPosition().toShortString()).append(' ');
      }
      say("game time " + level.getGameTime() + ", whitetails within 320: " + seen.size() + " [" + sp + "], step " + step(p));
      tick(p, 40);
      d = data(p);
      say("record after 2 s: " + d);
      int ax = d.getInt("areaX"), az = d.getInt("areaZ"), r = d.getInt("areaR");
      double nearest = Double.MAX_VALUE;
      for (Whitetail w : level.getEntitiesOfClass(Whitetail.class, p.getBoundingBox().inflate(330.0, 100.0, 330.0))) {
         nearest = Math.min(nearest, Math.hypot(w.getX() - ax, w.getZ() - az));
      }
      verdict(d.getBoolean("area") && r == 64 && nearest >= 10 && nearest <= r, "beginner area assigned where whitetails are: centre " + ax + "," + az + " r " + r
         + ", nearest live whitetail " + (int)nearest + " blocks from the centre (inside the circle, not on it), advice #" + d.getInt("advice") + ", "
         + (int)Math.hypot(ax - p.getX(), az - p.getZ()) + " blocks from the hunter");
   }

   /** ticks the fake hunter the way a connection would (its tick count runs with it) */
   static void tick(ServerPlayer p, int n) {
      for (int i = 0; i < n; i++) {
         int before = p.tickCount;
         try {
            p.doTick();
         } catch (Throwable t) {
            if (i == 0) {
               FrontierQa.fail("tick", t);
            }
         }
         if (p.tickCount == before) {
            p.tickCount++;
         }
      }
   }

   static java.util.UUID windDeer;

   static Wilderness.Wind wind(ServerLevel level) {
      return Wilderness.wind(level.getSeed(), level.getGameTime(), level.isRaining(), level.isThundering());
   }

   /** a calm whitetail 12-20 blocks off, on about the hunter's level: downwind of the hunter ("up": the hunter is upwind) or upwind of him */
   static void windSpawn(ServerPlayer p, ServerLevel level, boolean hunterUpwind) {
      Wilderness.Wind wind = wind(level);
      double ws = Math.hypot(wind.east(), wind.south());
      double ux = ws < 1e-6 ? 1 : wind.east() / ws, uz = ws < 1e-6 ? 0 : wind.south() / ws;
      if (!hunterUpwind) {
         double up = Wilderness.scent(wind, 16 * ux, 16 * uz, false, false); // deer - hunter along the wind: the hunter is upwind
         double down = Wilderness.scent(wind, -16 * ux, -16 * uz, false, false);
         verdict(ws < 0.6 || up > down, "the deer's nose agrees with the step: scent from upwind " + String.format("%.3f", up) + " > from downwind "
            + String.format("%.3f", down) + " (wind toward " + String.format("%.2f,%.2f", wind.east(), wind.south()) + ", speed " + String.format("%.2f", ws) + ")");
      }
      double sgn = hunterUpwind ? 1 : -1;
      BlockPos at = p.blockPosition();
      BlockPos best = null;
      int bestDy = Integer.MAX_VALUE;
      for (int dist = 12; dist <= 20; dist += 2) {
         for (int deg = -20; deg <= 20; deg += 10) {
            double r = Math.toRadians(deg);
            double dx = sgn * (ux * Math.cos(r) - uz * Math.sin(r)), dz = sgn * (ux * Math.sin(r) + uz * Math.cos(r));
            int x = at.getX() + (int)Math.round(dx * dist), z = at.getZ() + (int)Math.round(dz * dist);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - at.getY()) < bestDy) {
               bestDy = Math.abs(y - at.getY());
               best = new BlockPos(x, y, z);
            }
         }
      }
      if (windDeer != null && level.getEntity(windDeer) != null) {
         level.getEntity(windDeer).discard();
      }
      @SuppressWarnings("unchecked")
      EntityType<Whitetail> type = (EntityType<Whitetail>)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:whitetail"));
      Whitetail w = type.spawn(level, best, MobSpawnType.COMMAND);
      windDeer = w == null ? null : w.getUUID();
      say("whitetail placed " + (hunterUpwind ? "downwind of the hunter (hunter upwind)" : "upwind of the hunter (hunter downwind)") + " at " + best.toShortString()
         + ", " + (int)Math.sqrt(best.distSqr(at)) + " blocks, height difference " + bestDy);
   }

   static void windCheck(ServerPlayer p, ServerLevel level, boolean hunterUpwind) {
      int before = data(p).getInt("done");
      tick(p, 100);
      boolean counted = (data(p).getInt("done") & FirstHunt.Step.WIND.bit()) != 0;
      Whitetail w = windDeer == null ? null : (Whitetail)level.getEntity(windDeer);
      double ws = Math.hypot(wind(level).east(), wind(level).south());
      String deer = w == null ? "gone" : "alertness " + String.format("%.2f", w.alertness()) + ", " + (int)w.distanceTo(p) + " blocks";
      if (hunterUpwind) {
         verdict(ws < 0.6 || !counted || (before & FirstHunt.Step.WIND.bit()) != 0, "standing upwind of a whitetail for 5 s does not complete 'Approach with the wind' ("
            + deer + ")");
      } else {
         verdict(counted, "downwind of a calm whitetail for a few seconds completes 'Approach with the wind' (step now " + step(p) + ", " + deer + ")");
      }
      if (w != null) {
         w.discard();
      }
   }
}
