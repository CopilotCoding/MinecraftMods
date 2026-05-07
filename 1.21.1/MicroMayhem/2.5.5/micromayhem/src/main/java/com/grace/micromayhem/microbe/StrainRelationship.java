package com.grace.micromayhem.microbe;

/**
 * The ecological relationship between two cohabiting strains.
 * Derived deterministically from both strain IDs — same pair always same relationship.
 */
public enum StrainRelationship {
    NEUTRAL("Neutral", "Coexist, share space passively without interaction."),
    COMPETITIVE("Competitive", "Actively fight for colony space. High aggression wins."),
    SYNERGISTIC("Synergistic", "Both grow faster when cohabiting. Effects may stack."),
    PARASITIC("Parasitic", "One feeds off the other's colony size, shrinking it slowly."),
    SUPPRESSIVE("Suppressive", "One prevents the other from progressing past LATENT stage.");

    public final String displayName;
    public final String description;

    StrainRelationship(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Deterministically derive the relationship between two strains.
     * Order-independent — derive(a,b) == derive(b,a) for symmetric relationships.
     */
    public static StrainRelationship derive(long strainIdA, long strainIdB) {
        // XOR is commutative so order doesn't matter
        long combined = strainIdA ^ strainIdB ^ 0x6C62272E07BB0142L;
        java.util.Random rng = new java.util.Random(combined);
        float roll = rng.nextFloat();
        if (roll < 0.40f) return NEUTRAL;
        if (roll < 0.65f) return COMPETITIVE;
        if (roll < 0.80f) return SYNERGISTIC;
        if (roll < 0.92f) return PARASITIC;
        return SUPPRESSIVE;
    }

    /**
     * Whether this relationship is inherently harmful to the host
     * (competitive/parasitic strains stress the host, synergistic may benefit it).
     */
    public boolean stressesHost() {
        return this == COMPETITIVE || this == PARASITIC;
    }
}
