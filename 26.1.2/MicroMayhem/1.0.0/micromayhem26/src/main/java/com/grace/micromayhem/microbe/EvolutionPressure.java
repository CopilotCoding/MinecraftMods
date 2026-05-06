package com.grace.micromayhem.microbe;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Maps a pressure item placed in the Irradiator to a mutation bias.
 *
 * When a bias is active, the mutation pool is filtered/weighted so that
 * child strains are more likely to express the desired trait.
 *
 * Bias types:
 *   VIRULENCE_UP      — push virulence toward grade III
 *   VIRULENCE_DOWN    — push virulence toward grade I
 *   BENEFICIAL        — strongly bias effect toward beneficial
 *   HARMFUL           — strongly bias effect toward harmful
 *   TRANSMISSIBILITY  — increase transmissibility
 *   NEW_VECTOR        — add a new spread vector
 *   WITHER            — force wither/necrotic effect
 *   REGENERATION      — force regeneration/resistance effect
 *   COMMENSAL         — push toward NONE effect (tame the strain)
 *   GENERATION_RESET  — attempt to stabilize (slow mutation rate)
 */
public enum EvolutionPressure {

    VIRULENCE_UP("Virulence Amplification",
        "Pushes strain toward Grade III severity.",
        Items.BLAZE_ROD),

    VIRULENCE_DOWN("Virulence Attenuation",
        "Reduces strain toward Grade I mild expression.",
        Items.FEATHER),

    BENEFICIAL("Beneficial Selection",
        "Biases mutations toward helpful effects.",
        Items.SUGAR),

    HARMFUL("Pathogenic Selection",
        "Biases mutations toward harmful effects.",
        Items.ROTTEN_FLESH),

    TRANSMISSIBILITY("Transmission Enhancement",
        "Increases how easily the strain spreads.",
        Items.SLIME_BALL),

    NEW_VECTOR("Vector Expansion",
        "Adds a new spread pathway to the strain.",
        Items.CHORUS_FRUIT),

    WITHER("Necrotic Induction",
        "Forces mutation toward wither/necrotic expression.",
        Items.FERMENTED_SPIDER_EYE),

    REGENERATION("Regenerative Cultivation",
        "Forces mutation toward regeneration/healing.",
        Items.GOLDEN_APPLE),

    COMMENSAL("Commensal Selection",
        "Pushes strain toward harmless coexistence.",
        Items.BONE_MEAL),

    STABILISE("Genetic Stabilisation",
        "Slows future mutation accumulation by 50%.",
        Items.AMETHYST_SHARD);

    public final String displayName;
    public final String description;
    public final Item triggerItem;

    EvolutionPressure(String displayName, String description, Item triggerItem) {
        this.displayName = displayName;
        this.description = description;
        this.triggerItem = triggerItem;
    }

    /** Look up pressure from an item in the bias slot. Returns null if no match. */
    public static EvolutionPressure fromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (EvolutionPressure p : values()) {
            if (stack.is(p.triggerItem)) return p;
        }
        return null;
    }

    /**
     * Apply this pressure to a candidate mutant strain, modifying it in-place
     * to express the biased trait. Returns true if the bias was successfully applied.
     */
    public boolean applyBias(MicrobeStrain candidate, Random rng) {
        return switch (this) {
            case VIRULENCE_UP -> {
                candidate.virulence = Math.min(3, candidate.virulence + 1);
                yield true;
            }
            case VIRULENCE_DOWN -> {
                candidate.virulence = Math.max(1, candidate.virulence - 1);
                yield true;
            }
            case BENEFICIAL -> {
                MicrobeEffect[] beneficial = Arrays.stream(MicrobeEffect.values())
                    .filter(e -> e.beneficial()).toArray(MicrobeEffect[]::new);
                candidate.effect = beneficial[rng.nextInt(beneficial.length)];
                yield true;
            }
            case HARMFUL -> {
                MicrobeEffect[] harmful = Arrays.stream(MicrobeEffect.values())
                    .filter(e -> !e.beneficial() && e != MicrobeEffect.NONE)
                    .toArray(MicrobeEffect[]::new);
                candidate.effect = harmful[rng.nextInt(harmful.length)];
                yield true;
            }
            case TRANSMISSIBILITY -> {
                candidate.transmissibility = Math.min(0.95f, candidate.transmissibility + 0.2f);
                yield true;
            }
            case NEW_VECTOR -> {
                SpreadVector[] all = SpreadVector.values();
                List<SpreadVector> candidates = new ArrayList<>(Arrays.asList(all));
                candidates.removeAll(candidate.spreadVectors);
                if (!candidates.isEmpty()) {
                    candidate.spreadVectors.add(candidates.get(rng.nextInt(candidates.size())));
                    yield true;
                }
                yield false;
            }
            case WITHER -> {
                candidate.effect = MicrobeEffect.WITHER;
                candidate.virulence = Math.max(candidate.virulence, 2);
                yield true;
            }
            case REGENERATION -> {
                candidate.effect = MicrobeEffect.REGENERATION;
                yield true;
            }
            case COMMENSAL -> {
                candidate.effect = MicrobeEffect.NONE;
                candidate.transmissibility = Math.max(0.02f, candidate.transmissibility - 0.15f);
                yield true;
            }
            case STABILISE -> {
                // Halve the mutation accumulator so this strain mutates slower in future
                candidate.mutationAccumulator = Math.max(0, candidate.mutationAccumulator - 0.5f);
                yield true;
            }
        };
    }
}
