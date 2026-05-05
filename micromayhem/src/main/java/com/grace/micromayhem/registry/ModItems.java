package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(MicroMayhem.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MicroMayhem.MODID);

    public static final DeferredItem<NutrientAgarItem> NUTRIENT_AGAR =
        ITEMS.register("nutrient_agar", () -> new NutrientAgarItem(
            new Item.Properties().stacksTo(16)
        ));

    public static final DeferredItem<SampleBagItem> SAMPLE_BAG =
        ITEMS.register("sample_bag", () -> new SampleBagItem(
            new Item.Properties().stacksTo(1)
        ));

    // ---- Hazmat Suit ----
    public static final DeferredItem<HazmatArmorItem> HAZMAT_HELMET =
        ITEMS.register("hazmat_helmet", () -> new HazmatArmorItem(
            ArmorItem.Type.HELMET, new Item.Properties().durability(240)
        ));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_CHESTPLATE =
        ITEMS.register("hazmat_chestplate", () -> new HazmatArmorItem(
            ArmorItem.Type.CHESTPLATE, new Item.Properties().durability(320)
        ));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_LEGGINGS =
        ITEMS.register("hazmat_leggings", () -> new HazmatArmorItem(
            ArmorItem.Type.LEGGINGS, new Item.Properties().durability(300)
        ));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_BOOTS =
        ITEMS.register("hazmat_boots", () -> new HazmatArmorItem(
            ArmorItem.Type.BOOTS, new Item.Properties().durability(260)
        ));

    // ---- Sampling Tools ----
    public static final DeferredItem<SwabItem> SWAB =
        ITEMS.register("swab", () -> new SwabItem(
            new Item.Properties().stacksTo(16)
        ));

    public static final DeferredItem<PetriDishItem> PETRI_DISH =
        ITEMS.register("petri_dish", () -> new PetriDishItem(
            new Item.Properties().stacksTo(1)
        ));

    // ---- Syringes ----
    public static final DeferredItem<SyringeItem> SYRINGE =
        ITEMS.register("syringe", () -> new SyringeItem(
            new Item.Properties().stacksTo(1)
        ));

    public static final DeferredItem<VaccineSyringeItem> VACCINE_SYRINGE =
        ITEMS.register("vaccine_syringe", () -> new VaccineSyringeItem(
            new Item.Properties().stacksTo(1)
        ));

    // ---- Antibiotics ----
    public static final DeferredItem<AntibioticItem> ANTIBIOTIC =
        ITEMS.register("antibiotic", () -> new AntibioticItem(
            AntibioticItem.TreatmentType.ANTIBIOTIC,
            new Item.Properties().stacksTo(16)
        ));

    public static final DeferredItem<AntibioticItem> ANTIVIRAL =
        ITEMS.register("antiviral", () -> new AntibioticItem(
            AntibioticItem.TreatmentType.ANTIVIRAL,
            new Item.Properties().stacksTo(16)
        ));

    public static final DeferredItem<AntibioticItem> ANTIFUNGAL =
        ITEMS.register("antifungal", () -> new AntibioticItem(
            AntibioticItem.TreatmentType.ANTIFUNGAL,
            new Item.Properties().stacksTo(16)
        ));

    public static final DeferredItem<AntibioticItem> ANTIPARASITIC =
        ITEMS.register("antiparasitic", () -> new AntibioticItem(
            AntibioticItem.TreatmentType.ANTIPARASITIC,
            new Item.Properties().stacksTo(16)
        ));

    // ---- Block Items ----
    public static final DeferredItem<BlockItem> MICROSCOPE_ITEM =
        ITEMS.registerSimpleBlockItem("microscope", ModBlocks.MICROSCOPE);

    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM =
        ITEMS.registerSimpleBlockItem("centrifuge", ModBlocks.CENTRIFUGE);

    public static final DeferredItem<BlockItem> AUTOCLAVE_ITEM =
        ITEMS.registerSimpleBlockItem("autoclave", ModBlocks.AUTOCLAVE);

    public static final DeferredItem<BlockItem> IRRADIATOR_ITEM =
        ITEMS.registerSimpleBlockItem("irradiator", ModBlocks.IRRADIATOR);

    public static final DeferredItem<BlockItem> GENE_SPLICER_ITEM =
        ITEMS.registerSimpleBlockItem("gene_splicer", ModBlocks.GENE_SPLICER);

    // ---- Creative Tab ----
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MICRO_TAB =
        TABS.register("micromayhem_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("MicroMayhem"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SWAB.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(SWAB.get());
                output.accept(NUTRIENT_AGAR.get());
                output.accept(SAMPLE_BAG.get());
                output.accept(HAZMAT_HELMET.get());
                output.accept(HAZMAT_CHESTPLATE.get());
                output.accept(HAZMAT_LEGGINGS.get());
                output.accept(HAZMAT_BOOTS.get());
                output.accept(PETRI_DISH.get());
                output.accept(SYRINGE.get());
                output.accept(VACCINE_SYRINGE.get());
                output.accept(ANTIBIOTIC.get());
                output.accept(ANTIVIRAL.get());
                output.accept(ANTIFUNGAL.get());
                output.accept(ANTIPARASITIC.get());
                output.accept(MICROSCOPE_ITEM.get());
                output.accept(CENTRIFUGE_ITEM.get());
                output.accept(AUTOCLAVE_ITEM.get());
                output.accept(IRRADIATOR_ITEM.get());
                output.accept(GENE_SPLICER_ITEM.get());
            })
            .build()
        );
}
