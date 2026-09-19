package com.zzq.survival_toolbox.mixin;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MerchantScreen Mixin（仅客户端）：原版报价列表"变短"之后的收尾（交易机搜索用）
 * <p>
 * 原版村民界面的报价列表在界面开着时**长度不会变**，所以原版有两处"只在变短时才出问题"的地方：
 * </p>
 * <ol>
 *   <li><b>选中项越界（会崩游戏）</b>：{@code MerchantScreen#render} 里那句
 *       {@code merchantOffers.get(this.shopItem)} **没有边界检查**（{@code shopItem}
 *       = 玩家最后点的那条报价，1.20.1 与 1.21.1 都一样）。
 *       交易机搜索会把列表直接缩短（服务端按关键词重建后重发原版报价包），
 *       于是"先点第 8 条报价 → 再输关键词"就在这句上 IndexOutOfBoundsException，
 *       而且发生在渲染线程，游戏直接崩。</li>
 *   <li><b>滚动位置越界（列表一片空白）</b>：{@code scrollOff} 只在鼠标滚轮那一下
 *       （{@code mouseScrolled}）夹到 {@code 列表长度 - 7}；列表变短后它可能还停在旧值，
 *       原版渲染条件是 {@code i1 >= scrollOff && i1 < 7 + scrollOff}，
 *       于是"搜索结果有 20 条、scrollOff 还停在 25"就会**一行都不画**，看起来像坏了
 *       （列表 ≤ 7 条时原版自己会全画，不受影响）。</li>
 * </ol>
 * <p>
 * 这里在 render 开头把两个值夹回合法范围。对普通村民界面无影响：
 * 它们的列表不会变短，这两个 if 永远不成立。
 * </p>
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {

    /** 原版"当前选中的报价"（私有字段、没有 setter，只能 @Shadow） */
    @Shadow
    private int shopItem;

    /** 原版报价列表的滚动位置（包级私有字段，访问级别要和原版一致） */
    @Shadow
    int scrollOff;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void zzq_fixStaleSelection(CallbackInfo ci) {
        MerchantMenu menu = ((MerchantScreen) (Object) this).getMenu();
        if (menu == null) return;
        int size = menu.getOffers().size();
        // 1) 选中项越界 → 按回第一条（列表被过滤空时原版自己会跳过那一段）
        if (this.shopItem > 0 && this.shopItem >= size) {
            this.shopItem = 0;
        }
        // 2) 滚动位置越界 → 夹到原版自己用的上限（7 是同时可见的报价条数）
        int maxScroll = Math.max(0, size - 7);
        if (this.scrollOff > maxScroll) {
            this.scrollOff = maxScroll;
        }
    }
}
