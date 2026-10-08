package com.formaworks.frontierhunts.licence;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Rut;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.journal.JournalApi;
import com.formaworks.frontierhunts.journal.Rank;
import com.formaworks.frontierhunts.journal.RankPerks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * [1.1.6] The ranger's game report, a service at the licence counter for Woodsman rank and up: where the nearest
 * whitetail, elk and moose groups are (compass direction and a rough distance - a ranger's survey, not a GPS fix) and
 * what the rut is doing. Tracker rank and up also hear how many animals are in each group and how many are bucks or
 * bulls. Legend rank gets it free. One report per in-game half day, because the herds don't move that fast.
 */
public final class GameReport {
   private GameReport() {
   }

   public static final int TOKENS = 10, EMERALDS = 2;
   /** how far the survey reaches (loaded country only) */
   static final double RANGE = 320.0;
   /** animals this close together count as one group */
   static final double GROUP = 40.0;
   static final long COOLDOWN = 12000L;
   private static final Map<UUID, Long> LAST = new HashMap<>();

   /** why the report can't be had right now (rank or the cooldown), or null */
   static String refusal(ServerPlayer p) {
      if (!RankPerks.atLeast(p, Rank.WOODSMAN)) {
         return "rank_woodsman";
      }
      Long last = LAST.get(p.getUUID());
      long now = p.serverLevel().getGameTime();
      return last != null && now >= last && now - last < COOLDOWN ? "report_wait" : null;
   }

   static boolean free(ServerPlayer p) {
      return RankPerks.atLeast(p, Rank.LEGEND);
   }

   static void deliver(ServerPlayer p) {
      LAST.put(p.getUUID(), p.serverLevel().getGameTime());
      boolean detail = RankPerks.atLeast(p, Rank.TRACKER);
      Vec3 me = p.position();
      List<Whitetail> all = p.serverLevel().getEntitiesOfClass(Whitetail.class, new AABB(me, me).inflate(RANGE, 96.0, RANGE),
         d -> d.isAlive() && !d.downed());
      p.sendSystemMessage(Component.literal("Ranger's game report").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
      StringBuilder note = new StringBuilder("Ranger's game report:");
      for (GameSpecies sp : new GameSpecies[]{GameSpecies.WHITETAIL, GameSpecies.ELK, GameSpecies.MOOSE}) {
         List<Whitetail> mine = new ArrayList<>();
         for (Whitetail d : all) {
            if (d.species() == sp) {
               mine.add(d);
            }
         }
         String name = sp == GameSpecies.WHITETAIL ? "Whitetail" : sp == GameSpecies.ELK ? "Elk" : "Moose";
         Rut.Phase phase = Rut.phase(sp, p.serverLevel());
         String line;
         if (mine.isEmpty()) {
            line = name + ": none seen within " + (int)RANGE + " m. Try other country - " + habitat(sp) + ".";
         } else {
            // nearest animal, then everything grouped around it
            Whitetail near = mine.get(0);
            for (Whitetail d : mine) {
               if (d.distanceToSqr(p) < near.distanceToSqr(p)) {
                  near = d;
               }
            }
            int n = 0, males = 0;
            Vec3 c = Vec3.ZERO;
            for (Whitetail d : mine) {
               if (d.distanceToSqr(near) <= GROUP * GROUP) {
                  n++;
                  c = c.add(d.position());
                  try {
                     if (d.traits().buck()) {
                        males++;
                     }
                  } catch (RuntimeException ignored) {
                  }
               }
            }
            c = c.scale(1.0 / n);
            int groups = groups(mine);
            String male = sp == GameSpecies.WHITETAIL ? "buck" : "bull";
            line = name + ": a group " + band(c.distanceTo(me)) + " to the " + compass(c.subtract(me)) + (detail
               ? " - " + n + " head, " + males + " " + male + (males == 1 ? "" : "s")
               : "") + (groups > 1 ? " (" + (groups - 1) + " more group" + (groups > 2 ? "s" : "") + " further out)" : "") + ".";
         }
         line += " Rut: " + phase.title + ".";
         p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
         note.append(' ').append(line);
      }
      for (String r : com.formaworks.frontierhunts.freak.FreakQuest.rumours(p)) {
         p.sendSystemMessage(Component.literal(r).withStyle(ChatFormatting.GOLD));
         note.append(' ').append(r);
      }
      String tip = detail ? "Approach into the wind. The report is a survey: animals keep moving." : "Reach Tracker rank and the ranger will count heads for you.";
      p.sendSystemMessage(Component.literal(tip).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
      JournalApi.note(p, note.toString());
      JournalApi.count(p, "licence.reports", 1);
   }

   private static int groups(List<Whitetail> list) {
      List<Vec3> centres = new ArrayList<>();
      for (Whitetail d : list) {
         boolean found = false;
         for (Vec3 c : centres) {
            if (c.distanceToSqr(d.position()) <= GROUP * GROUP) {
               found = true;
               break;
            }
         }
         if (!found) {
            centres.add(d.position());
         }
      }
      return centres.size();
   }

   static String band(double m) {
      if (m < 80) {
         return "close by (under 80 m)";
      }
      if (m < 160) {
         return "about 100-150 m";
      }
      if (m < 240) {
         return "about 200 m";
      }
      return "a long walk (250 m+)";
   }

   static String compass(Vec3 d) {
      // Minecraft: -Z is north, +X is east
      double deg = Math.toDegrees(Math.atan2(d.x, -d.z));
      String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
      return names[(int)Math.floorMod(Math.round(deg / 45.0), 8L)];
   }

   private static String habitat(GameSpecies sp) {
      return switch (sp) {
         case ELK -> "elk hold high parks and timber edges in the mountains";
         case MOOSE -> "moose keep to willow bottoms, bogs and lake shores in the north";
         default -> "whitetail like forest edges, brush and field margins";
      };
   }
}
