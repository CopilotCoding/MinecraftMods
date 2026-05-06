package com.grace.micromayhem.microbe;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

/**
 * Dynamic block contamination tracking.
 * Ported to 26.1.2: SavedDataType + Codec replaces old Factory/save override.
 */
public class BlockContaminationData extends SavedData {

    private static final String DATA_NAME = "micromayhem_block_contamination";
    private static final int DECAY_TICKS = 72000;
    private static final int MAX_STRAINS_PER_BLOCK = 3;

    private static class ContaminatedBlock {
        long[] strainIds;
        int ticksRemaining;
        ContaminatedBlock(long[] strainIds, int ticksRemaining) {
            this.strainIds = strainIds;
            this.ticksRemaining = ticksRemaining;
        }
    }

    // Codec encodes each contaminated block as a CompoundTag entry in a list.
    private static final Codec<BlockContaminationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.list(CompoundTag.CODEC).fieldOf("Blocks").forGetter(d -> {
            List<CompoundTag> list = new ArrayList<>();
            for (Map.Entry<Long, ContaminatedBlock> entry : d.contaminated.entrySet()) {
                CompoundTag e = new CompoundTag();
                e.putLong("Pos", entry.getKey());
                e.putLongArray("Strains", entry.getValue().strainIds);
                e.putInt("Ticks", entry.getValue().ticksRemaining);
                list.add(e);
            }
            return list;
        })
    ).apply(instance, blockTags -> {
        BlockContaminationData data = new BlockContaminationData();
        for (CompoundTag e : blockTags) {
            long pos = e.getLongOr("Pos", 0L);
            long[] strains = e.getLongArray("Strains").orElse(new long[0]);
            int ticks = e.getIntOr("Ticks", 0);
            data.contaminated.put(pos, new ContaminatedBlock(strains, ticks));
        }
        return data;
    }));

    public static final SavedDataType<BlockContaminationData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("micromayhem", "block_contamination"),
        BlockContaminationData::new,
        CODEC,
        null
    );

    private final Map<Long, ContaminatedBlock> contaminated = new LinkedHashMap<>();

    public static BlockContaminationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void shed(BlockPos pos, long strainId) {
        long key = pos.asLong();
        ContaminatedBlock existing = contaminated.get(key);
        if (existing != null) {
            for (long id : existing.strainIds) if (id == strainId) return;
            if (existing.strainIds.length >= MAX_STRAINS_PER_BLOCK) return;
            long[] newIds = Arrays.copyOf(existing.strainIds, existing.strainIds.length + 1);
            newIds[newIds.length - 1] = strainId;
            existing.strainIds = newIds;
            existing.ticksRemaining = DECAY_TICKS;
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
            entry.getValue().ticksRemaining -= 20;
            if (entry.getValue().ticksRemaining <= 0) { it.remove(); changed = true; }
        }
        if (changed) setDirty();
    }
}
