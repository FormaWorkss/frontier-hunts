package com.formaworks.frontierhunts.camps;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Real-time weekend events. The server checks the wall clock once a second: inside the configured weekly window
 * (default Friday 18:00 → Sunday 23:59, server time zone) the next event of the rotation runs. Events only observe
 * harvests that the record book accepts; they never change animals, spawns or rules, so normal play is untouched.
 */
public final class EventService {
   private static final Logger LOG = LogUtils.getLogger();
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ROOT);

   private EventService() {
   }

   public record Window(long startMs, long endMs, HuntEvents.Type type) {
      long key() {
         return this.startMs / 1000L;
      }
   }

   /** The weekly window containing {@code nowMs}, or null. */
   public static Window windowAt(long nowMs) {
      ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(CampsConfig.zone());
      ZonedDateTime start = now.with(TemporalAdjusters.previousOrSame(CampsConfig.startDay)).with(CampsConfig.startTime).withSecond(0).withNano(0);
      if (start.isAfter(now)) {
         start = start.minusWeeks(1L);
      }
      ZonedDateTime end = endFor(start);
      return now.isBefore(end) ? new Window(start.toInstant().toEpochMilli(), end.toInstant().toEpochMilli(), typeFor(start)) : null;
   }

   /** The next window that starts after {@code nowMs}. */
   public static Window nextWindow(long nowMs) {
      ZonedDateTime now = Instant.ofEpochMilli(nowMs).atZone(CampsConfig.zone());
      ZonedDateTime start = now.with(TemporalAdjusters.nextOrSame(CampsConfig.startDay)).with(CampsConfig.startTime).withSecond(0).withNano(0);
      if (!start.isAfter(now)) {
         start = start.plusWeeks(1L);
      }
      return new Window(start.toInstant().toEpochMilli(), endFor(start).toInstant().toEpochMilli(), typeFor(start));
   }

   private static ZonedDateTime endFor(ZonedDateTime start) {
      ZonedDateTime end = start.with(TemporalAdjusters.nextOrSame(CampsConfig.endDay)).with(CampsConfig.endTime).withSecond(59).withNano(0);
      if (!end.isAfter(start)) {
         end = end.plusWeeks(1L);
      }
      return end;
   }

   static HuntEvents.Type typeFor(ZonedDateTime start) {
      // [gear20] Predator Weekend is about beta animals that do not spawn in the wild: left out of the rotation (an admin
      // can still start it with /events start predator)
      List<String> rot = CampsConfig.rotation.stream().filter(r -> HuntEvents.Type.find(r) != HuntEvents.Type.PREDATOR).toList();
      if (rot.isEmpty()) {
         return HuntEvents.Type.BIG_BUCK;
      }
      long week = Math.floorDiv(start.toLocalDate().toEpochDay() + 3L, 7L);
      HuntEvents.Type t = HuntEvents.Type.find(rot.get((int)Math.floorMod(week, (long)rot.size())));
      return t == null ? HuntEvents.Type.BIG_BUCK : t;
   }

   // ------------------------------------------------------------------ scheduler
   public static void tick(MinecraftServer server, long tick) {
      if (tick % 20L != 0L) {
         return;
      }
      try {
         HuntEvents ev = HuntEvents.get(server);
         long now = System.currentTimeMillis();
         if (ev.active != null) {
            if (now >= ev.active.endMs) {
               finish(server, ev, true);
            }
            return;
         }
         if (!CampsConfig.eventsEnabled) {
            return;
         }
         Window w = windowAt(now);
         if (w != null && w.key() != ev.lastWindow) {
            start(server, ev, w.type(), now, w.endMs(), false, w.key());
         }
      } catch (Exception e) {
         LOG.error("Frontier Hunts events: scheduler error", e);
      }
   }

   public static void start(MinecraftServer server, HuntEvents ev, HuntEvents.Type type, long startMs, long endMs, boolean forced, long window) {
      HuntEvents.Active a = new HuntEvents.Active();
      a.type = type;
      a.startMs = startMs;
      a.endMs = endMs;
      a.forced = forced;
      a.window = window;
      ev.active = a;
      if (!forced) {
         ev.lastWindow = window;
      }
      ev.setDirty();
      RecordService.markDirty();
      String ends = "ends " + Instant.ofEpochMilli(endMs).atZone(CampsConfig.zone()).format(WHEN) + " (" + Fmt.duration(endMs - startMs) + ")";
      Msg.broadcast(server, Msg.tag(type.title, Msg.BRASS).append(Msg.text("has begun! " + type.rules, Msg.PAPER)));
      Msg.broadcast(server, Msg.text("   Prizes: " + prizeLine() + " · " + ends + " · live standings in /records → Events", Msg.MUTED));
      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         Msg.title(p, Msg.text(type.title.toUpperCase(Locale.ROOT), Msg.BRASS), Msg.text(type.tagline + " · " + ends, Msg.PAPER), 15, 90, 30);
         Msg.sound(p, SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst().value(), 0.7F, 1.0F);
      }
   }

   static String prizeLine() {
      StringBuilder b = new StringBuilder();
      for (int i = 0; i < 3; i++) {
         if (i > 0) {
            b.append(" · ");
         }
         b.append(Fmt.ordinal(i + 1)).append(' ').append(CampsConfig.prizeTokens[i]).append(" tokens");
         ItemStack s = prizeItem(i);
         if (!s.isEmpty()) {
            b.append(" + ").append(s.getCount() > 1 ? s.getCount() + "× " : "").append(s.getHoverName().getString());
         }
      }
      return b.toString();
   }

   static ItemStack prizeItem(int place) {
      String spec = place < CampsConfig.prizeItems.length ? CampsConfig.prizeItems[place] : "";
      return parseItem(spec);
   }

   static ItemStack parseItem(String spec) {
      if (spec == null || spec.isBlank()) {
         return ItemStack.EMPTY;
      }
      String id = spec.trim();
      int count = 1;
      int star = id.indexOf('*');
      if (star > 0) {
         try {
            count = Math.clamp(Integer.parseInt(id.substring(star + 1).trim()), 1, 64);
         } catch (NumberFormatException ignored) {
         }
         id = id.substring(0, star).trim();
      }
      ResourceLocation rl = ResourceLocation.tryParse(id);
      if (rl == null) {
         return ItemStack.EMPTY;
      }
      Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
      return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
   }

   public static void finish(MinecraftServer server, HuntEvents ev, boolean award) {
      HuntEvents.Active a = ev.active;
      if (a == null) {
         return;
      }
      ev.active = null;
      ev.setDirty();
      RecordService.markDirty();
      List<HuntEvents.Entry> rank = a.ranking();
      HuntEvents.Past past = new HuntEvents.Past();
      past.type = a.type;
      past.startMs = a.startMs;
      past.endMs = System.currentTimeMillis();
      if (!award) {
         Msg.broadcast(server, Msg.tag(a.type.title, Msg.MUTED).append(Msg.text("was called off by an admin. No prizes this time.", Msg.PAPER)));
         return;
      }
      if (rank.isEmpty()) {
         Msg.broadcast(server, Msg.tag(a.type.title, Msg.BRASS).append(Msg.text("is over. Nobody placed this time — the woods win.", Msg.PAPER)));
      } else {
         Msg.broadcast(server, Msg.tag(a.type.title, Msg.BRASS).append(Msg.text("is over! Final standings:", Msg.BRASS)));
      }
      for (int i = 0; i < Math.min(3, rank.size()); i++) {
         HuntEvents.Entry e = rank.get(i);
         int tokens = CampsConfig.prizeTokens[i];
         Tokens.credit(server.overworld(), e.id, tokens);
         com.formaworks.frontierhunts.journal.JournalHooks.eventPlaced(server, e.id, i + 1); // [journal] weekend event podium
         ItemStack prize = prizeItem(i);
         ServerPlayer p = server.getPlayerList().getPlayer(e.id);
         if (!prize.isEmpty()) {
            if (p != null) {
               give(p, prize.copy());
            } else {
               ev.pendingItems.computeIfAbsent(e.id, k -> new ArrayList<>()).add(CampsConfig.prizeItems[i]);
            }
         }
         String line = Fmt.ordinal(i + 1) + " · " + e.name + " · " + a.type.valueText(e);
         past.podium.add(line);
         Msg.broadcast(server, Msg.text("   " + line + "  (+" + tokens + " tokens" + (prize.isEmpty() ? "" : ", " + prize.getHoverName().getString()) + ")", i == 0 ? Msg.BRASS : Msg.PAPER));
         if (p != null) {
            Msg.title(p, Msg.text(Fmt.ordinal(i + 1).toUpperCase(Locale.ROOT) + " PLACE", Msg.BRASS), Msg.text(a.type.title + " · +" + tokens + " tokens", Msg.PAPER), 10, 80, 30);
            Msg.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.0F);
         }
         CampRegistry.Camp c = CampRegistry.get(server).campOf(e.id);
         if (c != null) {
            c.log(e.name + " placed " + Fmt.ordinal(i + 1) + " in " + a.type.title);
            CampRegistry.get(server).setDirty();
         }
      }
      ev.history.addFirst(past);
      while (ev.history.size() > 12) {
         ev.history.removeLast();
      }
      Window next = nextWindow(System.currentTimeMillis());
      if (CampsConfig.eventsEnabled) {
         Msg.broadcast(server, Msg.text("   Next up: " + next.type().title + " · " + Instant.ofEpochMilli(next.startMs()).atZone(CampsConfig.zone()).format(WHEN), Msg.MUTED));
      }
   }

   static void give(ServerPlayer p, ItemStack s) {
      if (!p.getInventory().add(s)) {
         p.drop(s, false);
      }
   }

   // ------------------------------------------------------------------ scoring
   public static void onHarvest(MinecraftServer server, HarvestRecord r) {
      HuntEvents ev = HuntEvents.get(server);
      HuntEvents.Active a = ev.active;
      long now = System.currentTimeMillis();
      if (a == null || now < a.startMs || now >= a.endMs) {
         return;
      }
      double pts = a.type.points(r);
      if (pts <= 0.0) {
         return;
      }
      HuntEvents.Entry e = a.entries.computeIfAbsent(r.hunter, k -> {
         HuntEvents.Entry x = new HuntEvents.Entry();
         x.id = k;
         return x;
      });
      e.name = r.name;
      boolean improved;
      if (a.type.single()) {
         improved = pts > e.points;
         e.points = Math.max(e.points, pts);
      } else {
         improved = true;
         e.points += pts;
      }
      if (r.score > e.best) {
         e.best = r.score;
      }
      e.count++;
      if (improved) {
         e.last = now;
      }
      ev.setDirty();
      RecordService.markDirty();
      List<HuntEvents.Entry> rank = a.ranking();
      int place = rank.indexOf(e) + 1;
      ServerPlayer p = server.getPlayerList().getPlayer(r.hunter);
      if (!rank.isEmpty() && !rank.getFirst().id.equals(a.leader)) {
         a.leader = rank.getFirst().id;
         if (CampsConfig.announceLeads) {
            Msg.broadcast(server, Msg.tag(a.type.title, Msg.BRASS).append(Msg.text(rank.getFirst().name + " takes the lead · " + a.type.valueText(rank.getFirst()), Msg.PAPER)));
         }
         if (p != null && place == 1) {
            Msg.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.4F);
         }
      } else if (p != null && place > 0) {
         Msg.bar(p, a.type.title + " · you are " + Fmt.ordinal(place) + " · " + a.type.valueText(e));
      }
   }

   // ------------------------------------------------------------------ sync / login
   static CompoundTag liveTag(MinecraftServer server) {
      HuntEvents ev = HuntEvents.get(server);
      CompoundTag t = new CompoundTag();
      long now = System.currentTimeMillis();
      if (ev.active != null) {
         HuntEvents.Active a = ev.active;
         t.putBoolean("active", true);
         t.putString("type", a.type.name());
         t.putString("title", a.type.title);
         t.putString("tagline", a.type.tagline);
         t.putLong("left", Math.max(0L, a.endMs - now));
         ListTag top = new ListTag();
         List<HuntEvents.Entry> rank = a.ranking();
         for (int i = 0; i < Math.min(20, rank.size()); i++) {
            CompoundTag e = new CompoundTag();
            e.putString("name", rank.get(i).name);
            e.putString("value", a.type.valueText(rank.get(i)));
            top.add(e);
         }
         t.put("top", top);
      } else if (CampsConfig.eventsEnabled) {
         Window next = nextWindow(now);
         t.putBoolean("active", false);
         t.putString("title", next.type().title);
         t.putLong("in", Math.max(0L, next.startMs() - now));
      }
      return t;
   }

   public static void login(ServerPlayer p) {
      HuntEvents ev = HuntEvents.get(p.server);
      List<String> items = ev.pendingItems.remove(p.getUUID());
      if (items != null) {
         ev.setDirty();
         for (String s : items) {
            ItemStack st = parseItem(s);
            if (!st.isEmpty()) {
               give(p, st);
               Msg.tell(p, Msg.tag("Prize", Msg.BRASS).append(Msg.text("Your event prize is in your pack: " + st.getHoverName().getString(), Msg.PAPER)));
            }
         }
      }
      if (ev.active != null) {
         HuntEvents.Active a = ev.active;
         Msg.tell(p, Msg.tag(a.type.title, Msg.BRASS).append(Msg.text("is running · " + a.type.tagline + " · ends in " + Fmt.duration(a.endMs - System.currentTimeMillis()) + " · /records to see standings", Msg.PAPER)));
      }
   }

   // ------------------------------------------------------------------ commands
   static long parseDuration(String s) {
      try {
         String x = s.trim().toLowerCase(Locale.ROOT);
         long mult = 3600000L;
         if (x.endsWith("m")) {
            mult = 60000L;
            x = x.substring(0, x.length() - 1);
         } else if (x.endsWith("h")) {
            x = x.substring(0, x.length() - 1);
         } else if (x.endsWith("d")) {
            mult = 86400000L;
            x = x.substring(0, x.length() - 1);
         }
         long v = Long.parseLong(x);
         return v <= 0L ? -1L : Math.min(v * mult, 14L * 86400000L);
      } catch (Exception e) {
         return -1L;
      }
   }

   public static void commands(CommandDispatcher<CommandSourceStack> d) {
      d.register(
         Commands.literal("huntevent")
            .executes(ctx -> status(ctx.getSource()))
            .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
            .then(Commands.literal("start").requires(s -> s.hasPermission(2)).then(Commands.argument("event", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(HuntEvents.Type.values()).map(t -> t.name().toLowerCase(Locale.ROOT)), b))
               .executes(ctx -> force(ctx.getSource(), StringArgumentType.getString(ctx, "event"), "48h"))
               .then(Commands.argument("duration", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("30m", "2h", "48h", "3d"), b)).executes(ctx -> force(ctx.getSource(), StringArgumentType.getString(ctx, "event"), StringArgumentType.getString(ctx, "duration"))))))
            .then(Commands.literal("stop").requires(s -> s.hasPermission(2)).executes(ctx -> stop(ctx.getSource(), true)))
            .then(Commands.literal("cancel").requires(s -> s.hasPermission(2)).executes(ctx -> stop(ctx.getSource(), false)))
            .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(ctx -> {
               CampsConfig.load();
               RecordService.markDirty();
               ctx.getSource().sendSuccess(() -> Component.literal("Reloaded " + CampsConfig.file().getFileName() + " · " + CampsConfig.describe()), true);
               return 1;
            }))
            .then(Commands.literal("config").requires(s -> s.hasPermission(2))
               .executes(ctx -> {
                  ctx.getSource().sendSuccess(() -> Component.literal(CampsConfig.describe() + " · file: config/" + CampsConfig.file().getFileName()), false);
                  return 1;
               })
               .then(Commands.argument("key", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(CampsConfig.keys(), b)).then(Commands.argument("value", StringArgumentType.greedyString()).executes(ctx -> {
                  String err = CampsConfig.set(StringArgumentType.getString(ctx, "key"), StringArgumentType.getString(ctx, "value"));
                  if (err != null) {
                     ctx.getSource().sendFailure(Component.literal(err));
                     return 0;
                  }
                  RecordService.markDirty();
                  ctx.getSource().sendSuccess(() -> Component.literal("Saved · " + CampsConfig.describe()), true);
                  return 1;
               }))))
      );
   }

   private static int status(CommandSourceStack src) {
      HuntEvents ev = HuntEvents.get(src.getServer());
      long now = System.currentTimeMillis();
      if (ev.active != null) {
         HuntEvents.Active a = ev.active;
         src.sendSuccess(() -> Msg.tag(a.type.title, Msg.BRASS).append(Msg.text(a.type.rules + " · ends in " + Fmt.duration(a.endMs - now), Msg.PAPER)), false);
         List<HuntEvents.Entry> rank = a.ranking();
         for (int i = 0; i < Math.min(5, rank.size()); i++) {
            HuntEvents.Entry e = rank.get(i);
            int n = i + 1;
            src.sendSuccess(() -> Component.literal(n + ". " + e.name + " · " + a.type.valueText(e)), false);
         }
      } else {
         Window next = nextWindow(now);
         src.sendSuccess(() -> Component.literal((CampsConfig.eventsEnabled ? "No event running. Next: " + next.type().title + " in " + Fmt.duration(next.startMs() - now) : "Weekend events are disabled.") + " · " + CampsConfig.describe()), false);
      }
      return 1;
   }

   private static int force(CommandSourceStack src, String typeName, String dur) {
      HuntEvents.Type t = HuntEvents.Type.find(typeName);
      if (t == null) {
         src.sendFailure(Component.literal("Unknown event. Use big_buck, predator, waterfowl or rut_rally."));
         return 0;
      }
      long ms = parseDuration(dur);
      if (ms <= 0L) {
         src.sendFailure(Component.literal("Duration like 30m, 2h or 3d"));
         return 0;
      }
      HuntEvents ev = HuntEvents.get(src.getServer());
      if (ev.active != null) {
         src.sendFailure(Component.literal(ev.active.type.title + " is running. /huntevent stop (award prizes) or /huntevent cancel first."));
         return 0;
      }
      long now = System.currentTimeMillis();
      Window w = windowAt(now);
      start(src.getServer(), ev, t, now, now + ms, true, 0L);
      if (w != null) {
         // an admin-run event replaces this weekend's scheduled one
         ev.lastWindow = w.key();
         ev.setDirty();
      }
      src.sendSuccess(() -> Component.literal("Started " + t.title + " for " + Fmt.duration(ms)), true);
      return 1;
   }

   private static int stop(CommandSourceStack src, boolean award) {
      HuntEvents ev = HuntEvents.get(src.getServer());
      if (ev.active == null) {
         src.sendFailure(Component.literal("No event is running."));
         return 0;
      }
      Window w = windowAt(System.currentTimeMillis());
      if (w != null) {
         ev.lastWindow = w.key();
      }
      finish(src.getServer(), ev, award);
      src.sendSuccess(() -> Component.literal(award ? "Event ended and prizes awarded." : "Event cancelled."), true);
      return 1;
   }

   static String whoami(MinecraftServer server, UUID id) {
      return RecordService.nameOf(server, id);
   }
}
