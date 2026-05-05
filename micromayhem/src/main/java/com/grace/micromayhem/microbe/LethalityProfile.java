package com.grace.micromayhem.microbe;

/**
 * How lethal a strain is capable of being.
 *
 * Distribution at world generation:
 *   NON_LETHAL:   ~70% — causes damage but body stabilises
 *   CONDITIONAL:  ~20% — lethal only if host is compromised
 *   ACUTE:         ~8% — can kill a healthy host without treatment
 *   CATASTROPHIC:  ~2% — fast progression, high immune evasion
 *   WORLD_STRAIN:    1  — unique per world, endgame discovery
 */
public enum LethalityProfile {
    NON_LETHAL("Non-lethal", 0f, false),
    CONDITIONAL("Conditionally Lethal", 0.3f, false),
    ACUTE("Acutely Lethal", 0.65f, false),
    CATASTROPHIC("Catastrophic", 0.95f, false),
    WORLD_STRAIN("UNKNOWN", 1.0f, true);

    public final String displayName;
    public final float deathProbability;
    public final boolean isWorldStrain;

    LethalityProfile(String displayName, float deathProbability, boolean isWorldStrain) {
        this.displayName = displayName;
        this.deathProbability = deathProbability;
        this.isWorldStrain = isWorldStrain;
    }

    public static LethalityProfile fromSeed(long seed) {
        java.util.Random rng = new java.util.Random(seed);
        float roll = rng.nextFloat();
        if (roll < 0.70f) return NON_LETHAL;
        if (roll < 0.90f) return CONDITIONAL;
        if (roll < 0.98f) return ACUTE;
        return CATASTROPHIC;
    }
}
