package com.formaworks.frontierhunts.survival;

import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;

/**
 * [survival] Nutrition of one food: meter points (0..100 scale) of protein, fat and energy, where it comes from, and how
 * long it keeps.
 *
 * @param shelfDays in-game days it keeps at room temperature before it spoils (0 = never spoils)
 * @param raw       raw meat or fish: smokehouse / salt / drying rack accept it, eating it raw gives a bit less
 * @param dry       can be dried into jerky on a drying rack
 * @param spoiled   eating it makes you sick
 */
public record FoodValues(float protein, float fat, float energy, Source source, float shelfDays, boolean raw, boolean dry, boolean spoiled) {
   public static final FoodValues NONE = new FoodValues(0F, 0F, 0F, Source.CROP, 0F, false, false, false);

   public enum Source {
      /** wild game meat, fat and organs, and food made from them (jerky, pemmican) */
      GAME,
      /** fish and other things from the water */
      FISH,
      /** domestic animals: beef, pork, mutton, chicken, eggs, milk */
      FARM,
      /** crops, bread and baked goods */
      CROP,
      /** wild plants: berries, mushrooms, kelp */
      FORAGE;

      public static Source parse(String s) {
         try {
            return valueOf(s.trim().toUpperCase(Locale.ROOT));
         } catch (RuntimeException e) {
            return CROP;
         }
      }

      public String key() {
         return this.name().toLowerCase(Locale.ROOT);
      }
   }

   public boolean perishable() {
      return this.shelfDays > 0F;
   }

   public float total() {
      return this.protein + this.fat + this.energy;
   }

   public FoodValues scaled(float p, float f, float e) {
      return new FoodValues(this.protein * p, this.fat * f, this.energy * e, this.source, this.shelfDays, this.raw, this.dry, this.spoiled);
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeByte(Math.round(Math.min(255F, this.protein * 2F)));
      buf.writeByte(Math.round(Math.min(255F, this.fat * 2F)));
      buf.writeByte(Math.round(Math.min(255F, this.energy * 2F)));
      buf.writeByte(this.source.ordinal() | (this.raw ? 8 : 0) | (this.dry ? 16 : 0) | (this.spoiled ? 32 : 0));
      buf.writeShort(Math.round(Math.min(65000F, this.shelfDays * 100F)));
   }

   public static FoodValues read(FriendlyByteBuf buf) {
      float p = buf.readUnsignedByte() / 2F;
      float f = buf.readUnsignedByte() / 2F;
      float e = buf.readUnsignedByte() / 2F;
      int flags = buf.readByte();
      float shelf = buf.readUnsignedShort() / 100F;
      Source[] v = Source.values();
      return new FoodValues(
         Math.max(0F, p), Math.max(0F, f), Math.max(0F, e), v[Math.min(v.length - 1, flags & 7)], Math.max(0F, shelf), (flags & 8) != 0, (flags & 16) != 0, (flags & 32) != 0
      );
   }
}
