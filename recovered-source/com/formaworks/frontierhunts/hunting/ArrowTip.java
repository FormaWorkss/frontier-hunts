package com.formaworks.frontierhunts.hunting;

import com.formaworks.frontierhunts.HuntContent;
import java.util.Map.Entry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.DeferredItem;

public enum ArrowTip {
   FIELD_POINT("field_point", "Field Point", 1.0F, 0.45F, 0.8F, 0.97F, true, 0, 10988712),
   FIXED_BROADHEAD("fixed_broadhead", "Fixed-Blade Broadhead", 1.0F, 1.0F, 1.0F, 0.85F, true, 0, 12043199),
   MECHANICAL_BROADHEAD("mechanical_broadhead", "Mechanical Broadhead", 0.85F, 1.35F, 1.1F, 0.75F, true, 0, 10134440),
   CUT_ON_CONTACT("cut_on_contact_broadhead", "Cut-on-Contact Broadhead", 1.22F, 0.95F, 1.0F, 0.85F, true, 0, 13225414),
   JUDO_POINT("judo_point", "Judo Point", 0.3F, 0.0F, 0.6F, 1.0F, false, 0, 9146246),
   FLINT_POINT("flint_point", "Knapped Flint Point", 0.9F, 0.9F, 0.9F, 0.7F, true, 0, 6118488),
   OBSIDIAN_POINT("obsidian_point", "Obsidian Point", 0.85F, 1.15F, 1.05F, 0.5F, true, 0, 2300718),
   BONE_POINT("bone_point", "Bone Point", 0.8F, 0.75F, 0.85F, 0.75F, true, 0, 14735293),
   TRACER_BROADHEAD("tracer_broadhead", "Tracer Broadhead", 1.0F, 1.0F, 1.0F, 0.85F, true, 16726556, 12043199),
   TRACER_FIELD_POINT("tracer_field_point", "Tracer Field Point", 1.0F, 0.45F, 0.8F, 0.97F, true, 3997546, 10988712),
   TRACER_ICE("tracer_ice_broadhead", "Glacier Tracer Broadhead", 1.22F, 0.95F, 1.0F, 0.85F, true, 4638975, 13225414);

   public final String id;
   public final String title;
   public final float penetration;
   public final float bleed;
   public final float damage;
   public final float recovery;
   public final boolean lethal;
   public final int glow;
   public final int metal;
   public static final String KEY = "arrow_tip";

   private ArrowTip(
      String nullxx,
      String nullxxx,
      float nullxxxx,
      float nullxxxxx,
      float nullxxxxxx,
      float nullxxxxxxx,
      boolean nullxxxxxxxx,
      int nullxxxxxxxxx,
      int nullxxxxxxxxxx
   ) {
      this.id = nullxx;
      this.title = nullxxx;
      this.penetration = nullxxxx;
      this.bleed = nullxxxxx;
      this.damage = nullxxxxxx;
      this.recovery = nullxxxxxxx;
      this.lethal = nullxxxxxxxx;
      this.glow = nullxxxxxxxxx;
      this.metal = nullxxxxxxxxxx;
   }

   public boolean tracer() {
      return this.glow != 0;
   }

   public boolean primitive() {
      return this == FLINT_POINT || this == OBSIDIAN_POINT || this == BONE_POINT;
   }

   public boolean broadhead() {
      return this == FIXED_BROADHEAD || this == MECHANICAL_BROADHEAD || this == CUT_ON_CONTACT || this == TRACER_BROADHEAD || this == TRACER_ICE;
   }

   public ArrowTip shape() {
      return this == TRACER_BROADHEAD ? FIXED_BROADHEAD : (this == TRACER_FIELD_POINT ? FIELD_POINT : (this == TRACER_ICE ? CUT_ON_CONTACT : this));
   }

   public static ArrowTip byOrdinal(int var0) {
      ArrowTip[] var1 = values();
      return var1[Math.clamp((long)var0, 0, var1.length - 1)];
   }

   public static ArrowTip byId(String var0) {
      for (ArrowTip var4 : values()) {
         if (var4.id.equals(var0)) {
            return var4;
         }
      }

      return null;
   }

