package com.zzq.survival_toolbox.screen;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 功能页的"产物格"容器（铁砧 / 锻造台）
 * <p>
 * 产物是原版菜单现算出来的（见 {@link com.zzq.survival_toolbox.util.PocketPageEngine}），
 * 所以这里不存数据、也不落盘：
 * <ul>
 *   <li>服务端：由菜单每次广播前把原版算出来的产物塞进来，然后走原版槽位同步给客户端显示；</li>
 *   <li>客户端：{@link #setItem} 接受原版同步，仅用于显示；</li>
 *   <li>取走产物<b>不经过这里</b>：{@link #removeItem} 永远返回空，真正取走由菜单拦截后调用原版
 *       {@code onTake}（扣经验、消耗输入）。这样即使原版的双击收集想顺手拿走，也拿不到东西，不会刷物品。</li>
 * </ul>
 * </p>
 */
public class PocketResultContainer implements Container {

    private ItemStack result = ItemStack.EMPTY;

    /** 服务端：写入原版算出来的产物（空 = 清空） */
    public void setResult(ItemStack stack) {
        this.result = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return this.result.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? this.result : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        // 取走由菜单拦截 + 原版 onTake 负责，这里不给东西（防止任何原版路径直接拿走产物）
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            this.result = stack.copy(); // 客户端：接受原版槽位同步；服务端：不用（服务端走 setResult）
        }
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.result = ItemStack.EMPTY;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }
}
