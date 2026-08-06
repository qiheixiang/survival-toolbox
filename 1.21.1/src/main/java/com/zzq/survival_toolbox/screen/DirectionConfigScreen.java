package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.util.DirectionConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * 万向漏斗方向配置 GUI 界面
 * <p>
 * 控制单个方向的：
 * <ul>
 *   <li>传输速度（加减按钮，支持长按）</li>
 *   <li>过滤模式（白名单/黑名单/禁用）</li>
 *   <li>方向模式（流入/流出/禁止）</li>
 * </ul>
 * 下方显示过滤物品列表（3×9）和玩家背包。
 * </p>
 */
public class DirectionConfigScreen extends AbstractContainerScreen<DirectionConfigMenu> {

    private final DirectionConfig config;
    private Button btnSpeedValue;
    private Button btnSpeedMinus, btnSpeedPlus;
    private Button btnFilterToggle;
    private Button btnModeToggle;
    private Button btnBack;

    // ---- 长按相关 ----
    private Button pressedButton = null;
    private long pressStartTime = 0;
    private boolean isStopping = false;
    private static final long PRESS_DELAY_MS = 150;
    private static final long PRESS_INTERVAL_MS = 20;
    private final Map<Button, Integer> buttonActions = new HashMap<>();
    private long lastTriggerCount = 0;

    public DirectionConfigScreen(DirectionConfigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.config = menu.getConfig();
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // ---- 返回按钮 ----
        btnBack = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.direction_config.back"),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 6)
        ).bounds(x + imageWidth - 10, y, 14, 14).build();
        addRenderableWidget(btnBack);

