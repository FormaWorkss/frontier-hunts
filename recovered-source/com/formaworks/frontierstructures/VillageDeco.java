package com.formaworks.frontierstructures;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * [villages] Furnishing of a village: fire pit ringed with river stones and seats, flagpole by the main way in, the
 * Big-Buck Board and a Ranger Contract Board, stone well, cabin-lantern posts, trail benches and camp chairs, firewood
 * stacks with a chopping block, hitching rails with hay and a water trough, a few barrels, fenced kitchen gardens and
 * Trail Signs at the roads in. [gear20] Frontier Hunts' own furniture and signs instead of the bulky vanilla builds
 * (roofed board, log sign posts, stair benches, log benches); fewer, varied pieces so no two corners look the same.
 * Every block is written only if it lies in the chunk being generated.
 */
final class VillageDeco {
    private final WorldGenLevel level;
    private final BoundingBox box;
    private final VillagePlan plan;
    private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

    VillageDeco(WorldGenLevel level, BoundingBox box, VillagePlan plan) {this.level = level; this.box = box; this.plan = plan;}

    void set(int x, int y, int z, BlockState s) {
        if (!box.isInside(x, y, z)) return;
        level.setBlock(m.set(x, y, z), s, 2);
        // [gear20] multi-block pieces (Big-Buck Board) keep their block entity on one part only: drop the empty
        // placeholder world generation leaves on the others, or the chunk logs a warning every time it loads
        if (s.hasBlockEntity() && s.getBlock() instanceof EntityBlock eb && eb.newBlockEntity(m, s) == null) level.getChunk(m).removeBlockEntity(m);
    }

