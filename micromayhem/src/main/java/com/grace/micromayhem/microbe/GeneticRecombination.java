package com.grace.micromayhem.microbe;

import java.util.*;

/**
 * Handles genetic recombination between two parent MicrobeStrains.
 *
 * Crossover rules:
 *   - Types must match (same kingdom)
 *   - Each trait independently inherits from parent A or B (50/50)
 *   - 15% chance per trait of splice-junction novelty — emergent expression
 *     that neither parent carried
 *   - Spread vectors: union of both parents' vectors, then randomly trim
 *     to at most 4, favouring vectors present in both
 *   - Generation = max(parentA.gen, parentB.gen) + 1
 *   - Mutation accumulator inherits the higher of the two (genetic instability
 *     is dominant — a stressed genome stays stressed)
 *
 * Returns a list of 2–3 candidate recombinants.
 */
public class GeneticRecombination {

    private static final float NOVELTY_CHANCE   = 0.15f;
    private static final int   CANDIDATE_COUNT  = 3;   // always produce 3, player picks

    /**
     * Attempt recombination. Returns empty if types don't match.
     * Seed is used for determinism (world seed ^ game time).
     */
    public static List<MicrobeStrain> recombine(
            MicrobeStrain parentA, MicrobeStrain parentB,
            long seed, MicrobeRegistry registry) {

        if (parentA.type != parentB.type) return Collections.emptyList();

        List<MicrobeStrain> results = new ArrayList<>();
        Random rng = new Random(seed);

        for (int c = 0; c < CANDIDATE_COUNT; c++) {
            long childSeed = seed ^ ((long)(c + 1) * 0x9E3779B97F4A7C15L);
            MicrobeStrain child = new MicrobeStrain();
            child.strainId = childSeed;
            child.type = parentA.type;

            // ---- Name: combine genus from one, species from other ----
            child.genus   = rng.nextBoolean() ? parentA.genus   : parentB.genus;
            child.species = rng.nextBoolean() ? parentA.species : parentB.species;
            // Append a splice marker so it's clearly a recombinant
            child.species = child.species + "×";

            // ---- Effect: 50/50 or novel ----
            if (rng.nextFloat() < NOVELTY_CHANCE) {
                child.effect = novelEffect(parentA.effect, parentB.effect, rng);
            } else {
                child.effect = rng.nextBoolean() ? parentA.effect : parentB.effect;
            }

            // ---- Virulence: inherit, average, or novel ----
            if (rng.nextFloat() < NOVELTY_CHANCE) {
                // Novel: could be higher than either parent (hybrid vigour) or lower
                int avg = (parentA.virulence + parentB.virulence);
                child.virulence = Math.max(1, Math.min(3, (avg / 2) + (rng.nextInt(3) - 1)));
            } else {
                child.virulence = rng.nextBoolean() ? parentA.virulence : parentB.virulence;
            }

            // ---- Transmissibility: weighted blend + optional novelty ----
            if (rng.nextFloat() < NOVELTY_CHANCE) {
                // Hybrid can be more transmissible than either parent
                float max = Math.max(parentA.transmissibility, parentB.transmissibility);
                child.transmissibility = Math.min(0.98f, max + rng.nextFloat() * 0.15f);
            } else {
                float blend = (parentA.transmissibility + parentB.transmissibility) / 2f;
                child.transmissibility = blend + (rng.nextFloat() - 0.5f) * 0.05f;
                child.transmissibility = Math.max(0.02f, Math.min(0.95f, child.transmissibility));
            }

            // ---- Spread vectors: union, biased toward shared vectors ----
            child.spreadVectors = recombineVectors(parentA.spreadVectors, parentB.spreadVectors, rng);

            // ---- Effect duration: inherit from higher-virulence parent ----
            child.effectDurationTicks = parentA.virulence >= parentB.virulence
                ? parentA.effectDurationTicks : parentB.effectDurationTicks;
            // Small variance
            child.effectDurationTicks = Math.max(200,
                (int)(child.effectDurationTicks * (0.85f + rng.nextFloat() * 0.3f)));

            // ---- Generation and stability ----
            child.generation = Math.max(parentA.generation, parentB.generation) + 1;
            // Instability is dominant — stressed genomes stay stressed
            child.mutationAccumulator = Math.max(
                parentA.mutationAccumulator, parentB.mutationAccumulator);

            registry.addStrain(child);
            results.add(child);
        }

        return results;
    }

