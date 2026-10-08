package com.formaworks.frontierhunts.camps.client;

import com.formaworks.frontierhunts.camps.CampsActions;
import com.formaworks.frontierhunts.camps.CampsNet;
import com.formaworks.frontierhunts.camps.Fmt;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The hunting-camp ledger: Camp · Guided hunts · Records · Events. All content comes from the server as JSON
 * ({@link CampsNet.View}); every button sends a request that the server validates and answers with a fresh view.
 */
public final class CampScreen extends Screen {
   private static final int PAPER = 0xFFE8E0CB;
   private static final int PAPER_DARK = 0xFFDCD2B8;
   private static final int INK = 0xFF263D34;
   private static final int MUTED = 0xFF637365;
   private static final int GOLD = 0xFFAC8951;
   private static final int RUST = 0xFF91563F;
   private static final int MOSS = 0xFF477445;
   private static final int SIDE = 0xFF1C3028;
   private static final String[] TABS = new String[]{"camp", "guided", "records", "events"};
   private static final String[] TAB_TITLES = new String[]{"Camp", "Guided hunts", "Records", "Events"};
   private static final String[] COLOR_NAMES = new String[]{
      "White", "Orange", "Magenta", "Light blue", "Yellow", "Lime", "Pink", "Gray", "Light gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black"
   };
   static JsonObject state = new JsonObject();
   // form state survives refreshes
   private static String foundName = "";
   private static int foundColor = 13;
   private static String rename;
   private static int renameColor = -1;
   private static int inviteIdx;
   private static int offerPlayer;
   private static int offerSpecies;
   private static String offerScore = "120";
   private static String offerWeight = "0";
   private static String offerDays = "3";
   private static String offerFee = "40";
   private static String confirm = "";
   private static long confirmUntil;
   private int left;
   private int top;
   private int panelW;
   private int panelH;
   private int side;
   private int bodyX;
   private int bodyW;
   private int viewTop;
   private int viewBottom;
   private int cursor;
   private int scroll;
   private int maxScroll;
   private final List<CampScreen.Note> notes = new ArrayList<>();
   private final List<CampScreen.Box> boxes = new ArrayList<>();
   private final List<CampScreen.Control> controls = new ArrayList<>();

   public CampScreen() {
      super(Component.literal("Hunting Camp"));
   }

   // ------------------------------------------------------------------ networking
   public static void receive(CampsNet.View view) {
      JsonObject j;
      try {
         j = JsonParser.parseString(view.json()).getAsJsonObject();
      } catch (Exception e) {
         return;
      }
      state = j;
      Minecraft mc = Minecraft.getInstance();
      if (mc.screen instanceof CampScreen s) {
         s.refresh();
      } else if (bool(j, "open")) {
         mc.setScreen(new CampScreen());
      }
   }

   public static void clearState() {
      state = new JsonObject();
      rename = null;
      renameColor = -1;
      confirm = "";
   }

   public static void send(int op, String... args) {
      PacketDistributor.sendToServer(new CampsNet.Req(op, List.of(args)), new CustomPacketPayload[0]);
   }

   private String tab() {
      return str(state, "tab", "camp");
   }

   // ------------------------------------------------------------------ layout
   @Override
   protected void init() {
      this.refresh();
   }

   public void refresh() {
      if (this.font == null) {
         return;
      }
      int keepScroll = this.scroll;
      this.clearWidgets();
      this.notes.clear();
      this.boxes.clear();
      this.controls.clear();
      this.panelW = Math.min(600, this.width - 16);
      this.panelH = Math.min(400, this.height - 16);
      this.left = (this.width - this.panelW) / 2;
      this.top = (this.height - this.panelH) / 2;
      this.side = this.panelW < 460 ? 98 : 124;
      this.bodyX = this.left + this.side + 16;
      this.bodyW = this.panelW - this.side - 34;
      this.viewTop = this.top + 60;
      this.viewBottom = this.top + this.panelH - 24;
      this.cursor = 0;
      int tabH = 24;
      for (int i = 0; i < TABS.length; i++) {
         String t = TABS[i];
         boolean sel = t.equals(this.tab());
         this.addRenderableWidget(new CampScreen.LedgerButton(this.left + 7, this.top + 58 + i * (tabH + 3), this.side - 14, tabH, TAB_TITLES[i], b -> {
            this.scroll = 0;
            if (t.equals("records")) {
               send(CampsNet.OPEN, t, str(state, "cat", "WHITETAIL"), str(state, "scope", "season"), "0");
            } else {
               send(CampsNet.OPEN, t);
            }
         }, sel));
      }
      this.addRenderableWidget(new CampScreen.LedgerButton(this.left + this.panelW - 28, this.top + 9, 19, 19, "×", b -> this.onClose(), false));
      if (!state.has("tab")) {
         this.line("Opening the camp ledger…", MUTED);
      } else {
         switch (this.tab()) {
            case "guided" -> this.buildGuided();
            case "records" -> this.buildRecords();
            case "events" -> this.buildEvents();
            default -> this.buildCamp();
         }
      }
      this.maxScroll = Math.max(0, this.cursor - (this.viewBottom - this.viewTop) + 8);
      this.scroll = Math.clamp(keepScroll, 0, this.maxScroll);
      this.positionControls();
   }

