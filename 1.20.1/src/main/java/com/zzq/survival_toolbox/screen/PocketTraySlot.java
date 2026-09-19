package com.zzq.survival_toolbox.screen;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 托盘槽位（27 格，位于右侧展开面板里）
 * <p>
 * {@code Slot.x/y} 在两个版本里都是 {@code final}，槽位没法在运行时挪出屏幕，
 * 所以"面板收起时隐藏托盘格"用的是原版自己的机制：{@link #isActive()} 返回 false 时，
 * 原版界面既不渲染该格（{@code if (slot.isActive()) renderSlot(...)}），
 * 也不会把它算进鼠标悬停（{@code isHovering(...) && slot.isActive()}），
 * 于是收起状态下托盘格彻底看不见也点不到。
 * （已核对 1.20.1 的 {@code AbstractContainerScreen}：第 102、106、267 行都带 isActive 判断。）
 * </p>
 * <p>
 * 面板状态是服务端权威并随同步包回传，两端一致，因此服务端的 {@code isActive} 判断同样成立。
 * </p>
 */
public class PocketTraySlot extends Slot {

    private final PocketTrayContainer tray;
    private final PocketDimensionMenu menu;

    public PocketTraySlot(PocketTrayContainer tray, PocketDimensionMenu menu, int index, int x, int y) {
        super(tray, index, x, y);
        this.tray = tray;
        this.menu = menu;
    }

    @Override
    public boolean isActive() {
        return this.menu.isPanelOpen(PocketDimensionMenu.PANEL_TRAY);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        // 流体格上是显示用的桶图标，不接受物品放置（正常点击已在菜单里拦截，这里再兜一层）
        return !this.tray.isFluidAt(this.getSlotIndex());
    }

    @Override
    public boolean mayPickup(net.minecraft.world.entity.player.Player player) {
        // 两件都要挡住（已核对原版 AbstractContainerMenu：双击收集 PICKUP_ALL 的遍历只查 mayPickup，不查 isActive）：
        // 1) 面板收起时不能被取走；
        // 2) 流体格上的"桶"只是显示图标，任何路径都不能被拿走，否则等于刷桶。
        return isActive() && !this.tray.isFluidAt(this.getSlotIndex());
    }
}
