package com.grace.annihilatetnt.block;

import com.grace.annihilatetnt.entity.PrimedAnnihilateTNT;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class AnnihilateTNTBlock extends Block {

    public static final BooleanProperty UNSTABLE = BlockStateProperties.UNSTABLE;

    private final int radius;

    public AnnihilateTNTBlock(int radius) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.FIRE)
                .instrument(NoteBlockInstrument.BASS)
                .strength(0.0F)
                .sound(SoundType.GRASS)
                .ignitedByLava());
        this.radius = radius;
        this.registerDefaultState(this.stateDefinition.any().setValue(UNSTABLE, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UNSTABLE);
    }

    public int getRadius() {
        return radius;
    }

    private void primeTNT(Level level, BlockPos pos, @javax.annotation.Nullable LivingEntity igniter) {
        if (!level.isClientSide()) {
            PrimedAnnihilateTNT entity = new PrimedAnnihilateTNT(
                    level,
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5,
                    igniter,
                    radius
            );
            level.addFreshEntity(entity);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        }
    }

    // Redstone activation
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            if (level.hasNeighborSignal(pos)) {
                primeTNT(level, pos, null);
                level.removeBlock(pos, false);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.hasNeighborSignal(pos)) {
            primeTNT(level, pos, null);
            level.removeBlock(pos, false);
        }
    }

    // Explosion chain
    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide()) {
            PrimedAnnihilateTNT entity = new PrimedAnnihilateTNT(
                    level,
                    pos.getX() + 0.5,
                    pos.getY(),
                    pos.getZ() + 0.5,
                    explosion.getIndirectSourceEntity(),
                    radius
            );
            int fuse = entity.getFuse();
            entity.setFuse((short) (level.random.nextInt(fuse / 4) + fuse / 8));
            level.addFreshEntity(entity);
        }
    }

    // Flint & steel / fire charge activation
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
            primeTNT(level, pos, player);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
            if (!player.isCreative()) {
                if (stack.is(Items.FLINT_AND_STEEL)) {
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                } else {
                    stack.shrink(1);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    // Fire ignition
    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos,
                             @javax.annotation.Nullable net.minecraft.core.Direction face,
                             @javax.annotation.Nullable LivingEntity igniter) {
        primeTNT(level, pos, igniter);
        level.removeBlock(pos, false);
    }

    // Unstable flag: player breaking in survival primes it
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && state.getValue(UNSTABLE)) {
            primeTNT(level, pos, null);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
