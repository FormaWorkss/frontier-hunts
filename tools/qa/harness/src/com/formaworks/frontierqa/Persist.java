package com.formaworks.frontierqa;

import com.formaworks.frontierhunts.academy.Academy;
import com.formaworks.frontierhunts.academy.Course;
import com.formaworks.frontierhunts.academy.TrainingStore;
import com.formaworks.frontierhunts.camps.Tokens;
import com.formaworks.frontierhunts.expedition.Campaign;
import com.formaworks.frontierhunts.expedition.CampaignProgress;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.firsthunt.FirstHunt;
import com.formaworks.frontierhunts.firsthunt.FirstHuntNetwork;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.licence.LicenceOffice;
import com.formaworks.frontierhunts.licence.LicenceStore;
import com.formaworks.frontierhunts.licence.LicenceTime;
import com.formaworks.frontierhunts.licence.Regulations;
import com.formaworks.frontierhunts.progression.Durability;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * [1.2.7] Progress-survives-interruptions test. Drives the fake hunter FHQA through each milestone the way a client would
 * (licence bought at a counter, academy course passed, a whitetail shot / tagged / skinned, a contract paid, the first
 * hunt claimed), with FHQA put on the server's player list so the game saves it exactly like a connected player.
 *
 * <pre>
 *   /fhqa persist join            load FHQA's save (as a login does), add to the player list, fire the login event
 *   /fhqa persist load            the same without the login event (to check the save before login code touches it)
 *   /fhqa persist login           fire the login event (starter kit, queued rewards, first-hunt offer)
 *   /fhqa persist licence | permit | contract | claim     one milestone
 *   /fhqa persist harvest spawn | tag | done                the deer, in three steps (it must fall and settle between them)
 *   /fhqa persist snapshot        write the expected state to the world folder
 *   /fhqa persist check           compare the loaded state with the snapshot
 *   /fhqa persist again           try every claim a second time: nothing may pay twice
 *   /fhqa persist disconnect      a hunter who left while their claims were still queued gets nothing (and a control who stayed does)
 *   /fhqa persist leave           log out (save the player file, as PlayerList.remove does)
 *   /fhqa persist crash           kill the JVM (Runtime.halt) as soon as no progress save is pending
 *   /fhqa persist nocommit        switch Durability's commit saves off (control run: the old behaviour)
 * </pre>
 * Every verdict line is "[FHQA] persist PASS|FAIL ...".
 */
final class Persist {
   private Persist() {
   }

   static final String SNAP = "fhqa_persist.nbt";
   static boolean armed;
   static boolean listening;
   static UUID deer;
   static long contractSerial;

   static void say(String s) {
      FrontierQa.say("persist " + s);
   }

   static void verdict(boolean ok, String what) {
      say((ok ? "PASS " : "FAIL ") + what);
   }

   static int run(CommandSourceStack src, String args) {
      MinecraftServer server = src.getServer();
      String[] a = args.trim().split("\\s+");
      try {
         switch (a[0]) {
            case "join" -> join(server, true);
            case "load" -> join(server, false);
            case "login" -> {
               FakePlayer fp = FrontierQa.fake(server);
               EventHooks.firePlayerLoggedIn(fp);
               say("login event fired: tokens " + Tokens.balance(server.overworld(), fp.getUUID()) + ", inventory " + inventory(fp));
            }
            case "licence" -> licence(server);
            case "permit" -> {
               FakePlayer fp = FrontierQa.fake(server);
               FirstHunt.action(fp, FirstHuntNetwork.A_PERMIT);
               say("intro permit asked: inventory " + inventory(fp));
            }
            case "harvest" -> harvest(server, a.length > 1 ? a[1] : "spawn");
            case "contract" -> contract(server, FrontierQa.fake(server), true);
            case "claim" -> {
               FakePlayer fp = FrontierQa.fake(server);
               int before = Tokens.balance(server.overworld(), fp.getUUID());
               FirstHunt.action(fp, FirstHuntNetwork.A_CLAIM);
               int after = Tokens.balance(server.overworld(), fp.getUUID());
               verdict(after - before == FirstHunt.REWARD_TOKENS, "first hunt reward paid: +" + (after - before) + " tokens, record " + firstHunt(fp));
            }
            case "status" -> say("state " + snapshot(server, FrontierQa.fake(server)));
            case "snapshot" -> {
               CompoundTag s = snapshot(server, FrontierQa.fake(server));
               NbtIo.write(s, file(server));
               say("snapshot written " + s);
            }
            case "check" -> check(server);
            case "again" -> again(server);
            case "disconnect" -> disconnect(server);
            case "leave" -> leave(server);
            case "crash" -> {
               armed = true;
               listen();
               say("crash armed: the JVM halts at the end of the first tick with no progress save pending (pending now: " + Durability.pending() + ")");
            }
            case "nocommit" -> {
               Durability.disabled = true;
               say("Durability commit saves OFF (control run)");
            }
            default -> say("FAIL unknown persist command " + a[0]);
         }
      } catch (Throwable t) {
         FrontierQa.fail("persist " + args, t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t);
      }
      return 1;
   }

