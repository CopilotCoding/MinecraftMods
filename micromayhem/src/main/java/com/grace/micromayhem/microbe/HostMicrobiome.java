package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * The complete microbiome state for one host (player or mob).
 * Replaces the old PlayerMicrobiome with a four-system architecture.
 *
 * Stored in player persistent NBT under NBT_KEY.
 */
public class HostMicrobiome {

    public static final String NBT_KEY = "MM_HostMicrobiome";

    private final Map<BodySystem, SystemColony> systems = new EnumMap<>(BodySystem.class);

    public HostMicrobiome() {
        for (BodySystem sys : BodySystem.values()) {
            systems.put(sys, new SystemColony(sys));
        }
    }

    // ---- System access ----

    public SystemColony getSystem(BodySystem system) {
        return systems.get(system);
    }

    // ---- Infection entry ----

    /**
     * Attempt to infect this host with a strain.
     * Entry system is determined by strain's primary body system affinity.
     * Beneficial colony mass resists entry.
     *
     * @param transmissibilityBonus positive = easier, negative = harder
     * @return true if infection took hold
     */
    public boolean tryInfect(MicrobeStrain strain, float transmissibilityBonus,
                              PlayerImmuneSystem immune, MicrobeRegistry registry) {
        BodySystem targetSystem = strain.primarySystem;
        SystemColony colony = systems.get(targetSystem);

        // Already present
        if (colony.hasStrain(strain.strainId)) return false;

        // Immunity check
        if (immune.naturalImmunity.contains(strain.strainId)) return false;

        // Beneficial resistance
        float bMass = colony.getBeneficialMass(registry);
        float resistance = bMass > 0.5f ? 0.4f : bMass * 0.8f;

        // System health modifier — broken barriers let more in
        float systemHealthMod = (1f - colony.systemHealth) * 0.3f;

        float chance = strain.transmissibility + transmissibilityBonus
            - resistance + systemHealthMod
            - immune.getLocalImmunity(targetSystem) * 0.3f;

        if (Math.random() > Math.max(0, Math.min(1, chance))) return false;

        colony.addStrain(strain.strainId, 0.02f); // start with small colony
        registry.incrementHostCount(strain.strainId);
        return true;
    }

    /** Direct injection — bypasses transmission check, goes to target system */
    public void injectDirect(MicrobeStrain strain, BodySystem system, MicrobeRegistry registry) {
        SystemColony colony = systems.get(system);
        if (!colony.hasStrain(strain.strainId)) {
            colony.addStrain(strain.strainId, 0.08f); // higher initial colony from injection
            registry.incrementHostCount(strain.strainId);
        }
    }

    /** Migrate a strain from one system to another */
    public void migrateStrain(long strainId, BodySystem from, BodySystem to, MicrobeRegistry registry) {
        SystemColony src = systems.get(from);
        SystemColony dst = systems.get(to);
        if (!src.hasStrain(strainId)) return;
        float size = src.colonySizes.getOrDefault(strainId, 0f) * 0.3f; // only fraction migrates
        if (!dst.hasStrain(strainId)) {
            dst.addStrain(strainId, size);
            registry.incrementHostCount(strainId);
        }
    }

    // ---- Clearing ----

    public void clearStrain(long strainId, MicrobeRegistry registry) {
        for (SystemColony colony : systems.values()) {
            if (colony.hasStrain(strainId)) {
                colony.removeStrain(strainId);
                registry.decrementHostCount(strainId);
            }
        }
    }

    public void clearAllOfType(MicrobeType type, MicrobeRegistry registry) {
        for (SystemColony colony : systems.values()) {
            List<Long> toRemove = new ArrayList<>();
            for (long id : colony.getAllStrainIds()) {
                MicrobeStrain s = registry.getStrain(id);
                if (s != null && s.type == type) toRemove.add(id);
            }
            for (long id : toRemove) {
                colony.removeStrain(id);
                registry.decrementHostCount(id);
            }
        }
    }

    public void clearAll(MicrobeRegistry registry) {
        for (SystemColony colony : systems.values()) {
            for (long id : new ArrayList<>(colony.getAllStrainIds()))
                registry.decrementHostCount(id);
            colony.colonySizes.clear();
            colony.stages.clear();
            colony.stageTicks.clear();
        }
    }

    // ---- Query helpers ----

    public boolean hasAnyActiveInfection() {
        for (SystemColony colony : systems.values()) {
            for (SystemColony.InfectionStage stage : colony.stages.values()) {
                if (stage == SystemColony.InfectionStage.ACTIVE
                        || stage == SystemColony.InfectionStage.SEVERE) return true;
            }
        }
        return false;
    }

    public boolean hasStrain(long strainId) {
        for (SystemColony colony : systems.values())
            if (colony.hasStrain(strainId)) return true;
        return false;
    }

    public List<Long> getAllBeneficialStrains(MicrobeRegistry registry) {
        List<Long> result = new ArrayList<>();
        for (SystemColony colony : systems.values()) {
            for (long id : colony.getAllStrainIds()) {
                MicrobeStrain s = registry.getStrain(id);
                if (s != null && s.effect.beneficial()) result.add(id);
            }
        }
        return result;
    }

    public float getTotalBeneficialMass(MicrobeRegistry registry) {
        float total = 0f;
        for (SystemColony colony : systems.values())
            total += colony.getBeneficialMass(registry);
        return total;
    }

    public boolean isMicrobiomeStable() {
        // Stable = 3+ established strains across all systems, no SEVERE anywhere
        int established = 0;
        for (SystemColony colony : systems.values()) {
            established += colony.establishedStrains.size();
            for (SystemColony.InfectionStage s : colony.stages.values())
                if (s == SystemColony.InfectionStage.SEVERE) return false;
        }
        return established >= 3;
    }

    public int countFailedSystems() {
        int count = 0;
        for (SystemColony colony : systems.values())
            if (colony.inFailure) count++;
        return count;
    }

    // ---- NBT ----

    public void saveToTag(CompoundTag playerTag) {
        ListTag sysList = new ListTag();
        for (SystemColony colony : systems.values()) sysList.add(colony.save());
        CompoundTag tag = new CompoundTag();
        tag.put("Systems", sysList);
        playerTag.put(NBT_KEY, tag);
    }

    public void loadFromTag(CompoundTag playerTag) {
        if (!playerTag.contains(NBT_KEY)) return;
        CompoundTag tag = playerTag.getCompound(NBT_KEY);
        ListTag sysList = tag.getList("Systems", Tag.TAG_COMPOUND);
        for (int i = 0; i < sysList.size(); i++) {
            SystemColony colony = SystemColony.load(sysList.getCompound(i));
            systems.put(colony.system, colony);
        }
    }
}
