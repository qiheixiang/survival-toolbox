package com.zzq.survival_toolbox.client.gui;

import com.zzq.survival_toolbox.ModConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 模组主配置界面
 * <p>
 * 提供进入各子配置界面的入口：
 * <ul>
 *   <li>嗜血附魔配置</li>
 *   <li>自适应附魔配置</li>
 *   <li>铁砧球配置</li>
 * </ul>
 * </p>
 */
public class MainConfigScreen extends Screen {

    private final Screen parentScreen;
    private int left;
    private int top;
    private final int rowHeight = 30;

    public MainConfigScreen(Screen parent) {
        super(Component.translatable("config.zzq_survival_toolbox.title"));
        this.parentScreen = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.left = (this.width - 260) / 2;
        this.top = (this.height - 120) / 2;
        if (this.top < 0) this.top = 10;
        if (this.left < 0) this.left = 10;

        int y = top + 30;
        int btnWidth = 200;
        int btnHeight = 24;

        // ---- 返回按钮 ----
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty.back_btn"),
                button -> onClose()
        ).bounds(left + 10, top + 5, 60, 20).build());

        // ---- 嗜血配置按钮 ----
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.bloodthirsty_btn"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new BloodthirstyConfigScreen(this));
                    }
                }
        ).bounds(left + (260 - btnWidth) / 2, y, btnWidth, btnHeight).build());

        // ---- 自适应配置按钮 ----
        y += rowHeight;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.adaptation_btn"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AdaptationConfigScreen(this));
                    }
                }
        ).bounds(left + (260 - btnWidth) / 2, y, btnWidth, btnHeight).build());

        // ---- 铁砧球配置按钮 ----
        y += rowHeight;
        addRenderableWidget(Button.builder(
                Component.translatable("config.zzq_survival_toolbox.anvil_orb_btn"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AnvilOrbConfigScreen(this));
                    }
                }
        ).bounds(left + (260 - btnWidth) / 2, y, btnWidth, btnHeight).build());

        // ---- 配置文件位置提示（静态文字，不可点击） ----
        y += rowHeight * 2;
        Button configPathHint = Button.builder(
                Component.literal(".minecraft/config/zzq_survival_toolbox-common.toml"),
                button -> {}
        ).bounds(left + (260 - btnWidth) / 2, y, btnWidth, btnHeight).build();
        configPathHint.active = false;  // 不可点击
        addRenderableWidget(configPathHint);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawString(
                font,
                Component.translatable("config.zzq_survival_toolbox.title"),
                left + 80,
                top + 8,
                0xFFFFFF,
                false
        );

        guiGraphics.drawString(
                font,
                Component.translatable("config.zzq_survival_toolbox.subtitle"),
                left + 80,
                top + 8 + font.lineHeight + 2,
                0xAAAAAA,
                false
        );
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }
}