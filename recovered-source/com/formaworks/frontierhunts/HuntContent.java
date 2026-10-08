package com.formaworks.frontierhunts;

import com.formaworks.frontierhunts.camp.CampContent;
import com.formaworks.frontierhunts.expedition.ExpeditionContent;
import com.formaworks.frontierhunts.expedition.ReserveArchitecture;
import com.formaworks.frontierhunts.expedition.TrophyMount;
import com.formaworks.frontierhunts.hunting.ArrowRefitRecipe;
import com.formaworks.frontierhunts.hunting.ArrowTip;
import com.formaworks.frontierhunts.hunting.ArrowTipItem;
import com.formaworks.frontierhunts.hunting.ButcherRecipe;
import com.formaworks.frontierhunts.hunting.DeerSpawnEgg;
import com.formaworks.frontierhunts.hunting.DeerTraits;
import com.formaworks.frontierhunts.hunting.FieldBow;
import com.formaworks.frontierhunts.hunting.FieldItem;
import com.formaworks.frontierhunts.hunting.GameSpecies;
import com.formaworks.frontierhunts.hunting.HuntArrowItem;
import com.formaworks.frontierhunts.hunting.HuntEntities;
import com.formaworks.frontierhunts.hunting.QuiverItem;
import com.formaworks.frontierhunts.workshop.EquipmentCatalog;
import com.formaworks.frontierhunts.workshop.WorkshopContent;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;

