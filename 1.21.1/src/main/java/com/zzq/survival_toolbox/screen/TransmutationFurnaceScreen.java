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

    private static final ResourceLocation GUI = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/blast_furnace.png");
    // 1.21.1 中火焰图标从 blast_furnace.png 里拆出来，改成了独立 sprite：
    // textures/gui/sprites/container/blast_furnace/lit_progress.png
    private static final ResourceLocation LIT_PROGRESS_SPRITE = ResourceLocation.withDefaultNamespace("container/blast_furnace/lit_progress");

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

        // ---- 燃烧进度条（火焰随燃烧剩余时间缩小）----
        // 与原版高炉一致：火焰用独立 sprite 绘制，从底部往上画 flameHeight 像素。
        if (menu.isBurning()) {
            int progress = menu.getBurnProgress();  // 0~13（燃烧剩余时间）
            int flameHeight = progress + 1;          // 1~14
            guiGraphics.blitSprite(LIT_PROGRESS_SPRITE, 14, 14, 0, 14 - flameHeight,
                    x + 56, y + 36 + 14 - flameHeight, 14, flameHeight);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}
