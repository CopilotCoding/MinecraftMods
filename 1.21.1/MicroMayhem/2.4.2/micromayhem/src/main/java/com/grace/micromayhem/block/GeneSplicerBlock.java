package com.grace.micromayhem.block;

import com.grace.micromayhem.blockentity.GeneSplicerBlockEntity;
import com.grace.micromayhem.registry.ModBlockEntities;
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

public class GeneSplicerBlock extends BaseEntityBlock {
    public static final MapCodec<GeneSplicerBlock> CODEC = simpleCodec(GeneSplicerBlock::new);
    public GeneSplicerBlock(Properties props) { super(props); }
    @Override public MapCodec<GeneSplicerBlock> codec() { return CODEC; }
    @Override public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GeneSplicerBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof GeneSplicerBlockEntity machine) player.openMenu(machine);
        return InteractionResult.CONSUME;
    }
    @Override public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.GENE_SPLICER.get(), GeneSplicerBlockEntity::tick);
    }
}