public final class HuntContent {
   public static final Items ITEMS = DeferredRegister.createItems("frontierhunts");
   public static final DeferredItem<Item> JOURNAL = ITEMS.register("hunter_journal", () -> new Item(new Properties().stacksTo(1)) {
         public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
            if (var2 instanceof ServerPlayer var4) {
               HuntService.openJournal(var4);
            }

            return InteractionResultHolder.sidedSuccess(var2.getItemInHand(var3), var1.isClientSide);
         }
      });
   public static final DeferredItem<Item> FIELD_BOW = ITEMS.register("field_bow", () -> new FieldBow(new Properties().durability(480)));
   public static final DeferredItem<Item> FIELD_ARROW = ITEMS.register("field_arrow", () -> new HuntArrowItem(false, false, new Properties()));
   public static final DeferredItem<Item> PRIMITIVE_ARROW = ITEMS.register("primitive_arrow", () -> new HuntArrowItem(true, false, new Properties()));
   public static final DeferredItem<Item> TRACER_ARROW = ITEMS.register("tracer_arrow", () -> new HuntArrowItem(false, true, new Properties()));
   public static final Map<ArrowTip, DeferredItem<Item>> TIPS = new EnumMap<>(ArrowTip.class);
   public static final DeferredItem<Item> QUIVER;
   public static final DeferredItem<Item> FIELD_KNIFE;
   public static final DeferredItem<Item> SKINNING_TOOL;
   public static final DeferredItem<Item> DEER_HIDE;
   public static final DeferredItem<Item> VENISON;
   public static final DeferredItem<Item> COOKED_VENISON;
   public static final DeferredItem<Item> VENISON_QUARTER;
   public static final DeferredItem<Item> AGED_VENISON;
   public static final DeferredItem<Item> BACKSTRAP;
   public static final DeferredItem<Item> COOKED_BACKSTRAP;
   public static final DeferredItem<Item> TANNED_HIDE;
   public static final DeferredItem<Item> WHITETAIL_TROPHY;
   public static final DeferredItem<Item> BUCK_EGG;
   public static final DeferredItem<Item> DOE_EGG;
   public static final DeferredItem<Item> WHITETAIL_EGG;
   public static final DeferredItem<Item> ELK_BULL_EGG;
   public static final DeferredItem<Item> ELK_COW_EGG;
   public static final DeferredItem<Item> ELK_EGG;
   public static final DeferredItem<Item> MOOSE_BULL_EGG;
   public static final DeferredItem<Item> MOOSE_COW_EGG;
   public static final DeferredItem<Item> MOOSE_EGG;
   private static final DeferredRegister<CreativeModeTab> TABS;
   private static final DeferredRegister<RecipeSerializer<?>> RECIPES;
   public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ArrowRefitRecipe>> ARROW_REFIT;
   public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ButcherRecipe>> BUTCHER;
   public static final ResourceKey<CreativeModeTab> WILDLIFE_TAB;
   public static final ResourceKey<CreativeModeTab> WORLD_TAB;
   public static final ResourceKey<CreativeModeTab> GEAR_TAB;

   public static boolean archery(Item var0) {
      return var0 != FIELD_ARROW.get() && var0 != PRIMITIVE_ARROW.get() && var0 != QUIVER.get() ? var0 instanceof ArrowTipItem : true;
   }

   private static DeferredItem<?>[] eggs() {
      return new DeferredItem[]{BUCK_EGG, DOE_EGG, WHITETAIL_EGG, ELK_BULL_EGG, ELK_COW_EGG, ELK_EGG, MOOSE_BULL_EGG, MOOSE_COW_EGG, MOOSE_EGG};
   }

   public static void register(IEventBus var0) {
      ITEMS.register(var0);
      TABS.register(var0);
      RECIPES.register(var0);
      var0.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent var0x) -> { // [onebook] decompile fix: typed lambda
         if (var0x.getTabKey().location().equals(ResourceLocation.withDefaultNamespace("spawn_eggs"))) {
            for (DeferredItem var4 : eggs()) {
               var0x.accept((ItemLike)var4.get());
            }
         }
      });
   }

   private HuntContent() {
   }

   static {
      for (ArrowTip var3 : ArrowTip.values()) {
         TIPS.put(var3, ITEMS.register(var3.id, () -> new ArrowTipItem(var3, new Properties())));
      }

      QUIVER = ITEMS.register("hunters_quiver", () -> new QuiverItem(new Properties()));
      FIELD_KNIFE = ITEMS.register(
         "field_knife", () -> new SwordItem(Tiers.IRON, new Properties().durability(240).attributes(SwordItem.createAttributes(Tiers.IRON, 1, -1.8F)))
      );
      SKINNING_TOOL = ITEMS.register("skinning_tool", () -> new FieldItem(new Properties().durability(384)));
      DEER_HIDE = ITEMS.register("deer_hide", () -> new FieldItem(new Properties()));
      VENISON = ITEMS.register("venison", () -> new FieldItem(new Properties().food(new Builder().nutrition(3).saturationModifier(0.3F).build())));
      COOKED_VENISON = ITEMS.register("cooked_venison", () -> new FieldItem(new Properties().food(new Builder().nutrition(8).saturationModifier(0.8F).build())));
      VENISON_QUARTER = ITEMS.register(
         "venison_quarter", () -> new FieldItem(new Properties().stacksTo(16).food(new Builder().nutrition(4).saturationModifier(0.3F).build()))
      );
      AGED_VENISON = ITEMS.register(
         "aged_venison", () -> new FieldItem(new Properties().stacksTo(16).food(new Builder().nutrition(5).saturationModifier(0.4F).build()))
      );
      BACKSTRAP = ITEMS.register("backstrap", () -> new FieldItem(new Properties().food(new Builder().nutrition(4).saturationModifier(0.4F).build())));
      COOKED_BACKSTRAP = ITEMS.register(
         "cooked_backstrap", () -> new FieldItem(new Properties().food(new Builder().nutrition(11).saturationModifier(1.1F).build()))
      );
      TANNED_HIDE = ITEMS.register("tanned_hide", () -> new FieldItem(new Properties()));
      WHITETAIL_TROPHY = ITEMS.register(
         "whitetail_trophy",
         () -> new FieldItem(new Properties().stacksTo(1)) {
               public InteractionResult useOn(UseOnContext var1) {
                  return TrophyMount.place(var1);
               }

               public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
                  CustomData var5 = (CustomData)var1.get(DataComponents.CUSTOM_DATA);
                  if (var5 != null) {
                     CompoundTag var6 = var5.copyTag();
                     if (var6.getCompound("deer_traits").getInt("schema") >= 1) {
                        DeerTraits var7 = DeerTraits.load(var6.getCompound("deer_traits"));
                        var3.add(Component.literal(var7.description() + " · " + var7.massKg() + " kg"));
                        var3.add(
                           Component.literal(
                              var7.buck() ? var7.totalPoints() + " points · " + var7.trophyGrade() + " · Score " + var7.trophyScore() : "Antlerless harvest"
                           )
                        );
                     }

                     var3.add(
                        Component.literal(
                           String.format(Locale.ROOT, "%.1f m · %s", var6.getDouble("shot_metres"), var6.getString("region").toLowerCase(Locale.ROOT))
                        )
                     );
                  }
               }
            }
      );
      ITEMS.addAlias(FrontierHunts.id("european_mount"), FrontierHunts.id("whitetail_trophy"));
      BUCK_EGG = ITEMS.register("whitetail_buck_spawn_egg", () -> new DeerSpawnEgg(true));
      DOE_EGG = ITEMS.register("whitetail_doe_spawn_egg", () -> new DeerSpawnEgg(false));
      WHITETAIL_EGG = ITEMS.register("whitetail_spawn_egg", () -> new DeferredSpawnEggItem(HuntEntities.WHITETAIL, 8414026, 14735037, new Properties()));
      ELK_BULL_EGG = ITEMS.register("elk_bull_spawn_egg", () -> new DeerSpawnEgg(GameSpecies.ELK, true, 6176550, 14205850));
      ELK_COW_EGG = ITEMS.register("elk_cow_spawn_egg", () -> new DeerSpawnEgg(GameSpecies.ELK, false, 8149564, 14865072));
      ELK_EGG = ITEMS.register(
         "elk_spawn_egg", () -> new DeferredSpawnEggItem((Supplier)HuntEntities.GAME.get(GameSpecies.ELK), 7031342, 14271130, new Properties())
      );
      MOOSE_BULL_EGG = ITEMS.register("moose_bull_spawn_egg", () -> new DeerSpawnEgg(GameSpecies.MOOSE, true, 2761246, 10127986));
      MOOSE_COW_EGG = ITEMS.register("moose_cow_spawn_egg", () -> new DeerSpawnEgg(GameSpecies.MOOSE, false, 3879467, 11773327));
      MOOSE_EGG = ITEMS.register(
         "moose_spawn_egg", () -> new DeferredSpawnEggItem((Supplier)HuntEntities.GAME.get(GameSpecies.MOOSE), 3024418, 9075562, new Properties())
      );
      TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "frontierhunts");
      RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "frontierhunts");
      ARROW_REFIT = RECIPES.register("arrow_refit", () -> new SimpleCraftingRecipeSerializer(ArrowRefitRecipe::new));
      BUTCHER = RECIPES.register("butcher", () -> new SimpleCraftingRecipeSerializer(ButcherRecipe::new));
      WILDLIFE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, FrontierHunts.id("wildlife"));
      WORLD_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, FrontierHunts.id("world"));
      GEAR_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, FrontierHunts.id("field_equipment"));
      TABS.register(
         "wildlife",
         () -> CreativeModeTab.builder()
               .title(Component.translatable("itemGroup.frontierhunts.wildlife"))
               .icon(() -> new ItemStack((ItemLike)BUCK_EGG.get()))
               .displayItems((var0, var1) -> {
                  for (DeferredItem var5 : eggs()) {
                     var1.accept((ItemLike)var5.get());
                  }

                  var1.accept(net.minecraft.world.item.Items.RABBIT_SPAWN_EGG);
                  var1.accept(net.minecraft.world.item.Items.FOX_SPAWN_EGG);
               })
               .build()
      );
      TABS.register(
         "world",
         () -> CreativeModeTab.builder()
               .title(Component.translatable("itemGroup.frontierhunts.world"))
               .withTabsBefore(new ResourceKey[]{WILDLIFE_TAB})
               .icon(() -> new ItemStack((ItemLike)ExpeditionContent.UNDERGROWTH.get()))
               .displayItems((var0, var1) -> {
                  var1.accept((ItemLike)ExpeditionContent.MOSSY_STONE.get());
                  var1.accept((ItemLike)ExpeditionContent.FOREST_DUFF.get());
                  var1.accept((ItemLike)ExpeditionContent.FOREST_LITTER.get());
                  var1.accept((ItemLike)ExpeditionContent.UNDERGROWTH.get());
                  var1.accept((ItemLike)ExpeditionContent.FALLEN_BRANCH.get());
                  var1.accept((ItemLike)ExpeditionContent.DEADFALL_LOG.get());
                  ExpeditionContent.FOLIAGE.values().forEach(var1x -> var1.accept((ItemLike)var1x.get()));
                  ReserveArchitecture.BLOCKS.values().forEach(var1x -> var1.accept((ItemLike)var1x.get()));
               })
               .build()
      );
      TABS.register(
         "field_equipment",
         () -> CreativeModeTab.builder()
               .title(Component.translatable("itemGroup.frontierhunts"))
               .withTabsBefore(new ResourceKey[]{WILDLIFE_TAB, WORLD_TAB})
               .icon(() -> new ItemStack(com.formaworks.frontierhunts.onebook.OneBook.handbookOr(JOURNAL.get()))) // [onebook] tab icon = the Frontier Handbook
               .displayItems(
                  (var0, var1) -> {
                     // [onebook] the Frontier Handbook is the only book: the Hunter's Journal and Expedition Guide items are legacy
                     // (registered for old worlds, hidden, uncraftable); their screens open from the Handbook and J / N
                     var1.accept(com.formaworks.frontierhunts.onebook.OneBook.handbookOr(JOURNAL.get()));
                     var1.accept((ItemLike)WorkshopContent.BENCH.get());
                     var1.accept((ItemLike)WorkshopContent.ATTACHMENT_BENCH.get());
                     ExpeditionContent.STATIONS
                        .entrySet()
                        .stream()
                        .filter(var0x -> !var0x.getKey().equals("trophy_plinth"))
                        .forEach(var1x -> var1.accept((ItemLike)var1x.getValue().get()));
                     var1.accept((ItemLike)CampContent.CONTRACT_BOARD.get());
                     var1.accept((ItemLike)CampContent.SMOKEHOUSE.get());
                     var1.accept((ItemLike)CampContent.TANNING_RACK.get());
                     var1.accept((ItemLike)CampContent.GAME_POLE.get());
                     ExpeditionContent.ITEMS
                        .entrySet()
                        .stream()
                        .filter(var0x -> !var0x.getKey().equals("expedition_guide") && EquipmentCatalog.creative((Item)var0x.getValue().get()))
                        .forEach(var1x -> var1.accept((ItemLike)var1x.getValue().get()));
                     var1.accept((ItemLike)QUIVER.get());
                     var1.accept((ItemLike)WorkshopContent.ROD.get());
                     var1.accept((ItemLike)ExpeditionContent.TRAIL_CAMERA.get());
                     var1.accept((ItemLike)ExpeditionContent.CAMERA_HUB.get());
                     var1.accept((ItemLike)ExpeditionContent.SHOOTING_TARGET.get());

                     for (DeferredItem var5 : new DeferredItem[]{
                        DEER_HIDE, TANNED_HIDE, VENISON, COOKED_VENISON, VENISON_QUARTER, AGED_VENISON, BACKSTRAP, COOKED_BACKSTRAP, WHITETAIL_TROPHY
                     }) {
                        var1.accept((ItemLike)var5.get());
                     }
                  }
               )
               .build()
      );
   }
}
