package com.formaworks.frontierhunts.ecology.bones;

import com.formaworks.frontierhunts.ecology.EcologyContent;
import com.formaworks.frontierhunts.ecology.Prey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * [ecology] Lays out a bone site: a skull (or a shed antler) in the middle and a few ribs / vertebrae / leg bones
 * scattered a block or three around it, each on natural ground (soil, sand, snow, forest floor) with air or a short
 * plant above and no water. Every search is a short, bounded column scan.
 */
public final class BoneSites {
   /** What lies at a site. */
   public enum Kind {
      WHITETAIL(BoneSpecies.WHITETAIL), ELK(BoneSpecies.ELK), MOOSE(BoneSpecies.MOOSE), BISON(BoneSpecies.BISON), PRONGHORN(null), SHED(null),
      /** a medium animal without a skull block of its own (boar): ribs and legs only */
      REMAINS(null);

      public final BoneSpecies skull;

      Kind(BoneSpecies skull) {
         this.skull = skull;
      }

      public static Kind of(Prey.Kind k) {
         return switch (k) {
            case WHITETAIL -> WHITETAIL;
            case ELK -> ELK;
            case MOOSE -> MOOSE;
            case BISON -> BISON;
            case PRONGHORN -> PRONGHORN;
            default -> REMAINS;
         };
      }

      public static Kind byName(String s) {
         for (Kind k : values()) {
            if (k.name().equalsIgnoreCase(s)) {
               return k;
            }
         }
         return null;
      }
   }

   private static final TagKey<Biome> WHITETAIL_HABITAT = biomeTag("whitetail_habitat");
   private static final TagKey<Biome> ELK_HABITAT = biomeTag("elk_habitat");
   private static final TagKey<Biome> MOOSE_HABITAT = biomeTag("moose_habitat");
   private static final TagKey<Biome> BISON_RANGE = biomeTag("wildlife2026/bison");
   private static final TagKey<Biome> PRONGHORN_RANGE = biomeTag("wildlife2026/pronghorn");

   private BoneSites() {
   }

