package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.IrradiatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class IrradiatorScreen extends AbstractContainerScreen<IrradiatorMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/irradiator.png");

    public IrradiatorScreen(IrradiatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176; this.imageHeight = 202;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 202);
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 52 / menu.getMaxProgress();
            g.fill(leftPos + 60, topPos + 44, leftPos + 60 + filled, topPos + 54, 0xFFFF9900);
            g.fill(leftPos + 60, topPos + 54, leftPos + 112, topPos + 55, 0xFF333333);
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) { super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