   public String summary() {
      return switch (this) {
         case FIELD_POINT -> "Practice tip. Clean holes, light blood trail. Almost always recoverable.";
         case FIXED_BROADHEAD -> "Three solid blades. The all-round hunting head.";
         case MECHANICAL_BROADHEAD -> "Blades open on impact: the widest wound and the heaviest blood trail, but less penetration.";
         case CUT_ON_CONTACT -> "Two long blades that slice from the tip. The deepest penetration, made for elk and moose.";
         case JUDO_POINT -> "Spring arms stop the arrow in grass. Stuns small game; never kills big game.";
         case FLINT_POINT -> "Hand-knapped flint, sinew-lashed. A true primitive head.";
         case OBSIDIAN_POINT -> "Glass-sharp volcanic stone. Cuts deep but often shatters.";
         case BONE_POINT -> "Ground bone point. Light, quiet and traditional.";
         case TRACER_BROADHEAD -> "Fixed-blade head with a red burning tracer. Watch the flight and find the arrow after.";
         case TRACER_FIELD_POINT -> "Field point with a green tracer. Perfect for practice at dusk.";
         case TRACER_ICE -> "Cut-on-contact head with an ice-blue tracer.";
      };
   }

   public static ArrowTip of(ItemStack var0) {
      if (var0.isEmpty()) {
         return FIXED_BROADHEAD;
      } else if (var0.is((Item)HuntContent.TRACER_ARROW.get())) {
         return TRACER_BROADHEAD;
      } else {
         CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
         if (var1 != null) {
            ArrowTip var2 = byId(var1.copyTag().getString("arrow_tip"));
            if (var2 != null) {
               return var2;
            }
         }

         return var0.is((Item)HuntContent.PRIMITIVE_ARROW.get()) ? FLINT_POINT : FIXED_BROADHEAD;
      }
   }

   public static ItemStack with(ItemStack var0, ArrowTip var1) {
      CompoundTag var2 = var0.has(DataComponents.CUSTOM_DATA) ? ((CustomData)var0.get(DataComponents.CUSTOM_DATA)).copyTag() : new CompoundTag();
      ArrowTip var3 = var0.is((Item)HuntContent.PRIMITIVE_ARROW.get()) ? FLINT_POINT : FIXED_BROADHEAD;
      if (var1 == var3) {
         var2.remove("arrow_tip");
      } else {
         var2.putString("arrow_tip", var1.id);
      }

      if (var2.isEmpty()) {
         var0.remove(DataComponents.CUSTOM_DATA);
      } else {
         var0.set(DataComponents.CUSTOM_DATA, CustomData.of(var2));
      }

      return var0;
   }

   public static boolean fitted(ItemStack var0) {
      CustomData var1 = (CustomData)var0.get(DataComponents.CUSTOM_DATA);
      return var1 != null && byId(var1.copyTag().getString("arrow_tip")) != null;
   }

   public static boolean arrow(ItemStack var0) {
      return var0.is((Item)HuntContent.FIELD_ARROW.get()) || var0.is((Item)HuntContent.PRIMITIVE_ARROW.get()) || var0.is((Item)HuntContent.TRACER_ARROW.get());
   }

   public static boolean primitiveShaft(ItemStack var0) {
      return var0.is((Item)HuntContent.PRIMITIVE_ARROW.get());
   }

   public static ItemStack arrowStack(boolean var0, ArrowTip var1, int var2) {
      return with(new ItemStack(var0 ? (ItemLike)HuntContent.PRIMITIVE_ARROW.get() : (ItemLike)HuntContent.FIELD_ARROW.get(), var2), var1);
   }

   public ItemStack tipItem(int var1) {
      return new ItemStack((ItemLike)HuntContent.TIPS.get(this).get(), var1);
   }

   public static ArrowTip ofTipItem(ItemStack var0) {
      for (Entry var2 : HuntContent.TIPS.entrySet()) {
         if (var0.is((Item)((DeferredItem)var2.getValue()).get())) {
            return (ArrowTip)var2.getKey();
         }
      }

      return null;
   }
}
