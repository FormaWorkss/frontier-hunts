package com.formaworks.frontierhunts.client.tree;

import java.io.BufferedReader;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** Swaps the realistic world's trunk models for connected {@link TrunkModel}s while the pack is loaded. */
public final class TreeModels {
   static final ResourceLocation ROUND_LIST = ResourceLocation.fromNamespaceAndPath("frontierhunts", "realistic_world/round_blocks.txt");

   private TreeModels() {
   }

   public static void wrap(ModelEvent.ModifyBakingResult event) {
      Set<Block> logs = Collections.newSetFromMap(new IdentityHashMap<>());
      try {
         Resource res = Minecraft.getInstance().getResourceManager().getResource(ROUND_LIST).orElse(null);
         if (res == null) {
            return;
         }
         try (BufferedReader r = res.openAsReader()) {
            String line;
            while ((line = r.readLine()) != null) {
               line = line.trim();
               if (!line.isEmpty() && !line.startsWith("#")) {
                  ResourceLocation id = ResourceLocation.tryParse(line);
                  if (id != null) {
                     BuiltInRegistries.BLOCK.getOptional(id).ifPresent(logs::add);
                  }
               }
            }
         }
      } catch (Exception e) {
         return;
      }
      Map<ModelResourceLocation, BakedModel> models = event.getModels();
      // mossy cobblestone is a building block: keep its vanilla texture under the realistic pack
      for (BlockState state : net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE.getStateDefinition().getPossibleStates()) {
         var key = BlockModelShaper.stateToModelLocation(state);
         var model = models.get(key);
         if (model != null) models.put(key, new OriginalSurface(model));
      }
      // (the .50 full-block forest-litter overlay looked like blotches; .62 dresses bare ground under
      // a canopy with small loose pieces instead - see ForestFloorModel)
      forestFloor(event, models);
      // leaf sprays on branch tips use each leaves block's fringe texture
      java.util.Map<Block, TextureAtlasSprite> fringe = new IdentityHashMap<>();
      for (Block b : BuiltInRegistries.BLOCK) {
         if (!(b instanceof net.minecraft.world.level.block.LeavesBlock)) {
            continue;
         }
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         String[] names = {id.getNamespace() + ":block/" + id.getPath() + "_fringe", "frontierhunts:block/rw/" + id.getPath() + "_fringe",
            "frontierhunts:block/" + id.getPath() + "_fringe"};
         for (String n : names) {
            ResourceLocation nl = ResourceLocation.parse(n);
            if (Minecraft.getInstance().getResourceManager().getResource(
               ResourceLocation.fromNamespaceAndPath(nl.getNamespace(), "textures/" + nl.getPath() + ".png")).isEmpty()) {
               continue; // (asking the atlas for a missing sprite only logs a warning)
            }
            TextureAtlasSprite sp = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.parse(n)));
            if (sp != null && !sp.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation())) {
               fringe.put(b, sp);
               break;
            }
         }
      }
      TrunkModel.FRINGE = fringe;
      // leaves: one rounded canopy with sprays instead of cubes
      for (java.util.Map.Entry<Block, TextureAtlasSprite> e : fringe.entrySet()) {
         Block b = e.getKey();
         boolean conifer = LeafModel.isConifer(BuiltInRegistries.BLOCK.getKey(b).getPath());
         for (BlockState st : b.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(st);
            BakedModel m = models.get(mrl);
            if (m != null && !(m instanceof LeafModel)) {
               models.put(mrl, new LeafModel(m, m.getParticleIcon(), e.getValue(), conifer));
            }
         }
      }
      // Only natural forest litter drapes; placed carpets keep their normal shape.
      for (Block b : BuiltInRegistries.BLOCK) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         if (!id.toString().equals("frontierhunts:forest_litter")) {
            continue;
         }
         for (BlockState st : b.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(st);
            BakedModel m = models.get(mrl);
            if (m != null && !(m instanceof DrapeModel)) {
               models.put(mrl, new DrapeModel(m));
            }
         }
      }
      TreeGrowth.clear(); // trees grown with the old models are stale
      java.util.Map<Object, TextureAtlasSprite> barks = new IdentityHashMap<>();
      for (Block b : BuiltInRegistries.BLOCK) {
         // Baking also runs on the title screen, before a server has bound block
         // tags. Use the pack's explicit log set so leaf-owned limbs have bark on
         // the first world load, without needing a manual resource reload.
         if (logs.contains(b) || b.defaultBlockState().is(net.minecraft.tags.BlockTags.LOGS)) {
            BakedModel m = models.get(BlockModelShaper.stateToModelLocation(b.defaultBlockState()));
            if (m != null) {
               barks.put(b, m.getParticleIcon());
            }
         }
      }
      TrunkModel.BARK = barks;
      TrunkModel.GENERATION++;
      TreeGrowth.clear();
      TreeLod.clear();
      TextureAtlasSprite deadBark=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,
         ResourceLocation.parse("frontierhunts:block/deadfall_log")));
      TextureAtlasSprite deadEnd=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,
         ResourceLocation.parse("frontierhunts:block/deadfall_log_end")));
      for(String name:new String[]{"forest_sticks","fallen_branch"}) {
         Block debris=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("frontierhunts",name));
         for(BlockState state:debris.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key=BlockModelShaper.stateToModelLocation(state);
            BakedModel model=models.get(key);
            if(model!=null) models.put(key,new GroundWoodModel(model,state,name.equals("fallen_branch"),deadBark,deadEnd));
         }
      }
      Block stub = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("frontierhunts", "branch_stub"));
      for (BlockState state : stub.getStateDefinition().getPossibleStates()) {
         ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
         BakedModel model = models.get(key);
         if (model != null && !(model instanceof BranchStubModel)) models.put(key, new BranchStubModel(model));
      }
      for (Block b : logs) {
         ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
         TextureAtlasSprite end = event.getTextureGetter()
            .apply(new Material(TextureAtlas.LOCATION_BLOCKS, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "block/" + id.getPath() + "_top")));
         for (BlockState s : b.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(s);
            BakedModel m = models.get(mrl);
            if (m != null && !(m instanceof TrunkModel)) {
               models.put(mrl, new TrunkModel(m, m.getParticleIcon(), end, logs, TrunkModel.isConifer(id.getPath())));
            }
         }
      }
   }
   /** Natural ground blocks that may carry forest-floor dressing (fixed ids: tags are unbound on the title screen). */
   private static final String[] FLOOR_GROUND = {"minecraft:grass_block", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:dirt",
      "minecraft:rooted_dirt", "minecraft:moss_block", "frontierhunts:forest_duff"};

   private static void forestFloor(ModelEvent.ModifyBakingResult event, Map<ModelResourceLocation, BakedModel> models) {
      java.util.function.Function<String, TextureAtlasSprite> sprite = name -> {
         ResourceLocation id = ResourceLocation.parse(name);
         if (Minecraft.getInstance().getResourceManager().getResource(
            ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png")).isEmpty()) {
            return null; // (asking the atlas for a missing sprite only logs a warning)
         }
         TextureAtlasSprite found = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, id));
         return found == null || found.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()) ? null : found;
      };
      Block litter = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("frontierhunts", "forest_litter")).orElse(null);
      ForestFloorModel.Sprites sprites = new ForestFloorModel.Sprites(
         sprite.apply("frontierhunts:block/needle_litter"),
         new TextureAtlasSprite[]{sprite.apply("frontierhunts:block/forest_litter_0"), sprite.apply("frontierhunts:block/forest_litter_1"),
            sprite.apply("frontierhunts:block/forest_litter_2"), sprite.apply("frontierhunts:block/rw/forest_floor")},
         sprite.apply("frontierhunts:block/rw/moss_patch"),
         sprite.apply("frontierhunts:block/deadfall_log"),
         sprite.apply("frontierhunts:block/pine_cone"),
         sprite.apply("minecraft:block/brown_mushroom"),
         sprite.apply("minecraft:block/red_mushroom"),
         sprite.apply("frontierhunts:block/mossy_stone"),
         litter);
      if (sprites.needles() == null && sprites.leafLitter()[0] == null) {
         return; // nothing to dress with
      }
      for (String name : FLOOR_GROUND) {
         Block ground = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(name)).orElse(null);
         if (ground == null) continue;
         for (BlockState state : ground.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
            BakedModel model = models.get(key);
            if (model != null && !(model instanceof ForestFloorModel)) models.put(key, new ForestFloorModel(model, sprites));
         }
      }
   }

   private static final class OriginalSurface extends net.neoforged.neoforge.client.model.BakedModelWrapper<BakedModel> {
      OriginalSurface(BakedModel original) {
         super(original);
      }

      @Override
      public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState state, net.minecraft.core.Direction side,
         net.minecraft.util.RandomSource random, net.neoforged.neoforge.client.model.data.ModelData data, net.minecraft.client.renderer.RenderType type) {
         return com.formaworks.frontierhunts.client.terrain.BlendSpriteSource.originalQuads(this.originalModel.getQuads(state, side, random, data, type));
      }

      @Override
      public java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> getQuads(BlockState state, net.minecraft.core.Direction side,
         net.minecraft.util.RandomSource random) {
         return com.formaworks.frontierhunts.client.terrain.BlendSpriteSource.originalQuads(this.originalModel.getQuads(state, side, random));
      }
   }
}
