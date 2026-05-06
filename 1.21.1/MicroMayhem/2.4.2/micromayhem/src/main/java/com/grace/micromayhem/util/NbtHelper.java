package com.grace.micromayhem.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Compatibility shim for ItemStack NBT access in 1.21.1.
 * In 1.21.1, item NBT moved to DataComponents.CUSTOM_DATA.
 */
public class NbtHelper {

    public static boolean hasTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return new CompoundTag();
        return data.copyTag();
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return new CompoundTag();
        return data.copyTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    public static void removeTag(ItemStack stack) {
        stack.remove(DataComponents.CUSTOM_DATA);
    }

    /** Read-modify-write helper. */
    public static void updateTag(ItemStack stack, java.util.function.Consumer<CompoundTag> mutator) {
        CompoundTag tag = getOrCreateTag(stack);
        mutator.accept(tag);
        setTag(stack, tag);
    }
}
