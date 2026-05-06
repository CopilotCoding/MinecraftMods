package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.blockentity.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MicroMayhem.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroscopeBlockEntity>> MICROSCOPE =
        BLOCK_ENTITIES.register("microscope", () ->
            new BlockEntityType<>(MicroscopeBlockEntity::new, false,
                ModBlocks.MICROSCOPE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
        BLOCK_ENTITIES.register("centrifuge", () ->
            new BlockEntityType<>(CentrifugeBlockEntity::new, false,
                ModBlocks.CENTRIFUGE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoclaveBlockEntity>> AUTOCLAVE =
        BLOCK_ENTITIES.register("autoclave", () ->
            new BlockEntityType<>(AutoclaveBlockEntity::new, false,
                ModBlocks.AUTOCLAVE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IrradiatorBlockEntity>> IRRADIATOR =
        BLOCK_ENTITIES.register("irradiator", () ->
            new BlockEntityType<>(IrradiatorBlockEntity::new, false,
                ModBlocks.IRRADIATOR.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneSplicerBlockEntity>> GENE_SPLICER =
        BLOCK_ENTITIES.register("gene_splicer", () ->
            new BlockEntityType<>(GeneSplicerBlockEntity::new, false,
                ModBlocks.GENE_SPLICER.get()));
}
