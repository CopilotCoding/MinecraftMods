package com.grace.micromayhem.item;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Armor material for the Hazmat Suit.
 * Low physical protection — purely biological defense.
 * Wrapped in Holder as required by 1.21.1 ArmorItem constructor.
 */
public class HazmatArmorMaterial {

    public static final Holder<ArmorMaterial> HAZMAT = Holder.direct(new ArmorMaterial(
        buildDefenseMap(),
        5,
        SoundEvents.ARMOR_EQUIP_LEATHER,
        () -> Ingredient.of(net.minecraft.world.item.Items.SLIME_BALL),
        List.of(new ArmorMaterial.Layer(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("micromayhem", "hazmat")
        )),
        0f,
        0f
    ));

    private static Map<ArmorItem.Type, Integer> buildDefenseMap() {
        Map<ArmorItem.Type, Integer> map = new EnumMap<>(ArmorItem.Type.class);
        map.put(ArmorItem.Type.HELMET,     1);
        map.put(ArmorItem.Type.CHESTPLATE, 2);
        map.put(ArmorItem.Type.LEGGINGS,   1);
        map.put(ArmorItem.Type.BOOTS,      1);
        return map;
    }
}
