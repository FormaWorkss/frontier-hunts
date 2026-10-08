package com.formaworks.frontierhunts.camps.client;

import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camps.CampsNet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Client copy of the public live state: Big-Buck Board standings and the running weekend event. */
public final class LiveCache {
   public enum Board {
      WHITETAIL,
      ELK,
      MOOSE
   }

   public static final class Entry {
      public String name = "";
      public double score;
      public int pl;
      public int pr;
      public String date = "";
      public boolean legendary;
      public String species = "whitetail";
      public CompoundTag traits;
      private ItemStack trophy;

      /** A display-only trophy item carrying the harvested animal's traits (null when no rack data). */
      public ItemStack trophy() {
         if (this.trophy == null && this.traits != null) {
            ItemStack s = new ItemStack(HuntContent.WHITETAIL_TROPHY.get());
            CompoundTag t = new CompoundTag();
            t.put("deer_traits", this.traits);
            t.putString("species", "frontierhunts:" + this.species);
            s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
            this.trophy = s;
         }
         return this.trophy;
      }
   }

   public static int season = 1;
   public static final Map<Board, List<Entry>> BOARDS = new EnumMap<>(Board.class);
   public static boolean eventActive;
   public static String eventTitle = "";
   public static String eventTagline = "";
   public static String eventType = "";
   /** client wall-clock ms when the event ends / the next one starts */
   public static long eventEndsAt;
   public static long nextAt;
   public static final List<String[]> EVENT_TOP = new ArrayList<>();
   /** client ms of the last change worth flashing on the HUD */
   public static long flashUntil;
   private static String lastLeader = "";

   private LiveCache() {
   }

   public static List<Entry> board(Board b) {
      return BOARDS.getOrDefault(b, List.of());
   }

   public static void accept(CampsNet.Live live) {
      CompoundTag t = live.tag();
      long now = System.currentTimeMillis();
      season = Math.max(1, t.getInt("season"));
      CompoundTag boards = t.getCompound("boards");
      for (Board b : Board.values()) {
         List<Entry> l = new ArrayList<>();
         for (Tag x : boards.getList(b.name(), 10)) {
            CompoundTag c = (CompoundTag)x;
            Entry e = new Entry();
            e.name = c.getString("name");
            e.score = c.getDouble("score");
            e.pl = c.getInt("pl");
            e.pr = c.getInt("pr");
            e.date = c.getString("date");
            e.legendary = c.getBoolean("legendary");
            e.species = c.getString("species");
            e.traits = c.contains("traits", 10) ? c.getCompound("traits") : null;
            if (l.size() < 10) {
               l.add(e);
            }
         }
         BOARDS.put(b, l);
      }
      CompoundTag ev = t.getCompound("event");
      boolean wasActive = eventActive;
      eventActive = ev.getBoolean("active");
      eventTitle = ev.getString("title");
      eventTagline = ev.getString("tagline");
      eventType = ev.getString("type");
      eventEndsAt = now + ev.getLong("left");
      nextAt = now + ev.getLong("in");
      EVENT_TOP.clear();
      ListTag top = ev.getList("top", 10);
      for (int i = 0; i < top.size(); i++) {
         CompoundTag c = top.getCompound(i);
         EVENT_TOP.add(new String[]{c.getString("name"), c.getString("value")});
      }
      String leader = EVENT_TOP.isEmpty() ? "" : EVENT_TOP.getFirst()[0] + EVENT_TOP.getFirst()[1];
      if (eventActive && (!wasActive || !leader.equals(lastLeader))) {
         flashUntil = now + 8000L;
      }
      lastLeader = leader;
   }

   public static void clear() {
      BOARDS.clear();
      EVENT_TOP.clear();
      eventActive = false;
      lastLeader = "";
   }
}
