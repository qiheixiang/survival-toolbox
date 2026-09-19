package com.zzq.survival_toolbox.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.gui.components.Button;

/**
 * 拆解台 GUI 界面
 * <p>
 * 显示输入槽、九宫格、输出槽以及翻页按钮。
 * 左侧显示拆解配方页码，右侧显示合成配方页码。
 * 当产物超过 9 格时，显示一键拆解按钮。
 * </p>
 */
public class DisassembleScreen extends AbstractContainerScreen<DisassembleMenu> {

    private Button btnDisassembleLeft;
    private Button btnDisassembleRight;
    private Button btnCraftingLeft;
    private Button btnCraftingRight;
    private Button btnBulkDisassemble;
    private Button btnOneClickDisassemble;

    public DisassembleScreen(DisassembleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;

        int leftSlotX = this.leftPos + 19;
        int leftSlotY = this.topPos + 28;
        int leftSlotCenter = leftSlotX + 9;

        int btnWidth = 12;
        int gap = 2;
        int groupWidth = btnWidth * 2 + gap;
        int leftBtnX = leftSlotCenter - groupWidth / 2;
        int rightBtnX = leftBtnX + btnWidth + gap;

        // ---- 拆解翻页按钮（输入槽下方） ----
        this.btnDisassembleLeft = this.addRenderableWidget(
                Button.builder(Component.literal("<"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0))
                        .bounds(leftBtnX, leftSlotY + 18 + 2, btnWidth, 12).build()
        );
        this.btnDisassembleRight = this.addRenderableWidget(
                Button.builder(Component.literal(">"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1))
                        .bounds(rightBtnX, leftSlotY + 18 + 2, btnWidth, 12).build()
        );

        // ---- 批量拆解按钮 ----
        int bulkWidth = 36;
        int bulkX = leftSlotCenter - bulkWidth / 2;
        int bulkY = leftSlotY + 18 + 2 + 14;
        this.btnBulkDisassemble = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.zzq_survival_toolbox.disassemble.bulk_disassemble"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 5))
                        .bounds(bulkX, bulkY, bulkWidth, 16).build()
        );

        // ---- 合成翻页按钮（输出槽下方） ----
        int rightSlotX = this.leftPos + 139;
        int rightSlotY = this.topPos + 28;
        int rightSlotCenter = rightSlotX + 9;
        int rightLeftBtnX = rightSlotCenter - groupWidth / 2;
        int rightRightBtnX = rightLeftBtnX + btnWidth + gap;

        this.btnCraftingLeft = this.addRenderableWidget(
                Button.builder(Component.literal("<"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2))
                        .bounds(rightLeftBtnX, rightSlotY + 18 + 2, btnWidth, 12).build()
        );
        this.btnCraftingRight = this.addRenderableWidget(
                Button.builder(Component.literal(">"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 3))
                        .bounds(rightRightBtnX, rightSlotY + 18 + 2, btnWidth, 12).build()
        );

