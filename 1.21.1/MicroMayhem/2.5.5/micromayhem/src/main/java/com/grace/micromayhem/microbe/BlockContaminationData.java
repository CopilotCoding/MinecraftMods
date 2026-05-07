package com.grace.micromayhem.microbe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Dynamic block contamination — tracks which blocks have been shed on by infected mobs.
 * Separate from the world-seeded MicrobePresenceHelper which is static.
 * Contamination decays after ~3 in-game days (72000 ticks).
 */
public class BlockContaminationData extends SavedData {

    private static final String DATA_NAME = "micromayhem_block_contamination";
    private static final int DECAY_TICKS = 72000; // 3 in-game days
    private static final int MAX_STRAINS_PER_BLOCK = 3;

    private static class ContaminatedBlock {
        long[] strainIds;
        int ticksRemaining;

        ContaminatedBlock(long[] strainIds, int ticksRemaining) {
            this.strainIds = strainIds;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final Map<Long, ContaminatedBlock> contaminated = new LinkedHashMap<>();

    public static BlockContaminationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(BlockContaminationData::new, BlockContaminationData::load),
            DATA_NAME
        );
    }

    public void shed(BlockPos pos, long strainId) {
        long key = pos.asLong();
        ContaminatedBlock existing = contaminated.get(key);
        if (existing != null) {
            // Add strain if not already present and under cap
            for (long id : existing.strainIds) if (id == strainId) return;
            if (existing.strainIds.length >= MAX_STRAINS_PER_BLOCK) return;
            long[] newIds = Arrays.copyOf(existing.strainIds, existing.strainIds.length + 1);
            newIds[newIds.length - 1] = strainId;
            existing.strainIds = newIds;
            existing.ticksRemaining = DECAY_TICKS; // refresh timer
        } else {
            contaminated.put(key, new ContaminatedBlock(new long[]{strainId}, DECAY_TICKS));
        }
        setDirty();
    }

    public long[] getStrains(BlockPos pos) {
        ContaminatedBlock block = contaminated.get(pos.asLong());
        return block != null ? block.strainIds : new long[0];
    }

    public boolean isContaminated(BlockPos pos) {
        return contaminated.containsKey(pos.asLong());
    }

    public void tick() {
        boolean changed = false;
        Iterator<Map.Entry<Long, ContaminatedBlock>> it = contaminated.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, ContaminatedBlock> entry = it.next();
            entry.getValue().ticksRemaining -= 20; // called once per second
            if (entry.getValue().ticksRemaining <= 0) {
                it.remove();
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, ContaminatedBlock> entry : contaminated.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putLong("Pos", entry.getKey());
            e.putLongArray("Strains", entry.getValue().strainIds);
            e.putInt("Ticks", entry.getValue().ticksRemaining);
            list.add(e);
        }
        tag.put("Blocks", list);
        return tag;
    }

    public static BlockContaminationData load(CompoundTag tag, HolderLookup.Provider reg) {
        BlockContaminationData data = new BlockContaminationData();
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            long pos = e.getLong("Pos");
            long[] strains = e.getLongArray("Strains");
            int ticks = e.getInt("Ticks");
            data.contaminated.put(pos, new ContaminatedBlock(strains, ticks));
        }
        return data;
    }
}
