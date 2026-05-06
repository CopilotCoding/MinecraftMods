package com.grace.micromayhem.block;

import com.grace.micromayhem.blockentity.IrradiatorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import com.grace.micromayhem.registry.ModBlockEntities;

public class IrradiatorBlock extends BaseEntityBlock {

    public static final MapCodec<IrradiatorBlock> CODEC = simpleCodec(IrradiatorBlock::new);

    public IrradiatorBlock(Properties props) { super(props); }

    @Override
    public MapCodec<IrradiatorBlock> codec() { return CODEC; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IrradiatorBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof IrradiatorBlockEntity irradiator) {
            player.openMenu(irradiator);
        }
        return InteractionResult.CONSUME;
    }

    /** Server-side ticker — advances the irradiation cycle each tick. */
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.IRRADIATOR.get(),
            IrradiatorBlockEntity::tick);
    }
}
