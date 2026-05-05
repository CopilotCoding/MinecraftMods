package com.grace.micromayhem.block;

import com.grace.micromayhem.blockentity.MicroscopeBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MicroscopeBlock extends BaseEntityBlock {

    public static final MapCodec<MicroscopeBlock> CODEC = simpleCodec(MicroscopeBlock::new);

    public MicroscopeBlock(Properties props) { super(props); }

    @Override
    public MapCodec<MicroscopeBlock> codec() { return CODEC; }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicroscopeBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MicroscopeBlockEntity machine) {
            player.openMenu(machine);
        }
        return InteractionResult.CONSUME;
    }
}
