package com.formaworks.frontierhunts.archery;

import com.formaworks.frontierhunts.expedition.HuntProjectile;
import com.formaworks.frontierhunts.expedition.Weapon;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.FieldArrow;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * [archery2] Which arrow made a deer's newest impact mark (head, cedar shaft, crossbow bolt), so the arrow drawn
 * sticking out of the animal - in play and in the kill cam - is the one that was shot. Extra keys only
 * ({@code tip<i>}, {@code prim<i>}, {@code bolt<i>}); a mark without them draws the stock broadhead.
 */
public final class ArrowMarks {
   private ArrowMarks() {
   }

   /** A copy of {@code marks} with the newest mark stamped for {@code projectile} (unchanged if it is not an arrow). */
   public static CompoundTag stamp(CompoundTag marks, Entity projectile) {
      if (marks == null) {
         return null;
      }
      ArrowTip tip = null;
      boolean primitive = false;
      boolean bolt = false;
      if (projectile instanceof FieldArrow a) {
         tip = a.tip();
         primitive = a.primitive();
      } else if (projectile instanceof HuntProjectile h && h.kind().bow && h.kind() != Weapon.HUNTING_SPEAR && h.kind() != Weapon.BOWFISHING_BOW) {
         tip = h.tip();
         primitive = h.primitive();
         bolt = h.kind() == Weapon.CROSSBOW;
      }
      int n = Math.clamp(marks.getInt("count"), 0, 5);
      if (tip == null || n == 0 || !marks.getBoolean("arrow" + (n - 1))) {
         return marks;
      }
      CompoundTag out = marks.copy();
      out.putByte("tip" + (n - 1), (byte)tip.ordinal());
      out.putBoolean("prim" + (n - 1), primitive);
      out.putBoolean("bolt" + (n - 1), bolt);
      return out;
   }
}
