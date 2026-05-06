package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.GeneSplicerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class GeneSplicerScreen extends AbstractContainerScreen<GeneSplicerMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/genesplicer.png");

    public GeneSplicerScreen(GeneSplicerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176; this.imageHeight = 216;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 216);
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 52 / menu.getMaxProgress();
            g.fill(leftPos + 60, topPos + 44, leftPos + 60 + filled, topPos + 54, 0xFF00CCFF);
            g.fill(leftPos + 60, topPos + 54, leftPos + 112, topPos + 55, 0xFF333333);
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) { super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
