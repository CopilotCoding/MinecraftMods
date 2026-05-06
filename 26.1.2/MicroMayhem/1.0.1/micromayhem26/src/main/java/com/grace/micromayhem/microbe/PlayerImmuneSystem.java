package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Per-player immune system state.
 *
 * Tracks:
 *   - immuneStrength: overall immune capacity (0–1)
 *   - susceptibilityTier: permanent hidden roll affecting autoimmune risk
 *   - adaptiveImmunity: evasion bonuses gained from naturally clearing strains
 *   - autoimmune state (primed → active → severity levels)
 *   - rapid clearance tracker for autoimmune triggering
 *   - system-local immunity per BodySystem
 */
public class PlayerImmuneSystem {

    public static final String NBT_KEY = "MM_ImmuneSystem";

    public enum SusceptibilityTier {
        RESILIENT(0.15f),   // ~50% of players
        NORMAL(0.50f),      // ~40%
        PREDISPOSED(1.0f);  // ~10%

        public final float autoimmuneMult;
        SusceptibilityTier(float mult) { this.autoimmuneMult = mult; }

        public static SusceptibilityTier roll(long seed) {
            Random rng = new Random(seed ^ 0x5C3B117L);
            float r = rng.nextFloat();
            if (r < 0.50f) return RESILIENT;
            if (r < 0.90f) return NORMAL;
            return PREDISPOSED;
        }
    }

    // ---- Core state ----
    public float immuneStrength = 0.7f;
    public float immuneStrengthCeiling = 1.0f; // can increase from natural clearances
    public SusceptibilityTier susceptibility = SusceptibilityTier.NORMAL;
    public boolean susceptibilityRolled = false;

    // ---- Adaptive immunity ----
    /** strainId → evasion bonus this player's immune system has against it */
    public final Map<Long, Float> adaptiveImmunity = new HashMap<>();
    /** strains fully cleared naturally — grants immunity */
    public final Set<Long> naturalImmunity = new HashSet<>();

    // ---- Autoimmune state ----
    public boolean autoimmunePrimed = false;
    public long autoimmunePrimedAt  = -1L; // world time when primed
    public boolean autoimmune       = false;
    public int autoimmuneLevel      = 0;    // 1–3
    public long lastMisfireTime     = 0L;

    // ---- Rapid clearance tracker ----
    /** World times of recent natural clearances (last 48 in-game hours = 48*1200 ticks) */
    public final List<Long> recentClearanceTimes = new ArrayList<>();

    // ---- Local system immunity ----
    public final Map<BodySystem, Float> localImmunity = new EnumMap<>(BodySystem.class);

    // ---- Stability tracking ----
    public int daysStable = 0; // consecutive days with MICROBIOME_STABLE

    public PlayerImmuneSystem() {
        for (BodySystem sys : BodySystem.values()) {
            localImmunity.put(sys, 0.5f);
        }
    }

    // ---- Susceptibility ----

    public void rollSusceptibility(Player player) {
        if (susceptibilityRolled) return;
        long seed = player.getUUID().getMostSignificantBits()
                  ^ player.getUUID().getLeastSignificantBits();
        susceptibility = SusceptibilityTier.roll(seed);
        susceptibilityRolled = true;
    }

    // ---- Immune strength ----

    public void deplete(float amount) {
        immuneStrength = Math.max(0f, immuneStrength - amount);
    }

    public void regenerate(float amount) {
        immuneStrength = Math.min(immuneStrengthCeiling, immuneStrength + amount);
    }

    /** Called when a strain is naturally cleared. Strengthens adaptive immunity. */
    public void onNaturalClearance(long strainId, long worldTime) {
        naturalImmunity.add(strainId);
        // Adaptive bonus for this strain — slight ceiling raise
        immuneStrengthCeiling = Math.min(1.0f, immuneStrengthCeiling + 0.005f);
        // Track for autoimmune rapid-clearance check
        recentClearanceTimes.add(worldTime);
        // Prune old clearances (older than 48 in-game hours = 57600 ticks)
        recentClearanceTimes.removeIf(t -> worldTime - t > 57600);
        // Adaptive evasion bonus increase
        adaptiveImmunity.merge(strainId, 0.05f, (a, b) -> Math.min(0.5f, a + b));
    }

    /** Called when a strain is cleared by antibiotics — no immunity bonus, slight penalty */
    public void onAntibioticClearance() {
        immuneStrengthCeiling = Math.max(0.5f, immuneStrengthCeiling - 0.01f);
    }

    // ---- Autoimmune ----

    /**
     * Check whether a SEVERE→CLEARING transition should trigger autoimmune.
     * Returns true if autoimmune was triggered.
     */
    public boolean checkAutoimmuneTrigger(MicrobeStrain strain, long worldTime) {
        if (strain.autoimmunePotential == AutoimmunePotential.NONE) return false;
        if (immuneStrength > 0.4f) return false; // immune system not exhausted

        // Combined probability: strain potential × susceptibility × immune exhaustion
        float baseProbability = strain.autoimmunePotential.baseProbability;
        float susceptMult     = susceptibility.autoimmuneMult;
        float exhaustionMult  = 1.0f + (0.4f - immuneStrength) * 2f; // more exhausted = higher risk

        float finalProbability = baseProbability * susceptMult * exhaustionMult;

        if (Math.random() > finalProbability) return false;

        if (!autoimmunePrimed) {
            // First trigger — prime only, no full condition yet
            autoimmunePrimed = true;
            autoimmunePrimedAt = worldTime;
            return true;
        }

        // Second trigger within 30 in-game days (36000 ticks)
        if (worldTime - autoimmunePrimedAt <= 36000) {
            autoimmune = true;
            autoimmuneLevel = Math.min(3, autoimmuneLevel + 1);
            return true;
        }

        // Primed period expired — reset and prime again
        autoimmunePrimedAt = worldTime;
        return true;
    }

