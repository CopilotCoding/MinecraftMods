package com.grace.micromayhem.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.function.Consumer;

import java.util.List;

/**
 * Nutrient Agar — a growth medium required to transfer a culture to a new Petri Dish.
 * Crafted from: mushroom + sugar + bowl (shapeless).
 * Consumed one per transfer.
 */
public class NutrientAgarItem extends Item {

    public NutrientAgarItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
        lines.accept(Component.literal("§7Growth medium for microbial culture transfer."));
        lines.accept(Component.literal("§7Hold a ready Petri Dish in main hand,"));
        lines.accept(Component.literal("§7empty Petri Dish in off-hand, Nutrient Agar in inventory."));
        lines.accept(Component.literal("§7Right-click to duplicate the culture."));
    }
}
