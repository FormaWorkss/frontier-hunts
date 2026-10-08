package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ExpeditionLedger;
import com.formaworks.frontierhunts.expedition.ExpeditionService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;

/** Shared hunting camps: founding, membership, the camp post, shared rank/upgrades, notifications and respawn. */
public final class CampService {
   public static final String[] UPGRADES = new String[]{"lodge_stores", "ammo_reloader", "bow_tuning_rack", "fishing_station"};
   // [benches] the retired stations in UPGRADES are handed out as their successors (ExpeditionService.give -> OldBenches.modernise)
   public static final String[] UPGRADE_TITLES = new String[]{"Lodge stores", "Reloading Bench", "Gunsmith's Bench", "Frontier Workbench"};
   public static final String[] RANKS = new String[]{"Spike camp", "Tent camp", "Wall-tent camp", "Hunting lodge", "Frontier lodge"};
   static final long INVITE_TICKS = 6000L;

   private CampService() {
   }

   public static CampRegistry reg(MinecraftServer server) {
      return CampRegistry.get(server);
   }

   public static CampRegistry.Camp campOf(ServerPlayer p) {
      return reg(p.server).campOf(p.getUUID());
   }

   public static String name(ServerPlayer p) {
      return p.getGameProfile().getName();
   }

   /** [1.1.6] 60 / 120 / 200 / 320 tokens: a camp upgrade is a goal for the whole camp, and each tier changes something (CampPerks) */
   public static final int[] UPGRADE_COSTS = {60, 120, 200, 320};

   public static int upgradeCost(int tier) {
      return UPGRADE_COSTS[Math.clamp(tier, 0, UPGRADE_COSTS.length - 1)];
   }

