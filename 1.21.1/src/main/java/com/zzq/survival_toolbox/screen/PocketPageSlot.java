package com.zzq.survival_toolbox.screen;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 功能页槽位（托盘之外的所有页：熔炉 / 铁砧 / 锻造台 ……）
 * <p>
 * 与托盘槽位同一个套路：{@code Slot.x/y} 是 final 的，面板收起时靠 {@link #isActive()} 让原版
 * 既不渲染也不响应悬停；{@link #mayPickup}/{@link #mayPlace} 也一起看它，
 * 免得双击收集（原版 PICKUP_ALL 只查 mayPickup）在面板收起时把格子里的东西拿走。
 * </p>
 * <p>
 * {@code result = true} 表示这是"产物格"（铁砧/锻造台）：原版算出来的东西只能看，
 * {@link #mayPickup} 直接 false，取走由菜单拦截后调用原版 {@code onTake} 完成（不会刷物品）。
 * </p>
 */
public class PocketPageSlot extends Slot {

    private final PocketDimensionMenu menu;
    private final int panel;
    private final boolean result;
    /** 只读条件（拆解页的九宫格：输入槽有东西时那是"原版算出来的预览"，只能看不能动） */
    private final java.util.function.BooleanSupplier readOnly;

    public PocketPageSlot(net.minecraft.world.Container container, PocketDimensionMenu menu,
                          int index, int x, int y, int panel) {
        this(container, menu, index, x, y, panel, false);
    }

    public PocketPageSlot(net.minecraft.world.Container container, PocketDimensionMenu menu,
                          int index, int x, int y, int panel, boolean result) {
        this(container, menu, index, x, y, panel, result, null);
    }

    public PocketPageSlot(net.minecraft.world.Container container, PocketDimensionMenu menu,
                          int index, int x, int y, int panel, boolean result,
                          java.util.function.BooleanSupplier readOnly) {
        super(container, index, x, y);
        this.menu = menu;
        this.panel = panel;
        this.result = result;
        this.readOnly = readOnly;
    }

    /** 这一格是不是产物格（菜单分流点击时也用） */
    public boolean isResultSlot() {
        return this.result;
    }

    @Override
    public boolean isActive() {
        return this.menu.isPanelOpen(this.panel);
    }

    /** 这一格现在是不是只读（产物格恒只读；拆解页九宫格在拆解模式下也只读） */
    public boolean isReadOnly() {
        return this.result || (this.readOnly != null && this.readOnly.getAsBoolean());
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !isReadOnly() && isActive();
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        return !isReadOnly() && isActive();
    }
}
