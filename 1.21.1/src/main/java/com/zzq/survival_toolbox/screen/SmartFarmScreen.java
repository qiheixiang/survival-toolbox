package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.SmartFarmBlockEntity;
import com.zzq.survival_toolbox.network.UpdateSmartFarmPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 智慧农场 GUI 界面
 * <p>
 * 左侧：水桶槽、锄头槽、工具栏（9格）、存储区（3×9）
 * 右侧面板：总开关、收获开关、播种开关、运行间隔、范围 X/Y/Z、范围显示
 * 支持长按加减间隔和范围值。
 * </p>
 */
public class SmartFarmScreen extends AbstractContainerScreen<SmartFarmMenu> {

    private final SmartFarmBlockEntity be;

    private Button btnEnabled;
    private Button btnShowRange;
    private Button btnHarvest;
    private Button btnSow;
    private Button displayInterval, displayRangeX, displayRangeY, displayRangeZ;
    private Button btnIntervalM, btnIntervalP;
    private Button btnRangeXM, btnRangeXP;
    private Button btnRangeYM, btnRangeYP;
    private Button btnRangeZM, btnRangeZP;

    private int currentInterval, currentRangeX, currentRangeY, currentRangeZ;
    private boolean currentEnabled, currentShowRange;
    private boolean currentHarvestEnabled, currentSowEnabled;

    private static final int CONTAINER_WIDTH = 176;
    private static final int PANEL_WIDTH = 90;
    private static final int GUI_HEIGHT = 242;

    // ---- 长按相关 ----
    private Button pressedButton = null;
    private long pressStartTime = 0;
    private int pressCounter = 0;
    private static final long PRESS_DELAY_MS = 300;
    private static final long PRESS_INTERVAL_MS = 50;

    private Component textEnabled, textDisabled;
    private Component textShow, textHide;
    private Component textHarvestOn, textHarvestOff;
    private Component textSowOn, textSowOff;

    public SmartFarmScreen(SmartFarmMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = CONTAINER_WIDTH + PANEL_WIDTH + 4;
        this.imageHeight = GUI_HEIGHT;
        this.be = menu.getBlockEntity();
        initTexts();
        loadValuesFromBE();
    }

    private void initTexts() {
        textEnabled = Component.translatable("gui.zzq_survival_toolbox.smart_farm.enabled");
        textDisabled = Component.translatable("gui.zzq_survival_toolbox.smart_farm.disabled");
        textShow = Component.translatable("gui.zzq_survival_toolbox.smart_farm.show");
        textHide = Component.translatable("gui.zzq_survival_toolbox.smart_farm.hide");
        textHarvestOn = Component.translatable("gui.zzq_survival_toolbox.smart_farm.harvest_on");
        textHarvestOff = Component.translatable("gui.zzq_survival_toolbox.smart_farm.harvest_off");
        textSowOn = Component.translatable("gui.zzq_survival_toolbox.smart_farm.sow_on");
        textSowOff = Component.translatable("gui.zzq_survival_toolbox.smart_farm.sow_off");
    }

    private void loadValuesFromBE() {
        this.currentInterval = be.getWorkInterval();
        this.currentRangeX = be.getRangeX();
        this.currentRangeY = be.getRangeY();
        this.currentRangeZ = be.getRangeZ();
        this.currentEnabled = be.isEnabled();
        this.currentShowRange = be.isShowRange();
        this.currentHarvestEnabled = be.isHarvestEnabled();
        this.currentSowEnabled = be.isSowEnabled();
    }

    @Override
    protected void init() {
        super.init();
        loadValuesFromBE();

        int left = this.leftPos;
        int top = this.topPos;
        int panelLeft = left + CONTAINER_WIDTH + 4;

        // ---- 总开关 ----
        btnEnabled = this.addRenderableWidget(Button.builder(
                currentEnabled ? textEnabled : textDisabled,
                btn -> {
                    currentEnabled = !currentEnabled;
                    be.setEnabled(currentEnabled);
                    btn.setMessage(currentEnabled ? textEnabled : textDisabled);
                    sendUpdate("enabled", currentEnabled ? 1 : 0);
                    updateXYZControls();
                }
        ).bounds(panelLeft + 4, top + 23, 80, 20).build());

        // ---- 收获开关 ----
        btnHarvest = this.addRenderableWidget(Button.builder(
                currentHarvestEnabled ? textHarvestOn : textHarvestOff,
                btn -> {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 100);
                    currentHarvestEnabled = !currentHarvestEnabled;
                    btn.setMessage(currentHarvestEnabled ? textHarvestOn : textHarvestOff);
                }
        ).bounds(panelLeft + 4, top + 46, 80, 20).build());