        // ---- 一键拆解按钮（产物超过 9 格时显示） ----
        int midAreaX = this.leftPos + 50;
        int midAreaY = this.topPos + 10;
        this.btnOneClickDisassemble = this.addRenderableWidget(
                Button.builder(Component.translatable("gui.zzq_survival_toolbox.disassemble.one_click"),
                                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 4))
                        .bounds(midAreaX + 10, midAreaY + 25, 48, 20).build()
        );
        this.btnOneClickDisassemble.visible = false;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        // ---- 输入区域 ----
        int leftAreaX = x + 10;
        int leftAreaY = y + 10;
        guiGraphics.fill(leftAreaX, leftAreaY, leftAreaX + 36, leftAreaY + 54, 0xBB000000);
        guiGraphics.drawString(font, Component.translatable("container.zzq_survival_toolbox.disassemble.input"),
                leftAreaX, leftAreaY - 10, 0x404040, false);

        // ---- 九宫格区域 ----
        int midAreaX = x + 50;
        int midAreaY = y + 10;
        guiGraphics.fill(midAreaX, midAreaY, midAreaX + 68, midAreaY + 68, 0xBB000000);

        // ---- 输出区域 ----
        int rightAreaX = x + 130;
        int rightAreaY = y + 10;
        guiGraphics.fill(rightAreaX, rightAreaY, rightAreaX + 36, rightAreaY + 54, 0xBB000000);
        guiGraphics.drawString(font, Component.translatable("container.zzq_survival_toolbox.disassemble.output"),
                rightAreaX, rightAreaY - 10, 0x404040, false);

        // ---- 绘制所有槽位 ----
        drawSlot(guiGraphics, leftAreaX + 9, leftAreaY + 18);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlot(guiGraphics, midAreaX + 7 + col * 18, midAreaY + 8 + row * 18);
            }
        }
        drawSlot(guiGraphics, rightAreaX + 9, rightAreaY + 18);

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(guiGraphics, x + 7 + col * 18, y + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(guiGraphics, x + 7 + col * 18, y + 141);
        }

        guiGraphics.drawString(font, this.title, x + 8, y + 6, 0x404040, false);
        guiGraphics.drawString(font, this.playerInventoryTitle, x + 8, y + 72, 0x404040, false);

        // 配方索引构建中 → 在九宫格上方暗色条带内居中显示 "X/Y" 进度。
        // 画在窗口内部（y=midAreaY），不会被按钮遮挡，也不会因窗口靠上而越出屏幕。
        // 阶段一、二都会推送进度；索引就绪后 indexState>=2，此处不再显示。
        // 配方索引构建中 → 在九宫格上方、"产物过多"提示(midAreaY-12)的上方居中显示 "X/Y" 进度。
        // 索引已就绪 → 同一位置常驻显示"全配方加载完毕"（诊断用：验证该位置文字是否真的可见）。
        int indexState = this.menu.getRecipeIndexState();
        if (indexState == 0 || indexState == 1) {
            String progress = DisassembleMenu.getClientIndexProgressText();
            if (!progress.isEmpty()) {
                int pw = font.width(progress);
                guiGraphics.drawString(font, progress, midAreaX + (68 - pw) / 2, midAreaY - 22, 0xFFAA00, false);
            }
        } else if (indexState >= 2) {
            Component readyText = Component.translatable("gui.zzq_survival_toolbox.disassemble.recipes_ready");
            int rw = font.width(readyText);
            guiGraphics.drawString(font, readyText, midAreaX + (68 - rw) / 2, midAreaY - 22, 0xFFAA00, false);
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        btnOneClickDisassemble.visible = menu.isButtonMode;

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // ---- 拆解配方页码 ----
        if (menu.getDisassembleRecipeTotal() > 0) {
            String text = menu.getDisassembleRecipeIndex() + "/" + menu.getDisassembleRecipeTotal();
            int textX = this.leftPos + 19 + 9 - font.width(text) / 2;
            int textY = this.topPos + 19;
            guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, false);
        }

        // ---- 合成配方页码 ----
        if (menu.getCraftRecipeTotal() > 0) {
            String text = menu.getCraftRecipeIndex() + "/" + menu.getCraftRecipeTotal();
            int textX = this.leftPos + 139 + 9 - font.width(text) / 2;
            int textY = this.topPos + 19;
            guiGraphics.drawString(font, text, textX, textY, 0xFFFFFF, false);
        }

        // ---- 产物过多提示 ----
        if (menu.isButtonMode()) {
            int midAreaX = this.leftPos + 50;
            int midAreaY = this.topPos + 10;
            guiGraphics.drawString(font,
                    Component.translatable("gui.zzq_survival_toolbox.disassemble.too_many_products"),
                    midAreaX + 4, midAreaY - 12, 0xFFFF0000, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}
