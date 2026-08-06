package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.MicroFarmBlockEntity;
import com.zzq.survival_toolbox.registry.ModBlocks;
import com.zzq.survival_toolbox.registry.ModMenus;
import com.zzq.survival_toolbox.registry.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 微型牧场菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0-8：食物区</li>
 *   <li>9：实体槽（被捕获实体物品）</li>
 *   <li>10-36：存储区（产出物）</li>
 * </ul>
 * DataSlot 提供剩余时间（秒）、实体数量和实体最大生命值。
 * </p>
 */
public class MicroFarmMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerLevelAccess access;
    private final MicroFarmBlockEntity blockEntity;

    private final DataSlot remainingSecondsDataSlot;
    private final DataSlot entityCountDataSlot;
    private final DataSlot maxHealthDataSlot;

    public static final int FOOD_START = 0;
    public static final int FOOD_END = 8;
    public static final int SLOT_ENTITY = 9;
    public static final int STORAGE_START = 10;
    public static final int STORAGE_END = 36;
    public static final int TOTAL_SLOTS = 37;

    public MicroFarmMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, getBlockEntity(playerInv, data));
    }

    private static MicroFarmBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf data) {
        BlockEntity be = playerInv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof MicroFarmBlockEntity) return (MicroFarmBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public MicroFarmMenu(int windowId, Inventory playerInv, MicroFarmBlockEntity blockEntity) {
        super(ModMenus.MICRO_FARM.get(), windowId);
        this.container = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.blockEntity = blockEntity;

        // ---- 实体槽 ----
        this.addSlot(new Slot(container, SLOT_ENTITY, 40, 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.getItem() == ModItems.CAPTURED_ENTITY.get();
            }
        });

        // ---- 食物区（3×3） ----
        int foodStartX = 100;
        int foodStartY = 11;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                this.addSlot(new Slot(container, slotIndex, foodStartX + col * 18, foodStartY + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !stack.isEmpty() && stack.getItem().isEdible();
                    }
                });
            }
        }

        // ---- 存储区（3×9） ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = STORAGE_START + row * 9 + col;
                this.addSlot(new Slot(container, slotIndex, 8 + col * 18, 72 + row * 18));
            }
        }

        // ---- 玩家背包 ----
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }

        // ---- DataSlot ----
        remainingSecondsDataSlot = new DataSlot() {
            @Override
            public int get() {
                return (int) (blockEntity.getRemainingTicks() / 20);
            }

            @Override
            public void set(int value) {
            }
        };
        this.addDataSlot(remainingSecondsDataSlot);

        entityCountDataSlot = new DataSlot() {
            @Override
            public int get() {
                return blockEntity.getEntityCount();
            }

            @Override
            public void set(int value) {
            }
        };
        this.addDataSlot(entityCountDataSlot);

        maxHealthDataSlot = new DataSlot() {
            @Override
            public int get() {
                return (int) blockEntity.getEntityMaxHealth();
            }

            @Override
            public void set(int value) {
            }
        };
        this.addDataSlot(maxHealthDataSlot);
    }

    public int getRemainingSeconds() {
        return remainingSecondsDataSlot.get();
    }

    public int getEntityCount() {
        return entityCountDataSlot.get();
    }

    public int getMaxHealth() {
        return maxHealthDataSlot.get();
    }

    public MicroFarmBlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.MICRO_FARM.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack stack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            stack = slotStack.copy();

            if (index < STORAGE_START) {
                if (!this.moveItemStackTo(slotStack, STORAGE_START, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (slotStack.getItem() == ModItems.CAPTURED_ENTITY.get()) {
                    if (!this.moveItemStackTo(slotStack, SLOT_ENTITY, SLOT_ENTITY + 1, false)) {
                        if (!this.moveItemStackTo(slotStack, STORAGE_START, this.slots.size(), false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else if (slotStack.getItem().isEdible()) {
                    if (!this.moveItemStackTo(slotStack, FOOD_START, FOOD_END + 1, false)) {
                        if (!this.moveItemStackTo(slotStack, STORAGE_START, this.slots.size(), false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                } else {
                    if (!this.moveItemStackTo(slotStack, STORAGE_START, this.slots.size(), false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (slotStack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return stack;
    }
}