   // ------------------------------------------------------------------ building blocks
   private void gap(int h) {
      this.cursor += h;
   }

   private void line(String text, int color) {
      this.notes.add(new CampScreen.Note(Component.literal(text).getVisualOrderText(), 0, this.cursor, color));
      this.cursor += 11;
   }

   private void lineAt(String text, int x, int y, int color) {
      this.notes.add(new CampScreen.Note(Component.literal(text).getVisualOrderText(), x, y, color));
   }

   private void wrap(String text, int color) {
      for (FormattedCharSequence s : this.font.split(Component.literal(text), this.bodyW - 6)) {
         this.notes.add(new CampScreen.Note(s, 0, this.cursor, color));
         this.cursor += 10;
      }
      this.cursor += 2;
   }

   private void heading(String text) {
      this.gap(6);
      this.notes.add(new CampScreen.Note(Component.literal(text.toUpperCase(Locale.ROOT)).getVisualOrderText(), 0, this.cursor, GOLD));
      this.boxes.add(new CampScreen.Box(0, this.cursor + 11, this.bodyW - 6, 1, 0x70AC8951));
      this.cursor += 16;
   }

   private Button button(int x, int w, String label, boolean active, Button.OnPress press) {
      CampScreen.LedgerButton b = new CampScreen.LedgerButton(this.bodyX + x, 0, w, 18, label, press, false);
      b.active = active;
      this.addRenderableWidget(b);
      this.controls.add(new CampScreen.Control(b, this.cursor));
      return b;
   }

   private EditBox field(int x, int w, String value, int max, Consumer<String> onChange) {
      EditBox e = new EditBox(this.font, this.bodyX + x, 0, w, 16, Component.empty());
      e.setMaxLength(max);
      e.setValue(value == null ? "" : value);
      e.setResponder(onChange);
      e.setTextColor(0xFFEEE6D3);
      this.addRenderableWidget(e);
      this.controls.add(new CampScreen.Control(e, this.cursor + 1));
      return e;
   }

   /** A button that asks for a second click within 4 seconds before sending. */
   private void confirmButton(int x, int w, String label, String key, Runnable action) {
      boolean armed = confirm.equals(key) && System.currentTimeMillis() < confirmUntil;
      this.button(x, w, armed ? "Click again to confirm" : label, true, b -> {
         if (confirm.equals(key) && System.currentTimeMillis() < confirmUntil) {
            confirm = "";
            action.run();
         } else {
            confirm = key;
            confirmUntil = System.currentTimeMillis() + 4000L;
            this.refresh();
         }
      });
   }

   private void swatch(int x, int y, int dye) {
      int c = DyeColor.byId(Math.floorMod(dye, 16)).getTextureDiffuseColor() | 0xFF000000;
      this.boxes.add(new CampScreen.Box(x - 1, y - 1, 12, 12, 0xFF3A2E1E));
      this.boxes.add(new CampScreen.Box(x, y, 10, 10, c));
   }