   // ------------------------------------------------------------------ membership
   public static String found(ServerPlayer p, String rawName, int color, BlockPos post) {
      CampRegistry reg = reg(p.server);
      if (reg.campOf(p.getUUID()) != null) {
         return "You already belong to a camp. Leave it first.";
      }
      String problem = reg.nameProblem(rawName, null);
      if (problem != null) {
         return problem;
      }
      CampRegistry.Camp c = reg.create(p.getUUID(), name(p), rawName, color, p.level().getGameTime());
      ExpeditionLedger ledger = ExpeditionLedger.get(p.serverLevel());
      c.tier = Math.clamp(ledger.hunter(p.getUUID()).camp, 0, 4);
      if (post != null && nearPost(p, post) && p.level().getBlockEntity(post) instanceof CampPostBlockEntity be && be.camp == null) {
         bindPost(c, p.serverLevel(), post);
      }
      reg.setDirty();
      Msg.tell(p, Msg.camp(c).append(Msg.text("Camp founded. Invite hunters from the camp screen or with /camp invite <name>.", Msg.PAPER)));
      Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 0.8F);
      return null;
   }

   public static String invite(ServerPlayer p, ServerPlayer target) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         return "Found a camp first.";
      }
      if (target == null || target == p) {
         return "Choose another hunter who is online.";
      }
      if (reg.campOf(target.getUUID()) == c) {
         return target.getGameProfile().getName() + " is already in your camp.";
      }
      if (c.members.size() >= CampsConfig.maxMembers) {
         return "Your camp is full (" + CampsConfig.maxMembers + " hunters).";
      }
      long now = p.level().getGameTime();
      Long prev = c.invites.get(target.getUUID());
      if (prev != null && prev - INVITE_TICKS + 200L > now) {
         return "You just invited " + target.getGameProfile().getName() + ".";
      }
      c.invites.put(target.getUUID(), now + INVITE_TICKS);
      reg.setDirty();
      MutableComponent m = Msg.camp(c)
         .append(Msg.text(name(p) + " invited you to join " + c.name + ". ", Msg.PAPER))
         .append(Msg.button("Join", "/camp accept " + c.id, "Join " + c.name, Msg.MOSS))
         .append(Component.literal(" "))
         .append(Msg.button("Decline", "/camp decline " + c.id, "Decline the invitation", Msg.RUST));
      Msg.tell(target, m);
      Msg.sound(target, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 1.2F);
      notify(p.server, c, Msg.text(name(p) + " invited " + target.getGameProfile().getName() + " to the camp.", Msg.MUTED), null, false);
      return null;
   }

   public static String accept(ServerPlayer p, String campRef) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = find(reg, campRef);
      if (c == null) {
         // accept the newest invite when no camp was named
         c = newestInvite(reg, p);
      }
      if (c == null || !c.invites.containsKey(p.getUUID())) {
         return "That invitation is no longer open.";
      }
      if (c.invites.get(p.getUUID()) < p.level().getGameTime()) {
         c.invites.remove(p.getUUID());
         return "That invitation expired. Ask for a new one.";
      }
      if (reg.campOf(p.getUUID()) != null) {
         return "Leave your current camp first (/camp leave).";
      }
      if (c.members.size() >= CampsConfig.maxMembers) {
         return c.name + " is full.";
      }
      reg.join(c, p.getUUID(), name(p));
      syncTier(p.server, c);
      notify(p.server, c, Msg.text(name(p) + " joined the camp.", Msg.MOSS), null, true);
      refreshPost(p.server, c);
      return null;
   }

   public static String decline(ServerPlayer p, String campRef) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = find(reg, campRef);
      if (c == null) {
         c = newestInvite(reg, p);
      }
      if (c == null || c.invites.remove(p.getUUID()) == null) {
         return "No open invitation.";
      }
      reg.setDirty();
      notify(p.server, c, Msg.text(name(p) + " declined the invitation.", Msg.MUTED), null, false);
      return null;
   }

   public static String leave(ServerPlayer p) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         return "You are not in a camp.";
      }
      boolean last = c.members.size() == 1;
      reg.leave(p.getUUID());
      clearRespawnFor(p, reg);
      if (last) {
         unbindPost(p.server, c);
         Msg.tell(p, Msg.camp(c).append(Msg.text("You struck camp. " + c.name + " is closed.", Msg.PAPER)));
      } else {
         Msg.tell(p, Msg.camp(c).append(Msg.text("You left the camp.", Msg.PAPER)));
         notify(p.server, c, Msg.text(name(p) + " left the camp." + (c.owner != null ? " " + c.members.get(c.owner) + " runs it now." : ""), Msg.MUTED), null, false);
         refreshPost(p.server, c);
      }
      return null;
   }

   public static String kick(ServerPlayer p, String who) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null || !p.getUUID().equals(c.owner)) {
         return "Only the camp's owner can remove hunters.";
      }
      UUID target = null;
      for (Map.Entry<UUID, String> e : c.members.entrySet()) {
         if (e.getKey().toString().equals(who) || e.getValue().equalsIgnoreCase(who)) {
            target = e.getKey();
         }
      }
      if (target == null || target.equals(p.getUUID())) {
         return "No such member.";
      }
      String n = c.members.get(target);
      reg.leave(target);
      ServerPlayer tp = p.server.getPlayerList().getPlayer(target);
      if (tp != null) {
         clearRespawnFor(tp, reg);
         Msg.tell(tp, Msg.camp(c).append(Msg.text("You were removed from the camp.", Msg.RUST)));
      }
      notify(p.server, c, Msg.text(n + " was removed from the camp.", Msg.MUTED), null, false);
      refreshPost(p.server, c);
      return null;
   }

   public static String disband(ServerPlayer p) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null || !p.getUUID().equals(c.owner)) {
         return "Only the camp's owner can strike the camp.";
      }
      notify(p.server, c, Msg.text(name(p) + " struck camp. " + c.name + " is closed.", Msg.RUST), null, false);
      unbindPost(p.server, c);
      reg.disband(c);
      for (ServerPlayer sp : p.server.getPlayerList().getPlayers()) {
         clearRespawnFor(sp, reg);
      }
      return null;
   }

   public static String edit(ServerPlayer p, String rawName, int color) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null || !p.getUUID().equals(c.owner)) {
         return "Only the camp's owner can rename it.";
      }
      String problem = reg.nameProblem(rawName, c);
      if (problem != null) {
         return problem;
      }
      String old = c.name;
      c.name = CampRegistry.clean(rawName);
      c.color = Math.floorMod(color, 16);
      if (!old.equals(c.name)) {
         c.log(name(p) + " renamed the camp from " + old);
      }
      reg.setDirty();
      refreshPost(p.server, c);
      notify(p.server, c, Msg.text("Camp colours updated by " + name(p) + ".", Msg.MUTED), p.getUUID(), false);
      return null;
   }

   public static String ask(ServerPlayer p, UUID campId) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.byId(campId);
      if (c == null) {
         return "That camp is gone.";
      }
      if (reg.campOf(p.getUUID()) != null) {
         return "Leave your current camp first.";
      }
      boolean told = false;
      for (UUID m : c.members.keySet()) {
         ServerPlayer mp = p.server.getPlayerList().getPlayer(m);
         if (mp != null) {
            Msg.tell(
               mp,
               Msg.camp(c)
                  .append(Msg.text(name(p) + " would like to join. ", Msg.PAPER))
                  .append(Msg.button("Invite", "/camp invite " + name(p), "Invite " + name(p), Msg.MOSS))
            );
            told = true;
         }
      }
      return told ? null : "Nobody from " + c.name + " is online right now.";
   }

   // ------------------------------------------------------------------ rank / upgrades (camp-level, mirrored into ExpeditionLedger.Hunter.camp)
   public static String upgrade(ServerPlayer p) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         return "Found or join a camp first.";
      }
      syncTier(p.server, c);
      if (c.tier >= 4) {
         return "Your camp is fully outfitted.";
      }
      int cost = upgradeCost(c.tier);
      if (!Tokens.spend(p.serverLevel(), p.getUUID(), cost)) {
         return "Outfitting the camp costs " + cost + " tokens.";
      }
      String id = UPGRADES[c.tier];
      ExpeditionService.give(p, new ItemStack((ItemLike)ExpeditionContent.STATIONS.get(id).get()));
      if (c.tier == 0) {
         ExpeditionService.give(p, new ItemStack((ItemLike)ExpeditionContent.STATIONS.get("lodge_stores").get(), 4));
      }
      c.tier++;
      c.log(name(p) + " outfitted the camp: " + UPGRADE_TITLES[c.tier - 1]);
      reg.setDirty();
      syncTier(p.server, c);
      refreshPost(p.server, c);
      notify(p.server, c, Msg.text(name(p) + " raised the camp to " + RANKS[c.tier] + " · " + UPGRADE_TITLES[c.tier - 1] + " added. "
         + CampPerks.PERKS[c.tier - 1], Msg.BRASS), null, true); // [1.1.6] what the tier changes
      return null;
   }

   /** Camp rank = best of the camp and its members' old per-player camp rank; written back to every member. */
   static void syncTier(MinecraftServer server, CampRegistry.Camp c) {
      ExpeditionLedger ledger = ExpeditionLedger.get(server.overworld());
      int t = c.tier;
      for (UUID m : c.members.keySet()) {
         ExpeditionLedger.Hunter h = ledger.hunters.get(m);
         if (h != null) {
            t = Math.max(t, Math.clamp(h.camp, 0, 4));
         }
      }
      boolean raised = t > c.tier;
      if (t != c.tier) {
         c.tier = t;
         reg(server).setDirty();
         refreshPost(server, c);
      }
      boolean dirty = false;
      for (UUID m : c.members.keySet()) {
         ExpeditionLedger.Hunter h = ledger.hunter(m);
         if (h.camp < c.tier) {
            h.camp = c.tier;
            dirty = true;
         }
      }
      if (dirty) {
         ledger.setDirty();
      }
      if (raised) {
         notify(server, c, Msg.text("The camp is now a " + RANKS[c.tier] + ".", Msg.BRASS), null, false);
      }
   }

   // ------------------------------------------------------------------ camp post
   static boolean nearPost(ServerPlayer p, BlockPos post) {
      return p.level().isLoaded(post) && p.distanceToSqr(post.getX() + 0.5, post.getY() + 1.0, post.getZ() + 0.5) < 64.0;
   }

   public static void postPlaced(ServerPlayer p, BlockPos pos) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         Msg.bar(p, "Name your camp to raise its flag.");
         CampsViews.sendCamp(p, true, pos);
         return;
      }
      if (c.post == null || p.getUUID().equals(c.owner)) {
         bindPost(c, p.serverLevel(), pos);
         notify(p.server, c, Msg.text(name(p) + " raised the camp flag at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".", Msg.MOSS), null, true);
      } else {
         Msg.bar(p, c.name + "'s flag already flies at " + c.post.toShortString() + ". Only " + c.members.get(c.owner) + " can move it.");
      }
   }

   public static String bind(ServerPlayer p, BlockPos pos) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         return "Found a camp first.";
      }
      if (!nearPost(p, pos) || !(p.level().getBlockEntity(pos) instanceof CampPostBlockEntity be)) {
         return "Stand at the camp post.";
      }
      if (be.camp != null && !be.camp.equals(c.id) && reg.byId(be.camp) != null) {
         return "That post belongs to another camp.";
      }
      if (c.post != null && !p.getUUID().equals(c.owner)) {
         return "Only " + c.members.get(c.owner) + " can move the camp flag.";
      }
      bindPost(c, p.serverLevel(), pos);
      notify(p.server, c, Msg.text(name(p) + " raised the camp flag at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ".", Msg.MOSS), null, true);
      return null;
   }

   static void bindPost(CampRegistry.Camp c, ServerLevel level, BlockPos pos) {
      if (c.post != null && !(c.post.equals(pos) && c.postDim.equals(level.dimension().location().toString()))) {
         unbindPost(level.getServer(), c);
      }
      c.post = pos.immutable();
      c.postDim = level.dimension().location().toString();
      c.log("Flag raised at " + pos.toShortString());
      reg(level.getServer()).setDirty();
      refreshPost(level.getServer(), c);
   }

   /** Clears the render copy of the camp's current post (if loaded) and forgets the post. */
   static void unbindPost(MinecraftServer server, CampRegistry.Camp c) {
      ServerLevel level = postLevel(server, c);
      if (level != null && level.isLoaded(c.post) && level.getBlockEntity(c.post) instanceof CampPostBlockEntity be) {
         be.show(null, "", 12, 0, 0);
      }
      c.post = null;
      c.postDim = "";
      reg(server).setDirty();
   }

   static ServerLevel postLevel(MinecraftServer server, CampRegistry.Camp c) {
      if (c.post == null || c.postDim.isEmpty()) {
         return null;
      }
      ResourceLocation rl = ResourceLocation.tryParse(c.postDim);
      return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
   }

   public static void refreshPost(MinecraftServer server, CampRegistry.Camp c) {
      ServerLevel level = postLevel(server, c);
      if (level != null && level.isLoaded(c.post) && level.getBlockEntity(c.post) instanceof CampPostBlockEntity be) {
         be.show(c.id, c.name, c.color, c.tier, c.members.size());
      }
   }

   public static void validatePost(CampPostBlockEntity be) {
      if (!(be.getLevel() instanceof ServerLevel level) || be.camp == null) {
         return;
      }
      CampRegistry.Camp c = reg(level.getServer()).byId(be.camp);
      boolean ours = c != null && c.post != null && c.post.equals(be.getBlockPos()) && c.postDim.equals(level.dimension().location().toString());
      if (ours) {
         be.show(c.id, c.name, c.color, c.tier, c.members.size());
      } else {
         be.show(null, "", 12, 0, 0);
      }
   }

   public static void postRemoved(Level level, BlockPos pos) {
      if (!(level instanceof ServerLevel sl)) {
         return;
      }
      CampRegistry reg = reg(sl.getServer());
      for (CampRegistry.Camp c : reg.camps.values()) {
         if (c.post != null && c.post.equals(pos) && c.postDim.equals(sl.dimension().location().toString())) {
            c.post = null;
            c.postDim = "";
            c.log("The camp flag came down");
            reg.setDirty();
            notify(sl.getServer(), c, Msg.text("The camp post at " + pos.toShortString() + " was taken down.", Msg.RUST), null, false);
            for (UUID m : c.members.keySet()) {
               ServerPlayer mp = sl.getServer().getPlayerList().getPlayer(m);
               if (mp != null) {
                  checkRespawn(mp, reg, c);
               }
            }
         }
      }
   }

   public static void openAtPost(ServerPlayer p, BlockPos pos) {
      CampsViews.sendCamp(p, true, pos);
   }

   // ------------------------------------------------------------------ respawn at the camp post
   public static String setRespawn(ServerPlayer p, boolean on) {
      CampRegistry reg = reg(p.server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c == null) {
         return "Join a camp first.";
      }
      if (!on) {
         Long spot = c.respawn.remove(p.getUUID());
         reg.setDirty();
         if (spot != null && BlockPos.of(spot).equals(p.getRespawnPosition())) {
            p.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
         }
         Msg.bar(p, "You will respawn at your bed or world spawn again.");
         return null;
      }
      ServerLevel level = postLevel(p.server, c);
      if (level == null || !level.isLoaded(c.post) || !(level.getBlockEntity(c.post) instanceof CampPostBlockEntity)) {
         return "Your camp has no standing post to return to" + (level != null && c.post != null && !level.isLoaded(c.post) ? " (visit it once to load it)." : ".");
      }
      BlockPos spot = respawnSpot(level, c.post);
      if (spot == null) {
         return "There is no clear ground next to the camp post.";
      }
      float yaw = level.getBlockState(c.post).hasProperty(CampPostBlock.FACING) ? level.getBlockState(c.post).getValue(CampPostBlock.FACING).toYRot() + 180.0F : 0.0F;
      p.setRespawnPosition(level.dimension(), spot, yaw, true, false);
      c.respawn.put(p.getUUID(), spot.asLong());
      reg.setDirty();
      Msg.tell(p, Msg.camp(c).append(Msg.text("You will respawn at the camp post (sleeping in a bed replaces this).", Msg.MOSS)));
      return null;
   }

   static BlockPos respawnSpot(ServerLevel level, BlockPos post) {
      int[][] ring = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {-2, 1}, {2, -1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}};
      for (int dy : new int[]{0, 1, -1}) {
         for (int[] o : ring) {
            BlockPos at = post.offset(o[0], dy, o[1]);
            BlockState feet = level.getBlockState(at);
            BlockState head = level.getBlockState(at.above());
            BlockPos below = at.below();
            if (feet.getBlock().isPossibleToRespawnInThis(feet)
               && head.getBlock().isPossibleToRespawnInThis(head)
               && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
               && !(feet.getBlock() instanceof CampPostBlock)) {
               return at;
            }
         }
      }
      return null;
   }

   /** Drops a respawn point we set once it no longer matches the camp's post. */
   static void checkRespawn(ServerPlayer p, CampRegistry reg, CampRegistry.Camp c) {
      Long spot = c.respawn.get(p.getUUID());
      if (spot == null) {
         return;
      }
      BlockPos at = BlockPos.of(spot);
      boolean valid = c.post != null && c.post.distManhattan(at) <= 4 && p.getRespawnDimension().location().toString().equals(c.postDim);
      if (!at.equals(p.getRespawnPosition())) {
         // they slept in a bed or used an anchor since: respect that choice
         c.respawn.remove(p.getUUID());
         reg.setDirty();
      } else if (!valid) {
         c.respawn.remove(p.getUUID());
         reg.setDirty();
         p.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
         Msg.tell(p, Msg.camp(c).append(Msg.text("Your camp respawn was cleared because the post is gone. Set it again at the new post.", Msg.RUST)));
      }
   }

   static void clearRespawnFor(ServerPlayer p, CampRegistry reg) {
      Long spot = reg.orphanRespawn.remove(p.getUUID());
      if (spot != null) {
         reg.setDirty();
         if (BlockPos.of(spot).equals(p.getRespawnPosition())) {
            p.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
            Msg.bar(p, "Your camp respawn point was cleared.");
         }
      }
   }

   // ------------------------------------------------------------------ notifications
   public static void notify(MinecraftServer server, CampRegistry.Camp c, Component body, UUID except, boolean chime) {
      if (c == null) {
         return;
      }
      for (UUID m : c.members.keySet()) {
         if (m.equals(except)) {
            continue;
         }
         ServerPlayer mp = server.getPlayerList().getPlayer(m);
         if (mp != null) {
            Msg.tell(mp, Msg.camp(c).append(body));
            if (chime) {
               Msg.sound(mp, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.45F, 1.05F);
            }
         }
      }
   }

   /** "[Ridge Camp] Jake tagged a 142 3/8" 5x5 buck · 96 kg · 212 m" to the hunter's camp. */
   public static void harvestNotice(MinecraftServer server, HarvestRecord r) {
      CampRegistry reg = reg(server);
      CampRegistry.Camp c = reg.campOf(r.hunter);
      if (c == null) {
         return;
      }
      c.harvests++;
      String line = r.name + " tagged a " + r.headline();
      if (r.quarry().rack() && r.score > c.bestScore) {
         c.bestScore = r.score;
         c.best = r.name + " · " + r.headline();
      }
      c.log(line);
      reg.setDirty();
      StringBuilder b = new StringBuilder(line);
      if (r.quarry().rack() && r.weightKg > 0.0) {
         b.append(" · ").append(Fmt.kg(r.weightKg));
      }
      if (r.shotM >= 1.0) {
         b.append(" · ").append(Fmt.metres(r.shotM));
      }
      if (!r.guide.isEmpty()) {
         b.append(" · guided by ").append(r.guide);
      }
      if (r.legendary) {
         b.append(" · LEGENDARY");
      }
      notify(server, c, Msg.text(b.toString(), r.legendary ? Msg.BRASS : Msg.PAPER), r.hunter, true);
   }

   // ------------------------------------------------------------------ ticking
   public static void tick(MinecraftServer server, long tick) {
      CampRegistry reg = reg(server);
      long now = server.overworld().getGameTime();
      if (tick % 100L == 0L) {
         for (CampRegistry.Camp c : reg.camps.values()) {
            if (c.invites.values().removeIf(t -> t < now)) {
               reg.setDirty();
            }
         }
      }
      if (tick % 600L == 0L) {
         for (CampRegistry.Camp c : new ArrayList<>(reg.camps.values())) {
            boolean online = false;
            for (UUID m : c.members.keySet()) {
               ServerPlayer mp = server.getPlayerList().getPlayer(m);
               if (mp != null) {
                  online = true;
                  if (!c.members.get(m).equals(name(mp))) {
                     c.members.put(m, name(mp));
                     reg.setDirty();
                  }
                  checkRespawn(mp, reg, c);
               }
            }
            if (online) {
               syncTier(server, c);
            }
         }
      }
   }

   public static void login(ServerPlayer p) {
      CampRegistry reg = reg(p.server);
      clearRespawnFor(p, reg);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c != null) {
         c.members.put(p.getUUID(), name(p));
         checkRespawn(p, reg, c);
         syncTier(p.server, c);
         int online = 0;
         for (UUID m : c.members.keySet()) {
            if (p.server.getPlayerList().getPlayer(m) != null) {
               online++;
            }
         }
         Msg.tell(p, Msg.camp(c).append(Msg.text(online + " of " + c.members.size() + " hunters in camp" + (c.log.isEmpty() ? "" : " · latest: " + c.log.peekFirst()), Msg.MUTED)));
         notify(p.server, c, Msg.text(name(p) + " is back in camp.", Msg.MUTED), p.getUUID(), false);
      }
      for (CampRegistry.Camp inv : reg.camps.values()) {
         Long until = inv.invites.get(p.getUUID());
         if (until != null && until > p.level().getGameTime() && c == null) {
            Msg.tell(
               p,
               Msg.camp(inv)
                  .append(Msg.text("You have an open invitation. ", Msg.PAPER))
                  .append(Msg.button("Join", "/camp accept " + inv.id, "Join " + inv.name, Msg.MOSS))
            );
         }
      }
   }

   // ------------------------------------------------------------------ helpers
   static CampRegistry.Camp find(CampRegistry reg, String ref) {
      if (ref == null || ref.isBlank()) {
         return null;
      }
      try {
         CampRegistry.Camp c = reg.byId(UUID.fromString(ref.trim()));
         if (c != null) {
            return c;
         }
      } catch (IllegalArgumentException ignored) {
      }
      return reg.byName(ref);
   }

   static CampRegistry.Camp newestInvite(CampRegistry reg, ServerPlayer p) {
      CampRegistry.Camp best = null;
      long bestT = Long.MIN_VALUE;
      for (CampRegistry.Camp c : reg.camps.values()) {
         Long t = c.invites.get(p.getUUID());
         if (t != null && t > bestT) {
            best = c;
            bestT = t;
         }
      }
      return best;
   }

   public static int color(String s) {
      for (DyeColor d : DyeColor.values()) {
         if (d.getName().equalsIgnoreCase(s)) {
            return d.getId();
         }
      }
      try {
         return Math.floorMod(Integer.parseInt(s), 16);
      } catch (NumberFormatException e) {
         return 12;
      }
   }

   // ------------------------------------------------------------------ commands
   public static void commands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         Commands.literal("camp")
            .executes(ctx -> {
               CampsViews.sendCamp(ctx.getSource().getPlayerOrException(), true, null);
               return 1;
            })
            .then(Commands.literal("found").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> run(ctx, p -> found(p, StringArgumentType.getString(ctx, "name"), Math.floorMod(StringArgumentType.getString(ctx, "name").hashCode(), 16), null)))))
            .then(Commands.literal("invite").then(Commands.argument("hunter", EntityArgument.player()).executes(ctx -> run(ctx, p -> invite(p, EntityArgument.getPlayer(ctx, "hunter"))))))
            .then(Commands.literal("accept").executes(ctx -> run(ctx, p -> accept(p, ""))).then(Commands.argument("camp", StringArgumentType.greedyString()).executes(ctx -> run(ctx, p -> accept(p, StringArgumentType.getString(ctx, "camp"))))))
            .then(Commands.literal("decline").executes(ctx -> run(ctx, p -> decline(p, ""))).then(Commands.argument("camp", StringArgumentType.greedyString()).executes(ctx -> run(ctx, p -> decline(p, StringArgumentType.getString(ctx, "camp"))))))
            .then(Commands.literal("leave").executes(ctx -> run(ctx, CampService::leave)))
            .then(Commands.literal("disband").executes(ctx -> run(ctx, CampService::disband)))
            .then(Commands.literal("kick").then(Commands.argument("member", StringArgumentType.word()).suggests((ctx, b) -> {
               ServerPlayer p = ctx.getSource().getPlayer();
               CampRegistry.Camp c = p == null ? null : campOf(p);
               return SharedSuggestionProvider.suggest(c == null ? List.of() : c.members.values(), b);
            }).executes(ctx -> run(ctx, p -> kick(p, StringArgumentType.getString(ctx, "member"))))))
            .then(Commands.literal("rename").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> run(ctx, p -> {
               CampRegistry.Camp c = campOf(p);
               return edit(p, StringArgumentType.getString(ctx, "name"), c == null ? 12 : c.color);
            }))))
            .then(Commands.literal("color").then(Commands.argument("dye", StringArgumentType.word()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(DyeColor.values()).map(DyeColor::getName), b)).executes(ctx -> run(ctx, p -> {
               CampRegistry.Camp c = campOf(p);
               return c == null ? "Join a camp first." : edit(p, c.name, color(StringArgumentType.getString(ctx, "dye")));
            }))))
            .then(Commands.literal("respawn").then(Commands.argument("on", BoolArgumentType.bool()).executes(ctx -> run(ctx, p -> setRespawn(p, BoolArgumentType.getBool(ctx, "on"))))))
            .then(Commands.literal("upgrade").executes(ctx -> run(ctx, CampService::upgrade)))
            .then(Commands.literal("info").executes(ctx -> {
               ServerPlayer p = ctx.getSource().getPlayerOrException();
               CampRegistry.Camp c = campOf(p);
               if (c == null) {
                  Msg.tell(p, Msg.text("You are not in a camp. Place a Camp Post or use /camp found <name>.", Msg.PAPER));
                  return 0;
               }
               Msg.tell(p, Msg.camp(c).append(Msg.text(RANKS[c.tier] + " · " + c.members.size() + " hunters · run by " + c.members.get(c.owner)
                  + (c.post != null ? " · post at " + c.post.toShortString() : " · no post") + " · " + c.harvests + " harvests", Msg.PAPER)));
               Msg.tell(p, Msg.gray(String.join(", ", c.members.values())));
               return 1;
            }))
      );
   }

   interface Action {
      String run(ServerPlayer p) throws CommandSyntaxException;
   }

   static int run(CommandContext<CommandSourceStack> ctx, CampService.Action a) throws CommandSyntaxException {
      ServerPlayer p = ctx.getSource().getPlayerOrException();
      String err = a.run(p);
      if (err != null) {
         ctx.getSource().sendFailure(Component.literal(err));
         return 0;
      }
      CampsViews.refresh(p);
      return 1;
   }

   static String lower(String s) {
      return s == null ? "" : s.toLowerCase(Locale.ROOT);
   }
}
