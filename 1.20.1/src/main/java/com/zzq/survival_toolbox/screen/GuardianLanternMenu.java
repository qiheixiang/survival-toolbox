package com.zzq.survival_toolbox.screen;

import com.zzq.survival_toolbox.block.entity.GuardianLanternBlockEntity;
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
 * 镇魂灯菜单容器
 * <p>
 * 槽位布局：
 * <ul>
 *   <li>0：武器槽</li>
 *   <li>1：黑白名单槽</li>
 * </ul>
 * 无玩家背包槽位，物品交互通过外部按钮完成。
 * </p>
 */
public class GuardianLanternMenu extends AbstractContainerMenu {

    private final GuardianLanternBlockEntity blockEntity;
    private final ContainerLevelAccess access;

    public GuardianLanternMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, getBlockEntity(playerInv, data));
    }

    private static GuardianLanternBlockEntity getBlockEntity(Inventory playerInv, FriendlyByteBuf data) {
        BlockEntity be = playerInv.player.level().getBlockEntity(data.readBlockPos());
        if (be instanceof GuardianLanternBlockEntity) return (GuardianLanternBlockEntity) be;
        throw new IllegalStateException("Invalid block entity");
    }

    public GuardianLanternMenu(int windowId, Inventory playerInv,
                               GuardianLanternBlockEntity blockEntity) {
        super(ModMenus.GUARDIAN_LANTERN.get(), windowId);
        this.blockEntity = blockEntity;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // ---- 武器槽 ----
        this.addSlot(new Slot(blockEntity, GuardianLanternBlockEntity.SLOT_WEAPON, 85, 168) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && (stack.isDamageableItem() || stack.getMaxStackSize() == 1);
            }
        });

        // ---- 黑白名单槽 ----
        this.addSlot(new Slot(blockEntity, GuardianLanternBlockEntity.SLOT_BLACKLIST, 125, 168) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !stack.isEmpty() && stack.getItem() instanceof BlacklistItem;
            }
        });
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return stillValid(this.access, player, ModBlocks.GUARDIAN_LANTERN.get());
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    public GuardianLanternBlockEntity getBlockEntity() {
        return blockEntity;
    }
}