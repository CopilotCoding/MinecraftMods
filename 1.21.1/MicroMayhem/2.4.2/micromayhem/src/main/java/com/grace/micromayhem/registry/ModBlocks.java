package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.block.AutoclaveBlock;
import com.grace.micromayhem.block.CentrifugeBlock;
import com.grace.micromayhem.block.GeneSplicerBlock;
import com.grace.micromayhem.block.IrradiatorBlock;
import com.grace.micromayhem.block.MicroscopeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(MicroMayhem.MODID);

    public static final DeferredBlock<MicroscopeBlock> MICROSCOPE =
        BLOCKS.register("microscope", () -> new MicroscopeBlock(
            BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
        ));

    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE =
        BLOCKS.register("centrifuge", () -> new CentrifugeBlock(
            BlockBehaviour.Properties.of()
                .strength(3.0f, 8.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
        ));

    public static final DeferredBlock<AutoclaveBlock> AUTOCLAVE =
        BLOCKS.register("autoclave", () -> new AutoclaveBlock(
            BlockBehaviour.Properties.of()
                .strength(3.5f, 10.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
        ));

    public static final DeferredBlock<IrradiatorBlock> IRRADIATOR =
        BLOCKS.register("irradiator", () -> new IrradiatorBlock(
            BlockBehaviour.Properties.of()
                .strength(3.0f, 8.0f)
                .sound(SoundType.METAL)
                .lightLevel(s -> 7)   // glows faintly while active
                .noOcclusion()
        ));

    public static final DeferredBlock<GeneSplicerBlock> GENE_SPLICER =
        BLOCKS.register("gene_splicer", () -> new GeneSplicerBlock(
            BlockBehaviour.Properties.of()
                .strength(3.0f, 8.0f)
                .sound(SoundType.METAL)
                .noOcclusion()
        ));
}
