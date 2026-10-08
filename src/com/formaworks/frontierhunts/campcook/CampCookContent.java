package com.formaworks.frontierhunts.campcook;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.items.TabPlacement;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * [licence] Registration of hunting-camp cooking: the Camp Dutch Oven (block, block entity, menu, its
 * {@code frontierhunts:camp_cooking} recipe type), the dishes, the raw game sausage, the seven camp-meal effects and the
 * oven sounds. Self-registering (RegisterEvent).
 */
@EventBusSubscriber(modid = FrontierHunts.ID, bus = EventBusSubscriber.Bus.MOD)
public final class CampCookContent {
   public static final DeferredHolder<Block, DutchOvenBlock> DUTCH_OVEN = DeferredHolder.create(Registries.BLOCK, FrontierHunts.id("dutch_oven"));
   public static final DeferredHolder<Item, Item> DUTCH_OVEN_ITEM = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("dutch_oven"));
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DutchOvenEntity>> DUTCH_OVEN_ENTITY = DeferredHolder.create(
      Registries.BLOCK_ENTITY_TYPE, FrontierHunts.id("dutch_oven"));
   public static final DeferredHolder<MenuType<?>, MenuType<DutchOvenMenu>> MENU = DeferredHolder.create(Registries.MENU, FrontierHunts.id("dutch_oven"));
   public static final DeferredHolder<RecipeType<?>, RecipeType<CampCookingRecipe>> TYPE = DeferredHolder.create(Registries.RECIPE_TYPE,
      FrontierHunts.id("camp_cooking"));
   public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CampCookingRecipe>> SERIALIZER = DeferredHolder.create(
      Registries.RECIPE_SERIALIZER, FrontierHunts.id("camp_cooking"));
   public static final Map<Dish, DeferredHolder<Item, Item>> DISHES = new LinkedHashMap<>();
   public static final DeferredHolder<Item, Item> RAW_SAUSAGE = DeferredHolder.create(Registries.ITEM, FrontierHunts.id("raw_game_sausage"));
   public static final Map<MealBuffs.Buff, DeferredHolder<MobEffect, MobEffect>> EFFECTS = new EnumMap<>(MealBuffs.Buff.class);
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_SIMMER = DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id("amb_pot_simmer"));
   public static final DeferredHolder<SoundEvent, SoundEvent> SND_LID = DeferredHolder.create(Registries.SOUND_EVENT, FrontierHunts.id("amb_pot_lid"));

   static {
      for (Dish d : Dish.values()) {
         DISHES.put(d, DeferredHolder.create(Registries.ITEM, FrontierHunts.id(d.id)));
      }
      for (MealBuffs.Buff b : MealBuffs.Buff.values()) {
         EFFECTS.put(b, DeferredHolder.create(Registries.MOB_EFFECT, FrontierHunts.id(b.id())));
      }
   }

   private CampCookContent() {
   }

   public static Holder<MobEffect> effect(MealBuffs.Buff b) {
      return EFFECTS.get(b);
   }

   @SubscribeEvent
   public static void register(RegisterEvent e) {
      e.register(Registries.BLOCK, FrontierHunts.id("dutch_oven"), () -> new DutchOvenBlock(BlockBehaviour.Properties.of()
         .mapColor(MapColor.COLOR_BLACK).strength(2.5F, 6.0F).sound(SoundType.LANTERN).noOcclusion()
         .lightLevel(s -> s.getValue(DutchOvenBlock.LIT) && !s.getValue(DutchOvenBlock.GRATE) ? 6 : 0)));
      e.register(Registries.ITEM, FrontierHunts.id("dutch_oven"), () -> new BlockItem(DUTCH_OVEN.get(), new Item.Properties().stacksTo(1)));
      e.register(Registries.BLOCK_ENTITY_TYPE, FrontierHunts.id("dutch_oven"),
         () -> BlockEntityType.Builder.of(DutchOvenEntity::new, DUTCH_OVEN.get()).build(null));
      e.register(Registries.MENU, FrontierHunts.id("dutch_oven"), () -> IMenuTypeExtension.create(DutchOvenMenu::client));
      e.register(Registries.RECIPE_TYPE, FrontierHunts.id("camp_cooking"), () -> RecipeType.<CampCookingRecipe>simple(FrontierHunts.id("camp_cooking")));
      e.register(Registries.RECIPE_SERIALIZER, FrontierHunts.id("camp_cooking"), CampCookingRecipe.Serializer::new);
      for (Dish d : Dish.values()) {
         e.register(Registries.ITEM, FrontierHunts.id(d.id), () -> new CampDishItem(d));
      }
      e.register(Registries.ITEM, FrontierHunts.id("raw_game_sausage"), () -> new Item(new Item.Properties()
         .food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build())));
      for (MealBuffs.Buff b : MealBuffs.Buff.values()) {
         e.register(Registries.MOB_EFFECT, FrontierHunts.id(b.id()), () -> {
            MealEffect m = new MealEffect(b);
            if (b == MealBuffs.Buff.STAMINA) {
               m.addAttributeModifier(Attributes.MOVEMENT_SPEED, FrontierHunts.id("meal_stamina"), 0.04, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
            return m;
         });
      }
      for (String s : new String[]{"amb_pot_simmer", "amb_pot_lid"}) {
         ResourceLocation id = FrontierHunts.id(s);
         e.register(Registries.SOUND_EVENT, id, () -> SoundEvent.createVariableRangeEvent(id));
      }
   }

   /** Journal checklist: Camp Cook entries (counters bumped by the oven's dish slot and the licence harvest hook). */
   @SubscribeEvent
   public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent e) {
      e.enqueueWork(() -> {
         com.formaworks.frontierhunts.journal.Checklist.register("campcook_first", com.formaworks.frontierhunts.journal.Checklist.Category.SURVIVAL,
            "campcook.meals", 1, 30, "frontierhunts:dutch_oven");
         com.formaworks.frontierhunts.journal.Checklist.register("campcook_kinds_5", com.formaworks.frontierhunts.journal.Checklist.Category.SURVIVAL,
            "campcook.kinds", 5, 80, "frontierhunts:venison_stew");
         com.formaworks.frontierhunts.journal.Checklist.register("campcook_all", com.formaworks.frontierhunts.journal.Checklist.Category.SURVIVAL,
            "campcook.kinds", 10, 200, "frontierhunts:hunters_breakfast");
         com.formaworks.frontierhunts.journal.Checklist.register("campcook_fed", com.formaworks.frontierhunts.journal.Checklist.Category.SURVIVAL,
            "campcook.fed_take", 1, 40, "frontierhunts:backstrap_mushrooms");
      });
   }

   @SubscribeEvent
   public static void creative(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey() != HuntContent.GEAR_TAB) {
         return;
      }
      TabPlacement.after(e, DUTCH_OVEN_ITEM.get(), "drying_rack", "smokehouse");
      String anchor = "pemmican";
      TabPlacement.after(e, RAW_SAUSAGE.get(), anchor, "cooked_backstrap");
      anchor = "raw_game_sausage";
      for (Map.Entry<Dish, DeferredHolder<Item, Item>> d : DISHES.entrySet()) {
         TabPlacement.after(e, d.getValue().get(), anchor, "cooked_backstrap");
         anchor = d.getKey().id;
      }
   }
}