   // ============================================================================================ the hunter on the player list

   @SuppressWarnings("unchecked")
   static void join(MinecraftServer server, boolean login) throws Exception {
      FakePlayer fp = FrontierQa.fake(server);
      PlayerList pl = server.getPlayerList();
      boolean had = pl.load(fp).isPresent(); // what PlayerList.placeNewPlayer does first: read the player file
      if (!had) {
         BlockPos sp = server.overworld().getSharedSpawnPos();
         int y = server.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
         fp.moveTo(sp.getX() + 0.5, y, sp.getZ() + 0.5, 0f, 0f);
      }
      var players = (List<ServerPlayer>)field(PlayerList.class, "players").get(pl);
      var byId = (java.util.Map<UUID, ServerPlayer>)field(PlayerList.class, "playersByUUID").get(pl);
      if (!players.contains(fp)) {
         players.add(fp);
      }
      byId.put(fp.getUUID(), fp);
      say("FHQA joined (save file " + (had ? "loaded" : "none: a new hunter") + ") at " + fp.blockPosition().toShortString() + " in "
         + fp.level().dimension().location() + ", tokens " + Tokens.balance(server.overworld(), fp.getUUID()) + ", inventory " + inventory(fp));
      if (login) {
         EventHooks.firePlayerLoggedIn(fp);
         say("login event fired: tokens " + Tokens.balance(server.overworld(), fp.getUUID()) + ", first hunt " + firstHunt(fp) + ", inventory "
            + inventory(fp));
      }
   }

   /** what PlayerList.remove does for a leaving player: the logout event, then the player file is written */
   @SuppressWarnings("unchecked")
   static void leave(MinecraftServer server) throws Exception {
      FakePlayer fp = FrontierQa.fake(server);
      EventHooks.firePlayerLoggedOut(fp);
      var save = PlayerList.class.getDeclaredMethod("save", ServerPlayer.class);
      save.setAccessible(true);
      save.invoke(server.getPlayerList(), fp);
      ((List<ServerPlayer>)field(PlayerList.class, "players").get(server.getPlayerList())).remove(fp);
      ((java.util.Map<UUID, ServerPlayer>)field(PlayerList.class, "playersByUUID").get(server.getPlayerList())).remove(fp.getUUID());
      say("FHQA logged out: player file written (" + inventory(fp).length() + " chars of inventory), world books not saved by the logout");
   }

   static java.lang.reflect.Field field(Class<?> c, String name) throws Exception {
      var f = c.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   static void listen() {
      if (listening) {
         return;
      }
      listening = true;
      NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, ServerTickEvent.Post.class, e -> {
         if (armed && !Durability.pending()) {
            String msg = "[FHQA] persist HALT now (commit saves this run: " + Durability.saves + ")";
            System.out.println(msg);
            FrontierQa.LOG.info(msg.substring(7));
            System.out.flush();
            Runtime.getRuntime().halt(137);
         }
      });
   }

