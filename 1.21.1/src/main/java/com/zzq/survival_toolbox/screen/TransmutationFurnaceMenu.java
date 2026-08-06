package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.TransmutationFurnaceBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 万物转化炉菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0：输入槽</li>
 *   <li>1：燃料槽</li>
 *   <li>2：输出槽</li>
 * </ul>
 * 容器数据：burnTime 和 totalBurnTime，用于绘制燃烧进度条。
 * </p>
 */
public class TransmutationFurnaceMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final TransmutationFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    public TransmutationFurnaceMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, getBlockEntity(playerInv, data), new SimpleContainerData(2));
    }

    private static TransmutationFurnaceBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf data) {
        BlockEntity be = playerInv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof TransmutationFurnaceBlockEntity) return (TransmutationFurnaceBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public TransmutationFurnaceMenu(int windowId, Inventory playerInv,
                                    TransmutationFurnaceBlockEntity blockEntity,
                                    ContainerData data) {
        super(ModMenus.TRANSMUTATION_FURNACE.get(), windowId);
        this.container = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.blockEntity = blockEntity;
        this.data = data;

        // ---- 输入槽 ----
        this.addSlot(new Slot(container, 0, 56, 17));
        // ---- 燃料槽 ----
        this.addSlot(new Slot(container, 1, 56, 53));
        // ---- 输出槽 ----
        this.addSlot(new Slot(container, 2, 116, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.TRANSMUTATION_FURNACE.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index == 2) {
                if (!this.moveItemStackTo(slotStack, 3, 39, true)) return ItemStack.EMPTY;
            } else if (index >= 3) {
                if (!this.moveItemStackTo(slotStack, 0, 1, false)) {
                    if (!this.moveItemStackTo(slotStack, 1, 2, false)) return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 3, 39, false)) return ItemStack.EMPTY;
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    public int getBurnProgress() {
        int burn = data.get(0);
        int total = data.get(1);
        // 火焰图标只有 14 像素高，原版用的是 * 13（0~13）；* 24 是“箭头进度条”（合成进度）的公式，
        // 误用它会让火焰贴图源坐标变成负数，1.20.1 的 blit 能容错画出，1.21.1 直接画不出来。
        return total != 0 && burn != 0 ? burn * 13 / total : 0;
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }
}