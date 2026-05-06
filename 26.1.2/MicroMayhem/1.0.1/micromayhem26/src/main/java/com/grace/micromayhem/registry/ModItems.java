package com.grace.micromayhem.registry;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.item.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(MicroMayhem.MODID);

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MicroMayhem.MODID);

    public static final DeferredItem<NutrientAgarItem> NUTRIENT_AGAR =
        ITEMS.registerItem("nutrient_agar", NutrientAgarItem::new, p -> p.stacksTo(16));

    public static final DeferredItem<SampleBagItem> SAMPLE_BAG =
        ITEMS.registerItem("sample_bag", SampleBagItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<DebugInfectSyringe> DEBUG_INFECT_SYRINGE =
        ITEMS.registerItem("debug_infect_syringe", DebugInfectSyringe::new, p -> p.stacksTo(1));

    public static final DeferredItem<HazmatArmorItem> HAZMAT_HELMET =
        ITEMS.registerItem("hazmat_helmet", p -> new HazmatArmorItem(ArmorType.HELMET, p),
            p -> p.humanoidArmor(com.grace.micromayhem.item.HazmatArmorMaterial.HAZMAT.value(), ArmorType.HELMET));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_CHESTPLATE =
        ITEMS.registerItem("hazmat_chestplate", p -> new HazmatArmorItem(ArmorType.CHESTPLATE, p),
            p -> p.humanoidArmor(com.grace.micromayhem.item.HazmatArmorMaterial.HAZMAT.value(), ArmorType.CHESTPLATE));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_LEGGINGS =
        ITEMS.registerItem("hazmat_leggings", p -> new HazmatArmorItem(ArmorType.LEGGINGS, p),
            p -> p.humanoidArmor(com.grace.micromayhem.item.HazmatArmorMaterial.HAZMAT.value(), ArmorType.LEGGINGS));
    public static final DeferredItem<HazmatArmorItem> HAZMAT_BOOTS =
        ITEMS.registerItem("hazmat_boots", p -> new HazmatArmorItem(ArmorType.BOOTS, p),
            p -> p.humanoidArmor(com.grace.micromayhem.item.HazmatArmorMaterial.HAZMAT.value(), ArmorType.BOOTS));

    public static final DeferredItem<SwabItem> SWAB =
        ITEMS.registerItem("swab", SwabItem::new, p -> p.stacksTo(16));

    public static final DeferredItem<PetriDishItem> PETRI_DISH =
        ITEMS.registerItem("petri_dish", PetriDishItem::new, p -> p.stacksTo(16));

    public static final DeferredItem<SyringeItem> SYRINGE =
        ITEMS.registerItem("syringe", SyringeItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<VaccineSyringeItem> VACCINE_SYRINGE =
        ITEMS.registerItem("vaccine_syringe", VaccineSyringeItem::new, p -> p.stacksTo(1));

    public static final DeferredItem<AntibioticItem> ANTIBIOTIC =
        ITEMS.registerItem("antibiotic", p -> new AntibioticItem(AntibioticItem.TreatmentType.ANTIBIOTIC, p), p -> p.stacksTo(16));
    public static final DeferredItem<AntibioticItem> ANTIVIRAL =
        ITEMS.registerItem("antiviral", p -> new AntibioticItem(AntibioticItem.TreatmentType.ANTIVIRAL, p), p -> p.stacksTo(16));
    public static final DeferredItem<AntibioticItem> ANTIFUNGAL =
        ITEMS.registerItem("antifungal", p -> new AntibioticItem(AntibioticItem.TreatmentType.ANTIFUNGAL, p), p -> p.stacksTo(16));
    public static final DeferredItem<AntibioticItem> ANTIPARASITIC =
        ITEMS.registerItem("antiparasitic", p -> new AntibioticItem(AntibioticItem.TreatmentType.ANTIPARASITIC, p), p -> p.stacksTo(16));

    public static final DeferredItem<BlockItem> MICROSCOPE_ITEM =
        ITEMS.registerSimpleBlockItem(ModBlocks.MICROSCOPE);
    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM =
        ITEMS.registerSimpleBlockItem(ModBlocks.CENTRIFUGE);
    public static final DeferredItem<BlockItem> AUTOCLAVE_ITEM =
        ITEMS.registerSimpleBlockItem(ModBlocks.AUTOCLAVE);
    public static final DeferredItem<BlockItem> IRRADIATOR_ITEM =
        ITEMS.registerSimpleBlockItem(ModBlocks.IRRADIATOR);
    public static final DeferredItem<BlockItem> GENE_SPLICER_ITEM =
        ITEMS.registerSimpleBlockItem(ModBlocks.GENE_SPLICER);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MICRO_TAB =
        TABS.register("micromayhem_tab", () -> CreativeModeTab.builder()
            .title(Component.literal("MicroMayhem"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SWAB.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(SWAB.get());
                output.accept(NUTRIENT_AGAR.get());
                output.accept(SAMPLE_BAG.get());
                output.accept(DEBUG_INFECT_SYRINGE.get());
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
