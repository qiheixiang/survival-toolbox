package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.SurvivalToolbox;
import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
import com.zzq.survival_toolbox.network.UpdateGuardianLanternPacket;
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
 * 镇魂灯 GUI 界面
 * <p>
 * 控制镇魂灯的各项配置：
 * <ul>
 *   <li>总开关、范围显示开关、照明开关</li>
 *   <li>范围 X/Y/Z 调整</li>
 *   <li>镇压开关及子选项（敌对/中立/被动）</li>
 *   <li>自动攻击开关及子选项（敌对/中立/被动）</li>
 *   <li>攻击间隔调整</li>
 * </ul>
 * 支持长按加减范围值和攻击间隔值。
 * </p>
 */
public class GuardianLanternScreen extends AbstractContainerScreen<GuardianLanternMenu> {

    private final GuardianLanternBlockEntity be;
    private final Map<String, Button> valueButtonMap = new HashMap<>();
    private final Map<String, Button> suppressSubButtons = new HashMap<>();
    private final Map<String, Button> attackSubButtons = new HashMap<>();
    private final Map<Button, String> buttonActions = new HashMap<>();

    private Button litButton;
    private Button intervalButton;
    private Button suppressToggleBtn;
    private Button attackToggleBtn;
    private Button enabledToggleBtn;

    private Button rangeXMinus, rangeXVal, rangeXPlus;
    private Button rangeYMinus, rangeYVal, rangeYPlus;
    private Button rangeZMinus, rangeZVal, rangeZPlus;

    private Button pressedButton = null;
    private long pressStartTime = 0;
    private int pressCounter = 0;
    private static final long PRESS_DELAY_MS = 150;
    private static final long PRESS_INTERVAL_MS = 30;