        // ---- 速度标签 ----
        Button btnSpeedLabel = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.omni_hopper.speed_label"),
                b -> {}
        ).bounds(x + 8, y + 10, 32, 20).build();
        btnSpeedLabel.active = false;
        addRenderableWidget(btnSpeedLabel);

        // ---- 速度数值 ----
        btnSpeedValue = Button.builder(
                Component.literal(String.valueOf(config.getSpeed())),
                b -> {}
        ).bounds(x + 44, y + 10, 36, 20).build();
        btnSpeedValue.active = false;
        addRenderableWidget(btnSpeedValue);

        // ---- 速度单位 ----
        Button btnSpeedUnit = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.omni_hopper.speed_unit"),
                b -> {}
        ).bounds(x + 84, y + 10, 30, 20).build();
        btnSpeedUnit.active = false;
        addRenderableWidget(btnSpeedUnit);

        // ---- 减号按钮 ----
        btnSpeedMinus = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.direction_config.minus"),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0)
        ).bounds(x + 118, y + 10, 20, 20).build();
        addRenderableWidget(btnSpeedMinus);
        buttonActions.put(btnSpeedMinus, 0);

        // ---- 加号按钮 ----
        btnSpeedPlus = Button.builder(
                Component.translatable("gui.zzq_survival_toolbox.direction_config.plus"),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1)
        ).bounds(x + 142, y + 10, 20, 20).build();
        addRenderableWidget(btnSpeedPlus);
        buttonActions.put(btnSpeedPlus, 1);

        // ---- 过滤模式切换 ----
        btnFilterToggle = Button.builder(
                getFilterModeText(config.getFilterMode()),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 2)
        ).bounds(x + 8, y + 36, 60, 20).build();
        addRenderableWidget(btnFilterToggle);

        // ---- 方向模式切换 ----
        btnModeToggle = Button.builder(
                getModeText(config.getMode()),
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 3)
        ).bounds(x + 72, y + 36, 60, 20).build();
        addRenderableWidget(btnModeToggle);
    }

    private Component getModeText(DirectionConfig.Mode mode) {
        return switch (mode) {
            case IN -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.in");
            case OUT -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.out");
            default -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.blocked");
        };
    }

    private Component getFilterModeText(DirectionConfig.FilterMode mode) {
        return switch (mode) {
            case WHITELIST -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.filter.whitelist");
            case BLACKLIST -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.filter.blacklist");
            default -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.filter.disabled");
        };
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        guiGraphics.drawString(font, this.title, x + 8, y + 4, 0x404040, false);

        guiGraphics.drawString(
                font,
                Component.translatable("gui.zzq_survival_toolbox.omni_hopper.filter_label"),
                x + 8, y + 62, 0x404040, false
        );

        // ---- 过滤物品格子（3行×9列） ----
        int filterStartX = x + 7;
        int filterStartY = y + 74;
        drawSlotGrid(guiGraphics, filterStartX, filterStartY, 3, 9);

        guiGraphics.drawString(font, this.playerInventoryTitle, x + 8, y + 130, 0x404040, false);
        drawSlotGrid(guiGraphics, x + 8, y + 140, 3, 9);
        drawSlotGrid(guiGraphics, x + 8, y + 198, 1, 9);
    }

    private void drawSlotGrid(GuiGraphics guiGraphics, int startX, int startY, int rows, int cols) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = startX + col * 18;
                int sy = startY + row * 18;
                guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF555555);
                guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF373737);
                guiGraphics.fill(sx, sy, sx + 18, sy + 1, 0xFFFFFFFF);
                guiGraphics.fill(sx, sy, sx + 1, sy + 18, 0xFFFFFFFF);
                guiGraphics.fill(sx + 17, sy, sx + 18, sy + 18, 0xFF555555);
                guiGraphics.fill(sx, sy + 17, sx + 18, sy + 18, 0xFF555555);
            }
        }
    }

    // ============================================================
    // 长按逻辑
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var entry : buttonActions.entrySet()) {
            Button btn = entry.getKey();
            if (btn.active && btn.visible && btn.isMouseOver(mouseX, mouseY)) {
                pressedButton = btn;
                pressStartTime = System.currentTimeMillis();
                isStopping = false;
                lastTriggerCount = 0;
                btn.onPress();
                return true;
            }
        }
        pressedButton = null;
        pressStartTime = 0;
        isStopping = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isStopping = true;
        pressedButton = null;
        pressStartTime = 0;
        lastTriggerCount = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleLongPress() {
        if (isStopping || pressedButton == null || !buttonActions.containsKey(pressedButton)) return;

        long elapsed = System.currentTimeMillis() - pressStartTime;
        if (elapsed >= PRESS_DELAY_MS) {
            long triggerCount = (elapsed - PRESS_DELAY_MS) / PRESS_INTERVAL_MS + 1;
            if (triggerCount > lastTriggerCount) {
                pressedButton.onPress();
                lastTriggerCount = triggerCount;
            }
        }
    }

    /**
     * 根据世界方向和玩家朝向，获取方向对应的显示名称
     */
    private Component getDirectionDisplayName(Direction worldDir) {
        Direction playerFacing = menu.getBlockEntity().getPlayerFacing();
        if (playerFacing == null) playerFacing = Direction.NORTH;

        if (worldDir == Direction.UP) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.up");
        if (worldDir == Direction.DOWN) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.down");
        if (worldDir == playerFacing) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.front");
        if (worldDir == playerFacing.getOpposite()) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.back");
        if (worldDir == playerFacing.getCounterClockWise()) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.left");
        if (worldDir == playerFacing.getClockWise()) return Component.translatable("gui.zzq_survival_toolbox.omni_hopper.right");
        return Component.literal("?");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        handleLongPress();

        // ---- 更新显示 ----
        if (btnSpeedValue != null) {
            btnSpeedValue.setMessage(Component.literal(String.valueOf(config.getSpeed())));
        }
        if (btnFilterToggle != null) {
            btnFilterToggle.setMessage(getFilterModeText(config.getFilterMode()));
        }
        if (btnModeToggle != null) {
            btnModeToggle.setMessage(getModeText(config.getMode()));
        }

        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        // ---- 方向名称 ----
        Component dirText = getDirectionDisplayName(menu.getDirection());
        int textX = this.leftPos + imageWidth - 10;
        int textY = this.topPos + 16;
        guiGraphics.drawString(font, dirText, textX, textY, 0xFFFFFF, true);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}