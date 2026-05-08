package com.grace.ezenchant.mixin;

import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EnchantmentMenu.class)
public interface EnchantmentMenuAccessor {
    @Accessor("enchantClue")
    int[] ezenchant$getEnchantClue();

    @Accessor("levelClue")
    int[] ezenchant$getLevelClue();
}
