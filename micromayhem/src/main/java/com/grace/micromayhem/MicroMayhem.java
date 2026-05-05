package com.grace.micromayhem;

import com.grace.micromayhem.registry.ModBlockEntities;
import com.grace.micromayhem.registry.ModBlocks;
import com.grace.micromayhem.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MicroMayhem.MODID)
public class MicroMayhem {

    public static final String MODID = "micromayhem";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MicroMayhem(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        LOGGER.info("[MicroMayhem] Registered all mod content.");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[MicroMayhem] Common setup complete.");
    }
}
