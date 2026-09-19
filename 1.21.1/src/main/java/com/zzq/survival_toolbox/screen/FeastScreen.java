package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.FeastBlockEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 混沌篝火 GUI 界面
 * <p>
 * 左侧：物品栏 + 名单槽
 * 右侧面板：总开关、工作间隔、目标选择、范围控制、范围显示
 * 支持长按加减范围值和间隔值。
 * </p>
 */
public class FeastScreen extends AbstractContainerScreen<FeastMenu> {

    private final FeastBlockEntity be;

    // ---- 右侧面板按钮 ----
    private Button btnEnabled;
    private Button btnShowRange;
    private Button btnIntervalValue;
    private Button btnIntervalMinus, btnIntervalPlus;
    private Button btnTargetPlayer, btnTargetNeutral, btnTargetPassive, btnTargetHostile;
    private Button btnRangeXVal, btnRangeYVal, btnRangeZVal;
    private Button btnRangeXMinus, btnRangeXPlus;
    private Button btnRangeYMinus, btnRangeYPlus;
    private Button btnRangeZMinus, btnRangeZPlus;

    // ---- 长按相关 ----
    private Button pressedButton = null;
    private long pressStartTime = 0;
    private int pressCounter = 0;
    private static final long PRESS_DELAY_MS = 150;
    private static final long PRESS_INTERVAL_MS = 30;
    private final Map<Button, Integer> buttonActions = new HashMap<>();

    private static final int CONTAINER_WIDTH = 176;
    private static final int PANEL_WIDTH = 80;
    private static final int GUI_HEIGHT = 222;

    public FeastScreen(FeastMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = CONTAINER_WIDTH + PANEL_WIDTH + 4;
        this.imageHeight = GUI_HEIGHT;
        this.be = menu.getBlockEntity();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.leftPos;
        int top = this.topPos;
        int panelLeft = left + CONTAINER_WIDTH + 4;

        // ---- 总开关 ----
        btnEnabled = createToggleButton(panelLeft + 4, top + 32, 72, 16,
                be.isEnabled() ? Component.translatable("gui.zzq_survival_toolbox.feast.enabled_on") :
                        Component.translatable("gui.zzq_survival_toolbox.feast.enabled_off"),
                100);

        // ---- 间隔控制 ----
        btnIntervalMinus = createButton(panelLeft + 4, top + 66,
                Component.translatable("gui.zzq_survival_toolbox.feast.minus"), 102);
        btnIntervalValue = createValueButton(panelLeft + 26, top + 66, 28,
                String.valueOf(be.getWorkInterval()));
        btnIntervalPlus = createButton(panelLeft + 56, top + 66,
                Component.translatable("gui.zzq_survival_toolbox.feast.plus"), 103);

        // ---- 目标选择（4个按钮，两行两列） ----
        int targetY = top + 90;
        btnTargetPlayer = createTargetButton(panelLeft + 4, targetY,
                Component.translatable("gui.zzq_survival_toolbox.feast.target_player"), 104);
        btnTargetNeutral = createTargetButton(panelLeft + 40, targetY,
                Component.translatable("gui.zzq_survival_toolbox.feast.target_neutral"), 105);
        btnTargetHostile = createTargetButton(panelLeft + 4, targetY + 18,
                Component.translatable("gui.zzq_survival_toolbox.feast.target_hostile"), 107);
        btnTargetPassive = createTargetButton(panelLeft + 40, targetY + 18,
                Component.translatable("gui.zzq_survival_toolbox.feast.target_passive"), 106);

        // ---- 范围控制 ----
        addRangeControl(panelLeft + 4, top + 142,
                Component.translatable("gui.zzq_survival_toolbox.feast.range_x"),
                be.getRangeX(), 108, 109);
        addRangeControl(panelLeft + 4, top + 162,
                Component.translatable("gui.zzq_survival_toolbox.feast.range_y"),
                be.getRangeY(), 110, 111);
        addRangeControl(panelLeft + 4, top + 182,
                Component.translatable("gui.zzq_survival_toolbox.feast.range_z"),
                be.getRangeZ(), 112, 113);

        // ---- 范围显示按钮 ----
        btnShowRange = createToggleButton(panelLeft + 4, top + 202, 72, 16,
                be.isShowRange() ? Component.translatable("gui.zzq_survival_toolbox.feast.show_range_on") :
                        Component.translatable("gui.zzq_survival_toolbox.feast.show_range_off"),
                101);

        updateButtons();
    }

    // ============================================================
    // 按钮创建辅助方法
    // ============================================================

