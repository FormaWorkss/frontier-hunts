package com.formaworks.frontierhunts.ecology;

import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.Whitetail;
import com.formaworks.frontierhunts.wildlife2026.WildlifeMob;
import com.formaworks.frontierhunts.wildlife2026.WildlifeSpecies;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Rabbit;

/**
 * [ecology] What a predator sees in a potential prey animal: kind, a readable label, whether it is young, weak or a
 * big male. Pure reads of existing state, no side effects.
 */
public record Prey(Kind kind, String label, boolean young, boolean weak, boolean male, boolean herd) {
   public enum Kind {
      WHITETAIL("whitetail", 2), ELK("elk", 3), MOOSE("moose", 3), BISON("bison", 3), PRONGHORN("pronghorn", 2), BOAR("boar", 2),
      GROUSE("grouse", 0), DUCK("duck", 0), RABBIT("rabbit", 0);

      public final String id;
      /** 0 small game (eaten whole, no carcass), 2 medium, 3 large */
      public final int size;

      Kind(String id, int size) {
         this.id = id;
         this.size = size;
      }

      public boolean small() {
         return this.size == 0;
      }
   }

   /** null when the entity is no prey (predators, players, downed or dying animals, anything else). */
   public static Prey of(LivingEntity e) {
      if (e == null || !e.isAlive() || e.isDeadOrDying()) {
         return null;
      }
      if (e instanceof Whitetail w) {
         if (w.downed() || w.sedated()) {
            return null;
         }
         GameSpecies s = w.species();
         boolean young = w.traits().yearling();
         boolean male = w.traits().buck();
         boolean weak = w.bleeding() || w.getHealth() < w.getMaxHealth() * 0.6F || w.traits().ageMonths() >= 108;
         Kind k = s == GameSpecies.ELK ? Kind.ELK : s == GameSpecies.MOOSE ? Kind.MOOSE : Kind.WHITETAIL;
         String name = (k == Kind.WHITETAIL ? "whitetail " : s.title.toLowerCase(java.util.Locale.ROOT) + " ")
            + (young ? "yearling" : s.sexName(male));
         return new Prey(k, name, young, weak, male, k != Kind.MOOSE);
      }
      if (e instanceof WildlifeMob m) {
         WildlifeSpecies s = m.species;
         Kind k = switch (s) {
            case BISON -> Kind.BISON;
            case PRONGHORN -> Kind.PRONGHORN;
            case BOAR -> Kind.BOAR;
            case GROUSE -> Kind.GROUSE;
            case DUCK -> Kind.DUCK;
            default -> null;
         };
         if (k == null) {
            return null;
         }
         // the old, the sick and the hurt: a stable share of each herd (by uuid) plus anything wounded
         long h = m.getUUID().getLeastSignificantBits() ^ m.getUUID().getMostSignificantBits();
         boolean old = Math.floorMod(h >>> 12, 100L) < 14;
         boolean weak = old || m.getHealth() < m.getMaxHealth() * 0.6F;
         boolean male = Math.floorMod(h >>> 20, 2L) == 0;
         String name = switch (k) {
            case BISON -> weak ? "old bison" : "bison";
            case PRONGHORN -> "pronghorn";
            case BOAR -> "wild hog";
            case GROUSE -> "grouse";
            default -> "duck";
         };
         return new Prey(k, name, false, weak, male, k == Kind.BISON || k == Kind.PRONGHORN || k == Kind.BOAR);
      }
      if (e instanceof Rabbit r) {
         return new Prey(Kind.RABBIT, "rabbit", r.isBaby(), false, false, false);
      }
      return null;
   }
}
