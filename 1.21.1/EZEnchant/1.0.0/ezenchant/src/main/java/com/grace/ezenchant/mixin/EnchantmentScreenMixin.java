package com.grace.ezenchant.mixin;

import com.grace.ezenchant.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {

    // Satisfy the compiler — this constructor is never called at runtime
    protected EnchantmentScreenMixin() {
        super(null, null, null);
    }

    @Inject(method = "renderBg", at = @At("TAIL"), remap = false)
    private void ezenchant$revealEnchantments(GuiGraphics graphics, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // Access menu directly via the inherited field — no @Shadow needed
        EnchantmentMenu enchMenu = this.menu;
        int[] enchantClue = ((EnchantmentMenuAccessor) enchMenu).ezenchant$getEnchantClue();
        int[] levelClue   = ((EnchantmentMenuAccessor) enchMenu).ezenchant$getLevelClue();

        Registry<Enchantment> enchantRegistry = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        List<Holder.Reference<Enchantment>> holders = enchantRegistry.holders().toList();

        var font = mc.font;

        for (int slot = 0; slot < 3; slot++) {
            int clueId = enchantClue[slot];
            int level  = levelClue[slot];

            if (clueId < 0 || level < 0) continue;
            if (clueId >= holders.size()) continue;

            Holder.Reference<Enchantment> holder = holders.get(clueId);
            Component fullName = Enchantment.getFullname(holder, level);

            int x = this.leftPos + 60;
            int y = this.topPos + 14 + slot * 19;

            graphics.fill(x - 2, y - 1, x + 108, y + 10, 0xAA000000);

            int color;
            if (holder.is(EnchantmentTags.CURSE)) {
                color = 0xFF5555;
            } else if (Config.HIGHLIGHT_TREASURE.getAsBoolean() && holder.is(EnchantmentTags.TREASURE)) {
                color = 0xFFAA00;
            } else {
                color = 0xFFFFFF;
            }

            String nameStr = font.plainSubstrByWidth(fullName.getString(), 106);
            graphics.drawString(font, nameStr, x, y, color, true);
        }
    }
}