    /**
     * Attempt autoimmune misfire. Returns a strain ID to invert, or -1 if no misfire.
     * Only fires if not wearing full hazmat suit (checked by caller).
     */
    public long tryMisfire(Collection<Long> beneficialStrains, float beneficialMass, long worldTime) {
        if (!autoimmune || autoimmuneLevel == 0) return -1L;
        if (beneficialStrains.isEmpty()) return -1L;

        // Misfire suppressed by strong beneficial microbiome
        if (beneficialMass > 0.6f) return -1L;

        // Base interval: 6000 ticks (5min), reduced by level
        long misfireInterval = 6000L - (autoimmuneLevel * 1000L);
        if (worldTime - lastMisfireTime < misfireInterval) return -1L;

        // 20% base chance, modified by level
        float chance = 0.20f + (autoimmuneLevel * 0.10f);
        if (Math.random() > chance) return -1L;

        lastMisfireTime = worldTime;
        List<Long> list = new ArrayList<>(beneficialStrains);
        return list.get((int)(Math.random() * list.size()));
    }

    /** Tick called once per in-game day to check stability de-escalation */
    public void onDayTick(boolean microbiomeStable) {
        if (microbiomeStable) {
            daysStable++;
            if (autoimmune && daysStable >= 7) {
                autoimmuneLevel = Math.max(0, autoimmuneLevel - 1);
                if (autoimmuneLevel == 0) autoimmune = false;
                daysStable = 0;
            }
        } else {
            daysStable = 0;
        }
    }

    // ---- Local immunity ----

    public float getLocalImmunity(BodySystem system) {
        return localImmunity.getOrDefault(system, 0.5f);
    }

    public void strengthenLocal(BodySystem system, float amount) {
        localImmunity.merge(system, amount, (a, b) -> Math.min(1.0f, a + b));
    }

    // ---- NBT ----

    public void saveToTag(CompoundTag playerTag) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("ImmuneStrength", immuneStrength);
        tag.putFloat("ImmuneCeiling", immuneStrengthCeiling);
        tag.putString("Susceptibility", susceptibility.name());
        tag.putBoolean("SusceptibilityRolled", susceptibilityRolled);
        tag.putBoolean("AutoimmunePrimed", autoimmunePrimed);
        tag.putLong("AutoimmunePrimedAt", autoimmunePrimedAt);
        tag.putBoolean("Autoimmune", autoimmune);
        tag.putInt("AutoimmuneLevel", autoimmuneLevel);
        tag.putLong("LastMisfire", lastMisfireTime);
        tag.putInt("DaysStable", daysStable);

        long[] naturalArr = naturalImmunity.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("NaturalImmunity", naturalArr);

        long[] clearTimes = recentClearanceTimes.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("RecentClearances", clearTimes);

        // Local immunity
        CompoundTag localTag = new CompoundTag();
        for (Map.Entry<BodySystem, Float> e : localImmunity.entrySet()) {
            localTag.putFloat(e.getKey().name(), e.getValue());
        }
        tag.put("LocalImmunity", localTag);

        playerTag.put(NBT_KEY, tag);
    }

    public void loadFromTag(CompoundTag playerTag) {
        if (!playerTag.contains(NBT_KEY)) return;
        CompoundTag tag = playerTag.getCompoundOrEmpty(NBT_KEY);
        immuneStrength        = tag.getFloatOr("ImmuneStrength", 0f);
        immuneStrengthCeiling = tag.contains("ImmuneCeiling") ? tag.getFloatOr("ImmuneCeiling", 0f) : 1.0f;
        susceptibilityRolled  = tag.getBooleanOr("SusceptibilityRolled", false);
        if (susceptibilityRolled)
            susceptibility    = SusceptibilityTier.valueOf(tag.getStringOr("Susceptibility", ""));
        autoimmunePrimed      = tag.getBooleanOr("AutoimmunePrimed", false);
        autoimmunePrimedAt    = tag.getLongOr("AutoimmunePrimedAt", 0L);
        autoimmune            = tag.getBooleanOr("Autoimmune", false);
        autoimmuneLevel       = tag.getIntOr("AutoimmuneLevel", 0);
        lastMisfireTime       = tag.getLongOr("LastMisfire", 0L);
        daysStable            = tag.getIntOr("DaysStable", 0);

        naturalImmunity.clear();
        for (long id : tag.getLongArray("NaturalImmunity").orElse(new long[0])) naturalImmunity.add(id);

        recentClearanceTimes.clear();
        for (long t : tag.getLongArray("RecentClearances").orElse(new long[0])) recentClearanceTimes.add(t);

        if (tag.contains("LocalImmunity")) {
            CompoundTag localTag = tag.getCompoundOrEmpty("LocalImmunity");
            for (BodySystem sys : BodySystem.values()) {
                if (localTag.contains(sys.name()))
                    localImmunity.put(sys, localTag.getFloatOr(sys.name(), 0f));
            }
        }
    }
}
