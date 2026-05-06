package com.grace.micromayhem;

import com.grace.micromayhem.client.screen.CentrifugeScreen;
import com.grace.micromayhem.client.screen.GeneSplicerScreen;
import com.grace.micromayhem.client.screen.IrradiatorScreen;
import com.grace.micromayhem.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = MicroMayhem.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MicroMayhem.MODID, value = Dist.CLIENT)
public class MicroMayhemClient {

    public MicroMayhemClient(ModContainer container) {}

    @SubscribeEvent
    static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.CENTRIFUGE.get(),   CentrifugeScreen::new);
        event.register(ModMenuTypes.IRRADIATOR.get(),   IrradiatorScreen::new);
        event.register(ModMenuTypes.GENE_SPLICER.get(), GeneSplicerScreen::new);
        MicroMayhem.LOGGER.info("[MicroMayhem] Client screens registered.");
    }
}
