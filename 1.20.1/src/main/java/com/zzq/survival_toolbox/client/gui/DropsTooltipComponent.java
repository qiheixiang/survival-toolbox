package com.zzq.survival_toolbox.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 掉落列表 Tooltip 组件
 * <p>
 * 在 {@link com.zzq.survival_toolbox.item.CapturedEntityItem} 的 Tooltip 中，
 * 以两列网格形式渲染掉落物品的图标和名称。
 * </p>
 */
public class DropsTooltipComponent implements TooltipComponent, ClientTooltipComponent {

    private final List<ItemStack> drops;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXT_GAP = 2;
    private static final int ROW_HEIGHT = 18;
    private static final int COLUMN_GAP = 12;

    private int maxWidthCol1 = -1;
    private int maxWidthCol2 = -1;
    private int totalWidth = -1;
    private int totalRows = -1;

    public DropsTooltipComponent(List<ItemStack> drops) {
        this.drops = drops;
    }

    private void computeWidths(Font font) {
        if (maxWidthCol1 != -1) return;

        int max1 = 0, max2 = 0;
        int effectiveCount = 0;

        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i);
            if (stack == null || stack.isEmpty()) continue;

            effectiveCount++;
            int nameWidth = font.width(stack.getDisplayName());
            int itemWidth = ICON_SIZE + ICON_TEXT_GAP + nameWidth;

            if (i % 2 == 0) {
                if (itemWidth > max1) max1 = itemWidth;
            } else {
                if (itemWidth > max2) max2 = itemWidth;
            }
        }

        maxWidthCol1 = max1;
        maxWidthCol2 = max2;
        totalRows = (int) Math.ceil((double) effectiveCount / 2);

        if (effectiveCount <= 1) {
            totalWidth = max1;
        } else if (max2 == 0) {
            totalWidth = max1;
        } else {
            totalWidth = max1 + COLUMN_GAP + max2;
        }
    }

    @Override
    public int getHeight() {
        if (drops == null || drops.isEmpty()) return 0;
        long count = drops.stream().filter(s -> s != null && !s.isEmpty()).count();
        if (count == 0) return 0;
        int rows = (int) Math.ceil((double) count / 2);
        return rows * ROW_HEIGHT + 2;
    }

    @Override
    public int getWidth(Font font) {
        if (drops == null || drops.isEmpty()) return 0;
        computeWidths(font);
        return totalWidth;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        if (drops == null || drops.isEmpty()) return;
        computeWidths(font);

        int index = 0;
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;

            int row = index / 2;
            int col = index % 2;
            int posX = x + (col == 0 ? 0 : maxWidthCol1 + COLUMN_GAP);
            int posY = y + row * ROW_HEIGHT + 1;

            guiGraphics.renderItem(stack, posX, posY);
            if (stack.getCount() > 1) {
                guiGraphics.renderItemDecorations(font, stack, posX, posY, String.valueOf(stack.getCount()));
            }
            index++;
        }
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix4f, MultiBufferSource.BufferSource bufferSource) {
        if (drops == null || drops.isEmpty()) return;
        computeWidths(font);

        PoseStack poseStack = new PoseStack();
        poseStack.mulPoseMatrix(matrix4f);

        int index = 0;
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;

            int row = index / 2;
            int col = index % 2;
            int textX = x + (col == 0 ? 0 : maxWidthCol1 + COLUMN_GAP) + ICON_SIZE + ICON_TEXT_GAP;
            int textY = y + row * ROW_HEIGHT + 2;

            font.drawInBatch(
                    stack.getDisplayName().getString(),
                    textX,
                    textY,
                    0xFFFFFF,
                    true,
                    poseStack.last().pose(),
                    bufferSource,
                    Font.DisplayMode.NORMAL,
                    0,
                    15728880
            );
            index++;
        }

        bufferSource.endBatch();
    }
}