package com.zzq.survival_toolbox.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 万物转化炉 GUI 界面
 * <p>
 * 复用高炉纹理，显示燃烧进度条。
 * </p>
 */
public class TransmutationFurnaceScreen extends AbstractContainerScreen<TransmutationFurnaceMenu> {

    private static final ResourceLocation GUI = new ResourceLocation("minecraft", "textures/gui/container/blast_furnace.png");

    public TransmutationFurnaceScreen(TransmutationFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // ---- 燃烧进度条 ----
        if (menu.isBurning()) {
            int progress = menu.getBurnProgress();
            guiGraphics.blit(GUI, x + 56, y + 36 + 12 - progress, 176, 12 - progress, 14, progress + 1);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}