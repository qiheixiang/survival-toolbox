package com.zzq.survival_toolbox.client.gui;

import com.zzq.survival_toolbox.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 嗜血附魔配置界面
 * <p>
 * 配置项：
 * <ul>
 *   <li>击杀消息开关</li>
 *   <li>攻击恢复消息开关</li>
 *   <li>击杀吸收倍数</li>
 *   <li>攻击力加成上限</li>
 *   <li>回血比例</li>
 *   <li>修复耐久倍数</li>
 * </ul>
 * </p>
 */
public class BloodthirstyConfigScreen extends Screen {

    private final Screen parentScreen;
    private int left;
    private int top;
    private final int rowHeight = 25;

    private Button btnKillMessage;
    private Button btnAttackMessage;
    private EditBox inputAbsorbMultiplier;
    private EditBox inputMaxBonus;
    private EditBox inputHealMultiplier;
    private EditBox inputRepairMultiplier;

    private final List<LabelInfo> labelInfos = new ArrayList<>();

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

    public BloodthirstyConfigScreen(Screen parent) {
        super(Component.translatable("config.zzq_survival_toolbox.bloodthirsty.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.left = (this.width - 320) / 2;
        this.top = (this.height - 340) / 2;
        if (this.top < 0) this.top = 5;
        if (this.left < 0) this.left = 5;

        int y = top + 30;
        int labelWidth = 140;
        int controlWidth = 150;
        int gap = 10;

        // ---- 返回按钮 ----
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty.back_btn"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parentScreen);
                    }
                }
        ).bounds(left + 10, top + 5, 60, 20).build());

        // ---- 杀怪提示开关 ----
        btnKillMessage = Button.builder(
                getToggleText(ModConfig.CLIENT.enableKillMessage.get()),
                button -> {
                    boolean val = !ModConfig.CLIENT.enableKillMessage.get();
                    ModConfig.CLIENT.enableKillMessage.set(val);
                    button.setMessage(getToggleText(val));
                }
        ).bounds(left + labelWidth + gap, y, 80, 20).build();
        addRenderableWidget(btnKillMessage);

        // ---- 攻击恢复提示开关 ----
        y += rowHeight;
        btnAttackMessage = Button.builder(
                getToggleText(ModConfig.CLIENT.enableAttackMessage.get()),
                button -> {
                    boolean val = !ModConfig.CLIENT.enableAttackMessage.get();
                    ModConfig.CLIENT.enableAttackMessage.set(val);
                    button.setMessage(getToggleText(val));
                }
        ).bounds(left + labelWidth + gap, y, 80, 20).build();
        addRenderableWidget(btnAttackMessage);

        // ---- 杀怪吸收倍数 ----
        y += rowHeight;
        inputAbsorbMultiplier = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        inputAbsorbMultiplier.setValue(String.valueOf(ModConfig.CLIENT.killAbsorbMultiplier.get()));
        inputAbsorbMultiplier.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        addRenderableWidget(inputAbsorbMultiplier);

        // ---- 攻击力上限 ----
        y += rowHeight;
        inputMaxBonus = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        inputMaxBonus.setValue(String.valueOf(ModConfig.CLIENT.maxBonus.get()));
        inputMaxBonus.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        addRenderableWidget(inputMaxBonus);

        // ---- 回血比例 ----
        y += rowHeight;
        inputHealMultiplier = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        inputHealMultiplier.setValue(String.valueOf(ModConfig.CLIENT.healMultiplier.get()));
        inputHealMultiplier.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        addRenderableWidget(inputHealMultiplier);

        // ---- 修复耐久倍数 ----
        y += rowHeight;
        inputRepairMultiplier = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        inputRepairMultiplier.setValue(String.valueOf(ModConfig.CLIENT.repairMultiplier.get()));
        inputRepairMultiplier.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        addRenderableWidget(inputRepairMultiplier);

        // ---- 保存按钮 ----
        y += rowHeight + 10;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty.save_btn"),
                button -> {
                    saveConfig();
                    onClose();
                }
        ).bounds(left + 40, y, 240, 20).build());

        // ---- 重置按钮 ----
        y += rowHeight + 5;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty.reset_btn"),
                button -> resetToDefault()
        ).bounds(left + 40, y, 240, 20).build());

        // ---- 标签悬停区域 ----
        labelInfos.clear();
        int labelY = top + 30;

        String[] labelKeys = {
                "config.zzq_survival_toolbox.bloodthirsty.label.kill_message",
                "config.zzq_survival_toolbox.bloodthirsty.label.attack_message",
                "config.zzq_survival_toolbox.bloodthirsty.label.absorb_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.label.max_bonus",
                "config.zzq_survival_toolbox.bloodthirsty.label.heal_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.label.repair_multiplier"
        };
        String[] tooltipKeys = {
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.kill_message",
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.attack_message",
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.absorb_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.max_bonus",
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.heal_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.tooltip.repair_multiplier"
        };

        for (int i = 0; i < labelKeys.length; i++) {
            String labelText = Component.translatable(labelKeys[i]).getString();
            int textWidth = font.width(labelText);
            labelInfos.add(new LabelInfo(
                    left + 10,
                    labelY + 2,
                    textWidth,
                    font.lineHeight,
                    tooltipKeys[i]
            ));
            labelY += rowHeight;
        }
    }

    private Component getToggleText(boolean value) {
        return value
                ? Component.translatable("config.zzq_survival_toolbox.toggle.on")
                : Component.translatable("config.zzq_survival_toolbox.toggle.off");
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(
                font,
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty.title"),
                left + 80,
                top + 8,
                0xFFFFFF,
                false
        );

        int y = top + 30;
        String[] labelKeys = {
                "config.zzq_survival_toolbox.bloodthirsty.label.kill_message",
                "config.zzq_survival_toolbox.bloodthirsty.label.attack_message",
                "config.zzq_survival_toolbox.bloodthirsty.label.absorb_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.label.max_bonus",
                "config.zzq_survival_toolbox.bloodthirsty.label.heal_multiplier",
                "config.zzq_survival_toolbox.bloodthirsty.label.repair_multiplier"
        };

        for (String key : labelKeys) {
            guiGraphics.drawString(font, Component.translatable(key), left + 10, y + 4, 0xAAAAAA, false);
            y += rowHeight;
        }

        for (LabelInfo info : labelInfos) {
            if (mouseX >= info.x && mouseX <= info.x + info.width &&
                    mouseY >= info.y && mouseY <= info.y + info.height) {
                guiGraphics.renderTooltip(
                        font,
                        Component.translatable(info.tooltipKey),
                        mouseX,
                        mouseY
                );
                break;
            }
        }
    }

    private void resetToDefault() {
        ModConfig.resetBloodthirstyToDefault();

        btnKillMessage.setMessage(getToggleText(ModConfig.DEFAULT_ENABLE_KILL_MESSAGE));
        btnAttackMessage.setMessage(getToggleText(ModConfig.DEFAULT_ENABLE_ATTACK_MESSAGE));
        inputAbsorbMultiplier.setValue(String.valueOf(ModConfig.DEFAULT_KILL_ABSORB_MULTIPLIER));
        inputMaxBonus.setValue(String.valueOf(ModConfig.DEFAULT_MAX_BONUS));
        inputHealMultiplier.setValue(String.valueOf(ModConfig.DEFAULT_HEAL_MULTIPLIER));
        inputRepairMultiplier.setValue(String.valueOf(ModConfig.DEFAULT_REPAIR_MULTIPLIER));

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                    Component.translatable("config.zzq_survival_toolbox.bloodthirsty.reset_success")
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    private void saveConfig() {
        boolean corrected = false;

        try {
            double absorb = Double.parseDouble(inputAbsorbMultiplier.getValue());
            if (absorb < 0 || absorb > 100) {
                absorb = Math.min(100, Math.max(0, absorb));
                inputAbsorbMultiplier.setValue(String.valueOf(absorb));
                corrected = true;
            }
            ModConfig.CLIENT.killAbsorbMultiplier.set(absorb);
        } catch (NumberFormatException ignored) {
        }

        try {
            double max = Double.parseDouble(inputMaxBonus.getValue());
            if (max < 0) max = 0;
            ModConfig.CLIENT.maxBonus.set(max);
        } catch (NumberFormatException ignored) {
        }

        try {
            double heal = Double.parseDouble(inputHealMultiplier.getValue());
            if (heal < 0 || heal > 100) {
                heal = Math.min(100, Math.max(0, heal));
                inputHealMultiplier.setValue(String.valueOf(heal));
                corrected = true;
            }
            ModConfig.CLIENT.healMultiplier.set(heal);
        } catch (NumberFormatException ignored) {
        }

        try {
            double repair = Double.parseDouble(inputRepairMultiplier.getValue());
            if (repair < 0 || repair > 100) {
                repair = Math.min(100, Math.max(0, repair));
                inputRepairMultiplier.setValue(String.valueOf(repair));
                corrected = true;
            }
            ModConfig.CLIENT.repairMultiplier.set(repair);
        } catch (NumberFormatException ignored) {
        }

        ModConfig.CLIENT_SPEC.save();

        if (corrected && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                    Component.literal("§e部分数值超出范围，已自动修正")
            );
        }
    }
}