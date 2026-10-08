package com.formaworks.frontierhunts.benches;

import com.formaworks.frontierhunts.FrontierHunts;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

/**
 * [benches] Bench recipes: {@code frontierhunts:bench_shaped} and {@code frontierhunts:bench_shapeless}. Same JSON as the
 * vanilla shaped / shapeless crafting recipes (the codecs mirror vanilla's), but their recipe type is
 * {@code frontierhunts:bench}, so a crafting table (which looks recipes up by {@link RecipeType#CRAFTING}) never matches
 * them. They stay {@link net.minecraft.world.item.crafting.CraftingRecipe}s (subclasses of ShapedRecipe /
 * ShapelessRecipe), so code that reads their ingredients, width and height keeps working. Which bench and tab makes them
 * is decided by {@link BenchCatalog} from the result item.
 *
 * <p>The vanilla recipe book never shows them (the client maps the type to an unlisted category, see
 * {@code benches/client/BenchClient}), and unlocking one never pops the "check your recipe book" toast.
 */
public final class BenchRecipes {
   public static final ResourceLocation TYPE_ID = FrontierHunts.id("bench");
   public static final ResourceLocation SHAPED_ID = FrontierHunts.id("bench_shaped");
   public static final ResourceLocation SHAPELESS_ID = FrontierHunts.id("bench_shapeless");
   public static final DeferredHolder<RecipeType<?>, RecipeType<net.minecraft.world.item.crafting.CraftingRecipe>> TYPE =
      DeferredHolder.create(Registries.RECIPE_TYPE, TYPE_ID);
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<Shaped>> SHAPED =
      DeferredHolder.create(Registries.RECIPE_SERIALIZER, SHAPED_ID);
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<Shapeless>> SHAPELESS =
      DeferredHolder.create(Registries.RECIPE_SERIALIZER, SHAPELESS_ID);

   private BenchRecipes() {
   }

   /** Bench recipe type object (registered by {@link BenchContent}). */
   static RecipeType<net.minecraft.world.item.crafting.CraftingRecipe> newType() {
      return new RecipeType<>() {
         @Override
         public String toString() {
            return TYPE_ID.toString();
         }
      };
   }

   /** The bench icon a recipe-unlock toast would show (never shown: {@link #showNotification} is false). */
   private static ItemStack toast(ItemStack result) {
      try {
         return BenchCatalog.benchFor(result.getItem()).stack();
      } catch (RuntimeException e) {
         return ItemStack.EMPTY;
      }
   }

   // ================================================================================================ shaped
   public static final class Shaped extends ShapedRecipe {
      final String group;
      final CraftingBookCategory category;
      final ItemStack result;
      final boolean notify;

      public Shaped(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean notify) {
         super(group, category, pattern, result, notify);
         this.group = group;
         this.category = category;
         this.result = result;
         this.notify = notify;
      }

      @Override
      public RecipeType<?> getType() {
         return TYPE.get();
      }

      @Override
      public RecipeSerializer<?> getSerializer() {
         return SHAPED.get();
      }

      @Override
      public boolean showNotification() {
         return false; // there is no recipe book entry to check
      }

      @Override
      public ItemStack getToastSymbol() {
         return toast(this.result);
      }

      public static final class Serializer implements RecipeSerializer<Shaped> {
         private static final MapCodec<Shaped> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
               Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
               CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
               ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern),
               ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
               Codec.BOOL.optionalFieldOf("show_notification", true).forGetter(r -> r.notify)
            ).apply(i, Shaped::new));
         private static final StreamCodec<RegistryFriendlyByteBuf, Shaped> STREAM = StreamCodec.of(
            (buf, r) -> {
               buf.writeUtf(r.group);
               buf.writeEnum(r.category);
               ShapedRecipePattern.STREAM_CODEC.encode(buf, r.pattern);
               ItemStack.STREAM_CODEC.encode(buf, r.result);
               buf.writeBoolean(r.notify);
            },
            buf -> {
               String group = buf.readUtf();
               CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
               ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buf);
               ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
               boolean notify = buf.readBoolean();
               return new Shaped(group, category, pattern, result, notify);
            });

         @Override
         public MapCodec<Shaped> codec() {
            return CODEC;
         }

         @Override
         public StreamCodec<RegistryFriendlyByteBuf, Shaped> streamCodec() {
            return STREAM;
         }
      }
   }

   // ================================================================================================ shapeless
   public static final class Shapeless extends ShapelessRecipe {
      final String group;
      final CraftingBookCategory category;
      final ItemStack result;
      final NonNullList<Ingredient> ingredients;

      public Shapeless(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
         super(group, category, result, ingredients);
         this.group = group;
         this.category = category;
         this.result = result;
         this.ingredients = ingredients;
      }

      @Override
      public RecipeType<?> getType() {
         return TYPE.get();
      }

      @Override
      public RecipeSerializer<?> getSerializer() {
         return SHAPELESS.get();
      }

      @Override
      public boolean showNotification() {
         return false;
      }

      @Override
      public ItemStack getToastSymbol() {
         return toast(this.result);
      }

      public static final class Serializer implements RecipeSerializer<Shapeless> {
         private static final int MAX = 9;
         private static final MapCodec<Shapeless> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
               Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
               CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
               ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.result),
               Ingredient.CODEC_NONEMPTY.listOf().fieldOf("ingredients").flatXmap(list -> {
                  Ingredient[] a = list.stream().filter(x -> !x.isEmpty()).toArray(Ingredient[]::new);
                  if (a.length == 0) {
                     return DataResult.<NonNullList<Ingredient>>error(() -> "No ingredients for bench shapeless recipe");
                  }
                  if (a.length > MAX) {
                     return DataResult.<NonNullList<Ingredient>>error(() -> "Too many ingredients for bench shapeless recipe (max " + MAX + ")");
                  }
                  return DataResult.success(NonNullList.of(Ingredient.EMPTY, a));
               }, DataResult::success).forGetter(r -> r.ingredients)
            ).apply(i, Shapeless::new));
         private static final StreamCodec<RegistryFriendlyByteBuf, Shapeless> STREAM = StreamCodec.of(
            (buf, r) -> {
               buf.writeUtf(r.group);
               buf.writeEnum(r.category);
               buf.writeVarInt(r.ingredients.size());
               for (Ingredient ing : r.ingredients) {
                  Ingredient.CONTENTS_STREAM_CODEC.encode(buf, ing);
               }
               ItemStack.STREAM_CODEC.encode(buf, r.result);
            },
            buf -> {
               String group = buf.readUtf();
               CraftingBookCategory category = buf.readEnum(CraftingBookCategory.class);
               int n = buf.readVarInt();
               NonNullList<Ingredient> ings = NonNullList.withSize(n, Ingredient.EMPTY);
               ings.replaceAll(x -> Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
               ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
               return new Shapeless(group, category, result, ings);
            });

         @Override
         public MapCodec<Shapeless> codec() {
            return CODEC;
         }

         @Override
         public StreamCodec<RegistryFriendlyByteBuf, Shapeless> streamCodec() {
            return STREAM;
         }
      }
   }
}
