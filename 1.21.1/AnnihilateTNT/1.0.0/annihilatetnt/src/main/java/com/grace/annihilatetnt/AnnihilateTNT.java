package com.grace.annihilatetnt;

import com.grace.annihilatetnt.registry.ModBlocks;
import com.grace.annihilatetnt.registry.ModCreativeTabs;
import com.grace.annihilatetnt.registry.ModEntityTypes;
import com.grace.annihilatetnt.registry.ModItems;
import com.grace.annihilatetnt.saveddata.PendingExplosionSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(AnnihilateTNT.MODID)
public class AnnihilateTNT {

    public static final String MODID = "annihilatetnt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AnnihilateTNT(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntityTypes.ENTITY_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(AnnihilateTNTClient::registerRenderers);
        }

        NeoForge.EVENT_BUS.register(this);

        LOGGER.info("AnnihilateTNT loaded. Prepare for consequences.");
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Only tick the overworld-equivalent dimension data (each dimension has its own SavedData)
        PendingExplosionSavedData data = PendingExplosionSavedData.get(serverLevel);
        if (data.hasPendingExplosions()) {
            data.tick(serverLevel);
        }
    }
}
