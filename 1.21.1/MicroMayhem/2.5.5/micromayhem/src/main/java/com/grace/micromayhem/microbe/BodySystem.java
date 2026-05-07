package com.grace.micromayhem.microbe;

/**
 * The four body system compartments, each hosting independent microbiome colonies.
 *
 * Strains have an affinity for one or more systems based on their spread vectors.
 * Infection progression and system failure are tracked independently per system.
 */
public enum BodySystem {
    RESPIRATORY("Respiratory", "Lungs and airways. Airborne/aerosol strains colonise here first."),
    DIGESTIVE("Digestive", "Gut and stomach. Largest natural microbiome, most stable defense."),
    SKIN("Skin", "Surface and mucous membranes. First contact barrier for most infections."),
    CIRCULATORY("Circulatory", "Bloodstream. Nearly sterile naturally. Most dangerous if colonised.");

    public final String displayName;
    public final String description;

    BodySystem(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Derive which system a strain primarily targets based on its spread vectors.
     * A strain can have affinity for multiple systems — this returns the primary one.
     */
    public static BodySystem primaryAffinity(MicrobeStrain strain) {
        for (SpreadVector v : strain.spreadVectors) {
            switch (v) {
                case AIRBORNE, AEROSOL -> { return RESPIRATORY; }
                case WATER, FOOD       -> { return DIGESTIVE; }
                case CONTACT, WOOD     -> { return SKIN; }
                case SOIL, STONE       -> { return DIGESTIVE; }
            }
        }
        // High aggression strains with no clear vector tend toward circulatory
        if (strain.aggressionFactor > 0.8f) return CIRCULATORY;
        return DIGESTIVE; // default
    }
}
