package com.grace.micromayhem.client.screen;

import com.grace.micromayhem.MicroMayhem;
import com.grace.micromayhem.menu.IrradiatorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class IrradiatorScreen extends AbstractContainerScreen<IrradiatorMenu> {

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath(MicroMayhem.MODID, "textures/gui/irradiator.png");

    public IrradiatorScreen(IrradiatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 202);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0f, 0f, imageWidth, imageHeight, imageWidth, imageHeight);
        if (menu.getMaxProgress() > 0) {
            int filled = menu.getProgress() * 52 / menu.getMaxProgress();
            g.fill(leftPos + 60, topPos + 44, leftPos + 60 + filled, topPos + 54, 0xFFFF9900);
        }
        super.extractContents(g, mouseX, mouseY, partial);
    }
}
