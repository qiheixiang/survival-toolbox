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
 * 铁砧球配置界面
 * <p>
 * 配置项：
 * <ul>
 *   <li>捕获血量阈值（0.01 ~ 1.0）</li>
 *   <li>禁止捕获的实体黑名单（逗号分隔的实体 ID）</li>
 * </ul>
 * </p>
 */
public class AnvilOrbConfigScreen extends Screen {

    private final Screen parentScreen;
    private int left;
    private int top;
    private final int rowHeight = 25;

    private EditBox inputHealthThreshold;
    private EditBox inputBlacklist;
    private Button btnReset;

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

    public AnvilOrbConfigScreen(Screen parent) {
        super(Component.translatable("config.zzq_survival_toolbox.anvil_orb.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.left = (this.width - 320) / 2;
        this.top = (this.height - 200) / 2;
        if (this.top < 0) this.top = 5;
        if (this.left < 0) this.left = 5;

        int y = top + 30;
        int labelWidth = 150;
        int controlWidth = 150;
        int gap = 10;

        // ---- 返回按钮 ----
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.anvil_orb.back_btn"),
                button -> onClose()
        ).bounds(left + 10, top + 5, 60, 20).build());

        // ---- 血量阈值输入框 ----
        inputHealthThreshold = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        inputHealthThreshold.setValue(String.valueOf(ModConfig.COMMON.anvilOrbHealthThreshold.get()));
        inputHealthThreshold.setFilter(s -> s.matches("\\d*\\.?\\d*"));
        addRenderableWidget(inputHealthThreshold);

        // ---- 黑名单输入框 ----
        y += rowHeight;
        inputBlacklist = new EditBox(font, left + labelWidth + gap, y - 2, controlWidth, 20,
                Component.literal(""));
        List<? extends String> list = ModConfig.COMMON.anvilOrbBlacklist.get();
        inputBlacklist.setValue(String.join(", ", list));
        inputBlacklist.setFilter(s -> true);
        addRenderableWidget(inputBlacklist);

        // ---- 保存按钮 ----
        y += rowHeight + 10;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.anvil_orb.save_btn"),
                button -> saveConfig()
        ).bounds(left + 40, y, 240, 20).build());

        // ---- 重置按钮 ----
        y += rowHeight + 5;
        btnReset = Button.builder(
                Component.translatable("config.zzq_survival_toolbox.anvil_orb.reset_btn"),
                button -> resetToDefault()
        ).bounds(left + 40, y, 240, 20).build();
        addRenderableWidget(btnReset);

        // ---- 标签悬停区域 ----
        labelInfos.clear();
        int labelY = top + 30;

        String label1 = Component.translatable("config.zzq_survival_toolbox.anvil_orb.health_threshold").getString();
        int w1 = font.width(label1);
        labelInfos.add(new LabelInfo(left + 10, labelY + 2, w1, font.lineHeight,
                "config.zzq_survival_toolbox.anvil_orb.health_threshold.tooltip"));

        labelY += rowHeight;
        String label2 = Component.translatable("config.zzq_survival_toolbox.anvil_orb.blacklist").getString();
        int w2 = font.width(label2);
        labelInfos.add(new LabelInfo(left + 10, labelY + 2, w2, font.lineHeight,
                "config.zzq_survival_toolbox.anvil_orb.blacklist.tooltip"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(
                font,
                Component.translatable("config.zzq_survival_toolbox.anvil_orb.title"),
                left + 80,
                top + 8,
                0xFFFFFF,
                false
        );

        int y = top + 30;
        guiGraphics.drawString(font, Component.translatable("config.zzq_survival_toolbox.anvil_orb.health_threshold"),
                left + 10, y + 4, 0xAAAAAA, false);

        y += rowHeight;
        guiGraphics.drawString(font, Component.translatable("config.zzq_survival_toolbox.anvil_orb.blacklist"),
                left + 10, y + 4, 0xAAAAAA, false);

        y += rowHeight;
        guiGraphics.drawString(font, Component.translatable("config.zzq_survival_toolbox.anvil_orb.blacklist_hint"),
                left + 10, y + 4, 0x666666, false);

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

    private void saveConfig() {
        boolean corrected = false;

        try {
            double val = Double.parseDouble(inputHealthThreshold.getValue().trim());
            if (val < 0.01) {
                val = 0.01;
                corrected = true;
            }
            if (val > 1.0) {
                val = 1.0;
                corrected = true;
            }
            ModConfig.COMMON.anvilOrbHealthThreshold.set(val);
            inputHealthThreshold.setValue(String.valueOf(val));
        } catch (NumberFormatException e) {
            inputHealthThreshold.setValue(String.valueOf(ModConfig.COMMON.anvilOrbHealthThreshold.get()));
        }

        String raw = inputBlacklist.getValue().trim();
        List<String> blacklist = new ArrayList<>();
        if (!raw.isEmpty()) {
            String[] parts = raw.split("\\s*,\\s*");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    blacklist.add(part);
                }
            }
        }
        ModConfig.COMMON.anvilOrbBlacklist.set(blacklist);
        ModConfig.COMMON_SPEC.save();

        if (corrected && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                    Component.literal("§e部分数值超出范围，已自动修正")
            );
        }
        onClose();
    }

    private void resetToDefault() {
        ModConfig.COMMON.anvilOrbHealthThreshold.set(1.0);
        ModConfig.COMMON.anvilOrbBlacklist.set(new ArrayList<>());
        ModConfig.COMMON_SPEC.save();

        inputHealthThreshold.setValue("1.0");
        inputBlacklist.setValue("");

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(
                    Component.translatable("config.zzq_survival_toolbox.anvil_orb.reset_success")
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}