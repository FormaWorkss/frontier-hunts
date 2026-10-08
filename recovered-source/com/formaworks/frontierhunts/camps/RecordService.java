package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.expedition.HuntingCalendar;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/** Record book service: harvest intake, rankings, season rollover, the live board sync and /records. */
public final class RecordService {
   private static final Logger LOG = LogUtils.getLogger();
   private static boolean liveDirty = true;

   private RecordService() {
   }

   /** Hunting seasons run September to August (the reserve calendar opens in September of year 1). */
   public static int seasonOf(long monthSerial) {
      return (int)Math.floorDiv(monthSerial - 8L, 12L) + 1;
   }

   public static int currentSeason(MinecraftServer server) {
      return Math.max(1, seasonOf(HuntingCalendar.date(server.overworld()).serial()));
   }

   public static String seasonTitle(int season) {
      return "Season " + season;
   }

   public static void markDirty() {
      liveDirty = true;
   }

   /**
    * Entry point for every harvest (deer via the Whitetail hook, wildlife via death events).
    * {@code r} must carry hunter, name, species, score/points/weight/shot, position and animal id.
    */
   public static void ingest(MinecraftServer server, ServerPlayer hunterOnline, HarvestRecord r) {
      try {
         if (r.hunter == null) {
            return;
         }
         if (hunterOnline != null && (hunterOnline.isSpectator() || hunterOnline.isCreative() && !CampsConfig.countCreative)) {
            return;
         }
         RecordBook book = RecordBook.get(server);
         if (book.seen(r.animal)) {
            return;
         }
         checkSeason(server);
         HuntingCalendar.Date date = HuntingCalendar.date(server.overworld());
         r.monthSerial = date.serial();
         r.day = date.day();
         r.season = book.season;
         r.gameTime = server.overworld().getGameTime();
         r.epochMs = System.currentTimeMillis();
         CampRegistry.Camp camp = CampRegistry.get(server).campOf(r.hunter);
         r.camp = camp == null ? "" : camp.name;
         GuidedService.onHarvest(server, r);
         Map<RecordBook.Category, Integer> ranks = book.add(r);
         announce(server, book, r, ranks);
         CampService.harvestNotice(server, r);
         EventService.onHarvest(server, r);
         liveDirty = true;
      } catch (Exception e) {
         LOG.error("Frontier Hunts record book: failed to record a harvest", e);
      }
   }