    private Button createToggleButton(int x, int y, int width, int height, Component text, int id) {
        Button btn = Button.builder(text, b -> sendClick(id))
                .bounds(x, y, width, height).build();
        addRenderableWidget(btn);
        buttonActions.put(btn, id);
        return btn;
    }

    private Button createButton(int x, int y, Component text, int id) {
        Button btn = Button.builder(text, b -> sendClick(id))
                .bounds(x, y, 16, 16).build();
        addRenderableWidget(btn);
        buttonActions.put(btn, id);
        return btn;
    }

    private Button createValueButton(int x, int y, int width, String text) {
        Button btn = Button.builder(Component.literal(text), b -> {})
                .bounds(x, y, width, 16).build();
        btn.active = false;
        addRenderableWidget(btn);
        return btn;
    }

    private Button createTargetButton(int x, int y, Component label, int id) {
        AtomicBoolean current = new AtomicBoolean(getInitialTargetState(id));
        Button btn = Button.builder(
                Component.literal(current.get() ? "✔ " : "✘ ").append(label),
                b -> {
                    boolean newVal = !current.get();
                    current.set(newVal);
                    b.setMessage(Component.literal(newVal ? "✔ " : "✘ ").append(label));
                    sendClick(id);
                }
        ).bounds(x, y, 36, 16).build();
        addRenderableWidget(btn);
        buttonActions.put(btn, id);
        return btn;
    }

    private boolean getInitialTargetState(int id) {
        return switch (id) {
            case 104 -> be.isTargetPlayer();
            case 105 -> be.isTargetNeutral();
            case 106 -> be.isTargetPassive();
            case 107 -> be.isTargetHostile();
            default -> false;
        };
    }

    private void addRangeControl(int x, int y, Component label, int value, int minusId, int plusId) {
        Button minus = createButton(x + 14, y,
                Component.translatable("gui.zzq_survival_toolbox.feast.minus"), minusId);
        Button val = createValueButton(x + 30, y, 20, String.valueOf(value));
        Button plus = createButton(x + 52, y,
                Component.translatable("gui.zzq_survival_toolbox.feast.plus"), plusId);

        if (minusId == 108) {
            btnRangeXMinus = minus;
            btnRangeXVal = val;
            btnRangeXPlus = plus;
        } else if (minusId == 110) {
            btnRangeYMinus = minus;
            btnRangeYVal = val;
            btnRangeYPlus = plus;
        } else if (minusId == 112) {
            btnRangeZMinus = minus;
            btnRangeZVal = val;
            btnRangeZPlus = plus;
        }
    }

    private void sendClick(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int left = this.leftPos;
        int top = this.topPos;
        int panelLeft = left + CONTAINER_WIDTH + 4;

        // ---- 左侧容器背景 ----
        gui.fill(left, top, left + CONTAINER_WIDTH, top + GUI_HEIGHT, 0xDD000000);
        gui.fill(left + 4, top + 4, left + CONTAINER_WIDTH - 4, top + GUI_HEIGHT - 4, 0xFF2A2A2A);

        // ---- 右侧面板背景 ----
        gui.fill(panelLeft, top, panelLeft + PANEL_WIDTH, top + GUI_HEIGHT, 0xDD222222);
        gui.fill(panelLeft + 2, top + 2, panelLeft + PANEL_WIDTH - 2, top + GUI_HEIGHT - 2, 0xFF333333);

        gui.drawString(font, this.title, left + 8, top + 6, 0xFFFFFF, false);

        // ---- 右侧面板标签 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.settings"),
                panelLeft + 4, top + 4, 0xAAAAAA, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.master_switch"),
                panelLeft + 4, top + 20, 0xAAAAAA, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.interval_label"),
                panelLeft + 4, top + 54, 0xAAAAAA, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.range_display"),
                panelLeft + 4, top + 130, 0xAAAAAA, false);

