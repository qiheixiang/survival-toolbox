package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.InfiniteSourceBlockEntity;
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
 * 无限之源菜单容器
 * <p>
 * 单槽位（标记物），用于记录流体类型。
 * 容器数据状态：0 表示无流体，1 表示有流体。
 * </p>
 */
public class InfiniteSourceMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final InfiniteSourceBlockEntity blockEntity;
    private final ContainerData data;

    public InfiniteSourceMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, getBlockEntity(playerInv, data), new SimpleContainerData(1));
    }

    private static InfiniteSourceBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf data) {
        BlockEntity be = playerInv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof InfiniteSourceBlockEntity) return (InfiniteSourceBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public InfiniteSourceMenu(int windowId, Inventory playerInv,
                              InfiniteSourceBlockEntity blockEntity, ContainerData data) {
        super(ModMenus.INFINITE_SOURCE.get(), windowId);
        this.container = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.blockEntity = blockEntity;
        this.data = data;

        // ---- 标记物槽位 ----
        this.addSlot(new Slot(container, InfiniteSourceBlockEntity.SLOT_MARKER, 80, 35));

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
        return stillValid(this.access, player, ModBlocks.INFINITE_SOURCE.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index == InfiniteSourceBlockEntity.SLOT_MARKER) {
                if (!this.moveItemStackTo(slotStack, 1, 37, true)) return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(slotStack, InfiniteSourceBlockEntity.SLOT_MARKER,
                        InfiniteSourceBlockEntity.SLOT_MARKER + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    public boolean hasFluid() {
        return data.get(0) == 1;
    }

    public InfiniteSourceBlockEntity getBlockEntity() {
        return blockEntity;
    }
}