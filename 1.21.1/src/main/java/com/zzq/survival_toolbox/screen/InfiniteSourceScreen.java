package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 无限之源 GUI 界面
 * <p>
 * 单槽位显示标记物，并展示当前绑定的流体类型名称。
 * </p>
 */
public class InfiniteSourceScreen extends AbstractContainerScreen<InfiniteSourceMenu> {

    private final InfiniteSourceBlockEntity be;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    public InfiniteSourceScreen(InfiniteSourceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.be = menu.getBlockEntity();
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;

        gui.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xFFC6C6C6);
        gui.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFF8B8B8B);

        gui.drawString(font, this.title, left + 8, top + 4, 0x404040, false);

        // ---- 槽位标签 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.infinite_source.slot_label"),
                left + 8, top + 26, 0x404040, false);

        // ---- 单个槽位 ----
        drawSlot(gui, left + 80, top + 35);

        // ---- 状态信息 ----
        String status = be.hasFluid() ?
                Component.translatable("gui.zzq_survival_toolbox.infinite_source.fluid",
                        be.getStoredFluid().getDisplayName().getString()).getString() :
                Component.translatable("gui.zzq_survival_toolbox.infinite_source.empty").getString();
        gui.drawString(font, status, left + 8, top + 60, 0x404040, false);

        // ---- 玩家背包 ----
        gui.drawString(font, Component.translatable("container.inventory"), left + 8, top + 74, 0x404040, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(gui, left + 8 + col * 18, top + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(gui, left + 8 + col * 18, top + 142);
        }
    }

    private void drawSlot(GuiGraphics gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        gui.fill(x, y, x + 18, y + 1, 0xFFFFFFFF);
        gui.fill(x, y, x + 1, y + 18, 0xFFFFFFFF);
        gui.fill(x + 17, y, x + 18, y + 18, 0xFF555555);
        gui.fill(x, y + 17, x + 18, y + 18, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }
}