        // ---- 范围 X/Y/Z 标签 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.range_x"),
                panelLeft + 4, top + 144, 0xAAAAAA, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.range_y"),
                panelLeft + 4, top + 164, 0xAAAAAA, false);
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.range_z"),
                panelLeft + 4, top + 184, 0xAAAAAA, false);

        // ---- 名单槽 ----
        int bx = left + 80;
        int by = top + 24;
        gui.fill(bx - 1, by - 1, bx + 17, by + 17, 0xFF555555);
        gui.fill(bx, by, bx + 16, by + 16, 0xFF8B8B8B);
        gui.fill(bx + 1, by + 1, bx + 15, by + 15, 0xFF373737);

        ItemStack blacklist = be.getBlacklist();
        if (blacklist.isEmpty()) {
            gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.blacklist_empty"),
                    bx + 1, by + 4, 0xFF888888, false);
        } else {
            gui.drawString(font, blacklist.getDisplayName().getString(), bx + 1, by + 4, 0xFFFFFF, false);
        }
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.blacklist_label"),
                bx, by - 10, 0xAAAAAA, false);

        // ---- 3×9 物品栏 ----
        gui.drawString(font, Component.translatable("gui.zzq_survival_toolbox.feast.storage"),
                left + 8, top + 50, 0xAAAAAA, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(gui, left + 8 + col * 18, top + 60 + row * 18);
            }
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(gui, left + 8 + col * 18, top + 132 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(gui, left + 8 + col * 18, top + 190);
        }
        gui.drawString(font, this.playerInventoryTitle, left + 8, top + 122, 0xAAAAAA, false);
    }

    private void drawSlot(GuiGraphics gui, int x, int y) {
        gui.fill(x, y, x + 18, y + 18, 0xFF555555);
        gui.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
        gui.fill(x, y, x + 18, y + 1, 0xFFFFFFFF);
        gui.fill(x, y, x + 1, y + 18, 0xFFFFFFFF);
        gui.fill(x + 17, y, x + 18, y + 18, 0xFF555555);
        gui.fill(x, y + 17, x + 18, y + 18, 0xFF555555);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        handleLongPress();
        updateButtons();
        super.render(gui, mouseX, mouseY, partialTick);
        renderTooltip(gui, mouseX, mouseY);
    }

    // ============================================================
    // 更新按钮状态
    // ============================================================

    private void updateButtons() {
        btnEnabled.setMessage(be.isEnabled() ?
                Component.translatable("gui.zzq_survival_toolbox.feast.enabled_on") :
                Component.translatable("gui.zzq_survival_toolbox.feast.enabled_off"));
        btnShowRange.setMessage(be.isShowRange() ?
                Component.translatable("gui.zzq_survival_toolbox.feast.show_range_on") :
                Component.translatable("gui.zzq_survival_toolbox.feast.show_range_off"));

        updateTargetButton(btnTargetPlayer, be.isTargetPlayer(),
                Component.translatable("gui.zzq_survival_toolbox.feast.target_player"));
        updateTargetButton(btnTargetNeutral, be.isTargetNeutral(),
                Component.translatable("gui.zzq_survival_toolbox.feast.target_neutral"));
        updateTargetButton(btnTargetPassive, be.isTargetPassive(),
                Component.translatable("gui.zzq_survival_toolbox.feast.target_passive"));
        updateTargetButton(btnTargetHostile, be.isTargetHostile(),
                Component.translatable("gui.zzq_survival_toolbox.feast.target_hostile"));

        btnRangeXVal.setMessage(Component.literal(String.valueOf(be.getRangeX())));
        btnRangeYVal.setMessage(Component.literal(String.valueOf(be.getRangeY())));
        btnRangeZVal.setMessage(Component.literal(String.valueOf(be.getRangeZ())));
        btnIntervalValue.setMessage(Component.literal(String.valueOf(be.getWorkInterval())));

        // 范围与运行间隔都仅在总开关关闭时可调整
        boolean enabled = be.isEnabled();
        btnRangeXMinus.active = !enabled;
        btnRangeXPlus.active = !enabled;
        btnRangeYMinus.active = !enabled;
        btnRangeYPlus.active = !enabled;
        btnRangeZMinus.active = !enabled;
        btnRangeZPlus.active = !enabled;
        btnIntervalMinus.active = !enabled;
        btnIntervalPlus.active = !enabled;
    }

    private void updateTargetButton(Button btn, boolean active, Component label) {
        btn.setMessage(Component.literal(active ? "✔ " : "✘ ").append(label));
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
                pressCounter = 0;
                btn.onPress();
                return true;
            }
        }
        pressedButton = null;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedButton = null;
        pressStartTime = 0;
        pressCounter = 0;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void handleLongPress() {
        if (pressedButton != null && buttonActions.containsKey(pressedButton)) {
            long elapsed = System.currentTimeMillis() - pressStartTime;
            if (elapsed >= PRESS_DELAY_MS) {
                int count = (int) ((elapsed - PRESS_DELAY_MS) / PRESS_INTERVAL_MS) + 1;
                if (count > pressCounter) {
                    pressedButton.onPress();
                    pressCounter = count;
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}