   private static TagKey<Biome> biomeTag(String path) {
      return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("frontierhunts", path));
   }

   /** Picks what died here from the biome: deer in woods, elk/moose in the north, bison and pronghorn on plains and badlands. */
   public static Kind pick(Holder<Biome> b, RandomSource r) {
      float wt = b.is(WHITETAIL_HABITAT) ? 5.0F : 0.0F;
      float elk = b.is(ELK_HABITAT) ? 3.0F : 0.0F;
      float moose = b.is(MOOSE_HABITAT) ? 3.0F : 0.0F;
      boolean badlands = b.is(net.neoforged.neoforge.common.Tags.Biomes.IS_BADLANDS);
      float bison = b.is(BISON_RANGE) || badlands ? 5.0F : 0.0F;
      float pronghorn = b.is(PRONGHORN_RANGE) ? 3.0F : 0.0F;
      float shed = wt > 0.0F && (b.is(net.minecraft.tags.BiomeTags.IS_FOREST) || b.is(net.minecraft.tags.BiomeTags.IS_TAIGA)) ? 2.5F : 0.0F;
      if (b.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SNOWY_PLAINS)) {
         moose += 2.0F;
         elk += 2.0F;
         bison += 1.0F;
      }
      if (badlands) {
         wt = 0.0F;
         shed = 0.0F;
      }
      float total = wt + elk + moose + bison + pronghorn + shed;
      if (total <= 0.0F) {
         return Kind.WHITETAIL;
      }
      float x = r.nextFloat() * total;
      if ((x -= wt) < 0.0F) return Kind.WHITETAIL;
      if ((x -= elk) < 0.0F) return Kind.ELK;
      if ((x -= moose) < 0.0F) return Kind.MOOSE;
      if ((x -= bison) < 0.0F) return Kind.BISON;
      if ((x -= pronghorn) < 0.0F) return Kind.PRONGHORN;
      return Kind.SHED;
   }

   /** Natural ground a carcass would lie on and bones sink into. */
   public static boolean ground(BlockState s) {
      if (s.is(BlockTags.DIRT)) {
         return !s.is(Blocks.MUD) && !s.is(Blocks.MUDDY_MANGROVE_ROOTS);
      }
      if (s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL)) {
         return true;
      }
      ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
      return id.getNamespace().equals("frontierhunts") && (id.getPath().contains("duff") || id.getPath().contains("litter"));
   }

   /** Air, or a short plant / thin snow that a bone may replace; never a fluid, never half a tall plant. */
   public static boolean clear(LevelAccessor level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      if (!level.getFluidState(p).isEmpty()) {
         return false;
      }
      if (s.isAir()) {
         return true;
      }
      if (s.getBlock() instanceof DoublePlantBlock || s.getBlock() instanceof BoneBlock) {
         return false;
      }
      if (s.is(Blocks.SNOW)) {
         return s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS) <= 2;
      }
      return s.canBeReplaced() && s.getCollisionShape(level, p).isEmpty();
   }

   /** Ground-level spot in this column near {@code y}: a clear block over natural ground; null if none within reach. */
   public static BlockPos spot(LevelAccessor level, int x, int y, int z) {
      BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
      for (int dy = 3; dy >= -4; dy--) {
         m.set(x, y + dy, z);
         if (!clear(level, m)) {
            continue;
         }
         BlockPos below = m.below();
         BlockState g = level.getBlockState(below);
         if (ground(g) && level.getFluidState(below).isEmpty() && g.isFaceSturdy(level, below, Direction.UP)
            && level.getFluidState(m.above()).isEmpty()) {
            return m.immutable();
         }
      }
      return null;
   }

   private static Direction facing(RandomSource r) {
      return Direction.Plane.HORIZONTAL.getRandomDirection(r);
   }

   /**
    * Places a site centred near {@code origin}. Returns the number of bone blocks placed (0 = nothing fit here).
    * {@code natural}: worldgen (mossy, weathered, permanent) vs. the remains of a recent kill (fresh, crumble away).
    */
   public static int place(LevelAccessor level, BlockPos origin, Kind kind, boolean natural, boolean antlers, RandomSource r, int flags) {
      BlockPos center = spot(level, origin.getX(), origin.getY(), origin.getZ());
      if (center == null) {
         return 0;
      }
      boolean mossy = natural && r.nextFloat() < 0.55F;
      int placed = 0;
      if (kind == Kind.SHED) {
         placed += set(level, center, EcologyContent.SHED_ANTLER.get().defaultBlockState()
            .setValue(BoneBlock.FACING, facing(r)).setValue(BoneBlock.MOSSY, mossy).setValue(BoneBlock.NATURAL, true), flags);
         if (r.nextFloat() < 0.3F) {
            // the matching side, a few steps away
            BlockPos p = near(level, center, 2, 4, r);
            if (p != null) {
               placed += set(level, p, EcologyContent.SHED_ANTLER.get().defaultBlockState()
                  .setValue(BoneBlock.FACING, facing(r)).setValue(BoneBlock.MOSSY, mossy).setValue(BoneBlock.NATURAL, true), flags);
            }
         }
         return placed;
      }
      if (kind.skull != null) {
         SkullBlock skull = EcologyContent.skull(kind.skull);
         placed += set(level, center, skull.defaultBlockState()
            .setValue(BoneBlock.FACING, facing(r)).setValue(BoneBlock.MOSSY, mossy).setValue(BoneBlock.NATURAL, natural)
            .setValue(SkullBlock.ANTLERS, kind == Kind.BISON || antlers), flags);
      }
      ScatteredBonesBlock.Part[] parts = ScatteredBonesBlock.Part.values();
      int pieces = natural ? 2 + r.nextInt(3) : 2 + (r.nextFloat() < 0.5F ? 1 : 0);
      boolean ribs = false;
      for (int i = 0; i < pieces; i++) {
         // the rib cage always; then legs and vertebrae
         ScatteredBonesBlock.Part part = !ribs ? ScatteredBonesBlock.Part.RIBS : parts[1 + r.nextInt(2)];
         BlockPos p = kind.skull == null && i == 0 ? center : near(level, center, 1, 3, r);
         if (p == null) {
            continue;
         }
         int n = set(level, p, EcologyContent.SCATTERED_BONES.get().defaultBlockState()
            .setValue(BoneBlock.FACING, facing(r)).setValue(BoneBlock.MOSSY, mossy && r.nextFloat() < 0.8F).setValue(BoneBlock.NATURAL, natural)
            .setValue(ScatteredBonesBlock.PART, part), flags);
         if (n > 0 && part == ScatteredBonesBlock.Part.RIBS) {
            ribs = true;
         }
         placed += n;
      }
      return placed;
   }

   /** a free spot 1..3 blocks from {@code c} (a few tries, bounded) */
   private static BlockPos near(LevelAccessor level, BlockPos c, int min, int max, RandomSource r) {
      for (int t = 0; t < 6; t++) {
         float a = r.nextFloat() * Mth.TWO_PI;
         float d = min + r.nextFloat() * (max - min);
         int x = c.getX() + Math.round(Mth.cos(a) * d);
         int z = c.getZ() + Math.round(Mth.sin(a) * d);
         if (x == c.getX() && z == c.getZ()) {
            continue;
         }
         BlockPos p = spot(level, x, c.getY(), z);
         if (p != null) {
            return p;
         }
      }
      return null;
   }

   private static int set(LevelAccessor level, BlockPos p, BlockState s, int flags) {
      if (!clear(level, p) || !s.canSurvive(level, p)) {
         return 0;
      }
      return level.setBlock(p, s, flags) ? 1 : 0;
   }
}
