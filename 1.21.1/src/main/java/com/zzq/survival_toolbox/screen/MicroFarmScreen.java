package com.zzq.survival_toolbox.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 微型牧场 GUI 界面
 * <p>
 * 显示实体槽（左侧）、食物区（3×3）、存储区（3×9）。
 * 顶部显示剩余时间（秒）。
 * </p>
 */
public class MicroFarmScreen extends AbstractContainerScreen<MicroFarmMenu> {

    public MicroFarmScreen(MicroFarmMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        // ---- 标题 + 时间 ----
        guiGraphics.drawString(font, this.title, x + 8, y + 4, 0x404040, false);
        int remaining = menu.getRemainingSeconds();
        String timeText = Component.translatable("gui.zzq_survival_toolbox.micro_farm.time",
                remaining).getString();
        int titleWidth = font.width(this.title);
        guiGraphics.drawString(font, timeText, x + 8 + titleWidth + 8, y + 4, 0x404040, false);

        // ---- 实体槽 ----
        int entityX = 40;
        int entityY = 20;
        guiGraphics.fill(x + entityX - 2, y + entityY - 2,
                x + entityX + 20, y + entityY + 20, 0xBB000000);
        drawSlot(guiGraphics, x + entityX, y + entityY);
        String entityLabel = Component.translatable("container.zzq_survival_toolbox.micro_farm.entity").getString();
        guiGraphics.drawString(font, entityLabel,
                x + entityX + 9 - font.width(entityLabel) / 2,
                y + entityY + 18 + 2, 0x404040, false);

        // ---- 食物区（3×3） ----
        int foodAreaX = 100;
        int foodAreaY = 11;
        int foodAreaWidth = 54;
        int foodAreaHeight = 54;
        guiGraphics.fill(x + foodAreaX - 2, y + foodAreaY - 2,
                x + foodAreaX + foodAreaWidth + 2, y + foodAreaY + foodAreaHeight + 2, 0xBB000000);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(guiGraphics, x + foodAreaX + col * 18, y + foodAreaY + row * 18);
            }
        }
        String foodLabel = Component.translatable("container.zzq_survival_toolbox.micro_farm.food").getString();
        guiGraphics.drawString(font, foodLabel,
                x + foodAreaX + (foodAreaWidth - font.width(foodLabel)) / 2,
                y + foodAreaY - 10, 0x404040, false);

        // ---- 存储区 ----
        int storageY = 72;
        guiGraphics.fill(x + 7, y + storageY, x + 169, y + storageY + 54, 0xBB000000);
        String storageLabel = Component.translatable("container.zzq_survival_toolbox.micro_farm.storage").getString();
        guiGraphics.drawString(font, storageLabel, x + 8, y + storageY - 10, 0x404040, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(guiGraphics, x + 8 + col * 18, y + storageY + row * 18);
            }
        }

        // ---- 玩家背包 ----
        int playerInvLabelY = 130;
        int playerInvStartY = 140;
        int hotbarY = 198;
        guiGraphics.drawString(font, this.playerInventoryTitle, x + 8, y + playerInvLabelY, 0x404040, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(guiGraphics, x + 8 + col * 18, y + playerInvStartY + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(guiGraphics, x + 8 + col * 18, y + hotbarY);
        }
    }

    private void drawSlot(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        guiGraphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        guiGraphics.fill(x, y, x + 18, y + 1, 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + 1, y + 18, 0xFFFFFFFF);
        guiGraphics.fill(x + 17, y, x + 18, y + 18, 0xFF555555);
        guiGraphics.fill(x, y + 17, x + 18, y + 18, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
