package com.grace.micromayhem.microbe;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.core.Holder;

/**
 * 35 hand-defined effects across negative/positive/neutral categories.
 * Custom effects (GLOOM, TREMORS, FEVER, MAGNETIC) are handled via
 * event callbacks in ModEvents rather than vanilla MobEffect.
 */
public enum MicrobeEffect {
    // ---- NONE ----
    NONE("None", null, EffectCategory.NEUTRAL),

    // ---- NEGATIVE (14) ----
    WITHER("Necrotic Infection",        MobEffects.WITHER,              EffectCategory.NEGATIVE),
    POISON("Toxin Release",             MobEffects.POISON,              EffectCategory.NEGATIVE),
    NAUSEA("Nausea",                    MobEffects.CONFUSION,           EffectCategory.NEGATIVE),
    SLOWNESS("Lethargy",                MobEffects.MOVEMENT_SLOWDOWN,   EffectCategory.NEGATIVE),
    WEAKNESS("Cellular Weakness",       MobEffects.WEAKNESS,            EffectCategory.NEGATIVE),
    BLINDNESS("Ocular Inflammation",    MobEffects.BLINDNESS,           EffectCategory.NEGATIVE),
    HUNGER("Appetite Disruption",       MobEffects.HUNGER,              EffectCategory.NEGATIVE),
    MINING_FATIGUE("Neural Degradation",MobEffects.DIG_SLOWDOWN,        EffectCategory.NEGATIVE),
    LEVITATION("Vestibular Disorder",   MobEffects.LEVITATION,          EffectCategory.NEGATIVE),
    DARKNESS("Photophobia",             MobEffects.DARKNESS,            EffectCategory.NEGATIVE),
    BAD_OMEN("Systemic Agitation",      MobEffects.BAD_OMEN,            EffectCategory.NEGATIVE),
    GLOOM("Neuroendocrine Disruption",  null,                           EffectCategory.NEGATIVE), // custom
    TREMORS("Motor Nerve Interference", null,                           EffectCategory.NEGATIVE), // custom
    FEVER("Pyrogenic Response",         null,                           EffectCategory.NEGATIVE), // custom

    // ---- POSITIVE (12) ----
    REGENERATION("Probiotic Boost",       MobEffects.REGENERATION,        EffectCategory.POSITIVE),
    SPEED("Metabolic Stimulant",          MobEffects.MOVEMENT_SPEED,      EffectCategory.POSITIVE),
    RESISTANCE("Immune Fortification",    MobEffects.DAMAGE_RESISTANCE,   EffectCategory.POSITIVE),
    ABSORPTION("Cell Wall Reinforcement", MobEffects.ABSORPTION,          EffectCategory.POSITIVE),
    HEALTH_BOOST("Symbiotic Growth",      MobEffects.HEALTH_BOOST,        EffectCategory.POSITIVE),
    NIGHT_VISION("Rhodopsin Synthesis",   MobEffects.NIGHT_VISION,        EffectCategory.POSITIVE),
    JUMP_BOOST("Neuromuscular Enhancement",MobEffects.JUMP,              EffectCategory.POSITIVE),
    HASTE("Adrenal Symbiosis",            MobEffects.DIG_SPEED,           EffectCategory.POSITIVE),
    FIRE_RESISTANCE("Thermophilic Shield",MobEffects.FIRE_RESISTANCE,     EffectCategory.POSITIVE),
    WATER_BREATHING("Gill Adaptation",    MobEffects.WATER_BREATHING,     EffectCategory.POSITIVE),
    SATURATION("Nutrient Synthesis",      MobEffects.SATURATION,          EffectCategory.POSITIVE),
    MAGNETIC("Electromagnetic Symbiosis", null,                           EffectCategory.POSITIVE), // custom

    // ---- NEUTRAL (9) ----
    GLOWING("Bioluminescence",            MobEffects.GLOWING,             EffectCategory.NEUTRAL),
    SLOW_FALLING("Buoyancy Factor",       MobEffects.SLOW_FALLING,        EffectCategory.NEUTRAL),
    INVISIBILITY("Optical Camouflage",    MobEffects.INVISIBILITY,        EffectCategory.NEUTRAL),
    LUCK("Microbiome Fortune",            MobEffects.LUCK,                EffectCategory.NEUTRAL),
    UNLUCK("Dysbiosis",                   MobEffects.UNLUCK,              EffectCategory.NEUTRAL),
    DOLPHINS_GRACE("Hydrodynamic Coat",   MobEffects.DOLPHINS_GRACE,      EffectCategory.NEUTRAL),
    CONDUIT_POWER("Bioelectric Field",    MobEffects.CONDUIT_POWER,       EffectCategory.NEUTRAL),
    HERO("Pheromone Emission",            MobEffects.HERO_OF_THE_VILLAGE, EffectCategory.NEUTRAL),
    WEAVING("Silk Synthesis",             MobEffects.WEAVING,             EffectCategory.NEUTRAL);

    public enum EffectCategory { NEGATIVE, POSITIVE, NEUTRAL }

    public final String displayName;
    public final Holder<MobEffect> vanillaEffect;
    public final EffectCategory category;

    MicrobeEffect(String displayName, Holder<MobEffect> effect, EffectCategory category) {
        this.displayName = displayName;
        this.vanillaEffect = effect;
        this.category = category;
    }

    public boolean hasVanillaEffect() { return vanillaEffect != null; }
    public boolean beneficial()       { return category == EffectCategory.POSITIVE; }
    /** Backward compat alias used throughout existing code */
    public boolean isBeneficial()     { return beneficial(); }

    /** Custom-callback effects that don't map to a vanilla MobEffect */
    public boolean isCustom() { return vanillaEffect == null && this != NONE; }

    /** Invert this effect for autoimmune misfire — beneficial→negative equivalent, vice versa */
    public MicrobeEffect invert() {
        return switch (this) {
            case REGENERATION  -> WITHER;
            case SPEED         -> SLOWNESS;
            case RESISTANCE    -> WEAKNESS;
            case ABSORPTION    -> HUNGER;
            case HEALTH_BOOST  -> WITHER;
            case NIGHT_VISION  -> BLINDNESS;
            case JUMP_BOOST    -> SLOWNESS;
            case HASTE         -> MINING_FATIGUE;
            case FIRE_RESISTANCE -> FEVER;
            case WATER_BREATHING -> NAUSEA;
            case SATURATION    -> HUNGER;
            case MAGNETIC      -> TREMORS;
            case WITHER        -> REGENERATION;
            case POISON        -> REGENERATION;
            case SLOWNESS      -> SPEED;
            case WEAKNESS      -> RESISTANCE;
            default            -> NAUSEA;
        };
    }
}
