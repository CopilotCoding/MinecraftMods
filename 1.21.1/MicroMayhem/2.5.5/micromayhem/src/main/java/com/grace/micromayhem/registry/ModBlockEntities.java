package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.blockentity.AutoclaveBlockEntity;
import com.grace.micromayhem.blockentity.CentrifugeBlockEntity;
import com.grace.micromayhem.blockentity.GeneSplicerBlockEntity;
import com.grace.micromayhem.blockentity.IrradiatorBlockEntity;
import com.grace.micromayhem.blockentity.MicroscopeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MicroMayhem.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MicroscopeBlockEntity>> MICROSCOPE =
        BLOCK_ENTITIES.register("microscope", () ->
            BlockEntityType.Builder.of(MicroscopeBlockEntity::new,
                ModBlocks.MICROSCOPE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CentrifugeBlockEntity>> CENTRIFUGE =
        BLOCK_ENTITIES.register("centrifuge", () ->
            BlockEntityType.Builder.of(CentrifugeBlockEntity::new,
                ModBlocks.CENTRIFUGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoclaveBlockEntity>> AUTOCLAVE =
        BLOCK_ENTITIES.register("autoclave", () ->
            BlockEntityType.Builder.of(AutoclaveBlockEntity::new,
                ModBlocks.AUTOCLAVE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IrradiatorBlockEntity>> IRRADIATOR =
        BLOCK_ENTITIES.register("irradiator", () ->
            BlockEntityType.Builder.of(IrradiatorBlockEntity::new,
                ModBlocks.IRRADIATOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneSplicerBlockEntity>> GENE_SPLICER =
        BLOCK_ENTITIES.register("gene_splicer", () ->
            BlockEntityType.Builder.of(GeneSplicerBlockEntity::new,
                ModBlocks.GENE_SPLICER.get()).build(null));
}
