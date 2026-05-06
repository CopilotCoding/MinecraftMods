package com.grace.micromayhem.microbe;

import net.minecraft.nbt.CompoundTag;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Per-player codex of discovered strains.
 * A strain is "discovered" when first viewed through the microscope.
 * Stored as part of player NBT.
 */
public class MicrobeCodex {

    public static final String NBT_KEY = "MicroMayhem_Codex";

    private final Set<Long> discovered = new HashSet<>();

    public void discover(long strainId) {
        discovered.add(strainId);
    }

    public boolean hasDiscovered(long strainId) {
        return discovered.contains(strainId);
    }

    public Set<Long> getDiscovered() {
        return Collections.unmodifiableSet(discovered);
    }

    public int count() {
        return discovered.size();
    }

    public void saveToTag(CompoundTag playerTag) {
        CompoundTag tag = new CompoundTag();
        long[] arr = discovered.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray("Discovered", arr);
        playerTag.put(NBT_KEY, tag);
    }

    public void loadFromTag(CompoundTag playerTag) {
        if (!playerTag.contains(NBT_KEY)) return;
        CompoundTag tag = playerTag.getCompoundOrEmpty(NBT_KEY);
        discovered.clear();
        for (long id : tag.getLongArray("Discovered").orElse(new long[0])) discovered.add(id);
    }
}
