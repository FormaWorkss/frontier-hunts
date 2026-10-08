package com.formaworks.frontierhunts.survival.item;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.survival.Clothing;
import com.formaworks.frontierhunts.survival.SurvivalContent;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * [survival] Warm clothing made from the hides and furs of the reserve's animals. Insulation, wind and water resistance
 * live in {@link Kind} (data overrides through {@link Clothing}); the 3D look is the client's GarmentModels.
 */
public final class GarmentItem extends ArmorItem {
   public enum Kind {
      FUR_HAT(Type.HELMET, 1.0F, 0.6F, 0.4F, 165),
      BUCKSKIN_COAT(Type.CHESTPLATE, 1.6F, 0.6F, 0.5F, 260),
      BEAR_FUR_COAT(Type.CHESTPLATE, 3.0F, 0.8F, 0.5F, 300),
      HIDE_ROBE(Type.CHESTPLATE, 2.4F, 0.9F, 0.7F, 280),
      BUCKSKIN_LEGGINGS(Type.LEGGINGS, 1.0F, 0.5F, 0.4F, 220),
      FUR_MUKLUKS(Type.BOOTS, 0.8F, 0.5F, 0.5F, 190);

      public final Type type;
      public final float insulation;
      public final float wind;
      public final float water;
      public final int durability;

      Kind(Type type, float insulation, float wind, float water, int durability) {
         this.type = type;
         this.insulation = insulation;
         this.wind = wind;
         this.water = water;
         this.durability = durability;
      }

      public String id() {
         return this.name().toLowerCase(Locale.ROOT);
      }

      Holder<ArmorMaterial> material() {
         return switch (this) {
            case BUCKSKIN_COAT, BUCKSKIN_LEGGINGS -> SurvivalContent.BUCKSKIN;
            case HIDE_ROBE -> SurvivalContent.HEAVY;
            default -> SurvivalContent.FUR;
         };
      }
   }

   public final Kind kind;

   public GarmentItem(Kind kind) {
      super(kind.material(), kind.type, new Properties().durability(kind.durability));
      this.kind = kind;
   }

   @Override
   public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
      return com.formaworks.frontierhunts.outfitter.OutfitTextures.of("garment/" + this.kind.id()); // [outfitter]
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
      lines.add(Component.translatable("survival.frontierhunts.garment." + this.kind.id()).withStyle(ChatFormatting.GRAY));
      Clothing.describe(stack, this.kind.type.getSlot(), lines);
   }
}
