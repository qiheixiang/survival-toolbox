package com.zzq.survival_toolbox.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * 锤炼箱界面
 * <p>
 * 上方 8 格自适应装备，下方 4 格被捕捉实体，标准背包区。
 * </p>
 */
public class TemperingBoxScreen extends AbstractContainerScreen<TemperingBoxMenu> {

    // 原版构造器：fromNamespaceAndPath 需要 Forge ≥ 47.3.19（见 SurvivalToolbox#CHANNEL）
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("zzq_survival_toolbox", "textures/gui/tempering_box.png");

    public TemperingBoxScreen(TemperingBoxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 190;
        this.inventoryLabelY = 88;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        gui.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 176, 190);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // 两个分区标题（灰色）
        gui.drawString(this.font, Component.translatable("gui.zzq_survival_toolbox.tempering_box.armor"),
                44, 6, 4210752, false);
        gui.drawString(this.font, Component.translatable("gui.zzq_survival_toolbox.tempering_box.entity"),
                44, 56, 4210752, false);
        gui.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }
}
