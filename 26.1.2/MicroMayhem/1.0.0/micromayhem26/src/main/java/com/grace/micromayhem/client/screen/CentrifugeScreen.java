package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/centrifuge.png");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 184);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 44 / menu.getMaxProgress();
            g.fill(leftPos + 55, topPos + 37, leftPos + 55 + filled, topPos + 47, 0xFF55FF55);
        }
        super.extractContents(g, mouseX, mouseY, partial);
    }
}
