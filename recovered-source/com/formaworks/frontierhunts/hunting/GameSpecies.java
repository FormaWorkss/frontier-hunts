package com.formaworks.frontierhunts.hunting;

import java.util.Locale;

public enum GameSpecies {
   WHITETAIL(
      "whitetail",
      "Whitetail deer",
      "buck",
      "doe",
      0,
      11,
      "whitetail_v3",
      "whitetail_anatomy",
      "whitetail_coat_summer",
      "whitetail_coat_winter",
      84.0F,
      1.47F,
      1.615F,
      GameSpecies.Antlers.PROCEDURAL_WHITETAIL,
      0.85F,
      1.85F,
      1.55F,
      26.0,
      0.3,
      new GameSpecies.Behavior(12, 2, 4, 0.35F, 1.0F, 1.0F, 0.95F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, false, 24.0F, 0.0F, 0.0F, 0.0F, 0)
   ),
   ELK(
      "elk",
      "Elk",
      "bull",
      "cow",
      0,
      11,
      "elk",
      "elk_anatomy",
      "elk_coat_summer",
      "elk_coat_winter",
      330.0F,
      1.3F,
      1.36F,
      GameSpecies.Antlers.MESH,
      1.15F,
      2.14F,
      1.97F,
      44.0,
      0.31,
      new GameSpecies.Behavior(18, 3, 7, 0.25F, 0.82F, 1.25F, 1.3F, 2.3F, 3.9F, 2.4F, 0.72F, 0.78F, true, 36.0F, 1.2F, 3.6F, 10.5F, 12)
   ),
   MOOSE(
      "moose",
      "Moose",
      "bull",
      "cow",
      0,
      11,
      "moose",
      "moose_anatomy",
      "moose_coat_summer",
      "moose_coat_winter",
      500.0F,
      1.4F,
      1.45F,
      GameSpecies.Antlers.MESH,
      1.25F,
      2.19F,
      2.0F,
      58.0,
      0.29,
      new GameSpecies.Behavior(4, 1, 2, 0.1F, 0.55F, 0.8F, 1.62F, 2.7F, 3.7F, 3.8F, 0.58F, 0.66F, true, 0.0F, 1.1F, 4.2F, 8.5F, 16)
   );

   public final String id;
   public final String title;
   public final String maleName;
   public final String femaleName;
   private final int firstMonth;
   private final int lastMonth;
   private final String skeleton;
   private final String anatomy;
   private final String coatSummer;
   private final String coatWinter;
   public final float referenceMassKg;
   public final float gameScaleXZ;
   public final float gameScaleY;
   public final GameSpecies.Antlers antlers;
   public final float width;
   public final float height;
   public final float eyeHeight;
   public final double health;
   public final double speed;
   public final GameSpecies.Behavior behavior;

   private GameSpecies(
      String nullxx,
      String nullxxx,
      String nullxxxx,
      String nullxxxxx,
      int nullxxxxxx,
      int nullxxxxxxx,
      String nullxxxxxxxx,
      String nullxxxxxxxxx,
      String nullxxxxxxxxxx,
      String nullxxxxxxxxxxx,
      float nullxxxxxxxxxxxx,
      float nullxxxxxxxxxxxxx,
      float nullxxxxxxxxxxxxxx,
      GameSpecies.Antlers nullxxxxxxxxxxxxxxx,
      float nullxxxxxxxxxxxxxxxx,
      float nullxxxxxxxxxxxxxxxxx,
      float nullxxxxxxxxxxxxxxxxxx,
      double nullxxxxxxxxxxxxxxxxxxx,
      double nullxxxxxxxxxxxxxxxxxxxx,
      GameSpecies.Behavior nullxxxxxxxxxxxxxxxxxxxxx
   ) {
      this.id = nullxx;
      this.title = nullxxx;
      this.maleName = nullxxxx;
      this.femaleName = nullxxxxx;
      this.firstMonth = nullxxxxxx;
      this.lastMonth = nullxxxxxxx;
      this.skeleton = nullxxxxxxxx;
      this.anatomy = nullxxxxxxxxx;
      this.coatSummer = nullxxxxxxxxxx;
      this.coatWinter = nullxxxxxxxxxxx;
      this.referenceMassKg = nullxxxxxxxxxxxx;
      this.gameScaleXZ = nullxxxxxxxxxxxxx;
      this.gameScaleY = nullxxxxxxxxxxxxxx;
      this.antlers = nullxxxxxxxxxxxxxxx;
      this.width = nullxxxxxxxxxxxxxxxx;
      this.height = nullxxxxxxxxxxxxxxxxx;
      this.eyeHeight = nullxxxxxxxxxxxxxxxxxx;
      this.health = nullxxxxxxxxxxxxxxxxxxx;
      this.speed = nullxxxxxxxxxxxxxxxxxxxx;
      this.behavior = nullxxxxxxxxxxxxxxxxxxxxx;
   }

   public static GameSpecies byId(String var0) {
      if (var0 == null) {
         return null;
      } else {
         for (GameSpecies var4 : values()) {
            if (var4.id.equals(var0.toLowerCase(Locale.ROOT))) {
               return var4;
            }
         }

         return null;
      }
   }

   public boolean inSeason(int var1) {
      return this.firstMonth <= this.lastMonth ? var1 >= this.firstMonth && var1 <= this.lastMonth : var1 >= this.firstMonth || var1 <= this.lastMonth;
   }

   public String skeletonResource() {
      return "/assets/frontierhunts/models/entity/" + this.skeleton + ".fhsk";
   }

   public String anatomyResource() {
      return "/assets/frontierhunts/models/entity/" + this.anatomy + ".fhan";
   }

   public String coatTexture(boolean var1) {
      return "textures/entity/" + (var1 ? this.coatWinter : this.coatSummer) + ".png";
   }

   public String sexName(boolean var1) {
      return var1 ? this.maleName : this.femaleName;
   }

   public boolean meshAntlers() {
      return this.antlers == GameSpecies.Antlers.MESH;
   }

   public boolean defendsTerritory() {
      return this == MOOSE;
   }

   public static enum Antlers {
      NONE,
      PROCEDURAL_WHITETAIL,
      MESH;
   }

   public static record Behavior(
      int herdCap,
      int groupMin,
      int groupMax,
      float bachelorChance,
      float spook,
      float alertRange,
      float walkStride,
      float trotStride,
      float runStride,
      float trotSpeed,
      float voicePitch,
      float stepPitch,
      boolean bugles,
      float herdRadius,
      float strollSpeed,
      float warySpeed,
      float panicSpeed,
      int points
   ) {
   }
}