        // ---- 播种开关 ----
        btnSow = this.addRenderableWidget(Button.builder(
                currentSowEnabled ? textSowOn : textSowOff,
                btn -> {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 101);
                    currentSowEnabled = !currentSowEnabled;
                    btn.setMessage(currentSowEnabled ? textSowOn : textSowOff);
                }
        ).bounds(panelLeft + 4, top + 68, 80, 20).build());

        // ---- 运行间隔 ----
        btnIntervalM = createButton(panelLeft + 4, top + 99,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.minus"),
                () -> {
                    currentInterval = Math.max(1, currentInterval - 1);
                    be.setWorkInterval(currentInterval);
                    sendUpdate("interval", currentInterval);
                    updateInterval();
                });
        this.addRenderableWidget(btnIntervalM);

        displayInterval = Button.builder(Component.literal(String.valueOf(currentInterval)), b -> {})
                .bounds(panelLeft + 26, top + 99, 30, 20).build();
        displayInterval.active = false;
        this.addRenderableWidget(displayInterval);

        btnIntervalP = createButton(panelLeft + 58, top + 99,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.plus"),
                () -> {
                    currentInterval = Math.min(600, currentInterval + 1);
                    be.setWorkInterval(currentInterval);
                    sendUpdate("interval", currentInterval);
                    updateInterval();
                });
        this.addRenderableWidget(btnIntervalP);

        // ---- 范围 X ----
        btnRangeXM = createButton(panelLeft + 4, top + 135,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.minus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeX = Math.max(1, currentRangeX - 1);
                        be.setRangeX(currentRangeX);
                        sendUpdate("rangeX", currentRangeX);
                        updateRangeX();
                    }
                });
        this.addRenderableWidget(btnRangeXM);

        displayRangeX = Button.builder(Component.literal(String.valueOf(currentRangeX)), b -> {})
                .bounds(panelLeft + 26, top + 135, 30, 20).build();
        displayRangeX.active = false;
        this.addRenderableWidget(displayRangeX);

        btnRangeXP = createButton(panelLeft + 58, top + 135,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.plus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeX = Math.min(64, currentRangeX + 1);
                        be.setRangeX(currentRangeX);
                        sendUpdate("rangeX", currentRangeX);
                        updateRangeX();
                    }
                });
        this.addRenderableWidget(btnRangeXP);

        // ---- 范围 Y ----
        btnRangeYM = createButton(panelLeft + 4, top + 161,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.minus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeY = Math.max(1, currentRangeY - 1);
                        be.setRangeY(currentRangeY);
                        sendUpdate("rangeY", currentRangeY);
                        updateRangeY();
                    }
                });
        this.addRenderableWidget(btnRangeYM);

        displayRangeY = Button.builder(Component.literal(String.valueOf(currentRangeY)), b -> {})
                .bounds(panelLeft + 26, top + 161, 30, 20).build();
        displayRangeY.active = false;
        this.addRenderableWidget(displayRangeY);

        btnRangeYP = createButton(panelLeft + 58, top + 161,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.plus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeY = Math.min(64, currentRangeY + 1);
                        be.setRangeY(currentRangeY);
                        sendUpdate("rangeY", currentRangeY);
                        updateRangeY();
                    }
                });
        this.addRenderableWidget(btnRangeYP);

        // ---- 范围 Z ----
        btnRangeZM = createButton(panelLeft + 4, top + 187,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.minus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeZ = Math.max(1, currentRangeZ - 1);
                        be.setRangeZ(currentRangeZ);
                        sendUpdate("rangeZ", currentRangeZ);
                        updateRangeZ();
                    }
                });
        this.addRenderableWidget(btnRangeZM);

        displayRangeZ = Button.builder(Component.literal(String.valueOf(currentRangeZ)), b -> {})
                .bounds(panelLeft + 26, top + 187, 30, 20).build();
        displayRangeZ.active = false;
        this.addRenderableWidget(displayRangeZ);

        btnRangeZP = createButton(panelLeft + 58, top + 187,
                Component.translatable("gui.zzq_survival_toolbox.smart_farm.plus"),
                () -> {
                    if (!currentEnabled) {
                        currentRangeZ = Math.min(64, currentRangeZ + 1);
                        be.setRangeZ(currentRangeZ);
                        sendUpdate("rangeZ", currentRangeZ);
                        updateRangeZ();
                    }
                });
        this.addRenderableWidget(btnRangeZP);

        // ---- 范围显示 ----
        btnShowRange = this.addRenderableWidget(Button.builder(
                currentShowRange ? textShow : textHide,
                btn -> {
                    currentShowRange = !currentShowRange;
                    be.setShowRange(currentShowRange);
                    btn.setMessage(currentShowRange ? textShow : textHide);
                    sendUpdate("showRange", currentShowRange ? 1 : 0);
                }
        ).bounds(panelLeft + 4, top + 215, 80, 20).build());

        updateXYZControls();
    }

    private Button createButton(int x, int y, Component label, Runnable action) {
        return Button.builder(label, btn -> action.run())
                .bounds(x, y, 20, 20).build();
    }

    private void updateInterval() {
        displayInterval.setMessage(Component.literal(String.valueOf(currentInterval)));
    }

    private void updateRangeX() {
        displayRangeX.setMessage(Component.literal(String.valueOf(currentRangeX)));
    }

    private void updateRangeY() {
        displayRangeY.setMessage(Component.literal(String.valueOf(currentRangeY)));
    }

    private void updateRangeZ() {
        displayRangeZ.setMessage(Component.literal(String.valueOf(currentRangeZ)));
    }

    private void updateXYZControls() {
        // 范围与运行间隔都仅在总开关关闭时可调整
        boolean dis = currentEnabled;
        btnRangeXM.active = !dis;
        btnRangeXP.active = !dis;
        btnRangeYM.active = !dis;
        btnRangeYP.active = !dis;
        btnRangeZM.active = !dis;
        btnRangeZP.active = !dis;
        btnIntervalM.active = !dis;
        btnIntervalP.active = !dis;
    }

    private void sendUpdate(String action, int value) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new UpdateSmartFarmPacket(be.getBlockPos(), action, value));
    }

    // ============================================================
    // 长按逻辑
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var r : this.renderables) {
            if (r instanceof Button btn && btn.active && btn.visible && btn.isMouseOver(mouseX, mouseY)) {
                if (btn == btnIntervalM || btn == btnIntervalP ||
                        btn == btnRangeXM || btn == btnRangeXP ||
                        btn == btnRangeYM || btn == btnRangeYP ||
                        btn == btnRangeZM || btn == btnRangeZP) {
                    pressedButton = btn;
                    pressStartTime = System.currentTimeMillis();
                    pressCounter = 0;
                    btn.onPress();
                    return true;
                }
            }
        }
        pressedButton = null;
        pressStartTime = 0;
        pressCounter = 0;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedButton = null;
        pressStartTime = 0;
        pressCounter = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int panelLeft = left + CONTAINER_WIDTH + 4;

        gui.fill(left, top, left + CONTAINER_WIDTH, top + GUI_HEIGHT, 0xFFC6C6C6);
        gui.fill(left + 7, top + 7, left + CONTAINER_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFF8B8B8B);

        gui.fill(panelLeft, top, panelLeft + PANEL_WIDTH, top + GUI_HEIGHT, 0xFFAAAAAA);
        gui.fill(panelLeft + 2, top + 2, panelLeft + PANEL_WIDTH - 2, top + GUI_HEIGHT - 2, 0xFFCCCCCC);

        gui.drawString(font, this.title, left + 8, top + 4, 0x404040, false);

        // ---- 左侧标签 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.water"),
                left + 8, top + 13, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.hoe"),
                left + 80, top + 13, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.toolbar"),
                left + 8, top + 50, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.storage"),
                left + 8, top + 82, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.inventory"),
                left + 8, top + 150, 0x404040, false);

        // ---- 右侧面板标签 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.settings"),
                panelLeft + 4, top + 4, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.master_switch"),
                panelLeft + 4, top + 14, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.interval"),
                panelLeft + 4, top + 90, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.interval_hint"),
                panelLeft + 4, top + 120, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.range_x"),
                panelLeft + 4, top + 126, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.range_y"),
                panelLeft + 4, top + 152, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.range_z"),
                panelLeft + 4, top + 178, 0x404040, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.smart_farm.range_display"),
                panelLeft + 4, top + 206, 0x404040, false);

        // ---- 绘制所有槽位 ----
        drawSlot(gui, left + 26, top + 24);
        drawSlot(gui, left + 80, top + 24);
        for (int i = 0; i < 9; i++) drawSlot(gui, left + 8 + i * 18, top + 62);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlot(gui, left + 8 + c * 18, top + 92 + r * 18);
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlot(gui, left + 8 + c * 18, top + 160 + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            drawSlot(gui, left + 8 + c * 18, top + 218);
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
        // ---- 更新收获/播种按钮 ----
        if (btnHarvest != null) {
            currentHarvestEnabled = be.isHarvestEnabled();
            btnHarvest.setMessage(currentHarvestEnabled ? textHarvestOn : textHarvestOff);
        }
        if (btnSow != null) {
            currentSowEnabled = be.isSowEnabled();
            btnSow.setMessage(currentSowEnabled ? textSowOn : textSowOff);
        }

        // ---- 长按触发 ----
        if (pressedButton != null) {
            long elapsed = System.currentTimeMillis() - pressStartTime;
            if (elapsed >= PRESS_DELAY_MS) {
                int count = (int) ((elapsed - PRESS_DELAY_MS) / PRESS_INTERVAL_MS) + 1;
                if (count > pressCounter) {
                    pressedButton.onPress();
                    pressCounter = count;
                }
            }
        }

        this.renderBackground(gui, mouseX, mouseY, partialTick);
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }
}