   // ------------------------------------------------------------------ CAMP
   private void buildCamp() {
      JsonObject camp = state.has("camp") && state.get("camp").isJsonObject() ? state.getAsJsonObject("camp") : null;
      JsonObject post = state.has("post") && state.get("post").isJsonObject() ? state.getAsJsonObject("post") : null;
      if (post != null) {
         if (!bool(post, "bound")) {
            this.heading("Unclaimed camp post");
            if (camp == null) {
               this.wrap("Name your camp below to raise its flag on this post.", INK);
            } else {
               this.wrap("Raise " + str(camp, "name", "your camp") + "'s flag here. " + (str(camp, "post", "").isEmpty() ? "" : "The flag moves from its current post."), INK);
               this.button(0, 180, "Raise our flag here", bool(camp, "owner") || str(camp, "post", "").isEmpty(), b -> send(CampsNet.CAMP_BIND));
               this.gap(22);
            }
         } else if (!bool(post, "mine")) {
            this.heading("Camp post");
            this.swatch(0, this.cursor, num(post, "color"));
            this.lineAt(str(post, "campName", "?"), 16, this.cursor + 1, INK);
            this.gap(13);
            this.line(str(post, "rank", "") + " · " + num(post, "members") + " hunters", MUTED);
            if (camp == null) {
               this.button(0, 140, "Ask to join", true, b -> send(CampsNet.CAMP_ASK, str(post, "campId", "")));
               this.gap(22);
            } else if (bool(camp, "owner")) {
               this.wrap("This post flies another camp's flag. Place your own Camp Post to raise yours.", MUTED);
            }
         }
      }
      JsonArray invites = arr(state, "invites");
      if (camp == null && !invites.isEmpty()) {
         this.heading("Invitations");
         for (JsonElement el : invites) {
            JsonObject inv = el.getAsJsonObject();
            this.swatch(0, this.cursor + 4, num(inv, "color"));
            this.lineAt(str(inv, "name", "?") + "  ·  " + num(inv, "members") + " hunters", 16, this.cursor + 5, INK);
            String id = str(inv, "id", "");
            this.button(this.bodyW - 150, 70, "Join", true, b -> send(CampsNet.CAMP_ACCEPT, id));
            this.button(this.bodyW - 76, 70, "Decline", true, b -> send(CampsNet.CAMP_DECLINE, id));
            this.gap(22);
         }
      }
      if (camp == null) {
         this.heading("Found a camp");
         this.wrap("A camp is your crew on this server: a shared rank and stations, a flag with your name on it, news of every animal your campmates tag, and an optional respawn at the post. There are no claims — everything stays open.", INK);
         this.line("Camp name", MUTED);
         this.field(0, Math.min(220, this.bodyW - 10), foundName, 24, s -> foundName = s);
         this.gap(22);
         this.swatch(0, this.cursor + 4, foundColor);
         this.button(16, 150, "Colour: " + COLOR_NAMES[foundColor], true, b -> {
            foundColor = (foundColor + 1) % 16;
            this.refresh();
         });
         this.button(172, 120, "Found camp", true, b -> send(CampsNet.CAMP_FOUND, foundName.trim(), Integer.toString(foundColor)));
         this.gap(24);
         this.wrap("Tip: craft a Camp Post (sticks, wool, cobblestone) and place it where you want camp — the flag goes up the moment you name it.", MUTED);
         return;
      }
      // --- camp overview
      boolean owner = bool(camp, "owner");
      this.gap(2);
      this.swatch(0, this.cursor + 2, num(camp, "color"));
      this.lineAt(str(camp, "name", "Camp"), 16, this.cursor + 3, INK);
      this.gap(16);
      int tier = num(camp, "tier");
      this.line(str(camp, "rank", "") + "  ·  rank " + roman(tier) + " / IV  ·  " + arr(camp, "members").size() + " of " + num(camp, "max") + " hunters  ·  " + num(camp, "harvests") + " harvests", MUTED);
      String postAt = str(camp, "post", "");
      this.line(postAt.isEmpty() ? "No flag raised yet — place a Camp Post to mark camp." : "Flag flies at " + postAt, postAt.isEmpty() ? RUST : INK);
      if (!str(camp, "best", "").isEmpty()) {
         this.line("Camp best: " + str(camp, "best", ""), INK);
      }
      this.gap(4);
      boolean respawn = bool(camp, "respawn");
      this.button(0, 196, respawn ? "Respawn at camp post: ON" : "Respawn at camp post: OFF", !postAt.isEmpty() || respawn, b -> send(CampsNet.CAMP_RESPAWN, respawn ? "0" : "1"));
      this.gap(24);
      // --- members
      this.heading("Hunters in camp");
      for (JsonElement el : arr(camp, "members")) {
         JsonObject m = el.getAsJsonObject();
         boolean online = bool(m, "online");
         this.boxes.add(new CampScreen.Box(2, this.cursor + 7, 5, 5, online ? 0xFF5F9A4E : 0xFF9A9486));
         String tags = (bool(m, "owner") ? "  · runs camp" : "") + (bool(m, "respawn") ? "  · respawns here" : "");
         this.lineAt(str(m, "name", "?") + tags, 12, this.cursor + 5, online ? INK : MUTED);
         if (owner && !bool(m, "owner")) {
            String id = str(m, "id", "");
            this.confirmButton(this.bodyW - 116, 110, "Remove", "kick:" + id, () -> send(CampsNet.CAMP_KICK, id));
         }
         this.gap(20);
      }
      JsonArray online = arr(state, "online");
      this.gap(4);
      if (online.isEmpty()) {
         this.line("Invite: nobody else is online right now.", MUTED);
      } else {
         inviteIdx = Math.floorMod(inviteIdx, online.size());
         String who = online.get(inviteIdx).getAsString();
         this.button(0, 180, "Invite: " + who + (online.size() > 1 ? "  ›" : ""), online.size() > 1, b -> {
            inviteIdx++;
            this.refresh();
         });
         this.button(186, 90, "Send invite", true, b -> send(CampsNet.CAMP_INVITE, who));
         this.gap(22);
      }
      JsonArray invited = arr(camp, "invited");
      if (!invited.isEmpty()) {
         this.line("Invited: " + join(invited), MUTED);
      }
      // --- outfitting
      this.heading("Outfit the camp");
      int cost = num(camp, "nextCost");
      if (cost > 0) {
         this.wrap("Next: " + str(camp, "nextUpgrade", "") + " for " + cost + " tokens. The station goes into your pack; the camp's rank rises for everyone in camp.", INK);
         this.button(0, 160, "Outfit (" + cost + " tokens)", num(state, "tokens") >= cost, b -> send(CampsNet.CAMP_UPGRADE));
         this.gap(22);
      } else {
         this.line("Fully outfitted — a frontier lodge.", MOSS);
      }
      this.wrap("Camp stations are shared: anyone in camp can use the smokehouse, tanning rack, lodge stores, racks and benches at camp." /* [benches] */, MUTED);
      // --- settings
      if (owner) {
         this.heading("Camp name & colours");
         if (rename == null) {
            rename = str(camp, "name", "");
         }
         if (renameColor < 0) {
            renameColor = num(camp, "color");
         }
         this.field(0, Math.min(200, this.bodyW - 10), rename, 24, s -> rename = s);
         this.gap(22);
         this.swatch(0, this.cursor + 4, renameColor);
         this.button(16, 150, "Colour: " + COLOR_NAMES[renameColor], true, b -> {
            renameColor = (renameColor + 1) % 16;
            this.refresh();
         });
         this.button(172, 80, "Save", true, b -> {
            send(CampsNet.CAMP_EDIT, rename.trim(), Integer.toString(renameColor));
            rename = null;
            renameColor = -1;
         });
         this.gap(24);
         this.confirmButton(0, 196, "Strike camp (disband)", "disband", () -> send(CampsNet.CAMP_DISBAND));
         this.gap(22);
         this.wrap("Leaving as the owner hands the camp to the longest-serving hunter; the last one out strikes it.", MUTED);
      } else {
         this.gap(6);
         this.confirmButton(0, 160, "Leave camp", "leave", () -> send(CampsNet.CAMP_LEAVE));
         this.gap(22);
      }
      JsonArray log = arr(camp, "log");
      if (!log.isEmpty()) {
         this.heading("Camp log");
         for (JsonElement e : log) {
            this.wrap(e.getAsString(), MUTED);
         }
      }
   }

