package com.formaworks.frontierstructures;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * [structures2] Natural ground heights straight from the chunk generator (pure functions of seed and position): one
 * alpine layout sample per column on Frontier terrain (getBaseColumn builds a whole 384-block column and samples the
 * slope - far too slow for site checks), the WG heightmaps elsewhere. Cached per instance.
 */
final class Heights implements TerrainFit.Natural {
    private final ChunkGenerator gen;
    private final LevelHeightAccessor height;
    private final RandomState random;
    private final Map<Long, int[]> cache = new HashMap<>();

    Heights(ChunkGenerator gen, LevelHeightAccessor height, RandomState random) {
        this.gen = gen;
        this.height = height;
        this.random = random;
    }

    private int[] sample(int x, int z) {
        return cache.computeIfAbsent(TerrainFit.key(x, z), k -> {
            int lo = height.getMinBuildHeight(), hi = height.getMaxBuildHeight();
            if (gen instanceof com.formaworks.frontierhunts.landscape.AlpineGenerator alpine) {
                var a = alpine.layout().sample(x, z);
                int f = Math.max(lo, Math.min(hi, a.floor() + 1));
                return new int[]{f, Math.max(lo, Math.min(hi, Math.max(a.floor(), a.water()) + 1)),
                    com.formaworks.frontierhunts.landscape.AlpineLayout.sea(a.district()) ? 1 : 0};
            }
            int floor = gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, random);
            int surface = gen.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, height, random);
            return new int[]{floor, Math.max(floor, surface), 0};
        });
    }

    /** first air y above solid ground (water does not count) */
    public int floor(int x, int z) {return sample(x, z)[0];}

    /** first air y above ground or water; greater than floor over water */
    public int surface(int x, int z) {return sample(x, z)[1];}

    /** [integ] beach / sea-shore district of the alpine layout */
    @Override public boolean shore(int x, int z) {return sample(x, z)[2] != 0;}
}