    /**
     * Calculate splice duration in ticks (10–20s = 200–400 ticks).
     * Scales with genetic distance between the two parents.
     */
    public static int spliceDuration(MicrobeStrain a, MicrobeStrain b) {
        // Genetic distance: generation difference + trait divergence
        int genDiff = Math.abs(a.generation - b.generation);
        float traitDist = Math.abs(a.transmissibility - b.transmissibility)
            + (a.virulence != b.virulence ? 0.3f : 0f)
            + (a.effect != b.effect ? 0.4f : 0f);

        float distance = Math.min(1.0f, (genDiff * 0.1f + traitDist) / 2f);
        // More different = longer splice = 200 + up to 200 extra ticks
        return 200 + (int)(distance * 200);
    }

    /** Check if two strains can be spliced. Returns null if ok, error message if not. */
    public static String validatePair(MicrobeStrain a, MicrobeStrain b) {
        if (a == null || b == null) return "One or both strains not found in registry.";
        if (a.strainId == b.strainId) return "Cannot splice a strain with itself.";
        if (a.type != b.type) {
            return "Cross-kingdom splicing rejected. " +
                a.type.displayName + " and " + b.type.displayName +
                " are genetically incompatible.";
        }
        return null;
    }

    // ---- Helpers ----

    /**
     * Novel effect: something that is a plausible emergent outcome of the two parents
     * but not present in either. Biased toward the dominant category (harmful/beneficial).
     */
    private static MicrobeEffect novelEffect(MicrobeEffect a, MicrobeEffect b, Random rng) {
        // If both harmful, novel is still harmful; if mixed, coin flip
        boolean targetBeneficial;
        if (a.beneficial() && b.beneficial()) targetBeneficial = true;
        else if (!a.beneficial() && !b.beneficial()) targetBeneficial = false;
        else targetBeneficial = rng.nextBoolean();

        MicrobeEffect[] pool = Arrays.stream(MicrobeEffect.values())
            .filter(e -> e.beneficial() == targetBeneficial && e != a && e != b && e != MicrobeEffect.NONE)
            .toArray(MicrobeEffect[]::new);

        if (pool.length == 0) return rng.nextBoolean() ? a : b;
        return pool[rng.nextInt(pool.length)];
    }

    /**
     * Recombine spread vectors:
     *   - Vectors in BOTH parents: always included
     *   - Vectors in only one parent: 60% chance included
     *   - Entirely novel vector (splice junction): 20% chance of one extra
     *   - Cap at 4 vectors total
     */
    private static List<SpreadVector> recombineVectors(
            List<SpreadVector> vecA, List<SpreadVector> vecB, Random rng) {

        Set<SpreadVector> shared = new LinkedHashSet<>(vecA);
        shared.retainAll(vecB);

        Set<SpreadVector> onlyA = new LinkedHashSet<>(vecA);
        onlyA.removeAll(vecB);

        Set<SpreadVector> onlyB = new LinkedHashSet<>(vecB);
        onlyB.removeAll(vecA);

        List<SpreadVector> result = new ArrayList<>(shared);

        for (SpreadVector v : onlyA) if (rng.nextFloat() < 0.6f) result.add(v);
        for (SpreadVector v : onlyB) if (rng.nextFloat() < 0.6f) result.add(v);

        // Novel vector from splice junction
        if (rng.nextFloat() < 0.20f && result.size() < 4) {
            List<SpreadVector> candidates = new ArrayList<>(Arrays.asList(SpreadVector.values()));
            candidates.removeAll(result);
            if (!candidates.isEmpty()) result.add(candidates.get(rng.nextInt(candidates.size())));
        }

        // Cap at 4
        while (result.size() > 4) result.remove(result.size() - 1);

        // Ensure at least 1
        if (result.isEmpty()) result.add(vecA.isEmpty() ? vecB.get(0) : vecA.get(0));

        return result;
    }
}
