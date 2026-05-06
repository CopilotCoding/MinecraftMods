package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.block.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(MicroMayhem.MODID);

    public static final DeferredBlock<MicroscopeBlock> MICROSCOPE =
        BLOCKS.registerBlock("microscope", MicroscopeBlock::new, () ->
            BlockBehaviour.Properties.of().strength(2.0f, 6.0f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE =
        BLOCKS.registerBlock("centrifuge", CentrifugeBlock::new, () ->
            BlockBehaviour.Properties.of().strength(3.0f, 8.0f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<AutoclaveBlock> AUTOCLAVE =
        BLOCKS.registerBlock("autoclave", AutoclaveBlock::new, () ->
            BlockBehaviour.Properties.of().strength(3.5f, 10.0f).sound(SoundType.METAL).noOcclusion());

    public static final DeferredBlock<IrradiatorBlock> IRRADIATOR =
        BLOCKS.registerBlock("irradiator", IrradiatorBlock::new, () ->
            BlockBehaviour.Properties.of().strength(3.0f, 8.0f).sound(SoundType.METAL).lightLevel(s -> 7).noOcclusion());

    public static final DeferredBlock<GeneSplicerBlock> GENE_SPLICER =
        BLOCKS.registerBlock("gene_splicer", GeneSplicerBlock::new, () ->
            BlockBehaviour.Properties.of().strength(3.0f, 8.0f).sound(SoundType.METAL).noOcclusion());
}
