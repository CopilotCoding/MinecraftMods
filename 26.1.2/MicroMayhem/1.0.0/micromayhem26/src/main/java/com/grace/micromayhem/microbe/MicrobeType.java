package com.grace.micromayhem.microbe;

/**
 * The broad biological category of a microbe.
 * Each type has different generation rules, spread vectors, and mutation rates.
 */
public enum MicrobeType {
    BACTERIA("Bacterium", 0.03f, true),
    VIRUS("Virus", 0.08f, false),
    PARASITE("Parasite", 0.01f, true),
    FUNGUS("Fungus", 0.02f, true);

    public final String displayName;
    /** Base mutation rate per in-game day */
    public final float baseMutationRate;
    /** Whether this type can be cultured in a petri dish */
    public final boolean culturable;

    MicrobeType(String displayName, float baseMutationRate, boolean culturable) {
        this.displayName = displayName;
        this.baseMutationRate = baseMutationRate;
        this.culturable = culturable;
    }
}
