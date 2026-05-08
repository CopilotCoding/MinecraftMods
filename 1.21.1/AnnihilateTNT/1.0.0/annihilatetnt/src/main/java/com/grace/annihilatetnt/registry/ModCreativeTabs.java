package com.grace.annihilatetnt.registry;

import com.grace.annihilatetnt.AnnihilateTNT;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AnnihilateTNT.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ANNIHILATE_TAB =
            CREATIVE_MODE_TABS.register("annihilatetnt_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.annihilatetnt"))
                            .icon(() -> ModItems.TNT_64X.get().getDefaultInstance())
                            .displayItems((parameters, output) -> {
                                output.accept(ModItems.TNT_2X.get());
                                output.accept(ModItems.TNT_4X.get());
                                output.accept(ModItems.TNT_8X.get());
                                output.accept(ModItems.TNT_16X.get());
                                output.accept(ModItems.TNT_32X.get());
                                output.accept(ModItems.TNT_64X.get());
                            })
                            .build()
            );
}
