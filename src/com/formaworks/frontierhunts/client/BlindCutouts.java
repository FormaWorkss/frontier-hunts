package com.formaworks.frontierhunts.client;

import com.formaworks.frontierhunts.expedition.BlindVolumes;
import com.formaworks.frontierhunts.expedition.HubGroundBlind;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Client half of the ground-blind rework: plants, leaves and trunks standing inside a deployed blind are hidden
 * (never removed). Every block model that could be such a block is wrapped in an outer {@link CutoutModel}. Its
 * {@code getModelData} marks positions inside a blind's volume ({@link BlindVolumes}) and {@code getQuads} /
 * {@code getRenderTypes} then return nothing, the blind frame piece for that cell, or the block plus the frame
 * piece (a trunk passing through the wall or roof). Both the vanilla section compiler and Sodium go through these
 * model-data aware calls. With no blind in the client level the wrapper is a single volatile read and a delegate.
 *
 * <p>The wrap has to sit outside the realistic preset's TrunkModel / LeafModel wrappers (client/tree/TreeModels),
 * which FrontierSettingsHooks installs at {@link EventPriority#LOWEST}. Listeners of one priority run in
 * registration order, so this class registers its LOWEST listener only once models start loading (from
 * {@link ModelEvent.RegisterAdditional}), i.e. after every annotation-registered one.
 */
@EventBusSubscriber(modid = "frontierhunts", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class BlindCutouts {
   static final ModelProperty<Cut> CUT = new ModelProperty<>();
   private static final int STRIDE = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
   private static volatile boolean listening;

   static {
      BlindVolumes.clientChanged = BlindCutouts::dirty;
   }

   private BlindCutouts() {
   }

   record Cut(int verdict, BlockState part, Vec3 offset) {
   }

   @SubscribeEvent
   public static void additional(ModelEvent.RegisterAdditional event) {
      BlindVolumes.clientChanged = BlindCutouts::dirty;
      if (!listening) {
         IEventBus bus = ModList.get().getModContainerById("frontierhunts").map(ModContainer::getEventBus).orElse(null);
         if (bus != null) {
            bus.addListener(EventPriority.LOWEST, ModelEvent.ModifyBakingResult.class, BlindCutouts::wrap);
            listening = true;
         }
      }
   }

   /** Fallback if the late registration above was not possible: same wrap, ordinary LOWEST listener. */
   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void fallback(ModelEvent.ModifyBakingResult event) {
      if (!listening) {
         wrap(event);
      }
   }

   static void wrap(ModelEvent.ModifyBakingResult event) {
      Map<ModelResourceLocation, BakedModel> models = event.getModels();

      for (Block block : BuiltInRegistries.BLOCK) {
         if (block instanceof HubGroundBlind) {
            // the blind's own parts host the frame pieces of neighbouring plant/leaf cells (see FrameHostModel)
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
               ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
               BakedModel model = models.get(key);
               if (model != null && !(model instanceof FrameHostModel)) {
                  models.put(key, new FrameHostModel(model));
               }
            }

            continue;
         }

         boolean named = named(BuiltInRegistries.BLOCK.getKey(block).getPath());

         for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            if (named || mayHide(state)) {
               ModelResourceLocation key = BlockModelShaper.stateToModelLocation(state);
               BakedModel model = models.get(key);
               if (model != null && !(model instanceof CutoutModel)) {
                  models.put(key, new CutoutModel(model));
               }
            }
         }
      }
   }

   private static boolean named(String path) {
      return path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae") || path.contains("leaves") || path.equals("log");
   }

   private static boolean mayHide(BlockState state) {
      try {
         Block b = state.getBlock();
         // tag-free on purpose: at the first bake no tags are bound yet (and kind() must not cache that)
         return b instanceof LeavesBlock || b instanceof RotatedPillarBlock || BlindVolumes.plantLike(state);
      } catch (RuntimeException e) {
         return false;
      }
   }

   /** Rebuild the sections around a volume (and its neighbours' faces) after it appeared, changed or went away. */
   static void dirty(BlindVolumes.Volume v) {
      Runnable r = () -> {
         LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
         if (renderer != null) {
            var level = Minecraft.getInstance().level;
            if (level == null) {
               renderer.setBlocksDirty(v.minX - 1, v.minY - 1, v.minZ - 1, v.maxX + 1, v.maxY + 1, v.maxZ + 1);
               return;
            }

            // "important" rebuilds (flag 8), like a block the player just placed: the blind and the plants it hides
            // change on the very next frame instead of waiting in the background build queue
            net.minecraft.core.BlockPos.MutableBlockPos p = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int sx = (v.minX - 1) >> 4; sx <= (v.maxX + 1) >> 4; sx++) {
               for (int sy = (v.minY - 1) >> 4; sy <= (v.maxY + 1) >> 4; sy++) {
                  for (int sz = (v.minZ - 1) >> 4; sz <= (v.maxZ + 1) >> 4; sz++) {
                     p.set((sx << 4) + 8, (sy << 4) + 8, (sz << 4) + 8);
                     var st = level.getBlockState(p);
                     renderer.blockChanged(level, p, st, st, 8);
                  }
               }
            }
         }
      };
      if (RenderSystem.isOnRenderThread()) {
         r.run();
      } else {
         Minecraft.getInstance().execute(r);
      }
   }

   /** The model under any CutoutModel layers (e.g. to test for the realistic preset's TrunkModel). */
   public static BakedModel unwrap(BakedModel model) {
      while (model instanceof CutoutModel c) {
         model = c.original();
      }

      return model;
   }

   /**
    * The real frame part of this volume next to a plant/leaf frame cell that draws that cell's frame piece, or null.
    * Deterministic (fixed direction order) so the plant cell and the host agree. Reads at most 2 blocks from the
    * section being built, inside both vanilla's and Sodium's render-region margins.
    */
   static BlockPos hostOf(BlockAndTintGetter level, BlindVolumes.Volume v, BlockPos cell) {
      for (Direction d : Direction.values()) {
         BlockPos p = cell.relative(d);
         if (v.contains(p.getX(), p.getY(), p.getZ())) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof HubGroundBlind && HubGroundBlind.base(p, s).equals(v.base)) {
               return p;
            }
         }
      }

      return null;
   }

   static Cut cut(BlockAndTintGetter level, BlockPos pos, BlockState state) {
      BlindVolumes.Volume v = BlindVolumes.clientVolumeAt(pos);
      if (v == null) {
         return null;
      }

      int verdict = BlindVolumes.verdict(v, pos, BlindVolumes.kind(state));
      if (verdict == BlindVolumes.KEEP) {
         return null;
      }

      if (verdict == BlindVolumes.SUBSTITUTE && hostOf(level, v, pos) != null) {
         // an adjacent real part draws this frame piece (so shaders see blind geometry, not a waving plant)
         return new Cut(BlindVolumes.HIDE, null, Vec3.ZERO);
      }

      int part = v.part(pos.getX(), pos.getY(), pos.getZ());
      BlockState partState = part < 0 ? null : v.partState(part);
      Vec3 offset = Vec3.ZERO;
      if (verdict == BlindVolumes.SUBSTITUTE && state.hasOffsetFunction()) {
         // grass and flowers are drawn with a random offset: undo it so the frame piece sits true
         offset = state.getOffset(level, pos);
      }

      return new Cut(verdict, partState, offset);
   }

   static BakedModel frame(Cut cut) {
      return Minecraft.getInstance().getBlockRenderer().getBlockModel(cut.part);
   }

   static List<BakedQuad> shifted(List<BakedQuad> quads, Vec3 offset) {
      if (quads.isEmpty() || offset == Vec3.ZERO || offset.lengthSqr() < 1.0E-12) {
         return quads;
      }

      float dx = (float)offset.x;
      float dy = (float)offset.y;
      float dz = (float)offset.z;
      List<BakedQuad> out = new ArrayList<>(quads.size());

      for (BakedQuad q : quads) {
         int[] v = q.getVertices().clone();

         for (int i = 0; i + 2 < v.length; i += STRIDE) {
            v[i] = Float.floatToRawIntBits(Float.intBitsToFloat(v[i]) - dx);
            v[i + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[i + 1]) - dy);
            v[i + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(v[i + 2]) - dz);
         }

         out.add(new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion()));
      }

      return out;
   }

   static List<BakedQuad> moved(List<BakedQuad> quads, int dx, int dy, int dz) {
      return shifted(quads, new Vec3(-dx, -dy, -dz));
   }

   /** A frame piece drawn by a neighbouring real part: the piece's part state and its offset from the host. */
   record Hosted(BlockState part, int dx, int dy, int dz) {
   }

   static final ModelProperty<List<Hosted>> HOSTED = new ModelProperty<>();

   /**
    * Wraps the blind's own part models. A part next to a plant or leaf frame cell (whose block is hidden) also draws
    * that cell's frame piece, one block over, so the piece carries the blind's block ID for shader packs.
    */
   static final class FrameHostModel extends BakedModelWrapper<BakedModel> {
      FrameHostModel(BakedModel original) {
         super(original);
      }

      @Override
      public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
         ModelData inner = this.originalModel.getModelData(level, pos, state, data);
         if (!BlindVolumes.clientActive() || level == null || pos == null || state == null || !(state.getBlock() instanceof HubGroundBlind)) {
            return inner;
         }

         try {
            BlindVolumes.Volume v = BlindVolumes.clientVolumeAt(pos);
            if (v == null || !HubGroundBlind.base(pos, state).equals(v.base)) {
               return inner;
            }

            List<Hosted> hosted = null;

            for (Direction d : Direction.values()) {
               BlockPos n = pos.relative(d);
               if (!v.contains(n.getX(), n.getY(), n.getZ())) {
                  continue;
               }

               int part = v.part(n.getX(), n.getY(), n.getZ());
               if (part < 0) {
                  continue;
               }

               byte k = BlindVolumes.kind(level.getBlockState(n));
               if ((k == BlindVolumes.PLANT || k == BlindVolumes.LEAF) && pos.equals(hostOf(level, v, n))) {
                  if (hosted == null) {
                     hosted = new ArrayList<>(2);
                  }

                  hosted.add(new Hosted(v.partState(part), d.getStepX(), d.getStepY(), d.getStepZ()));
               }
            }

            return hosted == null ? inner : (inner == null ? ModelData.builder() : inner.derive()).with(HOSTED, List.copyOf(hosted)).build();
         } catch (RuntimeException e) {
            return inner;
         }
      }

      @Override
      public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
         ChunkRenderTypeSet own = this.originalModel.getRenderTypes(state, rand, data);
         List<Hosted> hosted = data == null ? null : data.get(HOSTED);
         if (hosted == null) {
            return own;
         }

         List<ChunkRenderTypeSet> sets = new ArrayList<>(hosted.size() + 1);
         sets.add(own);

         for (Hosted h : hosted) {
            sets.add(model(h.part()).getRenderTypes(h.part(), rand, ModelData.EMPTY));
         }

         return ChunkRenderTypeSet.union(sets);
      }

      @Override
      public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
         List<Hosted> hosted = data == null ? null : data.get(HOSTED);
         if (hosted == null) {
            return this.originalModel.getQuads(state, side, rand, data, renderType);
         }

         List<BakedQuad> out = new ArrayList<>();
         if (renderType == null || this.originalModel.getRenderTypes(state, rand, data).contains(renderType)) {
            out.addAll(this.originalModel.getQuads(state, side, rand, data, renderType));
         }

         if (side == null) {
            // a moved piece is not on this block's faces, so all of its quads go in the unculled list
            for (Hosted h : hosted) {
               BakedModel m = model(h.part());
               if (renderType == null || m.getRenderTypes(h.part(), rand, ModelData.EMPTY).contains(renderType)) {
                  for (Direction d : SIDES) {
                     out.addAll(moved(m.getQuads(h.part(), d, rand, ModelData.EMPTY, renderType), h.dx(), h.dy(), h.dz()));
                  }
               }
            }
         }

         return out;
      }
   }

   private static final Direction[] SIDES = new Direction[]{null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

   static BakedModel model(BlockState state) {
      return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
   }

   /** Outer wrapper around plant / leaf / log models (after any other wrapping this mod does). */
   static final class CutoutModel extends BakedModelWrapper<BakedModel> {
      CutoutModel(BakedModel original) {
         super(original);
      }

      BakedModel original() {
         return this.originalModel;
      }

      @Override
      public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
         ModelData inner = this.originalModel.getModelData(level, pos, state, data);
         if (!BlindVolumes.clientActive() || pos == null || state == null) {
            return inner;
         }

         Cut cut;
         try {
            cut = BlindCutouts.cut(level, pos, state);
         } catch (RuntimeException e) {
            return inner;
         }

         return cut == null ? inner : (inner == null ? ModelData.builder() : inner.derive()).with(CUT, cut).build();
      }

      @Override
      public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
         Cut cut = data == null ? null : data.get(CUT);
         if (cut == null) {
            return this.originalModel.getRenderTypes(state, rand, data);
         }

         return switch (cut.verdict()) {
            case BlindVolumes.HIDE -> ChunkRenderTypeSet.none();
            case BlindVolumes.SUBSTITUTE -> frame(cut).getRenderTypes(cut.part(), rand, ModelData.EMPTY);
            default -> ChunkRenderTypeSet.union(
               this.originalModel.getRenderTypes(state, rand, data), frame(cut).getRenderTypes(cut.part(), rand, ModelData.EMPTY)
            );
         };
      }

      @Override
      public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
         Cut cut = data == null ? null : data.get(CUT);
         if (cut == null) {
            return this.originalModel.getQuads(state, side, rand, data, renderType);
         }

         switch (cut.verdict()) {
            case BlindVolumes.HIDE:
               return List.of();
            case BlindVolumes.SUBSTITUTE:
               return shifted(frame(cut).getQuads(cut.part(), side, rand, ModelData.EMPTY, renderType), cut.offset());
            default:
               BakedModel frame = frame(cut);
               List<BakedQuad> own = renderType == null || this.originalModel.getRenderTypes(state, rand, data).contains(renderType)
                  ? this.originalModel.getQuads(state, side, rand, data, renderType)
                  : List.of();
               List<BakedQuad> piece = renderType == null || frame.getRenderTypes(cut.part(), rand, ModelData.EMPTY).contains(renderType)
                  ? frame.getQuads(cut.part(), side, rand, ModelData.EMPTY, renderType)
                  : List.of();
               if (piece.isEmpty()) {
                  return own;
               } else if (own.isEmpty()) {
                  return piece;
               } else {
                  List<BakedQuad> both = new ArrayList<>(own.size() + piece.size());
                  both.addAll(own);
                  both.addAll(piece);
                  return both;
               }
         }
      }

      @Override
      public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
         Cut cut = data == null ? null : data.get(CUT);
         return cut != null && cut.verdict() == BlindVolumes.SUBSTITUTE
            ? frame(cut).useAmbientOcclusion(cut.part(), ModelData.EMPTY, renderType)
            : this.originalModel.useAmbientOcclusion(state, data, renderType);
      }
   }
}
