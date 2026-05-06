package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.CentrifugeMenu;
import com.grace.micromayhem.menu.GeneSplicerMenu;
import com.grace.micromayhem.menu.IrradiatorMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, MicroMayhem.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<CentrifugeMenu>> CENTRIFUGE =
        MENUS.register("centrifuge", () ->
            IMenuTypeExtension.create(CentrifugeMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<IrradiatorMenu>> IRRADIATOR =
        MENUS.register("irradiator", () ->
            IMenuTypeExtension.create(IrradiatorMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<GeneSplicerMenu>> GENE_SPLICER =
        MENUS.register("gene_splicer", () ->
            IMenuTypeExtension.create(GeneSplicerMenu::new));
}
