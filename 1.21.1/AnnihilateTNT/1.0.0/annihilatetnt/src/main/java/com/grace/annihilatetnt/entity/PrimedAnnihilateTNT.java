package com.grace.annihilatetnt.entity;

import com.grace.annihilatetnt.registry.ModEntityTypes;
import com.grace.annihilatetnt.saveddata.PendingExplosionSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

public class PrimedAnnihilateTNT extends Entity {

    private static final EntityDataAccessor<Integer> DATA_FUSE_ID =
            SynchedEntityData.defineId(PrimedAnnihilateTNT.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RADIUS =
            SynchedEntityData.defineId(PrimedAnnihilateTNT.class, EntityDataSerializers.INT);

    /** Radii above this threshold use lazy tick-based destruction */
    public static final int LAZY_THRESHOLD = 32;

    @Nullable
    private LivingEntity owner;
    private int fuse;
    private int radius;

    public PrimedAnnihilateTNT(EntityType<? extends PrimedAnnihilateTNT> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.fuse = 80;
        this.radius = 8;
    }

    public PrimedAnnihilateTNT(Level level, double x, double y, double z,
                                @Nullable LivingEntity owner, int radius) {
        this(ModEntityTypes.PRIMED_ANNIHILATE_TNT.get(), level);
        this.setPos(x, y, z);
        double angle = level.random.nextDouble() * (Math.PI * 2.0);
        this.setDeltaMovement(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02);
        this.fuse = 80;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.owner = owner;
        this.radius = radius;
        this.entityData.set(DATA_RADIUS, radius);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUSE_ID, 80);
        builder.define(DATA_RADIUS, 8);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.fuse = tag.getShort("Fuse");
        this.radius = tag.getInt("Radius");
        this.entityData.set(DATA_FUSE_ID, this.fuse);
        this.entityData.set(DATA_RADIUS, this.radius);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putShort("Fuse", (short) this.fuse);
        tag.putInt("Radius", this.radius);
    }

    @Nullable
    public LivingEntity getOwner() { return owner; }

    public int getFuse() { return fuse; }

    public void setFuse(short fuse) {
        this.fuse = fuse;
        this.entityData.set(DATA_FUSE_ID, (int) fuse);
    }

    public int getRadius() { return radius; }

    @Override
    public void tick() {
        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04, 0.0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        if (this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.7, -0.5, 0.7));
        }

        this.fuse--;
        this.entityData.set(DATA_FUSE_ID, this.fuse);

        if (this.fuse <= 0) {
            this.discard();
            if (!this.level().isClientSide()) {
                this.doExplode();
            }
        } else {
            this.updateInWaterStateAndDoFluidPushing();
            if (this.level().isClientSide()) {
                this.level().addParticle(ParticleTypes.SMOKE,
                        this.getX(), this.getY() + 0.5, this.getZ(), 0, 0, 0);
            }
        }
    }

    private void doExplode() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        BlockPos origin = BlockPos.containing(this.getX(), this.getY(), this.getZ());

        if (radius <= LAZY_THRESHOLD) {
            destroySphere(serverLevel, origin, radius);
        } else {
            PendingExplosionSavedData savedData = PendingExplosionSavedData.get(serverLevel);
            savedData.addExplosion(origin, radius, serverLevel);
        }
    }

    /**
     * Destroys all non-bedrock, non-air blocks within a perfect sphere of given radius.
     * Called directly for small explosions, and per-chunk for large ones.
     */
    public static void destroySphere(ServerLevel level, BlockPos origin, int radius) {
        long radiusSq = (long) radius * radius;
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        int minY = Math.max(level.getMinBuildHeight(), oy - radius);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, oy + radius);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = oz - radius; z <= oz + radius; z++) {
                    long dx = x - ox, dy = y - oy, dz = z - oz;
                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        mutablePos.set(x, y, z);
                        BlockState state = level.getBlockState(mutablePos);
                        if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                            level.setBlock(mutablePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        return new AABB(getX() - 0.5, getY() - 0.5, getZ() - 0.5,
                        getX() + 0.5, getY() + 0.5, getZ() + 0.5);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }
}
