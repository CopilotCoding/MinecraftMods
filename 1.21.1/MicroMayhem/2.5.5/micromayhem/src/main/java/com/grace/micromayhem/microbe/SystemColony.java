package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Represents the microbial colony state within one body system (respiratory, digestive, etc.)
 *
 * Tracks:
 *   - Active strains and their colony sizes (0–1, shared pool capped at 1.0)
 *   - System health (0–1, degrades from severe infections)
 *   - Cohabitation counters (days strains have coexisted peacefully)
 *   - Established strains (3+ days peaceful cohabitation)
 */
public class SystemColony {

    public final BodySystem system;

    /** Colony size per strain — shared pool, sum ideally ≤ 1.0 but can exceed briefly */
    public final Map<Long, Float> colonySizes = new LinkedHashMap<>();

    /** Infection stage per strain */
    public final Map<Long, InfectionStage> stages = new LinkedHashMap<>();

    /** Ticks each strain has been in current stage */
    public final Map<Long, Integer> stageTicks = new LinkedHashMap<>();

    /** Days two strains have cohabited without COMPETITIVE conflict */
    public final Map<Long, Integer> cohabitationDays = new LinkedHashMap<>();

    /** Strains established after 3+ peaceful days */
    public final Set<Long> establishedStrains = new HashSet<>();

    /** System health 0–1. Zero = system failure. */
    public float systemHealth = 1.0f;

    /** Whether this system is currently in failure state */
    public boolean inFailure = false;

    public enum InfectionStage {
        EXPOSED, LATENT, ACTIVE, SEVERE, CLEARING
    }

    public SystemColony(BodySystem system) {
        this.system = system;
    }

    // ---- Colony access ----

    public float getTotalColonyMass() {
        float total = 0f;
        for (float v : colonySizes.values()) total += v;
        return total;
    }

    public float getBeneficialMass(MicrobeRegistry registry) {
        float mass = 0f;
        for (Map.Entry<Long, Float> e : colonySizes.entrySet()) {
            MicrobeStrain s = registry.getStrain(e.getKey());
            if (s != null && s.effect.beneficial()) mass += e.getValue();
        }
        return mass;
    }

    public float getPathogenicMass(MicrobeRegistry registry) {
        float mass = 0f;
        for (Map.Entry<Long, Float> e : colonySizes.entrySet()) {
            MicrobeStrain s = registry.getStrain(e.getKey());
            if (s != null && s.effect.category == MicrobeEffect.EffectCategory.NEGATIVE)
                mass += e.getValue();
        }
        return mass;
    }

    public long getDominantStrain() {
        long best = -1;
        float bestSize = 0f;
        for (Map.Entry<Long, Float> e : colonySizes.entrySet()) {
            if (e.getValue() > bestSize) { bestSize = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    public boolean hasStrain(long strainId) {
        return colonySizes.containsKey(strainId);
    }

    public InfectionStage getStage(long strainId) {
        return stages.getOrDefault(strainId, InfectionStage.LATENT);
    }

    public void addStrain(long strainId, float initialColony) {
        colonySizes.put(strainId, initialColony);
        stages.put(strainId, InfectionStage.EXPOSED);
        stageTicks.put(strainId, 0);
    }

    public void removeStrain(long strainId) {
        colonySizes.remove(strainId);
        stages.remove(strainId);
        stageTicks.remove(strainId);
        cohabitationDays.remove(strainId);
        establishedStrains.remove(strainId);
    }

    public Set<Long> getAllStrainIds() {
        return Collections.unmodifiableSet(colonySizes.keySet());
    }

    // ---- System health ----

    public void damageSystem(float amount) {
        systemHealth = Math.max(0f, systemHealth - amount);
        if (systemHealth <= 0f) inFailure = true;
    }

    public void repairSystem(float amount) {
        systemHealth = Math.min(1f, systemHealth + amount);
        if (systemHealth > 0.1f) inFailure = false;
    }

    // ---- NBT ----

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("System", system.name());
        tag.putFloat("SystemHealth", systemHealth);
        tag.putBoolean("InFailure", inFailure);

        ListTag strainList = new ListTag();
        for (long id : colonySizes.keySet()) {
            CompoundTag st = new CompoundTag();
            st.putLong("StrainId", id);
            st.putFloat("ColonySize", colonySizes.getOrDefault(id, 0f));
            st.putString("Stage", stages.getOrDefault(id, InfectionStage.LATENT).name());
            st.putInt("StageTicks", stageTicks.getOrDefault(id, 0));
            st.putInt("CohabDays", cohabitationDays.getOrDefault(id, 0));
            st.putBoolean("Established", establishedStrains.contains(id));
            strainList.add(st);
        }
        tag.put("Strains", strainList);
        return tag;
    }

    public static SystemColony load(CompoundTag tag) {
        BodySystem sys = BodySystem.valueOf(tag.getString("System"));
        SystemColony colony = new SystemColony(sys);
        colony.systemHealth = tag.getFloat("SystemHealth");
        colony.inFailure    = tag.getBoolean("InFailure");

        ListTag strainList = tag.getList("Strains", Tag.TAG_COMPOUND);
        for (int i = 0; i < strainList.size(); i++) {
            CompoundTag st = strainList.getCompound(i);
            long id = st.getLong("StrainId");
            colony.colonySizes.put(id, st.getFloat("ColonySize"));
            colony.stages.put(id, InfectionStage.valueOf(st.getString("Stage")));
            colony.stageTicks.put(id, st.getInt("StageTicks"));
            colony.cohabitationDays.put(id, st.getInt("CohabDays"));
            if (st.getBoolean("Established")) colony.establishedStrains.add(id);
        }
        return colony;
    }
}
