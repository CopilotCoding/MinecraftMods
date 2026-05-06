package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.GeneSplicerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class GeneSplicerScreen extends AbstractContainerScreen<GeneSplicerMenu> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/genesplicer.png");

    public GeneSplicerScreen(GeneSplicerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 216);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 52 / menu.getMaxProgress();
            g.fill(leftPos + 60, topPos + 44, leftPos + 60 + filled, topPos + 54, 0xFF00CCFF);
        }
        super.extractContents(g, mouseX, mouseY, partial);
    }
}
