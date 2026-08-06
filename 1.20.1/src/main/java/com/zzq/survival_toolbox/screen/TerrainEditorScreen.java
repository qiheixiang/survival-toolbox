package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.item.TerrainEditorItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 地形编辑器 GUI 界面
 * <p>
 * 控制地形编辑器的各项参数：
 * <ul>
 *   <li>范围 X/Y/Z</li>
 *   <li>偏移量 X/Y/Z</li>
 *   <li>显示范围开关</li>
 *   <li>保护模式开关</li>
 *   <li>模式切换（破坏/替换/填充）</li>
 * </ul>
 * 包含放置方块槽位，用于替换/填充模式。
 * 支持长按加减按钮。
 * </p>
 */
public class TerrainEditorScreen extends AbstractContainerScreen<TerrainEditorMenu> {

    private final TerrainEditorMenu menu;
    private Button rangeXVal, rangeYVal, rangeZVal;
    private Button offsetXVal, offsetYVal, offsetZVal;
    private Button btnShowRange, btnBreakProtected, btnMode;

    private static final int ID_RANGE_X_MINUS = 0;
    private static final int ID_RANGE_X_PLUS = 1;
    private static final int ID_RANGE_Y_MINUS = 2;
    private static final int ID_RANGE_Y_PLUS = 3;
    private static final int ID_RANGE_Z_MINUS = 4;
    private static final int ID_RANGE_Z_PLUS = 5;
    private static final int ID_OFFSET_X_MINUS = 6;
    private static final int ID_OFFSET_X_PLUS = 7;
    private static final int ID_OFFSET_Y_MINUS = 8;
    private static final int ID_OFFSET_Y_PLUS = 9;
    private static final int ID_OFFSET_Z_MINUS = 10;
    private static final int ID_OFFSET_Z_PLUS = 11;
    private static final int ID_SHOW_RANGE = 12;
    private static final int ID_BREAK_PROTECTED = 13;
    private static final int ID_MODE = 14;

    private Button pressedButton = null;
    private long pressStartTime = 0;
    private int pressCounter = 0;
    private static final long PRESS_DELAY_MS = 300;
    private static final long PRESS_INTERVAL_MS = 50;
    private final Map<Button, Integer> buttonActions = new HashMap<>();

    public TerrainEditorScreen(TerrainEditorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.menu = menu;
        this.imageWidth = 256;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        int col1X = 10;
        int col2X = 90;
        int col3X = 170;

        int rangeBaseX = col1X + 15 - 8;
        addRangeRow(x + rangeBaseX, y + 35, ID_RANGE_X_MINUS, ID_RANGE_X_PLUS, () -> menu.getRangeX());
        addRangeRow(x + rangeBaseX, y + 65, ID_RANGE_Y_MINUS, ID_RANGE_Y_PLUS, () -> menu.getRangeY());
        addRangeRow(x + rangeBaseX, y + 95, ID_RANGE_Z_MINUS, ID_RANGE_Z_PLUS, () -> menu.getRangeZ());

        int offsetBaseX = col2X + 15 - 8;
        addOffsetRow(x + offsetBaseX, y + 35, ID_OFFSET_X_MINUS, ID_OFFSET_X_PLUS, () -> menu.getOffsetX());
        addOffsetRow(x + offsetBaseX, y + 65, ID_OFFSET_Y_MINUS, ID_OFFSET_Y_PLUS, () -> menu.getOffsetY());
        addOffsetRow(x + offsetBaseX, y + 95, ID_OFFSET_Z_MINUS, ID_OFFSET_Z_PLUS, () -> menu.getOffsetZ());

        btnShowRange = Button.builder(
                getShowRangeText(menu.getShowRange()),
                b -> sendButtonClick(ID_SHOW_RANGE)
        ).bounds(x + col3X + 10, y + 35, 80, 20).build();
        addRenderableWidget(btnShowRange);

        btnBreakProtected = Button.builder(
                getBreakProtectedText(menu.getBreakProtected()),
                b -> sendButtonClick(ID_BREAK_PROTECTED)
        ).bounds(x + col3X + 10, y + 65, 80, 20).build();
        addRenderableWidget(btnBreakProtected);

        btnMode = Button.builder(
                getModeText(menu.getMode()),
                b -> sendButtonClick(ID_MODE)
        ).bounds(x + col3X + 10, y + 95, 80, 20).build();
        addRenderableWidget(btnMode);
    }

    // ============================================================
    // 辅助创建方法
    // ============================================================

    private void addRangeRow(int x, int y, int minusId, int plusId, java.util.function.IntSupplier valueSupplier) {
        Button minus = Button.builder(Component.translatable("gui.zzq_survival_toolbox.terrain_editor.minus"),
                        b -> sendButtonClick(minusId))
                .bounds(x, y, 16, 20).build();
        this.addRenderableWidget(minus);
        buttonActions.put(minus, minusId);

        Button val = Button.builder(Component.literal(String.valueOf(valueSupplier.getAsInt())), b -> {})
                .bounds(x + 20, y, 24, 20).build();
        val.active = false;
        this.addRenderableWidget(val);
        if (minusId == ID_RANGE_X_MINUS) rangeXVal = val;
        else if (minusId == ID_RANGE_Y_MINUS) rangeYVal = val;
        else if (minusId == ID_RANGE_Z_MINUS) rangeZVal = val;

        Button plus = Button.builder(Component.translatable("gui.zzq_survival_toolbox.terrain_editor.plus"),
                        b -> sendButtonClick(plusId))
                .bounds(x + 48, y, 16, 20).build();
        this.addRenderableWidget(plus);
        buttonActions.put(plus, plusId);
    }