   private static void announce(MinecraftServer server, RecordBook book, HarvestRecord r, Map<RecordBook.Category, Integer> ranks) {
      ServerPlayer hunter = server.getPlayerList().getPlayer(r.hunter);
      RecordBook.Category cat = RecordBook.Category.of(r);
      String what = r.name + "'s " + r.headline();
      if (r.legendary && CampsConfig.announceRecords) {
         Msg.broadcast(server, Msg.tag("Legend", Msg.BRASS).append(Msg.text(r.name + " took " + (r.legendName.isEmpty() ? "a legendary animal" : r.legendName) + " · " + r.headline(), Msg.BRASS)));
      }
      if (cat != null && ranks.containsKey(cat)) {
         int rank = ranks.get(cat);
         int allRank = book.allTimeRank(cat, r);
         boolean contested = book.season(cat).size() > 1;
         if (allRank == 1 && book.allTime.getOrDefault(cat, List.of()).size() > 1) {
            if (CampsConfig.announceRecords) {
               Msg.broadcast(server, Msg.tag("Record book", Msg.BRASS).append(Msg.text("New all-time record! " + what + " tops the " + cat.title.toLowerCase() + ".", Msg.BRASS)));
            }
            if (hunter != null) {
               Msg.title(hunter, Msg.text("ALL-TIME RECORD", Msg.BRASS), Msg.text(r.headline(), Msg.PAPER), 10, 70, 20);
               Msg.sound(hunter, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
            }
         } else if (rank == 1 && contested) {
            if (CampsConfig.announceRecords) {
               Msg.broadcast(server, Msg.tag("Record book", Msg.BRASS).append(Msg.text(what + " is the new " + seasonTitle(book.season).toLowerCase() + " leader for " + cat.tab.toLowerCase() + ".", Msg.PAPER)));
            }
            if (hunter != null) {
               Msg.title(hunter, Msg.text("SEASON LEADER", Msg.BRASS), Msg.text(r.headline(), Msg.PAPER), 10, 60, 20);
               Msg.sound(hunter, SoundEvents.PLAYER_LEVELUP, 0.7F, 1.2F);
            }
         } else if (cat.rack && rank <= 5 && CampsConfig.announceRecords) {
            Msg.broadcast(server, Msg.tag("Big-Buck Board", Msg.BRASS).append(Msg.text(what + " takes #" + rank + " on the " + cat.tab.toLowerCase() + " board.", Msg.PAPER)));
         }
      }
      if (hunter != null) {
         StringBuilder b = new StringBuilder(r.headline());
         if (r.quarry().rack() && r.weightKg > 0.0) {
            b.append(" · ").append(Fmt.kg(r.weightKg));
         }
         if (r.shotM >= 1.0) {
            b.append(" · ").append(Fmt.metres(r.shotM));
         }
         if (cat != null && ranks.containsKey(cat)) {
            b.append(" · #").append(ranks.get(cat)).append(' ').append(cat.tab.toLowerCase()).append(" this season");
         }
         if (r.quarry().kind == Quarry.Kind.BIRD) {
            Msg.bar(hunter, "Record book · " + b);
         } else {
            Msg.tell(hunter, Msg.tag("Record book", Msg.MUTED).append(Msg.text(b.toString(), Msg.PAPER)));
         }
      }
   }

   // ------------------------------------------------------------------ season rollover
   public static void checkSeason(MinecraftServer server) {
      RecordBook book = RecordBook.get(server);
      int cur = currentSeason(server);
      if (cur == book.season) {
         return;
      }
      if (cur < book.season) {
         // calendar length changed by an admin: adopt the new numbering without archiving
         book.season = cur;
         book.setDirty();
         return;
      }
      int closing = book.season;
      boolean any = !book.current.isEmpty();
      RecordBook.Archive a = book.archiveSeason();
      book.season = cur;
      book.setDirty();
      liveDirty = true;
      if (!any) {
         return;
      }
      Msg.broadcast(server, Msg.tag("Record book", Msg.BRASS).append(Msg.text(seasonTitle(closing) + " is closed. Champions:", Msg.BRASS)));
      for (Map.Entry<RecordBook.Category, List<HarvestRecord>> e : a.tops.entrySet()) {
         HarvestRecord top = e.getValue().getFirst();
         Msg.broadcast(server, Msg.text("  " + e.getKey().tab + " · " + top.name + " · " + e.getKey().valueText(top) + (e.getKey().rack ? " " + top.pointsText() : ""), Msg.PAPER));
      }
      if (!a.counts.isEmpty()) {
         Map.Entry<String, Integer> most = a.counts.entrySet().iterator().next();
         Msg.broadcast(server, Msg.text("  Most harvests · " + most.getKey() + " · " + most.getValue(), Msg.PAPER));
      }
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         Msg.title(p, Msg.text(seasonTitle(cur).toUpperCase(), Msg.BRASS), Msg.text("A new season opens · the " + seasonTitle(closing).toLowerCase() + " board is archived", Msg.PAPER), 10, 80, 30);
         Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 0.7F);
      }
   }

   // ------------------------------------------------------------------ live sync (boards, event HUD)
   public static void tick(MinecraftServer server, long tick) {
      if (tick % 100L == 0L) {
         checkSeason(server);
      }
      if (liveDirty && tick % 10L == 0L) {
         liveDirty = false;
         CampsNet.Live live = new CampsNet.Live(liveTag(server));
         for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CampsNet.send(p, live);
         }
      }
   }

   public static void login(ServerPlayer p) {
      CampsNet.send(p, new CampsNet.Live(liveTag(p.server)));
   }

   static CompoundTag liveTag(MinecraftServer server) {
      RecordBook book = RecordBook.get(server);
      CompoundTag t = new CompoundTag();
      t.putInt("season", book.season);
      CompoundTag cats = new CompoundTag();
      for (RecordBook.Category c : TrophyBoardBlockEntity.SHOWN) {
         ListTag l = new ListTag();
         List<HarvestRecord> top = book.top(c, false, 10);
         for (int i = 0; i < top.size(); i++) {
            HarvestRecord r = top.get(i);
            CompoundTag e = new CompoundTag();
            e.putString("name", r.name);
            e.putDouble("score", r.score);
            e.putInt("pl", r.pointsL);
            e.putInt("pr", r.pointsR);
            e.putString("date", r.dateText());
            e.putBoolean("legendary", r.legendary);
            e.putString("species", r.species);
            if (i < 5 && r.traits != null) {
               e.put("traits", r.traits.copy());
            }
            l.add(e);
         }
         cats.put(c.name(), l);
      }
      t.put("boards", cats);
      t.put("event", EventService.liveTag(server));
      return t;
   }

   // ------------------------------------------------------------------ screen
   public static void openRecords(ServerPlayer p, RecordBook.Category cat, BlockPos board) {
      CampsViews.sendRecords(p, cat == null ? RecordBook.Category.WHITETAIL : cat, "season", true);
   }

   // ------------------------------------------------------------------ board placement for admins
   public static String placeBoard(ServerLevel level, BlockPos origin, Direction facing) {
      BlockState state = CampsContent.TROPHY_BOARD.get().defaultBlockState().setValue(TrophyBoardBlock.FACING, facing).setValue(TrophyBoardBlock.PART, TrophyBoardBlock.ORIGIN);
      for (int p = 0; p < 6; p++) {
         BlockPos at = TrophyBoardBlock.partPos(origin, facing, p);
         BlockState s = level.getBlockState(at);
         if (!s.canBeReplaced() || !level.getWorldBorder().isWithinBounds(at)) {
            return "Blocked at " + at.toShortString();
         }
      }
      level.setBlock(origin, state, 3);
      TrophyBoardBlock.placeParts(level, origin, facing);
      return null;
   }

   /** Finds a clear, level spot 4-8 blocks from world spawn with the board facing spawn. */
   public static BlockPos spawnSpot(ServerLevel level, Direction[] facingOut) {
      BlockPos spawn = level.getSharedSpawnPos();
      for (int dist = 5; dist <= 9; dist++) {
         for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            for (int side = 0; side <= 4; side++) {
               int lateral = (side + 1) / 2 * (side % 2 == 0 ? 1 : -1);
               BlockPos base = spawn.relative(d, dist).relative(d.getClockWise(), lateral);
               int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ());
               BlockPos origin = new BlockPos(base.getX(), y, base.getZ());
               Direction facing = d.getOpposite();
               if (clear(level, origin, facing)) {
                  facingOut[0] = facing;
                  return origin;
               }
            }
         }
      }
      return null;
   }

   private static boolean clear(ServerLevel level, BlockPos origin, Direction facing) {
      int solid = 0;
      for (int p = 0; p < 6; p++) {
         BlockPos at = TrophyBoardBlock.partPos(origin, facing, p);
         if (!level.getBlockState(at).canBeReplaced() || !level.getFluidState(at).isEmpty()) {
            return false;
         }
         if (p < 3) {
            BlockPos below = at.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
               solid++;
            }
         }
      }
      return solid >= 2;
   }

   // ------------------------------------------------------------------ helpers
   public static String nameOf(MinecraftServer server, UUID id) {
      ServerPlayer p = server.getPlayerList().getPlayer(id);
      if (p != null) {
         return p.getGameProfile().getName();
      }
      try {
         if (server.getProfileCache() != null) {
            return server.getProfileCache().get(id).map(g -> g.getName()).orElse("Hunter");
         }
      } catch (Exception ignored) {
      }
      String n = RecordBook.get(server).names.get(id);
      return n == null ? "Hunter" : n;
   }

   // ------------------------------------------------------------------ commands
   public static void commands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         Commands.literal("records")
            .executes(ctx -> {
               CampsViews.sendRecords(ctx.getSource().getPlayerOrException(), RecordBook.Category.WHITETAIL, "season", true);
               return 1;
            })
            .then(Commands.literal("top").then(Commands.argument("category", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(RecordBook.Category.values()).map(x -> x.name().toLowerCase()), b)).executes(ctx -> top(ctx.getSource(), StringArgumentType.getString(ctx, "category"), false)).then(Commands.literal("alltime").executes(ctx -> top(ctx.getSource(), StringArgumentType.getString(ctx, "category"), true)))))
            .then(Commands.literal("board").requires(s -> s.hasPermission(2))
               .then(Commands.literal("spawn").executes(ctx -> {
                  ServerLevel level = ctx.getSource().getServer().overworld();
                  Direction[] f = new Direction[1];
                  BlockPos at = spawnSpot(level, f);
                  if (at == null) {
                     ctx.getSource().sendFailure(Component.literal("No clear, level 3x2 spot within 9 blocks of spawn. Stand where you want it and use /records board here."));
                     return 0;
                  }
                  String err = placeBoard(level, at, f[0]);
                  if (err != null) {
                     ctx.getSource().sendFailure(Component.literal(err));
                     return 0;
                  }
                  ctx.getSource().sendSuccess(() -> Component.literal("Big-Buck Board raised at " + at.toShortString() + " facing spawn."), true);
                  return 1;
               }))
               .then(Commands.literal("here").executes(ctx -> {
                  ServerPlayer p = ctx.getSource().getPlayerOrException();
                  Direction facing = p.getDirection().getOpposite();
                  BlockPos origin = p.blockPosition().relative(p.getDirection(), 2);
                  String err = placeBoard(p.serverLevel(), origin, facing);
                  if (err != null) {
                     ctx.getSource().sendFailure(Component.literal(err));
                     return 0;
                  }
                  ctx.getSource().sendSuccess(() -> Component.literal("Big-Buck Board raised at " + origin.toShortString()), true);
                  return 1;
               })))
            .then(Commands.literal("remove").requires(s -> s.hasPermission(2)).then(Commands.argument("id", LongArgumentType.longArg(1L)).executes(ctx -> {
               long id = LongArgumentType.getLong(ctx, "id");
               RecordBook book = RecordBook.get(ctx.getSource().getServer());
               HarvestRecord r = book.byId(id);
               if (r == null || !book.remove(id)) {
                  ctx.getSource().sendFailure(Component.literal("No record #" + id));
                  return 0;
               }
               liveDirty = true;
               ctx.getSource().sendSuccess(() -> Component.literal("Removed record #" + id + " (" + r.name + " · " + r.headline() + ")"), true);
               return 1;
            })))
            .then(Commands.literal("recent").executes(ctx -> {
               RecordBook book = RecordBook.get(ctx.getSource().getServer());
               List<HarvestRecord> l = book.recent(10, x -> true);
               if (l.isEmpty()) {
                  ctx.getSource().sendSuccess(() -> Component.literal("No harvests recorded this season yet."), false);
               }
               for (HarvestRecord r : l) {
                  ctx.getSource().sendSuccess(() -> Component.literal("#" + r.id + " · " + r.name + " · " + r.headline() + " · " + r.dateText()), false);
               }
               return 1;
            }))
      );
   }

   private static int top(CommandSourceStack src, String catName, boolean allTime) {
      RecordBook.Category c = RecordBook.Category.find(catName);
      if (c == null) {
         src.sendFailure(Component.literal("Unknown category"));
         return 0;
      }
      RecordBook book = RecordBook.get(src.getServer());
      MutableComponent head = Msg.tag(c.title + (allTime ? " · all time" : " · " + seasonTitle(book.season)), Msg.BRASS);
      src.sendSuccess(() -> head, false);
      if (c == RecordBook.Category.HARVESTS) {
         int i = 1;
         for (Map.Entry<UUID, Integer> e : book.counts(allTime, 10)) {
            int rank = i++;
            src.sendSuccess(() -> Component.literal(rank + ". " + book.names.getOrDefault(e.getKey(), "Hunter") + " · " + e.getValue()), false);
         }
         return 1;
      }
      List<HarvestRecord> l = new ArrayList<>(book.top(c, allTime, 10));
      if (l.isEmpty()) {
         src.sendSuccess(() -> Component.literal("No entries yet."), false);
      }
      for (int i = 0; i < l.size(); i++) {
         HarvestRecord r = l.get(i);
         int rank = i + 1;
         src.sendSuccess(() -> Component.literal(rank + ". " + r.name + " · " + c.valueText(r) + (c.rack ? " " + r.pointsText() : "") + " · " + r.dateText() + " (#" + r.id + ")"), false);
      }
      return 1;
   }
}
