package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.TemperingBoxBlockEntity;
import com.zzq.survival_toolbox.item.CapturedEntityItem;
import com.zzq.survival_toolbox.registry.ModEnchantments;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 锤炼箱菜单
 * <p>
 * 槽位：0-7 自适应装备，8-11 被捕捉实体，12-38 玩家背包，39-47 快捷栏。
 * </p>
 */
public class TemperingBoxMenu extends AbstractContainerMenu {

    private final TemperingBoxBlockEntity box;

    /** 客户端构造：从数据包读取方块实体 */
    public TemperingBoxMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        this(windowId, inv,
                (TemperingBoxBlockEntity) inv.player.level().getBlockEntity(data.readBlockPos()));
    }

    public TemperingBoxMenu(int windowId, Inventory inv, TemperingBoxBlockEntity box) {
        super(ModMenus.TEMPERING_BOX.get(), windowId);
        this.box = box;
        box.startOpen(inv.player);

        // 装备槽 0-7（2 行 × 4 列）
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 4; col++) {
                this.addSlot(new Slot(box, row * 4 + col, 44 + col * 18, 18 + row * 18));
            }
        }
        // 实体槽 8-11（1 行 × 4 列）
        for (int col = 0; col < 4; col++) {
            this.addSlot(new Slot(box, 8 + col, 44 + col * 18, 66));
        }
        // 玩家背包 12-38
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 98 + row * 18));
            }
        }
        // 快捷栏 39-47
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 172));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 12) {
                // 容器 → 玩家背包
                if (!this.moveItemStackTo(stack, 12, 48, true)) return ItemStack.EMPTY;
            } else if (stack.getEnchantments().getLevel(
                    ModEnchantments.adaptation(player.level().registryAccess())) > 0) {
                // 自适应装备 → 装备槽
                if (!this.moveItemStackTo(stack, 0, 8, false)) return ItemStack.EMPTY;
            } else if (stack.getItem() instanceof CapturedEntityItem) {
                // 被捕捉实体 → 实体槽
                if (!this.moveItemStackTo(stack, 8, 12, false)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.box.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.box.stopOpen(player);
    }
}