    private void addOffsetRow(int x, int y, int minusId, int plusId, java.util.function.IntSupplier valueSupplier) {
        Button minus = Button.builder(Component.translatable("gui.zzq_survival_toolbox.terrain_editor.minus"),
                        b -> sendButtonClick(minusId))
                .bounds(x, y, 16, 20).build();
        this.addRenderableWidget(minus);
        buttonActions.put(minus, minusId);

        Button val = Button.builder(Component.literal(String.valueOf(valueSupplier.getAsInt())), b -> {})
                .bounds(x + 20, y, 24, 20).build();
        val.active = false;
        this.addRenderableWidget(val);
        if (minusId == ID_OFFSET_X_MINUS) offsetXVal = val;
        else if (minusId == ID_OFFSET_Y_MINUS) offsetYVal = val;
        else if (minusId == ID_OFFSET_Z_MINUS) offsetZVal = val;

        Button plus = Button.builder(Component.translatable("gui.zzq_survival_toolbox.terrain_editor.plus"),
                        b -> sendButtonClick(plusId))
                .bounds(x + 48, y, 16, 20).build();
        this.addRenderableWidget(plus);
        buttonActions.put(plus, plusId);
    }

    private void sendButtonClick(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    // ============================================================
    // 多语言文本
    // ============================================================

    private Component getShowRangeText(boolean show) {
        return Component.translatable(show ?
                "gui.zzq_survival_toolbox.terrain_editor.show_range.on" :
                "gui.zzq_survival_toolbox.terrain_editor.show_range.off");
    }

    private Component getBreakProtectedText(boolean enabled) {
        return Component.translatable(enabled ?
                "gui.zzq_survival_toolbox.terrain_editor.break_protected.on" :
                "gui.zzq_survival_toolbox.terrain_editor.break_protected.off");
    }

    private Component getModeText(int mode) {
        return switch (mode) {
            case 0 -> Component.translatable("gui.zzq_survival_toolbox.terrain_editor.mode.destroy");
            case 1 -> Component.translatable("gui.zzq_survival_toolbox.terrain_editor.mode.replace");
            default -> Component.translatable("gui.zzq_survival_toolbox.terrain_editor.mode.fill");
        };
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

    private void clientTick() {
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

    // ============================================================
    // 渲染
    // ============================================================

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        clientTick();

        // ---- 更新数值显示 ----
        if (rangeXVal != null) rangeXVal.setMessage(Component.literal(String.valueOf(menu.getRangeX())));
        if (rangeYVal != null) rangeYVal.setMessage(Component.literal(String.valueOf(menu.getRangeY())));
        if (rangeZVal != null) rangeZVal.setMessage(Component.literal(String.valueOf(menu.getRangeZ())));
        if (offsetXVal != null) offsetXVal.setMessage(Component.literal(String.valueOf(menu.getOffsetX())));
        if (offsetYVal != null) offsetYVal.setMessage(Component.literal(String.valueOf(menu.getOffsetY())));
        if (offsetZVal != null) offsetZVal.setMessage(Component.literal(String.valueOf(menu.getOffsetZ())));

        // ---- 更新按钮文字 ----
        if (btnShowRange != null) btnShowRange.setMessage(getShowRangeText(menu.getShowRange()));
        if (btnBreakProtected != null) btnBreakProtected.setMessage(getBreakProtectedText(menu.getBreakProtected()));
        if (btnMode != null) btnMode.setMessage(getModeText(menu.getMode()));

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.terrain_editor.range"),
                x + 10, y + 15, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.terrain_editor.offset"),
                x + 90, y + 15, 0xFFFFFF, false);

        guiGraphics.drawString(font, Component.literal("X:"), x + 8, y + 39, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.literal("Y:"), x + 8, y + 69, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.literal("Z:"), x + 8, y + 99, 0xFFFFFF, false);

        guiGraphics.drawString(font, Component.literal("X:"), x + 88, y + 39, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.literal("Y:"), x + 88, y + 69, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.literal("Z:"), x + 88, y + 99, 0xFFFFFF, false);

        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.terrain_editor.inventory"),
                x + 8, y + 130, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.terrain_editor.place_block"),
                x + 170 + 10, y + 158, 0xFFFFFF, false);

        // ---- 绘制槽位边框 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + 140 + row * 18;
                drawSlotBorder(guiGraphics, sx, sy);
            }
        }
        for (int col = 0; col < 9; col++) {
            int sx = x + 8 + col * 18;
            int sy = y + 140 + 54 + 2;
            drawSlotBorder(guiGraphics, sx, sy);
        }

        // ---- 放置方块槽位 ----
        int slotX = x + 190;
        int slotY = y + 153 + 18;
        drawSlotBorder(guiGraphics, slotX, slotY);
    }

    private void drawSlotBorder(GuiGraphics guiGraphics, int sx, int sy) {
        guiGraphics.fill(sx, sy, sx + 1, sy + 18, 0xFFFFFFFF);
        guiGraphics.fill(sx + 17, sy, sx + 18, sy + 18, 0xFFFFFFFF);
        guiGraphics.fill(sx, sy, sx + 18, sy + 1, 0xFFFFFFFF);
        guiGraphics.fill(sx, sy + 17, sx + 18, sy + 18, 0xFFFFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}