package com.formaworks.frontierhunts.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * [recipes] A recipe made only at the Clothing Table ({@code frontierhunts:clothing_workbench}): JSON type
 * {@code frontierhunts:clothing_table}, same fields as a shaped crafting recipe (pattern / key / result / category /
 * group) plus an optional {@code "sew": "lining" | "mittens"}.
 *
 * <p>It has its own {@link RecipeType}, so a crafting table never sees it, {@link #matches} is always false (defence in
 * depth) and it is {@link #isSpecial() special}, so the vanilla recipe book never lists or unlocks it. The Clothing Table
 * lists these next to its crafting recipes ({@code workshop/EquipmentCatalog}) and the server crafts them through the
 * usual {@code WorkbenchMenu} button flow (reach, menu, spectator, rate limit and an inventory plan recomputed on the
 * server).
 *
 * <p>A sewing recipe ({@code "sew"}) does not produce its result: it sews the ingredients into the garment in the
 * player's selected hotbar slot ({@link ClothingTable#sew}); its result item is only the list icon.
 */
public final class ClothingTableRecipe extends ShapedRecipe {
   public enum Sew {
      NONE(""), LINING("lining"), MITTENS("mittens");

      public final String id;

      Sew(String id) {
         this.id = id;
      }

      /** Bit of the {@code frontierhunts:lining} component this sewing sets (0 = not a sewing recipe). */
      public int bit() {
         return this == LINING ? 1 : (this == MITTENS ? 2 : 0);
      }

      static DataResult<Sew> parse(String s) {
         for (Sew v : values()) {
            if (v.id.equals(s)) {
               return DataResult.success(v);
            }
         }
         return DataResult.error(() -> "Unknown sew mode '" + s + "' (expected lining or mittens)");
      }
   }

   private final String group;
   private final CraftingBookCategory category;
   private final ItemStack result;
   private final Sew sew;
   private final ItemStack display;

   public ClothingTableRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, Sew sew) {
      super(group, category, pattern, result, false);
      this.group = group;
      this.category = category;
      this.result = result;
      this.sew = sew;
      ItemStack d = result.copy();
      if (sew != Sew.NONE) {
         d.setCount(1);
         d.set(DataComponents.ITEM_NAME, Component.translatable("recipes.frontierhunts.sew." + sew.id));
      }
      this.display = d;
   }

   public Sew sew() {
      return this.sew;
   }

   public boolean sewing() {
      return this.sew != Sew.NONE;
   }

   /** The item the Clothing Table hands out (empty for sewing recipes, which change the held garment instead). */
   public ItemStack product() {
      return this.sewing() ? ItemStack.EMPTY : this.result.copy();
   }

   @Override
   public ItemStack getResultItem(HolderLookup.Provider registries) {
      return this.display;
   }

   @Override
   public boolean matches(CraftingInput input, Level level) {
      return false; // never in a crafting grid
   }

   @Override
   public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
      return ItemStack.EMPTY;
   }

   @Override
   public boolean isSpecial() {
      return true; // keeps it out of the vanilla recipe book (and JEI's crafting category: different type anyway)
   }

   @Override
   public boolean showNotification() {
      return false;
   }

   @Override
   public RecipeType<?> getType() {
      return RecipesContent.CLOTHING_TABLE.get();
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return RecipesContent.CLOTHING_TABLE_SERIALIZER.get();
   }

   // ------------------------------------------------------------------------------------------------ serializer
   public static final class Serializer implements RecipeSerializer<ClothingTableRecipe> {
      private static final MapCodec<ClothingTableRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.EQUIPMENT).forGetter(r -> r.category),
            ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
            Codec.STRING.comapFlatMap(Sew::parse, s -> s.id).optionalFieldOf("sew", Sew.NONE).forGetter(r -> r.sew)
         ).apply(i, ClothingTableRecipe::new));
      private static final StreamCodec<RegistryFriendlyByteBuf, ClothingTableRecipe> STREAM_CODEC = StreamCodec.of(
         (buf, r) -> {
            buf.writeUtf(r.group);
            buf.writeEnum(r.category);
            ShapedRecipePattern.STREAM_CODEC.encode(buf, r.pattern);
            ItemStack.STREAM_CODEC.encode(buf, r.result);
            buf.writeByte(r.sew.ordinal());
         },
         buf -> {
            String group = buf.readUtf();
            CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buf);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            int sew = buf.readByte();
            return new ClothingTableRecipe(group, category, pattern, result, Sew.values()[Math.floorMod(sew, Sew.values().length)]);
         }
      );

      @Override
      public MapCodec<ClothingTableRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, ClothingTableRecipe> streamCodec() {
         return STREAM_CODEC;
      }
   }
}