   // ------------------------------------------------------------------ GUIDED HUNTS
   private void buildGuided() {
      JsonArray hunts = arr(state, "hunts");
      this.heading("Your contracts");
      if (hunts.isEmpty()) {
         this.line("No open contracts.", MUTED);
      }
      for (JsonElement el : hunts) {
         JsonObject h = el.getAsJsonObject();
         String role = str(h, "role", "");
         String st = str(h, "state", "");
         String id = Long.toString(h.get("id").getAsLong());
         long left = h.get("left").getAsLong();
         String days = String.format(Locale.ROOT, "%.1f", h.get("leftDays").getAsDouble());
         int y0 = this.cursor;
         this.gap(3);
         switch (role + ":" + st) {
            case "client:OFFERED" -> {
               this.line("Offer from " + str(h, "guide", "?"), INK);
               this.line(str(h, "terms", ""), GOLD);
               this.line("Fee " + num(h, "fee") + " tokens (escrowed) · " + num(h, "days") + " reserve days · lapses in " + Fmt.duration(left), MUTED);
               this.button(0, 150, "Accept & pay " + num(h, "fee"), num(state, "tokens") >= num(h, "fee"), b -> send(CampsNet.GUIDE_ACCEPT, id));
               this.button(156, 80, "Decline", true, b -> send(CampsNet.GUIDE_DECLINE, id));
               this.gap(22);
            }
            case "guide:OFFERED" -> {
               this.line("Offer to " + str(h, "client", "?") + " · waiting for a reply", INK);
               this.line(str(h, "terms", ""), GOLD);
               this.line("Fee " + num(h, "fee") + " · " + num(h, "days") + " reserve days · lapses in " + Fmt.duration(left), MUTED);
               this.button(0, 100, "Withdraw", true, b -> send(CampsNet.GUIDE_CANCEL, id));
               this.gap(22);
            }
            case "client:ACTIVE" -> {
               this.line("Guided by " + str(h, "guide", "?"), INK);
               this.line(str(h, "terms", ""), GOLD);
               this.line(num(h, "escrow") + " tokens in escrow · " + days + " reserve days left (" + Fmt.duration(left) + ")", MUTED);
               this.wrap("Take the animal with " + str(h, "guide", "your guide") + " within " + num(state, "range") + " m and the fee is paid. Time out: full refund.", MUTED);
               this.confirmButton(0, 190, "Call off (75% refunded)", "cancel:" + id, () -> send(CampsNet.GUIDE_CANCEL, id));
               this.gap(22);
            }
            case "guide:ACTIVE" -> {
               this.line("Guiding " + str(h, "client", "?"), INK);
               this.line(str(h, "terms", ""), GOLD);
               this.line(num(h, "escrow") + " tokens paid on success · " + days + " reserve days left (" + Fmt.duration(left) + ")", MUTED);
               this.confirmButton(0, 190, "Release client (refund)", "cancel:" + id, () -> send(CampsNet.GUIDE_CANCEL, id));
               this.gap(22);
            }
            default -> {
               this.line(str(h, "guide", "Outfitter"), INK);
               this.line(str(h, "terms", ""), GOLD);
               this.line("Purse " + num(h, "reward") + " + " + num(h, "deposit") + " deposit back · " + days + " reserve days left (" + Fmt.duration(left) + ")", MUTED);
               this.confirmButton(0, 200, "Abandon (lose deposit)", "cancel:" + id, () -> send(CampsNet.GUIDE_CANCEL, id));
               this.gap(22);
            }
         }
         this.boxes.add(new CampScreen.Box(-6, y0, 2, this.cursor - y0 - 2, role.equals("guide") ? MOSS : GOLD));
         this.gap(4);
      }
      // --- offer form
      this.heading("Offer to guide a hunter");
      JsonArray players = arr(state, "players");
      JsonArray species = arr(state, "species");
      if (players.isEmpty()) {
         this.wrap("Nobody else is online. When a friend joins, offer to guide them: you set the quarry, the minimum score or weight, the time limit and your fee. The fee sits in escrow until the hunt ends.", MUTED);
      } else {
         offerPlayer = Math.floorMod(offerPlayer, players.size());
         offerSpecies = Math.floorMod(offerSpecies, Math.max(1, species.size()));
         String who = players.get(offerPlayer).getAsString();
         JsonObject sp = species.get(offerSpecies).getAsJsonObject();
         boolean rack = bool(sp, "rack");
         int half = Math.min(170, (this.bodyW - 16) / 2);
         this.button(0, half, "Client: " + who + (players.size() > 1 ? "  ›" : ""), players.size() > 1, b -> {
            offerPlayer++;
            this.refresh();
         });
         this.button(half + 6, half, "Quarry: " + str(sp, "title", "?") + "  ›", true, b -> {
            offerSpecies++;
            this.refresh();
         });
         this.gap(24);
         int fw = Math.min(64, (this.bodyW - 20) / 4 - 4);
         String[] labels = rack ? new String[]{"Min score \"", "Min kg", "Days", "Fee"} : new String[]{"—", "Min kg", "Days", "Fee"};
         for (int i = 0; i < 4; i++) {
            this.lineAt(labels[i], i * (fw + 10), this.cursor, MUTED);
         }
         this.gap(11);
         if (rack) {
            this.field(0, fw, offerScore, 3, s -> offerScore = digits(s));
         }
         this.field(fw + 10, fw, offerWeight, 4, s -> offerWeight = digits(s));
         this.field((fw + 10) * 2, fw, offerDays, 2, s -> offerDays = digits(s));
         this.field((fw + 10) * 3, fw, offerFee, 3, s -> offerFee = digits(s));
         this.gap(22);
         this.button(0, 150, "Send offer to " + who, true, b -> send(CampsNet.GUIDE_OFFER, who, str(sp, "id", "whitetail"), rack ? offerScore : "0", offerWeight, offerDays, offerFee));
         this.gap(22);
         this.wrap("The client must take the animal while you are within " + num(state, "range") + " m. Days are reserve (in-game) days; fees up to 500 tokens.", MUTED);
      }
      // --- outfitter board
      boolean near = bool(state, "nearBoard");
      this.heading("Outfitter board" + (near ? "" : " · book at a Contract Board"));
      this.wrap("Outfitters post three contracts every reserve day. Put down a small deposit; fill it in time for the purse plus your deposit back.", MUTED);
      boolean busy = bool(state, "busy");
      for (JsonElement el : arr(state, "outfitters")) {
         JsonObject o = el.getAsJsonObject();
         int y0 = this.cursor;
         this.gap(2);
         this.line(str(o, "guide", "") + " · " + str(o, "outfitter", ""), INK);
         this.wrap(str(o, "story", ""), MUTED);
         this.line(str(o, "terms", ""), GOLD);
         this.line("Deposit " + num(o, "deposit") + " · purse " + num(o, "reward") + " · " + num(o, "days") + " reserve days", MUTED);
         String key = str(o, "key", "");
         boolean booked = bool(o, "booked");
         this.button(0, 120, booked ? "Booked" : "Book (" + num(o, "deposit") + ")", near && !busy && !booked && num(state, "tokens") >= num(o, "deposit"), b -> send(CampsNet.NPC_BOOK, key));
         this.gap(22);
         this.boxes.add(new CampScreen.Box(-6, y0, 2, this.cursor - y0 - 2, 0xFF8B7A55));
         this.gap(4);
      }
      if (busy) {
         this.line("You are already on a guided hunt.", RUST);
      }
      JsonArray hist = arr(state, "history");
      if (!hist.isEmpty()) {
         this.heading("Past contracts");
         for (JsonElement e : hist) {
            this.wrap(e.getAsString(), MUTED);
         }
      }
   }

