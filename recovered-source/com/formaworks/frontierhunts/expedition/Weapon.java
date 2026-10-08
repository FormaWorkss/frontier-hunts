package com.formaworks.frontierhunts.expedition;

import java.util.Locale;

public enum Weapon {
   RECURVE_BOW(1, 24, 12.0F, 2.8, 0, "field_arrow", true),
   COMPOUND_BOW(1, 28, 18.0F, 3.6, 0, "field_arrow", true),
   CROSSBOW(1, 36, 22.0F, 4.2, 0, "field_arrow", true),
   HUNTING_SPEAR(1, 20, 16.0F, 1.9, 0, "", true),
   LEVER_RIFLE(5, 16, 22.0F, 26.0, 0, "rifle_round", false),
   SEMI_AUTO_RIFLE(20, 3, 16.0F, 34.0, 0, "rifle_round", false),
   PUMP_SHOTGUN(4, 19, 5.0F, 17.0, 9, "shotgun_shell", false),
   DOUBLE_BARREL(2, 8, 5.5F, 17.0, 9, "shotgun_shell", false),
   SEMI_AUTO_SHOTGUN(5, 9, 3.8F, 17.0, 12, "shotgun_shell", false),
   REVOLVER(6, 10, 15.0F, 22.0, 0, "pistol_round", false),
   FIELD_PISTOL(8, 6, 12.0F, 21.0, 0, "pistol_round", false),
   TRANQUILIZER_RIFLE(1, 30, 1.0F, 9.0, 0, "tranquilizer_dart", false),
   FLARE_GUN(1, 30, 2.0F, 4.1, 0, "flare_round", false),
   BAIT_LAUNCHER(1, 25, 0.0F, 3.0, 0, "bait", false),
   BOWFISHING_BOW(1, 24, 6.0F, 2.6, 0, "bowfishing_arrow", true);

   public final int capacity;
   public final int interval;
   public final int pellets;
   public final float damage;
   public final float speed;
   public final String ammo;
   public final boolean bow;

   private Weapon(int nullxx, int nullxxx, float nullxxxx, double nullxxxxx, int nullxxxxxx, String nullxxxxxxx, boolean nullxxxxxxxx) {
      this.capacity = nullxx;
      this.interval = nullxxx;
      this.damage = nullxxxx;
      this.speed = (float)nullxxxxx;
      this.pellets = nullxxxxxx;
      this.ammo = nullxxxxxxx;
      this.bow = nullxxxxxxxx;
   }

   public String id() {
      return this.name().toLowerCase(Locale.ROOT);
   }

   public boolean automatic() {
      return this == SEMI_AUTO_RIFLE;
   }

   public int magazineBonus() {
      return this == SEMI_AUTO_RIFLE ? 10 : 3;
   }
}