   // ============================================================================================ milestones

   static void licence(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      ServerLevel level = fp.serverLevel();
      Tokens.credit(level, fp.getUUID(), 600);
      BlockPos at = fp.blockPosition().offset(2, 0, 0);
      Block board = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("frontierhunts:expedition_board"));
      level.setBlockAndUpdate(at, board.defaultBlockState());
      // [1.2.9] the licence is the whole Ranger Academy (never sold); a deer tag is bought at the counter
      var training = com.formaworks.frontierhunts.academy.TrainingStore.get(server);
      for (var c : com.formaworks.frontierhunts.academy.Course.curriculum()) {
         training.record(fp.getUUID(), c).passes = Math.max(1, training.record(fp.getUUID(), c).passes);
      }
      training.setDirty();
      if (count(fp, com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get()) == 0) { // [1.2.9] crafted (no starter permit hands one out)
         fp.getInventory().add(new ItemStack(com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get()));
      }
      boolean issued = LicenceOffice.graduate(fp);
      int before = Tokens.balance(level, fp.getUUID());
      LicenceOffice.request(fp, LicenceOffice.WHAT_TAG, com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER.ordinal(), LicenceOffice.PAY_TOKENS);
      int after = Tokens.balance(level, fp.getUUID());
      LicenceStore.Season s = season(fp);
      boolean item = count(fp, com.formaworks.frontierhunts.licence.LicenceContent.LICENCE.get()) > 0;
      boolean tag = count(fp, com.formaworks.frontierhunts.licence.LicenceContent.tag(com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER)) > 0;
      verdict(s != null && s.licence && item && tag && after < before, "Academy licence issued " + issued + " and a deer tag bought at the counter: " + before
         + " -> " + after + " tokens, record " + (s != null && s.licence) + ", licence in pack " + item + ", tag in pack " + tag + ", commit pending "
         + Durability.pending());
   }

   static void harvest(MinecraftServer server, String step) {
      FakePlayer fp = FrontierQa.fake(server);
      ServerLevel level = fp.serverLevel();
      switch (step) {
         case "spawn" -> {
            @SuppressWarnings("unchecked")
            EntityType<Whitetail> type = (EntityType<Whitetail>)BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("frontierhunts:whitetail"));
            BlockPos p = fp.blockPosition().offset(6, 0, 6);
            p = new BlockPos(p.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()), p.getZ());
            Whitetail w = type.spawn(level, p, MobSpawnType.COMMAND);
            if (w == null) {
               say("FAIL no whitetail spawned");
               return;
            }
            deer = w.getUUID();
            int cx = p.getX() >> 4, cz = p.getZ() >> 4; // keep the ground around the test deer loaded and ticking wherever it runs
            for (int dx = -5; dx <= 5; dx++) {
               for (int dz = -5; dz <= 5; dz++) {
                  level.setChunkForced(cx + dx, cz + dz, true);
               }
            }
            say("whitetail " + deer + " spawned at " + p.toShortString() + ", first hunt " + firstHunt(fp));
         }
         case "shoot" -> {
            // a real field arrow, loosed by FHQA broadside from 5 blocks: the arrow flies and hits under the game's own physics
            Whitetail w = deer(level);
            if (w == null) {
               return;
            }
            if (w.downed() || !w.isAlive()) {
               say("whitetail already down: no shot");
               return;
            }
            float yaw = w.yBodyRot * ((float)Math.PI / 180F);
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
            Vec3 chest = w.position().add(fwd.scale(w.getBbWidth() * 0.25)).add(0, w.getBbHeight() * 0.55, 0).add(w.getDeltaMovement().scale(2.0));
            // a clear line: either flank, 3-6 m out, the arrow starting in open air with nothing between it and the chest
            Vec3 from = null;
            for (double dist : new double[]{5.0, 4.0, 6.0, 3.0}) {
               for (int sgn : new int[]{1, -1}) {
                  Vec3 f = chest.add(side.scale(sgn * dist)).add(0, 0.3, 0);
                  boolean air = level.getBlockState(BlockPos.containing(f)).getCollisionShape(level, BlockPos.containing(f)).isEmpty();
                  var clip = level.clip(new net.minecraft.world.level.ClipContext(f, chest, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                     net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
                  if (from == null && air && clip.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                     from = f;
                  }
               }
            }
            if (from == null) {
               from = chest.add(side.scale(5.0)).add(0, 0.3, 0);
               say("no clear line to the deer from either flank: shooting anyway");
            }
            // (the hunter stays where they stand, ~10 m off, so the deer stays calm: only the arrow starts close, for a sure broadside line)
            @SuppressWarnings("unchecked")
            var type = (EntityType<? extends com.formaworks.frontierhunts.hunting.FieldArrow>)com.formaworks.frontierhunts.hunting.HuntEntities.ARROW.get();
            var arrow = new com.formaworks.frontierhunts.hunting.FieldArrow(type, level);
            arrow.setOwner(fp);
            arrow.setPos(from.x, from.y, from.z);
            arrow.setLaunch(arrow.position());
            Vec3 d = chest.subtract(from);
            arrow.shoot(d.x, d.y, d.z, 2.75F, 0.0F);
            boolean added = level.addFreshEntity(arrow);
            say("arrow loosed at the whitetail from " + String.format("%.1f", from.distanceTo(chest)) + " m (added " + added + "), deer at "
               + w.blockPosition().toShortString());
         }
         case "hit" -> {
            Whitetail w = deer(level);
            if (w == null) {
               return;
            }
            String region = "";
            try {
               region = String.valueOf(field(Whitetail.class, "shotRegion").get(w));
            } catch (Exception ignored) {
            }
            String dress = "";
            try {
               @SuppressWarnings("unchecked")
               var acc = (net.minecraft.network.syncher.EntityDataAccessor<Integer>)field(Whitetail.class, "DRESS_DURATION").get(null);
               dress = ", skinning " + field(Whitetail.class, "dressingTicks").getInt(w) + "/" + w.getEntityData().get(acc) + " by "
                  + field(Whitetail.class, "dressingHunter").get(w) + ", hunter " + String.format("%.1f", Math.sqrt(w.distanceToSqr(fp))) + " m away holding "
                  + fp.getMainHandItem() + ", on player list " + (server.getPlayerList().getPlayer(fp.getUUID()) == fp);
            } catch (Exception ex) {
               dress = ", " + ex;
            }
            say("whitetail at " + w.blockPosition().toShortString() + ": region " + region + ", downed " + w.downed() + ", alive " + w.isAlive() + ", health "
               + w.getHealth() + ", first hunt " + firstHunt(fp) + dress);
         }
         case "tag" -> {
            Whitetail w = deer(level);
            if (w == null) {
               return;
            }
            say("whitetail at " + w.blockPosition().toShortString() + " downed " + w.downed() + " alive " + w.isAlive());
            fp.moveTo(w.getX() + 1.4, w.getY(), w.getZ(), fp.getYRot(), fp.getXRot()); // (a fake player ignores teleportTo: its connection drops the teleport)
            fp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, w.position().add(0, 0.4, 0));
            if (!select(fp, com.formaworks.frontierhunts.licence.LicenceContent.tag(Regulations.TagKind.DEER))) {
               say("no deer tag in the pack: " + inventory(fp));
            }
            w.interact(fp, InteractionHand.MAIN_HAND);
            boolean tagged = w.getPersistentData().getCompound(com.formaworks.frontierhunts.licence.Tagging.KEY).getBoolean("done");
            if (!select(fp, com.formaworks.frontierhunts.HuntContent.SKINNING_TOOL.get())) {
               say("no skinning knife in the pack: " + inventory(fp));
            }
            w.interact(fp, InteractionHand.MAIN_HAND);
            say("tagged " + tagged + ", skinning started; deer data " + w.getPersistentData());
         }
         default -> { // done
            Whitetail w = deer(level);
            int harvests = JournalApi.counter(fp, "harvests");
            boolean harvested = w != null && w.getPersistentData() != null && w.toString() != null && harvestedFlag(w);
            verdict(harvested && harvests > 0, "whitetail skinned by FHQA: harvested " + harvested + ", journal harvests " + harvests + ", first hunt "
               + firstHunt(fp) + ", commit pending " + Durability.pending());
         }
         case "pickup" -> {
            // pick up what the carcass dropped (the hunter walks over it)
            for (var ie : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, fp.getBoundingBox().inflate(8))) {
               ItemStack st = ie.getItem().copy();
               if (fp.getInventory().add(st)) {
                  ie.discard();
               }
            }
            say("after pickup: " + inventory(fp));
         }
      }
   }

   static boolean harvestedFlag(Whitetail w) {
      try {
         return field(Whitetail.class, "harvested").getBoolean(w);
      } catch (Exception e) {
         return false;
      }
   }

   static Whitetail deer(ServerLevel level) {
      if (deer == null || !(level.getEntity(deer) instanceof Whitetail w)) {
         say("FAIL the test whitetail is gone");
         return null;
      }
      return w;
   }

   static boolean select(ServerPlayer p, net.minecraft.world.item.Item item) {
      var inv = p.getInventory();
      for (int i = 0; i < inv.items.size(); i++) {
         if (inv.items.get(i).is(item)) {
            if (i < 9) {
               inv.selected = i;
            } else {
               ItemStack hot = inv.items.get(0);
               inv.items.set(0, inv.items.get(i));
               inv.items.set(i, hot);
               inv.selected = 0;
            }
            return true;
         }
      }
      return false;
   }

   static boolean contract(MinecraftServer server, ServerPlayer p, boolean report) {
      ServerLevel level = server.overworld();
      ExpeditionLedger.Hunter h = ExpeditionLedger.get(p.serverLevel()).hunter(p.getUUID());
      Campaign.Mission m = Campaign.CONTRACTS.get(0);
      long now = level.getGameTime();
      h.contract = 0;
      h.contractCount = m.amount();
      h.started = Math.max(1L, now - 2400L);
      h.contractReadyAt = Math.max(2L, now);
      h.contractSerial = 7000 + (p.getUUID().hashCode() & 0xFF);
      ExpeditionLedger.get(p.serverLevel()).setDirty();
      contractSerial = h.contractSerial;
      int before = Tokens.balance(level, p.getUUID());
      boolean paid = CampaignProgress.claimContract(p, Long.toString(h.contractSerial));
      int after = Tokens.balance(level, p.getUUID());
      if (report) {
         verdict(paid && after > before && h.contract == -1, "contract '" + m.title() + "' paid: " + before + " -> " + after + " tokens, contract now "
            + h.contract + ", completed " + h.completed + ", commit pending " + Durability.pending());
      }
      return paid;
   }

   // ============================================================================================ snapshot / check

   static Path file(MinecraftServer server) {
      return server.getWorldPath(LevelResource.ROOT).resolve(SNAP);
   }

   static LicenceStore.Season season(ServerPlayer p) {
      LicenceStore.Hunter h = LicenceStore.get(p.server).find(p.getUUID());
      return h == null ? null : h.peek(LicenceTime.period(p.level()));
   }

   static String inventory(ServerPlayer p) {
      TreeMap<String, Integer> m = new TreeMap<>();
      var inv = p.getInventory();
      for (var list : List.of(inv.items, inv.armor, inv.offhand)) {
         for (ItemStack s : list) {
            if (!s.isEmpty()) {
               m.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).toString().replace("frontierhunts:", "fh:"), s.getCount(), Integer::sum);
            }
         }
      }
      return m.toString();
   }

   static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
      int n = 0;
      for (ItemStack s : p.getInventory().items) {
         if (s.is(item)) {
            n += s.getCount();
         }
      }
      return n;
   }

   static String firstHunt(Player p) {
      CompoundTag d = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("frontierhunts_firsthunt");
      return "{started " + d.getBoolean("started") + ", done " + Integer.toBinaryString(d.getInt("done")) + ", permit " + d.getBoolean("permit")
         + ", claimed " + d.getBoolean("claimed") + "}";
   }

   static CompoundTag snapshot(MinecraftServer server, ServerPlayer p) {
      CompoundTag s = new CompoundTag();
      UUID id = p.getUUID();
      s.putInt("tokens", Tokens.balance(server.overworld(), id));
      s.putString("inventory", inventory(p));
      s.putString("firsthunt", firstHunt(p));
      s.putBoolean("kitMark", p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean("frontierhunts_kit"));
      LicenceStore.Season se = season(p);
      s.putBoolean("licenceRecord", se != null && se.licence);
      s.putInt("deerTagsRecord", se == null ? 0 : se.tags[Regulations.TagKind.DEER.ordinal()]);
      TrainingStore.Record r = TrainingStore.get(server).peek(id, Course.byKey("archery"));
      s.putInt("archeryPasses", r == null ? 0 : r.passes);
      ExpeditionLedger.Hunter h = ExpeditionLedger.get(server.overworld()).hunters.get(id);
      s.putInt("contract", h == null ? -1 : h.contract);
      s.putInt("contractsCompleted", h == null ? 0 : h.completed);
      s.putInt("harvests", JournalApi.counter(p, "harvests"));
      s.putBoolean("inTraining", Academy.inTraining(p));
      s.putString("dimension", p.level().dimension().location().toString());
      return s;
   }

   static void check(MinecraftServer server) throws Exception {
      Path f = file(server);
      if (!Files.exists(f)) {
         say("FAIL no snapshot in this world (" + f + ")");
         return;
      }
      CompoundTag want = NbtIo.read(f);
      CompoundTag got = snapshot(server, FrontierQa.fake(server));
      List<String> bad = new ArrayList<>();
      for (String k : want.getAllKeys()) {
         if (!java.util.Objects.equals(want.get(k), got.get(k))) {
            bad.add(k + ": expected " + want.get(k) + " found " + got.get(k));
         }
      }
      for (String k : want.getAllKeys()) {
         verdict(java.util.Objects.equals(want.get(k), got.get(k)), "after restart " + k + " = " + got.get(k) + (java.util.Objects.equals(want.get(k), got.get(k)) ? ""
            : " (expected " + want.get(k) + ")"));
      }
      say(bad.isEmpty() ? "SUMMARY progress identical after the restart" : "SUMMARY " + bad.size() + " difference(s) after the restart");
   }

   /** every reward a second time, after the restart: nothing may pay twice */
   static void again(MinecraftServer server) {
      FakePlayer fp = FrontierQa.fake(server);
      ServerLevel level = server.overworld();
      String inv0 = inventory(fp);
      int t0 = Tokens.balance(level, fp.getUUID());
      FirstHunt.action(fp, FirstHuntNetwork.A_CLAIM);
      verdict(Tokens.balance(level, fp.getUUID()) == t0 && inventory(fp).equals(inv0), "first-hunt reward claimed again: no second payout");
      if (fp.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("frontierhunts_firsthunt").getBoolean("permit")) {
         FirstHunt.action(fp, FirstHuntNetwork.A_PERMIT);
         verdict(inventory(fp).equals(inv0), "free starter permit asked again: nothing issued");
      } else {
         say("free starter permit not taken yet: not asked again");
      }
      ExpeditionLedger.Hunter h = ExpeditionLedger.get(level).hunter(fp.getUUID());
      boolean paid = CampaignProgress.claimContract(fp, Long.toString(h.contractSerial));
      verdict(!paid && Tokens.balance(level, fp.getUUID()) == t0, "paid contract claimed again (serial " + h.contractSerial + "): refused");
      EventHooks.firePlayerLoggedIn(fp);
      verdict(inventory(fp).equals(inv0), "logging in again: no second starter kit (" + inventory(fp) + ")");
      say("tokens " + t0 + " -> " + Tokens.balance(level, fp.getUUID()));
   }

   // ============================================================================================ leaving during a reward

   static void disconnect(MinecraftServer server) throws Exception {
      ServerLevel level = server.overworld();
      boolean[] ok = new boolean[2];
      for (int k = 0; k < 2; k++) {
         boolean leaves = k == 0;
         String name = leaves ? "FHQA2" : "FHQA3";
         FakePlayer p = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.toLowerCase().getBytes()), name));
         BlockPos sp = FrontierQa.fake(server).blockPosition();
         p.moveTo(sp.getX() + 0.5 + k, sp.getY(), sp.getZ() + 0.5, 0f, 0f);
         FrontierQa.wire(p);
         Block board = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("frontierhunts:expedition_board"));
         level.setBlockAndUpdate(p.blockPosition().offset(-2, 0, 0), board.defaultBlockState());
         Tokens.credit(level, p.getUUID(), 300);
         FirstHunt.begin(p, false);
         var complete = FirstHunt.class.getDeclaredMethod("complete", ServerPlayer.class, FirstHunt.Step.class);
         complete.setAccessible(true);
         complete.invoke(null, p, FirstHunt.Step.HARVEST);
         // the contract is ready to hand in before the hunter goes
         ExpeditionLedger.Hunter h = ExpeditionLedger.get(level).hunter(p.getUUID());
         Campaign.Mission m = Campaign.CONTRACTS.get(0);
         h.contract = 0;
         h.contractCount = m.amount();
         h.started = Math.max(1L, level.getGameTime() - 2400L);
         h.contractReadyAt = Math.max(2L, level.getGameTime());
         h.contractSerial = 9100 + k;
         // [1.2.9] a licence first (the whole Academy issues it), so the paid request in flight is a deer tag (licences aren't sold)
         var training = com.formaworks.frontierhunts.academy.TrainingStore.get(server);
         for (var c : com.formaworks.frontierhunts.academy.Course.curriculum()) {
            training.record(p.getUUID(), c).passes = 1;
         }
         LicenceOffice.graduate(p);
         if (leaves) {
            p.disconnect(); // what ServerGamePacketListenerImpl.onDisconnect does before the packets still queued for this hunter run
         }
         int t0 = Tokens.balance(level, p.getUUID());
         String inv0 = inventory(p);
         // the three requests that were in flight when the connection dropped
         FirstHunt.action(p, FirstHuntNetwork.A_CLAIM);
         int deer = com.formaworks.frontierhunts.licence.Regulations.TagKind.DEER.ordinal();
         LicenceStore.Season se0 = season(p);
         int tags0 = se0 == null ? 0 : se0.tags[deer];
         LicenceOffice.request(p, LicenceOffice.WHAT_TAG, deer, LicenceOffice.PAY_TOKENS);
         boolean contract = CampaignProgress.claimContract(p, Long.toString(h.contractSerial));
         int t1 = Tokens.balance(level, p.getUUID());
         boolean claimed = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound("frontierhunts_firsthunt").getBoolean("claimed");
         LicenceStore.Season se = season(p);
         boolean licence = se != null && se.tags[deer] > tags0; // a deer tag bought
         String inv1 = inventory(p);
         if (leaves) {
            ok[k] = t1 == t0 && !claimed && !licence && !contract && inv1.equals(inv0);
            verdict(ok[k], "hunter left with three claims in flight: tokens " + t0 + " -> " + t1 + ", first-hunt claimed " + claimed + ", deer tag " + licence
               + ", contract paid " + contract + ", pack " + inv0 + " -> " + inv1);
         } else {
            ok[k] = t1 != t0 && claimed && licence && contract;
            verdict(ok[k], "control hunter who stayed: the same three claims do pay (tokens " + t0 + " -> " + t1 + ", claimed " + claimed + ", deer tag "
               + licence + ", contract " + contract + ")");
         }
      }
   }
}
