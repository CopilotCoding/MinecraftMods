package com.grace.micromayhem;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = MicroMayhem.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MicroMayhem.MODID, value = Dist.CLIENT)
public class MicroMayhemClient {

    public MicroMayhemClient(ModContainer container) {
        // Client-side setup only
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        MicroMayhem.LOGGER.info("[MicroMayhem] Client setup complete.");
    }
}
