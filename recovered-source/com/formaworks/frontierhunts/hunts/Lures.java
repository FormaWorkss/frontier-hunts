package com.formaworks.frontierhunts.hunts;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * [hunts] Calls blown in the last minute (predator call, duck call), per dimension, in memory: the wildlife lure goal
 * reads them when deciding whether an animal answers. A handful of entries at most; cleared at server stop.
 */
public final class Lures {
   public enum Kind {
      PREDATOR,
      DUCK
   }

   /** One blown call: {@code id} lets each animal answer a call once. */
   public record Call(long id, Kind kind, UUID caller, String dim, Vec3 pos, long time, double radius) {
   }

   /** a call is heard and answered within this many ticks */
   public static final long ANSWER_WINDOW = 400L;
   private static final long KEEP = 1200L;
   private static final List<Call> CALLS = new ArrayList<>();
   private static long nextId = 1L;

   private Lures() {
   }

   public static synchronized Call blow(Kind kind, UUID caller, String dim, Vec3 pos, long now, double radius) {
      CALLS.removeIf(c -> now - c.time() > KEEP || now < c.time());
      while (CALLS.size() >= 64) {
         CALLS.removeFirst();
      }
      Call c = new Call(nextId++, kind, caller, dim, pos, now, radius);
      CALLS.add(c);
      return c;
   }

   /** The freshest call of this kind the animal at {@code at} can hear and has not answered yet, or null. */
   public static synchronized Call heard(Kind kind, String dim, Vec3 at, long now, long answered) {
      Call best = null;
      for (int i = CALLS.size() - 1; i >= 0; i--) {
         Call c = CALLS.get(i);
         if (c.kind() != kind || c.id() <= answered || now - c.time() > ANSWER_WINDOW || now < c.time() || !c.dim().equals(dim)) {
            continue;
         }
         if (c.pos().distanceToSqr(at) <= c.radius() * c.radius()) {
            best = c;
            break;
         }
      }
      return best;
   }

   public static synchronized boolean any(long now) {
      for (Call c : CALLS) {
         if (now - c.time() <= ANSWER_WINDOW && now >= c.time()) {
            return true;
         }
      }
      return false;
   }

   public static synchronized void clear() {
      CALLS.clear();
   }
}
