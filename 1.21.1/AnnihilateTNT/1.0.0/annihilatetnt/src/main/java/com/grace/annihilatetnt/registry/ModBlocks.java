package com.grace.annihilatetnt.registry;

import com.grace.annihilatetnt.AnnihilateTNT;
import com.grace.annihilatetnt.block.AnnihilateTNTBlock;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AnnihilateTNT.MODID);

    public static final DeferredBlock<AnnihilateTNTBlock> TNT_2X  = BLOCKS.register("tnt_2x",  () -> new AnnihilateTNTBlock(8));
    public static final DeferredBlock<AnnihilateTNTBlock> TNT_4X  = BLOCKS.register("tnt_4x",  () -> new AnnihilateTNTBlock(16));
    public static final DeferredBlock<AnnihilateTNTBlock> TNT_8X  = BLOCKS.register("tnt_8x",  () -> new AnnihilateTNTBlock(32));
    public static final DeferredBlock<AnnihilateTNTBlock> TNT_16X = BLOCKS.register("tnt_16x", () -> new AnnihilateTNTBlock(64));
    public static final DeferredBlock<AnnihilateTNTBlock> TNT_32X = BLOCKS.register("tnt_32x", () -> new AnnihilateTNTBlock(128));
    public static final DeferredBlock<AnnihilateTNTBlock> TNT_64X = BLOCKS.register("tnt_64x", () -> new AnnihilateTNTBlock(256));
}
