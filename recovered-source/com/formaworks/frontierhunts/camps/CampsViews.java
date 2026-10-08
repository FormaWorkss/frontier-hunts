package com.formaworks.frontierhunts.camps;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Builds the JSON the camp screen shows. One session per player remembers the open tab and where it was opened. */
public final class CampsViews {
   static final Map<UUID, CampsViews.Session> SESSIONS = new HashMap<>();
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ROOT);

   private CampsViews() {
   }

   public static final class Session {
      public String tab = "camp";
      public RecordBook.Category cat = RecordBook.Category.WHITETAIL;
      public String scope = "season";
      public int archive;
      public BlockPos post;
      public long openUntil;
      public long lastAction = Long.MIN_VALUE;
   }

   static CampsViews.Session session(ServerPlayer p) {
      return SESSIONS.computeIfAbsent(p.getUUID(), k -> new CampsViews.Session());
   }

   public static void forget(UUID id) {
      SESSIONS.remove(id);
   }

   public static void sendCamp(ServerPlayer p, boolean open, BlockPos post) {
      CampsViews.Session s = session(p);
      s.tab = "camp";
      s.post = post == null ? null : post.immutable();
      send(p, open);
   }

   public static void sendGuided(ServerPlayer p, boolean open) {
      session(p).tab = "guided";
      send(p, open);
   }

   public static void sendRecords(ServerPlayer p, RecordBook.Category cat, String scope, boolean open) {
      CampsViews.Session s = session(p);
      s.tab = "records";
      s.cat = cat;
      s.scope = scope;
      send(p, open);
   }

   public static void sendEvents(ServerPlayer p, boolean open) {
      session(p).tab = "events";
      send(p, open);
   }

   /** Re-sends the player's current tab if their screen is open. */
   public static void refreshIfOpen(ServerPlayer p) {
      if (p == null) {
         return;
      }
      CampsViews.Session s = SESSIONS.get(p.getUUID());
      if (s != null && s.openUntil > p.level().getGameTime()) {
         send(p, false);
      }
   }

   public static void refresh(ServerPlayer p) {
      refreshIfOpen(p);
   }

   static void send(ServerPlayer p, boolean open) {
      CampsViews.Session s = session(p);
      if (s.post != null && !CampService.nearPost(p, s.post)) {
         s.post = null;
      }
      if (open) {
         s.openUntil = p.level().getGameTime() + 12000L;
      }
      JsonObject j = new JsonObject();
      j.addProperty("open", open);
      j.addProperty("tab", s.tab);
      j.addProperty("me", p.getGameProfile().getName());
      j.addProperty("tokens", Tokens.balance(p.serverLevel(), p.getUUID()));
      j.addProperty("op", p.hasPermissions(2));
      j.addProperty("season", RecordBook.get(p.server).season);
      switch (s.tab) {
         case "guided" -> guided(p, j);
         case "records" -> records(p, s, j);
         case "events" -> events(p, j);
         default -> camp(p, s, j);
      }
      CampsNet.send(p, new CampsNet.View(j.toString()));
   }

   // ------------------------------------------------------------------ camp tab
   static void camp(ServerPlayer p, CampsViews.Session s, JsonObject j) {
      MinecraftServer server = p.server;
      CampRegistry reg = CampRegistry.get(server);
      CampRegistry.Camp c = reg.campOf(p.getUUID());
      if (c != null) {
         JsonObject o = new JsonObject();
         o.addProperty("id", c.id.toString());
         o.addProperty("name", c.name);
         o.addProperty("color", c.color);
         o.addProperty("tier", c.tier);
         o.addProperty("rank", CampService.RANKS[c.tier]);
         o.addProperty("owner", p.getUUID().equals(c.owner));
         o.addProperty("ownerName", c.members.getOrDefault(c.owner, "?"));
         o.addProperty("nextCost", c.tier < 4 ? CampService.upgradeCost(c.tier) : 0);
         o.addProperty("nextUpgrade", c.tier < 4 ? CampService.UPGRADE_TITLES[c.tier] : "");
         o.addProperty("post", c.post == null ? "" : c.post.getX() + ", " + c.post.getY() + ", " + c.post.getZ() + (c.postDim.equals("minecraft:overworld") ? "" : " · " + c.postDim));
         o.addProperty("harvests", c.harvests);
         o.addProperty("best", c.best);
         o.addProperty("respawn", c.respawn.containsKey(p.getUUID()));
         o.addProperty("max", CampsConfig.maxMembers);
         JsonArray members = new JsonArray();
         for (Map.Entry<UUID, String> e : c.members.entrySet()) {
            JsonObject m = new JsonObject();
            m.addProperty("id", e.getKey().toString());
            m.addProperty("name", e.getValue());
            m.addProperty("online", server.getPlayerList().getPlayer(e.getKey()) != null);
            m.addProperty("owner", e.getKey().equals(c.owner));
            m.addProperty("respawn", c.respawn.containsKey(e.getKey()));
            members.add(m);
         }
         o.add("members", members);
         JsonArray log = new JsonArray();
         c.log.stream().limit(10).forEach(log::add);
         o.add("log", log);
         JsonArray invited = new JsonArray();
         c.invites.keySet().forEach(id -> invited.add(RecordService.nameOf(server, id)));
         o.add("invited", invited);
         j.add("camp", o);
      }
      JsonArray invites = new JsonArray();
      for (CampRegistry.Camp x : reg.camps.values()) {
         Long until = x.invites.get(p.getUUID());
         if (until != null && until > p.level().getGameTime()) {
            JsonObject i = new JsonObject();
            i.addProperty("id", x.id.toString());
            i.addProperty("name", x.name);
            i.addProperty("color", x.color);
            i.addProperty("members", x.members.size());
            invites.add(i);
         }
      }
      j.add("invites", invites);
      JsonArray online = new JsonArray();
      for (ServerPlayer o : server.getPlayerList().getPlayers()) {
         if (o != p && (c == null || !c.members.containsKey(o.getUUID()))) {
            online.add(o.getGameProfile().getName());
         }
      }
      j.add("online", online);
      if (s.post != null && p.level().getBlockEntity(s.post) instanceof CampPostBlockEntity be) {
         JsonObject pc = new JsonObject();
         CampRegistry.Camp owner = be.camp == null ? null : reg.byId(be.camp);
         pc.addProperty("bound", owner != null);
         pc.addProperty("mine", owner != null && owner == c);
         pc.addProperty("campName", owner == null ? "" : owner.name);
         pc.addProperty("campId", owner == null ? "" : owner.id.toString());
         pc.addProperty("members", owner == null ? 0 : owner.members.size());
         pc.addProperty("rank", owner == null ? "" : CampService.RANKS[owner.tier]);
         pc.addProperty("color", owner == null ? 12 : owner.color);
         j.add("post", pc);
      }
   }

   // ------------------------------------------------------------------ guided tab
   static void guided(ServerPlayer p, JsonObject j) {
      MinecraftServer server = p.server;
      GuidedHunts g = GuidedHunts.get(server);
      long now = server.overworld().getGameTime();
      JsonArray hunts = new JsonArray();
      for (GuidedHunts.Hunt h : g.involving(p.getUUID())) {
         JsonObject o = new JsonObject();
         o.addProperty("id", h.id);
         o.addProperty("npc", h.npc);
         o.addProperty("role", h.npc ? "outfitter" : (p.getUUID().equals(h.client) ? "client" : "guide"));
         o.addProperty("state", h.state.name());
         o.addProperty("terms", h.terms());
         o.addProperty("guide", h.npc ? h.guideName + " · " + h.outfitter : h.guideName);
         o.addProperty("client", h.clientName);
         o.addProperty("fee", h.fee);
         o.addProperty("escrow", h.escrow);
         o.addProperty("reward", h.reward);
         o.addProperty("deposit", h.deposit);
         o.addProperty("days", h.days);
         o.addProperty("story", h.story);
         long left = h.state == GuidedHunts.State.OFFERED ? h.offerUntil - now : h.deadline - GuidedService.cal(server);
         o.addProperty("left", Math.max(0L, left) * 50L);
         o.addProperty("leftDays", Math.max(0L, left) / 24000.0);
         hunts.add(o);
      }
      j.add("hunts", hunts);
      JsonArray history = new JsonArray();
      int n = 0;
      for (GuidedHunts.Hunt h : g.history) {
         if (n < 6 && (p.getUUID().equals(h.client) || p.getUUID().equals(h.guide))) {
            n++;
            history.add((h.npc ? h.outfitter : h.guideName + " → " + h.clientName) + " · " + h.terms() + " · " + h.state.name().toLowerCase(Locale.ROOT) + (h.result.isEmpty() ? "" : " · " + h.result));
         }
      }
      j.add("history", history);
      boolean near = GuidedService.nearContractBoard(p);
      j.addProperty("nearBoard", near);
      JsonArray outfit = new JsonArray();
      for (GuidedHunts.Hunt o : GuidedService.todaysOutfitters(server)) {
         JsonObject x = new JsonObject();
         x.addProperty("key", Long.toString(o.npcKey));
         x.addProperty("guide", o.guideName);
         x.addProperty("outfitter", o.outfitter);
         x.addProperty("story", o.story);
         x.addProperty("terms", o.terms());
         x.addProperty("days", o.days);
         x.addProperty("deposit", o.deposit);
         x.addProperty("reward", o.reward);
         x.addProperty("booked", g.isBooked(p.getUUID(), o.npcKey));
         outfit.add(x);
      }
      j.add("outfitters", outfit);
      j.addProperty("busy", g.activeFor(p.getUUID()) != null);
      JsonArray players = new JsonArray();
      for (ServerPlayer o : server.getPlayerList().getPlayers()) {
         if (o != p) {
            players.add(o.getGameProfile().getName());
         }
      }
      j.add("players", players);
      JsonArray species = new JsonArray();
      for (Quarry q : Quarry.values()) {
         JsonObject x = new JsonObject();
         x.addProperty("id", q.id);
         x.addProperty("title", q.title);
         x.addProperty("rack", q.rack());
         species.add(x);
      }
      j.add("species", species);
      j.addProperty("range", CampsConfig.guideRange);
   }

   // ------------------------------------------------------------------ records tab
   static void records(ServerPlayer p, CampsViews.Session s, JsonObject j) {
      RecordBook book = RecordBook.get(p.server);
      j.addProperty("cat", s.cat.name());
      JsonArray cats = new JsonArray();
      for (RecordBook.Category c : RecordBook.Category.values()) {
         JsonObject o = new JsonObject();
         o.addProperty("id", c.name());
         o.addProperty("tab", c.tab);
         o.addProperty("title", c.title);
         cats.add(o);
      }
      j.add("cats", cats);
      JsonArray seasons = new JsonArray();
      book.archive.forEach(a -> seasons.add(a.season));
      j.add("archives", seasons);
      String scope = s.scope;
      RecordBook.Archive arch = null;
      if (scope.equals("archive")) {
         if (book.archive.isEmpty()) {
            scope = "season";
         } else {
            s.archive = Math.clamp(s.archive, 0, book.archive.size() - 1);
            arch = book.archive.get(s.archive);
            j.addProperty("archiveSeason", arch.season);
            j.addProperty("archiveIdx", s.archive);
         }
      }
      j.addProperty("scope", scope);
      String me = p.getGameProfile().getName();
      JsonArray rows = new JsonArray();
      RecordBook.Category c = s.cat;
      if (c == RecordBook.Category.HARVESTS) {
         if (arch != null) {
            int i = 1;
            for (Map.Entry<String, Integer> e : arch.counts.entrySet()) {
               rows.add(row(i++, e.getKey(), e.getValue() + (e.getValue() == 1 ? " harvest" : " harvests"), "", e.getKey().equals(me), false, 0L));
            }
         } else {
            int i = 1;
            for (Map.Entry<UUID, Integer> e : book.counts(scope.equals("alltime"), 15)) {
               String n = book.names.getOrDefault(e.getKey(), "Hunter");
               rows.add(row(i++, n, e.getValue() + (e.getValue() == 1 ? " harvest" : " harvests"), "", e.getKey().equals(p.getUUID()), false, 0L));
            }
         }
      } else {
         List<HarvestRecord> l = arch != null ? arch.tops.getOrDefault(c, List.of()) : book.top(c, scope.equals("alltime"), 15);
         for (int i = 0; i < l.size(); i++) {
            HarvestRecord r = l.get(i);
            rows.add(row(i + 1, r.name, c.valueText(r), detail(c, r, scope.equals("alltime")), p.getUUID().equals(r.hunter), r.legendary, r.id));
         }
      }
      j.add("rows", rows);
      JsonArray recent = new JsonArray();
      for (HarvestRecord r : book.recent(8, x -> true)) {
         recent.add(r.name + " · " + r.headline() + " · " + r.dateText());
      }
      j.add("recent", recent);
      JsonArray mine = new JsonArray();
      for (HarvestRecord r : book.recent(6, x -> p.getUUID().equals(x.hunter))) {
         mine.add(r.headline() + " · " + r.dateText() + (r.guide.isEmpty() ? "" : " · guided by " + r.guide));
      }
      j.add("mine", mine);
      j.addProperty("seasonHarvests", book.current.size());
   }

   static String detail(RecordBook.Category c, HarvestRecord r, boolean allTime) {
      StringBuilder b = new StringBuilder();
      Quarry q = r.quarry();
      if (c.rack) {
         if (!r.pointsText().isEmpty()) {
            b.append(r.pointsText()).append(" · ");
         }
         b.append(Fmt.kg(r.weightKg));
      } else if (c == RecordBook.Category.LONGEST) {
         b.append(r.headline());
      } else {
         b.append(q.title);
      }
      if (r.shotM >= 1.0 && c != RecordBook.Category.LONGEST) {
         b.append(" · ").append(Fmt.metres(r.shotM));
      }
      b.append(" · ").append(r.dateText());
      if (allTime) {
         b.append(" · S").append(r.season);
      }
      if (!r.guide.isEmpty()) {
         b.append(" · guide ").append(r.guide);
      }
      if (r.called) {
         b.append(" · called in");
      }
      if (r.legendary && !r.legendName.isEmpty()) {
         b.append(" · ").append(r.legendName);
      }
      return b.toString();
   }

   static JsonObject row(int rank, String name, String value, String detail, boolean me, boolean legendary, long id) {
      JsonObject o = new JsonObject();
      o.addProperty("rank", rank);
      o.addProperty("name", name);
      o.addProperty("value", value);
      o.addProperty("detail", detail);
      o.addProperty("me", me);
      o.addProperty("legendary", legendary);
      o.addProperty("id", id);
      return o;
   }

   // ------------------------------------------------------------------ events tab
   static void events(ServerPlayer p, JsonObject j) {
      MinecraftServer server = p.server;
      HuntEvents ev = HuntEvents.get(server);
      long now = System.currentTimeMillis();
      String me = p.getGameProfile().getName();
      if (ev.active != null) {
         HuntEvents.Active a = ev.active;
         JsonObject o = new JsonObject();
         o.addProperty("title", a.type.title);
         o.addProperty("tagline", a.type.tagline);
         o.addProperty("rules", a.type.rules);
         o.addProperty("left", Math.max(0L, a.endMs - now));
         o.addProperty("ends", Instant.ofEpochMilli(a.endMs).atZone(CampsConfig.zone()).format(WHEN));
         o.addProperty("forced", a.forced);
         JsonArray rows = new JsonArray();
         List<HuntEvents.Entry> rank = a.ranking();
         for (int i = 0; i < Math.min(15, rank.size()); i++) {
            HuntEvents.Entry e = rank.get(i);
            rows.add(row(i + 1, e.name, a.type.valueText(e), "", e.id.equals(p.getUUID()), false, 0L));
         }
         o.add("rows", rows);
         HuntEvents.Entry mine = a.entries.get(p.getUUID());
         o.addProperty("mine", mine == null ? "No entry yet" : "You: " + Fmt.ordinal(rank.indexOf(mine) + 1) + " · " + a.type.valueText(mine));
         j.add("event", o);
      }
      j.addProperty("prizes", EventService.prizeLine());
      j.addProperty("enabled", CampsConfig.eventsEnabled);
      EventService.Window next = EventService.nextWindow(now);
      JsonObject n = new JsonObject();
      n.addProperty("title", next.type().title);
      n.addProperty("tagline", next.type().tagline);
      n.addProperty("rules", next.type().rules);
      n.addProperty("in", Math.max(0L, next.startMs() - now));
      n.addProperty("when", Instant.ofEpochMilli(next.startMs()).atZone(CampsConfig.zone()).format(WHEN) + " → " + Instant.ofEpochMilli(next.endMs()).atZone(CampsConfig.zone()).format(WHEN));
      j.add("next", n);
      j.addProperty("schedule", CampsConfig.title(CampsConfig.startDay) + " " + CampsConfig.startTime + " → " + CampsConfig.title(CampsConfig.endDay) + " " + CampsConfig.endTime + " · " + CampsConfig.zone().getId());
      JsonArray hist = new JsonArray();
      for (HuntEvents.Past past : ev.history) {
         if (hist.size() >= 5) {
            break;
         }
         JsonObject o = new JsonObject();
         o.addProperty("title", past.type.title);
         o.addProperty("when", Instant.ofEpochMilli(past.startMs).atZone(CampsConfig.zone()).format(DateTimeFormatter.ofPattern("d MMM", Locale.ROOT)));
         JsonArray pod = new JsonArray();
         past.podium.forEach(pod::add);
         o.add("podium", pod);
         hist.add(o);
      }
      j.add("history", hist);
      j.addProperty("meName", me);
   }
}
