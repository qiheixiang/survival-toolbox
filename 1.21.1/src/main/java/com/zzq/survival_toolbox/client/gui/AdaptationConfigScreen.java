package com.zzq.survival_toolbox.client.gui;

import com.mojang.blaze3d.vertex.Tesselator;
import com.zzq.survival_toolbox.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.gui.widget.ScrollPanel;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自适应附魔配置界面
 * <p>
 * 包含所有自适应附魔相关配置项，支持滚动查看。
 * 配置项分为开关类和数值类，数值类支持输入框直接修改。
 * </p>
 */
public class AdaptationConfigScreen extends Screen {

    private final Screen parentScreen;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private static final int ROW_HEIGHT = 24;
    private static final int LABEL_WIDTH = 155;
    private static final int CONTROL_WIDTH = 200;
    private static final int GAP = 10;

    private ConfigScrollPanel scrollPanel;

    private static final String[] LABEL_KEYS = {
            "config.zzq_survival_toolbox.adaptation.label.layer_message",
            "config.zzq_survival_toolbox.adaptation.label.restore_message",
            "config.zzq_survival_toolbox.adaptation.label.show_shield_text",
            "config.zzq_survival_toolbox.adaptation.label.max_layers",
            "config.zzq_survival_toolbox.adaptation.label.gain_multiplier",
            "config.zzq_survival_toolbox.adaptation.label.layer_damage_reduction",
            "config.zzq_survival_toolbox.adaptation.label.shield_per_layer",
            "config.zzq_survival_toolbox.adaptation.label.shield_cap",
            "config.zzq_survival_toolbox.adaptation.label.shield_regen_base",
            "config.zzq_survival_toolbox.adaptation.label.shield_regen_bonus",
            "config.zzq_survival_toolbox.adaptation.label.flight_threshold",
            "config.zzq_survival_toolbox.adaptation.label.revive_threshold",
            "config.zzq_survival_toolbox.adaptation.label.revive_cost",
            "config.zzq_survival_toolbox.adaptation.label.adapt_time",
            "config.zzq_survival_toolbox.adaptation.label.adapt_time_reduction",
            "config.zzq_survival_toolbox.adaptation.label.heal_multiplier",
            "config.zzq_survival_toolbox.adaptation.label.armor_repair_per_layer",
            "config.zzq_survival_toolbox.adaptation.label.shield_bar_x",
            "config.zzq_survival_toolbox.adaptation.label.shield_bar_y",
            "config.zzq_survival_toolbox.adaptation.label.shield_bar_width",
            "config.zzq_survival_toolbox.adaptation.label.shield_bar_height"
    };

    private static final String[] TOOLTIP_KEYS = {
            "config.zzq_survival_toolbox.adaptation.tooltip.layer_message",
            "config.zzq_survival_toolbox.adaptation.tooltip.restore_message",
            "config.zzq_survival_toolbox.adaptation.tooltip.show_shield_text",
            "config.zzq_survival_toolbox.adaptation.tooltip.max_layers",
            "config.zzq_survival_toolbox.adaptation.tooltip.gain_multiplier",
            "config.zzq_survival_toolbox.adaptation.tooltip.layer_damage_reduction",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_per_layer",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_cap",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_regen_base",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_regen_bonus",
            "config.zzq_survival_toolbox.adaptation.tooltip.flight_threshold",
            "config.zzq_survival_toolbox.adaptation.tooltip.revive_threshold",
            "config.zzq_survival_toolbox.adaptation.tooltip.revive_cost",
            "config.zzq_survival_toolbox.adaptation.tooltip.adapt_time",
            "config.zzq_survival_toolbox.adaptation.tooltip.adapt_time_reduction",
            "config.zzq_survival_toolbox.adaptation.tooltip.heal_multiplier",
            "config.zzq_survival_toolbox.adaptation.tooltip.armor_repair_per_layer",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_bar_x",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_bar_y",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_bar_width",
            "config.zzq_survival_toolbox.adaptation.tooltip.shield_bar_height"
    };

