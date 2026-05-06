package com.grace.micromayhem.microbe;

/**
 * Where a microbe naturally lives and how it spreads to new hosts.
 */
public enum SpreadVector {
    SOIL("Soil-borne", "Found in dirt, gravel, sand, and clay"),
    STONE("Lithotrophic", "Colonises stone and ore-bearing rock"),
    WOOD("Wood-rot", "Lives in wood, leaves, and organic matter"),
    WATER("Waterborne", "Suspended in water sources and rain"),
    FOOD("Foodborne", "Contaminates raw food items"),
    AIRBORNE("Airborne", "Drifts in biome air; passive exposure"),
    CONTACT("Contact", "Spreads on entity-to-entity touch"),
    AEROSOL("Aerosol", "Ejected from infected mob sneezing particles");

    public final String displayName;
    public final String description;

    SpreadVector(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}
