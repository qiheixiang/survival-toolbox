package com.zzq.survival_toolbox.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * AbstractContainerScreen Mixin（仅客户端）：把格子右下角那颗数量文字换成本实现提供的文案
 * <p>
 * 通过 {@code @Redirect} 重定向那次 {@code renderItemDecorations} 调用：
 * 在**原版画数字之前**把字符串换掉 —— 绘制时机 / 位置 / 样式 / 阴影全跟原版一模一样，
 * 只有一个数字，不会出现"两个数字叠着"或者"缩写被原版数字盖住"。
 * </p>
 * <p>
 * ⚠️⚠️ 注入点在两个版本里**不在同一个方法里**，写错了游戏直接启动崩溃
 * （曾出现过：{@code InjectionError: ... failed injection check, (0/1) succeeded. Scanned 0 target(s).}）：
 * <ul>
 *   <li><b>1.21.1</b>：{@code renderSlot} 已经不画物品了，它把画格子这件事整个交给
 *       {@code renderSlotContents(GuiGraphics, ItemStack, Slot, String)}，那次
 *       {@code renderItemDecorations} 调用在 <b>renderSlotContents</b> 里（用 javap 反编译确认过）。</li>
 *   <li><b>1.20.1</b>：没有 {@code renderSlotContents} 这个东西，调用就在
 *       {@code renderSlot} 里（SRG: {@code m_280092_} → {@code m_280302_}，靠 refmap 映射）。</li>
 * </ul>
 * 两边源码看似一样、其实 {@code method = } 必须不同；改这里之前先用 javap 核对真实字节码。
 * </p>
 * <p>
 * ⚠️ 请勿改成"自己再画一个缩写在上面"（曾出现过：那个覆盖框又小又半透明，19456 这类长数字根本盖不住，
 * 结果玩家看到的是原样数字）。文案统一走 {@code PocketStorageHelper.formatItemCount}。
 * </p>
 * <p>
 * 对其它界面无副作用：普通 GUI 的堆叠上限是 64，替换出来的字符串跟原版完全一样。
 * </p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // 1.21.1：renderItemDecorations 的调用点在 renderSlotContents 里（不是 renderSlot！）
    @Redirect(method = "renderSlotContents(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/inventory/Slot;Ljava/lang/String;)V",
              at = @At(value = "INVOKE",
                      target = "Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private void zzq_countText(GuiGraphics gui, Font font, ItemStack stack, int x, int y, String altText) {
        // ⚠️⚠️ **只改本 mod 自己的次元袋界面**，其它界面（箱子 / 玩家背包 / 别的 mod）原样交回原版。
        //     需求：数字缩放只局限于次元袋界面，外面的数字不要动，不要大范围全改
        //     —— 上一版是全局替换，结果每个容器界面的数字都跟着变小，还和原版那个数字叠成了两个。
        //     另外空栈直接交回原版（原版内部有 !isEmpty() 守卫；自行绘制会画出 "0"）。
        if (stack.isEmpty()
                || !(net.minecraft.client.Minecraft.getInstance().screen
                        instanceof com.zzq.survival_toolbox.screen.PocketDimensionScreen)) {
            gui.renderItemDecorations(font, stack, x, y, altText);
            return;
        }
        // 次元袋界面里：数量文字由本 mixin 自绘（0.75 倍、右对齐进格子）
        boolean ownText = altText == null && stack.getCount() != 1;
        // ⚠️ 原生这次必须传**空串**：传 null 时原版会自己算一遍数量并画出来
        //    （`String s = text == null ? String.valueOf(stack.getCount()) : text;`），
        //    于是会和自绘的那个叠成"两个数字"（实测）。空串宽度为 0，等于什么都不画。
        gui.renderItemDecorations(font, stack, x, y, ownText ? "" : altText);
        if (!ownText) return;
        String text = com.zzq.survival_toolbox.util.PocketStorageHelper.formatItemCount(stack.getCount());
        if (text == null || text.isEmpty()) return;
        // 自绘数量：缩小到 0.75 倍再右对齐到格子右下角。
        // 实测：像 "1.7k" 这种长度按原版字号会顶出格子里、侵入相邻格子。
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(x + 17.0F, y + 17.0F, 200.0F);
        pose.scale(0.75F, 0.75F, 1.0F);
        gui.drawString(font, text, -font.width(text), -font.lineHeight, 0xFFFFFF, true);
        pose.popPose();
    }
}
