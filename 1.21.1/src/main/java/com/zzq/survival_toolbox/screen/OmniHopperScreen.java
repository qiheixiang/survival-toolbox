package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.OmniHopperBlockEntity;
import com.zzq.survival_toolbox.util.DirectionConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 万向漏斗主 GUI 界面
 * <p>
 * 显示 27 格缓存区，以及六个方向的控制按钮。
 * 点击方向按钮进入对应的方向配置子界面。
 * </p>
 */
public class OmniHopperScreen extends AbstractContainerScreen<OmniHopperMenu> {

    private final OmniHopperBlockEntity be;
    private final Button[] directionButtons = new Button[6];
    private static final int[] DISPLAY_INDICES = {0, 1, 2, 3, 4, 5};

    private static final String[] DISPLAY_KEYS = {
            "gui.zzq_survival_toolbox.omni_hopper.up",
            "gui.zzq_survival_toolbox.omni_hopper.down",
            "gui.zzq_survival_toolbox.omni_hopper.front",
            "gui.zzq_survival_toolbox.omni_hopper.back",
            "gui.zzq_survival_toolbox.omni_hopper.left",
            "gui.zzq_survival_toolbox.omni_hopper.right"
    };

    public OmniHopperScreen(OmniHopperMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.be = menu.getBlockEntity();
    }

    /**
     * 根据显示索引和玩家放置朝向计算世界方向
     */
    private Direction displayIndexToWorldDirection(int displayIndex, Direction playerFacing) {
        return switch (displayIndex) {
            case 0 -> Direction.UP;
            case 1 -> Direction.DOWN;
            case 2 -> playerFacing;
            case 3 -> playerFacing.getOpposite();
            case 4 -> playerFacing.getCounterClockWise();
            case 5 -> playerFacing.getClockWise();
            default -> Direction.NORTH;
        };
    }

    private Component getModeDisplayText(DirectionConfig.Mode mode) {
        return switch (mode) {
            case IN -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.in");
            case OUT -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.out");
            default -> Component.translatable("gui.zzq_survival_toolbox.omni_hopper.mode.blocked");
        };
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        Direction playerFacing = be.getPlayerFacing();
        if (playerFacing == null) playerFacing = Direction.NORTH;

        var configs = be.getAllConfigs();

        for (int i = 0; i < 6; i++) {
            Direction worldDir = displayIndexToWorldDirection(i, playerFacing);
            DirectionConfig cfg = configs.get(worldDir);
            int btnId = i;
            Component dirLabel = Component.translatable(DISPLAY_KEYS[i]);

            Button btn = Button.builder(
                    Component.literal("").append(dirLabel).append(": ").append(getModeDisplayText(cfg.getMode())),
                    b -> {
                        if (this.minecraft != null && this.minecraft.gameMode != null) {
                            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, btnId);
                        }
                    }
            ).bounds(x + 8 + (i % 3) * 52, y + 18 + (i / 3) * 22, 48, 18).build();
            addRenderableWidget(btn);
            directionButtons[i] = btn;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        guiGraphics.fill(x + 7, y + 7, x + imageWidth - 7, y + imageHeight - 7, 0xFF8B8B8B);

        guiGraphics.drawString(font, this.title, x + 8, y + 4, 0x404040, false);
        guiGraphics.drawString(font, Component.translatable("gui.zzq_survival_toolbox.omni_hopper.cache"),
                x + 8, y + 65, 0x404040, false);

        // ---- 缓存区槽位 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + 77 + row * 18;
                drawSlotBackground(guiGraphics, sx, sy);
            }
        }

        // ---- 玩家背包 ----
        guiGraphics.drawString(font, this.playerInventoryTitle, x + 8, y + 130, 0x404040, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = x + 8 + col * 18;
                int sy = y + 140 + row * 18;
                drawSlotBackground(guiGraphics, sx, sy);
            }
        }
        for (int col = 0; col < 9; col++) {
            int sx = x + 8 + col * 18;
            int sy = y + 198;
            drawSlotBackground(guiGraphics, sx, sy);
        }
    }

    private void drawSlotBackground(GuiGraphics guiGraphics, int sx, int sy) {
        guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF555555);
        guiGraphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF373737);
        guiGraphics.fill(sx, sy, sx + 18, sy + 1, 0xFFFFFFFF);
        guiGraphics.fill(sx, sy, sx + 1, sy + 18, 0xFFFFFFFF);
        guiGraphics.fill(sx + 17, sy, sx + 18, sy + 18, 0xFF555555);
        guiGraphics.fill(sx, sy + 17, sx + 18, sy + 18, 0xFF555555);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Direction playerFacing = be.getPlayerFacing();
        if (playerFacing == null) playerFacing = Direction.NORTH;
        var configs = be.getAllConfigs();

        // ---- 更新按钮文字 ----
        for (int i = 0; i < 6; i++) {
            Direction worldDir = displayIndexToWorldDirection(i, playerFacing);
            DirectionConfig cfg = configs.get(worldDir);
            Component dirLabel = Component.translatable(DISPLAY_KEYS[i]);
            directionButtons[i].setMessage(
                    Component.literal("").append(dirLabel).append(": ").append(getModeDisplayText(cfg.getMode()))
            );
        }

        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 标签在 renderBg 中绘制
    }
}