    /** level ground under a furnishing cell: clear brush above, top block, fill gaps beneath */
    void base(int x, int y, int z, BlockState top) {
        if (!box.isInside(x, y, z)) return;
        for (int yy = y; yy <= y + 3; yy++) {
            var s = level.getBlockState(m.set(x, yy, z));
            if (!s.isAir() && (VillageGround.flora(s) || TerrainFit.terrain(s))) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
        }
        if (top != null) level.setBlock(m.set(x, y - 1, z), top, 2);
        for (int yy = y - (top != null ? 2 : 1), n = 0; n < 6; yy--, n++) {
            var s = level.getBlockState(m.set(x, yy, z));
            if (s.isSolid() && !VillageGround.soft(s)) break;
            level.setBlock(m, n == 0 && top == null ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
        }
    }

    static Direction dir(int facing) {return Direction.from2DDataValue(facing);}

    double h(int x, int z, int salt) {return VillageGround.hash(plan.seed + salt, x, z);}

    BlockState stone(int x, int z) {
        double r = h(x, z, 31);
        return r < 0.45 ? Blocks.COBBLESTONE.defaultBlockState() : r < 0.75 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    BlockState fence(Block b, boolean n, boolean e, boolean s, boolean w) {
        return b.defaultBlockState().setValue(FenceBlock.NORTH, n).setValue(FenceBlock.EAST, e).setValue(FenceBlock.SOUTH, s).setValue(FenceBlock.WEST, w);
    }

    static net.minecraft.world.level.block.state.properties.BooleanProperty side(Direction d) {
        return switch (d) {case NORTH -> FenceBlock.NORTH; case SOUTH -> FenceBlock.SOUTH; case EAST -> FenceBlock.EAST; default -> FenceBlock.WEST;};
    }

    BlockState fenceAlong(Block b, Direction axisDir, boolean back, boolean fwd) {
        var s = b.defaultBlockState();
        if (back) s = s.setValue(side(axisDir.getOpposite()), true);
        if (fwd) s = s.setValue(side(axisDir), true);
        return s;
    }

    String key(String what) {return "frontierstructures.village." + plan.type + "." + what;}

    Component tr(String k, String fallback) {return Component.translatableWithFallback(k, fallback);}

    void sign(int x, int y, int z, BlockState state, Component... lines) {
        if (!box.isInside(x, y, z)) return;
        level.setBlock(m.set(x, y, z), state, 2);
        if (level.getBlockEntity(m) instanceof SignBlockEntity be) {
            // SignBlockEntity.setText marks the block for a client update (needs a Level): during world generation
            // load the text as saved data instead
            SignText t = new SignText();
            for (int i = 0; i < Math.min(4, lines.length); i++) t = t.setMessage(i, lines[i]);
            var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var text = SignText.DIRECT_CODEC.encodeStart(ops, t).result().orElse(null);
            if (text == null) return;
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.put("front_text", text);
            tag.put("back_text", text.copy());
            tag.putBoolean("is_waxed", true);
            be.loadCustomOnly(tag, level.registryAccess());
            be.setChanged();
        }
    }

    static final String[][] NAMES = {
        {"outpost", "Frontier", "Outpost"}, {"lakeside", "Reedbank", "Landing"}, {"crowsnest", "Crowsnest", "Ridge"}, {"junction", "Pine", "Junction"}};

    Component[] nameLines() {
        String a = "Frontier", b = "Outpost";
        for (String[] n : NAMES) if (n[0].equals(plan.type)) {a = n[1]; b = n[2];}
        return new Component[]{Component.empty(), tr(key("sign1"), a), tr(key("sign2"), b), Component.empty()};
    }

    void all() {
        for (var d : plan.decos) {
            int x = d.x, y = d.y, z = d.z;
            if (x < box.minX() - 6 || x > box.maxX() + 6 || z < box.minZ() - 6 || z > box.maxZ() + 6) continue;
            switch (d.kind) {
                case "firepit" -> firepit(x, y, z, d.facing);
                case "flagpole" -> {flagpole(x, y, z, d.arg); campPost(x, y, z, dir(d.facing));}
                case "notice_board" -> board(x, y, z, dir(d.facing));
                case "well" -> well(x, y, z);
                case "lamp" -> lamp(x, y, z, d.facing);
                case "bench" -> bench(x, y, z, dir(d.facing), d.arg);
                case "woodpile" -> woodpile(x, y, z, dir(d.facing), d.arg);
                case "hitch" -> hitch(x, y, z, dir(d.facing));
                case "barrels" -> barrels(x, y, z, dir(d.facing), d.arg);
                case "garden" -> garden(x, y, z, dir(d.facing), d.arg);
                case "signpost" -> signpost(x, y, z, dir(d.facing), d.arg);
                case "seat" -> seat(x, y, z, dir(d.facing), d.arg);
                default -> {}
            }
        }
    }

    // ------------------------------------------------------------------ Frontier Hunts furniture

    /** a Frontier Hunts block state ("trail_bench", "facing", "south", ...), or air when the block is missing */
    static BlockState fh(String id, String... kv) {
        Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("frontierhunts", id));
        BlockState s = b.defaultBlockState();
        for (int i = 0; i + 1 < kv.length; i += 2) s = with(s, kv[i], kv[i + 1]);
        return s;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState with(BlockState s, String name, String value) {
        Property p = s.getBlock().getStateDefinition().getProperty(name);
        if (p == null) return s;
        var v = p.getValue(value);
        return v.isPresent() ? s.setValue(p, (Comparable)v.get()) : s;
    }

    static String id(Direction d) {return d.getSerializedName();}

    /** a two-seat Trail Bench; the sitters look along {@code f}; (x, z) is the left seat seen from behind */
    void trailBench(int x, int y, int z, Direction f) {
        Direction l = f.getCounterClockWise();
        base(x, y, z, null);
        base(x + l.getStepX(), y, z + l.getStepZ(), null);
        set(x, y, z, fh("trail_bench", "facing", id(f), "left", "true", "right", "false"));
        set(x + l.getStepX(), y, z + l.getStepZ(), fh("trail_bench", "facing", id(f), "left", "false", "right", "true"));
    }

    /** a single seat: 0 camp chair, 1 stump seat, 2 lodge chair */
    void chair(int x, int y, int z, Direction f, int kind) {
        base(x, y, z, null);
        set(x, y, z, fh(kind == 0 ? "camp_chair" : kind == 1 ? "log_stump_seat" : "lodge_chair", "facing", id(f)));
    }

    // ------------------------------------------------------------------ pieces

    /** river-stone fire pit; seats on three sides, the side toward the main road ({@code open}) left open */
    void firepit(int x, int y, int z, int open) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int r2 = dx * dx + dz * dz;
            if (r2 > 5) continue;
            if (r2 == 0) {base(x, y, z, Blocks.GRAVEL.defaultBlockState()); set(x, y, z, Blocks.CAMPFIRE.defaultBlockState());}
            else if (r2 <= 2) {
                base(x + dx, y, z + dz, Blocks.COARSE_DIRT.defaultBlockState());
                boolean corner = dx != 0 && dz != 0;
                set(x + dx, y, z + dz, fh(h(x + dx, z + dz, 3) < 0.25 ? "mossy_river_stone" : "river_stone", "form",
                    String.valueOf(corner ? (int)(h(x + dx, z + dz, 5) * 2) : 1 + (int)(h(x + dx, z + dz, 5) * 3)), "waterlogged", "false"));
            }
            else base(x + dx, y, z + dz, h(x + dx, z + dz, 7) < 0.6 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.ROOTED_DIRT.defaultBlockState());
        }
        int k = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d.get2DDataValue() == open) continue;
            int sx = x + d.getStepX() * 3, sz = z + d.getStepZ() * 3;
            Direction look = d.getOpposite(), r = look.getClockWise();
            int kind = (int)(h(x, z, 61 + k) * 3);
            if (kind == 0) trailBench(sx + r.getStepX(), y, sz + r.getStepZ(), look);
            else if (kind == 1) {chair(sx + r.getStepX(), y, sz + r.getStepZ(), look, 0); chair(sx - r.getStepX(), y, sz - r.getStepZ(), look, 0);}
            else {chair(sx, y, sz, look, 1); chair(sx + r.getStepX() * 2, y, sz + r.getStepZ() * 2, look, 1);}
            k++;
        }
    }

    /** a slim flagpole on a stone footing */
    void flagpole(int x, int y, int z, int rot) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx == 0 || dz == 0) base(x + dx, y, z + dz, dx == 0 && dz == 0 ? Blocks.COBBLESTONE.defaultBlockState() : stone(x + dx, z + dz));
        set(x, y, z, Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
        for (int i = 1; i <= 6; i++) set(x, y + i, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        Block banner = switch (plan.type) {
            case "lakeside" -> Blocks.BLUE_BANNER;
            case "crowsnest" -> Blocks.RED_BANNER;
            case "junction" -> Blocks.YELLOW_BANNER;
            default -> Blocks.GREEN_BANNER;
        };
        set(x, y + 7, z, banner.defaultBlockState().setValue(BannerBlock.ROTATION, rot & 15));
    }

    /** [gear21] a Camp Post (the camp's flag and name plaque) beside the flagpole, facing the green */
    void campPost(int x, int y, int z, Direction f) {
        Direction s = f.getClockWise();
        int px = x + s.getStepX() * 2, pz = z + s.getStepZ() * 2;
        base(px, y, pz, Blocks.COARSE_DIRT.defaultBlockState());
        set(px, y, pz, fh("camp_post", "facing", id(f), "half", "lower"));
        set(px, y + 1, pz, fh("camp_post", "facing", id(f), "half", "upper"));
    }

    /** the Big-Buck Board and a Ranger Contract Board side by side, fronts toward {@code f} */
    void board(int x, int y, int z, Direction f) {
        Direction r = f.getClockWise();
        for (int i = -3; i <= 2; i++) base(x + r.getStepX() * i, y, z + r.getStepZ() * i, Blocks.COARSE_DIRT.defaultBlockState());
        // Big-Buck Board: three columns, two rows; part = row * 3 + column (column -1..1 along the clockwise side)
        for (int p = 0; p < 6; p++) {
            int col = p % 3 - 1 - 2, row = p / 3;
            set(x + r.getStepX() * col, y + row, z + r.getStepZ() * col, fh("big_buck_board", "facing", id(f), "part", String.valueOf(p)));
        }
        set(x + r.getStepX(), y, z + r.getStepZ(), fh("contract_board", "facing", id(f), "bench_part", "left"));
        set(x + r.getStepX() * 2, y, z + r.getStepZ() * 2, fh("contract_board", "facing", id(f), "bench_part", "right"));
    }

    void well(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int wx = x + dx, wz = z + dz;
            base(wx, y, wz, null);
            if (dx == 0 && dz == 0) {
                set(wx, y - 4, wz, Blocks.COBBLESTONE.defaultBlockState());
                for (int k = 1; k <= 3; k++) set(wx, y - k, wz, Blocks.WATER.defaultBlockState());
                continue;
            }
            double r = h(wx, wz, 41);
            BlockState st = r < 0.4 ? Blocks.STONE_BRICKS.defaultBlockState() : r < 0.7 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
            for (int k = 1; k <= 4; k++) set(wx, y - k, wz, Blocks.COBBLESTONE.defaultBlockState());
            // low stone curb (a wall block), posts on two corners only
            set(wx, y, wz, dx != 0 && dz != 0 ? st : Blocks.COBBLESTONE_WALL.defaultBlockState());
            if (dx == dz && dx != 0) {set(wx, y + 1, wz, Blocks.SPRUCE_FENCE.defaultBlockState()); set(wx, y + 2, wz, Blocks.SPRUCE_FENCE.defaultBlockState());}
        }
        // a crossbeam between the two posts with the bucket rope's lantern
        set(x - 1, y + 3, z - 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        set(x, y + 3, z, Blocks.SPRUCE_SLAB.defaultBlockState());
        set(x + 1, y + 3, z + 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        set(x, y + 2, z, Blocks.CHAIN.defaultBlockState());
    }

    /** a cabin lantern on a spruce post */
    void lamp(int x, int y, int z, int facing) {
        base(x, y, z, null);
        set(x, y, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(x, y + 1, z, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(x, y + 2, z, fh("cabin_lantern", "facing", id(dir(facing))));
    }

    /** seating by the green: a Trail Bench, or two camp chairs, or stump seats; sitters look along {@code f} */
    void bench(int x, int y, int z, Direction f, int v) {
        Direction l = f.getCounterClockWise();
        switch (Math.floorMod(v, 3)) {
            case 0 -> trailBench(x, y, z, f);
            case 1 -> {chair(x, y, z, f, 0); chair(x + l.getStepX(), y, z + l.getStepZ(), f, 0);}
            default -> {chair(x, y, z, f, 1); chair(x + l.getStepX() * 2, y, z + l.getStepZ() * 2, f, 1);}
        }
    }

    /** a porch corner by a building: a chair or two and a lantern on a stump; sitters look along {@code f} */
    void seat(int x, int y, int z, Direction f, int v) {
        Direction l = f.getCounterClockWise();
        chair(x, y, z, f, v == 2 ? 2 : 0);
        if (v != 1) chair(x + l.getStepX() * 2, y, z + l.getStepZ() * 2, f, v == 2 ? 2 : 0);
        base(x + l.getStepX(), y, z + l.getStepZ(), null);
        set(x + l.getStepX(), y, z + l.getStepZ(), fh("log_stump_seat", "facing", id(f)));
        set(x + l.getStepX(), y + 1, z + l.getStepZ(), fh("cabin_lantern", "facing", id(f)));
    }

    /** split firewood stacked along the wall, a chopping block beside */
    void woodpile(int x, int y, int z, Direction f, int v) {
        Direction face = f.getClockWise();
        int n = 2 + Math.floorMod(v, 2);
        for (int i = 0; i < n; i++) {
            int bx = x + f.getStepX() * (i - 1), bz = z + f.getStepZ() * (i - 1);
            base(bx, y, bz, null);
            set(bx, y, bz, fh("stacked_firewood", "facing", id(face)));
            if (i > 0 && i < n - 1 || v == 2 && i == 0) set(bx, y + 1, bz, fh("stacked_firewood", "facing", id(face)));
        }
        int cx = x + f.getStepX() * (n + 1), cz = z + f.getStepZ() * (n + 1);
        base(cx, y, cz, null);
        set(cx, y, cz, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
    }

    /** a hitching rail: two posts and a rail at chest height (horses tie to it), hay at one end, a trough at the other */
    void hitch(int x, int y, int z, Direction f) {
        for (int i = -2; i <= 3; i++) {
            int bx = x + f.getStepX() * i, bz = z + f.getStepZ() * i;
            base(bx, y, bz, null);
            if (i == -2) set(bx, y, bz, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
            else if (i == 3) set(bx, y, bz, Blocks.HAY_BLOCK.defaultBlockState());
            else {
                if (i == -1 || i == 2) set(bx, y, bz, Blocks.SPRUCE_FENCE.defaultBlockState());
                set(bx, y + 1, bz, fenceAlong(Blocks.SPRUCE_FENCE, f, i > -1, i < 2));
            }
        }
    }

    /** a couple of barrels (never a wall of them): side by side, one with a hay bale, or one with a composter */
    void barrels(int x, int y, int z, Direction f, int v) {
        Direction r = f.getClockWise();
        base(x, y, z, null);
        set(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
        int bx = x + r.getStepX(), bz = z + r.getStepZ();
        base(bx, y, bz, null);
        switch (Math.floorMod(v, 4)) {
            case 0 -> set(bx, y, bz, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, f));
            case 1 -> set(bx, y, bz, Blocks.HAY_BLOCK.defaultBlockState());
            case 2 -> set(bx, y, bz, Blocks.COMPOSTER.defaultBlockState());
            default -> set(bx, y, bz, fh("log_stump_seat", "facing", id(f)));
        }
    }

    /** fenced kitchen garden: 3 x 3 crops around a water hole, gate toward {@code f} */
    void garden(int x, int y, int z, Direction f, int v) {
        Block crop = v == 0 ? Blocks.CARROTS : v == 1 ? Blocks.POTATOES : Blocks.BEETROOTS;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int gx = x + dx, gz = z + dz;
            boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            if (ring) {
                base(gx, y, gz, null);
                if (dx == 2 * f.getStepX() && dz == 2 * f.getStepZ()) {set(gx, y, gz, Blocks.SPRUCE_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, f)); continue;}
                set(gx, y, gz, fence(Blocks.SPRUCE_FENCE, ring(dx, dz - 1), ring(dx + 1, dz), ring(dx, dz + 1), ring(dx - 1, dz)));
            } else if (dx == 0 && dz == 0) {
                base(gx, y, gz, Blocks.WATER.defaultBlockState());
                set(gx, y - 2, gz, Blocks.DIRT.defaultBlockState());
            } else {
                base(gx, y, gz, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                int age = crop == Blocks.BEETROOTS ? 1 + (int)(h(gx, gz, 51) * 3) : 3 + (int)(h(gx, gz, 51) * 5);
                BlockState c = crop.defaultBlockState();
                c = crop == Blocks.BEETROOTS ? c.setValue(BeetrootBlock.AGE, Math.min(3, age)) : c.setValue(CropBlock.AGE, Math.min(7, age));
                set(gx, y, gz, c);
            }
        }
    }

    static boolean ring(int dx, int dz) {return Math.abs(dx) <= 2 && Math.abs(dz) <= 2 && (Math.abs(dx) == 2 || Math.abs(dz) == 2);}

    /** a Trail Sign where a road comes in; the main road's ({@code main} = 1) also carries the village's name */
    void signpost(int x, int y, int z, Direction f, int main) {
        base(x, y, z, null);
        set(x, y, z, fh("trail_sign", "facing", id(f)));
        if (main == 1) {
            Direction r = f.getClockWise();
            int sx = x + r.getStepX(), sz = z + r.getStepZ();
            base(sx, y, sz, null);
            sign(sx, y, sz, Blocks.SPRUCE_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation(f)), nameLines());
        }
    }

    /** standing-sign rotation whose text faces {@code f} */
    static int rotation(Direction f) {
        return switch (f) {case SOUTH -> 0; case WEST -> 4; case NORTH -> 8; default -> 12;};
    }
}
