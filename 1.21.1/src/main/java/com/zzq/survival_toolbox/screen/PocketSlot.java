package com.zzq.survival_toolbox.screen;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 次元袋专用槽位
 * <p>
 * 虚拟堆叠容器的槽位交互与标准容器不同：
 * <ul>
 *   <li>{@code set}：空 = 取走整格（按显示量扣减条目）；非空 = 条目显示值设为该数量（doClick 合并回写）</li>
 *   <li>{@code safeInsert}：直接并入容器（合并计数），容量无限全部放入</li>
 * </ul>
 * 这样绕开 Minecraft 对"getItem 返回值可修改并回写"的假设，保证数量精确。
 * </p>
 */
public class PocketSlot extends Slot {

    private final PocketDimensionContainer pocket;

    public PocketSlot(PocketDimensionContainer container, int index, int x, int y) {
        super(container, index, x, y);
        this.pocket = container;
    }

    @Override
    public void set(ItemStack stack) {
        this.pocket.setBySlot(this.getSlotIndex(), stack);
    }

    @Override
    public ItemStack safeInsert(ItemStack stack) {
        if (stack.isEmpty() || !this.mayPlace(stack)) return stack;
        return this.pocket.insert(stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        // 流体格上的"桶"只是显示图标：任何路径都不能把它当真物品拿走，否则等于刷桶。
        // 尤其要点名原版"双击收集"（ClickType.PICKUP_ALL）：它遍历所有槽位时只看 mayPickup
        // （已核对 1.21.1 AbstractContainerMenu 第 499~511 行），不看 isActive / 悬停。
        return !this.pocket.isFluidSlot(this.getSlotIndex());
    }
}
