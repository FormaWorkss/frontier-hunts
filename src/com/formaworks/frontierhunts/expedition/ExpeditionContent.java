package com.formaworks.frontierhunts.expedition;

import com.formaworks.frontierhunts.FrontierHunts;
import com.formaworks.frontierhunts.HuntContent;
import com.formaworks.frontierhunts.camp.CampingTent;
import com.formaworks.frontierhunts.environment.CascadeMist;
import com.formaworks.frontierhunts.environment.DeadfallLog;
import com.formaworks.frontierhunts.environment.ForestFloor;
import com.formaworks.frontierhunts.environment.WhiteWater;
import com.formaworks.frontierhunts.environment.WildLeaves;
import com.formaworks.frontierhunts.hunting.DeerSign;
import com.formaworks.frontierhunts.hunting.FallenBranch;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.OffsetType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Blocks;

public final class ExpeditionContent {
   public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "frontierhunts");
   public static final Map<String, DeferredItem<Item>> ITEMS = new LinkedHashMap<>();
   public static final Blocks BLOCKS = DeferredRegister.createBlocks("frontierhunts");
   public static final Map<String, DeferredBlock<CampingTent>> TENTS = new LinkedHashMap<>();
   public static final DeferredBlock<CampingTent> WOODLAND_TENT = BLOCKS.register(
      "woodland_camp_tent", () -> new CampingTent(false, Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<CampingTent> DOME_TENT = BLOCKS.register(
      "trail_dome_tent", () -> new CampingTent(true, Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<CampingTent> WALL_TENT = BLOCKS.register(
      "canvas_wall_tent", () -> new CampingTent("canvas_wall_tent", Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<CampingTent> BELL_TENT = BLOCKS.register(
      "bell_tent", () -> new CampingTent("bell_tent", Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<CampingTent> CABIN_TENT = BLOCKS.register(
      "family_cabin_tent", () -> new CampingTent("family_cabin_tent", Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<CampingTent> PUP_TENT = BLOCKS.register(
      "pup_tent", () -> new CampingTent("pup_tent", Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final Map<String, DeferredBlock<ScentDecoy>> DECOYS = new LinkedHashMap<>();
   public static final DeferredBlock<FallenBranch> FALLEN_BRANCH = BLOCKS.register(
      "fallen_branch",
      () -> new FallenBranch(
            Properties.of()
               .mapColor(MapColor.WOOD)
               .strength(0.2F)
               .sound(SoundType.WOOD)
               .noCollission()
               .noOcclusion()
               .instrument(NoteBlockInstrument.BASS)
               .pushReaction(PushReaction.DESTROY)
         )
   );
   public static final Map<String, DeferredBlock<ExpeditionStation>> STATIONS = new LinkedHashMap<>();
   public static final Map<String, DeferredBlock<DeerSign>> SIGNS = new LinkedHashMap<>();
   public static final DeferredBlock<ShootingTarget> SHOOTING_TARGET = BLOCKS.register(
      "shooting_target", () -> new ShootingTarget(Properties.of().strength(1.4F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<TrailCameraBlock> TRAIL_CAMERA = BLOCKS.register(
      "trail_camera", () -> new TrailCameraBlock(Properties.of().strength(1.2F).sound(SoundType.METAL).noOcclusion().noCollission())
   );
   public static final DeferredBlock<CameraHub> CAMERA_HUB = BLOCKS.register(
      "camera_base_station", () -> new CameraHub(Properties.of().strength(1.4F).sound(SoundType.METAL).noOcclusion())
   );
   public static final DeferredBlock<ForestFloor.Litter> FOREST_LITTER = BLOCKS.register(
      "forest_litter", () -> new ForestFloor.Litter(floor().mapColor(MapColor.DIRT).offsetType(OffsetType.XZ))
   );
   public static final DeferredBlock<ForestFloor.Undergrowth> UNDERGROWTH = BLOCKS.register(
      "undergrowth", () -> new ForestFloor.Undergrowth(floor().offsetType(OffsetType.XZ))
   );
   public static final DeferredBlock<ForestFloor.Boulder> MOSSY_STONE = BLOCKS.register(
      "mossy_stone", () -> new ForestFloor.Boulder(Properties.of().mapColor(MapColor.STONE).strength(1.1F).sound(SoundType.STONE).noOcclusion())
   );
   public static final DeferredBlock<DeadfallLog> DEADFALL_LOG = BLOCKS.register(
      "deadfall_log", () -> new DeadfallLog(Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava())
   );
   public static final DeferredBlock<CascadeMist> CASCADE_MIST = BLOCKS.register(
      "cascade_mist",
      () -> new CascadeMist(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().pushReaction(PushReaction.DESTROY))
   );
   public static final DeferredBlock<WhiteWater> WHITEWATER = BLOCKS.register(
      "whitewater",
      () -> new WhiteWater(Properties.of().replaceable().noCollission().noOcclusion().instabreak().noLootTable().pushReaction(PushReaction.DESTROY))
   );
   public static final DeferredBlock<Block> FOREST_DUFF = BLOCKS.register(
      "forest_duff", () -> new Block(Properties.of().mapColor(MapColor.PODZOL).strength(0.5F).sound(SoundType.ROOTED_DIRT))
   );
   public static final Map<String, DeferredBlock<LeavesBlock>> FOLIAGE = new LinkedHashMap<>();
   public static final DeferredBlock<TowerBlind> TOWER_BLIND = BLOCKS.register(
      "tower_blind", () -> new TowerBlind(Properties.of().strength(2.2F).sound(SoundType.METAL).noOcclusion())
   );
   public static final DeferredBlock<HubGroundBlind> GROUND_BLIND = BLOCKS.register(
      "hub_ground_blind", () -> new HubGroundBlind(Properties.of().strength(1.2F).sound(SoundType.WOOL).noOcclusion())
   );
   public static final DeferredBlock<MountedTreeStand> TREE_STAND = BLOCKS.register(
      "mounted_tree_stand", () -> new MountedTreeStand(Properties.of().strength(1.8F).sound(SoundType.METAL).noOcclusion())
   );
   public static final DeferredBlock<StandLadder> STAND_LADDER = BLOCKS.register(
      "stand_ladder", () -> new StandLadder(Properties.of().strength(1.8F).sound(SoundType.METAL).noOcclusion())
   );
   public static final DeferredBlock<FieldSpotlight> SPOTLIGHT = BLOCKS.register(
      "field_spotlight",
      () -> new FieldSpotlight(
            Properties.of().strength(2.0F).sound(SoundType.METAL).noOcclusion().lightLevel(var0 -> var0.getValue(FieldSpotlight.LIT) ? 15 : 0)
         )
   );
   public static final DeferredBlock<FieldSpotlight.Beam> BEAM = BLOCKS.register(
      "spotlight_beam",
      () -> new FieldSpotlight.Beam(
            Properties.of().noCollission().air().noLootTable().noOcclusion().lightLevel(var0 -> (Integer)var0.getValue(FieldSpotlight.Beam.POWER))
         )
   );
   public static final DeferredHolder<EntityType<?>, EntityType<TrophyMount>> TROPHY_MOUNT = TYPES.register(
      "wall_trophy",
      () -> Builder.of(TrophyMount::new, MobCategory.MISC)
            .sized(1.12F, 1.15F)
            .clientTrackingRange(8)
            .updateInterval(Integer.MAX_VALUE)
            .build("frontierhunts:wall_trophy")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<LensView>> LENS_VIEW = TYPES.register(
      "lens_view",
      () -> Builder.of(LensView::new, MobCategory.MISC)
            .sized(0.1F, 0.1F)
            .clientTrackingRange(16)
            .updateInterval(20)
            .noSummon()
            .noSave()
            .fireImmune()
            .build("frontierhunts:lens_view")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<TreeStandSeat>> STAND_SEAT = TYPES.register(
      "tree_stand_seat",
      () -> Builder.of(TreeStandSeat::new, MobCategory.MISC)
            .sized(0.15F, 0.15F)
            .clientTrackingRange(6)
            .updateInterval(10)
            .noSummon()
            .build("frontierhunts:tree_stand_seat")
   );
   public static final DeferredHolder<EntityType<?>, EntityType<HuntProjectile>> PROJECTILE = TYPES.register(
      "expedition_projectile",
      () -> Builder.of(HuntProjectile::new, MobCategory.MISC)
            .sized(0.08F, 0.08F)
            .clientTrackingRange(16)
            .updateInterval(1)
            .build("frontierhunts:expedition_projectile")
   );
   private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "frontierhunts");
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationStorage>> STORAGE = BLOCK_ENTITIES.register(
      "lodge_storage",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
               StationStorage::new,
               new Block[]{
                  (Block)STATIONS.get("lodge_stores").get(),
                  (Block)STATIONS.get("trophy_plinth").get(),
                  (Block)STATIONS.get("gun_rack").get(),
                  (Block)STATIONS.get("bow_tuning_rack").get()
               }
            )
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReserveCot.Anchor>> COT_ANCHOR = BLOCK_ENTITIES.register(
      "camp_cot",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
               ReserveCot.Anchor::new, new Block[]{(Block)ReserveArchitecture.BLOCKS.get("camp_cot").get()}
            )
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CampingTent.Anchor>> TENT_ANCHOR = BLOCK_ENTITIES.register(
      "camping_tent",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
               CampingTent.Anchor::new, TENTS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)
            )
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeerSign.Mark>> SIGN_MARK = BLOCK_ENTITIES.register(
      "deer_sign",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
               DeerSign.Mark::new, SIGNS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)
            )
            .build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TargetFace>> TARGET_FACE = BLOCK_ENTITIES.register(
      "shooting_target",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TargetFace::new, new Block[]{(Block)SHOOTING_TARGET.get()}).build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrailCamera>> CAMERA = BLOCK_ENTITIES.register(
      "trail_camera",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TrailCamera::new, new Block[]{(Block)TRAIL_CAMERA.get()}).build(null)
   );
   public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScentDecoy.Wick>> DECOY_WICK = BLOCK_ENTITIES.register(
      "scent_decoy",
      () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(
               ScentDecoy.Wick::new, DECOYS.values().stream().map(DeferredHolder::get).toArray(Block[]::new)
            )
            .build(null)
   );
   private static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, "frontierhunts");
   private static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, "frontierhunts");
   public static final DeferredHolder<StructureType<?>, StructureType<ExpeditionSite>> SITE = STRUCTURES.register(
      "expedition_site", () -> () -> ExpeditionSite.CODEC
   );
   public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register("expedition_site", () -> (StructurePieceType.StructureTemplateType)ExpeditionSite.Piece::new);

   private static Properties floor() {
      return Properties.of()
         .mapColor(MapColor.PLANT)
         .noCollission()
         .instabreak()
         .sound(SoundType.GRASS)
         .noOcclusion()
         .replaceable()
         .pushReaction(PushReaction.DESTROY)
         .ignitedByLava();
   }

   private static Properties needles(MapColor var0) {
      return Properties.of()
         .mapColor(var0)
         .strength(0.2F)
         .randomTicks()
         .sound(SoundType.GRASS)
         .noOcclusion()
         .isValidSpawn((var0x, var1, var2, var3) -> false)
         .isSuffocating((var0x, var1, var2) -> false)
         .isViewBlocking((var0x, var1, var2) -> false)
         .ignitedByLava()
         .pushReaction(PushReaction.DESTROY)
         .isRedstoneConductor((var0x, var1, var2) -> false);
   }

   private static void foliage(String var0, MapColor var1) {
      DeferredBlock var2 = BLOCKS.register(var0, () -> new WildLeaves(needles(var1)));
      FOLIAGE.put(var0, var2);
      HuntContent.ITEMS.registerSimpleBlockItem(var2);
   }

   private static void item(String var0, Supplier<Item> var1) {
      ITEMS.put(var0, HuntContent.ITEMS.register(var0, var1));
   }

   public static Item item(String var0) {
      return (Item)ITEMS.get(var0).get();
   }

   public static void register(IEventBus var0) {
      ReserveArchitecture.init();
      TYPES.register(var0);
      BLOCKS.register(var0);
      BLOCK_ENTITIES.register(var0);
      STRUCTURES.register(var0);
      PIECES.register(var0);
      var0.addListener(ExpeditionNetwork::register);
   }

   private ExpeditionContent() {
   }

   static {
      TENTS.put("woodland_camp_tent", WOODLAND_TENT);
      TENTS.put("trail_dome_tent", DOME_TENT);
      TENTS.put("canvas_wall_tent", WALL_TENT);
      TENTS.put("bell_tent", BELL_TENT);
      TENTS.put("family_cabin_tent", CABIN_TENT);
      TENTS.put("pup_tent", PUP_TENT);
      BLOCKS.addAlias(FrontierHunts.id("alpine_dome_tent"), FrontierHunts.id("bell_tent"));
      BLOCKS.addAlias(FrontierHunts.id("ridge_a_frame_tent"), FrontierHunts.id("pup_tent"));
      HuntContent.ITEMS.addAlias(FrontierHunts.id("alpine_dome_tent"), FrontierHunts.id("bell_tent"));
      HuntContent.ITEMS.addAlias(FrontierHunts.id("ridge_a_frame_tent"), FrontierHunts.id("pup_tent"));
      // [items] the anatomy binoculars were removed (user request); stacks saved in old worlds load as plain binoculars
      HuntContent.ITEMS.addAlias(FrontierHunts.id("anatomy_binoculars"), FrontierHunts.id("binoculars"));
      // [sign] the mock scrape kit was removed (user request); kits saved in old worlds load as bait
      HuntContent.ITEMS.addAlias(FrontierHunts.id("mock_scrape_kit"), FrontierHunts.id("bait"));
      HuntContent.ITEMS.registerSimpleBlockItem(FALLEN_BRANCH);
      item("ghillie_hood", () -> new GhillieSuit(Type.HELMET));
      item("ghillie_jacket", () -> new GhillieSuit(Type.CHESTPLATE));
      item("ghillie_trousers", () -> new GhillieSuit(Type.LEGGINGS));

      for (GhillieSuit.Pattern var3 : GhillieSuit.Pattern.values()) {
         if (var3 != GhillieSuit.Pattern.WOODLAND) {
            item(var3.item("hood"), () -> new GhillieSuit(Type.HELMET, var3));
            item(var3.item("jacket"), () -> new GhillieSuit(Type.CHESTPLATE, var3));
            item(var3.item("trousers"), () -> new GhillieSuit(Type.LEGGINGS, var3));
         }
      }

      for (String var13 : List.of("whitetail")) {
         DeferredBlock var21 = BLOCKS.register(var13 + "_scent_decoy", () -> new ScentDecoy(var13));
         DECOYS.put(var13, var21);
         HuntContent.ITEMS.registerSimpleBlockItem(var21);
      }

      for (DeerSign.Kind var26 : DeerSign.Kind.values()) {
         DeferredBlock var4 = BLOCKS.register(
            var26.blockId(),
            () -> DeerSign.create(
                  var26.id,
                  Properties.of()
                     .strength(0.25F)
                     .sound(var26 == DeerSign.Kind.RUB ? SoundType.WOOD : SoundType.GRASS)
                     .noCollission()
                     .noOcclusion()
                     .instabreak()
                     .replaceable()
               )
         );
         SIGNS.put(var26.id, var4);
      }

      HuntContent.ITEMS.registerSimpleBlockItem(TRAIL_CAMERA);
      HuntContent.ITEMS.registerSimpleBlockItem(CAMERA_HUB);
      HuntContent.ITEMS.registerSimpleBlockItem(FOREST_LITTER);
      HuntContent.ITEMS.registerSimpleBlockItem(UNDERGROWTH);
      HuntContent.ITEMS.registerSimpleBlockItem(MOSSY_STONE);
      HuntContent.ITEMS.registerSimpleBlockItem(DEADFALL_LOG);
      HuntContent.ITEMS.registerSimpleBlockItem(FOREST_DUFF);
      foliage("pine_needles", MapColor.COLOR_GREEN);
      foliage("fir_needles", MapColor.PLANT);
      foliage("aspen_leaves", MapColor.COLOR_LIGHT_GREEN);
      foliage("birch_leaves", MapColor.COLOR_LIGHT_GREEN);
      foliage("maple_leaves", MapColor.COLOR_ORANGE);
      foliage("willow_leaves", MapColor.PLANT);
      HuntContent.ITEMS.registerSimpleBlockItem(SHOOTING_TARGET);

      for (Coverall.Style var27 : Coverall.Style.values()) {
         if (var27 == Coverall.Style.SCENT_SUIT) { // [clothing] the Carbon Scent Suit is a base layer worn under clothing
            item(var27.id, () -> new ScentControl(ScentControl.Piece.SUIT));
            continue;
         }
         item(var27.id, () -> new Coverall(var27));
      }

      item("carbon_hood", () -> new ScentControl(Type.HELMET));
      item("carbon_jacket", () -> new ScentControl(Type.CHESTPLATE));
      item("carbon_trousers", () -> new ScentControl(Type.LEGGINGS));
      item("field_camera", FieldCamera::new);
      item("field_battery_pack", FieldElectronics.Battery::new);
      item("night_vision_binoculars", () -> new ExpeditionGear("night_vision_binoculars"));

      for (String var16 : TENTS.keySet()) {
         item(var16, () -> new CampingTent.Kit(var16));
      }

      for (Weapon var28 : Weapon.values()) {
         item(var28.id(), () -> new ExpeditionWeapon(var28));
      }

      for (String var18 : List.of("rifle_round", "shotgun_shell", "pistol_round", "tranquilizer_dart", "bowfishing_arrow", "flare_round")) {
         item(var18, () -> new Item(new net.minecraft.world.item.Item.Properties()));
      }

      for (String var19 : List.of(
         "expedition_guide",
         "binoculars",
         "rangefinder",
         "thermal_binoculars",
         "deer_call",
         "predator_call",
         "grunt_tube",
         "bleat_call",
         "rattling_antlers",
         "wind_checker",
         "scent_cover",
         "bait",
         "medkit",
         "field_tent",
         "suppressor",
         "sniper_magazine",
         "pistol_magazine",
         "extended_magazine",
         "steady_stock",
         "bipod",
         "reflex_sight",
         "micro_red_dot",
         "holographic_sight",
         "two_power_prism",
         "muzzle_brake",
         "angled_foregrip",
         "six_power_scope",
         "eight_power_scope",
         "twelve_power_scope",
         "thermal_scope",
         "fishing_drag_kit",
         "glow_lure",
         "field_spotlight",
         "field_flashlight",
         "attachment_tool",
         "hunter_pack",
         "field_blind",
         "tree_stand",
         "tower_blind",
         "fish_finder",
         "landing_net",
         "chum_bucket"
      )) {
         item(var19, () -> new ExpeditionGear(var19));
      }

      for (String var20 : List.of(
         "expedition_board",
         "ammo_reloader",
         "bow_tuning_rack",
         "fishing_station",
         "clothing_workbench",
         "tent_bench",
         "lodge_stores",
         "trophy_plinth",
         "gun_rack"
      )) {
         DeferredBlock var25 = BLOCKS.register(var20, () -> ExpeditionStation.create(var20, Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
         STATIONS.put(var20, var25);
         HuntContent.ITEMS.registerSimpleBlockItem(var25);
      }
   }
}