   // ------------------------------------------------------------------ RECORDS
   private void buildRecords() {
      String cat = str(state, "cat", "WHITETAIL");
      String scope = str(state, "scope", "season");
      JsonArray cats = arr(state, "cats");
      int perRow = 4;
      int bw = (this.bodyW - 6 - (perRow - 1) * 4) / perRow;
      for (int i = 0; i < cats.size(); i++) {
         JsonObject c = cats.get(i).getAsJsonObject();
         String id = str(c, "id", "");
         CampScreen.LedgerButton b = (CampScreen.LedgerButton)this.button(i % perRow * (bw + 4), bw, str(c, "tab", id), true, x -> send(CampsNet.OPEN, "records", id, scope, "0"));
         b.selected = id.equals(cat);
         if (i % perRow == perRow - 1 || i == cats.size() - 1) {
            this.gap(21);
         }
      }
      this.gap(3);
      JsonArray archives = arr(state, "archives");
      int sw = Math.min(110, (this.bodyW - 12) / 3);
      ((CampScreen.LedgerButton)this.button(0, sw, "This season", true, b -> send(CampsNet.OPEN, "records", cat, "season", "0"))).selected = scope.equals("season");
      ((CampScreen.LedgerButton)this.button(sw + 4, sw, "All time", true, b -> send(CampsNet.OPEN, "records", cat, "alltime", "0"))).selected = scope.equals("alltime");
      ((CampScreen.LedgerButton)this.button((sw + 4) * 2, sw, "Past seasons", !archives.isEmpty(), b -> send(CampsNet.OPEN, "records", cat, "archive", "0"))).selected = scope.equals("archive");
      this.gap(22);
      String title = "";
      for (JsonElement el : cats) {
         if (str(el.getAsJsonObject(), "id", "").equals(cat)) {
            title = str(el.getAsJsonObject(), "title", "");
         }
      }
      if (scope.equals("archive")) {
         int idx = num(state, "archiveIdx");
         this.button(0, 24, "‹", idx < archives.size() - 1, b -> send(CampsNet.OPEN, "records", cat, "archive", Integer.toString(idx + 1)));
         this.lineAt("Season " + num(state, "archiveSeason") + " (final)", 32, this.cursor + 5, INK);
         this.button(150, 24, "›", idx > 0, b -> send(CampsNet.OPEN, "records", cat, "archive", Integer.toString(idx - 1)));
         this.gap(22);
      }
      this.heading(title + " · " + (scope.equals("alltime") ? "all time" : (scope.equals("archive") ? "season " + num(state, "archiveSeason") : "season " + num(state, "season"))));
      JsonArray rows = arr(state, "rows");
      if (rows.isEmpty()) {
         this.wrap(scope.equals("season") ? "No entries yet this season. Every recovered animal is entered automatically — the first one leads." : "No entries.", MUTED);
      }
      for (JsonElement el : rows) {
         JsonObject r = el.getAsJsonObject();
         int rank = num(r, "rank");
         boolean me = bool(r, "me");
         String detail = str(r, "detail", "");
         int h = detail.isEmpty() ? 14 : 24;
         if (me) {
            this.boxes.add(new CampScreen.Box(-4, this.cursor - 2, this.bodyW - 2, h, 0x40AC8951));
         } else if (rank % 2 == 0) {
            this.boxes.add(new CampScreen.Box(-4, this.cursor - 2, this.bodyW - 2, h, 0x18263D34));
         }
         this.lineAt(rank + ".", 0, this.cursor + 1, rank <= 3 ? GOLD : MUTED);
         this.lineAt(str(r, "name", "") + (bool(r, "legendary") ? "  ★" : ""), 24, this.cursor + 1, INK);
         String val = str(r, "value", "");
         this.lineAt(val, this.bodyW - 12 - this.font.width(val), this.cursor + 1, rank == 1 ? GOLD : INK);
         if (!detail.isEmpty()) {
            this.lineAt(this.font.plainSubstrByWidth(detail, this.bodyW - 36), 24, this.cursor + 11, MUTED);
         }
         this.gap(h + 1);
      }
      JsonArray mine = arr(state, "mine");
      if (!mine.isEmpty()) {
         this.heading("Your latest harvests");
         for (JsonElement e : mine) {
            this.wrap(e.getAsString(), INK);
         }
      }
      JsonArray recent = arr(state, "recent");
      this.heading("Latest in the reserve · " + num(state, "seasonHarvests") + " this season");
      if (recent.isEmpty()) {
         this.line("Quiet so far.", MUTED);
      }
      for (JsonElement e : recent) {
         this.wrap(e.getAsString(), MUTED);
      }
      this.gap(4);
      this.wrap("Seasons run September to August on the reserve calendar. When a new season opens the board is archived and the champions announced.", MUTED);
   }

