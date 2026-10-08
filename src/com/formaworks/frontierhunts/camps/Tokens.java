package com.formaworks.frontierhunts.camps;

import com.formaworks.frontierhunts.HunterLedger;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * The mod's existing currency: reserve tokens in {@link HunterLedger} (SavedData "frontierhunts_hunters").
 * HunterLedger only exposes capped/side-effecting credit methods, so payouts write the balance field directly
 * (same module, no obfuscation). If the coordinator later adds {@code HunterLedger.credit(UUID,int)} this class is the
 * single place to switch over.
 */
public final class Tokens {
   private static Field field;
   private static boolean failed;

   private Tokens() {
   }

   public static int balance(ServerLevel level, UUID id) {
      return HunterLedger.get(level).hunter(id).tokens();
   }

   /** Takes tokens if the balance covers them. Zero always succeeds. */
   public static boolean spend(ServerLevel level, UUID id, int amount) {
      if (amount <= 0) {
         return amount == 0;
      }
      return HunterLedger.get(level).spend(id, amount);
   }

   /** Adds tokens (saturating at Integer.MAX_VALUE). Works for offline hunters. */
   public static boolean credit(ServerLevel level, UUID id, int amount) {
      if (amount <= 0) {
         return amount == 0;
      }
      HunterLedger ledger = HunterLedger.get(level);
      HunterLedger.Hunter hunter = ledger.hunter(id);
      Field f = field();
      if (f != null) {
         try {
            int cur = f.getInt(hunter);
            f.setInt(hunter, (int)Math.min(Integer.MAX_VALUE, (long)cur + amount));
            ledger.setDirty();
            return true;
         } catch (Throwable ignored) {
            failed = true;
         }
      }
      int left = amount;
      while (left > 0) {
         int chunk = Math.min(100, left);
         if (!ledger.provisionPayment(id, chunk)) {
            return false;
         }
         left -= chunk;
      }
      return true;
   }

   private static synchronized Field field() {
      if (field == null && !failed) {
         try {
            Field f = HunterLedger.Hunter.class.getDeclaredField("tokens");
            f.setAccessible(true);
            field = f;
         } catch (Throwable t) {
            failed = true;
         }
      }
      return field;
   }
}
