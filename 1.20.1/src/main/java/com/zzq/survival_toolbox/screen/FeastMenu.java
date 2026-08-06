package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.FeastBlockEntity;
import com.zzq.survival_toolbox.item.BlacklistItem;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 混沌篝火菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0-26：物品存储区（食物、药水等）</li>
 *   <li>27：黑白名单槽</li>
 * </ul>
 * 按钮 ID：100~113 控制各项配置开关和数值调节。
 * </p>
 */
public class FeastMenu extends AbstractContainerMenu {

    private final FeastBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public FeastMenu(int id, Inventory inv, FriendlyByteBuf data) {
        this(id, inv, getBlockEntity(inv, data));
    }

    private static FeastBlockEntity getBlockEntity(Inventory inv, FriendlyByteBuf data) {
        BlockEntity be = inv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof FeastBlockEntity) return (FeastBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public FeastMenu(int id, Inventory inv, FeastBlockEntity blockEntity) {
        super(ModMenus.FEAST.get(), id);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // ---- 黑白名单槽 ----
        this.addSlot(new Slot(blockEntity, FeastBlockEntity.SLOT_BLACKLIST, 80, 24) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.getItem() instanceof BlacklistItem;
            }
        });

        // ---- 3行×9列物品栏 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(blockEntity, row * 9 + col, 8 + col * 18, 60 + row * 18));
            }
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 132 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 190));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.FEAST.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();
            if (index < FeastBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(slotStack, FeastBlockEntity.TOTAL_SLOTS,
                        this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(slotStack, 0, FeastBlockEntity.TOTAL_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }

    // ============================================================
    // 按钮事件（100~113）
    // ============================================================

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case 100 -> blockEntity.setEnabled(!blockEntity.isEnabled());
            case 101 -> blockEntity.setShowRange(!blockEntity.isShowRange());
            case 102 -> blockEntity.setWorkInterval(Math.max(1, blockEntity.getWorkInterval() - 1));
            case 103 -> blockEntity.setWorkInterval(Math.min(600, blockEntity.getWorkInterval() + 1));
            case 104 -> blockEntity.setTargetPlayer(!blockEntity.isTargetPlayer());
            case 105 -> blockEntity.setTargetNeutral(!blockEntity.isTargetNeutral());
            case 106 -> blockEntity.setTargetPassive(!blockEntity.isTargetPassive());
            case 107 -> blockEntity.setTargetHostile(!blockEntity.isTargetHostile());
            case 108 -> blockEntity.setRangeX(Math.max(1, blockEntity.getRangeX() - 1));
            case 109 -> blockEntity.setRangeX(Math.min(64, blockEntity.getRangeX() + 1));
            case 110 -> blockEntity.setRangeY(Math.max(1, blockEntity.getRangeY() - 1));
            case 111 -> blockEntity.setRangeY(Math.min(64, blockEntity.getRangeY() + 1));
            case 112 -> blockEntity.setRangeZ(Math.max(1, blockEntity.getRangeZ() - 1));
            case 113 -> blockEntity.setRangeZ(Math.min(64, blockEntity.getRangeZ() + 1));
            default -> {
                return super.clickMenuButton(player, id);
            }
        }
        return true;
    }

    public FeastBlockEntity getBlockEntity() {
        return blockEntity;
    }
}