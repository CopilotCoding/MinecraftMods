package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/centrifuge.png");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176; this.imageHeight = 184;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 176, 184);
        // Progress bar: x=60,y=37 width=52 — fills left to right
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 44 / menu.getMaxProgress();
            g.fill(leftPos + 55, topPos + 37, leftPos + 55 + filled, topPos + 47, 0xFF55FF55);
            g.fill(leftPos + 55, topPos + 47, leftPos + 99, topPos + 48, 0xFF333333);
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float partial) { super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
