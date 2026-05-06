package com.grace.micromayhem.microbe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Derives which MicrobeStrains are present at a given location/entity
 * without storing per-block data.
 *
 * All calculations are deterministic from:
 *   world seed XOR block/entity position hash XOR strain ID
 *
 * Returns 1–3 strains for a given context.
 */
public class MicrobePresenceHelper {

    private static final int MAX_STRAINS_PER_SAMPLE = 3;

    /**
     * Strains present on a specific block.
     * Filtered by block material and biome to feel ecologically sensible.
     */
    public static List<MicrobeStrain> getStrainForBlock(ServerLevel level, BlockPos pos) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        long seed = level.getSeed() ^ posHash(pos);

        List<MicrobeStrain> result = new ArrayList<>();
        List<SpreadVector> validVectors = getVectorsForBlock(level, pos);

        List<MicrobeStrain> candidates = new ArrayList<>(registry.getAllStrains());
        for (int i = 0; i < Math.min(MAX_STRAINS_PER_SAMPLE, candidates.size()); i++) {
            long pick = seed ^ (i * 0x6C62272E07BB0142L);
            MicrobeStrain s = registry.getStrainForSeed(pick);
            // Only include if at least one of its vectors matches the block context
            for (SpreadVector v : s.spreadVectors) {
                if (validVectors.contains(v)) {
                    result.add(s);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Strains present on/in an entity (body flora).
     * Mobs and players each have a stable set derived from their entity UUID.
     */
    public static List<MicrobeStrain> getStrainForEntity(ServerLevel level, Entity entity) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        long seed = level.getSeed() ^ entity.getUUID().getMostSignificantBits()
                    ^ entity.getUUID().getLeastSignificantBits();

        List<MicrobeStrain> result = new ArrayList<>();
        List<MicrobeStrain> candidates = new ArrayList<>(registry.getAllStrains());

        for (int i = 0; i < Math.min(MAX_STRAINS_PER_SAMPLE, candidates.size()); i++) {
            long pick = seed ^ (i * 0x9E3779B97F4A7C15L);
            MicrobeStrain s = registry.getStrainForSeed(pick);
            result.add(s);
        }
        return result;
    }

    /**
     * Airborne strains for a given position — biome-weighted.
     */
    public static List<MicrobeStrain> getAirborneStrains(ServerLevel level, BlockPos pos) {
        MicrobeRegistry registry = MicrobeRegistry.get(level);
        long seed = level.getSeed() ^ posHash(pos) ^ 0xDEADBEEFCAFEL;

        List<MicrobeStrain> result = new ArrayList<>();
        List<MicrobeStrain> candidates = new ArrayList<>(registry.getAllStrains());

        for (int i = 0; i < 2; i++) {
            long pick = seed ^ (i * 0xB7E151628AED2A6BL);
            MicrobeStrain s = registry.getStrainForSeed(pick);
            if (s.spreadVectors.contains(SpreadVector.AIRBORNE) ||
                s.spreadVectors.contains(SpreadVector.AEROSOL)) {
                result.add(s);
            }
        }
        return result;
    }

    // ---- Helpers ----

    /** Map a block + biome context to which spread vectors are applicable */
    private static List<SpreadVector> getVectorsForBlock(ServerLevel level, BlockPos pos) {
        List<SpreadVector> vectors = new ArrayList<>();
        BlockState state = level.getBlockState(pos);

        if (state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) ||
            state.is(Blocks.GRAVEL) || state.is(Blocks.SAND) ||
            state.is(Blocks.CLAY) || state.is(Blocks.COARSE_DIRT) ||
            state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM)) {
            vectors.add(SpreadVector.SOIL);
        }
        if (state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE) ||
            state.is(Blocks.DEEPSLATE) || state.is(Blocks.TUFF) ||
            state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE) ||
            state.is(Blocks.ANDESITE)) {
            vectors.add(SpreadVector.STONE);
        }
        if (state.is(Blocks.OAK_LOG) || state.is(Blocks.BIRCH_LOG) ||
            state.is(Blocks.OAK_LEAVES) || state.is(Blocks.JUNGLE_LOG) ||
            state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.SPRUCE_LOG) ||
            state.is(Blocks.MANGROVE_LOG) || state.is(Blocks.MUSHROOM_STEM)) {
            vectors.add(SpreadVector.WOOD);
        }
        if (state.is(Blocks.WATER) || state.is(Blocks.BUBBLE_COLUMN)) {
            vectors.add(SpreadVector.WATER);
        }
        // Air blocks in any biome get airborne
        if (state.is(Blocks.AIR) || state.is(Blocks.CAVE_AIR)) {
            vectors.add(SpreadVector.AIRBORNE);
        }

        return vectors;
    }

    private static long posHash(BlockPos pos) {
        long x = pos.getX();
        long y = pos.getY();
        long z = pos.getZ();
        return x * 0x9E3779B97F4A7C15L ^ y * 0x6C62272E07BB0142L ^ z * 0xB7E151628AED2A6BL;
    }
}
