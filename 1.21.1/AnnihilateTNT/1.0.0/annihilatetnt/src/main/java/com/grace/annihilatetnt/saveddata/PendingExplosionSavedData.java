package com.grace.annihilatetnt.saveddata;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PendingExplosionSavedData extends SavedData {

    private static final String DATA_NAME = "annihilatetnt_pending_explosions";

    private static final int WRITES_PER_TICK = 100_000;

    public static class PendingExplosion {
        public final BlockPos origin;
        public final int radius;
        // Full 3D cursor — resumes exactly where it left off including mid-column
        public int curX, curZ, curY;
        public final int minX, maxX, minY, maxY, minZ, maxZ;
        public boolean done = false;

        public PendingExplosion(BlockPos origin, int radius, int worldMinY, int worldMaxY) {
            this.origin = origin;
            this.radius = radius;
            this.minX = origin.getX() - radius;
            this.maxX = origin.getX() + radius;
            this.minY = Math.max(worldMinY, origin.getY() - radius);
            this.maxY = Math.min(worldMaxY, origin.getY() + radius);
            this.minZ = origin.getZ() - radius;
            this.maxZ = origin.getZ() + radius;
            this.curX = minX;
            this.curZ = minZ;
            this.curY = this.minY; // will be overridden to colMinY on first column anyway
        }

        public PendingExplosion(BlockPos origin, int radius,
                                int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                int curX, int curZ, int curY) {
            this.origin = origin;
            this.radius = radius;
            this.minX = minX; this.maxX = maxX;
            this.minY = minY; this.maxY = maxY;
            this.minZ = minZ; this.maxZ = maxZ;
            this.curX = curX;
            this.curZ = curZ;
            this.curY = curY;
        }

        public int processTick(ServerLevel level, int writeBudget) {
            int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
            long radiusSq = (long) radius * radius;
            int writes = 0;

            BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();

            outer:
            for (int x = curX; x <= maxX; x++) {
                int startZ = (x == curX) ? curZ : minZ;
                for (int z = startZ; z <= maxZ; z++) {
                    long dx = x - ox, dz = z - oz;
                    long xzSq = dx * dx + dz * dz;

                    if (xzSq > radiusSq) continue;

                    // Exact Y range for this column
                    int dyMax = (int) Math.floor(Math.sqrt((double)(radiusSq - xzSq)));
                    int colMinY = Math.max(minY, oy - dyMax);
                    int colMaxY = Math.min(maxY, oy + dyMax);

                    // Resume mid-column if this is the saved cursor column, else start from top
                    int startY = (x == curX && z == curZ) ? Math.max(curY, colMinY) : colMinY;

                    for (int y = startY; y <= colMaxY; y++) {
                        mPos.set(x, y, z);
                        if (level.isLoaded(mPos)) {
                            BlockState state = level.getBlockState(mPos);
                            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                                level.setBlock(mPos, Blocks.AIR.defaultBlockState(), 3);
                                writes++;
                                if (writes >= writeBudget) {
                                    // Save exact position — resume at next Y in this column
                                    curX = x;
                                    curZ = z;
                                    curY = y + 1;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }

            if (curX > maxX) {
                done = true;
            }

            return writes;
        }
    }

    private final List<PendingExplosion> pendingExplosions = new ArrayList<>();

    public PendingExplosionSavedData() {}

    public static PendingExplosionSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        PendingExplosionSavedData::new,
                        PendingExplosionSavedData::load
                ),
                DATA_NAME
        );
    }

    public void addExplosion(BlockPos origin, int radius, ServerLevel level) {
        pendingExplosions.add(new PendingExplosion(origin, radius,
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1));
        setDirty();
    }

    public void tick(ServerLevel level) {
        if (pendingExplosions.isEmpty()) return;

        int budget = WRITES_PER_TICK;
        Iterator<PendingExplosion> iter = pendingExplosions.iterator();

        while (iter.hasNext() && budget > 0) {
            PendingExplosion exp = iter.next();
            int written = exp.processTick(level, budget);
            budget -= written;
            if (exp.done) {
                iter.remove();
            }
        }

        setDirty();
    }

    public boolean hasPendingExplosions() {
        return !pendingExplosions.isEmpty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (PendingExplosion exp : pendingExplosions) {
            CompoundTag e = new CompoundTag();
            e.putInt("OriginX", exp.origin.getX());
            e.putInt("OriginY", exp.origin.getY());
            e.putInt("OriginZ", exp.origin.getZ());
            e.putInt("Radius", exp.radius);
            e.putInt("MinX", exp.minX); e.putInt("MaxX", exp.maxX);
            e.putInt("MinY", exp.minY); e.putInt("MaxY", exp.maxY);
            e.putInt("MinZ", exp.minZ); e.putInt("MaxZ", exp.maxZ);
            e.putInt("CurX", exp.curX);
            e.putInt("CurZ", exp.curZ);
            e.putInt("CurY", exp.curY);
            list.add(e);
        }
        tag.put("Explosions", list);
        return tag;
    }

    public static PendingExplosionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PendingExplosionSavedData data = new PendingExplosionSavedData();
        ListTag list = tag.getList("Explosions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            BlockPos origin = new BlockPos(e.getInt("OriginX"), e.getInt("OriginY"), e.getInt("OriginZ"));
            data.pendingExplosions.add(new PendingExplosion(
                    origin, e.getInt("Radius"),
                    e.getInt("MinX"), e.getInt("MaxX"),
                    e.getInt("MinY"), e.getInt("MaxY"),
                    e.getInt("MinZ"), e.getInt("MaxZ"),
                    e.getInt("CurX"), e.getInt("CurZ"), e.getInt("CurY")
            ));
        }
        return data;
    }
}
