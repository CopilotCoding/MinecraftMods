package com.grace.annihilatetnt.registry;

import com.grace.annihilatetnt.AnnihilateTNT;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AnnihilateTNT.MODID);

    public static final DeferredItem<BlockItem> TNT_2X  = ITEMS.registerSimpleBlockItem("tnt_2x",  ModBlocks.TNT_2X);
    public static final DeferredItem<BlockItem> TNT_4X  = ITEMS.registerSimpleBlockItem("tnt_4x",  ModBlocks.TNT_4X);
    public static final DeferredItem<BlockItem> TNT_8X  = ITEMS.registerSimpleBlockItem("tnt_8x",  ModBlocks.TNT_8X);
    public static final DeferredItem<BlockItem> TNT_16X = ITEMS.registerSimpleBlockItem("tnt_16x", ModBlocks.TNT_16X);
    public static final DeferredItem<BlockItem> TNT_32X = ITEMS.registerSimpleBlockItem("tnt_32x", ModBlocks.TNT_32X);
    public static final DeferredItem<BlockItem> TNT_64X = ITEMS.registerSimpleBlockItem("tnt_64x", ModBlocks.TNT_64X);
}