    public AdaptationConfigScreen(Screen parent) {
        super(Component.translatable("config.zzq_survival_toolbox.adaptation.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.panelWidth = Math.min(380, this.width - 20);
        this.panelHeight = this.height - 100;
        this.left = (this.width - this.panelWidth) / 2;
        if (this.left < 5) this.left = 5;
        this.top = 30;

        this.scrollPanel = new ConfigScrollPanel(
                Minecraft.getInstance(),
                this.panelWidth,
                this.panelHeight,
                this.top,
                this.left
        );
        addRenderableWidget(scrollPanel);
        scrollPanel.initWidgets();

        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.adaptation.back_btn"),
                button -> onClose()
        ).bounds(this.left + 10, 5, 60, 20).build());

        int btnY = this.top + this.panelHeight + 10;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.adaptation.save_btn"),
                button -> saveConfig()
        ).bounds(this.left + 30, btnY, this.panelWidth - 60, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.adaptation.reset_btn"),
                button -> resetToDefault()
        ).bounds(this.left + 30, btnY + 28, this.panelWidth - 60, 20).build());
    }

    private class ConfigScrollPanel extends ScrollPanel {

        private final List<GuiEventListener> childWidgets = new ArrayList<>();
        private final Map<GuiEventListener, Integer> baseYMap = new HashMap<>();
        private final List<EditBox> editBoxes = new ArrayList<>();
        private final List<LabelInfo> labelInfos = new ArrayList<>();

        private Button btnLayerMessage;
        private Button btnRestoreMessage;
        private Button btnShowShieldText;

        public ConfigScrollPanel(Minecraft client, int width, int height, int top, int left) {
            super(client, width, height, top, left);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            for (GuiEventListener child : childWidgets) {
                if (child instanceof EditBox box && box.isFocused()) {
                    if (box.charTyped(codePoint, modifiers)) return true;
                }
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        protected int getContentHeight() {
            return 26 * ROW_HEIGHT + 20;
        }

        @Override
        public NarrationPriority narrationPriority() {
            return NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput narrationElementOutput) {
        }

        public void initWidgets() {
            childWidgets.clear();
            editBoxes.clear();
            labelInfos.clear();
            baseYMap.clear();

            int contentX = this.left + 10;
            int inputX = contentX + LABEL_WIDTH + GAP;
            int y = 5;
            int boxHeight = 18;

            // ---- 三个开关按钮 ----
            btnLayerMessage = Button.builder(
                    getToggleText(ModConfig.CLIENT.enableAdaptationLayerMessage.get()),
                    button -> {
                        boolean val = !ModConfig.CLIENT.enableAdaptationLayerMessage.get();
                        ModConfig.CLIENT.enableAdaptationLayerMessage.set(val);
                        button.setMessage(getToggleText(val));
                    }
            ).bounds(inputX, y, 80, 18).build();
            childWidgets.add(btnLayerMessage);
            baseYMap.put(btnLayerMessage, y);
            y += ROW_HEIGHT;

            btnRestoreMessage = Button.builder(
                    getToggleText(ModConfig.CLIENT.enableAdaptationRestoreMessage.get()),
                    button -> {
                        boolean val = !ModConfig.CLIENT.enableAdaptationRestoreMessage.get();
                        ModConfig.CLIENT.enableAdaptationRestoreMessage.set(val);
                        button.setMessage(getToggleText(val));
                    }
            ).bounds(inputX, y, 80, 18).build();
            childWidgets.add(btnRestoreMessage);
            baseYMap.put(btnRestoreMessage, y);
            y += ROW_HEIGHT;

            btnShowShieldText = Button.builder(
                    getToggleText(ModConfig.CLIENT.showShieldBarText.get()),
                    button -> {
                        boolean val = !ModConfig.CLIENT.showShieldBarText.get();
                        ModConfig.CLIENT.showShieldBarText.set(val);
                        button.setMessage(getToggleText(val));
                    }
            ).bounds(inputX, y, 80, 18).build();
            childWidgets.add(btnShowShieldText);
            baseYMap.put(btnShowShieldText, y);
            y += ROW_HEIGHT;

            // ---- 数值输入框 ----
            Object[] initialValues = {
                    ModConfig.CLIENT.adaptMaxLayers.get(),
                    ModConfig.CLIENT.adaptLayerGainMultiplier.get(),
                    ModConfig.CLIENT.adaptLayerDamageReduction.get(),
                    ModConfig.CLIENT.adaptShieldPerLayer.get(),
                    ModConfig.CLIENT.adaptShieldCap.get(),
                    ModConfig.CLIENT.adaptShieldRegenBase.get(),
                    ModConfig.CLIENT.adaptShieldRegenBonus.get(),
                    ModConfig.CLIENT.adaptFlightThreshold.get(),
                    ModConfig.CLIENT.adaptReviveThreshold.get(),
                    ModConfig.CLIENT.adaptReviveCost.get(),
                    ModConfig.CLIENT.adaptTime.get(),
                    ModConfig.CLIENT.adaptTimeReduction.get(),
                    ModConfig.CLIENT.adaptHealMultiplier.get(),
                    ModConfig.CLIENT.adaptArmorRepairPerLayer.get(),
                    ModConfig.CLIENT.shieldBarX.get(),
                    ModConfig.CLIENT.shieldBarY.get(),
                    ModConfig.CLIENT.shieldBarWidth.get(),
                    ModConfig.CLIENT.shieldBarHeight.get()
            };

            boolean[] integerFlags = {
                    false, false, false, false, false, false, false,
                    true, true, true, true, false, false, false,
                    true, true, true, true
            };

            for (int i = 0; i < initialValues.length; i++) {
                String initVal = String.valueOf(initialValues[i]);
                EditBox box = new EditBox(font, inputX, y, CONTROL_WIDTH, boxHeight, Component.literal(""));
                if (integerFlags[i]) {
                    box.setFilter(s -> s.matches("-?\\d*"));
                } else {
                    box.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
                }
                box.setValue(initVal);
                childWidgets.add(box);
                editBoxes.add(box);
                baseYMap.put(box, y);
                y += ROW_HEIGHT;
            }

            // ---- 标签悬停提示区域 ----
            int labelY = 5;
            for (int i = 0; i < LABEL_KEYS.length; i++) {
                String labelText = Component.translatable(LABEL_KEYS[i]).getString();
                int textWidth = font.width(labelText);
                labelInfos.add(new LabelInfo(contentX, labelY, textWidth, font.lineHeight, TOOLTIP_KEYS[i]));
                labelY += ROW_HEIGHT;
            }
        }

        @Override
        protected void drawPanel(GuiGraphics guiGraphics, int entryRight, int relativeY,
                                 Tesselator tess, int mouseX, int mouseY) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000);

            // ---- 标签绘制 ----
            int labelX = this.left + 10;
            int labelY = 5 + relativeY;
            for (String key : LABEL_KEYS) {
                guiGraphics.drawString(font, Component.translatable(key), labelX, labelY + 4, 0xAAAAAA, false);
                labelY += ROW_HEIGHT;
            }

            // ---- 更新控件 Y 坐标 ----
            for (GuiEventListener child : childWidgets) {
                Integer baseY = baseYMap.get(child);
                if (baseY != null) {
                    int newY = baseY + relativeY;
                    if (child instanceof Button btn) btn.setY(newY);
                    else if (child instanceof EditBox box) box.setY(newY);
                }
            }

            // ---- 悬停提示 ----
            for (LabelInfo info : labelInfos) {
                int infoY = info.y + relativeY;
                if (mouseX >= info.x && mouseX <= info.x + info.width &&
                        mouseY >= infoY && mouseY <= infoY + info.height) {
                    guiGraphics.renderTooltip(font, Component.translatable(info.tooltipKey), mouseX, mouseY);
                    break;
                }
            }

            // ---- 渲染子控件 ----
            for (GuiEventListener child : childWidgets) {
                if (child instanceof Renderable renderable) {
                    renderable.render(guiGraphics, mouseX, mouseY, 0);
                }
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return childWidgets;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = false;
            for (GuiEventListener child : childWidgets) {
                boolean isOver = child.isMouseOver(mouseX, mouseY);
                if (child instanceof EditBox box) {
                    box.setFocused(isOver);
                    handled = box.mouseClicked(mouseX, mouseY, button) || handled;
                } else if (isOver) {
                    handled = child.mouseClicked(mouseX, mouseY, button) || handled;
                }
            }
            return handled || super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            for (GuiEventListener child : childWidgets) {
                if (child instanceof EditBox box && box.isFocused()) {
                    if (box.keyPressed(keyCode, scanCode, modifiers)) return true;
                }
                if (child instanceof Button btn && btn.isFocused()) {
                    if (btn.keyPressed(keyCode, scanCode, modifiers)) return true;
                }
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private Component getToggleText(boolean value) {
            return value
                    ? Component.translatable("config.zzq_survival_toolbox.toggle.on")
                    : Component.translatable("config.zzq_survival_toolbox.toggle.off");
        }
    }

    private void resetToDefault() {
        ModConfig.resetAdaptationToDefault();

        scrollPanel.btnLayerMessage.setMessage(
                scrollPanel.getToggleText(ModConfig.CLIENT.enableAdaptationLayerMessage.get()));
        scrollPanel.btnRestoreMessage.setMessage(
                scrollPanel.getToggleText(ModConfig.CLIENT.enableAdaptationRestoreMessage.get()));
        scrollPanel.btnShowShieldText.setMessage(
                scrollPanel.getToggleText(ModConfig.CLIENT.showShieldBarText.get()));

        List<EditBox> boxes = scrollPanel.editBoxes;
        if (boxes.size() >= 18) {
            boxes.get(0).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_MAX_LAYERS));
            boxes.get(1).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_LAYER_GAIN_MULTIPLIER));
            boxes.get(2).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_LAYER_DAMAGE_REDUCTION));
            boxes.get(3).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_SHIELD_PER_LAYER));
            boxes.get(4).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_SHIELD_CAP));
            boxes.get(5).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_SHIELD_REGEN_BASE));
            boxes.get(6).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_SHIELD_REGEN_BONUS));
            boxes.get(7).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_FLIGHT_THRESHOLD));
            boxes.get(8).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_REVIVE_THRESHOLD));
            boxes.get(9).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_REVIVE_COST));
            boxes.get(10).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_TIME));
            boxes.get(11).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_TIME_REDUCTION));
            boxes.get(12).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_HEAL_MULTIPLIER));
            boxes.get(13).setValue(String.valueOf(ModConfig.DEFAULT_ADAPT_ARMOR_REPAIR_PER_LAYER));
            boxes.get(14).setValue(String.valueOf(ModConfig.DEFAULT_SHIELD_BAR_X));
            boxes.get(15).setValue(String.valueOf(ModConfig.DEFAULT_SHIELD_BAR_Y));
            boxes.get(16).setValue(String.valueOf(ModConfig.DEFAULT_SHIELD_BAR_WIDTH));
            boxes.get(17).setValue(String.valueOf(ModConfig.DEFAULT_SHIELD_BAR_HEIGHT));
        }
    }

    private void saveConfig() {
        List<EditBox> boxes = scrollPanel.editBoxes;
        if (boxes.size() >= 18) {
            setDoubleWithRange(ModConfig.CLIENT.adaptMaxLayers, boxes.get(0), 0, Float.MAX_VALUE);
            setDoubleWithRange(ModConfig.CLIENT.adaptLayerGainMultiplier, boxes.get(1), 0, 100);
            setDoubleWithRange(ModConfig.CLIENT.adaptLayerDamageReduction, boxes.get(2), 0.0, 1.0);
            setDoubleWithRange(ModConfig.CLIENT.adaptShieldPerLayer, boxes.get(3), 0, 1000);
            setDoubleWithRange(ModConfig.CLIENT.adaptShieldCap, boxes.get(4), 0, 1_000_000_000);
            setDoubleWithRange(ModConfig.CLIENT.adaptShieldRegenBase, boxes.get(5), 0, 1000);
            setDoubleWithRange(ModConfig.CLIENT.adaptShieldRegenBonus, boxes.get(6), 0, 100);
            setIntWithRange(ModConfig.CLIENT.adaptFlightThreshold, boxes.get(7), 0, Integer.MAX_VALUE);
            setIntWithRange(ModConfig.CLIENT.adaptReviveThreshold, boxes.get(8), 0, Integer.MAX_VALUE);
            setIntWithRange(ModConfig.CLIENT.adaptReviveCost, boxes.get(9), 0, Integer.MAX_VALUE);
            setIntWithRange(ModConfig.CLIENT.adaptTime, boxes.get(10), 1, 600);
            setDoubleWithRange(ModConfig.CLIENT.adaptTimeReduction, boxes.get(11), 0, 100);
            setDoubleWithRange(ModConfig.CLIENT.adaptHealMultiplier, boxes.get(12), 0, 100);
            setDoubleWithRange(ModConfig.CLIENT.adaptArmorRepairPerLayer, boxes.get(13), 0, 100);
            setIntWithRange(ModConfig.CLIENT.shieldBarX, boxes.get(14), -1, 4096);
            setIntWithRange(ModConfig.CLIENT.shieldBarY, boxes.get(15), -1, 4096);
            setIntWithRange(ModConfig.CLIENT.shieldBarWidth, boxes.get(16), 0, 4096);
            setIntWithRange(ModConfig.CLIENT.shieldBarHeight, boxes.get(17), 0, 256);
        }
        ModConfig.CLIENT_SPEC.save();
        onClose();
    }

    private void setDoubleWithRange(ModConfigSpec.DoubleValue config, EditBox box, double min, double max) {
        try {
            double val = Double.parseDouble(box.getValue().trim());
            if (val < min) {
                val = min;
                box.setValue(String.valueOf(min));
            } else if (val > max) {
                val = max;
                box.setValue(String.valueOf(max));
            }
            config.set(val);
        } catch (NumberFormatException ignored) {
            box.setValue(String.valueOf(config.get()));
        }
    }

    private void setIntWithRange(ModConfigSpec.IntValue config, EditBox box, int min, int max) {
        try {
            int val = Integer.parseInt(box.getValue().trim());
            if (val < min) {
                val = min;
                box.setValue(String.valueOf(min));
            } else if (val > max) {
                val = max;
                box.setValue(String.valueOf(max));
            }
            config.set(val);
        } catch (NumberFormatException ignored) {
            box.setValue(String.valueOf(config.get()));
        }
    }

    private static class LabelInfo {
        int x, y, width, height;
        String tooltipKey;

        LabelInfo(int x, int y, int width, int height, String tooltipKey) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.tooltipKey = tooltipKey;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawString(font,
                Component.translatable("config.zzq_survival_toolbox.adaptation.title"),
                this.left + (this.panelWidth / 2) - 40, 10, 0xFFFFFF, false);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}