   // ------------------------------------------------------------------ EVENTS
   private void buildEvents() {
      JsonObject ev = state.has("event") && state.get("event").isJsonObject() ? state.getAsJsonObject("event") : null;
      if (ev != null) {
         this.gap(2);
         this.line(str(ev, "title", "").toUpperCase(Locale.ROOT) + "  ·  LIVE", GOLD);
         this.line(str(ev, "tagline", ""), INK);
         this.wrap(str(ev, "rules", ""), MUTED);
         this.line("Ends " + str(ev, "ends", "") + "  ·  " + Fmt.duration(ev.get("left").getAsLong()) + " left", INK);
         this.line(str(ev, "mine", ""), MOSS);
         this.line("Prizes: " + str(state, "prizes", ""), MUTED);
         this.heading("Standings");
         JsonArray rows = arr(ev, "rows");
         if (rows.isEmpty()) {
            this.line("No entries yet — be the first on the board.", MUTED);
         }
         for (JsonElement el : rows) {
            JsonObject r = el.getAsJsonObject();
            int rank = num(r, "rank");
            if (bool(r, "me")) {
               this.boxes.add(new CampScreen.Box(-4, this.cursor - 2, this.bodyW - 2, 13, 0x40AC8951));
            }
            this.lineAt(rank + ".", 0, this.cursor, rank <= 3 ? GOLD : MUTED);
            this.lineAt(str(r, "name", ""), 24, this.cursor, INK);
            String val = str(r, "value", "");
            this.lineAt(val, this.bodyW - 12 - this.font.width(val), this.cursor, rank == 1 ? GOLD : INK);
            this.gap(13);
         }
      } else {
         this.heading("No event running");
         if (bool(state, "enabled")) {
            JsonObject n = state.getAsJsonObject("next");
            this.line("Next: " + str(n, "title", "") + " · in " + Fmt.duration(n.get("in").getAsLong()), INK);
            this.line(str(n, "when", ""), MUTED);
            this.line(str(n, "tagline", ""), GOLD);
            this.wrap(str(n, "rules", ""), MUTED);
            this.line("Prizes: " + str(state, "prizes", ""), MUTED);
         } else {
            this.wrap("Weekend events are switched off on this server.", MUTED);
         }
      }
      this.gap(4);
      this.line("Weekend window: " + str(state, "schedule", ""), MUTED);
      this.wrap("The rotation: Big Buck Weekend (best whitetail rack), Predator Weekend (coyote 1, wolf 2, cougar 3), Waterfowl Weekend (ducks & grouse), Rut Rally (deer that answered your call). Hold the player-list key in the field to see the live standings.", MUTED);
      JsonArray hist = arr(state, "history");
      if (!hist.isEmpty()) {
         this.heading("Past events");
         for (JsonElement el : hist) {
            JsonObject h = el.getAsJsonObject();
            this.line(str(h, "title", "") + " · " + str(h, "when", ""), INK);
            JsonArray pod = arr(h, "podium");
            if (pod.isEmpty()) {
               this.line("   nobody placed", MUTED);
            }
            for (JsonElement p : pod) {
               this.line("   " + p.getAsString(), MUTED);
            }
         }
      }
      if (bool(state, "op")) {
         this.heading("Admin");
         this.wrap("/huntevent start big_buck 2h · /huntevent stop (award) · /huntevent cancel · /huntevent config timezone America/Chicago · /huntevent config rotation big_buck,predator · /records board spawn", MUTED);
      }
   }

