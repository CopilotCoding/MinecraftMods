package com.grace.micromayhem.microbe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.*;

/**
 * Per-world saved data holding every known MicrobeStrain.
 * Strains are generated from world seed + index.
 * Each in-game day, mutation accumulators tick and child strains spawn.
 */
public class MicrobeRegistry extends SavedData {

    private static final String DATA_NAME = "micromayhem_strains";
    private static final int BASE_STRAIN_COUNT = 64;
    private static final float MUTATION_THRESHOLD = 1.0f;

    private final Map<Long, MicrobeStrain> strains = new LinkedHashMap<>();
    private long worldSeed;
    private long dayCounter;

    private MicrobeRegistry() {}

    // ---- Access ----

    public static MicrobeRegistry get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
            new SavedData.Factory<>(
                MicrobeRegistry::new,
                (tag, provider) -> MicrobeRegistry.load(tag)
            ),
            DATA_NAME
        );
    }

    public void init(long seed) {
        if (!strains.isEmpty()) return;
        this.worldSeed = seed;
        for (int i = 0; i < BASE_STRAIN_COUNT; i++) {
            long strainSeed = seed ^ ((long) i * 0x9E3779B97F4A7C15L);
            MicrobeStrain strain = MicrobeStrain.generate(strainSeed);
            strains.put(strain.strainId, strain);
        }
        setDirty();
    }

    public MicrobeStrain getStrain(long id) { return strains.get(id); }

    public Collection<MicrobeStrain> getAllStrains() {
        return Collections.unmodifiableCollection(strains.values());
    }

    public void addStrain(MicrobeStrain strain) {
        strains.put(strain.strainId, strain);
        setDirty();
    }

    public void onDayPassed(long currentDay) {
        if (currentDay <= dayCounter) return;
        dayCounter = currentDay;

        Random rng = new Random(worldSeed ^ currentDay);
        List<MicrobeStrain> toAdd = new ArrayList<>();

        for (MicrobeStrain strain : strains.values()) {
            float dailyMutation = strain.type.baseMutationRate * strain.virulence;
            strain.mutationAccumulator += dailyMutation;
            if (strain.mutationAccumulator >= MUTATION_THRESHOLD) {
                strain.mutationAccumulator -= MUTATION_THRESHOLD;
                long childSeed = strain.strainId ^ rng.nextLong();
                MicrobeStrain child = strain.mutate(childSeed);
                if (!strains.containsKey(child.strainId)) {
                    toAdd.add(child);
                }
            }
        }
        for (MicrobeStrain child : toAdd) strains.put(child.strainId, child);
        setDirty();
    }

    public MicrobeStrain getStrainForSeed(long derivedSeed) {
        long key = closestStrainKey(derivedSeed);
        return strains.getOrDefault(key, pickAny(derivedSeed));
    }

    private long closestStrainKey(long seed) {
        long best = -1, bestDist = Long.MAX_VALUE;
        for (long id : strains.keySet()) {
            long dist = Math.abs(id - seed);
            if (dist < bestDist) { bestDist = dist; best = id; }
        }
        return best;
    }

    private MicrobeStrain pickAny(long seed) {
        List<MicrobeStrain> list = new ArrayList<>(strains.values());
        int idx = (int) Math.abs(seed % list.size());
        return list.get(idx);
    }

    // ---- Serialisation — 1.21.1 signature ----

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("WorldSeed", worldSeed);
        tag.putLong("DayCounter", dayCounter);
        ListTag list = new ListTag();
        for (MicrobeStrain s : strains.values()) list.add(s.save());
        tag.put("Strains", list);
        return tag;
    }

    public static MicrobeRegistry load(CompoundTag tag) {
        MicrobeRegistry reg = new MicrobeRegistry();
        reg.worldSeed = tag.getLong("WorldSeed");
        reg.dayCounter = tag.getLong("DayCounter");
        ListTag list = tag.getList("Strains", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MicrobeStrain s = MicrobeStrain.load(list.getCompound(i));
            reg.strains.put(s.strainId, s);
        }
        return reg;
    }

    // ---- v2.0 additions ----

    /** Called when a new infection starts in any host */
    public void incrementHostCount(long strainId) {
        MicrobeStrain s = strains.get(strainId);
        if (s != null) { s.hostCount++; s.daysSinceLastHost = 0; setDirty(); }
    }

    /** Called when an infection clears */
    public void decrementHostCount(long strainId) {
        MicrobeStrain s = strains.get(strainId);
        if (s != null) { s.hostCount = Math.max(0, s.hostCount - 1); setDirty(); }
    }

    public MicrobeStrain getOrCreateWorldStrain() {
        for (MicrobeStrain s : strains.values()) {
            if (s.isWorldStrain) return s;
        }
        long worldStrainSeed = worldSeed ^ 0xD34DB33FL;
        MicrobeStrain ws = MicrobeStrain.generate(worldStrainSeed);
        ws.isWorldStrain       = true;
        ws.lethalityProfile    = LethalityProfile.WORLD_STRAIN;
        ws.aggressionFactor    = 0.95f;
        ws.defenseFactor       = 0.90f;
        ws.autoimmunePotential = AutoimmunePotential.HIGH;
        ws.virulence           = 3;
        ws.transmissibility    = 0.85f;
        ws.fullyCharacterised  = false;
        ws.microscopeViewCount = 0;
        strains.put(ws.strainId, ws);
        setDirty();
        return ws;
    }
}