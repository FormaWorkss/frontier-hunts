package com.formaworks.frontierstructures;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;

@Mod(FrontierStructures.ID)
public final class FrontierStructures {
    public static final String ID="frontierstructures";
    public static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(ID,path);}
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(ID);
    private static final DeferredRegister<StructureType<?>> STRUCTURES=DeferredRegister.create(Registries.STRUCTURE_TYPE,ID);
    private static final DeferredRegister<StructurePieceType> PIECES=DeferredRegister.create(Registries.STRUCTURE_PIECE,ID);
    public static final DeferredHolder<StructureType<?>,StructureType<ExpeditionSite>> SITE=STRUCTURES.register("expedition_site",()->()->ExpeditionSite.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> PIECE=PIECES.register("expedition_site",()->(StructurePieceType.StructureTemplateType)ExpeditionSite.Piece::new);
    // [villages] planned hunting villages built from the authored buildings
    public static final DeferredHolder<StructureType<?>,StructureType<Village>> VILLAGE=STRUCTURES.register("village",()->()->Village.CODEC);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> VILLAGE_GROUNDS=PIECES.register("village_grounds",()->(StructurePieceType.ContextlessType)Village.Grounds::new);
    public static final DeferredHolder<StructurePieceType,StructurePieceType> VILLAGE_BUILDING=PIECES.register("village_building",()->(StructurePieceType.StructureTemplateType)Village.Building::new);
    public static final DeferredBlock<Block> FLOOR=BLOCKS.registerSimpleBlock("workshop_floor",BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE));
    public static final DeferredBlock<StructureSpace> SPACE=BLOCKS.register("structure_space",()->new StructureSpace(BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().strength(0).isViewBlocking((s,l,p)->false).isSuffocating((s,l,p)->false)));
    public static final java.util.List<DeferredBlock<TimberWindow>> WINDOWS=new java.util.ArrayList<>();
    static {
        for(String name:new String[]{"lookout_stair_rail","lookout_deck_rail","lookout_brace","lookout_cross_brace"}) {
            var block=BLOCKS.register(name,()->new LookoutTimber(name,BlockBehaviour.Properties.ofFullCopy(Blocks.DARK_OAK_PLANKS).noOcclusion()));
            ITEMS.registerSimpleBlockItem(name,block);
        }
        for(String name:new String[]{"spruce_casement_window","dark_oak_lattice_window","spruce_picture_window"}){
            var block=BLOCKS.register(name,()->new TimberWindow(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).strength(.8f).noOcclusion().isViewBlocking((s,l,p)->false).isSuffocating((s,l,p)->false)));
            WINDOWS.add(block);ITEMS.registerSimpleBlockItem(name,block);
        }
    }
    public FrontierStructures(IEventBus bus){
        BLOCKS.register(bus);ITEMS.register(bus);STRUCTURES.register(bus);PIECES.register(bus);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{
            if(e.getTabKey()==net.minecraft.world.item.CreativeModeTabs.BUILDING_BLOCKS)for(var w:WINDOWS)e.accept(w.get());
        });
        NeoForge.EVENT_BUS.addListener(ExistingTerrain::commands);
        NeoForge.EVENT_BUS.addListener(ExistingTerrain::tick);
        NeoForge.EVENT_BUS.addListener(ExistingTerrain::stopped);
        NeoForge.EVENT_BUS.addListener(Workshop::commands);
        NeoForge.EVENT_BUS.addListener(Workshop::tick);
        NeoForge.EVENT_BUS.addListener(Workshop::stopped);
        NeoForge.EVENT_BUS.addListener(VillageCommand::register); // [villages]
        NeoForge.EVENT_BUS.addListener(VillageTrees::tick); // [1.1.0] tidy cut trees around villages already in a world
        NeoForge.EVENT_BUS.addListener(VillageTrees::stopped);
        com.formaworks.frontierhunts.expedition.SettlementTreeSpace.EXTRA = VillageTrees::reserved; // [1.1.0] no trees cut by villages
        com.formaworks.frontierhunts.wildlife2026.SpawnClearance.EXTRA = VillageTrees::inhabited; // [1.1.0] no wild animals spawning in villages
    }
}