   // ------------------------------------------------------------------ render
   private void positionControls() {
      for (CampScreen.Control c : this.controls) {
         int y = this.viewTop + c.y - this.scroll;
         c.widget.setY(y);
         c.widget.visible = y >= this.viewTop && y + c.widget.getHeight() <= this.viewBottom;
      }
   }

   @Override
   public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
   }

   @Override
   public void render(GuiGraphics g, int mx, int my, float pt) {
      g.fill(0, 0, this.width, this.height, 0xB00C1512);
      g.fill(this.left + 4, this.top + 4, this.left + this.panelW + 4, this.top + this.panelH + 4, 0x80000000);
      g.fill(this.left, this.top, this.left + this.panelW, this.top + this.panelH, PAPER);
      g.fill(this.left + this.side, this.top + this.panelH - 3, this.left + this.panelW, this.top + this.panelH, PAPER_DARK);
      g.fill(this.left, this.top, this.left + this.side, this.top + this.panelH, SIDE);
      g.fill(this.left + this.side, this.top, this.left + this.side + 2, this.top + this.panelH, GOLD);
      g.drawString(this.font, "FRONTIER", this.left + 10, this.top + 14, 0xFFEEE5CE, false);
      g.drawString(this.font, this.font.plainSubstrByWidth("HUNTING CAMP", this.side - 16), this.left + 10, this.top + 27, 0xFFB4C9B8, false);
      int idx = Math.max(0, Arrays.asList(TABS).indexOf(this.tab()));
      g.drawString(this.font, TAB_TITLES[idx].toUpperCase(Locale.ROOT), this.bodyX, this.top + 14, INK, false);
      String meta = num(state, "tokens") + " TOKENS  ·  SEASON " + Math.max(1, num(state, "season"));
      g.drawString(this.font, meta, this.bodyX, this.top + 30, MUTED, false);
      g.fill(this.bodyX, this.top + 46, this.left + this.panelW - 14, this.top + 47, 0xFFBDB99F);
      g.enableScissor(this.bodyX - 8, this.viewTop - 2, this.left + this.panelW - 10, this.viewBottom);
      for (CampScreen.Box b : this.boxes) {
         int y = this.viewTop + b.y - this.scroll;
         g.fill(this.bodyX + b.x, y, this.bodyX + b.x + b.w, y + b.h, b.color);
      }
      for (CampScreen.Note n : this.notes) {
         int y = this.viewTop + n.y - this.scroll;
         if (y + 10 >= this.viewTop && y < this.viewBottom) {
            g.drawString(this.font, n.text, this.bodyX + n.x, y, n.color, false);
         }
      }
      g.disableScissor();
      if (this.maxScroll > 0) {
         int track = this.viewBottom - this.viewTop;
         int thumb = Math.max(14, track * track / (track + this.maxScroll));
         int ty = this.viewTop + (track - thumb) * this.scroll / this.maxScroll;
         g.fill(this.left + this.panelW - 8, this.viewTop, this.left + this.panelW - 6, this.viewBottom, 0xFFC7C2AA);
         g.fill(this.left + this.panelW - 8, ty, this.left + this.panelW - 6, ty + thumb, GOLD);
      }
      g.drawString(this.font, this.maxScroll > 0 ? "SCROLL FOR MORE · ESC TO CLOSE" : "ESC TO CLOSE", this.bodyX, this.top + this.panelH - 15, MUTED, false);
      super.render(g, mx, my, pt);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double dx, double dy) {
      this.scroll = Math.clamp(this.scroll - (int)(dy * 24.0), 0, this.maxScroll);
      this.positionControls();
      return true;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void removed() {
      super.removed();
      if (Minecraft.getInstance().getConnection() != null) {
         send(CampsActions.CLOSE);
      }
   }

   // ------------------------------------------------------------------ json helpers
   static boolean bool(JsonObject o, String k) {
      try {
         return o != null && o.has(k) && o.get(k).getAsBoolean();
      } catch (Exception e) {
         return false;
      }
   }

   static int num(JsonObject o, String k) {
      try {
         return o != null && o.has(k) ? o.get(k).getAsInt() : 0;
      } catch (Exception e) {
         return 0;
      }
   }

   static String str(JsonObject o, String k, String d) {
      try {
         return o != null && o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : d;
      } catch (Exception e) {
         return d;
      }
   }

   static JsonArray arr(JsonObject o, String k) {
      return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
   }

   static String join(JsonArray a) {
      List<String> l = new ArrayList<>();
      a.forEach(e -> l.add(e.getAsString()));
      return String.join(", ", l);
   }

   static String digits(String s) {
      return s.replaceAll("[^0-9]", "");
   }

   static String roman(int n) {
      return switch (n) {
         case 1 -> "I";
         case 2 -> "II";
         case 3 -> "III";
         case 4 -> "IV";
         default -> "0";
      };
   }

   private record Note(FormattedCharSequence text, int x, int y, int color) {
   }

   private record Box(int x, int y, int w, int h, int color) {
   }

   private record Control(AbstractWidget widget, int y) {
   }

   static final class LedgerButton extends Button {
      boolean selected;

      LedgerButton(int x, int y, int w, int h, String label, Button.OnPress press, boolean selected) {
         super(x, y, w, h, Component.literal(label), press, DEFAULT_NARRATION);
         this.selected = selected;
      }

      @Override
      protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
         int bg = !this.active ? 0xFF8B9380 : (this.isHoveredOrFocused() ? 0xFF4F6B53 : (this.selected ? 0xFF3D5944 : 0xFF2B4335));
         g.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bg);
         if (this.selected || this.isFocused()) {
            g.fill(this.getX(), this.getY(), this.getX() + 2, this.getY() + this.height, GOLD);
         }
         this.renderScrollingString(g, Minecraft.getInstance().font, 5, this.active ? 0xFFEEE6D3 : 0xFFD9DCCB);
      }
   }
}
