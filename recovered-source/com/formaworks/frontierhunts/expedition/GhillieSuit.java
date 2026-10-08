package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.item.ArmorMaterial.Layer;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GhillieSuit extends ArmorItem {
   public final GhillieSuit.Pattern pattern;
   public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, "frontierhunts");
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MATERIAL = MATERIALS.register(
      "woodland_ghillie",
      () -> new ArmorMaterial(
            Map.of(Type.HELMET, 1, Type.CHESTPLATE, 2, Type.LEGGINGS, 1),
            8,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(new ItemLike[]{Items.STRING}),
            List.of(new Layer(FrontierHunts.id("woodland_ghillie"))),
            0.0F,
            0.0F
         )
   );
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> CARBON = MATERIALS.register(
      "carbon_layer",
      () -> new ArmorMaterial(
            Map.of(Type.HELMET, 1, Type.CHESTPLATE, 3, Type.LEGGINGS, 2),
            12,
            SoundEvents.ARMOR_EQUIP_LEATHER,
            () -> Ingredient.of(new ItemLike[]{Items.LEATHER}),
            List.of(new Layer(FrontierHunts.id("carbon"))),
            0.0F,
            0.0F
         )
   );

   public GhillieSuit(Type var1) {
      this(var1, GhillieSuit.Pattern.WOODLAND);
   }

   public GhillieSuit(Type var1, GhillieSuit.Pattern var2) {
      super(MATERIAL, var1, new Properties().durability(var1 == Type.CHESTPLATE ? 240 : 180));
      this.pattern = var2;
   }

   public ResourceLocation getArmorTexture(ItemStack var1, Entity var2, EquipmentSlot var3, Layer var4, boolean var5) {
      // [outfitter] hood / jacket / trousers share one painted outfit texture per pattern (Vanilla or Ultra detail)
      return com.formaworks.frontierhunts.outfitter.OutfitTextures.of("ghillie/" + this.pattern.id);
   }

   public static double coverage(Player var0) {
      double var1 = 0.0;

      for (EquipmentSlot var4 : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
         Item var5 = var0.getItemBySlot(var4).getItem();
         if (var5 instanceof GhillieSuit) {
            GhillieSuit var6 = (GhillieSuit)var5;
            if (var6.getType().getSlot() == var4) {
               var1 += var4 == EquipmentSlot.CHEST ? 0.5 : 0.25;
            }
         }
      }

      return var1;
   }

   public static double sightMultiplier(Player var0) {
      double var1 = coverage(var0);
      double var3 = Coverall.concealment(var0);
      if (var1 == 0.0 && var3 <= 0.0) {
         return 1.0;
      } else {
         GhillieSuit.Pattern var5 = environment(var0);
         double var6 = 0.0;

         for (EquipmentSlot var9 : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS)) {
            Item var11 = var0.getItemBySlot(var9).getItem();
            if (var11 instanceof GhillieSuit) {
               GhillieSuit var10 = (GhillieSuit)var11;
               if (var10.getType().getSlot() == var9) {
                  var6 += (var9 == EquipmentSlot.CHEST ? 0.5 : 0.25) * (var10.pattern == var5 ? 1.0 : 0.25);
               }
            }
         }

         var6 = Math.min(1.0, Math.max(var6, var3) + Math.min(var6, var3) * 0.25);
         double var13 = var0.getDeltaMovement().horizontalDistance();
         double var14 = var0.isSprinting() ? 0.08 : (var13 > 0.04 ? 0.24 : (var0.isCrouching() ? 0.62 : 0.42));
         return !var0.isOnFire() && !var0.isInWater() ? 1.0 - var6 * var14 : 1.0;
      }
   }

   public static GhillieSuit.Pattern environment(Player var0) {
      Level var1 = var0.level();
      BlockPos var2 = var0.blockPosition();
      Holder var3 = var1.getBiome(var2);
      if (((Biome)var3.value()).coldEnoughToSnow(var2) || var1.getBlockState(var2.below()).is(Blocks.SNOW_BLOCK) || var1.getBlockState(var2).is(Blocks.SNOW)) {
         return GhillieSuit.Pattern.SNOW;
      } else if (var3.is(Biomes.SWAMP) || var3.is(Biomes.MANGROVE_SWAMP) || var3.is(BiomeTags.IS_RIVER)) {
         return GhillieSuit.Pattern.WETLAND;
      } else {
         return !var3.is(BiomeTags.IS_FOREST) && !var3.is(BiomeTags.IS_TAIGA) && !var3.is(BiomeTags.IS_JUNGLE)
            ? GhillieSuit.Pattern.GRASSLAND
            : GhillieSuit.Pattern.WOODLAND;
      }
   }

   public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
      var3.add(Component.literal(this.pattern.title + " leaf mesh · visual concealment"));
      var3.add(Component.literal("Best match: " + this.pattern.description));
      var3.add(Component.literal("Best while still and crouched. Movement reveals you."));
      var3.add(Component.literal("Animals can still hear and smell you."));
   }

   public static enum Pattern {
      WOODLAND("woodland", "Woodland", "Forests and wooded taiga"),
      GRASSLAND("grassland", "Grassland", "Plains and dry savanna"),
      WETLAND("wetland", "Wetland", "Swamps, rivers and reed beds"),
      SNOW("snow", "Snowfield", "Snowy terrain and cold tundra");

      public final String id;
      public final String title;
      public final String description;

      private Pattern(String nullxx, String nullxxx, String nullxxxx) {
         this.id = nullxx;
         this.title = nullxxx;
         this.description = nullxxxx;
      }

      public String item(String var1) {
         return this == WOODLAND ? "ghillie_" + var1 : "ghillie_" + this.id + "_" + var1;
      }
   }
}
