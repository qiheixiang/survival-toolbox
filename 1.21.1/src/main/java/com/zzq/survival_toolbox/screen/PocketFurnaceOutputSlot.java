package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.util.PocketFurnace;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 熔炼页的产物格
 * <p>
 * 默认产物放在这个格子里（和原版熔炉一样），由玩家自己取；
 * 页面上有个按钮可以切成"直接进储物空间"，那种模式下这个格子会一直空着。
 * </p>
 * <p>
 * 规则照原版熔炉的产物格：<b>只能拿不能放</b>（{@code mayPlace = false}）；
 * 拿的时候走原版 {@code onTake}，在这里把攒着的经验给玩家（原版熔炉也是取产物时才结算经验）。
 * 面板收起时同样靠 {@link #isActive()} 隐身/不可点。
 * </p>
 */
public class PocketFurnaceOutputSlot extends Slot {

    private final PocketDimensionMenu menu;

    public PocketFurnaceOutputSlot(PocketFurnaceContainer container, PocketDimensionMenu menu,
                                   int x, int y) {
        super(container, PocketFurnace.SLOT_OUTPUT, x, y);
        this.menu = menu;
    }

    @Override
    public boolean isActive() {
        return this.menu.isPanelOpen(PocketDimensionMenu.PANEL_FURNACE);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false; // 原版熔炉的产物格也不接受放置
    }

    @Override
    public boolean mayPickup(Player player) {
        return isActive();
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        menu.onFurnaceOutputTaken(player);
    }
}
