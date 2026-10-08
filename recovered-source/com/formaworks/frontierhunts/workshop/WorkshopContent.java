package com.formaworks.frontierhunts.workshop;

import com.formaworks.frontierhunts.HuntContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public final class WorkshopContent {
   private static final Blocks BLOCKS = DeferredRegister.createBlocks("frontierhunts");
   public static final DeferredBlock<WorkbenchBlock> BENCH = BLOCKS.register(
      "weapons_workbench", () -> new WorkbenchBlock(Properties.of().strength(3.0F).sound(SoundType.WOOD).noOcclusion())
   );
   public static final DeferredBlock<WorkbenchBlock> ATTACHMENT_BENCH = BLOCKS.register(
      "attachment_workbench", () -> new WorkbenchBlock(Properties.of().strength(3.0F).sound(SoundType.WOOD).noOcclusion())
   );
   public static final DeferredItem<FieldRodItem> ROD = HuntContent.ITEMS.register("field_fishing_rod", FieldRodItem::new);
   public static final DeferredItem<Item> OPTIC = HuntContent.ITEMS
      .register("four_power_optic", () -> new Item(new net.minecraft.world.item.Item.Properties().stacksTo(1)));
   private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "frontierhunts");
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WorkbenchEntity>> ENTITY = ENTITIES.register(
      "weapons_workbench", () -> Builder.of(WorkbenchEntity::new, new Block[]{(Block)BENCH.get(), (Block)ATTACHMENT_BENCH.get()}).build(null)
   );
   private static final DeferredRegister<EntityType<?>> FLOATS = DeferredRegister.create(Registries.ENTITY_TYPE, "frontierhunts");
   public static final DeferredHolder<EntityType<?>, EntityType<FieldFloat>> FLOAT = FLOATS.register(
      "field_float",
      () -> net.minecraft.world.entity.EntityType.Builder.of(FieldFloat::new, MobCategory.MISC)
            .sized(0.08F, 0.22F)
            .clientTrackingRange(6)
            .updateInterval(2)
            .noSave()
            .build("frontierhunts:field_float")
   );
   private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "frontierhunts");
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> MENU = MENUS.register(
      "weapons_workbench", () -> new MenuType(WorkbenchMenu::new, FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<AttachmentMenu>> FITTING_MENU = MENUS.register(
      "attachment_fitting", () -> new MenuType(AttachmentMenu::new, FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> ATTACHMENT_MENU = MENUS.register(
      "attachment_workbench", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, true), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> AMMO_MENU = MENUS.register(
      "ammo_reloader", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, WorkshopKind.AMMUNITION), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> BOW_MENU = MENUS.register(
      "bow_workshop", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, WorkshopKind.BOWS), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> FISH_MENU = MENUS.register(
      "fishing_workshop", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, WorkshopKind.FISHING), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> CLOTHING_MENU = MENUS.register(
      "clothing_workshop", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, WorkshopKind.CLOTHING), FeatureFlags.DEFAULT_FLAGS)
   );
   public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> TENT_MENU = MENUS.register(
      "tent_bench", () -> new MenuType((var0, var1) -> new WorkbenchMenu(var0, var1, WorkshopKind.TENTS), FeatureFlags.DEFAULT_FLAGS)
   );

   public static void register(IEventBus var0) {
      BLOCKS.register(var0);
      MENUS.register(var0);
      ENTITIES.register(var0);
      FLOATS.register(var0);
      var0.addListener(FishingNetwork::register);
   }

   private WorkshopContent() {
   }

   static {
      HuntContent.ITEMS.registerSimpleBlockItem(BENCH);
      HuntContent.ITEMS.registerSimpleBlockItem(ATTACHMENT_BENCH);
   }
}
