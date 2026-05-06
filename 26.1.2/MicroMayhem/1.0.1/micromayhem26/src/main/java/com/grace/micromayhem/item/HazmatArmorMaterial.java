package com.grace.micromayhem.item;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

public class HazmatArmorMaterial {

    public static final Holder<ArmorMaterial> HAZMAT = Holder.direct(new ArmorMaterial(
        5,
        buildDefenseMap(),
        15,
        SoundEvents.ARMOR_EQUIP_LEATHER,
        0f,
        0f,
        ItemTags.REPAIRS_LEATHER_ARMOR,
        ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath("micromayhem", "hazmat"))
    ));

    private static Map<ArmorType, Integer> buildDefenseMap() {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.HELMET,     1);
        map.put(ArmorType.CHESTPLATE, 2);
        map.put(ArmorType.LEGGINGS,   1);
        map.put(ArmorType.BOOTS,      1);
        return map;
    }
}