    public GuardianLanternScreen(GuardianLanternMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 250;
        this.be = menu.getBlockEntity();
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // ---- 第一行：总开关 + 范围显示 + 照明 ----
        enabledToggleBtn = this.addRenderableWidget(Button.builder(
                        be.isEnabled() ? Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.enabled_on") :
                                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.enabled_off"),
                        btn -> {
                            boolean newVal = !be.isEnabled();
                            be.setEnabled(newVal);
                            btn.setMessage(newVal ?
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.enabled_on") :
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.enabled_off"));
                            sendUpdate("enabled", newVal ? 1 : 0);
                            updateControls();
                        })
                .bounds(x + 10, y + 10, 60, 20).build());

        this.addRenderableWidget(Button.builder(
                        be.isShowRange() ? Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.show_range_on") :
                                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.show_range_off"),
                        btn -> {
                            boolean newVal = !be.isShowRange();
                            be.setShowRange(newVal);
                            btn.setMessage(newVal ?
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.show_range_on") :
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.show_range_off"));
                            sendUpdate("showRange", newVal ? 1 : 0);
                        })
                .bounds(x + 80, y + 10, 80, 20).build());

        litButton = this.addRenderableWidget(Button.builder(
                        be.isLit() ? Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_on") :
                                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_off"),
                        btn -> {
                            if (!be.isEnabled()) return;
                            boolean newLit = !be.isLit();
                            be.setLit(newLit);
                            btn.setMessage(newLit ?
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_on") :
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_off"));
                            sendUpdate("lit", newLit ? 1 : 0);
                        })
                .bounds(x + 170, y + 10, 80, 20).build());

        // ---- 范围调整 ----
        addRangeControl(x, y + 40, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.range_x"), "rangeX");
        addRangeControl(x, y + 60, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.range_y"), "rangeY");
        addRangeControl(x, y + 80, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.range_z"), "rangeZ");

        // ---- 镇压控制 ----
        int suppressY = y + 116;
        suppressToggleBtn = this.addRenderableWidget(Button.builder(
                        be.isSuppressEnabled() ? Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.suppress_on") :
                                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.suppress_off"),
                        btn -> {
                            boolean newVal = !be.isSuppressEnabled();
                            be.setSuppressEnabled(newVal);
                            btn.setMessage(newVal ?
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.suppress_on") :
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.suppress_off"));
                            sendUpdate("suppressEnabled", newVal ? 1 : 0);
                            updateSuppressSubButtons();
                            updateControls();
                        })
                .bounds(x + 10, suppressY, 70, 20).build());

        int subY = y + 138;
        suppressSubButtons.put("hostile", addSuppressSubButton(x + 10, subY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.hostile"),
                be.isSuppressHostile(), "suppressHostile"));
        suppressSubButtons.put("neutral", addSuppressSubButton(x + 55, subY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.neutral"),
                be.isSuppressNeutral(), "suppressNeutral"));
        suppressSubButtons.put("passive", addSuppressSubButton(x + 100, subY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.passive"),
                be.isSuppressPassive(), "suppressPassive"));
        updateSuppressSubButtons();

        // ---- 自动攻击控制 ----
        int attackY = y + 168;
        Component attackText = be.getWeapon().isEmpty() ?
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.auto_attack_no_weapon") :
                (be.isAutoAttack() ?
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.auto_attack_on") :
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.auto_attack_off"));
        attackToggleBtn = this.addRenderableWidget(Button.builder(
                        attackText,
                        btn -> {
                            if (be.getWeapon().isEmpty()) return;
                            boolean newVal = !be.isAutoAttack();
                            be.setAutoAttack(newVal);
                            btn.setMessage(newVal ?
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.auto_attack_on") :
                                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.auto_attack_off"));
                            sendUpdate("autoAttack", newVal ? 1 : 0);
                            updateAttackSubButtons();
                            updateControls();
                        })
                .bounds(x + 10, attackY, 70, 20).build());

        int attackSubY = y + 190;
        attackSubButtons.put("hostile", addAttackSubButton(x + 10, attackSubY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.hostile"),
                be.isAttackHostile(), "attackHostile"));
        attackSubButtons.put("neutral", addAttackSubButton(x + 55, attackSubY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.neutral"),
                be.isAttackNeutral(), "attackNeutral"));
        attackSubButtons.put("passive", addAttackSubButton(x + 100, attackSubY,
                Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.passive"),
                be.isAttackPassive(), "attackPassive"));
        updateAttackSubButtons();

        // ---- 攻击间隔 ----
        addIntervalControl(x, y + 220);

        updateControls();
    }

    // ============================================================
    // 范围控制
    // ============================================================

    private void addRangeControl(int x, int y, Component label, String action) {
        int current = getCurrentValue(action);

        this.addRenderableWidget(Button.builder(label, btn -> {})
                .bounds(x + 10, y, 20, 20).build());

        Button minusBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.minus"),
                        btn -> {
                            if (!be.isEnabled()) performMinusAction(action);
                        })
                .bounds(x + 35, y, 20, 20).build());
        buttonActions.put(minusBtn, "minus_" + action);

        Button valueBtn = Button.builder(Component.literal(String.valueOf(current)), btn -> {})
                .bounds(x + 60, y, 30, 20).build();
        valueBtn.active = false;
        this.addRenderableWidget(valueBtn);
        valueButtonMap.put(action, valueBtn);

        Button plusBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.plus"),
                        btn -> {
                            if (!be.isEnabled()) performPlusAction(action);
                        })
                .bounds(x + 95, y, 20, 20).build());
        buttonActions.put(plusBtn, "plus_" + action);

        if (action.equals("rangeX")) {
            rangeXMinus = minusBtn;
            rangeXVal = valueBtn;
            rangeXPlus = plusBtn;
        } else if (action.equals("rangeY")) {
            rangeYMinus = minusBtn;
            rangeYVal = valueBtn;
            rangeYPlus = plusBtn;
        } else if (action.equals("rangeZ")) {
            rangeZMinus = minusBtn;
            rangeZVal = valueBtn;
            rangeZPlus = plusBtn;
        }

        // 范围仅在总开关关闭时可调整
        boolean enabled = be.isEnabled();
        minusBtn.active = !enabled;
        plusBtn.active = !enabled;
    }

    private void performMinusAction(String action) {
        if (be.isEnabled()) return;
        int newVal = Math.max(1, getCurrentValue(action) - 1);
        updateRangeValue(action, newVal);
    }

    private void performPlusAction(String action) {
        if (be.isEnabled()) return;
        int newVal = Math.min(64, getCurrentValue(action) + 1);
        updateRangeValue(action, newVal);
    }

    private int getCurrentValue(String action) {
        return switch (action) {
            case "rangeX" -> be.getRangeX();
            case "rangeY" -> be.getRangeY();
            case "rangeZ" -> be.getRangeZ();
            default -> 0;
        };
    }

    private void updateRangeValue(String action, int value) {
        switch (action) {
            case "rangeX" -> {
                be.setRangeX(value);
                sendUpdate("rangeX", value);
            }
            case "rangeY" -> {
                be.setRangeY(value);
                sendUpdate("rangeY", value);
            }
            case "rangeZ" -> {
                be.setRangeZ(value);
                sendUpdate("rangeZ", value);
            }
        }
        Button btn = valueButtonMap.get(action);
        if (btn != null) btn.setMessage(Component.literal(String.valueOf(value)));
    }

    // ============================================================
    // 子按钮
    // ============================================================

    private Button addSuppressSubButton(int x, int y, Component label, boolean initial, String action) {
        AtomicBoolean current = new AtomicBoolean(initial);
        Button btn = this.addRenderableWidget(Button.builder(
                        Component.literal(current.get() ? "✔ " : "✘ ").append(label),
                        b -> {
                            if (!be.isSuppressEnabled() || !be.isEnabled()) return;
                            boolean newVal = !current.get();
                            current.set(newVal);
                            b.setMessage(Component.literal(newVal ? "✔ " : "✘ ").append(label));
                            switch (action) {
                                case "suppressHostile" -> be.setSuppressHostile(newVal);
                                case "suppressNeutral" -> be.setSuppressNeutral(newVal);
                                case "suppressPassive" -> be.setSuppressPassive(newVal);
                            }
                            sendUpdate(action, newVal ? 1 : 0);
                        })
                .bounds(x, y, 42, 20).build());
        btn.active = be.isSuppressEnabled() && be.isEnabled();
        return btn;
    }

    private Button addAttackSubButton(int x, int y, Component label, boolean initial, String action) {
        AtomicBoolean current = new AtomicBoolean(initial);
        Button btn = this.addRenderableWidget(Button.builder(
                        Component.literal(current.get() ? "✔ " : "✘ ").append(label),
                        b -> {
                            if (be.getWeapon().isEmpty() || !be.isAutoAttack() || !be.isEnabled()) return;
                            boolean newVal = !current.get();
                            current.set(newVal);
                            b.setMessage(Component.literal(newVal ? "✔ " : "✘ ").append(label));
                            switch (action) {
                                case "attackHostile" -> be.setAttackHostile(newVal);
                                case "attackNeutral" -> be.setAttackNeutral(newVal);
                                case "attackPassive" -> be.setAttackPassive(newVal);
                            }
                            sendUpdate(action, newVal ? 1 : 0);
                        }
                )
                .bounds(x, y, 42, 20).build());
        btn.active = be.isAutoAttack() && !be.getWeapon().isEmpty() && be.isEnabled();
        return btn;
    }

    // ============================================================
    // 攻击间隔
    // ============================================================

    private void addIntervalControl(int x, int y) {
        int current = be.getAttackInterval();

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.attack_interval"),
                        btn -> {})
                .bounds(x + 10, y, 60, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.minus"),
                        btn -> {
                            if (!be.isEnabled()) return;
                            int newVal = Math.max(5, be.getAttackInterval() - 5);
                            be.setAttackInterval(newVal);
                            sendUpdate("attackInterval", newVal);
                            updateIntervalButton(newVal);
                        })
                .bounds(x + 75, y, 20, 20).build());

        intervalButton = Button.builder(
                        Component.literal(current + "t"),
                        btn -> {})
                .bounds(x + 100, y, 40, 20).build();
        intervalButton.active = false;
        this.addRenderableWidget(intervalButton);

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.plus"),
                        btn -> {
                            if (!be.isEnabled()) return;
                            int newVal = Math.min(200, be.getAttackInterval() + 5);
                            be.setAttackInterval(newVal);
                            sendUpdate("attackInterval", newVal);
                            updateIntervalButton(newVal);
                        })
                .bounds(x + 145, y, 20, 20).build());
    }

    private void updateIntervalButton(int value) {
        if (intervalButton != null) {
            intervalButton.setMessage(Component.literal(value + "t"));
        }
    }

    // ============================================================
    // 状态更新
    // ============================================================

    private void updateSuppressSubButtons() {
        boolean enabled = be.isSuppressEnabled() && be.isEnabled();
        for (Button btn : suppressSubButtons.values()) {
            btn.active = enabled;
        }
    }

    private void updateAttackSubButtons() {
        boolean enabled = be.isAutoAttack() && !be.getWeapon().isEmpty() && be.isEnabled();
        for (Button btn : attackSubButtons.values()) {
            btn.active = enabled;
        }
    }

    private void updateControls() {
        boolean enabled = be.isEnabled();

        if (litButton != null) {
            litButton.active = enabled;
            litButton.setMessage(enabled ?
                    (be.isLit() ? Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_on") :
                            Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_off")) :
                    Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.light_off"));
        }

        if (suppressToggleBtn != null) suppressToggleBtn.active = enabled;
        if (attackToggleBtn != null) attackToggleBtn.active = enabled;
        if (intervalButton != null) intervalButton.active = enabled;

        // 范围仅在总开关关闭时可调整
        if (rangeXMinus != null) {
            rangeXMinus.active = !enabled;
            rangeXPlus.active = !enabled;
        }
        if (rangeYMinus != null) {
            rangeYMinus.active = !enabled;
            rangeYPlus.active = !enabled;
        }
        if (rangeZMinus != null) {
            rangeZMinus.active = !enabled;
            rangeZPlus.active = !enabled;
        }

        updateSuppressSubButtons();
        updateAttackSubButtons();
    }

    // ============================================================
    // 网络同步
    // ============================================================

    private void sendUpdate(String action, int value) {
        SurvivalToolbox.CHANNEL.sendToServer(new UpdateGuardianLanternPacket(be.getBlockPos(), action, value));
    }

    // ============================================================
    // 鼠标事件（长按）
    // ============================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (var entry : buttonActions.entrySet()) {
            Button btn = entry.getKey();
            if (btn.active && btn.visible && btn.isMouseOver(mouseX, mouseY)) {
                pressedButton = btn;
                pressStartTime = System.currentTimeMillis();
                pressCounter = 0;
                String action = entry.getValue();
                if (action.startsWith("minus_")) {
                    performMinusAction(action.substring(6));
                } else if (action.startsWith("plus_")) {
                    performPlusAction(action.substring(5));
                }
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

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 处理长按
        if (pressedButton != null && buttonActions.containsKey(pressedButton)) {
            long elapsed = System.currentTimeMillis() - pressStartTime;
            if (elapsed >= PRESS_DELAY_MS) {
                int count = (int) ((elapsed - PRESS_DELAY_MS) / PRESS_INTERVAL_MS) + 1;
                if (count > pressCounter) {
                    String action = buttonActions.get(pressedButton);
                    if (action.startsWith("minus_")) {
                        performMinusAction(action.substring(6));
                    } else if (action.startsWith("plus_")) {
                        performPlusAction(action.substring(5));
                    }
                    pressCounter = count;
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    // ============================================================
    // 渲染背景
    // ============================================================

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        guiGraphics.drawString(font, this.title, x + 8, y + 4, 0x404040, false);

        // ---- 武器槽 ----
        int wx = x + 85;
        int wy = y + 168;
        guiGraphics.fill(wx - 1, wy - 1, wx + 17, wy + 17, 0xFF555555);
        guiGraphics.fill(wx, wy, wx + 16, wy + 16, 0xFF8B8B8B);
        guiGraphics.fill(wx + 1, wy + 1, wx + 15, wy + 15, 0xFF373737);

        ItemStack weapon = be.getWeapon();
        if (weapon.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.weapon_empty"),
                    wx + 4, wy + 4, 0xFF888888, false);
        } else {
            guiGraphics.drawString(font, weapon.getDisplayName().getString(), wx + 4, wy + 4, 0xFFFFFF, false);
        }
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.weapon"),
                wx, wy - 10, 0x404040, false);

        // ---- 黑白名单槽 ----
        int bx = x + 125;
        int by = y + 168;
        guiGraphics.fill(bx - 1, by - 1, bx + 17, by + 17, 0xFF555555);
        guiGraphics.fill(bx, by, bx + 16, by + 16, 0xFF8B8B8B);
        guiGraphics.fill(bx + 1, by + 1, bx + 15, by + 15, 0xFF373737);

        ItemStack blacklist = be.getBlacklist();
        if (blacklist.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.blacklist_empty"),
                    bx + 2, by + 4, 0xFF888888, false);
        } else {
            guiGraphics.drawString(font, blacklist.getDisplayName().getString(), bx + 2, by + 4, 0xFFFFFF, false);
        }
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.guardian_lantern.blacklist_label"),
                bx, by - 10, 0x404040, false);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}