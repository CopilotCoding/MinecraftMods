package com.grace.ezenchant;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(EZEnchant.MODID)
public class EZEnchant {
    public static final String MODID = "ezenchant";
    public static final Logger LOGGER = LogUtils.getLogger();

    public EZEnchant(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.info("EZEnchant loaded - no more gambling!");
    }
}
