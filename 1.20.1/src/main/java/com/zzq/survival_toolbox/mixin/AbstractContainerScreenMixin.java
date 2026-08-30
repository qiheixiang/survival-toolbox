package com.zzq.survival_toolbox.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AbstractContainerScreen Mixin（仅客户端）
 * <p>
 * 次元袋格子右下角堆叠数字 ≥1000 时替换为紧凑缩写（k/万/亿）。
 * 通过 @Redirect 重定向 renderSlot 内部的 renderItemDecorations 调用：
 * 在原版画数字之前把字符串换成缩写，绘制时机/位置/样式与原版完全一致，
 * 只有一个数字、不会出现覆盖残留或跑到图标后面的问题。
 * </p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Redirect(method = "renderSlot(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/inventory/Slot;)V",
              at = @At(value = "INVOKE",
                      target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void zzq_abbreviateDecorations(GuiGraphics gui, Font font, ItemStack stack, int x, int y, String altText) {
        if (altText == null && stack.getCount() >= 1000) {
            gui.renderItemDecorations(font, stack, x, y, abbreviate(stack.getCount()));
        } else {
            gui.renderItemDecorations(font, stack, x, y, altText);
        }
    }

    /** 数量缩写：≥1k 显示 k，≥1万 显示万，≥1亿 显示亿（最多 1 位小数，去尾零） */
    private static String abbreviate(long count) {
        if (count < 10000L) return trimZero(count / 1000.0) + "k";
        if (count < 100000000L) return trimZero(count / 10000.0) + "万";
        return trimZero(count / 100000000.0) + "亿";
    }

    private static String trimZero(double v) {
        long tenths = Math.round(v * 10);
        if (tenths % 10 == 0) return String.valueOf(tenths / 10);
        return (tenths / 10) + "." + (tenths % 10);
    }
}
