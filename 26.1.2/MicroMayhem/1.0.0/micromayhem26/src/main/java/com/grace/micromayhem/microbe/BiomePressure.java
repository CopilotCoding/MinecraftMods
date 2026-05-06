package com.grace.micromayhem.microbe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.resources.Identifier;

/**
 * Environmental pressure types derived from biome.
 * Affects colony growth rates and which strains are present in samples.
 */
public enum BiomePressure {
    HEAT("Heat",
        "Selects heat-resistant strains. Suppresses moisture-dependent fungi.",
        new float[]{1.3f, 0.5f, 1.1f, 1.0f}),  // bacteria, fungus, virus, parasite growth mods

    MOISTURE("Moisture",
        "Fungal growth +50%. High bacterial diversity.",
        new float[]{1.2f, 1.5f, 0.9f, 1.2f}),

    HUMIDITY("Humidity+Density",
        "All transmissibility +20%. Parasite prevalence high.",
        new float[]{1.1f, 1.3f, 1.0f, 1.5f}),

    EXTREME_HEAT("Extreme Heat",
        "Only extremophile strains survive. Everything else suppressed.",
        new float[]{0.2f, 0.1f, 0.3f, 0.1f}),

    COLD("Cold",
        "Slow mutation rate. Viral strains dominant.",
        new float[]{0.7f, 0.5f, 1.4f, 0.8f}),

    NEUTRAL("Neutral",
        "Baseline conditions. All strain types present.",
        new float[]{1.0f, 1.0f, 1.0f, 1.0f}),

    AQUATIC("Aquatic",
        "Waterborne vectors dominant. Contact transmission reduced.",
        new float[]{1.1f, 0.8f, 1.0f, 1.2f});

    public final String displayName;
    public final String description;
    /** Growth rate multipliers indexed by MicrobeType ordinal */
    public final float[] typeGrowthMods;

    BiomePressure(String displayName, String description, float[] typeGrowthMods) {
        this.displayName = displayName;
        this.description = description;
        this.typeGrowthMods = typeGrowthMods;
    }

    public float getGrowthMod(MicrobeType type) {
        return typeGrowthMods[type.ordinal()];
    }

    /** Derive pressure from biome at a position. */
    public static BiomePressure forPosition(ServerLevel level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);
        Identifier biomeKey = level.registryAccess()
            .lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .getKey(biomeHolder.value());

        if (biomeKey == null) return NEUTRAL;
        String path = biomeKey.getPath();

        // Nether biomes
        if (biomeKey.getNamespace().equals("minecraft") &&
            (path.contains("nether") || path.contains("basalt") || path.contains("crimson") || path.contains("warped"))) {
            return EXTREME_HEAT;
        }
        // Hot/arid
        if (path.contains("desert") || path.contains("badlands") || path.contains("savanna") || path.contains("mesa")) {
            return HEAT;
        }
        // Wet/swampy
        if (path.contains("swamp") || path.contains("mangrove") || path.contains("bog")) {
            return MOISTURE;
        }
        // Jungle/dense
        if (path.contains("jungle") || path.contains("bamboo") || path.contains("mushroom")) {
            return HUMIDITY;
        }
        // Cold/frozen
        if (path.contains("frozen") || path.contains("snowy") || path.contains("ice") ||
            path.contains("tundra") || path.contains("peaks")) {
            return COLD;
        }
        // Ocean/aquatic
        if (path.contains("ocean") || path.contains("river") || path.contains("beach") ||
            path.contains("deep")) {
            return AQUATIC;
        }

        return NEUTRAL;
    }
}
