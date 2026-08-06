package com.zzq.survival_toolbox.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 附魔数据交换 GUI 界面
 * <p>
 * 显示两个槽位（A 和 B），中间为交换按钮。
 * 点击交换按钮可互换两件装备的嗜血或自适应附魔数据。
 * </p>
 */
public class EnchantmentTransferScreen extends AbstractContainerScreen<EnchantmentTransferMenu> {

    private Button btnSwap;

    public EnchantmentTransferScreen(EnchantmentTransferMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // ---- 交换按钮 ----
        btnSwap = this.addRenderableWidget(
                Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.enchantment_transfer.swap"),
                        btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0)
                ).bounds(x + 62, y + 60, 52, 20).build()
        );
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        guiGraphics.drawString(font, this.title, x + 8, y + 6, 0x404040, false);

        // ---- 槽位 A ----
        int slotAX = x + 44;
        int slotAY = y + 35;
        drawSlot(guiGraphics, slotAX, slotAY);
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.enchantment_transfer.slot_a"),
                slotAX + 2, slotAY + 18 + 2, 0x404040, false);

        // ---- 槽位 B ----
        int slotBX = x + 116;
        int slotBY = y + 35;
        drawSlot(guiGraphics, slotBX, slotBY);
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.enchantment_transfer.slot_b"),
                slotBX + 2, slotBY + 18 + 2, 0x404040, false);

        // ---- 箭头 ----
        guiGraphics.drawString(font, Component.literal("← →"), x + 78, y + 39, 0x404040, false);

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(guiGraphics, x + 8 + col * 18, y + 84 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(guiGraphics, x + 8 + col * 18, y + 142);
        }
        guiGraphics.drawString(font, this.playerInventoryTitle, x + 8, y + 72, 0x404040, false);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}