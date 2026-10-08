package com.formaworks.frontierstructures;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/**
 * [villages] A planned hunting village made of Austin's authored buildings ({@link VillagePlan}). Pieces, in the order
 * every chunk runs them: grounds (terrain grading for the whole village), one template piece per building, furnishing
 * (road surfaces, natural edge, fire pit, lamps ...).
 * <p>One structure per layout (village_outpost, village_lakeside, village_crowsnest, village_junction), all in the
 * {@code frontierstructures:villages} set. Which layout a placement cell gets is decided ONCE per cell by the land
 * ({@link #choose}: a shore gets the fishing landing, a hill the Crowsnest, a wide flat Pine Junction, else an
 * outpost); each layout structure only succeeds where it is that choice. So world generation builds exactly one
 * village per cell and /locate of a layout answers where that layout really stands. The choice is cached per cell
 * (the set tries the layouts one after another, /locate asks again), and the site check is cheap - a biome sample and a
 * few dozen natural-height samples - with a time budget on the server thread so /locate can never trip the watchdog.
 */
public final class Village extends Structure {
    public static final MapCodec<Village> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(settingsCodec(i),
        Codec.STRING.fieldOf("layout").forGetter(s -> s.layout)).apply(i, Village::new));
    final String layout;
    
    public Village(StructureSettings settings, String layout) {super(settings); this.layout = layout;}

    @Override public StructureType<?> type() {return FrontierStructures.VILLAGE.get();}

    // ------------------------------------------------------------------ the per-cell choice

    private static final Optional<VillagePlan> NONE = Optional.empty();
    private static final Map<Long, Optional<VillagePlan>> CHOICES = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Optional<VillagePlan>> e) {return size() > 256;}
    };

    /** the village this placement cell gets (cached), or empty */
    static Optional<VillagePlan> choose(GenerationContext c) {
        long key = c.seed() * 0x9E3779B97F4A7C15L ^ c.chunkPos().toLong();
        synchronized (CHOICES) {
            var hit = CHOICES.get(key);
            if (hit != null) return hit;
        }
        if (Budget.exhausted()) return NONE; // not cached: a later, unhurried look decides this cell
        Optional<VillagePlan> r;
        try {
            var h = new Heights(c.chunkGenerator(), c.heightAccessor(), c.randomState());
            VillagePlan p = VillagePlan.chooseIn(VillagePlan.templates(c.structureTemplateManager()), h, c.random(), c.chunkPos().getMiddleBlockX(), c.chunkPos().getMiddleBlockZ(),
                c.chunkGenerator().getSeaLevel(), c.heightAccessor().getMinBuildHeight(), c.heightAccessor().getMaxBuildHeight(),
                (x, y, z) -> c.validBiome().test(c.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), c.randomState().sampler())));
            r = Optional.ofNullable(p);
            if (p != null) org.slf4j.LoggerFactory.getLogger("frontierstructures").debug("[Frontier Structures] village {} planned around {} {} {} (box {} {} .. {} {}, {} buildings)",
                p.type, p.cx, p.greenY, p.cz, p.minX, p.minZ, p.maxX, p.maxZ, p.lots.size());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger("frontierstructures").error("[Frontier Structures] village planning failed in chunk {}", c.chunkPos(), ex);
            r = NONE;
        }
        synchronized (CHOICES) {CHOICES.put(key, r);}
        Budget.done();
        return r;
    }

    /**
     * time budget for site checks on the server thread (/locate, /place): about 20 s per command. [gear.18] Was 35 s;
     * with villages more common there are more cells to check, and one cold check (falls planning) can take many
     * seconds on its own, so 35 s could run into the server's 60 s watchdog on a /locate of one village type.
     */
    static final class Budget {
        private static long burstStart, last;
        static synchronized boolean exhausted() {
            Thread t = Thread.currentThread();
            if (!t.getName().equals("Server thread")) return false;
            long now = System.currentTimeMillis();
            if (now - last > 1500) burstStart = now; // a new command
            last = now;
            return now - burstStart > 20000;
        }
        /** [gear.18] marks the end of a check, so one slow check is not mistaken for the gap before a new command */
        static synchronized void done() {
            if (Thread.currentThread().getName().equals("Server thread")) last = System.currentTimeMillis();
        }
    }

    @Override public Optional<GenerationStub> findGenerationPoint(GenerationContext c) {
        var choice = choose(c);
        if (choice.isEmpty() || !choice.get().type.equals(layout)) return Optional.empty();
        final VillagePlan plan = choice.get();
        // [gear.19] never half a village next to land generated on an older version (see OldLand)
        if (OldLand.touches(plan.minX, plan.minZ, plan.maxX, plan.maxZ, 12)) return Optional.empty();
        var tm = c.structureTemplateManager();
        return Optional.of(new GenerationStub(new BlockPos(plan.cx, plan.greenY, plan.cz), b -> addPieces(b, plan, tm)));
    }

    static void addPieces(StructurePiecesBuilder b, VillagePlan plan, StructureTemplateManager tm) {
        CompoundTag tag = plan.save();
        b.addPiece(new Grounds(tag, 0, plan));
        for (var l : plan.lots) b.addPiece(new Building(tm, l, plan.grade, plan.seed));
        b.addPiece(new Grounds(tag, 1, plan));
    }

    /** grading (phase 0) or furnishing (phase 1) of the whole village */
    public static final class Grounds extends StructurePiece {
        private final CompoundTag planTag;
        private final int phase;
        private VillagePlan plan;
        private VillageGround ground;

        Grounds(CompoundTag tag, int phase, VillagePlan plan) {
            super(FrontierStructures.VILLAGE_GROUNDS.get(), phase, new BoundingBox(plan.minX, plan.minY - 24, plan.minZ, plan.maxX, plan.maxY + 40, plan.maxZ));
            this.planTag = tag; this.phase = phase; this.plan = plan;
            VillageTrees.register(this.boundingBox, tag); // [1.1.0] trees keep clear of it from now on
        }

        public Grounds(CompoundTag t) {
            super(FrontierStructures.VILLAGE_GROUNDS.get(), t);
            this.planTag = t.getCompound("Plan"); this.phase = t.getInt("Phase");
            VillageTrees.register(this.boundingBox, this.planTag); // [1.1.0]
        }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c, CompoundTag t) {t.put("Plan", planTag); t.putInt("Phase", phase);}

        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
            try {
                if (plan == null) plan = VillagePlan.load(planTag);
                if (ground == null) ground = new VillageGround(plan, VillagePlan.templates(level.getLevel().getStructureManager()));
                var nat = new Heights(generator, level, level.getLevel().getChunkSource().randomState());
                if (phase == 0) ground.grade(level, box, nat);
                else {
                    ground.surface(level, box, nat);
                    new VillageDeco(level, box, plan).all();
                }
            } catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger("frontierstructures").error("[Frontier Structures] village grounds (phase {}) failed in chunk {}", phase, chunk, ex);
            }
        }
    }

    /** one of Austin's buildings, placed as authored (rotation only) on the yard the grounds piece prepared */
    public static final class Building extends TemplateStructurePiece {
        private final java.util.List<BlockPos> modded = new java.util.ArrayList<>();
        /** [1.1.3] the template's path stub, laid as one layer of trail instead of a pasted 2-block slab (grade 2) */
        private final java.util.List<BlockPos> stub = new java.util.ArrayList<>();
        private final int grade;
        private final long seed;
        private boolean scanned;

        Building(StructureTemplateManager m, VillagePlan.Lot l, int grade, long seed) {
            super(FrontierStructures.VILLAGE_BUILDING.get(), 0, m, l.template, l.template.toString(), settings(l.rotation), new BlockPos(l.ox, l.originY(), l.oz));
            this.grade = grade;
            this.seed = seed;
        }

        public Building(StructureTemplateManager m, CompoundTag t) {
            super(FrontierStructures.VILLAGE_BUILDING.get(), t, m, id -> settings(Rotation.valueOf(t.getString("Rotation"))));
            this.grade = t.contains("Grade") ? t.getInt("Grade") : 1;
            this.seed = t.getLong("Seed");
        }

        static StructurePlaceSettings settings(Rotation r) {
            return new StructurePlaceSettings().setRotation(r).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK).setLiquidSettings(LiquidSettings.IGNORE_WATERLOGGING);
        }

        @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c, CompoundTag t) {
            super.addAdditionalSaveData(c, t);
            t.putString("Rotation", placeSettings.getRotation().name());
            t.putInt("Grade", grade);
            t.putLong("Seed", seed);
        }

        static boolean stubBlock(String name) {
            return name.equals("minecraft:gravel") || name.equals("minecraft:coarse_dirt") || name.equals("minecraft:dirt_path") || name.equals("minecraft:dirt")
                || name.equals("minecraft:rooted_dirt") || name.equals("minecraft:podzol");
        }

        @Override protected void handleDataMarker(String s, BlockPos p, ServerLevelAccessor l, RandomSource r, BoundingBox b) {}

        @Override public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pos) {
            super.postProcess(level, structures, generator, random, box, chunk, pos);
            // multi-part modded blocks: drop block-entity placeholders of parts that have none (see TerrainFit)
            if (!scanned) {
                scanned = true;
                var data = template.save(new CompoundTag());
                var palette = data.getList("palette", 10);
                // [1.1.3] stub columns: only trail materials at the template's ground layers (y 0-1), nothing built above
                java.util.Map<Long, Boolean> stubCol = new java.util.HashMap<>();
                for (var e : data.getList("blocks", 10)) {
                    var b = (CompoundTag)e;
                    String name = palette.getCompound(b.getInt("state")).getString("Name");
                    var p = b.getList("pos", 3);
                    if (name.equals("minecraft:air") || name.equals("minecraft:structure_void") || name.equals("frontierstructures:structure_space")) continue;
                    long key = TerrainFit.key(p.getInt(0), p.getInt(2));
                    boolean ok = p.getInt(1) <= 1 ? stubBlock(name) : name.equals("minecraft:short_grass") || name.equals("minecraft:fern");
                    stubCol.merge(key, ok, (a, c) -> a && c);
                    if (!name.startsWith("frontierhunts:")) continue;
                    modded.add(templatePosition.offset(StructureTemplate.calculateRelativePosition(placeSettings, new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)))).immutable());
                }
                if (grade >= 2) {
                    for (var e : data.getList("blocks", 10)) {
                        var p = ((CompoundTag)e).getList("pos", 3);
                        if (p.getInt(1) > 1 || !stubCol.getOrDefault(TerrainFit.key(p.getInt(0), p.getInt(2)), false)) continue;
                        stub.add(templatePosition.offset(StructureTemplate.calculateRelativePosition(placeSettings, new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2)))).immutable());
                    }
                }
            }
            // [1.1.3] the path stub: earth underneath, one layer of trail on top, in the same blend as the village roads
            int topY = templatePosition.getY() + 1;
            for (BlockPos p : stub) {
                if (!box.isInside(p)) continue;
                net.minecraft.world.level.block.state.BlockState s;
                if (p.getY() < topY) s = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
                else {
                    double n1 = VillageGround.noise(seed + 9, p.getX(), p.getZ(), 7) * 0.7 + VillageGround.noise(seed + 19, p.getX(), p.getZ(), 3) * 0.3;
                    s = (n1 < -0.42 ? net.minecraft.world.level.block.Blocks.GRAVEL : n1 > 0.48 ? net.minecraft.world.level.block.Blocks.COARSE_DIRT
                        : net.minecraft.world.level.block.Blocks.DIRT_PATH).defaultBlockState();
                }
                level.setBlock(p, s, 2);
            }
            for (BlockPos p : modded) {
                if (!box.isInside(p)) continue;
                var s = level.getBlockState(p);
                if (s.hasBlockEntity() && s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb && eb.newBlockEntity(p, s) == null) level.getChunk(p).removeBlockEntity(p);
            }
        }
    }
}
