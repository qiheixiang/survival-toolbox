package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.SmartFarmBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 智慧农场菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0：水桶槽</li>
 *   <li>1：锄头槽</li>
 *   <li>2-10：工具栏（工具）</li>
 *   <li>11-37：存储区</li>
 * </ul>
 * 按钮 ID：100=收获开关，101=播种开关。
 * </p>
 */
public class SmartFarmMenu extends AbstractContainerMenu {

    private final SmartFarmBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public SmartFarmMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, getBlockEntity(playerInv, data));
    }

    private static SmartFarmBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf data) {
        BlockEntity be = playerInv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof SmartFarmBlockEntity) return (SmartFarmBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public SmartFarmMenu(int windowId, Inventory playerInv, SmartFarmBlockEntity blockEntity) {
        super(ModMenus.SMART_FARM.get(), windowId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // ---- 水桶槽 ----
        this.addSlot(new Slot(blockEntity, SmartFarmBlockEntity.SLOT_WATER, 26, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() == Items.WATER_BUCKET || stack.getItem() == Items.BUCKET;
            }
        });

        // ---- 锄头槽 ----
        this.addSlot(new Slot(blockEntity, SmartFarmBlockEntity.SLOT_HOE, 80, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.canPerformAction(net.minecraftforge.common.ToolActions.HOE_DIG);
            }
        });

        // ---- 工具栏（9格） ----
        for (int i = 0; i < 9; i++) {
            int idx = SmartFarmBlockEntity.SLOT_TOOLBAR_START + i;
            this.addSlot(new Slot(blockEntity, idx, 8 + i * 18, 62) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.isDamageableItem() || stack.getItem() == Items.SHEARS;
                }
            });
        }

        // ---- 存储区（3×9） ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = SmartFarmBlockEntity.STORAGE_START + row * 9 + col;
                this.addSlot(new Slot(blockEntity, idx, 8 + col * 18, 92 + row * 18));
            }
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 160 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 218));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.SMART_FARM.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index >= SmartFarmBlockEntity.STORAGE_START && index < SmartFarmBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(slotStack, SmartFarmBlockEntity.TOTAL_SLOTS,
                        this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, SmartFarmBlockEntity.STORAGE_START,
                        SmartFarmBlockEntity.TOTAL_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case 100 -> {
                blockEntity.setHarvestEnabled(!blockEntity.isHarvestEnabled());
                return true;
            }
            case 101 -> {
                blockEntity.setSowEnabled(!blockEntity.isSowEnabled());
                return true;
            }
            default -> {
                return super.clickMenuButton(player, id);
            }
        }
    }

    public SmartFarmBlockEntity getBlockEntity() {
        return blockEntity;
    }
}