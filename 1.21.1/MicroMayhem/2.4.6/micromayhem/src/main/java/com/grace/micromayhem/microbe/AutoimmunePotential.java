package com.grace.micromayhem.microbe;

/**
 * How capable a strain is of triggering autoimmune response.
 * Generated at strain creation — most strains have NONE.
 *
 * Distribution: NONE 60%, LOW 30%, MODERATE 8%, HIGH 2%
 */
public enum AutoimmunePotential {
    NONE(0f, "No autoimmune potential"),
    LOW(0.02f, "Low autoimmune potential"),
    MODERATE(0.12f, "Moderate autoimmune potential"),
    HIGH(0.55f, "High autoimmune potential");

    /** Base probability of triggering autoimmune on a SEVERE→CLEARING transition */
    public final float baseProbability;
    public final String description;

    AutoimmunePotential(float baseProbability, String description) {
        this.baseProbability = baseProbability;
        this.description = description;
    }

    public static AutoimmunePotential fromSeed(long seed) {
        java.util.Random rng = new java.util.Random(seed ^ 0xAA11BB22CC33DDL);
        float roll = rng.nextFloat();
        if (roll < 0.60f) return NONE;
        if (roll < 0.90f) return LOW;
        if (roll < 0.98f) return MODERATE;
        return HIGH;
    }
}
