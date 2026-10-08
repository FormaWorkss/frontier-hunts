package com.formaworks.frontierhunts.survival;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import com.formaworks.frontierhunts.survival.block.DryingRackBlock;
import com.formaworks.frontierhunts.survival.block.DryingRackEntity;
import com.formaworks.frontierhunts.survival.block.HideBedrollBlock;
import com.formaworks.frontierhunts.survival.item.GarmentItem;
import com.formaworks.frontierhunts.survival.item.LiningRecipe;
import com.formaworks.frontierhunts.survival.item.SaltCureRecipe;
import com.formaworks.frontierhunts.survival.item.SurvivalItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import com.mojang.serialization.Codec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [survival] Registration of everything Frontier Survival adds: foods, hides, warm clothing, the drying rack and hide
 * bedroll, the freshness / lining item components and the lining + salt-cure recipes. Self-registering (RegisterEvent),
 * no edits to FrontierHunts.java.
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class SurvivalContent {
   private SurvivalContent() {
   }

   public static ResourceLocation id(String name) {
      return ResourceLocation.fromNamespaceAndPath(FrontierHunts.ID, name);
   }

   // ------------------------------------------------------------------ components
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<Freshness>> FRESHNESS = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, id("freshness")
   );
   /** Bit 1 = fur lining, bit 2 = fur mittens (chest pieces). */
   public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> LINING = DeferredHolder.create(
      Registries.DATA_COMPONENT_TYPE, id("lining")
   );

   // ------------------------------------------------------------------ armor materials
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> BUCKSKIN = DeferredHolder.create(Registries.ARMOR_MATERIAL, id("buckskin"));
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> FUR = DeferredHolder.create(Registries.ARMOR_MATERIAL, id("frontier_fur"));
   public static final DeferredHolder<ArmorMaterial, ArmorMaterial> HEAVY = DeferredHolder.create(Registries.ARMOR_MATERIAL, id("heavy_hide"));

   // ------------------------------------------------------------------ blocks
   public static final DeferredHolder<Block, DryingRackBlock> DRYING_RACK = DeferredHolder.create(Registries.BLOCK, id("drying_rack"));
   public static final DeferredHolder<Block, HideBedrollBlock> BEDROLL = DeferredHolder.create(Registries.BLOCK, id("hide_bedroll"));
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DryingRackEntity>> DRYING_RACK_ENTITY = DeferredHolder.create(
      Registries.BLOCK_ENTITY_TYPE, id("drying_rack")
   );

   // ------------------------------------------------------------------ recipes
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LiningRecipe>> LINING_RECIPE = DeferredHolder.create(
      Registries.RECIPE_SERIALIZER, id("fur_lining")
   );
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SaltCureRecipe>> SALT_CURE = DeferredHolder.create(
      Registries.RECIPE_SERIALIZER, id("salt_cure")
   );

   // ------------------------------------------------------------------ items (id -> factory, in creative order)
   private static final Map<String, Supplier<Item>> FACTORIES = new LinkedHashMap<>();
   private static final Map<String, DeferredHolder<Item, Item>> ITEMS = new LinkedHashMap<>();

   public static final DeferredHolder<Item, Item> GAME_MEAT = food("game_meat", 3, 0.3F);
   public static final DeferredHolder<Item, Item> COOKED_GAME = food("cooked_game", 8, 0.85F);
   public static final DeferredHolder<Item, Item> BEAR_MEAT = food("bear_meat", 3, 0.3F);
   public static final DeferredHolder<Item, Item> COOKED_BEAR_MEAT = food("cooked_bear_meat", 8, 0.9F);
   public static final DeferredHolder<Item, Item> WILD_FOWL = food("wild_fowl", 2, 0.3F);
   public static final DeferredHolder<Item, Item> COOKED_WILD_FOWL = food("cooked_wild_fowl", 6, 0.65F);
   public static final DeferredHolder<Item, Item> ORGAN_MEAT = food("organ_meat", 3, 0.5F);
   public static final DeferredHolder<Item, Item> COOKED_ORGAN_MEAT = food("cooked_organ_meat", 6, 0.8F);
   public static final DeferredHolder<Item, Item> GAME_FAT = food("game_fat", 1, 0.2F);
   public static final DeferredHolder<Item, Item> TALLOW = food("tallow", 2, 0.5F);
   public static final DeferredHolder<Item, Item> JERKY = reg("jerky", () -> new SurvivalItem(new Item.Properties().food(
      new FoodProperties.Builder().nutrition(4).saturationModifier(0.55F).fast().build()
   )));
   public static final DeferredHolder<Item, Item> PEMMICAN = food("pemmican", 7, 1.0F);
   public static final DeferredHolder<Item, Item> SALT = reg("salt", () -> new SurvivalItem(new Item.Properties()));
   public static final DeferredHolder<Item, Item> SPOILED_MEAT = reg("spoiled_meat", () -> new SurvivalItem(new Item.Properties().food(
      new FoodProperties.Builder()
         .nutrition(2)
         .saturationModifier(0.1F)
         .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 600, 0), 1.0F)
         .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 240, 0), 0.6F)
         .effect(() -> new MobEffectInstance(MobEffects.POISON, 80, 0), 0.3F)
         .build()
   )));
   public static final DeferredHolder<Item, Item> HEAVY_HIDE = plain("heavy_hide");
   public static final DeferredHolder<Item, Item> FUR_PELT = plain("fur_pelt");
   public static final DeferredHolder<Item, Item> BEAR_PELT = plain("bear_pelt");
   public static final DeferredHolder<Item, Item> TANNED_HEAVY_HIDE = plain("tanned_heavy_hide");
   public static final DeferredHolder<Item, Item> TANNED_FUR = plain("tanned_fur");
   public static final DeferredHolder<Item, Item> BEAR_FUR = plain("bear_fur");
   public static final DeferredHolder<Item, Item> FUR_LINING = plain("fur_lining");
   public static final DeferredHolder<Item, Item> FUR_MITTENS = plain("fur_mittens");
   public static final DeferredHolder<Item, Item> FUR_HAT = garment("fur_hat", GarmentItem.Kind.FUR_HAT);
   public static final DeferredHolder<Item, Item> BUCKSKIN_COAT = garment("buckskin_coat", GarmentItem.Kind.BUCKSKIN_COAT);
   public static final DeferredHolder<Item, Item> BEAR_FUR_COAT = garment("bear_fur_coat", GarmentItem.Kind.BEAR_FUR_COAT);
   public static final DeferredHolder<Item, Item> HIDE_ROBE = garment("hide_robe", GarmentItem.Kind.HIDE_ROBE);
   public static final DeferredHolder<Item, Item> BUCKSKIN_LEGGINGS = garment("buckskin_leggings", GarmentItem.Kind.BUCKSKIN_LEGGINGS);
   public static final DeferredHolder<Item, Item> FUR_MUKLUKS = garment("fur_mukluks", GarmentItem.Kind.FUR_MUKLUKS);
   public static final DeferredHolder<Item, Item> DRYING_RACK_ITEM = reg("drying_rack", () -> new BlockItem(DRYING_RACK.get(), new Item.Properties()));
   public static final DeferredHolder<Item, Item> BEDROLL_ITEM = reg("hide_bedroll", () -> new BlockItem(BEDROLL.get(), new Item.Properties().stacksTo(1)));

   private static DeferredHolder<Item, Item> reg(String name, Supplier<Item> factory) {
      FACTORIES.put(name, factory);
      DeferredHolder<Item, Item> h = DeferredHolder.create(Registries.ITEM, id(name));
      ITEMS.put(name, h);
      return h;
   }

   private static DeferredHolder<Item, Item> food(String name, int nutrition, float saturation) {
      return reg(name, () -> new SurvivalItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
   }

   private static DeferredHolder<Item, Item> plain(String name) {
      return reg(name, () -> new SurvivalItem(new Item.Properties()));
   }

   private static DeferredHolder<Item, Item> garment(String name, GarmentItem.Kind kind) {
      return reg(name, () -> new GarmentItem(kind));
   }

   /** All survival items, in creative-tab order. */
   public static List<Item> items() {
      List<Item> out = new ArrayList<>();
      for (DeferredHolder<Item, Item> h : ITEMS.values()) {
         if (h.isBound()) {
            out.add(h.get());
         }
      }
      return out;
   }

   public static List<Item> garments() {
      List<Item> out = new ArrayList<>();
      for (DeferredHolder<Item, Item> h : ITEMS.values()) {
         if (h.isBound() && h.get() instanceof GarmentItem) {
            out.add(h.get());
         }
      }
      return out;
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.DATA_COMPONENT_TYPE, id("freshness"), () -> DataComponentType.<Freshness>builder()
            .persistent(Freshness.CODEC)
            .networkSynchronized(Freshness.STREAM_CODEC)
            .build());
      e.register(Registries.DATA_COMPONENT_TYPE, id("lining"), () -> DataComponentType.<Integer>builder()
            .persistent(Codec.intRange(0, 255))
            .networkSynchronized(ByteBufCodecs.VAR_INT)
            .build());
      e.register(Registries.ARMOR_MATERIAL, id("buckskin"), () -> material(3, 2, 1, 1, "buckskin", HuntContent.TANNED_HIDE::get));
      e.register(Registries.ARMOR_MATERIAL, id("frontier_fur"), () -> material(4, 2, 1, 1, "frontier_fur", () -> TANNED_FUR.get()));
      e.register(Registries.ARMOR_MATERIAL, id("heavy_hide"), () -> material(3, 2, 1, 1, "heavy_hide", () -> TANNED_HEAVY_HIDE.get()));
      e.register(Registries.BLOCK, id("drying_rack"), () -> new DryingRackBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.2F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
         ));
      e.register(Registries.BLOCK, id("hide_bedroll"), () -> new HideBedrollBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(0.3F).sound(SoundType.WOOL).noOcclusion().ignitedByLava()
               .pushReaction(PushReaction.DESTROY)
         ));
      e.register(Registries.BLOCK_ENTITY_TYPE, id("drying_rack"), () -> BlockEntityType.Builder.of(DryingRackEntity::new, DRYING_RACK.get()).build(null));
      e.register(Registries.RECIPE_SERIALIZER, id("fur_lining"), () -> new SimpleCraftingRecipeSerializer<>(LiningRecipe::new));
      e.register(Registries.RECIPE_SERIALIZER, id("salt_cure"), () -> new SimpleCraftingRecipeSerializer<>(SaltCureRecipe::new));
      for (Map.Entry<String, Supplier<Item>> f : FACTORIES.entrySet()) {
         e.register(Registries.ITEM, id(f.getKey()), f.getValue());
      }
   }

   private static ArmorMaterial material(int chest, int legs, int head, int feet, String layer, Supplier<ItemLike> repair) {
      return new ArmorMaterial(
         Map.of(ArmorItem.Type.HELMET, head, ArmorItem.Type.CHESTPLATE, chest, ArmorItem.Type.LEGGINGS, legs, ArmorItem.Type.BOOTS, feet),
         12,
         SoundEvents.ARMOR_EQUIP_LEATHER,
         () -> Ingredient.of(repair.get()),
         List.of(new ArmorMaterial.Layer(id(layer))),
         0.0F,
         0.0F
      );
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() != HuntContent.GEAR_TAB) {
         return;
      }
      String anchor = "whitetail_trophy";
      for (Map.Entry<String, DeferredHolder<Item, Item>> h : ITEMS.entrySet()) {
         // [recipes] fur & hide clothing, linings and mittens are Clothing Table work: not in the creative tabs (like ghillie/camo)
         if (h.getValue().isBound() && !com.formaworks.frontierhunts.recipes.ClothingTableItems.only(h.getKey())) {
            TabPlacement.after(e, h.getValue().get(), anchor, "cooked_backstrap");
            anchor = h.getKey();
         }
      }
   }

   static boolean isOurs(Item item) {
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
      return id.getNamespace().equals(FrontierHunts.ID) && ITEMS.containsKey(id.getPath());
   }
}
