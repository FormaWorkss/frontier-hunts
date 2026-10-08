package com.formaworks.frontierhunts.campcook;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * [licence] A Camp Dutch Oven recipe ({@code frontierhunts:camp_cooking}): up to six ingredients (one item each, in
 * any order and any slots; a slot may hold a stack for several batches), the bowls one batch is served in, the dish and
 * the cooking time over heat. Nothing else may be in the pot. Never in the vanilla recipe book (its own station).
 */
public class CampCookingRecipe implements Recipe<CampCookingRecipe.PotInput> {
   public static final int SLOTS = 6;
   final String group;
   final NonNullList<Ingredient> ingredients;
   final int bowls;
   final ItemStack result;
   final int time;

   public CampCookingRecipe(String group, List<Ingredient> ingredients, int bowls, ItemStack result, int time) {
      this.group = group;
      this.ingredients = NonNullList.copyOf(ingredients);
      this.bowls = bowls;
      this.result = result;
      this.time = time;
   }

   /** The pot's six ingredient slots. */
   public record PotInput(List<ItemStack> items) implements RecipeInput {
      @Override
      public ItemStack getItem(int i) {
         return this.items.get(i);
      }

      @Override
      public int size() {
         return this.items.size();
      }
   }

   public int bowls() {
      return this.bowls;
   }

   public int time() {
      return this.time;
   }

   public ItemStack result() {
      return this.result;
   }

   @Override
   public String getGroup() {
      return this.group;
   }

   /**
    * Which slot feeds each ingredient for one batch (a slot may feed several ingredients when it holds enough), or null.
    * Every non-empty slot must be used: no stray items in the pot.
    */
   public int[] plan(PotInput in) {
      int n = this.ingredients.size();
      int[] left = new int[in.size()];
      for (int i = 0; i < in.size(); i++) {
         left[i] = in.getItem(i).getCount();
      }
      int[] pick = new int[n];
      if (!assign(in, 0, left, pick)) {
         return null;
      }
      return pick;
   }

   private boolean assign(PotInput in, int k, int[] left, int[] pick) {
      if (k == this.ingredients.size()) {
         // every occupied slot used at least once
         for (int i = 0; i < in.size(); i++) {
            if (!in.getItem(i).isEmpty()) {
               boolean used = false;
               for (int p : pick) {
                  used |= p == i;
               }
               if (!used) {
                  return false;
               }
            }
         }
         return true;
      }
      Ingredient ing = this.ingredients.get(k);
      for (int i = 0; i < in.size(); i++) {
         ItemStack s = in.getItem(i);
         if (left[i] > 0 && !s.isEmpty() && ing.test(s)) {
            left[i]--;
            pick[k] = i;
            if (this.assign(in, k + 1, left, pick)) {
               return true;
            }
            left[i]++;
         }
      }
      return false;
   }

   @Override
   public boolean matches(PotInput in, Level level) {
      return this.plan(in) != null;
   }

   @Override
   public ItemStack assemble(PotInput in, HolderLookup.Provider reg) {
      return this.result.copy();
   }

   @Override
   public boolean canCraftInDimensions(int w, int h) {
      return true;
   }

   @Override
   public ItemStack getResultItem(HolderLookup.Provider reg) {
      return this.result;
   }

   @Override
   public NonNullList<Ingredient> getIngredients() {
      return this.ingredients;
   }

   @Override
   public boolean isSpecial() {
      return true;
   }

   @Override
   public ItemStack getToastSymbol() {
      return new ItemStack(CampCookContent.DUTCH_OVEN_ITEM.get());
   }

   @Override
   public RecipeSerializer<?> getSerializer() {
      return CampCookContent.SERIALIZER.get();
   }

   @Override
   public RecipeType<?> getType() {
      return CampCookContent.TYPE.get();
   }

   public static final class Serializer implements RecipeSerializer<CampCookingRecipe> {
      private static final MapCodec<CampCookingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
         Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
         Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(
            l -> l.isEmpty() || l.size() > SLOTS ? DataResult.error(() -> "a camp cooking recipe takes 1 to 6 ingredients") : DataResult.success(l),
            DataResult::success).forGetter(r -> r.ingredients),
         Codec.intRange(0, 16).optionalFieldOf("bowls", 0).forGetter(r -> r.bowls),
         ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
         Codec.intRange(20, 24000).optionalFieldOf("cookingtime", 600).forGetter(r -> r.time)
      ).apply(i, CampCookingRecipe::new));
      private static final StreamCodec<RegistryFriendlyByteBuf, CampCookingRecipe> STREAM = StreamCodec.composite(
         ByteBufCodecs.STRING_UTF8, r -> r.group,
         Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(SLOTS)), r -> r.ingredients,
         ByteBufCodecs.VAR_INT, r -> r.bowls,
         ItemStack.STREAM_CODEC, r -> r.result,
         ByteBufCodecs.VAR_INT, r -> r.time,
         CampCookingRecipe::new
      );

      @Override
      public MapCodec<CampCookingRecipe> codec() {
         return CODEC;
      }

      @Override
      public StreamCodec<RegistryFriendlyByteBuf, CampCookingRecipe> streamCodec() {
         return STREAM;
      }
   }
}
